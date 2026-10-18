package rdsco.fishino.app.employer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import java.net.URI

/**
 * Checks the license manager's signed answer before the app trusts it.
 *
 * Token format (same as license tokens): `rdl1.<base64url(JSON)>.<base64url(Ed25519 signature)>`,
 * signature over `rdl1.<payload>`, verified with the public key pinned at build
 * time. The address is saved permanently and all logins go there, so a forged
 * or replayed answer must never be accepted.
 */
class DirectoryTokenVerifier(publicKeyBase64: String) {

    private val publicKey: Ed25519PublicKeyParameters? = runCatching {
        val bytes = Base64.decode(publicKeyBase64.trim())
        if (bytes.size == Ed25519PublicKeyParameters.KEY_SIZE) Ed25519PublicKeyParameters(bytes, 0) else null
    }.getOrNull()

    /**
     * @return the employer the token vouches for, or null if the token is not
     *   genuine or was not issued for this exact request.
     */
    fun verify(token: String, expectedCode: String, expectedProduct: String, expectedChallenge: String): Employer? {
        val key = publicKey ?: return null
        val parts = token.split('.')

        if (parts.size != 3 || parts[0] != PREFIX) return null

        val signature = Base64Url.decode(parts[2]) ?: return null
        if (signature.size != SIGNATURE_SIZE) return null

        val signedPart = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        val signer = Ed25519Signer().apply {
            init(false, key)
            update(signedPart, 0, signedPart.size)
        }
        if (!signer.verifySignature(signature)) return null

        val claims = Base64Url.decode(parts[1])
            ?.let { runCatching { Json.parseToJsonElement(it.toString(Charsets.UTF_8)).jsonObject }.getOrNull() }
            ?: return null

        if (claims.string("typ") != TOKEN_TYPE) return null
        if (claims.string("code") != expectedCode) return null
        if (claims.string("prd") != expectedProduct) return null
        if (claims.string("chl") != expectedChallenge) return null

        val name = claims.string("name")?.trim().orEmpty()
        val url = claims.string("url")?.trim()?.trimEnd('/').orEmpty()

        if (name.isEmpty() || !isHttpsUrl(url)) return null

        return Employer(code = expectedCode, name = name, apiBaseUrl = url)
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null
    }.getOrDefault(false)

    companion object {
        const val PREFIX = "rdl1"
        const val TOKEN_TYPE = "app_directory"
        private const val SIGNATURE_SIZE = 64
    }
}
