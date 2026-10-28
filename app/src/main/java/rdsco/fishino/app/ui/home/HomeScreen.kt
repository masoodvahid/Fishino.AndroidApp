package rdsco.fishino.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.DocumentItem
import rdsco.fishino.app.panel.FormSummary
import rdsco.fishino.app.panel.HomeData
import rdsco.fishino.app.panel.ProfileApi
import rdsco.fishino.app.panel.RequestItem
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.Banner
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.FlatIcon
import rdsco.fishino.app.ui.common.GlassPanel
import rdsco.fishino.app.ui.common.Loadable
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.RequestIcon
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.requests.RequestsViewModel
import rdsco.fishino.app.ui.requests.RequirementOrError

/** Navigation always uses the actual IDs returned by the active employer. */
class HomeActions(
    val openDocuments: () -> Unit,
    val openDocument: (Long) -> Unit,
    val openRequests: () -> Unit,
    val openRequest: (Long) -> Unit,
    val newRequest: () -> Unit,
    val openForm: (Long) -> Unit,
    val openNewsItem: (Long) -> Unit,
    val openProfileSection: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    requestsViewModel: RequestsViewModel,
    appVersion: String,
    actions: HomeActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val forms by requestsViewModel.forms.state.collectAsStateWithLifecycle()
    val data = state.data
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(data?.profile?.username, data?.requirement?.key) {
        if (data != null && data.requirement?.key !in setOf("mobile", "password")) {
            requestsViewModel.forms.load(Unit, force = true)
        }
    }

    PullToRefreshBox(
        isRefreshing = state.loading && data != null,
        onRefresh = {
            viewModel.refresh()
            if (data != null && data.requirement?.key !in setOf("mobile", "password")) {
                requestsViewModel.forms.load(Unit, force = true)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                data != null -> homeSections(data, forms, actions, requestsViewModel.forms::reload)
                state.loading -> item { Loading() }
                else -> item { LoadError(state.error, onRetry = viewModel::refresh) }
            }
            if (data != null && state.error != null && !state.loading) {
                item { LoadError(state.error, onRetry = viewModel::refresh) }
            }
            item {
                Text(
                    stringResource(R.string.home_footer_version, appVersion.persianDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private fun LazyListScope.homeSections(
    data: HomeData,
    forms: Loadable<List<FormSummary>>,
    actions: HomeActions,
    retryForms: () -> Unit,
) {
    // No invented sample announcement if the employer has not published any news.
    if (data.latestNews.isNotEmpty()) {
        item("news") {
            NewsSlideshow(data.latestNews.take(3), onOpen = actions.openNewsItem)
        }
    }
    data.requirement?.let { requirement ->
        item("requirement") {
            Banner(
                title = requirement.title,
                text = requirement.message,
                tone = Tones.Warning,
                actionLabel = requirement.tab?.let { stringResource(R.string.home_requirement_action) },
                onAction = requirement.tab?.let { tab -> { actions.openProfileSection(tab) } },
            )
        }
    }
    item("documents") {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
            SectionHeader(stringResource(R.string.home_documents), stringResource(R.string.home_see_all), actions.openDocuments)
            GlassPanel(Modifier.fillMaxWidth()) {
                val recent = data.recentDocuments.take(3)
                if (recent.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        EmptyText(stringResource(if (data.requirement != null) R.string.home_documents_hidden else R.string.home_documents_empty))
                    }
                }
                recent.forEachIndexed { index, document ->
                    DocumentContent(document) { actions.openDocument(document.id) }
                    if (index < recent.lastIndex) ListDivider()
                }
            }
        }
    }
    item("requests") {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
            SectionHeader(stringResource(R.string.home_requests), stringResource(R.string.home_see_all), actions.openRequests)
            GlassPanel(Modifier.fillMaxWidth()) {
                val recent = data.recentRequests.take(3)
                if (recent.isEmpty()) {
                    val hidden = data.requirement?.key in setOf("mobile", "password")
                    Box(Modifier.padding(16.dp)) {
                        EmptyText(stringResource(if (hidden) R.string.home_documents_hidden else R.string.home_requests_empty))
                    }
                }
                recent.forEachIndexed { index, request ->
                    RequestContent(request) { actions.openRequest(request.id) }
                    if (index < recent.lastIndex) ListDivider()
                }
            }
        }
    }
    if (data.requirement?.key !in setOf("mobile", "password")) {
        item("new-request") {
            RequestShortcuts(forms, retryForms, actions)
        }
    }
}

@Composable
private fun RequestShortcuts(state: Loadable<List<FormSummary>>, retry: () -> Unit, actions: HomeActions) {
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_create_request),
            actionLabel = if (state.data.orEmpty().size > 4) stringResource(R.string.home_see_all) else null,
            onAction = if (state.data.orEmpty().size > 4) actions.newRequest else null,
        )
        when {
            state.data != null -> {
                val forms = state.data
                if (forms.isEmpty()) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp)) { EmptyText(stringResource(R.string.requests_no_forms)) }
                    }
                }
                forms.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { form ->
                            GlassPanel(
                                modifier = Modifier.weight(1f),
                                onClick = { actions.openForm(form.id) },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 12.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RequestIcon(requestIllustration(form.title))
                                    Text(
                                        form.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (state.error != null) {
                    RequirementOrError(state, retry, actions.openProfileSection)
                }
            }
            state.loading -> Loading()
            else -> RequirementOrError(state, retry, actions.openProfileSection)
        }
    }
}

