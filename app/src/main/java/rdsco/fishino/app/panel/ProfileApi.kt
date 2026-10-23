package rdsco.fishino.app.panel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.UploadPart
import rdsco.fishino.app.auth.array
import rdsco.fishino.app.auth.bool
import rdsco.fishino.app.auth.int
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.obj
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string

data class ProfileUser(
    val fullName: String,
    val username: String,
    val nationalCode: String?,
    val mobile: String?,
    val employeeNumber: String?,
    val jobTitle: String?,
    val workPlace: String?,
    val employer: String?,
    val hasAvatar: Boolean,
)

data class VerificationItem(val key: String, val label: String, val complete: Boolean, val message: String, val remaining: String?)

/** [step]: "mobile" (enter the number), "shahkar" or "done". */
data class MobileSection(
    val number: String?,
    val shahkarEnabled: Boolean,
    val validityDays: Int,
    val step: String,
    val items: List<VerificationItem>,
)

data class UploadField(val id: Long, val title: String, val required: Boolean, val formats: List<String>, val maxKb: Int, val comment: String?)

data class UploadedDocument(val id: Long, val title: String, val uploadedLabel: String?, val canDelete: Boolean, val isImage: Boolean)

data class DocumentsSection(val enabled: Boolean, val description: String?, val fields: List<UploadField>, val files: List<UploadedDocument>)

data class InfoOption(val value: String, val label: String)

data class InfoField(
    val key: String,
    val label: String,
    val value: String?,
    /** A change waiting for the administrator. */
    val pending: String?,
    val hasPending: Boolean,
    val options: List<InfoOption>?,
)

data class BaleSection(val available: Boolean, val connected: Boolean, val username: String?, val botUrl: String?, val codeLifetimeMinutes: Int)

data class ProfileData(
    val user: ProfileUser,
    /** A step the web would force first (mobile number, first password). */
    val requirement: Requirement?,
    /** Everything still to do: the onboarding step, the agreement and its prerequisites, required uploads. */
    val pending: List<Requirement>,
    val mobile: MobileSection,
    val hasSignature: Boolean,
    val documents: DocumentsSection,
    val infoEditable: Boolean,
    val info: List<InfoField>,
    val bale: BaleSection,
    val agreementRequiresSignature: Boolean,
    val agreementSigned: Boolean,
)

data class CodeSent(val smsToken: String, val message: String, val codeLength: Int)

data class BaleCode(val code: String, val command: String, val botUrl: String?, val lifetimeMinutes: Int, val message: String)

data class LoginLog(val dateLabel: String?, val timeLabel: String?, val description: String, val ip: String?)

data class AgreementSignature(val signerName: String, val signerMobile: String, val dateLabel: String, val timeLabel: String)

data class AgreementData(
    val contentHtml: String,
    val requiresSignature: Boolean,
    val signed: Boolean,
    val signature: AgreementSignature?,
    /** What must be done before signing (mobile, signature image, Shahkar). */
    val prerequisite: Requirement?,
)

/** The employee's profile (/profile/…), section by section as on the web. */
class ProfileApi(private val http: ApiClient) {

    suspend fun profile(token: String): ApiResult<ProfileData> = http.get("/profile", token, ::parseProfile)

    suspend fun changePassword(token: String, password: String, confirmation: String): ApiResult<String> =
        http.post("/profile/password", buildJsonObject { put("password", password); put("password_confirmation", confirmation) }, token, ::message)

    suspend fun sendMobileCode(token: String, mobile: String): ApiResult<CodeSent> =
        http.post("/profile/mobile/send", buildJsonObject { put("mobile", mobile) }, token, ::codeSent)

    suspend fun verifyMobileCode(token: String, code: String, smsToken: String): ApiResult<String> =
        http.post("/profile/mobile/verify", buildJsonObject { put("code", code); put("sms_token", smsToken) }, token, ::message)

    suspend fun verifyShahkar(token: String): ApiResult<String> = http.post("/profile/mobile/shahkar", buildJsonObject {}, token, ::message)

    suspend fun requestChange(token: String, key: String, value: String): ApiResult<String> =
        http.post("/profile/information", buildJsonObject { put("key", key); put("value", value) }, token, ::message)

    suspend fun baleCode(token: String): ApiResult<BaleCode> = http.post("/profile/bale/code", buildJsonObject {}, token) { data ->
        BaleCode(
            code = data.string("code").orEmpty(),
            command = data.string("command").orEmpty(),
            botUrl = data.string("bot_url")?.takeIf { it.startsWith("https://") },
            lifetimeMinutes = data.int("lifetime_minutes") ?: 10,
            message = data.string("message").orEmpty(),
        )
    }

    suspend fun baleDisconnect(token: String): ApiResult<String> = http.delete("/profile/bale", token, ::message)

    suspend fun logs(token: String): ApiResult<List<LoginLog>> = http.get("/profile/logs", token) { data ->
        data.objects("items").map { log ->
            LoginLog(log.string("date_label"), log.string("time_label"), log.string("description").orEmpty(), log.string("ip"))
        }
    }

    suspend fun uploadSignature(token: String, png: ByteArray): ApiResult<String> =
        http.upload("/profile/signature", token, UploadPart("signature", "signature.png", "image/png", png), ::message)

    suspend fun uploadAvatar(token: String, jpeg: ByteArray): ApiResult<String> =
        http.upload("/profile/avatar", token, UploadPart("avatar", "avatar.jpg", "image/jpeg", jpeg), ::message)

    suspend fun deleteAvatar(token: String): ApiResult<String> = http.delete("/profile/avatar", token, ::message)

    suspend fun uploadDocument(token: String, fieldId: Long, file: UploadPart): ApiResult<String> =
        http.upload("/profile/documents/$fieldId", token, file, ::message)

