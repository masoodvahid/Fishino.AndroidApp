package rdsco.fishino.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/** Numeric captcha the server asks for after repeated failures. */
data class Captcha(val key: String, val imageDataUri: String)

data class LoginConfig(
    val passwordEnabled: Boolean,
    val otpEnabled: Boolean,
    val defaultMethod: LoginMethod,
    val otpLength: Int,
    val resendAfterSeconds: Int,
)

enum class LoginMethod { Password, Otp }

data class OtpRequest(val otpToken: String, val message: String, val codeLength: Int, val resendAfterSeconds: Int)

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
 * The employer's own Fishino API (/api/v1), whose address came from the
 * license manager. Answers are {"ok": true, "data": …} or
 * {"ok": false, "error": {"code", "message", …}}.
 */
class AuthApi(
    private val client: OkHttpClient,
    apiBaseUrl: String,
    private val userAgent: String,
) {
    private val baseUrl = apiBaseUrl.trimEnd('/')

    suspend fun config(): ApiResult<LoginConfig> = get("/auth/config") { data ->
        val methods = data.obj("methods")
        val otp = data.obj("otp")
        val otpEnabled = methods?.bool("otp") == true

        LoginConfig(
            passwordEnabled = methods?.bool("password") != false,
            otpEnabled = otpEnabled,
            defaultMethod = if (otpEnabled && data.string("default_method") == "otp") LoginMethod.Otp else LoginMethod.Password,
            otpLength = otp?.int("length") ?: 5,
            resendAfterSeconds = otp?.int("resend_after_seconds") ?: 120,
        )
    }

    suspend fun captcha(): ApiResult<Captcha> = get("/auth/captcha") { data ->
        parseCaptcha(data) ?: throw IllegalStateException("no captcha")
    }

    suspend fun login(username: String, password: String, deviceId: String, captcha: CaptchaAnswer?): ApiResult<Session> =
        post("/auth/login", body {
            put("username", username)
            put("password", password)
            put("device_id", deviceId)
            captcha?.let { put("captcha", it.answer); put("captcha_key", it.key) }
        }, token = null, ::parseSession)

    suspend fun sendOtp(mobile: String, deviceId: String, captcha: CaptchaAnswer?): ApiResult<OtpRequest> =
        post("/auth/otp/send", body {
            put("mobile", mobile)
            put("device_id", deviceId)
            captcha?.let { put("captcha", it.answer); put("captcha_key", it.key) }
        }, token = null) { data ->
            OtpRequest(
                otpToken = data.string("otp_token") ?: throw IllegalStateException("no otp_token"),
                message = data.string("message").orEmpty(),
                codeLength = data.int("code_length") ?: 5,
                resendAfterSeconds = data.int("resend_after") ?: 120,
            )
        }

    suspend fun verifyOtp(mobile: String, code: String, otpToken: String, deviceId: String): ApiResult<Session> =
        post("/auth/otp/verify", body {
            put("mobile", mobile)
            put("code", code)
            put("otp_token", otpToken)
            put("device_id", deviceId)
        }, token = null, ::parseSession)

    suspend fun refresh(token: String): ApiResult<Session> = post("/auth/refresh", body {}, token, ::parseSession)

    suspend fun logout(token: String): ApiResult<Unit> = post("/auth/logout", body {}, token) { }

    private suspend fun <T> get(path: String, parse: (JsonObject) -> T): ApiResult<T> =
        execute(Request.Builder().url(baseUrl + path).get(), token = null, parse)

    private suspend fun <T> post(path: String, json: JsonObject, token: String?, parse: (JsonObject) -> T): ApiResult<T> =
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

    internal fun <T> interpret(status: Int, retryAfterHeader: String?, body: String, parse: (JsonObject) -> T): ApiResult<T> {
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

    private fun parseSession(data: JsonObject): Session {
        val user = data.obj("user") ?: throw IllegalStateException("no user")
        val expiresAt = data.string("expires_at")?.let(::parseIsoMillis)
            ?: (System.currentTimeMillis() + 15 * 60_000L)

        return Session(
            token = data.string("token") ?: throw IllegalStateException("no token"),
            expiresAtMillis = expiresAt,
            user = AppUser(
                id = user.long("id") ?: 0,
                username = user.string("username").orEmpty(),
                fullName = user.string("full_name").orEmpty(),
                mobile = user.string("mobile"),
            ),
        )
    }

    /** "2026-10-22T15:31:00+03:30" (java.time needs API 26; minSdk is 24). */
    private fun parseIsoMillis(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)?.time
    }.getOrNull()

    private fun parseCaptcha(data: JsonObject): Captcha? {
        val key = data.string("key") ?: return null
        val image = data.string("image")?.takeIf { it.startsWith("data:image/") } ?: return null
        return Captcha(key, image)
    }

    private fun body(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    private fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

    private fun JsonObject.primitive(key: String): JsonPrimitive? = runCatching { this[key]?.jsonPrimitive }.getOrNull()

    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull

    private fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class CaptchaAnswer(val key: String, val answer: String)
