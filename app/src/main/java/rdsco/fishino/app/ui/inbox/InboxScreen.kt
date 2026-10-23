package rdsco.fishino.app.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.InboxMessage
import rdsco.fishino.app.panel.MessageLink
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.home.persianDigits

/** Messages from the employer (and request updates), as the web inbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel, onOpenLink: (MessageLink) -> Unit) {
    LaunchedEffect(Unit) { viewModel.reload() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }

    PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::reload, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (unread > 0) {
                item {
                    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.inbox_unread, unread).persianDigits(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::markAllRead) { Text(stringResource(R.string.inbox_read_all)) }
                    }
                }
            }

            items(state.items, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    expanded = expanded == message.id,
                    onClick = {
                        expanded = if (expanded == message.id) null else message.id
                        viewModel.markRead(message)
                    },
                    onOpenLink = onOpenLink,
                )
            }

            item {
                when {
                    state.error != null -> LoadError(state.error, onRetry = viewModel::loadMore)
                    state.loading -> Loading()
                    state.hasMore -> LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    state.items.isEmpty() -> EmptyText(stringResource(R.string.inbox_empty))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: InboxMessage, expanded: Boolean, onClick: () -> Unit, onOpenLink: (MessageLink) -> Unit) {
    PanelCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!message.isRead) {
                Box(Modifier.padding(end = 8.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            Text(
                message.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        message.dateLabel?.let {
            Text(it.persianDigits(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        if (message.body.isNotBlank()) {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        message.link?.let { link ->
            val label = when (link.type) {
                "request" -> R.string.inbox_open_request
                "report" -> R.string.inbox_open_document
                else -> null
            }
            if (label != null) {
                TextButton(onClick = { onOpenLink(link) }) { Text(stringResource(label)) }
            }
        }
    }
}
