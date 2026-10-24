package rdsco.fishino.app.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.OtpCode
import rdsco.fishino.app.panel.DocumentDetail
import rdsco.fishino.app.ui.common.ActionState
import rdsco.fishino.app.ui.common.Banner
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.ErrorText
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.SuccessText
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.home.persianDigits
import rdsco.fishino.app.ui.login.SmsCodeListener
import rdsco.fishino.app.ui.requests.RequirementOrError

/** A document: view it in the app or as PDF and, when asked, sign it. */
@Composable
fun DocumentScreen(viewModel: DocumentViewModel, id: Long, onOpenView: (Long) -> Unit, onOpenProfileSection: (String) -> Unit) {
    LaunchedEffect(id) { viewModel.open(id) }
    val state by viewModel.detail.state.collectAsStateWithLifecycle()
    val action by viewModel.action.state.collectAsStateWithLifecycle()
    val pdfBusy by viewModel.pdfBusy.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val document = state.data
        when {
            document != null -> {
                item {
                    PanelCard {
                        Text(document.title.persianDigits(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (document.templateTitle.isNotBlank() && !document.title.startsWith(document.templateTitle)) {
                            Text(document.templateTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                document.signed -> Chip(stringResource(R.string.home_document_signed), Tones.Success)
                                document.canSign -> Chip(stringResource(R.string.home_document_needs_signature), Tones.Warning)
                            }
                            if (document.signatureRequired && !document.signed) Chip(stringResource(R.string.document_signature_mandatory), Tones.Danger)
                        }
                        document.signedLabel?.let {
                            Text(stringResource(R.string.document_signed_at, it).persianDigits(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                        // Viewing in the app needs no PDF; the PDF stays for keeping or sharing.
                        if (document.viewAvailable) {
                            Button(
                                onClick = { onOpenView(document.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp),
                            ) {
                                Text(stringResource(R.string.document_view_live))
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.openPdf(document.id, document.title) },
                            enabled = !pdfBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(top = if (document.viewAvailable) 8.dp else 12.dp).fillMaxWidth().height(48.dp),
                        ) {
                            if (pdfBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(if (document.viewAvailable) R.string.document_download_pdf else R.string.document_view_pdf))
                            }
                        }
                    }
                }

                if (document.canSign && !document.signed) {
                    item { SectionHeader(stringResource(R.string.document_sign_title)) }
                    items(document.requirements, key = { it.key }) { requirement ->
                        Banner(
                            title = requirement.label,
                            text = requirement.message,
                            tone = if (requirement.complete) Tones.Success else Tones.Danger,
                            actionLabel = if (!requirement.complete && requirement.tab != null) stringResource(R.string.home_requirement_action) else null,
                            onAction = requirement.tab?.takeIf { !requirement.complete }?.let { tab -> { onOpenProfileSection(tab) } },
                        )
                    }
                    item { SigningCard(viewModel, document, action) }
                } else {
                    item { Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) { ErrorText(action.error); SuccessText(action.message) } }
                }
            }
            state.loading -> item { Loading() }
            else -> item { RequirementOrError(state, onRetry = viewModel.detail::reload, onOpenProfileSection = onOpenProfileSection) }
        }
    }
}

@Composable
private fun SigningCard(viewModel: DocumentViewModel, document: DocumentDetail, action: ActionState) {
    val pending by viewModel.code.collectAsStateWithLifecycle()
    val listen by viewModel.smsListenRequest.collectAsStateWithLifecycle()
    var code by rememberSaveable(pending?.smsToken) { mutableStateOf("") }

    val current = pending
    SmsCodeListener(request = if (current != null) listen else 0) { message ->
        val length = current?.length ?: return@SmsCodeListener
        OtpCode.extract(message, length)?.let {
            code = it
            viewModel.sign(document.id, it)
        }
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current == null) {
                Text(
                    stringResource(if (document.readyToSign) R.string.document_sign_ready else R.string.document_sign_not_ready, document.maskedMobile).persianDigits(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ErrorText(action.error)
                SuccessText(action.message)
                OutlinedButton(
                    onClick = { viewModel.sendCode(document.id) },
                    enabled = document.readyToSign && !action.busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (action.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.document_send_code))
                }
            } else {
                Text(current.message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = OtpCode.digitsOnly(value, current.length)
                        if (code.length == current.length) viewModel.sign(document.id, code)
                    },
                    label = { Text(stringResource(R.string.profile_code)) },
                    singleLine = true,
                    enabled = !action.busy,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp, textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(action.error)
                Button(
                    onClick = { viewModel.sign(document.id, code) },
                    enabled = !action.busy && code.length == current.length,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (action.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(stringResource(R.string.document_sign))
                }
                Row {
                    TextButton(onClick = { viewModel.sendCode(document.id) }, enabled = !action.busy) { Text(stringResource(R.string.login_resend)) }
                    TextButton(onClick = viewModel::cancelCode, enabled = !action.busy) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}
