package rdsco.fishino.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.theme.LocalDarkTheme
import rdsco.fishino.app.ui.home.RequestIllustration

@Composable
fun FlatIcon(
    @DrawableRes drawable: Int,
    background: Color = Color(0xFFE5F1FF),
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    // Pastel tiles would glare in dark mode: the same hue, faint.
    val tile = if (LocalDarkTheme.current) background.copy(alpha = .16f) else background
    Box(modifier.size(size).background(tile, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(drawable), contentDescription = null, modifier = Modifier.size(size * .68f))
    }
}

@Composable
fun RequestIcon(kind: RequestIllustration, modifier: Modifier = Modifier) {
    val (drawable, background) = when (kind) {
        RequestIllustration.Installments -> R.drawable.ic_flat_installments to Color(0xFFEFE5FF)
        RequestIllustration.Employment -> R.drawable.ic_flat_certificate to Color(0xFFE5F1FF)
        RequestIllustration.Leave -> R.drawable.ic_flat_calendar to Color(0xFFE0F6EB)
        RequestIllustration.Advance -> R.drawable.ic_flat_coins to Color(0xFFFFF0D5)
        RequestIllustration.General -> R.drawable.ic_flat_clipboard to Color(0xFFE0F6EB)
    }
    FlatIcon(drawable, background, modifier)
}
