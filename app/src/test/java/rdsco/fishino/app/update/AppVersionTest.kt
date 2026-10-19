package rdsco.fishino.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `tags and version names parse`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("V1.2"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse(" 2 "))
    }

    @Test
    fun `unparsable versions are rejected`() {
        assertNull(AppVersion.parse("v1.2.3-beta"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun `comparison is numeric, not textual`() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertTrue(AppVersion.parse("1.2.10")!! > AppVersion.parse("1.2.9")!!)
        assertEquals(0, AppVersion.parse("1.2")!!.compareTo(AppVersion.parse("v1.2.0")!!))
    }
}
