package rdsco.fishino.app.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.AuthApi
import rdsco.fishino.app.auth.Captcha
import rdsco.fishino.app.auth.CaptchaAnswer
import rdsco.fishino.app.auth.LoginConfig
import rdsco.fishino.app.auth.LoginMethod
import rdsco.fishino.app.auth.LoginPrefs
import rdsco.fishino.app.auth.OtpCode
import rdsco.fishino.app.auth.Session
import rdsco.fishino.app.auth.SessionEnd
import rdsco.fishino.app.auth.SessionManager

/** Text for the user: the server's own Persian message, or one of ours. */
sealed interface UiText {
    data class Server(val text: String) : UiText
    data class Res(@StringRes val id: Int) : UiText
}

enum class OtpStep { EnterMobile, EnterCode }

data class LoginUiState(
    val loadingConfig: Boolean = true,
    val config: LoginConfig? = null,
    val configError: UiText? = null,
    val method: LoginMethod = LoginMethod.Password,
    val username: String = "",
    val password: String = "",
    val mobile: String = "",
    val otpStep: OtpStep = OtpStep.EnterMobile,
    val code: String = "",
    val otpToken: String? = null,
    val otpMessage: String? = null,
    val resendAtMillis: Long = 0,
    /** Increases each time a code is sent; the screen listens for the SMS. */
    val smsListenRequest: Int = 0,
    val captcha: Captcha? = null,
    val captchaAnswer: String = "",
    val busy: Boolean = false,
    val error: UiText? = null,
    val lockedUntilMillis: Long = 0,
    val notice: UiText? = null,
) {
    val otpLength: Int get() = config?.otpLength ?: 5
}

/**
 * Login with username and password or with a code sent by SMS, against the
 * employer's own Fishino. The server decides when a captcha is needed and
 * when attempts are locked; this screen shows what it says.
 */
