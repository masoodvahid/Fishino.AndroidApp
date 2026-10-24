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

val PageGradient = Brush.verticalGradient(listOf(Color(0xFFF5F6F7), Color(0xFFDDE0E3)))

/** Translucent white layers over the page gradient; text and icons stay sharp on every API level. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val layers = Brush.linearGradient(listOf(Color.White.copy(alpha = .88f), Color.White.copy(alpha = .46f)))
    val rim = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .55f))))
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
