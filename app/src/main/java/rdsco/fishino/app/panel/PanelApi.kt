package rdsco.fishino.app.panel

import kotlinx.serialization.json.JsonObject
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.bool
import rdsco.fishino.app.auth.int
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.obj
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string

data class Profile(
    val fullName: String,
    val username: String,
    val nationalCode: String?,
    val mobile: String?,
    val employeeNumber: String?,
    val jobTitle: String?,
    val workPlace: String?,
    val employer: String?,
)

/** Something the user must do on the web panel first (mobile number, password change, agreement). */
data class Requirement(val key: String, val title: String, val message: String)

data class DocumentItem(
    val id: Long,
    val title: String,
    val templateTitle: String,
    val dateLabel: String,
    val needsSignature: Boolean,
    /** A mandatory signature: the other documents stay hidden until it is signed. */
    val signatureRequired: Boolean,
    val signed: Boolean,
)

enum class RequestStatus { NeedsAction, Rejected, Approved, Reviewing, Pending }

data class RequestItem(
    val id: Long,
    val trackingCode: String,
    val title: String,
    val status: RequestStatus,
    val statusLabel: String,
    /** The latest reviewer's comment. */
    val comment: String?,
    /** "۲ روز پیش" — written by the server. */
    val updatedLabel: String?,
)

data class HomeData(
    val profile: Profile,
    val requirement: Requirement?,
    val pendingSignatures: Int,
    val recentDocuments: List<DocumentItem>,
    val recentRequests: List<RequestItem>,
)

data class Page<T>(val items: List<T>, val page: Int, val hasMore: Boolean, val total: Int)

/** The signed-in employee's own data (/home, /reports, /requests). */
class PanelApi(private val http: ApiClient) {

    suspend fun home(token: String): ApiResult<HomeData> = http.get("/home", token) { data ->
        HomeData(
            profile = parseProfile(data.obj("profile") ?: throw IllegalStateException("no profile")),
            requirement = data.obj("requirement")?.let(::parseRequirement),
            pendingSignatures = data.int("pending_signatures") ?: 0,
            recentDocuments = data.objects("recent_reports").mapNotNull(::parseDocument),
            recentRequests = data.objects("recent_requests").mapNotNull(::parseRequest),
        )
    }

    suspend fun documents(token: String, page: Int): ApiResult<Page<DocumentItem>> =
        http.get("/reports?page=$page", token) { parsePage(it, ::parseDocument) }

    suspend fun requests(token: String, page: Int): ApiResult<Page<RequestItem>> =
        http.get("/requests?page=$page", token) { parsePage(it, ::parseRequest) }

    internal companion object {
        fun parseProfile(data: JsonObject) = Profile(
            fullName = data.string("full_name").orEmpty(),
            username = data.string("username").orEmpty(),
            nationalCode = data.string("national_code"),
            mobile = data.string("mobile"),
            employeeNumber = data.string("employee_number"),
            jobTitle = data.string("job_title"),
            workPlace = data.string("work_place"),
            employer = data.string("employer"),
        )

        fun parseRequirement(data: JsonObject): Requirement? {
            val key = data.string("key") ?: return null
            return Requirement(key, title = data.string("title").orEmpty(), message = data.string("message").orEmpty())
        }

        fun parseDocument(data: JsonObject): DocumentItem? {
            val id = data.long("id") ?: return null

            return DocumentItem(
                id = id,
                title = data.string("title").orEmpty(),
                templateTitle = data.string("template_title").orEmpty(),
                dateLabel = data.string("date_label").orEmpty(),
                needsSignature = data.bool("needs_signature") == true,
                signatureRequired = data.bool("signature_required") == true,
                signed = data.bool("signed") == true,
            )
        }

        fun parseRequest(data: JsonObject): RequestItem? {
            val id = data.long("id") ?: return null
            val status = data.obj("status")

            return RequestItem(
                id = id,
                trackingCode = data.string("tracking_code").orEmpty(),
                title = data.string("title").orEmpty(),
                status = when (status?.string("key")) {
                    "needs-action" -> RequestStatus.NeedsAction
                    "rejected" -> RequestStatus.Rejected
                    "approved" -> RequestStatus.Approved
                    "reviewing" -> RequestStatus.Reviewing
                    else -> RequestStatus.Pending
                },
                statusLabel = status?.string("label").orEmpty(),
                comment = data.string("comment"),
                updatedLabel = data.string("updated_label"),
            )
        }

        fun <T> parsePage(data: JsonObject, parse: (JsonObject) -> T?): Page<T> = Page(
            items = data.objects("items").mapNotNull(parse),
            page = data.int("page") ?: 1,
            hasMore = data.bool("has_more") == true,
            total = data.int("total") ?: 0,
        )
    }
}
