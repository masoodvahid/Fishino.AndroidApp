package rdsco.fishino.app.ui.home

/** Persian digits for display; the server sends Latin ones. */
internal fun String.persianDigits(): String = buildString(length) {
    for (char in this@persianDigits) append(if (char in '0'..'9') '۰' + (char - '0') else char)
}
