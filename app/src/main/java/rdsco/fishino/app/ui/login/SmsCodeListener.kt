package rdsco.fishino.app.ui.login

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * Reads the login code from the SMS with Google's SMS User Consent API:
 * Android shows the incoming message and asks the user to allow Fishino to
 * read that one message. No SMS permission and no change to the SMS text
 * are needed. Listens for 5 minutes after each new [request]; on phones
 * without Google Play services nothing happens and the user types the code.
 */
@Composable
fun SmsCodeListener(request: Int, onMessage: (String) -> Unit) {
    if (request <= 0) return

    val context = LocalContext.current
    val currentOnMessage by rememberUpdatedState(onMessage)

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)?.let(currentOnMessage)
        }
    }

    DisposableEffect(request) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                val status = extras.parcelable<Status>(SmsRetriever.EXTRA_STATUS) ?: return
                if (status.statusCode != CommonStatusCodes.SUCCESS) return

                extras.parcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)?.let { consentIntent ->
                    runCatching { consent.launch(consentIntent) }
                }
            }
        }

        val registered = runCatching {
            SmsRetriever.getClient(context).startSmsUserConsent(null)
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess

        onDispose {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelable(key, T::class.java) else getParcelable(key)
