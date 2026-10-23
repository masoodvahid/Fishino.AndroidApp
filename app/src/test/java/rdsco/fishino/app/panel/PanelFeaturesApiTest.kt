package rdsco.fishino.app.panel

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.UploadPart

class PanelFeaturesApiTest {

    private fun client(server: MockWebServer) = ApiClient(OkHttpClient(), server.url("/api/v1/").toString(), "t")

    @Test
    fun `news detail keeps its media`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"id":4,"title":"خبر","type":{"key":"news","label":"خبر"},"date_label":"1405/08/01",
                    "view_count":3,"has_image":true,"content_html":"<p>متن</p>",
                    "gallery":[{"index":0,"name":"تصویر"}],"attachments":[{"index":0,"name":"a.pdf","size":10,"mime":"application/pdf"}]}}""",
                ),
            )

            val detail = (NewsApi(client(server)).detail("t", 4) as ApiResult.Ok).value

            assertEquals("خبر", detail.item.typeLabel)
            assertTrue(detail.item.hasImage)
            assertEquals(listOf(0 to "تصویر"), detail.gallery)
            assertEquals("application/pdf", detail.attachments.single().mime)
            assertEquals("/api/v1/news/4", server.takeRequest().path)
        }
    }

    @Test
    fun `a form is read with its fields and submitted with values`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"id":2,"title":"وام","subtitle":null,"external":true,"unique":false,"help_html":null,"preview_html":"<p>x</p>",
                    "fields":[{"key":"1","label":"نوع","type":"dropdown","required":true,"options":["ضروری","مسکن"]},
                              {"key":"2","label":"مبلغ","type":"currency","required":false,"options":[]}],
                    "external_url":"http://insecure.example"}}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(422).setBody("""{"ok":false,"error":{"code":"validation_failed","message":"x","fields":{"2":"مبلغ فقط باید شامل عدد صحیح باشد."}}}"""))

            val api = RequestApi(client(server))
            val form = (api.form("t", 2) as ApiResult.Ok).value
            assertEquals(FieldType.Dropdown, form.fields[0].type)
            assertEquals(listOf("ضروری", "مسکن"), form.fields[0].options)
            assertEquals(FieldType.Currency, form.fields[1].type)
            // Only https addresses are opened.
            assertNull(form.externalUrl)

            val error = (api.submit("t", 2, mapOf("1" to "مسکن", "2" to "abc")) as ApiResult.Failed).error
            assertEquals("مبلغ فقط باید شامل عدد صحیح باشد.", error.fields["2"])

            server.takeRequest()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body, body.contains("\"values\":{\"1\":\"مسکن\",\"2\":\"abc\"}"))
        }
    }

    @Test
    fun `the profile is read section by section`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{
                    "user":{"full_name":"علی رضایی","username":"ali","mobile":"09123456789","has_avatar":true},
                    "requirement":null,
                    "pending":[{"key":"signature","tab":"signature","title":"ثبت تصویر امضا الزامی است","message":"…"}],
                    "mobile":{"number":"09123456789","shahkar_enabled":true,"validity_days":10,"step":"shahkar",
                      "items":[{"key":"mobile","label":"تأیید","complete":true,"message":"ok","remaining":"۹ روز"}]},
                    "signature":{"has":false},
                    "documents":{"enabled":true,"description":null,"fields":[{"id":3,"title":"کارت ملی","required":true,"formats":["jpg","pdf"],"max_kb":256,"comment":null}],"files":[]},
                    "information":{"editable":true,"fields":[{"key":"gender","label":"جنسیت","value":"1","pending":null,"has_pending":false,
                      "options":[{"value":"1","label":"آقا"},{"value":"0","label":"خانم"}]}]},
                    "bale":{"available":false,"connected":false,"username":null,"bot_url":null,"code_lifetime_minutes":10},
                    "agreement":{"requires_signature":true,"signed":false}}}""",
                ),
            )

            val profile = (ProfileApi(client(server)).profile("t") as ApiResult.Ok).value

            assertEquals("signature", profile.pending.single().tab)
            assertEquals("shahkar", profile.mobile.step)
            assertEquals("۹ روز", profile.mobile.items.single().remaining)
            assertEquals(listOf("jpg", "pdf"), profile.documents.fields.single().formats)
            assertEquals("خانم", profile.info.single().options?.get(1)?.label)
            assertEquals("0", profile.info.single().options?.get(1)?.value)
            assertTrue(profile.user.hasAvatar)
            assertTrue(profile.agreementRequiresSignature)
        }
    }

    @Test
    fun `files are uploaded as multipart and downloaded with their name`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"message":"فایل با موفقیت بارگذاری شد."}}"""))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/pdf")
                    .setHeader("Content-Disposition", "attachment; filename=\"a.pdf\"; filename*=UTF-8''%D8%A2%DB%8C%DB%8C%D9%86.pdf")
                    .setBody(Buffer().write(byteArrayOf(1, 2, 3))),
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(ByteArray(2048))))

            val http = client(server)
            val message = (ProfileApi(http).uploadDocument("t", 3, UploadPart("file", "document.jpg", "image/jpeg", byteArrayOf(9))) as ApiResult.Ok).value
            assertEquals("فایل با موفقیت بارگذاری شد.", message)
            val upload = server.takeRequest()
            assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            assertTrue(upload.body.readUtf8().contains("filename=\"document.jpg\""))

            val file = (http.download("/x", "t") as ApiResult.Ok).value
            assertEquals("application/pdf", file.contentType)
            assertEquals("آیین.pdf", file.fileName)
            assertEquals(3, file.bytes.size)

            assertEquals(ApiClient.FILE_TOO_LARGE, (http.download("/y", "t", maxBytes = 1024) as ApiResult.Failed).error.code)
        }
    }

    @Test
    fun `inbox messages, documents and home extras are read`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"items":[{"id":9,"title":"درخواست بررسی شد","body":"متن","is_read":false,
                    "link":{"type":"request","id":12},"date_label":"1405/08/01 10:00"},{"id":8,"title":"x","body":"","is_read":true,"link":null}],
                    "has_more":true,"unread_count":3}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"id":5,"title":"فیش حقوقی مهر 1405","template_title":"فیش حقوقی","date_label":"مهر 1405",
                    "can_sign":true,"signature_required":false,"signed":false,"signed_label":null,"masked_mobile":"0912•••789",
                    "requirements":[{"key":"mobile","label":"تأیید","complete":true,"message":"ok","tab":"mobile"},
                                    {"key":"shahkar","label":"شاهکار","complete":false,"message":"…","tab":"mobile"}]}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"profile":{"full_name":"علی"},"requirement":null,"pending_signatures":0,"recent_reports":[],"recent_requests":[],
                    "latest_news":[{"id":3,"title":"اطلاعیه","type":{"key":"announcement","label":"اطلاعیه"},"has_image":false}],
                    "unread_notifications":2,
                    "channels":[{"key":"telegram","label":"تلگرام","url":"https://t.me/x"},{"key":"bad","label":"x","url":"javascript:alert(1)"},
                                {"key":"phone","label":"تلفن","url":"tel:02112345678"}]}}""",
                ),
            )

            val http = client(server)
            val inbox = (InboxApi(http).list("t", before = 20) as ApiResult.Ok).value
            assertEquals(MessageLink("request", 12), inbox.items[0].link)
            assertNull(inbox.items[1].link)
            assertEquals(3, inbox.unread)
            assertEquals("/api/v1/notifications?before=20", server.takeRequest().path)

            val document = (DocumentApi(http).detail("t", 5) as ApiResult.Ok).value
            assertEquals(false, document.readyToSign)
            assertEquals("mobile", document.requirements[1].tab)

            val home = (PanelApi(http).home("t") as ApiResult.Ok).value
            assertEquals("اطلاعیه", home.latestNews.single().title)
            assertEquals(2, home.unreadNotifications)
            assertEquals(listOf("telegram", "phone"), home.channels.map { it.key })
        }
    }

    @Test
    fun `shared file names never leave their folder`() {
        assertEquals("passwd", SharedFiles.safeName("../../etc/passwd"))
        assertEquals("آیین‌نامه_1.pdf".replace("‌", "_"), SharedFiles.safeName("آیین‌نامه 1.pdf"))
        assertEquals("file", SharedFiles.safeName("../"))
    }
}
