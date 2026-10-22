package rdsco.fishino.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStatusTest {

    @Test
    fun `a saved password belongs to one employer`() {
        val status = VaultStatus("https://hrm.a.com/api/v1", "ali", emptySet())

        assertEquals("ali", status.savedFor("https://hrm.a.com/api/v1"))
        assertNull(status.savedFor("https://hrm.b.com/api/v1"))
        assertNull(VaultStatus(null, null, emptySet()).savedFor("https://hrm.a.com/api/v1"))
    }

    @Test
    fun `declining is per employer and username, ignoring case`() {
        val status = VaultStatus(null, null, setOf("https://hrm.a.com/api/v1\nali"))

        assertTrue(status.isDeclined("https://hrm.a.com/api/v1", "Ali"))
        assertFalse(status.isDeclined("https://hrm.a.com/api/v1", "reza"))
        assertFalse(status.isDeclined("https://hrm.b.com/api/v1", "ali"))
    }
}
