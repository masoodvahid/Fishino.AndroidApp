package rdsco.fishino.app.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.FieldType
import rdsco.fishino.app.panel.FormDetail
import rdsco.fishino.app.panel.FormSummary
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.panel.RequestApi
import rdsco.fishino.app.panel.RequestDetail
import rdsco.fishino.app.panel.SubmitResult
import rdsco.fishino.app.ui.common.Action
import rdsco.fishino.app.ui.common.Loader
import rdsco.fishino.app.ui.home.latinDigits
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

/** New requests (forms) and a request's details. */
class RequestsViewModel(private val services: PanelServices) : ViewModel() {

    val forms = Loader<Unit, List<FormSummary>>(viewModelScope, services.authed) { token, _ -> services.requests.forms(token) }

    val form = Loader<Long, FormDetail>(viewModelScope, services.authed) { token, id -> services.requests.form(token, id) }

    val detail = Loader<Long, RequestDetail>(viewModelScope, services.authed) { token, id -> services.requests.detail(token, id) }

    val submit = Action(viewModelScope, services.authed)

    /** Field values of the open form, by field key (digits only for numbers). */
    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val _submitted = MutableStateFlow<SubmitResult?>(null)

    /** Set after a successful submit; the screen shows it and goes on. */
    val submitted: StateFlow<SubmitResult?> = _submitted.asStateFlow()

    private val _pdfBusy = MutableStateFlow(false)
    val pdfBusy: StateFlow<Boolean> = _pdfBusy.asStateFlow()

    private val _pdfError = MutableStateFlow<UiText?>(null)
    val pdfError: StateFlow<UiText?> = _pdfError.asStateFlow()

    private var openFormId: Long? = null

    fun openForm(id: Long) {
        if (openFormId != id) {
            openFormId = id
            _values.value = emptyMap()
            submit.clear()
        }
        form.load(id)
    }

    fun setValue(key: String, type: FieldType, value: String) {
        val cleaned = when (type) {
            FieldType.Number, FieldType.Currency -> value.latinDigits().filter { it in '0'..'9' }.take(18)
            else -> value.take(5000)
        }
        _values.update { it + (key to cleaned) }
    }

    fun submitForm() {
        val detail = form.state.value.data ?: return
        val missing = detail.fields.firstOrNull { it.required && _values.value[it.key].isNullOrBlank() }
        if (missing != null) return submit.fail(UiText.Server("وارد کردن ${missing.label} الزامی است."))

        submit.run(
            call = { token -> services.requests.submit(token, detail.summary.id, _values.value.filterValues { it.isNotBlank() }) },
            message = { it.message },
            onSuccess = { result ->
                _values.value = emptyMap()
                _submitted.value = result
            },
        )
    }

    fun consumeSubmitted() {
        _submitted.value = null
    }

    fun openPdf(id: Long) {
        if (_pdfBusy.value) return
        _pdfBusy.value = true
        _pdfError.value = null

        viewModelScope.launch {
            val result = services.authed.call { token -> services.http.download(RequestApi.pdfPath(id), token) }
            _pdfBusy.value = false

            when (result) {
                is ApiResult.Ok -> if (!services.files.open(result.value.bytes, "request-${1000 + id}.pdf", "application/pdf")) {
                    _pdfError.value = UiText.Res(R.string.file_no_viewer)
                }
                is ApiResult.Failed -> _pdfError.value = result.error.toUiText()
            }
        }
    }
}
