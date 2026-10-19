package rdsco.fishino.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface DownloadResult {
    data class Done(val file: File) : DownloadResult
    /** Size or SHA-256 did not match what GitHub published. */
    data object Corrupt : DownloadResult
    data object Failed : DownloadResult
}

/**
 * Downloads a release APK into [directory] (cache/updates). Only the newest
 * download is kept. The file is checked against the size and SHA-256 that
 * GitHub publishes for the asset before it is handed to the installer.
 */
class UpdateDownloader(
    private val client: OkHttpClient,
    private val directory: File,
    private val userAgent: String,
) {

    suspend fun download(release: Release, onProgress: (Float) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }

        val target = File(directory, "fishino-${release.version}.apk")
        val partial = File(directory, "${target.name}.part")

        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "application/octet-stream")
            .build()

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L

            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@withContext DownloadResult.Failed

                val total = release.apkSize.takeIf { it > 0 } ?: body.contentLength()

                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break

                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read

                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            val sha256 = digest.digest().toHex()
            val sizeMatches = release.apkSize <= 0 || release.apkSize == written
            val hashMatches = release.apkSha256 == null || release.apkSha256 == sha256

            if (!sizeMatches || !hashMatches) {
                partial.delete()
                return@withContext DownloadResult.Corrupt
            }

            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext DownloadResult.Failed
            }

            DownloadResult.Done(target)
        } catch (e: IOException) {
            partial.delete()
            DownloadResult.Failed
        } catch (e: Throwable) {
            // Cancellation included: never leave a half-written APK behind.
            partial.delete()
            throw e
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }
}

private const val HEX = "0123456789abcdef"

// Not String.format: with a Persian locale it may print Persian digits.
internal fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and 0xff
        append(HEX[value ushr 4])
        append(HEX[value and 0x0f])
    }
}
