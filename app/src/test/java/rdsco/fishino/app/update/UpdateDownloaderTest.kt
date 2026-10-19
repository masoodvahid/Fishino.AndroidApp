package rdsco.fishino.app.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class UpdateDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val apk = ByteArray(200_000) { (it % 251).toByte() }
    private val apkSha256 = MessageDigest.getInstance("SHA-256").digest(apk).toHex()

    private fun release(url: String, size: Long = apk.size.toLong(), sha256: String? = apkSha256) = Release(
        version = AppVersion(0, 2, 0),
        tag = "v0.2.0",
        notes = "",
        apkUrl = url,
        apkSize = size,
        apkSha256 = sha256,
    )

    private fun serve(block: suspend (MockWebServer, UpdateDownloader) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(apk)))
            block(server, UpdateDownloader(OkHttpClient(), folder.root.resolve("updates"), "test"))
        }
    }

    @Test
    fun `a matching download is kept and reports progress`() = serve { server, downloader ->
        var lastProgress = 0f
        val result = downloader.download(release(server.url("/fishino.apk").toString())) { lastProgress = it }

        assertTrue(result is DownloadResult.Done)
        val file = (result as DownloadResult.Done).file
        assertEquals("fishino-0.2.0.apk", file.name)
        assertTrue(file.readBytes().contentEquals(apk))
        assertEquals(1f, lastProgress)
    }

    @Test
    fun `a wrong hash is rejected and nothing is left behind`() = serve { server, downloader ->
        val result = downloader.download(release(server.url("/fishino.apk").toString(), sha256 = "00".repeat(32))) {}

        assertEquals(DownloadResult.Corrupt, result)
        assertTrue(folder.root.resolve("updates").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a truncated download is rejected`() = serve { server, downloader ->
        val result = downloader.download(release(server.url("/fishino.apk").toString(), size = apk.size + 1L, sha256 = null)) {}

        assertEquals(DownloadResult.Corrupt, result)
    }

    @Test
    fun `http errors fail cleanly`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val downloader = UpdateDownloader(OkHttpClient(), folder.root.resolve("updates"), "test")

            assertEquals(DownloadResult.Failed, downloader.download(release(server.url("/missing.apk").toString())) {})
        }
    }

    @Test
    fun `hex encoding is lower case and locale independent`() {
        assertEquals("00ff10ab", byteArrayOf(0, -1, 16, -85).toHex())
    }
}
