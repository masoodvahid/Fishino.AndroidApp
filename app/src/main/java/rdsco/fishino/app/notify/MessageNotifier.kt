package rdsco.fishino.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import rdsco.fishino.app.MainActivity
import rdsco.fishino.app.R

/**
 * Phone notifications for new inbox messages. Only the title is shown;
 * the text needs signing in (tapping opens the app on the inbox).
 */
object MessageNotifier {

    const val CHANNEL_ID = "messages"
    const val EXTRA_OPEN = "rdsco.fishino.app.OPEN"
    const val OPEN_INBOX = "inbox"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notify_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** One notification per message; several are grouped by Android. */
    fun show(context: Context, messages: List<Pair<Long, String>>, unreadCount: Int) {
        if (messages.isEmpty() || !canNotify(context)) return

        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = NotificationManagerCompat.from(context)

        messages.forEach { (id, title) ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notify_text, unreadCount))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            try {
                manager.notify(id.toInt(), notification)
            } catch (e: SecurityException) {
                // Permission withdrawn meanwhile.
            }
        }
    }

    private const val GROUP = "rdsco.fishino.app.MESSAGES"
}
