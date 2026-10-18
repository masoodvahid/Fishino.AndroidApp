package ir.fishino.app.employer

/**
 * The employer this installation is bound to.
 *
 * @property code the 6-digit employer code the user typed (kept so the app can
 *   ask the directory again later, e.g. if the employer's server moves).
 * @property apiBaseUrl base of the employer's own Fishino API, without a
 *   trailing slash, e.g. `https://hrm.payamnasr.com/api/v1`.
 */
data class Employer(
    val code: String,
    val name: String,
    val apiBaseUrl: String,
)
