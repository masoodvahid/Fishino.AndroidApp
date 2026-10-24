package rdsco.fishino.app.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiTest {

    private val api = AuthApi(OkHttpClient(), "https://hrm.example.com/api/v1/", "test")

    private fun failure(status: Int, body: String, retryAfter: String? = null): ApiError =
        (api.interpret(status, retryAfter, body) { } as ApiResult.Failed).error

    @Test
    fun `server errors keep code, message, captcha and retry time`() {
        val error = failure(
            401,
            """{"ok":false,"error":{"code":"invalid_credentials","message":"نام کاربری یا رمز عبور اشتباه است.","captcha":{"key":"k1","image":"data:image/jpeg;base64,AAA="}}}""",
        )
        assertEquals("invalid_credentials", error.code)
        assertEquals("نام کاربری یا رمز عبور اشتباه است.", error.message)
        assertEquals(Captcha("k1", "data:image/jpeg;base64,AAA="), error.captcha)

        val locked = failure(429, """{"ok":false,"error":{"code":"locked","message":"…","retry_after":840}}""", retryAfter = "840")
        assertEquals("locked", locked.code)
        assertEquals(840L, locked.retryAfterSeconds)
    }

    @Test
    fun `answers that are not this api map to local codes`() {
        assertEquals(ApiError.UNAUTHORIZED, failure(401, """{"message":"Unauthenticated."}""").code)
        assertEquals(ApiError.UNSUPPORTED_SERVER, failure(404, "<html>Not Found</html>").code)
        assertEquals(ApiError.TOO_MANY_REQUESTS, failure(429, """{"message":"Too Many Attempts."}""", retryAfter = "30").code)
        assertEquals(30L, failure(429, "", retryAfter = "30").retryAfterSeconds)
        assertEquals(ApiError.SERVER_ERROR, failure(500, "oops").code)
    }

    @Test
    fun `a captcha that is not an image is ignored`() {
        assertNull(failure(422, """{"ok":false,"error":{"code":"captcha_required","captcha":{"key":"k","image":"javascript:x"}}}""").captcha)
    }

    @Test
    fun `login sends the fields and reads the session`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"token":"1|abc","expires_at":"2026-10-22T15:31:00+03:30",
                    "user":{"id":7,"username":"ali","full_name":"علی رضایی","mobile":"09123456789"}}}""",
                ),
            )
            val client = AuthApi(OkHttpClient(), server.url("/api/v1").toString(), "Fishino-Android/0.1.0")

            val result = client.login("ali", "secret", "device-1", CaptchaAnswer("key", "27493"))

            val session = (result as ApiResult.Ok).value
            assertEquals("1|abc", session.token)
            assertEquals(AppUser(7, "ali", "علی رضایی", "09123456789"), session.user)
            assertEquals(1_792_670_460_000L, session.expiresAtMillis)

            val request = server.takeRequest()
            assertEquals("/api/v1/auth/login", request.path)
            assertEquals("application/json", request.getHeader("Accept"))
            val body = request.body.readUtf8()
            listOf("\"username\":\"ali\"", "\"device_id\":\"device-1\"", "\"captcha\":\"27493\"", "\"captcha_key\":\"key\"")
                .forEach { assertTrue(body, body.contains(it)) }
        }
    }

    @Test
    fun `config defaults to password when sms codes are off`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"methods":{"password":true,"otp":false},"default_method":"otp","otp":{"length":5}}}"""))
            val config = (AuthApi(OkHttpClient(), server.url("/api/v1").toString(), "t").config() as ApiResult.Ok).value

            assertEquals(LoginMethod.Password, config.defaultMethod)
            assertEquals(false, config.otpEnabled)
        }
    }

    @Test
    fun `config reads the logo version when the server has a logo`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"methods":{"password":true,"otp":false},"branding":{"logo_version":"1729760000"}}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"methods":{"password":true,"otp":false},"branding":{"logo_version":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"methods":{"password":true,"otp":false}}}"""))
            val api = AuthApi(OkHttpClient(), server.url("/api/v1").toString(), "t")

            assertEquals("1729760000", (api.config() as ApiResult.Ok).value.logoVersion)
            assertNull((api.config() as ApiResult.Ok).value.logoVersion)
            assertNull((api.config() as ApiResult.Ok).value.logoVersion)
        }
    }

    @Test
    fun `refresh sends the bearer token`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
            val result = AuthApi(OkHttpClient(), server.url("/api/v1").toString(), "t").refresh("1|abc")

            assertEquals(ApiError.UNAUTHORIZED, (result as ApiResult.Failed).error.code)
            assertEquals("Bearer 1|abc", server.takeRequest().getHeader("Authorization"))
        }
    }
}
