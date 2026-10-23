package rdsco.fishino.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import rdsco.fishino.app.FishinoApp
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiError
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.auth.int
import rdsco.fishino.app.auth.long
import rdsco.fishino.app.auth.objects
import rdsco.fishino.app.auth.string
import java.util.concurrent.TimeUnit

/**
 * Every few hours (the server decides how often): asks the employer's
 * Fishino for new inbox messages with the background token and shows them
 * as phone notifications. Each message is shown once; the server marks it
 * delivered.
 */
class MessageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FishinoApp).container
        val store = container.syncTokenStore
        val config = store.load() ?: return Result.success().also { cancel(applicationContext) }

        val http = ApiClient(container.httpClient, config.apiBaseUrl, container.userAgent)
        val result = http.get("/notifications/sync", config.token) { data ->
            SyncAnswer(
                unread = data.int("unread_count") ?: 0,
                messages = data.objects("new").mapNotNull { item -> item.long("id")?.let { it to (item.string("title") ?: "") } },
                intervalSeconds = data.long("check_interval_seconds"),
            )
        }

        when (result) {
            is ApiResult.Ok -> {
                MessageNotifier.show(applicationContext, result.value.messages, result.value.unread)
                val interval = result.value.intervalSeconds
                if (interval != null && interval != config.intervalSeconds) {
                    store.setInterval(interval)
                    schedule(applicationContext, interval, ExistingPeriodicWorkPolicy.UPDATE)
                }
            }
            is ApiResult.Failed -> when {
                // The token expired or the account may no longer use the app: stop until the next sign-in.
                result.error.code in setOf(ApiError.UNAUTHORIZED, "unauthenticated", "no_access", "account_disabled") -> {
                    store.clear()
                    cancel(applicationContext)
                }
                // Offline, server busy, …: the next period tries again.
                else -> Unit
            }
        }

        return Result.success()
    }

    private class SyncAnswer(val unread: Int, val messages: List<Pair<Long, String>>, val intervalSeconds: Long?)

    companion object {
        private const val WORK_NAME = "fishino-message-sync"

        /** WorkManager does not run periodic work more often than every 15 minutes. */
        private const val MIN_INTERVAL_SECONDS = 15 * 60L

        fun schedule(context: Context, intervalSeconds: Long, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val request = PeriodicWorkRequestBuilder<MessageSyncWorker>(intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS), TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
