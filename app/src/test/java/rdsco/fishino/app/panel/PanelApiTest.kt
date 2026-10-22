package rdsco.fishino.app.panel

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult

class PanelApiTest {

    private fun api(server: MockWebServer) = PanelApi(ApiClient(OkHttpClient(), server.url("/api/v1/").toString(), "t"))

    @Test
    fun `home reads the profile, documents and requests`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{
                    "profile":{"id":7,"username":"ali","full_name":"علی رضایی","national_code":"0012345678","mobile":"09123456789",
                      "employee_number":"4512","job_title":"کارشناس","work_place":null,"employer":"شرکت نمونه"},
                    "requirement":null,"pending_signatures":1,
                    "recent_reports":[
                      {"id":3,"title":"فیش حقوقی مهر 1405","template_title":"فیش حقوقی","date_label":"مهر 1405",
                       "needs_signature":true,"signature_required":true,"signed":false},
                      {"title":"بدون شناسه"}],
                    "recent_requests":[
                      {"id":9,"tracking_code":"1009","title":"مرخصی","status":{"key":"needs-action","label":"نیازمند تکمیل"},
                       "comment":"تاریخ را اصلاح کنید.","updated_label":"2 روز پیش"},
                      {"id":10,"tracking_code":"1010","title":"وام","status":{"key":"something-new","label":"؟"},"comment":null}]
                    }}""",
                ),
            )

            val home = (api(server).home("1|abc") as ApiResult.Ok).value

            assertEquals("علی رضایی", home.profile.fullName)
            assertEquals("4512", home.profile.employeeNumber)
            assertNull(home.profile.workPlace)
            assertNull(home.requirement)
            assertEquals(1, home.pendingSignatures)
            // An item without an id is skipped, not a failure of the whole screen.
            assertEquals(1, home.recentDocuments.size)
            assertTrue(home.recentDocuments[0].needsSignature)
            assertTrue(home.recentDocuments[0].signatureRequired)
            assertEquals(RequestStatus.NeedsAction, home.recentRequests[0].status)
            assertEquals("تاریخ را اصلاح کنید.", home.recentRequests[0].comment)
            assertEquals(RequestStatus.Pending, home.recentRequests[1].status)
            assertNull(home.recentRequests[1].comment)

            val request = server.takeRequest()
            assertEquals("/api/v1/home", request.path)
            assertEquals("Bearer 1|abc", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `lists are read page by page`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"items":[{"id":5,"title":"قرارداد","signed":true}],"page":2,"has_more":false,"total":16}}""",
                ),
            )

            val page = (api(server).documents("1|abc", 2) as ApiResult.Ok).value

            assertEquals(2, page.page)
            assertFalse(page.hasMore)
            assertEquals(16, page.total)
            assertTrue(page.items.single().signed)
            assertEquals("/api/v1/reports?page=2", server.takeRequest().path)
        }
    }

    @Test
    fun `a pending requirement and a refused token keep their codes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(403).setBody(
                    """{"ok":false,"error":{"code":"requirement_pending","message":"ابتدا قرارداد را امضا کنید."}}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))

            val pending = (api(server).requests("t", 1) as ApiResult.Failed).error
            assertEquals("requirement_pending", pending.code)
            assertEquals("ابتدا قرارداد را امضا کنید.", pending.message)

            assertEquals(ApiError.UNAUTHORIZED, (api(server).home("t") as ApiResult.Failed).error.code)
        }
    }
}
