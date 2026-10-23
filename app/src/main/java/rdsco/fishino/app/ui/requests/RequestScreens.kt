package rdsco.fishino.app.ui.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.FieldType
import rdsco.fishino.app.panel.FormField
import rdsco.fishino.app.panel.RequestDetail
import rdsco.fishino.app.ui.common.Banner
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.ErrorText
import rdsco.fishino.app.ui.common.HtmlText
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loadable
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.common.text
import rdsco.fishino.app.ui.common.tone
import rdsco.fishino.app.ui.home.persianDigits

/** The forms the user may fill for a new request. */
@Composable
fun FormsScreen(viewModel: RequestsViewModel, onOpen: (Long) -> Unit, onOpenProfileSection: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.forms.load(Unit, force = true) }
    val state by viewModel.forms.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val forms = state.data
        when {
            forms != null && forms.isEmpty() -> item { EmptyText(stringResource(R.string.requests_no_forms)) }
            forms != null -> {
                item { Text(stringResource(R.string.requests_choose_form), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(forms, key = { it.id }) { form ->
                    PanelCard(onClick = { onOpen(form.id) }) {
                        Text(form.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        listOfNotNull(form.subtitle, stringResource(if (form.external) R.string.requests_form_online else R.string.requests_form_internal))
                            .joinToString(" · ")
                            .let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            state.loading -> item { Loading() }
            else -> item { RequirementOrError(state, onRetry = viewModel.forms::reload, onOpenProfileSection = onOpenProfileSection) }
        }
    }
}

/** A failed load: a profile step to do first, or a plain error with retry. */
@Composable
fun <T> RequirementOrError(state: Loadable<T>, onRetry: () -> Unit, onOpenProfileSection: (String) -> Unit) {
    val tab = state.requirementTab
    if (tab != null) {
        Banner(
            text = state.error?.let { text(it) }.orEmpty(),
            tone = Tones.Warning,
            actionLabel = stringResource(R.string.home_requirement_action),
            onAction = { onOpenProfileSection(tab) },
        )
    } else {
        LoadError(state.error, onRetry = onRetry)
    }
}

/** Filling a form: help, fields, the request text preview, submit. */
@Composable
fun FormFillScreen(viewModel: RequestsViewModel, id: Long, onSubmitted: (Long) -> Unit, onOpenProfileSection: (String) -> Unit) {
    LaunchedEffect(id) { viewModel.openForm(id) }
    val state by viewModel.form.state.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val submit by viewModel.submit.state.collectAsStateWithLifecycle()
    val submitted by viewModel.submitted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(submitted) {
        submitted?.let {
            viewModel.consumeSubmitted()
            onSubmitted(it.id)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val form = state.data
        when {
            form != null -> {
                item {
                    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                        Text(form.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        form.summary.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }

                form.helpHtml?.let { help -> item { PanelCard { HtmlText(help, Modifier.fillMaxWidth(), sizeSp = 14f) } } }

                form.externalUrl?.let { url ->
                    item {
                        Banner(
                            text = stringResource(R.string.requests_online_form_hint),
                            tone = Tones.Info,
                            actionLabel = stringResource(R.string.requests_open_online_form),
                            onAction = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: ActivityNotFoundException) {
                                    // No browser: nothing to do.
                                }
                            },
                        )
                    }
                }

                if (form.fields.isNotEmpty()) {
                    item {
                        PanelCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                form.fields.forEach { field ->
                                    FieldInput(
                                        field = field,
                                        value = values[field.key].orEmpty(),
                                        error = submit.fieldErrors[field.key],
                                        enabled = !submit.busy,
                                        onChange = { viewModel.setValue(field.key, field.type, it) },
                                    )
                                }
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.requests_preview)) }
                item { PanelCard { HtmlText(form.previewHtml, Modifier.fillMaxWidth(), sizeSp = 14f) } }

                item {
                    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErrorText(submit.error)
                        Button(
                            onClick = viewModel::submitForm,
                            enabled = !submit.busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            if (submit.busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.requests_submit))
                            }
                        }
                    }
                }
            }
            state.loading -> item { Loading() }
            else -> item { RequirementOrError(state, onRetry = viewModel.form::reload, onOpenProfileSection = onOpenProfileSection) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(field: FormField, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    val label: @Composable () -> Unit = { Text(if (field.required) "${field.label} *" else field.label) }
    val supporting: (@Composable () -> Unit)? = error?.let { { Text(it) } }

    when (field.type) {
        FieldType.Dropdown -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = label,
                    isError = error != null,
                    supportingText = supporting,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            onChange(option)
                            expanded = false
                        })
                    }
                }
            }
        }
        FieldType.Number, FieldType.Currency -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            label = label,
            isError = error != null,
            supportingText = supporting,
            singleLine = true,
            suffix = if (field.type == FieldType.Currency) ({ Text(stringResource(R.string.requests_rial)) }) else null,
            visualTransformation = if (field.type == FieldType.Currency) ThousandsSeparator else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.Text -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            label = label,
            isError = error != null,
            supportingText = supporting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 2500000 shown as 2,500,000 while typing; the value stays digits only. */
private object ThousandsSeparator : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = digits.reversed().chunked(3).joinToString(",").reversed()

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var seen = 0
                formatted.forEachIndexed { index, char ->
                    if (char != ',' && ++seen == offset) return index + 1
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count { it != ',' }.coerceIn(0, digits.length)
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/** A request: status, text, the reviewers' answers and the PDF letter. */
@Composable
fun RequestDetailScreen(viewModel: RequestsViewModel, id: Long) {
    LaunchedEffect(id) { viewModel.detail.load(id, force = true) }
    val state by viewModel.detail.state.collectAsStateWithLifecycle()
    val pdfBusy by viewModel.pdfBusy.collectAsStateWithLifecycle()
    val pdfError by viewModel.pdfError.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val detail = state.data
        when {
            detail != null -> requestDetail(detail, pdfBusy, pdfError) { viewModel.openPdf(id) }
            state.loading -> item { Loading() }
            else -> item { LoadError(state.error, onRetry = viewModel.detail::reload) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.requestDetail(
    detail: RequestDetail,
    pdfBusy: Boolean,
    pdfError: rdsco.fishino.app.ui.login.UiText?,
    onPdf: () -> Unit,
) {
    item {
        PanelCard {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    detail.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Chip(detail.statusLabel, detail.status.tone(), Modifier.padding(start = 8.dp))
            }
            Text(
                listOfNotNull(stringResource(R.string.home_request_tracking, detail.trackingCode), detail.dateLabel).joinToString(" · ").persianDigits(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
            HtmlText(detail.contentHtml, Modifier.fillMaxWidth())
        }
    }

    item { SectionHeader(stringResource(R.string.requests_reviews)) }
    if (detail.reviews.isEmpty()) {
        item { EmptyText(stringResource(R.string.requests_no_reviews)) }
    } else {
        items(detail.reviews) { review ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(review.reviewer ?: stringResource(R.string.requests_reviewer), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        review.reviewerTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Chip(review.statusLabel, review.status.tone(), Modifier.padding(start = 8.dp))
                }
                review.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                review.dateLabel?.let {
                    Text(it.persianDigits(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    item {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPdf, enabled = !pdfBusy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (pdfBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.requests_pdf))
            }
            ErrorText(pdfError)
        }
    }
}
