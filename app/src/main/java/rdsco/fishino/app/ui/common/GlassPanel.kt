package rdsco.fishino.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.ui.theme.LocalDarkTheme
import rdsco.fishino.app.ui.theme.ThemeState

private val LightPage = Brush.verticalGradient(listOf(Color(0xFFF5F6F7), Color(0xFFDDE0E3)))
private val DarkPage = Brush.verticalGradient(listOf(Color(0xFF17191C), Color(0xFF0E0F11)))

val PageGradient: Brush get() = if (ThemeState.dark) DarkPage else LightPage

/** Translucent layers over the page gradient (white, or faint in dark mode); text and icons stay sharp on every API level. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val dark = LocalDarkTheme.current
    val layers = if (dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = .08f), Color.White.copy(alpha = .04f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = .88f), Color.White.copy(alpha = .46f)))
    }
    val rim = if (dark) {
        BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .05f))))
    } else {
        BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .55f))))
    }
    val glass = modifier.clip(shape).background(layers)
    if (onClick == null) {
        Surface(modifier = glass, shape = shape, color = Color.Transparent, border = rim) {
            Column(content = content)
        }
    } else {
        Surface(onClick = onClick, modifier = glass, shape = shape, color = Color.Transparent, border = rim) {
            Column(content = content)
        }
    }
}
