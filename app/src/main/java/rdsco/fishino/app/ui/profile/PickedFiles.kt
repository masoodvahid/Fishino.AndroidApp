package rdsco.fishino.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.UploadPart
import java.io.ByteArrayOutputStream

/** Files the user picked, prepared for upload. */
object PickedFiles {

    /** The server accepts a profile photo of at most 256 KB. */
    private const val AVATAR_MAX_BYTES = 250 * 1024
    private const val AVATAR_MAX_SIDE = 900

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data object Unreadable : Result<Nothing>
        data class TooLarge(val maxKb: Int) : Result<Nothing>
        data object WrongFormat : Result<Nothing>
    }

    /**
     * A photo as a JPEG under the server's limit: turned upright, scaled
     * down, quality lowered until it fits.
     */
    suspend fun avatarJpeg(context: Context, uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?.use { ApiClient.readAtMost(it, 25L * 1024 * 1024) }
            ?: return@withContext Result.Unreadable

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return@withContext Result.WrongFormat

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext Result.WrongFormat

        val upright = rotate(decoded, orientation(bytes))
        val scale = AVATAR_MAX_SIDE.toFloat() / maxOf(upright.width, upright.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true) else upright

        var quality = 90
        var output: ByteArray
        do {
            output = ByteArrayOutputStream().use { stream ->
                sized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
            quality -= 10
        } while (output.size > AVATAR_MAX_BYTES && quality >= 30)

        if (output.size > AVATAR_MAX_BYTES) Result.TooLarge(256) else Result.Ok(output)
    }

    /** A document as picked, if its extension is allowed and it fits [maxKb]. */
    suspend fun document(context: Context, uri: Uri, formats: List<String>, maxKb: Int): Result<UploadPart> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

        val extension = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
            ?: return@withContext Result.WrongFormat
        val normalized = if (extension == "jpeg" && "jpeg" !in formats && "jpg" in formats) "jpg" else extension
        if (formats.isNotEmpty() && normalized !in formats) return@withContext Result.WrongFormat

        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return@withContext Result.Unreadable
        val bytes = stream.use { ApiClient.readAtMost(it, maxKb * 1024L) } ?: return@withContext Result.TooLarge(maxKb)

        Result.Ok(UploadPart("file", "document.$normalized", mime, bytes))
    }

    private fun orientation(bytes: ByteArray): Int =
        runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
