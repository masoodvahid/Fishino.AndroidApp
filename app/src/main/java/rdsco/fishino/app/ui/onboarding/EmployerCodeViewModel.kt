package rdsco.fishino.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.employer.EmployerCode
import rdsco.fishino.app.employer.EmployerDirectory
import rdsco.fishino.app.employer.EmployerStore
import rdsco.fishino.app.employer.ResolveResult
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import rdsco.fishino.app.employer.EmployerLogo
import rdsco.fishino.app.employer.LogoCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CodeError {
    data object InvalidFormat : CodeError
    data object NotFound : CodeError
    data object Inactive : CodeError
    data class RateLimited(val minutes: Int) : CodeError
    data object Network : CodeError
    data object Untrusted : CodeError
    data object Server : CodeError
}

data class EmployerCodeUiState(
    val code: String = "",
    val checking: Boolean = false,
    val error: CodeError? = null,
    /** Employer found for the code, waiting for the user's confirmation. */
    val candidate: Employer? = null,
    /** The candidate's logo, when its server has one (it may arrive a moment later). */
    val candidateLogo: ImageBitmap? = null,
    val saving: Boolean = false,
)

/**
 * First-launch flow: code → employer name → user confirms → saved for good.
 * Saving is all that is needed; the activity switches screens when the
 * stored employer appears.
 */
class EmployerCodeViewModel(
    private val directory: EmployerDirectory,
    private val store: EmployerStore,
    private val logo: EmployerLogo,
) : ViewModel() {

    private var logoCandidate: LogoCandidate? = null
    private var logoJob: Job? = null

    private val _state = MutableStateFlow(EmployerCodeUiState())
    val state: StateFlow<EmployerCodeUiState> = _state.asStateFlow()

    fun onCodeChange(input: String) {
        _state.update { it.copy(code = EmployerCode.sanitize(input), error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.checking) return

        if (!EmployerCode.isComplete(current.code)) {
            _state.update { it.copy(error = CodeError.InvalidFormat) }
            return
        }

        _state.update { it.copy(checking = true, error = null) }

        viewModelScope.launch {
            val result = directory.resolve(current.code)

            _state.update {
                when (result) {
                    is ResolveResult.Found -> it.copy(checking = false, candidate = result.employer)
                    else -> it.copy(checking = false, error = result.toError())
                }
            }
            if (result is ResolveResult.Found) fetchLogo(result.employer)
        }
    }

    private fun fetchLogo(employer: Employer) {
        logoJob?.cancel()
        logoCandidate = null
        logoJob = viewModelScope.launch {
            val fetched = logo.fetch(employer.apiBaseUrl) ?: return@launch
            if (_state.value.candidate != employer) return@launch
            logoCandidate = fetched
            _state.update { it.copy(candidateLogo = fetched.image) }
        }
    }

    fun confirm() {
        val employer = _state.value.candidate ?: return
        if (_state.value.saving) return

        _state.update { it.copy(saving = true) }

        viewModelScope.launch {
            logoJob?.cancel()
            logo.keep(logoCandidate)
            logoCandidate = null
            store.save(employer)
            // Fresh state in case the user changes employer later in this session.
            _state.value = EmployerCodeUiState()
        }
    }

    fun reject() {
        logoJob?.cancel()
        logoCandidate = null
        _state.value = EmployerCodeUiState()
    }

    private fun ResolveResult.toError(): CodeError = when (this) {
        is ResolveResult.Found -> error("Found is not an error")
        ResolveResult.NotFound -> CodeError.NotFound
        ResolveResult.Inactive -> CodeError.Inactive
        is ResolveResult.RateLimited -> CodeError.RateLimited(((retryAfterSeconds + 59) / 60).toInt().coerceAtLeast(1))
        ResolveResult.Untrusted -> CodeError.Untrusted
        ResolveResult.NetworkError -> CodeError.Network
        ResolveResult.ServerError -> CodeError.Server
    }
}
