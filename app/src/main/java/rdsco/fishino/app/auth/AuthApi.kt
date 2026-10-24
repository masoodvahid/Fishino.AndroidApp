package rdsco.fishino.app.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
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
    /** Changes when the employer uploads a new logo; null: no logo (or an older server). */
    val logoVersion: String? = null,
)

enum class LoginMethod { Password, Otp }

data class OtpRequest(val otpToken: String, val message: String, val codeLength: Int, val resendAfterSeconds: Int)

/** Sign-in endpoints (/auth/…) of the employer's own Fishino. */
class AuthApi(private val http: ApiClient) {

    constructor(client: OkHttpClient, apiBaseUrl: String, userAgent: String) : this(ApiClient(client, apiBaseUrl, userAgent))

    suspend fun config(): ApiResult<LoginConfig> = http.get("/auth/config", token = null) { data ->
        val methods = data.obj("methods")
        val otp = data.obj("otp")
        val otpEnabled = methods?.bool("otp") == true

        LoginConfig(
            passwordEnabled = methods?.bool("password") != false,
            otpEnabled = otpEnabled,
            defaultMethod = if (otpEnabled && data.string("default_method") == "otp") LoginMethod.Otp else LoginMethod.Password,
            otpLength = otp?.int("length") ?: 5,
            resendAfterSeconds = otp?.int("resend_after_seconds") ?: 120,
            logoVersion = data.obj("branding")?.string("logo_version")?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun captcha(): ApiResult<Captcha> = http.get("/auth/captcha", token = null) { data ->
        parseCaptcha(data) ?: throw IllegalStateException("no captcha")
    }

    suspend fun login(username: String, password: String, deviceId: String, captcha: CaptchaAnswer?): ApiResult<Session> =
        http.post("/auth/login", body {
            put("username", username)
            put("password", password)
            put("device_id", deviceId)
            captcha?.let { put("captcha", it.answer); put("captcha_key", it.key) }
        }, token = null, ::parseSession)

    suspend fun sendOtp(mobile: String, deviceId: String, captcha: CaptchaAnswer?): ApiResult<OtpRequest> =
        http.post("/auth/otp/send", body {
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
        http.post("/auth/otp/verify", body {
            put("mobile", mobile)
            put("code", code)
            put("otp_token", otpToken)
            put("device_id", deviceId)
        }, token = null, ::parseSession)

    suspend fun refresh(token: String): ApiResult<Session> = http.post("/auth/refresh", body {}, token, ::parseSession)

    suspend fun logout(token: String): ApiResult<Unit> = http.post("/auth/logout", body {}, token) { }

    internal fun <T> interpret(status: Int, retryAfterHeader: String?, body: String, parse: (JsonObject) -> T): ApiResult<T> =
        http.interpret(status, retryAfterHeader, body, parse)

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
            sync = data.obj("sync")?.let { sync ->
                sync.string("token")?.let { SyncGrant(it, sync.long("check_interval_seconds") ?: 6 * 3600L) }
            },
        )
    }

    /** "2026-10-22T15:31:00+03:30" (java.time needs API 26; minSdk is 24). */
    private fun parseIsoMillis(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)?.time
    }.getOrNull()

    private fun body(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
}

data class CaptchaAnswer(val key: String, val answer: String)
