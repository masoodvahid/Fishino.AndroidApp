package rdsco.fishino.app.ui.login

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import rdsco.fishino.app.R

/**
 * Shows the system biometric prompt for the view model's request, with the
 * request's cipher: the saved password can only be encrypted or decrypted
 * through a successful prompt.
 */
@Composable
fun BiometricPromptEffect(request: BiometricRequest?, username: String?, viewModel: LoginViewModel) {
    val activity = LocalContext.current.findFragmentActivity() ?: return
    val loginTitle = stringResource(R.string.biometric_prompt_login_title)
    val enrollTitle = stringResource(R.string.biometric_prompt_enroll_title)
    val usePassword = stringResource(R.string.biometric_prompt_use_password)
    val cancel = stringResource(R.string.action_cancel)

    LaunchedEffect(request?.id) {
        val current = request ?: return@LaunchedEffect
        val purpose = current.purpose
        viewModel.onBiometricShown(current.id)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) viewModel.onBiometricSucceeded(purpose, cipher) else viewModel.onBiometricFailed(purpose, null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                viewModel.onBiometricFailed(purpose, if (cancelled) null else errString.toString())
            }

            // onAuthenticationFailed (a finger not recognised): the prompt says so itself and lets the user try again.
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (purpose == BiometricPurpose.Login) loginTitle else enrollTitle)
            .apply { if (purpose == BiometricPurpose.Login && !username.isNullOrBlank()) setSubtitle(username) }
            .setNegativeButtonText(if (purpose == BiometricPurpose.Login) usePassword else cancel)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        runCatching {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(info, BiometricPrompt.CryptoObject(current.cipher))
        }.onFailure { viewModel.onBiometricFailed(purpose, null) }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
