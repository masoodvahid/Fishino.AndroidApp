package rdsco.fishino.app.auth

data class AppUser(
    val id: Long,
    val username: String,
    val fullName: String,
    /** 09xxxxxxxxx, or null when the account has no number. */
    val mobile: String?,
)

/**
 * A signed-in user. Kept in memory only: the user signs in again whenever
 * the app is opened (see [SessionManager]).
 */
data class Session(
    val token: String,
    val expiresAtMillis: Long,
    val user: AppUser,
)
