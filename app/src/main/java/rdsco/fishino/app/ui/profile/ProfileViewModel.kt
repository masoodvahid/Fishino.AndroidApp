package rdsco.fishino.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.CredentialVault
import rdsco.fishino.app.auth.LoginPrefs
import rdsco.fishino.app.auth.OtpCode
import rdsco.fishino.app.auth.UploadPart
import rdsco.fishino.app.panel.AgreementData
import rdsco.fishino.app.panel.BaleCode
import rdsco.fishino.app.panel.LoginLog
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.panel.ProfileApi
import rdsco.fishino.app.panel.ProfileData
import rdsco.fishino.app.ui.common.Action
import rdsco.fishino.app.ui.common.Loader
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

/** A code sent by SMS (mobile number or agreement) waiting to be typed. */
data class PendingCode(val smsToken: String, val message: String, val length: Int, val target: String? = null)

data class BiometricSetting(val available: Boolean = false, val enabled: Boolean = false, val declined: Boolean = false, val loginName: String = "")

/**
 * The profile and its sections, as on the web: each action reloads the
 * profile so every section shows the server's state.
 */
class ProfileViewModel(
    private val services: PanelServices,
    private val apiBaseUrl: String,
    private val vault: CredentialVault,
    private val prefs: LoginPrefs,
) : ViewModel() {

    private val api: ProfileApi get() = services.profile

    val profile = Loader<Unit, ProfileData>(viewModelScope, services.authed) { token, _ -> api.profile(token) }

    val agreement = Loader<Unit, AgreementData>(viewModelScope, services.authed) { token, _ -> api.agreement(token) }

    val logs = Loader<Unit, List<LoginLog>>(viewModelScope, services.authed) { token, _ -> api.logs(token) }

    /** The current section's action (save, send code, upload, …). */
    val action = Action(viewModelScope, services.authed)

    private val _mobileCode = MutableStateFlow<PendingCode?>(null)
    val mobileCode: StateFlow<PendingCode?> = _mobileCode.asStateFlow()

    private val _agreementCode = MutableStateFlow<PendingCode?>(null)
    val agreementCode: StateFlow<PendingCode?> = _agreementCode.asStateFlow()

    /** Increases with each code sent; the screen listens for the SMS. */
    private val _smsListenRequest = MutableStateFlow(0)
    val smsListenRequest: StateFlow<Int> = _smsListenRequest.asStateFlow()

    private val _baleCode = MutableStateFlow<BaleCode?>(null)
    val baleCode: StateFlow<BaleCode?> = _baleCode.asStateFlow()

    /** Bumped when the photo changes, so cached copies are not shown. */
    private val _avatarVersion = MutableStateFlow(0)
    val avatarVersion: StateFlow<Int> = _avatarVersion.asStateFlow()

    private val _biometric = MutableStateFlow(BiometricSetting())
    val biometric: StateFlow<BiometricSetting> = _biometric.asStateFlow()

    private val _fileBusy = MutableStateFlow(false)
    val fileBusy: StateFlow<Boolean> = _fileBusy.asStateFlow()

    init {
        viewModelScope.launch {
            val lastPasswordLogin = prefs.remembered().username

            vault.status.collect { status ->
                val saved = status.savedFor(apiBaseUrl)
                val name = saved ?: lastPasswordLogin
                _biometric.value = BiometricSetting(
                    available = vault.isAvailable(),
                    enabled = saved != null,
                    declined = name.isNotEmpty() && status.isDeclined(apiBaseUrl, name),
                    loginName = name,
                )
            }
        }
    }

    fun refresh() = profile.load(Unit, force = true)

    /** Opening a section starts without old messages. */
    fun openSection() {
        action.clear()
        _baleCode.value = null
    }

    fun changePassword(password: String, confirmation: String) = action.run(
        call = { token -> api.changePassword(token, password, confirmation) },
        message = { it },
        onSuccess = { refresh() },
    )

    fun sendMobileCode(mobile: String) {
        val normalized = OtpCode.normalizeMobile(mobile) ?: return action.fail(UiText.Res(R.string.login_error_mobile))
        action.run(
            call = { token -> api.sendMobileCode(token, normalized) },
            message = { it.message },
            onSuccess = { sent ->
                _mobileCode.value = PendingCode(sent.smsToken, sent.message, sent.codeLength, normalized)
                _smsListenRequest.update { it + 1 }
            },
        )
    }

    fun verifyMobileCode(code: String) {
        val pending = _mobileCode.value ?: return
        action.run(
            call = { token -> api.verifyMobileCode(token, code, pending.smsToken) },
            message = { it },
            onSuccess = {
                _mobileCode.value = null
                refresh()
            },
        )
    }

    fun editMobile() {
        _mobileCode.value = null
        action.clear()
    }

    fun verifyShahkar() = action.run(call = { token -> api.verifyShahkar(token) }, message = { it }, onSuccess = { refresh() })

    fun requestChange(key: String, value: String, onDone: () -> Unit) = action.run(
        call = { token -> api.requestChange(token, key, value) },
        message = { it },
        onSuccess = {
            onDone()
            refresh()
        },
    )

    fun uploadSignature(png: ByteArray) = action.run(call = { token -> api.uploadSignature(token, png) }, message = { it }, onSuccess = { refresh() })

    fun uploadAvatar(jpeg: ByteArray) = action.run(
        call = { token -> api.uploadAvatar(token, jpeg) },
        message = { it },
        onSuccess = {
            _avatarVersion.update { it + 1 }
            refresh()
        },
    )

    fun deleteAvatar() = action.run(
        call = { token -> api.deleteAvatar(token) },
        message = { it },
        onSuccess = {
            _avatarVersion.update { it + 1 }
            refresh()
        },
    )

    fun uploadDocument(fieldId: Long, file: UploadPart) =
        action.run(call = { token -> api.uploadDocument(token, fieldId, file) }, message = { it }, onSuccess = { refresh() })

    fun deleteDocument(fileId: Long) = action.run(call = { token -> api.deleteDocument(token, fileId) }, message = { it }, onSuccess = { refresh() })

    fun createBaleCode() = action.run(call = { token -> api.baleCode(token) }, message = { it.message }, onSuccess = { _baleCode.value = it })

    fun disconnectBale() = action.run(call = { token -> api.baleDisconnect(token) }, message = { it }, onSuccess = { refresh() })

    fun sendAgreementCode() = action.run(
        call = { token -> api.sendAgreementCode(token) },
        message = { it.message },
        onSuccess = { sent ->
            _agreementCode.value = PendingCode(sent.smsToken, sent.message, sent.codeLength)
            _smsListenRequest.update { it + 1 }
        },
    )

    fun verifyAgreement(code: String) {
        val pending = _agreementCode.value ?: return
        action.run(
            call = { token -> api.verifyAgreement(token, code, pending.smsToken) },
            message = { it },
            onSuccess = {
                _agreementCode.value = null
                agreement.reload()
                refresh()
            },
        )
    }

    fun cancelAgreementCode() {
        _agreementCode.value = null
        action.clear()
    }

    fun openAgreementPdf() = openFile(ProfileApi.AGREEMENT_PDF_PATH, "agreement.pdf", "application/pdf")

    /** Off: the saved password is deleted. On: offered again at the next password login. */
    fun setBiometric(enabled: Boolean) {
        val name = _biometric.value.loginName.ifEmpty { return }

        viewModelScope.launch {
            runCatching {
                if (!enabled) vault.clear()
                vault.setDeclined(apiBaseUrl, name, declined = !enabled)
            }
        }
    }

    private fun openFile(path: String, name: String, mime: String) {
        if (_fileBusy.value) return
        _fileBusy.value = true

        viewModelScope.launch {
            val result = services.authed.call { token -> services.http.download(path, token) }
            _fileBusy.value = false

            when (result) {
                is ApiResult.Ok -> if (!services.files.open(result.value.bytes, name, mime)) action.fail(UiText.Res(R.string.file_no_viewer))
                is ApiResult.Failed -> action.fail(result.error.toUiText())
            }
        }
    }
}
