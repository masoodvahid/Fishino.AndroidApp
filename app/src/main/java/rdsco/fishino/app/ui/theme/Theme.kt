package rdsco.fishino.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import rdsco.fishino.app.R

private val Brand = Color(0xFF292B30)

private val FishinoColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBEDF0),
    onPrimaryContainer = Color(0xFF292B30),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF202228),
    surface = Color.White,
    onSurface = Color(0xFF202228),
    surfaceVariant = Color(0xFFEBEDF0),
    onSurfaceVariant = Color(0xFF626975),
    outline = Color(0xFFC9CED6),
    outlineVariant = Color(0xFFE4E7EC),
    error = Color(0xFFB42318),
    onError = Color.White,
)

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.vazir() = copy(fontFamily = Vazirmatn)

private val FishinoTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.vazir(),
        displayMedium = displayMedium.vazir(),
        displaySmall = displaySmall.vazir(),
        headlineLarge = headlineLarge.vazir(),
        headlineMedium = headlineMedium.vazir(),
        headlineSmall = headlineSmall.vazir(),
        titleLarge = titleLarge.vazir(),
        titleMedium = titleMedium.vazir(),
        titleSmall = titleSmall.vazir(),
        bodyLarge = bodyLarge.vazir(),
        bodyMedium = bodyMedium.vazir(),
        bodySmall = bodySmall.vazir(),
        labelLarge = labelLarge.vazir(),
        labelMedium = labelMedium.vazir(),
        labelSmall = labelSmall.vazir(),
    )
}

/**
 * Light theme only for now. The app is Persian, so layout is right-to-left
 * regardless of the phone's language.
 */
@Composable
fun FishinoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = FishinoColors,
            typography = FishinoTypography,
            content = content,
        )
    }
}
