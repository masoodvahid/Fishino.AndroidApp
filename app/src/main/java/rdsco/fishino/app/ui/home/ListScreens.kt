package rdsco.fishino.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PagedList

@Composable
fun DocumentsScreen(viewModel: HomeViewModel) {
    PagedListContent(viewModel.documents, stringResource(R.string.home_documents_empty)) { DocumentRow(it) }
}

@Composable
fun RequestsScreen(viewModel: HomeViewModel, onNewRequest: () -> Unit, onOpen: (Long) -> Unit) {
    PagedListContent(
        list = viewModel.requests,
        emptyText = stringResource(R.string.home_requests_empty),
        header = {
            item {
                Button(onClick = onNewRequest, shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.requests_new))
                }
            }
        },
    ) { request -> RequestRow(request) { onOpen(request.id) } }
}

/** A list loaded page by page, reloaded when shown and when pulled down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PagedListContent(
    list: PagedList<T>,
    emptyText: String,
    header: LazyListScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val state by list.state.collectAsStateWithLifecycle()
    LaunchedEffect(list) { list.reload() }

    PullToRefreshBox(isRefreshing = false, onRefresh = list::reload, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()
            items(state.items) { row(it) }

            item {
                when {
                    state.error != null -> LoadError(state.error, onRetry = list::loadMore)
                    state.loading -> Loading()
                    state.hasMore -> LaunchedEffect(state.items.size) { list.loadMore() }
                    state.items.isEmpty() -> EmptyText(emptyText)
                }
            }
        }
    }
}
