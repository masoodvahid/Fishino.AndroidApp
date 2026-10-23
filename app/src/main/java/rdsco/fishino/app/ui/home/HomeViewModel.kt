package rdsco.fishino.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.DocumentItem
import rdsco.fishino.app.panel.HomeData
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.panel.RequestItem
import rdsco.fishino.app.ui.common.PagedList
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

data class HomeUiState(
    val data: HomeData? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
)

/** The home screen and the full lists of documents and requests. */
class HomeViewModel(private val services: PanelServices) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val documents = PagedList<DocumentItem>(viewModelScope, services.authed) { token, page -> services.panel.documents(token, page) }

    val requests = PagedList<RequestItem>(viewModelScope, services.authed) { token, page -> services.panel.requests(token, page) }

    fun refresh() {
        if (_state.value.loading) return

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = services.authed.call { token -> services.panel.home(token) }) {
                is ApiResult.Ok -> {
                    services.unread.value = result.value.unreadNotifications
                    _state.update { it.copy(data = result.value, loading = false) }
                }
                is ApiResult.Failed -> _state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }
}
