package rdsco.fishino.app.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val session = Session("1|abc", expiresAtMillis = 15 * 60_000L, AppUser(1, "ali", "علی", null))

    @Test
    fun `the token is refreshed three minutes before it expires`() {
        assertEquals(12 * 60_000L, SessionManager.refreshDelay(15 * 60_000L, 0))
        assertEquals(5_000L, SessionManager.refreshDelay(60_000L, 0))
    }

    @Test
    fun `leaving the app for more than a minute signs out`() {
        assertFalse(SessionManager.shouldSignOut(0, SessionManager.BACKGROUND_GRACE_MS))
        assertTrue(SessionManager.shouldSignOut(0, SessionManager.BACKGROUND_GRACE_MS + 1))
    }

    @Test
    fun `coming back after a long break ends the session`() {
        var now = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope) { now }
        // An unreachable address: the logout call fails quietly.
        manager.signedIn(session, AuthApi(OkHttpClient(), "https://127.0.0.1:1/api/v1", "t"))

        manager.onAppBackground()
        now = 30_000
        manager.onAppForeground()
        assertEquals(session, manager.session.value)

        manager.onAppBackground()
        now += SessionManager.BACKGROUND_GRACE_MS + 1
        manager.onAppForeground()
        assertNull(manager.session.value)
        // Remembered so the login screen can offer the fingerprint right away.
        assertEquals(SessionEnd.Timeout, manager.lastEnd.value?.first)

        scope.cancel()
    }

    @Test
    fun `a refused token ends its own session only`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope)
        manager.signedIn(session, AuthApi(OkHttpClient(), "https://127.0.0.1:1/api/v1", "t"))

        manager.onUnauthorized("1|old")
        assertEquals(session, manager.session.value)

        manager.onUnauthorized(session.token)
        assertNull(manager.session.value)
        assertEquals(SessionEnd.Revoked, manager.lastEnd.value?.first)

        scope.cancel()
    }
}
