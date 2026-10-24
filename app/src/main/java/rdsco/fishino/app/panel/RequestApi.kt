package rdsco.fishino.app.panel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.array
import rdsco.fishino.app.auth.bool
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.obj
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string

data class FormSummary(val id: Long, val title: String, val subtitle: String?, val external: Boolean, val unique: Boolean)

enum class FieldType { Text, Number, Currency, Dropdown }

data class FormField(val key: String, val label: String, val type: FieldType, val required: Boolean, val options: List<String>)

data class FormDetail(
    val summary: FormSummary,
    val helpHtml: String?,
    val previewHtml: String,
    val fields: List<FormField>,
    val externalUrl: String?,
)

data class SubmitResult(val id: Long, val trackingCode: String, val message: String)

data class Review(
    val reviewer: String?,
    val reviewerTitle: String?,
    val status: RequestStatus,
    val statusLabel: String,
    val comment: String?,
    val dateLabel: String?,
)

data class RequestDetail(
    val id: Long,
    val trackingCode: String,
    val title: String,
    val subtitle: String?,
    val dateLabel: String?,
    val status: RequestStatus,
    val statusLabel: String,
    val contentHtml: String,
    val reviews: List<Review>,
    /** The PDF letter is built only once the request is approved. */
    val pdfAvailable: Boolean = false,
)

/** Request forms, submitting one, and a request's details (/forms, /requests/{id}). */
class RequestApi(private val http: ApiClient) {

    suspend fun forms(token: String): ApiResult<List<FormSummary>> =
        http.get("/forms", token) { data -> data.objects("items").mapNotNull(::parseSummary) }

    suspend fun form(token: String, id: Long): ApiResult<FormDetail> = http.get("/forms/$id", token) { data ->
        FormDetail(
            summary = parseSummary(data) ?: throw IllegalStateException("no id"),
            helpHtml = data.string("help_html"),
            previewHtml = data.string("preview_html").orEmpty(),
            fields = data.objects("fields").mapNotNull { field ->
                val key = field.string("key") ?: return@mapNotNull null
                FormField(
                    key = key,
                    label = field.string("label") ?: key,
                    type = when (field.string("type")) {
                        "number" -> FieldType.Number
                        "currency" -> FieldType.Currency
                        "dropdown" -> FieldType.Dropdown
                        else -> FieldType.Text
                    },
                    required = field.bool("required") != false,
                    options = field.array("options")?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty(),
                )
            },
            externalUrl = data.string("external_url")?.takeIf { it.startsWith("https://") },
        )
    }

    suspend fun submit(token: String, id: Long, values: Map<String, String>): ApiResult<SubmitResult> =
        http.post("/forms/$id", buildJsonObject { putJsonObject("values") { values.forEach { (key, value) -> put(key, value) } } }, token) { data ->
            SubmitResult(
                id = data.long("id") ?: 0,
                trackingCode = data.string("tracking_code").orEmpty(),
                message = data.string("message").orEmpty(),
            )
        }

    suspend fun detail(token: String, id: Long): ApiResult<RequestDetail> = http.get("/requests/$id", token) { data ->
        val status = data.obj("status")

        RequestDetail(
            id = data.long("id") ?: id,
            trackingCode = data.string("tracking_code").orEmpty(),
            title = data.string("title").orEmpty(),
            subtitle = data.string("subtitle"),
            dateLabel = data.string("date_label"),
            status = requestStatus(status?.string("key")),
            statusLabel = status?.string("label").orEmpty(),
            contentHtml = data.string("content_html").orEmpty(),
            // Older servers do not say: the same rule as newer ones.
            pdfAvailable = data.bool("pdf_available") ?: (requestStatus(status?.string("key")) == RequestStatus.Approved),
            reviews = data.objects("reviews").map { review ->
                val reviewStatus = review.obj("status")
                Review(
                    reviewer = review.string("reviewer"),
                    reviewerTitle = review.string("reviewer_title"),
                    status = requestStatus(reviewStatus?.string("key")),
                    statusLabel = reviewStatus?.string("label").orEmpty(),
                    comment = review.string("comment"),
                    dateLabel = review.string("date_label"),
                )
            },
        )
    }

    companion object {
        fun pdfPath(id: Long) = "/requests/$id/pdf"

        private fun parseSummary(data: JsonObject): FormSummary? = data.long("id")?.let { id ->
            FormSummary(
                id = id,
                title = data.string("title").orEmpty(),
                subtitle = data.string("subtitle"),
                external = data.bool("external") == true,
                unique = data.bool("unique") == true,
            )
        }
    }
}

internal fun requestStatus(key: String?): RequestStatus = when (key) {
    "needs-action" -> RequestStatus.NeedsAction
    "rejected" -> RequestStatus.Rejected
    "approved" -> RequestStatus.Approved
    "reviewing" -> RequestStatus.Reviewing
    else -> RequestStatus.Pending
}
