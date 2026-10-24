package rdsco.fishino.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.theme.ThemeState
import rdsco.fishino.app.panel.RequestStatus
import rdsco.fishino.app.ui.login.UiText

/** Content is never wider than this on tablets. */
val ContentMaxWidth = 560.dp

@Composable
fun text(value: UiText): String = when (value) {
    is UiText.Server -> value.text
    is UiText.Res -> stringResource(value.id)
}

@Composable
fun PanelCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(
        modifier = modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

class Tone(val background: Color, val content: Color)

/** Status colors, light or dark with the app's theme. */
object Tones {
    private val dark get() = ThemeState.dark

    val Warning get() = if (dark) Tone(Color(0xFF3A2A12), Color(0xFFFFC77A)) else Tone(Color(0xFFFFF4E5), Color(0xFF8A4B00))
    val Success get() = if (dark) Tone(Color(0xFF14301F), Color(0xFF7FD6A2)) else Tone(Color(0xFFE8F5EE), Color(0xFF1E7A46))
    val Danger get() = if (dark) Tone(Color(0xFF3A1714), Color(0xFFFF9C92)) else Tone(Color(0xFFFDECEA), Color(0xFFB42318))
    val Info get() = if (dark) Tone(Color(0xFF102C35), Color(0xFF8FD3E8)) else Tone(Color(0xFFE3F1F5), Color(0xFF06394A))
    val Neutral get() = if (dark) Tone(Color(0xFF262A30), Color(0xFFB7BEC8)) else Tone(Color(0xFFF0F2F5), Color(0xFF5B6470))
}

fun RequestStatus.tone(): Tone = when (this) {
    RequestStatus.NeedsAction -> Tones.Warning
    RequestStatus.Rejected -> Tones.Danger
    RequestStatus.Approved -> Tones.Success
    RequestStatus.Reviewing -> Tones.Info
    RequestStatus.Pending -> Tones.Neutral
}

@Composable
fun Chip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Surface(shape = RoundedCornerShape(50), color = tone.background, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tone.content, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun Banner(
    text: String,
    tone: Tone,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = tone.background, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tone.content)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = tone.content)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(actionLabel, color = tone.content, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
fun LoadError(error: UiText?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(error?.let { text(it) } ?: stringResource(R.string.error_server), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.home_load_error_retry)) }
    }
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
fun ErrorText(error: UiText?) {
    error ?: return
    Text(text(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun SuccessText(message: String?) {
    message?.takeIf { it.isNotBlank() } ?: return
    Text(message, color = Tones.Success.content, style = MaterialTheme.typography.bodySmall)
}
