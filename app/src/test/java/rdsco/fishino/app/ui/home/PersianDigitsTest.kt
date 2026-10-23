package rdsco.fishino.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDigitsTest {

    @Test
    fun `only latin digits change`() {
        assertEquals("فیش حقوقی مهر ۱۴۰۵ · کد ۱۰۲۳", "فیش حقوقی مهر 1405 · کد 1023".persianDigits())
    }

    @Test
    fun `typed persian and arabic digits become latin`() {
        assertEquals("1405 و 123", "۱۴۰۵ و ١٢٣".latinDigits())
    }
}
