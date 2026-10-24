package rdsco.fishino.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.BuildConfig
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.home.persianDigits

/** Shared, scrollable and keyboard-aware shell for both authentication steps. */
@Composable
fun AuthPage(step: Int, version: String = BuildConfig.VERSION_NAME, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize().background(PageGradient)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.fishino_logo), null, Modifier.size(52.dp))
                    Text(stringResource(R.string.auth_brand_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.auth_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    AuthStep(1, step, R.string.auth_step_employer, Modifier.weight(1f))
                    HorizontalDivider(Modifier.weight(.45f), color = MaterialTheme.colorScheme.outlineVariant)
                    AuthStep(2, step, R.string.auth_step_login, Modifier.weight(1f))
                }
                content()
                Text(
                    stringResource(R.string.home_footer_version, version.persianDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun AuthStep(number: Int, current: Int, label: Int, modifier: Modifier) {
    val active = current >= number
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Text(if (current > number) "✓" else number.toString().persianDigits(), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Six visual slots backed by ONE editable field: paste, deletion and IME work normally. */
@Composable
fun EmployerCodeInput(value: String, onValueChange: (String) -> Unit, enabled: Boolean, errorMessage: String?, onSubmit: () -> Unit) {
    val label = stringResource(R.string.employer_code_label)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent, textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                if (errorMessage != null) error(errorMessage)
            },
            decorationBox = { editor ->
                Box {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(6) { index ->
                            val selected = focused && index == value.length.coerceAtMost(5)
                            val border = when {
                                errorMessage != null -> MaterialTheme.colorScheme.error
                                selected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).clearAndSetSemantics {},
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .7f),
                                border = BorderStroke(if (selected) 2.dp else 1.dp, border),
                            ) {
                                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(value.getOrNull(index)?.toString()?.persianDigits() ?: "–", style = MaterialTheme.typography.titleLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { editor() }
                }
            },
        )
    }
}