@Composable
private fun ListDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Shared by the compact home list and the complete documents list. */
@Composable
private fun DocumentContent(document: DocumentItem, onClick: () -> Unit) {
    val status = stringResource(when {
        document.needsSignature -> R.string.home_document_needs_signature
        document.signed -> R.string.home_document_signed
        else -> R.string.home_document_new
    })
    BoxWithConstraints {
    val inlineStatus = maxWidth >= 350.dp && LocalDensity.current.fontScale <= 1.15f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlatIcon(R.drawable.ic_flat_document)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                document.title.persianDigits(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (document.dateLabel.isNotBlank()) {
                Text(document.dateLabel.persianDigits(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (document.templateTitle.isNotBlank() && !document.title.startsWith(document.templateTitle)) {
                Text(document.templateTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!inlineStatus) Chip(status, Tones.Neutral)
        }
        if (inlineStatus) Chip(status, Tones.Neutral, Modifier.widthIn(max = 96.dp))
        Icon(painterResource(R.drawable.ic_flat_chevron), contentDescription = null, modifier = Modifier.size(24.dp))
    }
    }
}

@Composable
private fun RequestContent(request: RequestItem, onClick: () -> Unit) {
    BoxWithConstraints {
    val inlineStatus = maxWidth >= 350.dp && LocalDensity.current.fontScale <= 1.15f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RequestIcon(requestIllustration(request.title))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(request.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                request.updatedLabel?.takeIf { it.isNotBlank() },
                request.trackingCode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_request_tracking, it) },
            ).joinToString(" · ").persianDigits()
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!inlineStatus) Chip(request.statusLabel, Tones.Neutral)
            request.comment?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.home_request_comment, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (inlineStatus) Chip(request.statusLabel, Tones.Neutral, Modifier.widthIn(max = 96.dp))
        Icon(painterResource(R.drawable.ic_flat_chevron), contentDescription = null, modifier = Modifier.size(24.dp))
    }
    }
}

@Composable
fun DocumentRow(document: DocumentItem, onClick: () -> Unit) {
    GlassPanel(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) { DocumentContent(document, onClick) }
}

@Composable
fun RequestRow(request: RequestItem, onClick: () -> Unit) {
    GlassPanel(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) { RequestContent(request, onClick) }
}

/** The profile photo, or the first letter of the name until one is uploaded. */
@Composable
fun Avatar(name: String, version: Int, modifier: Modifier = Modifier) {
    val initial: @Composable () -> Unit = {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    AuthedImage(
        path = ProfileApi.AVATAR_PATH,
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
        version = version,
        placeholder = initial,
    )
}
