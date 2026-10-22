package rdsco.fishino.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.CredentialVault
import rdsco.fishino.app.auth.LoginPrefs
import rdsco.fishino.app.auth.SessionManager
import rdsco.fishino.app.panel.DocumentItem
import rdsco.fishino.app.panel.HomeData
import rdsco.fishino.app.panel.Page
import rdsco.fishino.app.panel.PanelApi
import rdsco.fishino.app.panel.RequestItem
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

data class HomeUiState(
    val data: HomeData? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    /** Fingerprint sign-in: shown only on phones that have a strong biometric. */
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricDeclined: Boolean = false,
    /** The username typed at the last password login: fingerprint sign-in is saved for it. */
    val loginName: String = "",
)

data class ListUiState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: UiText? = null,
)

/**
 * A list loaded page by page with the current session's token. A token the
 * server refuses ends the session.
 */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val sessions: SessionManager,
    private val load: suspend (token: String, page: Int) -> ApiResult<Page<T>>,
) {
    private val _state = MutableStateFlow(ListUiState<T>())
    val state: StateFlow<ListUiState<T>> = _state.asStateFlow()
    private var job: Job? = null

    /** From the first page, e.g. when the list is opened. */
    fun reload() {
        job?.cancel()
        _state.value = ListUiState()
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        val token = sessions.session.value?.token ?: return

        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            when (val result = load(token, current.page + 1)) {
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
                is ApiResult.Failed -> {
                    if (result.error.code == ApiError.UNAUTHORIZED) sessions.onUnauthorized(token)
                    _state.update { it.copy(loading = false, error = result.error.toUiText()) }
                }
            }
        }
    }
}

/** The home screen: profile, latest documents and requests, and the two full lists. */
class HomeViewModel(
    private val api: PanelApi,
    private val apiBaseUrl: String,
    private val sessions: SessionManager,
    private val vault: CredentialVault,
    private val prefs: LoginPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val documents = PagedList<DocumentItem>(viewModelScope, sessions) { token, page -> api.documents(token, page) }

    val requests = PagedList<RequestItem>(viewModelScope, sessions) { token, page -> api.requests(token, page) }

    init {
        viewModelScope.launch {
            val lastPasswordLogin = prefs.remembered().username

            vault.status.collect { status ->
                val saved = status.savedFor(apiBaseUrl)
                val name = saved ?: lastPasswordLogin
                _state.update {
                    it.copy(
                        biometricAvailable = vault.isAvailable(),
                        biometricEnabled = saved != null,
                        biometricDeclined = name.isNotEmpty() && status.isDeclined(apiBaseUrl, name),
                        loginName = name,
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        val token = sessions.session.value?.token ?: return

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = api.home(token)) {
                is ApiResult.Ok -> _state.update { it.copy(data = result.value, loading = false) }
                is ApiResult.Failed -> {
                    if (result.error.code == ApiError.UNAUTHORIZED) sessions.onUnauthorized(token)
                    _state.update { it.copy(loading = false, error = result.error.toUiText()) }
                }
            }
        }
    }

    /**
     * Off: the saved password is deleted and not offered again. On: it can
     * only be saved during a password login, so the next one offers it.
     */
    fun setBiometric(enabled: Boolean) {
        val name = _state.value.loginName.ifEmpty { return }

        viewModelScope.launch {
            runCatching {
                if (!enabled) vault.clear()
                vault.setDeclined(apiBaseUrl, name, declined = !enabled)
            }
        }
    }
}
