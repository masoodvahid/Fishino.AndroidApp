package rdsco.fishino.app.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesTest {

    private val repo = "masoodvahid/Fishino.AndroidApp"
    private val officialPattern = "^fishino-[0-9][0-9.]*[.]apk$"
    private val testPattern = "^fishino-test-[0-9][0-9.]*[.]apk$"
    private val releases = GitHubReleases(OkHttpClient(), repo, officialPattern, "test")

    private fun asset(
        name: String = "fishino-0.2.0.apk",
        url: String = "https://github.com/$repo/releases/download/v0.2.0/$name",
        digest: String? = "sha256:" + "ab".repeat(32),
        state: String = "uploaded",
    ) = """{"name":"$name","state":"$state","size":5242880,"browser_download_url":"$url"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}}"""

    private fun release(assets: String, tag: String = "v0.2.0", prerelease: Boolean = false) =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"- رفع خطا\n","assets":[$assets]}"""

    @Test
    fun `the apk of the latest release is found`() {
        val result = releases.interpret(release(asset()))

        assertTrue(result is ReleaseLookup.Found)
        val found = (result as ReleaseLookup.Found).release
        assertEquals(AppVersion(0, 2, 0), found.version)
        assertEquals("https://github.com/$repo/releases/download/v0.2.0/fishino-0.2.0.apk", found.apkUrl)
        assertEquals(5_242_880L, found.apkSize)
        assertEquals("ab".repeat(32), found.apkSha256)
        assertEquals("- رفع خطا", found.notes)
    }

    @Test
    fun `the fishino apk is preferred and other files are ignored`() {
        val result = releases.interpret(release(asset(name = "notes.txt") + "," + asset(name = "other.apk") + "," + asset(name = "fishino-0.2.0.apk")))

        assertTrue((result as ReleaseLookup.Found).release.apkUrl.endsWith("/fishino-0.2.0.apk"))
    }

    @Test
    fun `test builds are never offered to the official app`() {
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release(asset(name = "fishino-test-0.2.0.apk"))))
        assertTrue(releases.interpret(release(asset(name = "fishino-test-0.2.0.apk") + "," + asset())) is ReleaseLookup.Found)
    }

    @Test
    fun `the test build only takes the test apk`() {
        val testReleases = GitHubReleases(OkHttpClient(), repo, testPattern, "test")

        assertEquals(ReleaseLookup.NoApk, testReleases.interpret(release(asset(name = "fishino-0.2.0.apk"))))
        val found = testReleases.interpret(release(asset(name = "fishino-0.2.0.apk") + "," + asset(name = "fishino-test-0.2.0.apk")))
        assertTrue((found as ReleaseLookup.Found).release.apkUrl.endsWith("/fishino-test-0.2.0.apk"))
    }

    @Test
    fun `missing digest is allowed`() {
        val result = releases.interpret(release(asset(digest = null)))

        assertEquals(null, (result as ReleaseLookup.Found).release.apkSha256)
    }

    @Test
    fun `a release without a finished apk has nothing to install`() {
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release("")))
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release(asset(state = "starter"))))
    }

    @Test
    fun `downloads from anywhere but this repository's releases are refused`() {
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release(asset(url = "https://evil.example.com/fishino.apk"))))
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release(asset(url = "http://github.com/$repo/releases/download/v0.2.0/fishino.apk"))))
        assertEquals(ReleaseLookup.NoApk, releases.interpret(release(asset(url = "https://github.com/someone/else/releases/download/v0.2.0/fishino.apk"))))
    }

    @Test
    fun `bad tags and pre-releases are not offered`() {
        assertEquals(ReleaseLookup.Failed, releases.interpret(release(asset(), tag = "nightly")))
        assertEquals(ReleaseLookup.NoRelease, releases.interpret(release(asset(), prerelease = true)))
        assertEquals(ReleaseLookup.Failed, releases.interpret("<html>"))
    }

    @Test
    fun `http answers map to results`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(asset())))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"API rate limit exceeded"}"""))
            val client = GitHubReleases(OkHttpClient(), repo, officialPattern, "Fishino-Android/0.1.0", server.url("/").toString())

            assertTrue(client.latest() is ReleaseLookup.Found)
            assertEquals(ReleaseLookup.NoRelease, client.latest())
            assertEquals(ReleaseLookup.Failed, client.latest())

            val request = server.takeRequest()
            assertEquals("/repos/$repo/releases/latest", request.path)
            assertEquals("Fishino-Android/0.1.0", request.getHeader("User-Agent"))
        }
    }

    @Test
    fun `an invalid repository disables updates`() {
        assertFalse(GitHubReleases(OkHttpClient(), "", officialPattern, "test").enabled)
        assertFalse(GitHubReleases(OkHttpClient(), "not a repo", officialPattern, "test").enabled)
        assertFalse(GitHubReleases(OkHttpClient(), repo, "", "test").enabled)
        assertTrue(releases.enabled)
    }
}
