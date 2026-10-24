package rdsco.fishino.app.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R

/** RTL places the logo on the right; the actions are envelope, menu, then logout toward the left. */
@Composable
fun HomeHeader(name: String, unread: Int, onInbox: () -> Unit, onMenu: () -> Unit, onLogout: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Brand(name)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HeaderActions(unread, onInbox, onMenu, onLogout)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Brand(name) }
                HeaderActions(unread, onInbox, onMenu, onLogout)
            }
        }
    }
}

@Composable
private fun Brand(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Use the real raster wordmark, not an adaptive-icon mask or a replacement emblem.
            Image(painterResource(R.drawable.fishino_logo), contentDescription = null, modifier = Modifier.size(36.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(
            stringResource(R.string.home_welcome, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderActions(unread: Int, onInbox: () -> Unit, onMenu: () -> Unit, onLogout: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color(0xFFEAF4FF), border = BorderStroke(1.dp, Color.White.copy(alpha = .8f))) {
            IconButton(onClick = onInbox, modifier = Modifier.size(48.dp)) {
                Box {
                    Image(
                        painterResource(R.drawable.ic_flat_mail),
                        contentDescription = if (unread > 0) stringResource(R.string.inbox_unread, unread) else stringResource(R.string.inbox_title),
                        modifier = Modifier.size(24.dp),
                    )
                    if (unread > 0) {
                        Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
        }
        Surface(shape = CircleShape, color = Color(0xFFF2EDFC), border = BorderStroke(1.dp, Color.White.copy(alpha = .8f))) {
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open), tint = Color(0xFF283B60))
            }
        }
        Surface(shape = CircleShape, color = Color(0xFFFFF1F1), border = BorderStroke(1.dp, Color(0xFFF8DFDF))) {
            IconButton(onClick = onLogout, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_power), contentDescription = stringResource(R.string.home_logout), tint = SignOutRed)
            }
        }
    }
}
