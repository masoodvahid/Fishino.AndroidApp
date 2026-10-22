package rdsco.fishino.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpCodeTest {

    @Test
    fun `the code of the expected length is taken from the sms`() {
        assertEquals("27493", OtpCode.extract("کد ورود شما به فیشی‌نو: 27493\nلغو11", 5))
        assertEquals("27493", OtpCode.extract("کد ورود: ۲۷۴۹۳ – ۱۴۰۵/۰۸/۰۱", 5))
        assertEquals("1234", OtpCode.extract("code 1234", 5))
        assertNull(OtpCode.extract("سلام، خوش آمدید", 5))
    }

    @Test
    fun `mobile numbers are normalized`() {
        listOf("09123456789", "9123456789", "+98 912 345 6789", "00989123456789", "۰۹۱۲۳۴۵۶۷۸۹").forEach {
            assertEquals(it, "09123456789", OtpCode.normalizeMobile(it))
        }
        listOf("", "0912345678", "08123456789", "abc").forEach { assertNull(it, OtpCode.normalizeMobile(it)) }
    }

    @Test
    fun `digits are kept and limited`() {
        assertEquals("123", OtpCode.digitsOnly("۱a٢3", 3))
        assertEquals("1234", OtpCode.digitsOnly("12-34"))
    }
}
