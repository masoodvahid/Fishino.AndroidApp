package rdsco.fishino.app.panel

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.bool
import rdsco.fishino.app.auth.int
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string

data class SigningRequirement(val key: String, val label: String, val complete: Boolean, val message: String, val tab: String?)

data class DocumentDetail(
    val id: Long,
    val title: String,
    val templateTitle: String,
    val dateLabel: String,
    val canSign: Boolean,
    val signatureRequired: Boolean,
    val signed: Boolean,
    val signedLabel: String?,
    val maskedMobile: String,
    val requirements: List<SigningRequirement>,
    /** The server can show the document as a page (no PDF needed). */
    val viewAvailable: Boolean = false,
) {
    val readyToSign: Boolean get() = canSign && !signed && requirements.isNotEmpty() && requirements.all { it.complete }
}

/** One document (/reports/{id}): its PDF and signing with a code by SMS. */
class DocumentApi(private val http: ApiClient) {

    suspend fun detail(token: String, id: Long): ApiResult<DocumentDetail> = http.get("/reports/$id", token) { data ->
        DocumentDetail(
            id = data.long("id") ?: id,
            title = data.string("title").orEmpty(),
            templateTitle = data.string("template_title").orEmpty(),
            dateLabel = data.string("date_label").orEmpty(),
            canSign = data.bool("can_sign") == true,
            signatureRequired = data.bool("signature_required") == true,
            signed = data.bool("signed") == true,
            signedLabel = data.string("signed_label"),
            maskedMobile = data.string("masked_mobile").orEmpty(),
            requirements = data.objects("requirements").map { item ->
                SigningRequirement(
                    key = item.string("key").orEmpty(),
                    label = item.string("label").orEmpty(),
                    complete = item.bool("complete") == true,
                    message = item.string("message").orEmpty(),
                    tab = item.string("tab"),
                )
            },
            viewAvailable = data.bool("view_available") == true,
        )
    }

    /** The document as the web panel shows it: an HTML page without script. */
    suspend fun view(token: String, id: Long): ApiResult<String> =
        when (val result = http.download(viewPath(id), token, maxBytes = 5L * 1024 * 1024)) {
            is ApiResult.Ok -> if (result.value.contentType.startsWith("text/html")) {
                ApiResult.Ok(result.value.bytes.toString(Charsets.UTF_8))
            } else {
                ApiResult.Failed(ApiError(ApiError.UNSUPPORTED_SERVER))
            }
            is ApiResult.Failed -> result
        }

    suspend fun sendCode(token: String, id: Long): ApiResult<CodeSent> = http.post("/reports/$id/sign/send", buildJsonObject {}, token) { data ->
        CodeSent(
            smsToken = data.string("sms_token") ?: throw IllegalStateException("no token"),
            message = data.string("message").orEmpty(),
            codeLength = data.int("code_length") ?: 5,
        )
    }

    suspend fun sign(token: String, id: Long, code: String, smsToken: String): ApiResult<String> =
        http.post("/reports/$id/sign", buildJsonObject { put("code", code); put("sms_token", smsToken) }, token, ProfileApi::message)

    companion object {
        fun pdfPath(id: Long) = "/reports/$id/pdf"

        fun viewPath(id: Long) = "/reports/$id/view"
    }
}
