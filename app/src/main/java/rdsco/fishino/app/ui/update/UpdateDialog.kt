package rdsco.fishino.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.update.Release
import java.util.Locale

@Composable
fun UpdateDialog(viewModel: UpdateViewModel, currentVersion: String) {
    // Store builds do not install APKs and must never query the install permission.
    if (!viewModel.enabled) return

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read the install permission whenever the user comes back from settings.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val canInstall = remember(lifecycleState, state) {
        state is UpdateState.ReadyToInstall && viewModel.canInstall()
    }

    val install: () -> Unit = { viewModel.installIntent()?.let { context.startSafely(it) } }

    // Open the installer once as soon as a verified APK is ready (and allowed);
    // after that the dialog's button does it, also across screen rotation.
    var autoOpenedFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        val ready = state as? UpdateState.ReadyToInstall ?: return@LaunchedEffect
        if (autoOpenedFor != ready.file.path && viewModel.canInstall()) {
            autoOpenedFor = ready.file.path
            install()
        }
    }

    when (val current = state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> SimpleDialog(
            onDismiss = viewModel::dismiss,
            text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.update_checking))
            } },
        )

        UpdateState.UpToDate -> SimpleDialog(
            onDismiss = viewModel::dismiss,
            text = { Text(stringResource(R.string.update_up_to_date, currentVersion)) },
            confirm = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_ok)) } },
        )

        is UpdateState.Available -> SimpleDialog(
            onDismiss = viewModel::dismiss,
            title = stringResource(R.string.update_available_title),
            text = { ReleaseDetails(current.release, currentVersion) },
            confirm = { Button(onClick = viewModel::download) { Text(stringResource(R.string.update_download)) } },
            dismiss = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) } },
        )

        is UpdateState.Downloading -> SimpleDialog(
            onDismiss = {},
            dismissible = false,
            title = stringResource(R.string.update_downloading_title),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.update_progress, (current.progress * 100).toInt(), megabytes(current.release.apkSize)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            dismiss = { TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.action_cancel)) } },
        )

        is UpdateState.ReadyToInstall -> SimpleDialog(
            onDismiss = viewModel::dismiss,
            title = stringResource(R.string.update_ready_title, current.release.version.toString()),
            text = {
                Text(stringResource(if (canInstall) R.string.update_ready_text else R.string.update_permission_text))
            },
            confirm = {
                Button(onClick = install) {
                    Text(stringResource(if (canInstall) R.string.update_install else R.string.update_allow_install))
                }
            },
            dismiss = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) } },
        )

        is UpdateState.Failed -> SimpleDialog(
            onDismiss = viewModel::dismiss,
            title = stringResource(R.string.update_failed_title),
            text = { Text(stringResource(failureMessage(current.reason))) },
            confirm = {
                if (current.release != null) {
                    Button(onClick = viewModel::download) { Text(stringResource(R.string.update_retry)) }
                } else {
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismiss = if (current.release != null) {
                { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_cancel)) } }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ReleaseDetails(release: Release, currentVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_available_text, release.version.toString(), currentVersion))
        if (release.apkSize > 0) {
            Text(stringResource(R.string.update_size, megabytes(release.apkSize)), style = MaterialTheme.typography.bodySmall)
        }
        if (release.notes.isNotBlank()) {
            Text(
                release.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun SimpleDialog(
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
    title: String? = null,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    dismissible: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (title != null) {
            { Text(title, style = MaterialTheme.typography.titleMedium) }
        } else {
            null
        },
        text = text,
        confirmButton = { confirm?.invoke() },
        dismissButton = dismiss,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    )
}

private fun failureMessage(reason: UpdateFailure): Int = when (reason) {
    UpdateFailure.Network -> R.string.update_error_network
    UpdateFailure.NoApk -> R.string.update_error_no_apk
    UpdateFailure.Corrupt -> R.string.update_error_corrupt
    UpdateFailure.WrongSignature -> R.string.update_error_signature
    UpdateFailure.NotNewer -> R.string.update_error_not_newer
}

private fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1_048_576.0)

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No installer or settings screen (very unusual ROMs): nothing more to do here.
    }
}
