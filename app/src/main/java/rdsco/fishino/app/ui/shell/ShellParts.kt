package rdsco.fishino.app.ui.shell

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.Channel
import rdsco.fishino.app.ui.home.persianDigits
import rdsco.fishino.app.ui.theme.ThemeMode

/** The menu's envelope by the user's name, with the number of unread messages (also when none). */
@Composable
fun InboxButton(unread: Int, onClick: () -> Unit) {
    val label = if (unread > 0) stringResource(R.string.inbox_unread, unread).persianDigits() else stringResource(R.string.inbox_all_read)
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp).semantics { contentDescription = label }) {
        BadgedBox(badge = {
            Badge(
                containerColor = if (unread > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                contentColor = if (unread > 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.surface,
            ) {
                Text(if (unread > 99) "۹۹+" else unread.toString().persianDigits())
            }
        }) {
            Image(painterResource(R.drawable.ic_flat_mail), contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * The company's communication channels (as the web's side toolbar): a
 * floating button that unfolds into one button per channel.
 */
@Composable
fun ChannelsButton(channels: List<Channel>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (open) {
            channels.forEach { channel ->
                ExtendedFloatingActionButton(
                    onClick = {
                        open = false
                        try {
                            context.startActivity(Intent(if (channel.url.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(channel.url)))
                        } catch (e: ActivityNotFoundException) {
                            // No app for this link.
                        }
                    },
                    icon = { Icon(if (channel.key == "phone") Icons.Filled.Call else Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    text = { Text(channel.label) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FloatingActionButton(onClick = { open = !open }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.Share, contentDescription = stringResource(R.string.channels_title))
        }
    }
}

/** Android 13+: asks once per sign-in for permission to show message notifications. */
@Composable
fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}

/** Light, dark or as the phone: three buttons in the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeChoice(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.System to R.string.theme_system,
        ThemeMode.Light to R.string.theme_light,
        ThemeMode.Dark to R.string.theme_dark,
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
