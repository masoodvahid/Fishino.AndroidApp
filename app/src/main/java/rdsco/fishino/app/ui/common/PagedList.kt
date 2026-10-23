package rdsco.fishino.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.Authed
import rdsco.fishino.app.panel.Page
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

data class ListUiState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: UiText? = null,
)

/** A list loaded page by page with the current session's token. */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val authed: Authed,
    private val load: suspend (token: String, page: Int) -> ApiResult<Page<T>>,
) {
    private val _state = MutableStateFlow(ListUiState<T>())
    val state: StateFlow<ListUiState<T>> = _state.asStateFlow()
    private var job: Job? = null

    /** From the first page, e.g. when the list is opened or pulled to refresh. */
    fun reload() {
        job?.cancel()
        _state.value = ListUiState()
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return

        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            when (val result = authed.call { token -> load(token, current.page + 1) }) {
                is ApiResult.Ok -> _state.update {
                    val page = result.value
                    it.copy(
                        // A new item at the top can push one onto the next page: no duplicates.
                        items = (it.items + page.items).distinct(),
                        page = page.page,
                        hasMore = page.hasMore && page.items.isNotEmpty(),
                        loading = false,
                    )
                }
                is ApiResult.Failed -> _state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }
}
