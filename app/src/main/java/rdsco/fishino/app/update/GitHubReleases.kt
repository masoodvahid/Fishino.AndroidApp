package rdsco.fishino.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The APK of the newest published release. */
data class Release(
    val version: AppVersion,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 published by GitHub for the asset, if any. */
    val apkSha256: String?,
)

sealed interface ReleaseLookup {
    data class Found(val release: Release) : ReleaseLookup
    /** The latest release has no usable APK (yet: CI may still be building it). */
    data object NoApk : ReleaseLookup
    data object NoRelease : ReleaseLookup
    /** Network error, GitHub rate limit or an unexpected answer. */
    data object Failed : ReleaseLookup
}

/**
 * Reads the latest published release (drafts and pre-releases excluded by
 * GitHub) of a public repository: `GET /repos/{owner}/{repo}/releases/latest`.
 * Unauthenticated, so GitHub allows 60 lookups per hour per IP address.
 */
class GitHubReleases(
    private val client: OkHttpClient,
    private val repository: String,
    private val userAgent: String,
    private val apiBaseUrl: String = "https://api.github.com/",
) {

    val enabled: Boolean get() = REPOSITORY.matches(repository)

    suspend fun latest(): ReleaseLookup = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext ReleaseLookup.Failed

        val request = Request.Builder()
            .url("${apiBaseUrl}repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> interpret(response.body?.string().orEmpty())
                    404 -> ReleaseLookup.NoRelease
                    else -> ReleaseLookup.Failed
                }
            }
        } catch (e: IOException) {
            ReleaseLookup.Failed
        }
    }

    internal fun interpret(body: String): ReleaseLookup {
        val release = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return ReleaseLookup.Failed

        if (release.bool("draft") == true || release.bool("prerelease") == true) return ReleaseLookup.NoRelease

        val tag = release.string("tag_name") ?: return ReleaseLookup.Failed
        val version = AppVersion.parse(tag) ?: return ReleaseLookup.Failed

        val assets = runCatching { release["assets"]?.jsonArray?.map { it.jsonObject } }.getOrNull().orEmpty()
        val apk = assets
            .filter { it.string("name")?.endsWith(".apk", ignoreCase = true) == true && it.string("state").let { state -> state == null || state == "uploaded" } }
            .sortedByDescending { it.string("name")?.startsWith("fishino", ignoreCase = true) == true }
            .firstOrNull()
            ?: return ReleaseLookup.NoApk

        val url = apk.string("browser_download_url")?.takeIf(::isGitHubDownload) ?: return ReleaseLookup.NoApk
        val sha256 = apk.string("digest")
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?.takeIf { SHA256.matches(it) }

        return ReleaseLookup.Found(
            Release(
                version = version,
                tag = tag,
                notes = release.string("body")?.trim().orEmpty(),
                apkUrl = url,
                apkSize = apk.long("size") ?: 0,
                apkSha256 = sha256,
            ),
        )
    }

    private fun isGitHubDownload(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && parsed.host == "github.com" && parsed.encodedPath.startsWith("/$repository/releases/download/")
    }

    private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

    private fun JsonObject.long(key: String): Long? = runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()

    private companion object {
        val REPOSITORY = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
