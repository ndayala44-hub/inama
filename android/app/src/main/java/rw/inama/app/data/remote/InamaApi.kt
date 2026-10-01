package rw.inama.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import rw.inama.app.data.json.Json
import rw.inama.app.data.json.JsonObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server could not be reached or failed (network, timeout, 5xx) — callers fall back. */
class ApiUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The server answered with an error the user should see (4xx), e.g. invalid_phone. */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/**
 * Thin HTTP client for the Inama v1 API (server/src/v1, see docs/API.md).
 * Base URL, token and language are read per call, so Settings changes apply immediately.
 * Every request sends Accept-Language — the server answers in that language once supported.
 */
class InamaApi(
    private val baseUrl: suspend () -> String,
    private val token: suspend () -> String?,
    private val language: suspend () -> String,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(path: String, auth: Boolean = false): JsonObject = execute("GET", path, null, auth)

    suspend fun postJson(path: String, body: JsonObject, auth: Boolean = false): JsonObject =
        execute("POST", path, Json.stringify(body).toRequestBody(JSON), auth)

    suspend fun putJson(path: String, body: JsonObject, auth: Boolean = true): JsonObject =
        execute("PUT", path, Json.stringify(body).toRequestBody(JSON), auth)

    suspend fun postPhoto(path: String, photo: File, mimeType: String, fields: Map<String, String>): JsonObject {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", photo.name, photo.asRequestBody(mimeType.toMediaType()))
        fields.forEach { (k, v) -> multipart.addFormDataPart(k, v) }
        return execute("POST", path, multipart.build(), auth = true)
    }

    private suspend fun execute(method: String, path: String, body: RequestBody?, auth: Boolean): JsonObject {
        val base = baseUrl().trimEnd('/')
        val builder = Request.Builder()
            .url(base + path)
            .header("Accept", "application/json")
            .header("Accept-Language", language())
            .method(method, body)
        if (auth) {
            val t = token() ?: throw ApiUnavailableException("Not signed in to the Inama server")
            builder.header("Authorization", "Bearer $t")
        }
        return withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw ApiUnavailableException("Inama server unreachable", e)
            } catch (e: IllegalArgumentException) {
                throw ApiUnavailableException("Invalid server address", e)
            }
            response.use { res ->
                val text = res.body?.string().orEmpty()
                val json = runCatching { Json.parseObject(text) }.getOrNull()
                when {
                    res.isSuccessful && json != null -> json.obj("data") ?: JsonObject(emptyMap())
                    res.code in 400..499 && res.code != 408 && res.code != 429 -> {
                        val error = json?.obj("error")
                        throw ApiException(res.code, error?.string("code", "error") ?: "error", error?.string("message", "Request failed") ?: "Request failed")
                    }
                    else -> throw ApiUnavailableException("Inama server error ${res.code}")
                }
            }
        }
    }
}
