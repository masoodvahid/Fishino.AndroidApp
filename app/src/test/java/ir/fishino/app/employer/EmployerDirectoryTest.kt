package ir.fishino.app.employer

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class EmployerDirectoryTest {

    private val issuer = TestIssuer()
    private val challenge = "Zx81kd0QpLm2Vb7nQ4rT9wYe"
    private val directory = EmployerDirectory(
        client = OkHttpClient(),
        directoryUrl = "https://api.example.com/api/v1/",
        productSlug = "fishino",
        verifier = DirectoryTokenVerifier(issuer.publicKeyBase64),
    )

    private fun interpret(status: Int, body: String, retryAfter: String? = null) =
        directory.interpret(status, retryAfter, body, code = "482913", challenge = challenge)

    @Test
    fun `a signed answer is found`() {
        val body = """{"ok":true,"data":{"code":"482913","employer":{"name":"x","api_base_url":"https://x"},"token":"${issuer.directoryToken()}"}}"""

        val result = interpret(200, body)

        assertEquals(ResolveResult.Found(Employer("482913", "شرکت پیام نصر", "https://hrm.payamnasr.com/api/v1")), result)
    }

    @Test
    fun `the unsigned fields of the answer are never trusted`() {
        val body = """{"ok":true,"data":{"employer":{"name":"x","api_base_url":"https://evil.example.com"},"token":"rdl1.e30.AAAA"}}"""

        assertEquals(ResolveResult.Untrusted, interpret(200, body))
        assertEquals(ResolveResult.Untrusted, interpret(200, "<html>captive portal</html>"))
    }

    @Test
    fun `error answers map to results`() {
        assertEquals(ResolveResult.NotFound, interpret(404, """{"ok":false,"error":{"code":"code_not_found","message":"..."}}"""))
        assertEquals(ResolveResult.Inactive, interpret(403, """{"ok":false,"error":{"code":"employer_inactive","message":"..."}}"""))
        assertEquals(ResolveResult.RateLimited(120), interpret(429, """{"ok":false,"error":{"code":"rate_limited"}}""", retryAfter = "120"))
        assertEquals(ResolveResult.RateLimited(60), interpret(429, ""))
        assertEquals(ResolveResult.ServerError, interpret(404, "Not Found"))
        assertEquals(ResolveResult.ServerError, interpret(500, ""))
    }
}
