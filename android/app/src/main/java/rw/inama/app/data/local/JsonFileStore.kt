package rw.inama.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rw.inama.app.data.json.Json
import rw.inama.app.data.json.JsonValue
import java.io.File

/**
 * One JSON document in app-private storage, exposed as a Flow. Writes are serialised with a mutex
 * and made atomic (write to a temp file, then rename), so a crash never leaves half a file.
 * Good for the MVP's data volumes (hundreds of records); a Room database can replace it behind
 * the same repository interfaces when volumes grow.
 */
class JsonFileStore<T>(
    private val file: File,
    private val default: () -> T,
    private val encode: (T) -> JsonValue,
    private val decode: (JsonValue) -> T,
) {
    /** Wraps the value so "loaded, and the value is null" (no farmer yet) differs from "not loaded". */
    private data class Loaded<V>(val value: V)

    private val mutex = Mutex()
    private val state = MutableStateFlow<Loaded<T>?>(null)

    val data: Flow<T> = state.onStart { load() }.filterNotNull().map { it.value }

    suspend fun get(): T = load()

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val loaded = state.value
        val current = if (loaded != null) loaded.value else read()
        val next = transform(current)
        write(next)
        state.value = Loaded(next)
        next
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        state.value = Loaded(default())
    }

    private suspend fun load(): T {
        state.value?.let { return it.value }
        return mutex.withLock {
            val loaded = state.value
            if (loaded != null) loaded.value else read().also { state.value = Loaded(it) }
        }
    }

    private suspend fun read(): T = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) decode(Json.parse(file.readText())) else default()
        } catch (e: Exception) {
            // Unreadable file: keep a copy for support, start fresh.
            runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) }
            default()
        }
    }

    private suspend fun write(value: T) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.stringify(encode(value)))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
