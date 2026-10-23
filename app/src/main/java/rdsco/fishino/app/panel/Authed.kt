package rdsco.fishino.app.panel

import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.SessionManager

/**
 * Runs an API call with the current session's token. A token the server no
 * longer accepts ends the session, so the app returns to the login screen.
 */
class Authed(private val sessions: SessionManager) {

    suspend fun <T> call(block: suspend (token: String) -> ApiResult<T>): ApiResult<T> {
        val token = sessions.session.value?.token ?: return ApiResult.Failed(ApiError(ApiError.UNAUTHORIZED))
        val result = block(token)

        if (result is ApiResult.Failed) {
            when (result.error.code) {
                ApiError.UNAUTHORIZED -> sessions.onUnauthorized(token)
                "no_access", "account_disabled" -> sessions.onRevoked(token, result.error.message)
            }
        }

        return result
    }
}
