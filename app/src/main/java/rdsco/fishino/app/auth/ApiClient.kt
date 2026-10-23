package rdsco.fishino.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * A failed call. [code] is the server's stable error code, or one of the
 * local ones in the companion object; [message] is the server's Persian
 * message, if it sent one.
 */
data class ApiError(
    val code: String,
    val message: String? = null,
    val status: Int = 0,
    val captcha: Captcha? = null,
    val retryAfterSeconds: Long? = null,
    /** Messages per form field (validation_failed). */
    val fields: Map<String, String> = emptyMap(),
    /** requirement_pending: the profile section ("mobile", "signature", …) where the user can do it. */
    val requirementTab: String? = null,
) {
    companion object {
        const val NETWORK = "network"
        const val UNAUTHORIZED = "unauthorized"

        /** The employer's Fishino is older than the app's API. */
        const val UNSUPPORTED_SERVER = "unsupported_server"
        const val TOO_MANY_REQUESTS = "too_many_requests"
        const val SERVER_ERROR = "server_error"
    }
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val error: ApiError) : ApiResult<Nothing>
}

/**
 * HTTP plumbing for the employer's own Fishino API (/api/v1), whose address
 * came from the license manager. Answers are {"ok": true, "data": …} or
 * {"ok": false, "error": {"code", "message", …}}.
 */
