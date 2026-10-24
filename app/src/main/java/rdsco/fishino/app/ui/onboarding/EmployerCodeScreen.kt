package rdsco.fishino.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.ui.common.AuthPage
import rdsco.fishino.app.ui.common.EmployerCodeInput
import rdsco.fishino.app.ui.common.EmployerLogoMark
import rdsco.fishino.app.ui.common.FlatIcon
import rdsco.fishino.app.ui.common.GlassPanel

@Composable
fun EmployerCodeScreen(viewModel: EmployerCodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthPage(step = 1) {
        GlassPanel(Modifier.fillMaxWidth()) {
            val candidate = state.candidate
            if (candidate == null) {
                CodeForm(state, viewModel::onCodeChange, viewModel::submit)
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmployerLogoMark(candidate.name, state.candidateLogo)
                }
                ConfirmEmployer(candidate, state.saving, viewModel::confirm, viewModel::reject)
            }
        }
        if (state.candidate == null) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FlatIcon(R.drawable.ic_auth_info)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auth_code_help_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.auth_code_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CodeForm(state: EmployerCodeUiState, onCodeChange: (String) -> Unit, onSubmit: () -> Unit) {
    val message = state.error?.let { errorMessage(it) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FlatIcon(R.drawable.ic_auth_employer, size = 64.dp)
        Text(stringResource(R.string.auth_code_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.auth_code_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(stringResource(R.string.employer_code_title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        EmployerCodeInput(state.code, onCodeChange, !state.checking, message, onSubmit)
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.auth_code_once), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onSubmit, enabled = state.code.length == 6 && !state.checking, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (state.checking) {
                CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.auth_code_continue))
            }
        }
        Text(stringResource(R.string.auth_code_confirm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConfirmEmployer(employer: Employer, saving: Boolean, onConfirm: () -> Unit, onReject: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.employer_confirm_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(employer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.employer_confirm_question), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onConfirm, enabled = !saving, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(stringResource(R.string.employer_confirm_yes))
        }
        TextButton(onClick = onReject, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.employer_confirm_no)) }
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
