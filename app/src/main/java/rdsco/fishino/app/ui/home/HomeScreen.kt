package rdsco.fishino.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.DocumentItem
import rdsco.fishino.app.panel.HomeData
import rdsco.fishino.app.panel.ProfileApi
import rdsco.fishino.app.panel.RequestItem
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.Banner
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.common.tone

/** Where the home screen sends the user. */
class HomeActions(
    val openDocuments: () -> Unit,
    val openDocument: (Long) -> Unit,
    val openRequests: () -> Unit,
    val openRequest: (Long) -> Unit,
    val newRequest: () -> Unit,
    val openNews: () -> Unit,
    val openNewsItem: (Long) -> Unit,
    /** A profile section ("mobile", "signature", …) where a pending requirement is done. */
    val openProfileSection: (String) -> Unit,
)

/**
 * After sign-in: the latest announcements, a welcome, what the user must do
 * first, and the latest documents and requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: HomeActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Fresh data each time the home screen is shown (sign-in, back from another screen).
    LaunchedEffect(Unit) { viewModel.refresh() }

    val data = state.data

    PullToRefreshBox(
        isRefreshing = state.loading && data != null,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                data != null -> homeSections(data, actions)
                state.loading -> item { Loading() }
                else -> item { LoadError(state.error, onRetry = viewModel::refresh) }
            }

            // A refresh that failed while older data is on screen.
            if (data != null && state.error != null && !state.loading) {
                item { LoadError(state.error, onRetry = viewModel::refresh) }
            }
        }
    }
}

private fun LazyListScope.homeSections(data: HomeData, actions: HomeActions) {
    if (data.latestNews.isNotEmpty()) {
        item { NewsSlideshow(data.latestNews, onOpen = actions.openNewsItem, onOpenAll = actions.openNews) }
    }

    item {
        Text(
            stringResource(R.string.home_welcome, data.profile.fullName.ifBlank { data.profile.username }),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
        )
    }

    data.requirement?.let { requirement ->
        item {
            Banner(
                title = requirement.title,
                text = requirement.message,
                tone = Tones.Warning,
                actionLabel = requirement.tab?.let { stringResource(R.string.home_requirement_action) },
                onAction = requirement.tab?.let { tab -> { actions.openProfileSection(tab) } },
            )
        }
    }

    if (data.pendingSignatures > 0) {
        item { Banner(text = stringResource(R.string.home_pending_signatures, data.pendingSignatures).persianDigits(), tone = Tones.Info) }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.home_documents),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = if (data.recentDocuments.isNotEmpty()) actions.openDocuments else null,
        )
    }
    if (data.recentDocuments.isEmpty()) {
        item { EmptyText(stringResource(if (data.requirement != null) R.string.home_documents_hidden else R.string.home_documents_empty)) }
    } else {
        items(data.recentDocuments, key = { "document-${it.id}" }) { document -> DocumentRow(document) { actions.openDocument(document.id) } }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.home_requests),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = actions.openRequests,
        )
    }
    if (data.recentRequests.isEmpty()) {
        // While the profile is incomplete (mobile number, first password) requests are hidden too.
        val hidden = data.requirement?.key in setOf("mobile", "password")
        item { EmptyText(stringResource(if (hidden) R.string.home_documents_hidden else R.string.home_requests_empty)) }
    } else {
        items(data.recentRequests, key = { "request-${it.id}" }) { request -> RequestRow(request) { actions.openRequest(request.id) } }
    }

    item {
        OutlinedButton(
            onClick = actions.newRequest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.requests_new))
        }
    }
}

/** The profile photo, or the first letter of the name until one is uploaded. */
@Composable
fun Avatar(name: String, version: Int, modifier: Modifier = Modifier) {
    val initial: @Composable () -> Unit = {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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

@Composable
fun DocumentRow(document: DocumentItem, onClick: () -> Unit) {
    PanelCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    document.title.persianDigits(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (document.templateTitle.isNotBlank() && !document.title.startsWith(document.templateTitle)) {
                    Text(document.templateTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                document.needsSignature -> Chip(stringResource(R.string.home_document_needs_signature), Tones.Warning, Modifier.padding(start = 8.dp))
                document.signed -> Chip(stringResource(R.string.home_document_signed), Tones.Success, Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun RequestRow(request: RequestItem, onClick: () -> Unit) {
    PanelCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                request.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Chip(request.statusLabel, request.status.tone(), Modifier.padding(start = 8.dp))
        }
        Text(
            listOfNotNull(
                request.trackingCode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_request_tracking, it) },
                request.updatedLabel,
            ).joinToString(" · ").persianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        request.comment?.let {
            Text(
                stringResource(R.string.home_request_comment, it),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
