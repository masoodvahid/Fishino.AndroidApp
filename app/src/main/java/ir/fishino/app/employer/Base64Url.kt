package ir.fishino.app.employer

import org.bouncycastle.util.encoders.Base64

/**
 * Unpadded base64url, as used by the license manager's tokens. Uses
 * BouncyCastle instead of java.util.Base64 so it works below API 26 and in
 * plain JVM unit tests.
 */
internal object Base64Url {
    fun encode(bytes: ByteArray): String =
        Base64.toBase64String(bytes).trimEnd('=').replace('+', '-').replace('/', '_')

    fun decode(value: String): ByteArray? {
        if (value.any { !(it.isLetterOrDigit() && it.code < 128) && it != '-' && it != '_' }) return null

        val standard = value.replace('-', '+').replace('_', '/')
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)

        return runCatching { Base64.decode(padded) }.getOrNull()
    }
}
