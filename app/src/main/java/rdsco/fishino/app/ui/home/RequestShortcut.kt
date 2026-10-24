package rdsco.fishino.app.ui.home

/** Only the illustration is inferred from the label; navigation always uses the server's form ID. */
enum class RequestIllustration { Installments, Employment, Leave, Advance, General }

fun requestIllustration(title: String): RequestIllustration {
    val normalized = title.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
    return when {
        "مرخصی" in normalized -> RequestIllustration.Leave
        "اشتغال" in normalized || "گواهی" in normalized -> RequestIllustration.Employment
        "مساعده" in normalized || "وام" in normalized -> RequestIllustration.Advance
        "اقساط" in normalized || "قسط" in normalized || "کسر" in normalized -> RequestIllustration.Installments
        else -> RequestIllustration.General
    }
}
