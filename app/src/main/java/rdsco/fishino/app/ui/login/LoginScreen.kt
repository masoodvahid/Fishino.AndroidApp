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
import androidx.compose.material3.MaterialTheme
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

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    employerName: String,
    appVersion: String,
    onChangeEmployer: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmChangeEmployer by rememberSaveable { mutableStateOf(false) }
    val now by rememberTicker(active = state.lockedUntilMillis > 0 || state.resendAtMillis > 0)

    SmsCodeListener(request = state.smsListenRequest, onMessage = viewModel::onSmsReceived)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                employerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            state.notice?.let { notice ->
                Notice(text(notice), onDismiss = viewModel::dismissNotice)
                Spacer(Modifier.height(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        state.loadingConfig -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }

                        state.config == null -> ConfigError(state.configError?.let { text(it) }, onRetry = viewModel::loadConfig)

                        else -> LoginForm(state, now, viewModel)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmChangeEmployer = true }) { Text(stringResource(R.string.home_change_employer)) }
            Text(
                stringResource(R.string.home_version, appVersion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(LoginMethod.Password to R.string.login_method_password, LoginMethod.Otp to R.string.login_method_otp)
                .forEachIndexed { index, (method, label) ->
                    SegmentedButton(
                        selected = state.method == method,
                        onClick = { viewModel.selectMethod(method) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
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
        state.method == LoginMethod.Password -> R.string.login_submit to viewModel::loginWithPassword
        state.otpStep == OtpStep.EnterMobile -> R.string.login_send_code to viewModel::sendOtp
        else -> R.string.login_submit to viewModel::verifyOtp
    }

    Button(
        onClick = action,
        enabled = !state.busy && lockedSeconds == 0L,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(label))
        }
    }
}

@Composable
private fun PasswordForm(state: LoginUiState, viewModel: LoginViewModel) {
    val focus = LocalFocusManager.current
    var visible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        enabled = !state.busy,
        textStyle = ltr(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        enabled = !state.busy,
        textStyle = ltr(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.login_hide_password else R.string.login_show_password), style = MaterialTheme.typography.labelMedium)
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
    Button(onClick = onRetry, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_retry))
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
