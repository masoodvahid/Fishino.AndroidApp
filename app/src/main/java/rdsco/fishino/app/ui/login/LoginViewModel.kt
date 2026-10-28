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
import rdsco.fishino.app.auth.CredentialInvalidatedException
import rdsco.fishino.app.auth.CredentialVault
import rdsco.fishino.app.auth.LoginConfig
import rdsco.fishino.app.auth.LoginMethod
import rdsco.fishino.app.auth.LoginPrefs
import rdsco.fishino.app.auth.OtpCode
import rdsco.fishino.app.auth.Session
import rdsco.fishino.app.auth.SessionEnd
import rdsco.fishino.app.auth.SessionManager
import rdsco.fishino.app.employer.EmployerLogo
import rdsco.fishino.app.notify.MessageSync
import javax.crypto.Cipher

/** Text for the user: the server's own Persian message, or one of ours. */
sealed interface UiText {
    data class Server(val text: String) : UiText
    data class Res(@StringRes val id: Int) : UiText
}

enum class OtpStep { EnterMobile, EnterCode }

enum class BiometricPurpose { Login, Enroll }

/**
 * After a login with an SMS code: fingerprint sign-in needs the password
 * once, so the offer asks for it (and checks it with the server).
 */
data class OtpEnrollment(val password: String = "", val busy: Boolean = false, val error: UiText? = null)

/** The screen shows the biometric prompt for [cipher], once per [id]. */
class BiometricRequest(val id: Int, val purpose: BiometricPurpose, val cipher: Cipher)

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
    /** Username whose password is saved for fingerprint sign-in with this employer. */
    val savedUsername: String? = null,
    val biometricAvailable: Boolean = false,
    val biometricRequest: BiometricRequest? = null,
    /** After a password login: "sign in with your fingerprint next time?" */
    val enrollmentOffer: Boolean = false,
    /** After an SMS-code login: the same offer, asking for the password once. */
    val otpEnrollment: OtpEnrollment? = null,
) {
    val canUseBiometric: Boolean get() = savedUsername != null && biometricAvailable && config?.passwordEnabled != false

    val otpLength: Int get() = config?.otpLength ?: 5
}

/**
 * Login with username and password or with a code sent by SMS, against the
 * employer's own Fishino. The server decides when a captcha is needed and
 * when attempts are locked; this screen shows what it says.
 */
