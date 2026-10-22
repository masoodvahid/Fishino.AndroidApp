package rdsco.fishino.app.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Why the user is looking at the login screen again. [Timeout]: the app was
 * left for more than [SessionManager.BACKGROUND_GRACE_MS].
 */
enum class SessionEnd { SignedOut, Timeout, Expired, Revoked }

/**
 * The signed-in user, in memory only — the user signs in every time the app
 * is opened:
 *
 * - closing the app (process ends) forgets the session;
 * - leaving the app for more than [BACKGROUND_GRACE_MS] signs out, so a
 *   phone left on a desk does not stay signed in;
 * - while the app is open the token is refreshed before it expires.
 *
 * A token the server no longer accepts (the user signed in on another phone,
 * was deactivated, …) ends the session.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _lastEnd = MutableStateFlow<Pair<SessionEnd, String?>?>(null)

    /** How the previous session ended, with the server's message if any; for the login screen. */
    val lastEnd: StateFlow<Pair<SessionEnd, String?>?> = _lastEnd.asStateFlow()

    private var api: AuthApi? = null
    private var refreshJob: Job? = null
    private var backgroundedAt: Long? = null

    fun signedIn(session: Session, api: AuthApi) {
        this.api = api
        _lastEnd.value = null
        _session.value = session
        scheduleRefresh()
    }

    /** User's choice, or the employer was changed. Tells the server too (best effort). */
    fun signOut() = signOut(SessionEnd.SignedOut)

    /**
     * A call with this token was refused (401): the server no longer accepts
     * it. Ignored when the session has moved on to another token meanwhile.
     */
    fun onUnauthorized(token: String) {
        if (_session.value?.token == token) end(SessionEnd.Revoked, null)
    }

    private fun signOut(reason: SessionEnd) {
        val current = _session.value
        val currentApi = api
        end(reason, null)

        if (current != null && currentApi != null) {
            scope.launch { currentApi.logout(current.token) }
        }
    }

    fun onAppBackground() {
        backgroundedAt = clock()
    }

    fun onAppForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null

        if (_session.value != null && shouldSignOut(since, clock())) {
            signOut(SessionEnd.Timeout)
        }
    }

    fun consumeLastEnd() {
        _lastEnd.value = null
    }

    private fun end(reason: SessionEnd, message: String?) {
        refreshJob?.cancel()
        refreshJob = null
        val hadSession = _session.value != null
        _session.value = null
        if (hadSession && reason != SessionEnd.SignedOut) _lastEnd.value = reason to message
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                val current = _session.value ?: break
                delay(refreshDelay(current.expiresAtMillis, clock()))

                val now = _session.value ?: break
                if (now.token != current.token) continue
                val currentApi = api ?: break

                when (val result = currentApi.refresh(now.token)) {
                    is ApiResult.Ok -> _session.value = result.value
                    is ApiResult.Failed -> when (result.error.code) {
                        // Temporary: try again shortly, unless the token is already dead.
                        ApiError.NETWORK, ApiError.TOO_MANY_REQUESTS, ApiError.SERVER_ERROR ->
                            if (clock() >= now.expiresAtMillis) {
                                end(SessionEnd.Expired, null)
                            } else {
                                delay(RETRY_MS)
                            }
                        ApiError.UNAUTHORIZED -> end(SessionEnd.Revoked, null)
                        else -> end(SessionEnd.Revoked, result.error.message)
                    }
                }
            }
        }
    }

    companion object {
        const val BACKGROUND_GRACE_MS = 60_000L
        private const val REFRESH_BEFORE_EXPIRY_MS = 3 * 60_000L
        private const val RETRY_MS = 30_000L

        internal fun refreshDelay(expiresAtMillis: Long, now: Long): Long =
            (expiresAtMillis - now - REFRESH_BEFORE_EXPIRY_MS).coerceAtLeast(5_000L)

        internal fun shouldSignOut(backgroundedAt: Long, now: Long): Boolean = now - backgroundedAt > BACKGROUND_GRACE_MS
    }
}
