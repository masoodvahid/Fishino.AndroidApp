package rdsco.fishino.app.employer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployerCodeTest {

    @Test
    fun `persian and arabic digits become ascii and extras are dropped`() {
        assertEquals("482913", EmployerCode.sanitize("۴۸۲۹۱۳"))
        assertEquals("482913", EmployerCode.sanitize("٤٨٢٩١٣"))
        assertEquals("482913", EmployerCode.sanitize(" 48-29 13 99"))
        assertEquals("", EmployerCode.sanitize("abc"))
    }

    @Test
    fun `only six digits are complete`() {
        assertTrue(EmployerCode.isComplete("482913"))
        assertFalse(EmployerCode.isComplete("48291"))
        assertFalse(EmployerCode.isComplete("48291a"))
    }
}
