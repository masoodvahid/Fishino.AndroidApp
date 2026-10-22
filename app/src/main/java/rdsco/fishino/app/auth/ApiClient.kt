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

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

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
