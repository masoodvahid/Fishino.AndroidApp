package rdsco.fishino.app.ui.documents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.requests.RequirementOrError
import java.net.URI

/**
 * The document itself, as on the web panel, without building or
 * downloading a PDF. White paper in dark mode too; pinch to zoom.
 */
@Composable
fun DocumentViewerScreen(viewModel: DocumentViewModel, id: Long, apiBaseUrl: String, onOpenProfileSection: (String) -> Unit) {
    LaunchedEffect(id) { viewModel.page.load(id) }
    val state by viewModel.page.state.collectAsStateWithLifecycle()
    val html = state.data

    when {
        html != null -> DocumentPage(html, siteRoot(apiBaseUrl), Modifier.fillMaxSize())
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Loading() }
        else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            RequirementOrError(state, onRetry = viewModel.page::reload, onOpenProfileSection = onOpenProfileSection)
        }
    }
}

/**
 * A web view with no script, no file access and no navigation: links open
 * in the browser. Fonts and images come from the employer's own site.
 */
@Composable
private fun DocumentPage(html: String, baseUrl: String?, modifier: Modifier) {
    AndroidView(
        modifier = modifier.background(androidx.compose.ui.graphics.Color.White),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.WHITE)
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    // Laid out like the paper page, then fitted to the screen.
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (request.isForMainFrame && (url.scheme == "https" || url.scheme == "http")) {
                            try {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                            } catch (e: ActivityNotFoundException) {
                                // No browser: stay on the document.
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}

/** https://host/ of the employer's API address: the page's own site. */
private fun siteRoot(apiBaseUrl: String): String? = runCatching {
    val uri = URI(apiBaseUrl)
    if (uri.scheme == null || uri.rawAuthority == null) null else "${uri.scheme}://${uri.rawAuthority}/"
}.getOrNull()
