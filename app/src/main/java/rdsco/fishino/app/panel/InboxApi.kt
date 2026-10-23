package rdsco.fishino.app.panel

import kotlinx.serialization.json.buildJsonObject
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.bool
import rdsco.fishino.app.auth.int
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.obj
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string

/** What a message points to: a request or a document. */
data class MessageLink(val type: String, val id: Long)

data class InboxMessage(
    val id: Long,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val link: MessageLink?,
    val dateLabel: String?,
)

data class InboxPage(val items: List<InboxMessage>, val hasMore: Boolean, val unread: Int)

/** The inbox (/notifications), the same messages as /user/inbox on the web. */
class InboxApi(private val http: ApiClient) {

    /** Newest first; [before] continues after the last message shown. */
    suspend fun list(token: String, before: Long?): ApiResult<InboxPage> =
        http.get("/notifications" + (before?.let { "?before=$it" } ?: ""), token) { data ->
            InboxPage(
                items = data.objects("items").mapNotNull { item ->
                    InboxMessage(
                        id = item.long("id") ?: return@mapNotNull null,
                        title = item.string("title").orEmpty(),
                        body = item.string("body").orEmpty(),
                        isRead = item.bool("is_read") == true,
                        link = item.obj("link")?.let { link ->
                            val type = link.string("type") ?: return@let null
                            link.long("id")?.let { MessageLink(type, it) }
                        },
                        dateLabel = item.string("date_label"),
                    )
                },
                hasMore = data.bool("has_more") == true,
                unread = data.int("unread_count") ?: 0,
            )
        }

    suspend fun markRead(token: String, id: Long): ApiResult<Int> =
        http.post("/notifications/$id/read", buildJsonObject {}, token) { it.int("unread_count") ?: 0 }

    suspend fun markAllRead(token: String): ApiResult<Int> =
        http.post("/notifications/read-all", buildJsonObject {}, token) { it.int("unread_count") ?: 0 }
}
