package rdsco.fishino.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rdsco.fishino.app.ui.shell.Screen

class ScreenTest {

    @Test
    fun `every screen survives saving its route`() {
        listOf(
            Screen.Home, Screen.Documents, Screen.Requests, Screen.News, Screen.Profile, Screen.Forms,
            Screen.Inbox, Screen.Privacy, Screen.NewsDetail(12), Screen.RequestDetail(7), Screen.DocumentDetail(9), Screen.DocumentView(9), Screen.FormFill(3), Screen.ProfileSection("mobile"),
        ).forEach { assertEquals(it, Screen.parse(it.route)) }
    }

    @Test
    fun `unknown routes are dropped`() {
        assertNull(Screen.parse("admin"))
        assertNull(Screen.parse("news/x"))
    }
}