class LoginViewModel(
    private val api: AuthApi,
    private val sessions: SessionManager,
    private val prefs: LoginPrefs,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        loadConfig()

        // This view model outlives a session; when one ends, explain why and start over.
        viewModelScope.launch {
            sessions.lastEnd.collect { end ->
                if (end == null) return@collect
                _state.update {
                    it.copy(
                        notice = endNotice(end.first, end.second),
                        otpStep = OtpStep.EnterMobile,
                        otpToken = null,
                        code = "",
                        error = null,
                    )
                }
                sessions.consumeLastEnd()
            }
        }
    }

    fun loadConfig() {
        _state.update { it.copy(loadingConfig = true, configError = null) }

        viewModelScope.launch {
            val remembered = prefs.remembered()

            when (val result = api.config()) {
                is ApiResult.Ok -> {
                    val config = result.value
                    val method = remembered.method?.takeIf { it == LoginMethod.Password || config.otpEnabled } ?: config.defaultMethod
                    _state.update {
                        it.copy(
                            loadingConfig = false,
                            config = config,
                            method = method,
                            username = it.username.ifEmpty { remembered.username },
                            mobile = it.mobile.ifEmpty { remembered.mobile },
                        )
                    }
                }
                is ApiResult.Failed -> _state.update { it.copy(loadingConfig = false, configError = result.error.toUiText()) }
            }
        }
    }

    fun selectMethod(method: LoginMethod) {
        _state.update { it.copy(method = method, error = null, captcha = null, captchaAnswer = "") }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value.take(32), error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value.take(255), error = null) }

    fun onMobileChange(value: String) = _state.update { it.copy(mobile = OtpCode.digitsOnly(value, 14), error = null) }

    fun onCaptchaChange(value: String) = _state.update { it.copy(captchaAnswer = OtpCode.digitsOnly(value, 6), error = null) }

    fun onCodeChange(value: String) {
        val code = OtpCode.digitsOnly(value, _state.value.otpLength)
        _state.update { it.copy(code = code, error = null) }
        if (code.length == _state.value.otpLength) verifyOtp()
    }

    /** A code read from the SMS (with the user's consent). */
    fun onSmsReceived(message: String) {
        if (_state.value.otpStep != OtpStep.EnterCode || _state.value.busy) return
        OtpCode.extract(message, _state.value.otpLength)?.let(::onCodeChange)
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            when (val result = api.captcha()) {
                is ApiResult.Ok -> _state.update { it.copy(captcha = result.value, captchaAnswer = "") }
                is ApiResult.Failed -> _state.update { it.copy(error = result.error.toUiText()) }
            }
        }
    }

    fun loginWithPassword() {
        val current = _state.value
        if (!canSubmit(current)) return

        when {
            current.username.isBlank() -> return showError(R.string.login_error_username)
            current.password.isEmpty() -> return showError(R.string.login_error_password)
            current.captcha != null && current.captchaAnswer.isEmpty() -> return showError(R.string.login_error_captcha)
        }

        submit(
            call = { deviceId -> api.login(current.username.trim(), current.password, deviceId, current.captchaAnswerOrNull()) },
            onSuccess = { session ->
                prefs.rememberPassword(current.username.trim())
                signedIn(session)
            },
        )
    }

    fun sendOtp() {
        val current = _state.value
        if (!canSubmit(current)) return

        val mobile = OtpCode.normalizeMobile(current.mobile) ?: return showError(R.string.login_error_mobile)
        if (current.captcha != null && current.captchaAnswer.isEmpty()) return showError(R.string.login_error_captcha)

        submit(
            call = { deviceId -> api.sendOtp(mobile, deviceId, current.captchaAnswerOrNull()) },
            onSuccess = { request ->
                prefs.rememberOtp(mobile)
                _state.update {
                    it.copy(
                        mobile = mobile,
                        otpStep = OtpStep.EnterCode,
                        otpToken = request.otpToken,
                        otpMessage = request.message,
                        code = "",
                        resendAtMillis = clock() + request.resendAfterSeconds * 1000L,
                        smsListenRequest = it.smsListenRequest + 1,
                    )
                }
            },
        )
    }

    fun verifyOtp() {
        val current = _state.value
        val token = current.otpToken ?: return
        if (!canSubmit(current) || current.code.length < 4) return

        submit(
            call = { deviceId -> api.verifyOtp(current.mobile, current.code, token, deviceId) },
            onSuccess = { session -> signedIn(session) },
            onFailure = { _state.update { it.copy(code = "") } },
        )
    }

    fun editMobile() {
        _state.update { it.copy(otpStep = OtpStep.EnterMobile, otpToken = null, otpMessage = null, code = "", error = null) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun canSubmit(state: LoginUiState): Boolean = !state.busy && clock() >= state.lockedUntilMillis

    private fun signedIn(session: Session) {
        _state.update { it.copy(password = "", code = "", captcha = null, captchaAnswer = "") }
        sessions.signedIn(session, api)
    }

    private fun LoginUiState.captchaAnswerOrNull(): CaptchaAnswer? =
        captcha?.let { CaptchaAnswer(it.key, captchaAnswer) }

    /**
     * Runs a call with the busy flag and turns its failure into screen
     * state: message, captcha to show, lockout.
     */
    private fun <T> submit(
        call: suspend (deviceId: String) -> ApiResult<T>,
        onSuccess: suspend (T) -> Unit,
        onFailure: (ApiError) -> Unit = {},
    ) {
        _state.update { it.copy(busy = true, error = null, notice = null) }

        viewModelScope.launch {
            val result = call(prefs.deviceId())
            _state.update { it.copy(busy = false) }

            when (result) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(captcha = null, captchaAnswer = "") }
                    onSuccess(result.value)
                }
                is ApiResult.Failed -> {
                    applyFailure(result.error)
                    onFailure(result.error)
                }
            }
        }
    }

    private fun applyFailure(error: ApiError) {
        val lockSeconds = when (error.code) {
            "locked", ApiError.TOO_MANY_REQUESTS -> error.retryAfterSeconds ?: 60
            else -> null
        }

        _state.update {
            it.copy(
                error = error.toUiText(),
                // A captcha is single-use: a new one comes with the answer, or none is needed.
                captcha = error.captcha ?: it.captcha.takeIf { _ -> error.code == "locked" },
                captchaAnswer = "",
                lockedUntilMillis = lockSeconds?.let { seconds -> clock() + seconds * 1000 } ?: it.lockedUntilMillis,
            )
        }

        // The server wants a captcha but did not send one (should not happen): fetch it.
        if (error.captcha == null && error.code in setOf("captcha_required", "captcha_invalid")) refreshCaptcha()
    }

    private fun showError(@StringRes id: Int) = _state.update { it.copy(error = UiText.Res(id)) }

    private fun endNotice(reason: SessionEnd, message: String?): UiText? =
        when (reason) {
            SessionEnd.SignedOut -> null
            SessionEnd.Expired -> UiText.Res(R.string.login_notice_expired)
            SessionEnd.Revoked -> message?.let(UiText::Server) ?: UiText.Res(R.string.login_notice_revoked)
        }
}

internal fun ApiError.toUiText(): UiText = when {
    code == ApiError.NETWORK -> UiText.Res(R.string.error_network)
    code == ApiError.UNSUPPORTED_SERVER -> UiText.Res(R.string.login_error_unsupported)
    code == ApiError.TOO_MANY_REQUESTS -> UiText.Res(R.string.login_error_too_many)
    !message.isNullOrBlank() -> UiText.Server(message)
    else -> UiText.Res(R.string.error_server)
}
