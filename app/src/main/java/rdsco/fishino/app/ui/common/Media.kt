package rdsco.fishino.app.ui.common

import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.text.method.LinkMovementMethod
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.PanelServices

/** The signed-in user's services; provided by the app shell. */
val LocalPanel = staticCompositionLocalOf<PanelServices> { error("No PanelServices provided") }

/**
 * Images that need the user's token (news covers, profile photo, …), kept
 * in memory only and dropped on sign-out ([clear]).
 */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    operator fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, image: ImageBitmap) {
        cache.put(key, image)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() = cache.evictAll()

    /** Decodes at most about [maxSide] pixels on the longer side. */
    fun decode(bytes: ByteArray, maxSide: Int = 1600): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/**
 * An image from the employer's Fishino, loaded with the user's token.
 * [version] changes the cache key after the user replaced the image.
 */
@Composable
fun AuthedImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    version: Int = 0,
    placeholder: @Composable () -> Unit = { Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) },
) {
    val services = LocalPanel.current
    val key = "${services.http.hashCode()}|$path|$version"
    var image by remember(key) { mutableStateOf(ImageCache[key]) }

    LaunchedEffect(key) {
        if (image != null) return@LaunchedEffect
        val result = services.authed.call { token -> services.http.download(path, token, maxBytes = 10L * 1024 * 1024) }
        if (result is ApiResult.Ok && result.value.contentType.startsWith("image/")) {
            withContext(Dispatchers.Default) { ImageCache.decode(result.value.bytes) }?.let {
                ImageCache.put(key, it)
                image = it
            }
        }
    }

    val loaded = image
    if (loaded == null) {
        placeholder()
    } else {
        Image(bitmap = loaded, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    }
}

/**
 * HTML written by the employer's admins (news, request text, agreement),
 * shown as styled text. Images inside it are left out; links open in the
 * browser. No script runs: it is a TextView, not a web view.
 */
@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, sizeSp: Float = 15f) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                typeface = ResourcesCompat.getFont(context, R.font.vazirmatn_regular)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                setLineSpacing(0f, 1.45f)
                textDirection = View.TEXT_DIRECTION_ANY_RTL
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.setLinkTextColor(linkColor)
            view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT, { ColorDrawable(0).apply { setBounds(0, 0, 0, 0) } }, null)
                .trim()
        },
    )
}
