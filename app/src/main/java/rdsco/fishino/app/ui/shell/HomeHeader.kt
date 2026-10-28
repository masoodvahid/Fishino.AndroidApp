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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.common.EmployerLogoImage
import rdsco.fishino.app.ui.theme.LocalDarkTheme

/**
 * RTL places the employer's logo and name on the right; on the left, from
 * the edge inward: the menu and the messages. Sign-out is in the menu.
 */
@Composable
fun HomeHeader(
    name: String,
    employerName: String,
    employerLogo: ImageBitmap?,
    unread: Int,
    onInbox: () -> Unit,
    onMenu: () -> Unit,
) {
    // Little below: the news slideshow follows closely.
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Brand(name, employerName, employerLogo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HeaderActions(unread, onInbox, onMenu)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Brand(name, employerName, employerLogo) }
                HeaderActions(unread, onInbox, onMenu)
            }
        }
    }
}

@Composable
private fun Brand(name: String, employerName: String, employerLogo: ImageBitmap?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmployerLogoImage(employerLogo, contentDescription = null, height = 40.dp, maxWidth = 110.dp)
            Text(
                employerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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

/** Messages first in code: in right-to-left the menu ends up at the edge. */
@Composable
fun HeaderActions(unread: Int, onInbox: () -> Unit, onMenu: () -> Unit) {
    val dark = LocalDarkTheme.current
    val rim = BorderStroke(1.dp, if (dark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .8f))

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = if (dark) Color(0xFF1C2A3A) else Color(0xFFEAF4FF), border = rim) {
            IconButton(onClick = onInbox, modifier = Modifier.size(48.dp)) {
                Box {
                    Image(
                        painterResource(R.drawable.ic_flat_mail),
                        contentDescription = if (unread > 0) stringResource(R.string.inbox_unread, unread) else stringResource(R.string.inbox_title),
                        modifier = Modifier.size(24.dp),
                    )
                    if (unread > 0) {
                        Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    }
                }
            }
        }
        Surface(shape = CircleShape, color = if (dark) Color(0xFF2A2638) else Color(0xFFF2EDFC), border = rim) {
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open), tint = if (dark) Color(0xFFD9DEEA) else Color(0xFF283B60))
            }
        }
    }
}
