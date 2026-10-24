package rdsco.fishino.app.employer

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.AuthApi
import java.io.File

/** A logo as downloaded, before it is kept: shown on the confirm screen. */
class LogoCandidate(val bytes: ByteArray, val version: String, val image: ImageBitmap)

/**
 * The employer's logo, as on its web panel (GET /auth/logo), kept on the
 * phone for the header and the menu. [version] (from /auth/config) changes
 * when the admin uploads a new one; servers without it simply have no logo.
 */
class EmployerLogo(context: Context, private val api: (apiBaseUrl: String) -> ApiClient) {

    private val directory = File(context.applicationContext.filesDir, "employer")
    private val imageFile = File(directory, "logo.png")
    private val versionFile = File(directory, "logo.version")
    private val lock = Mutex()

    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    /** Shows the kept logo; call once at start. */
    suspend fun load() = lock.withLock {
        _image.value = withContext(Dispatchers.IO) { runCatching { decode(imageFile.readBytes()) }.getOrNull() }
    }

    /** The logo of an employer that is not saved yet (confirm screen). */
    suspend fun fetch(apiBaseUrl: String): LogoCandidate? {
        val config = AuthApi(api(apiBaseUrl)).config() as? ApiResult.Ok ?: return null
        val version = config.value.logoVersion ?: return null
        return download(apiBaseUrl, version)
    }

    /** Keeps the logo the user saw when confirming the employer. */
    suspend fun keep(candidate: LogoCandidate?) = lock.withLock {
        if (candidate == null) clearFiles() else write(candidate)
    }

    /**
     * Follows what the employer's server publishes now ([version] null: no
     * logo). Downloads only when the version changed.
     */
    suspend fun sync(apiBaseUrl: String, version: String?) {
        if (version == null) {
            lock.withLock { clearFiles() }
            return
        }
        val kept = withContext(Dispatchers.IO) { runCatching { versionFile.readText() }.getOrNull() }
        if (kept == version && _image.value != null) return

        val candidate = download(apiBaseUrl, version) ?: return
        lock.withLock { write(candidate) }
    }

    suspend fun clear() = lock.withLock { clearFiles() }

    private suspend fun download(apiBaseUrl: String, version: String): LogoCandidate? {
        val result = api(apiBaseUrl).download(LOGO_PATH, token = null, maxBytes = MAX_BYTES) as? ApiResult.Ok ?: return null
        val image = withContext(Dispatchers.Default) { decode(result.value.bytes) } ?: return null
        return LogoCandidate(result.value.bytes, version, image)
    }

    private suspend fun write(candidate: LogoCandidate) {
        withContext(Dispatchers.IO) {
            runCatching {
                directory.mkdirs()
                imageFile.writeBytes(candidate.bytes)
                versionFile.writeText(candidate.version)
            }
        }
        _image.value = candidate.image
    }

    private suspend fun clearFiles() {
        withContext(Dispatchers.IO) {
            imageFile.delete()
            versionFile.delete()
        }
        _image.value = null
    }

    private companion object {
        const val LOGO_PATH = "/auth/logo"
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_SIDE = 512

        fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}
