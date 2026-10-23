package rdsco.fishino.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.InboxMessage
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.ui.common.ListUiState
import rdsco.fishino.app.ui.login.toUiText

/** The inbox: messages newest first, read marks, the unread count. */
class InboxViewModel(private val services: PanelServices) : ViewModel() {

    private val _state = MutableStateFlow(ListUiState<InboxMessage>())
    val state: StateFlow<ListUiState<InboxMessage>> = _state.asStateFlow()

    val unread: StateFlow<Int> = services.unread.asStateFlow()

    private var job: Job? = null

    fun reload() {
        job?.cancel()
        _state.value = ListUiState()
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return

        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val before = current.items.lastOrNull()?.id
            when (val result = services.authed.call { token -> services.inbox.list(token, before) }) {
                is ApiResult.Ok -> {
                    services.unread.value = result.value.unread
                    _state.update {
                        it.copy(
                            items = (it.items + result.value.items).distinctBy { message -> message.id },
                            hasMore = result.value.hasMore && result.value.items.isNotEmpty(),
                            loading = false,
                        )
                    }
                }
                is ApiResult.Failed -> _state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }

    /** Opening a message marks it read (on the server too). */
    fun markRead(message: InboxMessage) {
        if (message.isRead) return
        _state.update { state -> state.copy(items = state.items.map { if (it.id == message.id) it.copy(isRead = true) else it }) }
        services.unread.update { (it - 1).coerceAtLeast(0) }

        viewModelScope.launch {
            val result = services.authed.call { token -> services.inbox.markRead(token, message.id) }
            if (result is ApiResult.Ok) services.unread.value = result.value
        }
    }

    fun markAllRead() {
        _state.update { state -> state.copy(items = state.items.map { it.copy(isRead = true) }) }
        services.unread.value = 0

        viewModelScope.launch {
            services.authed.call { token -> services.inbox.markAllRead(token) }
        }
    }
}
