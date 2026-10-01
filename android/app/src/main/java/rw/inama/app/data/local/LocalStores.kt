package rw.inama.app.data.local

import android.content.Context
import rw.inama.app.data.json.JsonArray
import rw.inama.app.data.json.JsonNull
import rw.inama.app.data.json.JsonObject
import rw.inama.app.data.json.JsonValue
import rw.inama.app.data.mapper.StorageCodecs
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.ExpertCase
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.model.WeatherReport
import java.io.File

/** All on-phone data, one JSON file each, under the app's private files directory. */
class LocalStores(context: Context) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }

    private fun <T> list(name: String, encode: (T) -> JsonObject, decode: (JsonObject) -> T) =
        JsonFileStore(
            File(dir, "$name.json"),
            default = { emptyList<T>() },
            encode = { items: List<T> -> JsonArray(items.map(encode)) },
            decode = { v: JsonValue -> (v as? JsonArray)?.objects()?.mapNotNull { runCatching { decode(it) }.getOrNull() } ?: emptyList() },
        )

    private fun <T : Any> single(name: String, encode: (T) -> JsonObject, decode: (JsonObject) -> T) =
        JsonFileStore<T?>(
            File(dir, "$name.json"),
            default = { null },
            encode = { value -> value?.let(encode) ?: JsonNull },
            decode = { v -> (v as? JsonObject)?.let { runCatching { decode(it) }.getOrNull() } },
        )

    val settings = JsonFileStore(
        File(dir, "settings.json"),
        default = { AppSettings() },
        encode = { s: AppSettings -> StorageCodecs.settingsToJson(s) },
        decode = { v: JsonValue -> (v as? JsonObject)?.let(StorageCodecs::settingsFrom) ?: AppSettings() },
    )
    val session = single<Session>("session", StorageCodecs::sessionToJson, StorageCodecs::sessionFrom)
    val farmer = single<Farmer>("farmer", StorageCodecs::farmerToJson, StorageCodecs::farmerFrom)
    val weather = single<WeatherReport>("weather_cache", StorageCodecs::weatherToJson, StorageCodecs::weatherFrom)
    val fields = list<Field>("fields", StorageCodecs::fieldToJson, StorageCodecs::fieldFrom)
    val diagnoses = list<Diagnosis>("diagnoses", StorageCodecs::diagnosisToJson, StorageCodecs::diagnosisFrom)
    val answers = list<Answer>("answers", StorageCodecs::answerToJson, StorageCodecs::answerFrom)
    val tasks = list<FarmTask>("tasks", StorageCodecs::taskToJson, StorageCodecs::taskFrom)
    val cases = list<ExpertCase>("cases", StorageCodecs::caseToJson, StorageCodecs::caseFrom)

    suspend fun clearAll() {
        session.clear()
        farmer.clear()
        weather.clear()
        fields.clear()
        diagnoses.clear()
        answers.clear()
        tasks.clear()
        cases.clear()
        settings.clear()
    }
}
