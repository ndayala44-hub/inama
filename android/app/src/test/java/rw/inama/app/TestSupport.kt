package rw.inama.app

import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Test helpers with no extra dependencies (the domain layer is plain Kotlin). */
object TestSupport {

    /** Finds a file under the repository's shared/ folder, whatever the working directory. */
    fun sharedFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/$relative")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/$relative not found above ${File("").absolutePath}")
    }

    fun resource(path: String): String =
        TestSupport::class.java.getResource(path)?.readText() ?: error("missing test resource $path")

    /** Runs a suspend block that completes without real suspension (fakes return immediately). */
    fun <T> blocking(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome?.getOrThrow() ?: error("block suspended; use a real coroutine test runner")
    }
}
