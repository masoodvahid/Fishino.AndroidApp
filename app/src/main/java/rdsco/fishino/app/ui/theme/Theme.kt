package rdsco.fishino.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Dark: the brand's near-black becomes the background, controls turn light. */
private val FishinoDarkColors = darkColorScheme(
    primary = Color(0xFFE3E5EA),
    onPrimary = Color(0xFF1A1C20),
    primaryContainer = Color(0xFF2E3238),
    onPrimaryContainer = Color(0xFFE3E5EA),
    secondary = Color(0xFFC3C8D0),
    onSecondary = Color(0xFF1A1C20),
    secondaryContainer = Color(0xFF2E3238),
    onSecondaryContainer = Color(0xFFE3E5EA),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E6EA),
    surface = Color(0xFF1B1D21),
    onSurface = Color(0xFFE4E6EA),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFA9B0BB),
    surfaceContainerLowest = Color(0xFF0F1013),
    surfaceContainerLow = Color(0xFF17191C),
    surfaceContainer = Color(0xFF1B1D21),
    surfaceContainerHigh = Color(0xFF25282D),
    surfaceContainerHighest = Color(0xFF2F3238),
    outline = Color(0xFF4A505A),
    outlineVariant = Color(0xFF33373E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
)

/** The user's choice in the menu; [System] follows the phone. */
enum class ThemeMode { System, Light, Dark }

/** True while the dark colors are on. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The same flag for colors picked outside composition (status tones).
 * Read during composition, so a change redraws what uses it.
 */
object ThemeState {
    var dark by mutableStateOf(false)
        internal set
}

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
 * Light or dark, as the user chose (or as the phone is). The app is
 * Persian, so layout is right-to-left regardless of the phone's language.
 */
@Composable
fun FishinoTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Set before the content reads it, so the first frame has the right tones.
    ThemeState.dark = dark

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) FishinoDarkColors else FishinoColors,
            typography = FishinoTypography,
            content = content,
        )
    }
}
