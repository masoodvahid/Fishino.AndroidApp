package ir.fishino.app.employer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectoryTokenVerifierTest {

    private val issuer = TestIssuer()
    private val verifier = DirectoryTokenVerifier(issuer.publicKeyBase64)

    private fun verify(token: String, challenge: String = "Zx81kd0QpLm2Vb7nQ4rT9wYe") =
        verifier.verify(token, expectedCode = "482913", expectedProduct = "fishino", expectedChallenge = challenge)

    @Test
    fun `a genuine token yields the employer`() {
        val employer = verify(issuer.directoryToken())

        assertEquals(Employer("482913", "شرکت پیام نصر", "https://hrm.payamnasr.com/api/v1"), employer)
    }

    @Test
    fun `trailing slash of the address is dropped`() {
        assertEquals("https://hrm.payamnasr.com/api/v1", verify(issuer.directoryToken(url = "https://hrm.payamnasr.com/api/v1/"))?.apiBaseUrl)
    }

    @Test
    fun `a token signed by another key is rejected`() {
        assertNull(verify(TestIssuer().directoryToken()))
    }

    @Test
    fun `a tampered payload is rejected`() {
        val (prefix, _, signature) = issuer.directoryToken().split('.')
        val forgedPayload = issuer.directoryToken(url = "https://evil.example.com/api/v1").split('.')[1]

        assertNull(verify("$prefix.$forgedPayload.$signature"))
    }

    @Test
    fun `a replayed answer for another request is rejected`() {
        assertNull(verify(issuer.directoryToken(), challenge = "AnotherChallengeValue123"))
        assertNull(verify(issuer.directoryToken(code = "111111")))
        assertNull(verify(issuer.directoryToken(product = "tooka")))
    }

    @Test
    fun `license tokens and insecure addresses are rejected`() {
        assertNull(verify(issuer.directoryToken(type = "license")))
        assertNull(verify(issuer.directoryToken(url = "http://hrm.payamnasr.com/api/v1")))
        assertNull(verify(issuer.directoryToken(url = "https://hrm.payamnasr.com/api/v1?next=x")))
        assertNull(verify(issuer.directoryToken(name = "  ")))
    }

    @Test
    fun `garbage and a missing pinned key are rejected`() {
        assertNull(verify("not-a-token"))
        assertNull(verify("rdl1.!!!.###"))
        assertNull(DirectoryTokenVerifier("").verify(issuer.directoryToken(), "482913", "fishino", "Zx81kd0QpLm2Vb7nQ4rT9wYe"))
    }
}
