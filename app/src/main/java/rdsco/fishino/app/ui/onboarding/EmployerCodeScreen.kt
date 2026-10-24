package rdsco.fishino.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.ui.common.EmployerLogoMark

@Composable
fun EmployerCodeScreen(viewModel: EmployerCodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 56.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The code step is Fishino's own; the confirm step shows the employer found.
            if (state.candidate == null) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(150.dp),
                )
            } else {
                EmployerLogoMark(state.candidate!!.name, state.candidateLogo, Modifier.padding(vertical = 16.dp))
            }
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val candidate = state.candidate

                if (candidate == null) {
                    CodeForm(
                        state = state,
                        onCodeChange = viewModel::onCodeChange,
                        onSubmit = viewModel::submit,
                    )
                } else {
                    ConfirmEmployer(
                        employer = candidate,
                        saving = state.saving,
                        onConfirm = viewModel::confirm,
                        onReject = viewModel::reject,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeForm(state: EmployerCodeUiState, onCodeChange: (String) -> Unit, onSubmit: () -> Unit) {
    val error = state.error
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.employer_code_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.employer_code_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.employer_code_label)) },
            singleLine = true,
            enabled = !state.checking,
            isError = error != null,
            supportingText = if (error != null) {
                { Text(errorMessage(error)) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )

        Button(
            onClick = onSubmit,
            enabled = state.code.length == 6 && !state.checking,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.employer_code_submit))
            }
        }
    }
}

@Composable
private fun ConfirmEmployer(employer: Employer, saving: Boolean, onConfirm: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.employer_confirm_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            employer.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.employer_confirm_question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onConfirm,
            enabled = !saving,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.employer_confirm_yes))
        }
        TextButton(onClick = onReject, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.employer_confirm_no))
        }
    }
}

@Composable
private fun errorMessage(error: CodeError): String = when (error) {
    CodeError.InvalidFormat -> stringResource(R.string.employer_code_invalid_format)
    CodeError.NotFound -> stringResource(R.string.error_code_not_found)
    CodeError.Inactive -> stringResource(R.string.error_employer_inactive)
    is CodeError.RateLimited -> stringResource(R.string.error_rate_limited, error.minutes)
    CodeError.Network -> stringResource(R.string.error_network)
    CodeError.Untrusted -> stringResource(R.string.error_untrusted)
    CodeError.Server -> stringResource(R.string.error_server)
}