    suspend fun deleteDocument(token: String, fileId: Long): ApiResult<String> = http.delete("/profile/documents/$fileId", token, ::message)

    suspend fun agreement(token: String): ApiResult<AgreementData> = http.get("/profile/agreement", token) { data ->
        AgreementData(
            contentHtml = data.string("content_html").orEmpty(),
            requiresSignature = data.bool("requires_signature") == true,
            signed = data.bool("signed") == true,
            signature = data.obj("signature")?.let { signature ->
                AgreementSignature(
                    signerName = signature.string("signer_name").orEmpty(),
                    signerMobile = signature.string("signer_mobile").orEmpty(),
                    dateLabel = signature.string("date_label").orEmpty(),
                    timeLabel = signature.string("time_label").orEmpty(),
                )
            },
            prerequisite = data.obj("prerequisite")?.let(PanelApi::parseRequirement),
        )
    }

    suspend fun sendAgreementCode(token: String): ApiResult<CodeSent> = http.post("/profile/agreement/send", buildJsonObject {}, token, ::codeSent)

    suspend fun verifyAgreement(token: String, code: String, smsToken: String): ApiResult<String> =
        http.post("/profile/agreement/verify", buildJsonObject { put("code", code); put("sms_token", smsToken) }, token, ::message)

    companion object {
        const val AVATAR_PATH = "/profile/avatar"
        const val SIGNATURE_PATH = "/profile/signature"
        const val AGREEMENT_PDF_PATH = "/profile/agreement/pdf"

        fun documentPath(fileId: Long) = "/profile/documents/$fileId/file"

        internal fun message(data: JsonObject): String = data.string("message").orEmpty()

        private fun codeSent(data: JsonObject) = CodeSent(
            smsToken = data.string("sms_token") ?: throw IllegalStateException("no token"),
            message = data.string("message").orEmpty(),
            codeLength = data.int("code_length") ?: 5,
        )

        internal fun parseProfile(data: JsonObject): ProfileData {
            val user = data.obj("user") ?: throw IllegalStateException("no user")
            val mobile = data.obj("mobile")
            val documents = data.obj("documents")
            val information = data.obj("information")
            val bale = data.obj("bale")
            val agreement = data.obj("agreement")

            return ProfileData(
                user = ProfileUser(
                    fullName = user.string("full_name").orEmpty(),
                    username = user.string("username").orEmpty(),
                    nationalCode = user.string("national_code"),
                    mobile = user.string("mobile"),
                    employeeNumber = user.string("employee_number"),
                    jobTitle = user.string("job_title"),
                    workPlace = user.string("work_place"),
                    employer = user.string("employer"),
                    hasAvatar = user.bool("has_avatar") == true,
                ),
                requirement = data.obj("requirement")?.let(PanelApi::parseRequirement),
                pending = data.objects("pending").mapNotNull(PanelApi::parseRequirement),
                mobile = MobileSection(
                    number = mobile?.string("number"),
                    shahkarEnabled = mobile?.bool("shahkar_enabled") == true,
                    validityDays = mobile?.int("validity_days") ?: 0,
                    step = mobile?.string("step") ?: "mobile",
                    items = mobile?.objects("items").orEmpty().map { item ->
                        VerificationItem(
                            key = item.string("key").orEmpty(),
                            label = item.string("label").orEmpty(),
                            complete = item.bool("complete") == true,
                            message = item.string("message").orEmpty(),
                            remaining = item.string("remaining"),
                        )
                    },
                ),
                hasSignature = data.obj("signature")?.bool("has") == true,
                documents = DocumentsSection(
                    enabled = documents?.bool("enabled") == true,
                    description = documents?.string("description"),
                    fields = documents?.objects("fields").orEmpty().mapNotNull { field ->
                        UploadField(
                            id = field.long("id") ?: return@mapNotNull null,
                            title = field.string("title").orEmpty(),
                            required = field.bool("required") == true,
                            formats = field.array("formats")?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty(),
                            maxKb = field.int("max_kb") ?: 1,
                            comment = field.string("comment"),
                        )
                    },
                    files = documents?.objects("files").orEmpty().mapNotNull { file ->
                        UploadedDocument(
                            id = file.long("id") ?: return@mapNotNull null,
                            title = file.string("title").orEmpty(),
                            uploadedLabel = file.string("uploaded_label"),
                            canDelete = file.bool("can_delete") == true,
                            isImage = file.bool("is_image") == true,
                        )
                    },
                ),
                infoEditable = information?.bool("editable") == true,
                info = information?.objects("fields").orEmpty().mapNotNull { field ->
                    InfoField(
                        key = field.string("key") ?: return@mapNotNull null,
                        label = field.string("label").orEmpty(),
                        value = field.string("value"),
                        pending = field.string("pending"),
                        hasPending = field.bool("has_pending") == true,
                        options = field.array("options")?.let { options ->
                            options.mapNotNull { option ->
                                val item = runCatching { option as JsonObject }.getOrNull() ?: return@mapNotNull null
                                InfoOption(
                                    value = runCatching { item["value"]?.jsonPrimitive?.content }.getOrNull().orEmpty(),
                                    label = item.string("label").orEmpty(),
                                )
                            }
                        },
                    )
                },
                bale = BaleSection(
                    available = bale?.bool("available") == true,
                    connected = bale?.bool("connected") == true,
                    username = bale?.string("username"),
                    botUrl = bale?.string("bot_url")?.takeIf { it.startsWith("https://") },
                    codeLifetimeMinutes = bale?.int("code_lifetime_minutes") ?: 10,
                ),
                agreementRequiresSignature = agreement?.bool("requires_signature") == true,
                agreementSigned = agreement?.bool("signed") == true,
            )
        }
    }
}
