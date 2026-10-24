package rdsco.fishino.app.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.Captcha
import rdsco.fishino.app.auth.LoginMethod
import rdsco.fishino.app.ui.common.AuthPage
import rdsco.fishino.app.ui.common.GlassPanel
import rdsco.fishino.app.ui.common.FlatIcon
import rdsco.fishino.app.ui.common.EmployerLogoImage

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    employerName: String,
    appVersion: String,
    onChangeEmployer: () -> Unit,
    employerLogo: ImageBitmap? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmChangeEmployer by rememberSaveable { mutableStateOf(false) }
    val now by rememberTicker(active = state.lockedUntilMillis > 0 || state.resendAtMillis > 0)

    SmsCodeListener(request = state.smsListenRequest, onMessage = viewModel::onSmsReceived)
    BiometricPromptEffect(request = state.biometricRequest, username = state.savedUsername, viewModel = viewModel)

    AuthPage(step = 2, version = appVersion) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (employerLogo != null) {
                    EmployerLogoImage(employerLogo, employerName, height = 44.dp, maxWidth = 64.dp)
                } else {
                    FlatIcon(R.drawable.ic_auth_employer)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auth_selected_employer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(employerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { confirmChangeEmployer = true }, enabled = !state.busy) {
                    Text(stringResource(R.string.home_change_employer), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        state.notice?.let { notice ->
            Notice(text(notice), onDismiss = viewModel::dismissNotice)
            Spacer(Modifier.height(12.dp))
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.auth_login_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    state.loadingConfig -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                    state.config == null -> ConfigError(state.configError?.let { text(it) }, onRetry = viewModel::loadConfig)
                    else -> LoginForm(state, now, viewModel)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlatIcon(R.drawable.ic_auth_lock, size = 28.dp)
            Text(stringResource(R.string.auth_account_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }

    if (state.enrollmentOffer) {
        AlertDialog(
            // A choice is needed: the login finishes after the answer.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.biometric_offer_title)) },
            text = { Text(stringResource(R.string.biometric_offer_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::acceptEnrollment) { Text(stringResource(R.string.biometric_offer_accept)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineEnrollment) { Text(stringResource(R.string.biometric_offer_decline)) }
            },
        )
    }

    state.otpEnrollment?.let { offer ->
        AlertDialog(
            // A choice is needed: the login finishes after the answer.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.biometric_offer_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.biometric_offer_otp_text))
                    OutlinedTextField(
                        value = offer.password,
                        onValueChange = viewModel::onEnrollmentPasswordChange,
                        label = { Text(stringResource(R.string.login_password)) },
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        enabled = !offer.busy,
                        isError = offer.error != null,
                        supportingText = offer.error?.let { error -> { Text(text(error)) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.confirmOtpEnrollment() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOtpEnrollment, enabled = !offer.busy) {
                    if (offer.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.biometric_offer_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::skipOtpEnrollment, enabled = !offer.busy) { Text(stringResource(R.string.biometric_offer_decline)) }
            },
        )
    }

    if (confirmChangeEmployer) {
        AlertDialog(
            onDismissRequest = { confirmChangeEmployer = false },
            text = { Text(stringResource(R.string.home_change_employer_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmChangeEmployer = false
                    onChangeEmployer()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmChangeEmployer = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun LoginForm(state: LoginUiState, now: Long, viewModel: LoginViewModel) {
    val config = state.config ?: return
    val lockedSeconds = ((state.lockedUntilMillis - now + 999) / 1000).coerceAtLeast(0)

    if (config.otpEnabled && config.passwordEnabled) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
            listOf(LoginMethod.Password to R.string.login_method_password, LoginMethod.Otp to R.string.login_method_otp)
                .forEach { (method, label) ->
                    SegmentedButton(
                        selected = state.method == method,
                        onClick = { viewModel.selectMethod(method) },
                        shape = RoundedCornerShape(24.dp),
                        icon = {},
                        border = BorderStroke(0.dp, Color.Transparent),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.surface, inactiveContainerColor = Color.Transparent),
                        enabled = !state.busy,
                    ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
                }
        }
    }

    when (state.method) {
        LoginMethod.Password -> PasswordForm(state, viewModel)
        LoginMethod.Otp -> OtpForm(state, now, viewModel)
    }

    state.captcha?.let { captcha ->
        CaptchaField(captcha, state.captchaAnswer, enabled = !state.busy, viewModel = viewModel)
    }

    state.error?.let {
        Text(text(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (lockedSeconds > 0) {
        Text(
            stringResource(R.string.login_locked_countdown, formatSeconds(lockedSeconds)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    val (label, action) = when {
        state.method == LoginMethod.Password -> R.string.auth_login_button to viewModel::loginWithPassword
        state.otpStep == OtpStep.EnterMobile -> R.string.login_send_code to viewModel::sendOtp
        else -> R.string.auth_login_button to viewModel::verifyOtp
    }

    Button(
        onClick = action,
        enabled = !state.busy && lockedSeconds == 0L,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(label))
        }
    }
    if (state.canUseBiometric && config.passwordEnabled) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        OutlinedButton(onClick = viewModel::loginWithFingerprint, enabled = !state.busy && lockedSeconds == 0L,
            shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            FlatIcon(R.drawable.ic_auth_fingerprint, background = Color(0xFFE0F6EB), size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.biometric_login_button, state.savedUsername.orEmpty()), textAlign = TextAlign.Center)
        }
    }

}

@Composable
private fun PasswordForm(state: LoginUiState, viewModel: LoginViewModel) {
    val focus = LocalFocusManager.current
    var visible by rememberSaveable { mutableStateOf(false) }

    Text(stringResource(R.string.login_username), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        placeholder = { Text(stringResource(R.string.auth_username_placeholder), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { FlatIcon(R.drawable.ic_auth_person, size = 36.dp) },
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        enabled = !state.busy,
        textStyle = ltr(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )

    Text(stringResource(R.string.login_password), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        leadingIcon = { FlatIcon(R.drawable.ic_auth_lock, background = Color(0xFFEFE5FF), size = 36.dp) },
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        enabled = !state.busy,
        textStyle = ltr(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(painterResource(R.drawable.ic_auth_eye), contentDescription = stringResource(if (visible) R.string.login_hide_password else R.string.login_show_password), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.loginWithPassword() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OtpForm(state: LoginUiState, now: Long, viewModel: LoginViewModel) {
    if (state.otpStep == OtpStep.EnterMobile) {
        Text(
            stringResource(R.string.login_otp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.mobile,
            onValueChange = viewModel::onMobileChange,
            label = { Text(stringResource(R.string.login_mobile)) },
            placeholder = { Text("09xxxxxxxxx", style = ltr()) },
            shape = RoundedCornerShape(22.dp),
        singleLine = true,
            enabled = !state.busy,
            textStyle = ltr(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.sendOtp() }),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    state.otpMessage?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = { Text(stringResource(R.string.login_code)) },
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        enabled = !state.busy,
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp, textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.verifyOtp() }),
        modifier = Modifier.fillMaxWidth(),
    )

    val resendSeconds = ((state.resendAtMillis - now + 999) / 1000).coerceAtLeast(0)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = viewModel::editMobile, enabled = !state.busy) {
            Text(stringResource(R.string.login_edit_mobile, state.mobile), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = viewModel::sendOtp, enabled = !state.busy && resendSeconds == 0L) {
            Text(
                if (resendSeconds > 0) stringResource(R.string.login_resend_in, formatSeconds(resendSeconds)) else stringResource(R.string.login_resend),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CaptchaField(captcha: Captcha, answer: String, enabled: Boolean, viewModel: LoginViewModel) {
    val image = remember(captcha.imageDataUri) { decodeDataUri(captcha.imageDataUri) }

    Text(stringResource(R.string.login_captcha_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.login_captcha),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(140.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        TextButton(onClick = viewModel::refreshCaptcha, enabled = enabled) {
            Text(stringResource(R.string.login_captcha_refresh), style = MaterialTheme.typography.labelMedium)
        }
    }
    OutlinedTextField(
        value = answer,
        onValueChange = viewModel::onCaptchaChange,
        label = { Text(stringResource(R.string.login_captcha)) },
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        enabled = enabled,
        textStyle = ltr(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfigError(message: String?, onRetry: () -> Unit) {
    Text(message ?: stringResource(R.string.error_server), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Button(onClick = onRetry, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_retry))
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@Composable
private fun text(value: UiText): String = when (value) {
    is UiText.Server -> value.text
    is UiText.Res -> stringResource(value.id)
}

@Composable
private fun ltr(): TextStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)

/** Current time, updated every second while a countdown is shown. */
@Composable
private fun rememberTicker(active: Boolean): androidx.compose.runtime.State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active) {
        while (active) {
            now.longValue = System.currentTimeMillis()
            delay(1000)
        }
    }

    return now
}

private fun formatSeconds(seconds: Long): String = "%d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)

private fun decodeDataUri(dataUri: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(dataUri.substringAfter(','), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
