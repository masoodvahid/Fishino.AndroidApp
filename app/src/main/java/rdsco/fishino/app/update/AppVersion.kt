package rdsco.fishino.app.update

/**
 * major.minor.patch, parsed from a release tag ("v1.2.3") or versionName.
 * Missing parts count as zero ("1.2" == "1.2.0").
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d{1,4})(?:\.(\d{1,4}))?(?:\.(\d{1,4}))?$""")

        fun parse(value: String): AppVersion? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            val (major, minor, patch) = match.destructured

            return AppVersion(major.toInt(), minor.ifEmpty { "0" }.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}
