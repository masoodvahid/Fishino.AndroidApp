package rdsco.fishino.app.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import rdsco.fishino.app.auth.SyncGrant

/** Starts and stops the background message check. */
class MessageSync(context: Context, private val store: SyncTokenStore) {

    private val appContext = context.applicationContext

    /** At each sign-in: the new token replaces the old one and the check (re)starts. */
    suspend fun onSignedIn(apiBaseUrl: String, grant: SyncGrant) {
        runCatching {
            store.save(apiBaseUrl, grant.token, grant.checkIntervalSeconds)
            MessageSyncWorker.schedule(appContext, grant.checkIntervalSeconds, ExistingPeriodicWorkPolicy.UPDATE)
        }
    }

    /** Another employer: nothing of the old one keeps running. */
    suspend fun stop() {
        MessageSyncWorker.cancel(appContext)
        runCatching { store.clear() }
    }
}