class LoginViewModel(
    private val api: AuthApi,
    private val apiBaseUrl: String,
    private val sessions: SessionManager,
    private val prefs: LoginPrefs,
    private val vault: CredentialVault,
    private val messageSync: MessageSync,
    private val logo: EmployerLogo,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** A password login that waits for the answer to the fingerprint offer. */
    private class PendingEnrollment(val session: Session, val username: String, val password: String)

    private var pendingEnrollment: PendingEnrollment? = null

    /** An SMS-code login waiting for the answer to the fingerprint offer. */
    private var pendingOtpSession: Session? = null
    private var biometricRequestId = 0

    /**
     * Open the fingerprint prompt by itself: when the app starts and after
     * a timeout, not after the user signed out on purpose.
     */
    private var autoPromptPending = true

    init {
        loadConfig()

        viewModelScope.launch {
            vault.status.collect { status ->
                _state.update { it.copy(savedUsername = status.savedFor(apiBaseUrl), biometricAvailable = vault.isAvailable()) }
                maybeAutoPrompt()
            }
        }

        // This view model outlives a session; when one ends, explain why and start over.
        viewModelScope.launch {
            sessions.lastEnd.collect { end ->
                if (end == null) return@collect
                autoPromptPending = end.first == SessionEnd.Timeout || end.first == SessionEnd.Expired
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
                _state.update { it.copy(biometricAvailable = vault.isAvailable()) }
                maybeAutoPrompt()
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
                    // Username and password first; the SMS code is one tap away (or the only way, if the employer turned passwords off).
                    val method = if (config.passwordEnabled || !config.otpEnabled) LoginMethod.Password else LoginMethod.Otp
                    _state.update {
                        it.copy(
                            loadingConfig = false,
                            config = config,
                            method = method,
                            username = it.username.ifEmpty { remembered.username },
                            mobile = it.mobile.ifEmpty { remembered.mobile },
                        )
                    }
                    maybeAutoPrompt()
                    launch { logo.sync(apiBaseUrl, config.logoVersion) }
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
                val username = current.username.trim()
                prefs.rememberPassword(username)

                if (runCatching { vault.shouldOffer(apiBaseUrl, username) }.getOrDefault(false)) {
                    pendingEnrollment = PendingEnrollment(session, username, current.password)
                    _state.update { it.copy(password = "", enrollmentOffer = true) }
                } else {
                    signedIn(session)
                }
            },
        )
    }

    /** Sign in with the password saved for fingerprint sign-in: first the prompt. */
    fun loginWithFingerprint() {
        val current = _state.value
        if (!canSubmit(current) || current.biometricRequest != null) return
        if (current.captcha != null && current.captchaAnswer.isEmpty()) return showError(R.string.login_error_captcha)

        viewModelScope.launch {
            val saved = vault.saved(apiBaseUrl) ?: return@launch
            val cipher = try {
                vault.decryptionCipher(saved)
            } catch (e: CredentialInvalidatedException) {
                return@launch showError(R.string.biometric_error_invalidated)
            } catch (e: Exception) {
                return@launch showError(R.string.biometric_error_unavailable)
            }

            _state.update { it.copy(biometricRequest = BiometricRequest(++biometricRequestId, BiometricPurpose.Login, cipher), error = null) }
        }
    }

    /** The screen took the request and is showing the prompt. */
    fun onBiometricShown(id: Int) {
        _state.update { if (it.biometricRequest?.id == id) it.copy(biometricRequest = null) else it }
    }

    /** The prompt accepted the fingerprint; [cipher] is now unlocked. */
    fun onBiometricSucceeded(purpose: BiometricPurpose, cipher: Cipher) {
        when (purpose) {
            BiometricPurpose.Login -> viewModelScope.launch { loginWithSavedPassword(cipher) }
            BiometricPurpose.Enroll -> viewModelScope.launch {
                val pending = pendingEnrollment ?: return@launch
                runCatching { vault.save(apiBaseUrl, pending.username, pending.password, cipher) }
                    .onFailure { showError(R.string.biometric_error_unavailable) }
                finishEnrollment()
            }
        }
    }

    /** The prompt was cancelled ([message] null) or failed (too many tries, …). */
    fun onBiometricFailed(purpose: BiometricPurpose, message: String?) {
        when (purpose) {
            BiometricPurpose.Login -> message?.let { text -> _state.update { it.copy(error = UiText.Server(text)) } }
            BiometricPurpose.Enroll -> finishEnrollment()
        }
    }

    fun acceptEnrollment() {
        _state.update { it.copy(enrollmentOffer = false) }
        val cipher = runCatching { vault.encryptionCipher() }.getOrNull() ?: return finishEnrollment()
        _state.update { it.copy(biometricRequest = BiometricRequest(++biometricRequestId, BiometricPurpose.Enroll, cipher)) }
    }

    fun declineEnrollment() {
        _state.update { it.copy(enrollmentOffer = false) }
        val pending = pendingEnrollment ?: return

        viewModelScope.launch {
            runCatching { vault.setDeclined(apiBaseUrl, pending.username, declined = true) }
            finishEnrollment()
        }
    }

    private suspend fun loginWithSavedPassword(cipher: Cipher) {
        val saved = vault.saved(apiBaseUrl) ?: return
        val password = runCatching { vault.decrypt(saved, cipher) }.getOrNull()
            ?: return showError(R.string.biometric_error_unavailable)
        val current = _state.value

        _state.update { it.copy(method = LoginMethod.Password, username = saved.username) }

        submit(
            call = { deviceId -> api.login(saved.username, password, deviceId, current.captchaAnswerOrNull()) },
            onSuccess = { session ->
                prefs.rememberPassword(saved.username)
                signedIn(session)
            },
            onFailure = { error ->
                when (error.code) {
                    // Changed on the web since it was saved: forget it, the next password login offers it again.
                    "invalid_credentials" -> {
                        runCatching { vault.clear() }
                        _state.update { it.copy(error = UiText.Res(R.string.biometric_error_password_changed)) }
                    }
                    // The server wants a captcha first: the password is filled in, the user answers and taps "login".
                    "captcha_required", "captcha_invalid" -> _state.update { it.copy(password = password) }
                }
            },
        )
    }

    private fun finishEnrollment() {
        val pending = pendingEnrollment ?: return
        pendingEnrollment = null
        signedIn(pending.session)
    }

    private fun maybeAutoPrompt() {
        val current = _state.value
        if (!autoPromptPending || current.config == null || !current.canUseBiometric) return
        if (pendingEnrollment != null || pendingOtpSession != null || sessions.session.value != null) return

        autoPromptPending = false
        loginWithFingerprint()
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
            onSuccess = { session ->
                val username = session.user.username
                val offer = current.config?.passwordEnabled != false && username.isNotBlank() &&
                    runCatching { vault.shouldOffer(apiBaseUrl, username) }.getOrDefault(false)

                if (offer) {
                    pendingOtpSession = session
                    _state.update { it.copy(code = "", otpEnrollment = OtpEnrollment()) }
                } else {
                    signedIn(session)
                }
            },
            onFailure = { _ -> _state.update { it.copy(code = "") } },
        )
    }

    fun onEnrollmentPasswordChange(value: String) =
        _state.update { state -> state.copy(otpEnrollment = state.otpEnrollment?.copy(password = value.take(255), error = null)) }

    /**
     * Checks the password with a normal login (the new session replaces the
     * SMS one), then asks for the fingerprint to keep it.
     */
    fun confirmOtpEnrollment() {
        val offer = _state.value.otpEnrollment ?: return
        val otpSession = pendingOtpSession ?: return
        if (offer.busy) return
        if (offer.password.isEmpty()) {
            _state.update { it.copy(otpEnrollment = offer.copy(error = UiText.Res(R.string.login_error_password))) }
            return
        }

        _state.update { it.copy(otpEnrollment = offer.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val username = otpSession.user.username
            when (val result = api.login(username, offer.password, prefs.deviceId(), captcha = null)) {
                is ApiResult.Ok -> {
                    val session = result.value
                    pendingOtpSession = null
                    if (session.token != otpSession.token) launch { api.logout(otpSession.token) }
                    pendingEnrollment = PendingEnrollment(session, username, offer.password)
                    _state.update { it.copy(otpEnrollment = null) }
                    val cipher = runCatching { vault.encryptionCipher() }.getOrNull() ?: return@launch finishEnrollment()
                    _state.update { it.copy(biometricRequest = BiometricRequest(++biometricRequestId, BiometricPurpose.Enroll, cipher)) }
                }
                is ApiResult.Failed -> _state.update {
                    it.copy(otpEnrollment = it.otpEnrollment?.copy(busy = false, password = "", error = result.error.toUiText()))
                }
            }
        }
    }

    /** "Not now": the SMS login goes on; the offer is not repeated for this account. */
    fun skipOtpEnrollment() {
        val session = pendingOtpSession ?: return
        if (_state.value.otpEnrollment?.busy == true) return
        pendingOtpSession = null
        _state.update { it.copy(otpEnrollment = null) }

        viewModelScope.launch {
            runCatching { vault.setDeclined(apiBaseUrl, session.user.username, declined = true) }
            signedIn(session)
        }
    }

    fun editMobile() {
        _state.update { it.copy(otpStep = OtpStep.EnterMobile, otpToken = null, otpMessage = null, code = "", error = null) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun canSubmit(state: LoginUiState): Boolean = !state.busy && clock() >= state.lockedUntilMillis

    private fun signedIn(session: Session) {
        autoPromptPending = false
        _state.update { it.copy(password = "", code = "", captcha = null, captchaAnswer = "", biometricRequest = null) }
        sessions.signedIn(session, api)
        session.sync?.let { grant -> viewModelScope.launch { messageSync.onSignedIn(apiBaseUrl, grant) } }
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
        onFailure: suspend (ApiError) -> Unit = {},
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
            SessionEnd.SignedOut, SessionEnd.Timeout -> null
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
