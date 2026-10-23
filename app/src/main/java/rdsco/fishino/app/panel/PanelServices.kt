package rdsco.fishino.app.panel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.SessionManager
import java.io.File

/**
 * Everything the signed-in screens need for one employer's Fishino: the
 * APIs, token handling and opening downloaded files.
 */
class PanelServices(
    val http: ApiClient,
    sessions: SessionManager,
    val files: SharedFiles,
) {
    val authed = Authed(sessions)
    val panel = PanelApi(http)
    val news = NewsApi(http)
    val requests = RequestApi(http)
    val profile = ProfileApi(http)
}

/**
 * Hands a downloaded file (PDF, attachment) to a viewer app through the
 * FileProvider. Files live in the cache's "shared" folder, which is emptied
 * on sign-out.
 */
class SharedFiles(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "shared")

    /** False when no installed app can open this type. */
    suspend fun open(bytes: ByteArray, fileName: String, mimeType: String): Boolean {
        val file = withContext(Dispatchers.IO) {
            directory.mkdirs()
            File(directory, safeName(fileName)).apply { writeBytes(bytes) }
        }

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            appContext.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** Keeps letters (Persian too), digits, dot, dash; never a path. */
        internal fun safeName(name: String): String {
            val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
                .trim('.', '_')
                .take(80)
            return cleaned.ifEmpty { "file" }
        }
    }
}
