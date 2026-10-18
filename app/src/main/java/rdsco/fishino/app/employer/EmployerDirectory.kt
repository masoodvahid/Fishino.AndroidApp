package rdsco.fishino.app.employer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom

sealed interface ResolveResult {
    data class Found(val employer: Employer) : ResolveResult
    data object NotFound : ResolveResult
    data object Inactive : ResolveResult
    data class RateLimited(val retryAfterSeconds: Long) : ResolveResult
    /** Answer arrived but its signature or contents did not check out. */
    data object Untrusted : ResolveResult
    data object NetworkError : ResolveResult
    data object ServerError : ResolveResult
}

/**
 * Asks the license manager (سامانه مدیریت راهکار دیجیتال شریف) which
 * Fishino server belongs to an employer code: `POST {directoryUrl}app/resolve`.
 */
class EmployerDirectory(
    private val client: OkHttpClient,
    private val directoryUrl: String,
    private val productSlug: String,
    private val verifier: DirectoryTokenVerifier,
    private val random: SecureRandom = SecureRandom(),
) {

    suspend fun resolve(code: String): ResolveResult = withContext(Dispatchers.IO) {
        val challenge = newChallenge()
        val body = buildJsonObject {
            put("code", code)
            put("product", productSlug)
            put("challenge", challenge)
        }.toString()

        val request = Request.Builder()
            .url(directoryUrl + "app/resolve")
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                interpret(
                    status = response.code,
                    retryAfterHeader = response.header("Retry-After"),
                    body = response.body?.string().orEmpty(),
                    code = code,
                    challenge = challenge,
                )
            }
        } catch (e: IOException) {
            ResolveResult.NetworkError
        }
    }

    internal fun interpret(status: Int, retryAfterHeader: String?, body: String, code: String, challenge: String): ResolveResult {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val errorCode = json?.objectAt("error")?.stringAt("code")

        return when {
            status == 200 -> {
                val token = json?.objectAt("data")?.stringAt("token") ?: return ResolveResult.Untrusted
                verifier.verify(token, code, productSlug, challenge)
                    ?.let { ResolveResult.Found(it) }
                    ?: ResolveResult.Untrusted
            }
            status == 404 && errorCode == "code_not_found" -> ResolveResult.NotFound
            status == 422 && errorCode == "validation_failed" -> ResolveResult.NotFound
            status == 403 && errorCode == "employer_inactive" -> ResolveResult.Inactive
            status == 429 -> ResolveResult.RateLimited(retryAfterHeader?.trim()?.toLongOrNull()?.coerceAtLeast(1) ?: 60)
            else -> ResolveResult.ServerError
        }
    }

    private fun newChallenge(): String = Base64Url.encode(ByteArray(24).also(random::nextBytes))

    private fun JsonObject.objectAt(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

    private fun JsonObject.stringAt(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
