package rdsco.fishino.app.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.DocumentApi
import rdsco.fishino.app.panel.DocumentDetail
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.ui.common.Action
import rdsco.fishino.app.ui.common.Loader
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText
import rdsco.fishino.app.ui.profile.PendingCode

/** One document: its PDF and signing it with a code sent by SMS. */
class DocumentViewModel(private val services: PanelServices) : ViewModel() {

    val detail = Loader<Long, DocumentDetail>(viewModelScope, services.authed) { token, id -> services.documents.detail(token, id) }

    val action = Action(viewModelScope, services.authed)

    /** The page of the live view (no PDF is built for it). */
    val page = Loader<Long, String>(viewModelScope, services.authed) { token, id -> services.documents.view(token, id) }

    private val _code = MutableStateFlow<PendingCode?>(null)
    val code: StateFlow<PendingCode?> = _code.asStateFlow()

    private val _smsListenRequest = MutableStateFlow(0)
    val smsListenRequest: StateFlow<Int> = _smsListenRequest.asStateFlow()

    private val _pdfBusy = MutableStateFlow(false)
    val pdfBusy: StateFlow<Boolean> = _pdfBusy.asStateFlow()

    private var openId: Long? = null

    fun open(id: Long) {
        if (openId != id) {
            openId = id
            _code.value = null
            action.clear()
        }
        detail.load(id, force = true)
    }

    fun sendCode(id: Long) = action.run(
        call = { token -> services.documents.sendCode(token, id) },
        message = { it.message },
        onSuccess = { sent ->
            _code.value = PendingCode(sent.smsToken, sent.message, sent.codeLength)
            _smsListenRequest.update { it + 1 }
        },
    )

    fun sign(id: Long, code: String) {
        val pending = _code.value ?: return
        action.run(
            call = { token -> services.documents.sign(token, id, code, pending.smsToken) },
            message = { it },
            onSuccess = {
                _code.value = null
                detail.reload()
            },
        )
    }

    fun cancelCode() {
        _code.value = null
        action.clear()
    }

    fun openPdf(id: Long, title: String) {
        if (_pdfBusy.value) return
        _pdfBusy.value = true

        viewModelScope.launch {
            val result = services.authed.call { token -> services.http.download(DocumentApi.pdfPath(id), token) }
            _pdfBusy.value = false

            when (result) {
                is ApiResult.Ok -> {
                    val name = result.value.fileName ?: "$title.pdf"
                    if (!services.files.open(result.value.bytes, name, "application/pdf")) action.fail(UiText.Res(R.string.file_no_viewer))
                }
                is ApiResult.Failed -> action.fail(result.error.toUiText())
            }
        }
    }
}
