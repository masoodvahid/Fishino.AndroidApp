package rdsco.fishino.app.auth

/**
 * Digits typed or received in Persian/Arabic become ASCII. Mobile numbers
 * are accepted as 09…, 9…, +989… or 00989… and returned as 09xxxxxxxxx.
 */
object OtpCode {

    fun digitsOnly(value: String, maxLength: Int = Int.MAX_VALUE): String = buildString {
        for (char in value) {
            asciiDigit(char)?.let { append(it) }
            if (length == maxLength) break
        }
    }

    fun normalizeMobile(value: String): String? {
        val digits = digitsOnly(value)
        val national = when {
            digits.startsWith("0098") -> digits.removePrefix("0098")
            digits.startsWith("98") && digits.length == 12 -> digits.removePrefix("98")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }

        return if (national.length == 10 && national.startsWith("9")) "0$national" else null
    }

    /**
     * The login code in an SMS. The message may hold other numbers (the
     * opt-out line of the SMS panel, a date), so a group of exactly
     * [expectedLength] digits wins over other 4–6 digit groups.
     */
    fun extract(message: String, expectedLength: Int): String? {
        val normalized = buildString { for (char in message) append(asciiDigit(char) ?: char) }
        val groups = Regex("(?<!\\d)\\d{4,6}(?!\\d)").findAll(normalized).map { it.value }.toList()

        return groups.firstOrNull { it.length == expectedLength } ?: groups.firstOrNull()
    }

    private fun asciiDigit(char: Char): Char? = when (char) {
        in '0'..'9' -> char
        in '۰'..'۹' -> '0' + (char - '۰')
        in '٠'..'٩' -> '0' + (char - '٠')
        else -> null
    }
}
