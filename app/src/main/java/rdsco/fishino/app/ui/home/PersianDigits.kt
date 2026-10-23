package rdsco.fishino.app.ui.home

/** Persian digits for display; the server sends Latin ones. */
internal fun String.persianDigits(): String = buildString(length) {
    for (char in this@persianDigits) append(if (char in '0'..'9') '۰' + (char - '0') else char)
}

/** Persian and Arabic-Indic digits typed on a Persian keyboard, as Latin ones. */
internal fun String.latinDigits(): String = buildString(length) {
    for (char in this@latinDigits) {
        append(
            when (char) {
                in '۰'..'۹' -> '0' + (char - '۰')
                in '٠'..'٩' -> '0' + (char - '٠')
                else -> char
            },
        )
    }
}