class ApiClient(
    private val client: OkHttpClient,
    apiBaseUrl: String,
    private val userAgent: String,
) {
    private val baseUrl = apiBaseUrl.trimEnd('/')

    suspend fun <T> get(path: String, token: String?, parse: (JsonObject) -> T): ApiResult<T> =
        execute(Request.Builder().url(baseUrl + path).get(), token, parse)

    suspend fun <T> post(path: String, json: JsonObject, token: String?, parse: (JsonObject) -> T): ApiResult<T> =
        execute(Request.Builder().url(baseUrl + path).post(json.toString().toRequestBody(JSON)), token, parse)

    suspend fun <T> delete(path: String, token: String?, parse: (JsonObject) -> T): ApiResult<T> =
        execute(Request.Builder().url(baseUrl + path).delete(), token, parse)

    /** A multipart upload: one file field plus optional text fields. */
    suspend fun <T> upload(
        path: String,
        token: String?,
        file: UploadPart,
        parse: (JsonObject) -> T,
    ): ApiResult<T> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(file.field, file.fileName, file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()))
            .build()

        return execute(Request.Builder().url(baseUrl + path).post(body), token, parse)
    }

    /**
     * A file (image, PDF, attachment). Errors come back in the JSON format;
     * files larger than [maxBytes] are refused.
     */
    suspend fun download(path: String, token: String?, maxBytes: Long = 30L * 1024 * 1024): ApiResult<Download> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(baseUrl + path).get().header("User-Agent", userAgent)
            token?.let { builder.header("Authorization", "Bearer $it") }

            try {
                client.newCall(builder.build()).execute().use { response ->
                    val body = response.body ?: return@use ApiResult.Failed(ApiError(ApiError.SERVER_ERROR, status = response.code))
                    val type = response.header("Content-Type").orEmpty()

                    if (!response.isSuccessful || type.startsWith("application/json")) {
                        return@use interpret(response.code, response.header("Retry-After"), body.string()) { Download(ByteArray(0), type, null) }
                    }

                    if (body.contentLength() > maxBytes) {
                        return@use ApiResult.Failed(ApiError(FILE_TOO_LARGE, status = response.code))
                    }

                    val bytes = body.byteStream().use { stream -> readAtMost(stream, maxBytes) }
                        ?: return@use ApiResult.Failed(ApiError(FILE_TOO_LARGE, status = response.code))

                    ApiResult.Ok(Download(bytes, type.substringBefore(';').trim(), fileName(response.header("Content-Disposition"))))
                }
            } catch (e: IOException) {
                ApiResult.Failed(ApiError(ApiError.NETWORK))
            }
        }

    private suspend fun <T> execute(builder: Request.Builder, token: String?, parse: (JsonObject) -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            builder.header("Accept", "application/json").header("User-Agent", userAgent)
            token?.let { builder.header("Authorization", "Bearer $it") }

            try {
                client.newCall(builder.build()).execute().use { response ->
                    interpret(response.code, response.header("Retry-After"), response.body?.string().orEmpty(), parse)
                }
            } catch (e: IOException) {
                ApiResult.Failed(ApiError(ApiError.NETWORK))
            }
        }

    fun <T> interpret(status: Int, retryAfterHeader: String?, body: String, parse: (JsonObject) -> T): ApiResult<T> {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val retryAfter = retryAfterHeader?.trim()?.toLongOrNull()

        if (json?.bool("ok") == true) {
            val data = json.obj("data") ?: JsonObject(emptyMap())
            return runCatching { ApiResult.Ok(parse(data)) }
                .getOrElse { ApiResult.Failed(ApiError(ApiError.SERVER_ERROR, status = status)) }
        }

        val error = json?.obj("error")
        if (error != null) {
            return ApiResult.Failed(
                ApiError(
                    code = error.string("code") ?: ApiError.SERVER_ERROR,
                    message = error.string("message"),
                    status = status,
                    captcha = error.obj("captcha")?.let(::parseCaptcha),
                    retryAfterSeconds = error.long("retry_after") ?: retryAfter,
                    fields = error.obj("fields")?.let { fields ->
                        fields.keys.mapNotNull { key -> fields.string(key)?.let { key to it } }.toMap()
                    }.orEmpty(),
                    requirementTab = error.obj("requirement")?.string("tab"),
                ),
            )
        }

        // Not this API's format: an older Fishino, the framework's own
        // answers (401, 429) or something in between (a proxy page).
        val code = when (status) {
            401 -> ApiError.UNAUTHORIZED
            404, 405 -> ApiError.UNSUPPORTED_SERVER
            429 -> ApiError.TOO_MANY_REQUESTS
            else -> ApiError.SERVER_ERROR
        }

        return ApiResult.Failed(ApiError(code, status = status, retryAfterSeconds = retryAfter))
    }

    companion object {
        const val FILE_TOO_LARGE = "file_too_large"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The whole stream, or null when it is longer than [maxBytes]. */
        internal fun readAtMost(stream: java.io.InputStream, maxBytes: Long): ByteArray? {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                buffer.write(chunk, 0, read)
            }
            return buffer.toByteArray()
        }

        /** attachment; filename="a.pdf"; filename*=UTF-8''%D8%A2.pdf */
        internal fun fileName(disposition: String?): String? {
            if (disposition.isNullOrBlank()) return null
            Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let { match ->
                return runCatching { java.net.URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
            }
            return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)
        }
    }
}

/** A file to upload: form [field], original [fileName] (its extension counts on the server). */
class UploadPart(val field: String, val fileName: String, val mimeType: String, val bytes: ByteArray)

class Download(val bytes: ByteArray, val contentType: String, val fileName: String?)

internal fun parseCaptcha(data: JsonObject): Captcha? {
    val key = data.string("key") ?: return null
    val image = data.string("image")?.takeIf { it.startsWith("data:image/") } ?: return null
    return Captcha(key, image)
}

internal fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

internal fun JsonObject.array(key: String): JsonArray? = runCatching { this[key]?.jsonArray }.getOrNull()

/** The objects of an array; anything else in it is skipped. */
internal fun JsonObject.objects(key: String): List<JsonObject> =
    array(key)?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()

private fun JsonObject.primitive(key: String): JsonPrimitive? = runCatching { this[key]?.jsonPrimitive }.getOrNull()

/** A string value; JSON null and blank strings are null. */
internal fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

internal fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull

internal fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull
