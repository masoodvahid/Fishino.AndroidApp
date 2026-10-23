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

data class NewsItem(
    val id: Long,
    val title: String,
    val typeKey: String,
    val typeLabel: String,
    val dateLabel: String?,
    val viewCount: Int,
    val hasImage: Boolean,
    val summary: String,
)

data class NewsAttachment(val index: Int, val name: String, val size: Long, val mime: String)

data class NewsDetail(
    val item: NewsItem,
    val contentHtml: String,
    val gallery: List<Pair<Int, String>>,
    val attachments: List<NewsAttachment>,
)

/** News and announcements (/news). */
class NewsApi(private val http: ApiClient) {

    suspend fun list(token: String, page: Int): ApiResult<Page<NewsItem>> =
        http.get("/news?page=$page", token) { PanelApi.parsePage(it, ::parseItem) }

    suspend fun detail(token: String, id: Long): ApiResult<NewsDetail> = http.get("/news/$id", token) { data ->
        NewsDetail(
            item = parseItem(data) ?: throw IllegalStateException("no id"),
            contentHtml = data.string("content_html").orEmpty(),
            gallery = data.objects("gallery").mapNotNull { image -> image.int("index")?.let { it to image.string("name").orEmpty() } },
            attachments = data.objects("attachments").mapNotNull { file ->
                NewsAttachment(
                    index = file.int("index") ?: return@mapNotNull null,
                    name = file.string("name") ?: "پیوست",
                    size = file.long("size") ?: 0,
                    mime = file.string("mime") ?: "application/octet-stream",
                )
            },
        )
    }

    companion object {
        fun imagePath(id: Long) = "/news/$id/image"

        fun galleryPath(id: Long, index: Int) = "/news/$id/gallery/$index"

        fun attachmentPath(id: Long, index: Int) = "/news/$id/attachments/$index"

        internal fun parseItem(data: JsonObject): NewsItem? {
            val id = data.long("id") ?: return null
            val type = data.obj("type")

            return NewsItem(
                id = id,
                title = data.string("title").orEmpty(),
                typeKey = type?.string("key").orEmpty(),
                typeLabel = type?.string("label").orEmpty(),
                dateLabel = data.string("date_label"),
                viewCount = data.int("view_count") ?: 0,
                hasImage = data.bool("has_image") == true,
                summary = data.string("summary").orEmpty(),
            )
        }
    }
}
