package ir.fishino.app.employer

/**
 * Employer codes are exactly six digits. Users may type them with a Persian
 * or Arabic keyboard, so those digits are converted to ASCII.
 */
object EmployerCode {
    const val LENGTH = 6

    /** Keeps digits only (converted to ASCII), at most [LENGTH] of them. */
    fun sanitize(input: String): String = buildString {
        for (char in input) {
            toAsciiDigit(char)?.let { append(it) }
            if (length == LENGTH) break
        }
    }

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }

    private fun toAsciiDigit(char: Char): Char? = when (char) {
        in '0'..'9' -> char
        in '۰'..'۹' -> '0' + (char - '۰')
        in '٠'..'٩' -> '0' + (char - '٠')
        else -> null
    }
}
