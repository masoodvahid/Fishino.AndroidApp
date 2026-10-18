package rdsco.fishino.app.employer

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import java.security.SecureRandom

/** Issues tokens the way the license manager's LicenseTokenIssuer does. */
internal class TestIssuer {
    private val privateKey = Ed25519PrivateKeyParameters(SecureRandom())

    val publicKeyBase64: String = Base64.toBase64String(privateKey.generatePublicKey().encoded)

    fun issue(claims: JsonObject): String {
        val payload = Base64Url.encode(claims.toString().toByteArray(Charsets.UTF_8))
        val signedPart = "rdl1.$payload".toByteArray(Charsets.US_ASCII)
        val signature = Ed25519Signer().run {
            init(true, privateKey)
            update(signedPart, 0, signedPart.size)
            generateSignature()
        }

        return "rdl1.$payload.${Base64Url.encode(signature)}"
    }

    fun directoryToken(
        code: String = "482913",
        product: String = "fishino",
        challenge: String = "Zx81kd0QpLm2Vb7nQ4rT9wYe",
        name: String = "شرکت پیام نصر",
        url: String = "https://hrm.payamnasr.com/api/v1",
        type: String = DirectoryTokenVerifier.TOKEN_TYPE,
    ): String = issue(
        buildJsonObject {
            put("kid", "k20261018-abc123")
            put("typ", type)
            put("code", code)
            put("prd", product)
            put("name", name)
            put("url", url)
            put("chl", challenge)
            put("iat", 1_760_000_000)
        },
    )
}
