package rdsco.fishino.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.Authed
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

/**
 * One screen's data: loading, loaded, or failed. [requirementTab] is set
 * when the server says a profile step must be done first.
 */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val requirementTab: String? = null,
)

/** Loads one thing for a screen, e.g. a news item by id. */
class Loader<K, T>(
    private val scope: CoroutineScope,
    private val authed: Authed,
    private val load: suspend (token: String, key: K) -> ApiResult<T>,
) {
    private val _state = MutableStateFlow(Loadable<T>())
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()
    private var key: K? = null
    private var job: Job? = null

    /** Loads [key] unless it is already shown; [force] reloads it. */
    fun load(key: K, force: Boolean = false) {
        if (!force && this.key == key && (_state.value.data != null || _state.value.loading)) return

        job?.cancel()
        if (this.key != key) _state.value = Loadable()
        this.key = key
        _state.update { it.copy(loading = true, error = null, requirementTab = null) }

        job = scope.launch {
            when (val result = authed.call { token -> load(token, key) }) {
                is ApiResult.Ok -> _state.value = Loadable(data = result.value)
                is ApiResult.Failed -> _state.update { it.copy(loading = false, error = result.error.toUiText(), requirementTab = result.error.requirementTab) }
            }
        }
    }

    fun reload() {
        key?.let { load(it, force = true) }
    }
}

/** A one-off action (save, send, delete): busy flag, error and success message. */
data class ActionState(val busy: Boolean = false, val error: UiText? = null, val message: String? = null, val fieldErrors: Map<String, String> = emptyMap())

class Action(private val scope: CoroutineScope, private val authed: Authed) {
    private val _state = MutableStateFlow(ActionState())
    val state: StateFlow<ActionState> = _state.asStateFlow()

    fun <T> run(
        call: suspend (token: String) -> ApiResult<T>,
        message: (T) -> String? = { null },
        onFailure: (ApiError) -> Unit = {},
        onSuccess: suspend (T) -> Unit = {},
    ) {
        if (_state.value.busy) return
        _state.value = ActionState(busy = true)

        scope.launch {
            when (val result = authed.call(call)) {
                is ApiResult.Ok -> {
                    _state.value = ActionState(message = message(result.value))
                    onSuccess(result.value)
                }
                is ApiResult.Failed -> {
                    _state.value = ActionState(error = result.error.toUiText(), fieldErrors = result.error.fields)
                    onFailure(result.error)
                }
            }
        }
    }

    /** A local problem (e.g. empty field) shown like a server one. */
    fun fail(error: UiText) {
        _state.value = ActionState(error = error)
    }

    fun clear() {
        _state.value = ActionState()
    }
}
