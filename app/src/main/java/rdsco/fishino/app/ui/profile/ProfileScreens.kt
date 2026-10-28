package rdsco.fishino.app.ui.profile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.OtpCode
import rdsco.fishino.app.panel.InfoField
import rdsco.fishino.app.panel.ProfileApi
import rdsco.fishino.app.panel.ProfileData
import rdsco.fishino.app.panel.UploadField
import rdsco.fishino.app.panel.UploadedDocument
import rdsco.fishino.app.ui.common.ActionState
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.Banner
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.EmptyText
import rdsco.fishino.app.ui.common.ErrorText
import rdsco.fishino.app.ui.common.FlatIcon
import rdsco.fishino.app.ui.common.HtmlText
import rdsco.fishino.app.ui.common.GlassPanel
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.SuccessText
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.home.Avatar
import rdsco.fishino.app.ui.home.persianDigits
import rdsco.fishino.app.ui.login.SmsCodeListener
import rdsco.fishino.app.ui.login.UiText

/** The profile's sections, in the web's order, with their titles and icons. */
enum class ProfileSection(val tab: String, val title: Int, val description: Int, @DrawableRes val icon: Int, val tile: Color) {
    Password("password", R.string.profile_password, R.string.profile_password_desc, R.drawable.ic_auth_lock, Color(0xFFEFE5FF)),
    Mobile("mobile", R.string.profile_mobile, R.string.profile_mobile_desc, R.drawable.ic_flat_phone, Color(0xFFE0F5FA)),
    Signature("signature", R.string.profile_signature, R.string.profile_signature_desc, R.drawable.ic_flat_signature, Color(0xFFFFF0E0)),
    Avatar("avatar", R.string.profile_avatar, R.string.profile_avatar_desc, R.drawable.ic_auth_person, Color(0xFFE5F1FF)),
    Documents("documents", R.string.profile_documents, R.string.profile_documents_desc, R.drawable.ic_flat_document, Color(0xFFE5F1FF)),
    Information("profile", R.string.profile_information, R.string.profile_information_desc, R.drawable.ic_flat_idcard, Color(0xFFE9EAFE)),
    Bale("bale", R.string.profile_bale, R.string.profile_bale_desc, R.drawable.ic_flat_chat, Color(0xFFE0F6EB)),
    Agreement("agreement", R.string.profile_agreement, R.string.profile_agreement_desc, R.drawable.ic_flat_certificate, Color(0xFFFFF5D9)),
    Logs("logs", R.string.profile_logs, R.string.profile_logs_desc, R.drawable.ic_flat_history, Color(0xFFFDE7EB)),
    ;

    companion object {
        fun of(tab: String): ProfileSection = entries.firstOrNull { it.tab == tab } ?: Password
    }
}

/** The profile overview: who, what is still to do, and the sections. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onOpenSection: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.profile.state.collectAsStateWithLifecycle()
    val avatarVersion by viewModel.avatarVersion.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val data = state.data
        when {
            data != null -> {
                item {
                    PanelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(data.user.fullName, avatarVersion, Modifier.size(64.dp))
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(data.user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                listOfNotNull(data.user.jobTitle, data.user.employer).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(data.user.username, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                items(data.pending) { requirement ->
                    Banner(
                        title = requirement.title,
                        text = requirement.message,
                        tone = Tones.Warning,
                        actionLabel = requirement.tab?.let { stringResource(R.string.home_requirement_action) },
                        onAction = requirement.tab?.let { tab -> { onOpenSection(tab) } },
                    )
                }

                // Bale only once the employer has set up its bot (or the account is still connected).
                val sections = ProfileSection.entries.filter { it != ProfileSection.Bale || data.bale.available || data.bale.connected }
                items(sections.chunked(3)) { row ->
                    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { section ->
                            SectionTile(section, needsAction = data.pending.any { it.tab == section.tab }, Modifier.weight(1f)) { onOpenSection(section.tab) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            state.loading -> item { Loading() }
            else -> item { LoadError(state.error, onRetry = viewModel::refresh) }
        }
    }
}

/** A profile section in the overview's grid: its icon, its title below. */
@Composable
private fun SectionTile(section: ProfileSection, needsAction: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pendingLabel = stringResource(R.string.profile_needs_action)
    GlassPanel(modifier, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Box {
                FlatIcon(section.icon, section.tile, size = 48.dp)
                if (needsAction) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(12.dp)
                            .background(Tones.Warning.content, CircleShape)
                            .semantics { contentDescription = pendingLabel },
                    )
                }
            }
            Text(
                stringResource(section.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One profile section, as its own screen. */
@Composable
fun ProfileSectionScreen(viewModel: ProfileViewModel, tab: String, onOpenSection: (String) -> Unit) {
    val section = ProfileSection.of(tab)
    LaunchedEffect(section) {
        viewModel.openSection()
        viewModel.refresh()
    }
    val state by viewModel.profile.state.collectAsStateWithLifecycle()
    val action by viewModel.action.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                stringResource(section.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            )
        }

        val data = state.data
        when {
            data == null && state.loading -> item { Loading() }
            data == null -> item { LoadError(state.error, onRetry = viewModel::refresh) }
            else -> {
                // The web holds the user on a pending onboarding step.
                val requirement = data.requirement
                if (requirement != null && requirement.tab != section.tab && section != ProfileSection.Logs) {
                    item {
                        Banner(
                            title = requirement.title,
                            text = requirement.message,
                            tone = Tones.Warning,
                            actionLabel = stringResource(R.string.home_requirement_action),
                            onAction = { requirement.tab?.let(onOpenSection) },
                        )
                    }
                }

                when (section) {
                    ProfileSection.Password -> item { PasswordSection(viewModel, action) }
                    ProfileSection.Mobile -> item { MobileSection(viewModel, data, action) }
                    ProfileSection.Signature -> item { SignatureSection(viewModel, data, action) }
                    ProfileSection.Avatar -> item { AvatarSection(viewModel, data, action) }
                    ProfileSection.Documents -> documentsSection(viewModel, data, action)
                    ProfileSection.Information -> informationSection(viewModel, data, action)
                    ProfileSection.Bale -> item { BaleSection(viewModel, data, action) }
                    ProfileSection.Agreement -> item { AgreementSection(viewModel, action, onOpenSection) }
                    ProfileSection.Logs -> item { LogsSection(viewModel) }
                }
            }
        }
    }
}

@Composable
private fun ActionFeedback(action: ActionState) {
    ErrorText(action.error)
    SuccessText(action.message)
}

@Composable
private fun PrimaryButton(label: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(label)
    }
}

@Composable
private fun Guidance(items: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun ltr(style: TextStyle) = style.copy(textDirection = TextDirection.Ltr)

// ---------------------------------------------------------------- password

@Composable
private fun PasswordSection(viewModel: ProfileViewModel, action: ActionState) {
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val biometric by viewModel.biometric.collectAsStateWithLifecycle()

    LaunchedEffect(action.message) {
        if (action.message != null) {
            password = ""
            repeat = ""
        }
    }

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(255) },
                    label = { Text(stringResource(R.string.profile_new_password)) },
                    placeholder = { Text(stringResource(R.string.profile_password_min)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = ltr(MaterialTheme.typography.bodyLarge),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it.take(255) },
                    label = { Text(stringResource(R.string.profile_repeat_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = ltr(MaterialTheme.typography.bodyLarge),
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionFeedback(action)
                PrimaryButton(stringResource(R.string.profile_save_password), action.busy) { viewModel.changePassword(password, repeat) }
            }
        }
        Guidance(listOf(R.string.profile_password_tip_1, R.string.profile_password_tip_2, R.string.profile_password_tip_3))

        if (biometric.enabled || (biometric.available && biometric.loginName.isNotEmpty())) {
            val wanted = biometric.enabled || !biometric.declined
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_biometric_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                when {
                                    biometric.enabled -> R.string.home_biometric_on
                                    wanted -> R.string.home_biometric_pending
                                    else -> R.string.home_biometric_off
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = wanted, onCheckedChange = viewModel::setBiometric)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ mobile

@Composable
private fun MobileSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    val pending by viewModel.mobileCode.collectAsStateWithLifecycle()
    val listen by viewModel.smsListenRequest.collectAsStateWithLifecycle()
    var mobile by rememberSaveable { mutableStateOf(data.mobile.number.orEmpty()) }
    var code by rememberSaveable(pending?.smsToken) { mutableStateOf("") }

    val currentPending = pending
    SmsCodeListener(request = if (currentPending != null) listen else 0) { message ->
        val length = currentPending?.length ?: return@SmsCodeListener
        OtpCode.extract(message, length)?.let {
            code = it
            viewModel.verifyMobileCode(it)
        }
    }

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    currentPending != null -> {
                        Text(currentPending.message.ifBlank { stringResource(R.string.profile_mobile_code_sent) }, style = MaterialTheme.typography.bodyMedium)
                        CodeField(code, currentPending.length, enabled = !action.busy) {
                            code = it
                            if (it.length == currentPending.length) viewModel.verifyMobileCode(it)
                        }
                        ActionFeedback(action)
                        PrimaryButton(stringResource(R.string.profile_verify_code), action.busy, enabled = code.length >= 4) { viewModel.verifyMobileCode(code) }
                        TextButton(onClick = viewModel::editMobile, enabled = !action.busy) {
                            Text(stringResource(R.string.login_edit_mobile, currentPending.target.orEmpty()))
                        }
                    }
                    data.mobile.step == "mobile" -> {
                        Text(stringResource(R.string.profile_mobile_enter), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = OtpCode.digitsOnly(it, 14) },
                            label = { Text(stringResource(R.string.login_mobile)) },
                            placeholder = { Text("09xxxxxxxxx", style = ltr(MaterialTheme.typography.bodyLarge)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = ltr(MaterialTheme.typography.bodyLarge),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionFeedback(action)
                        PrimaryButton(stringResource(R.string.profile_mobile_send), action.busy) { viewModel.sendMobileCode(mobile) }
                    }
                    data.mobile.step == "shahkar" -> {
                        Text(
                            stringResource(R.string.profile_shahkar_text, data.mobile.number.orEmpty(), data.user.nationalCode.orEmpty()).persianDigits(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ActionFeedback(action)
                        PrimaryButton(stringResource(R.string.profile_shahkar_button), action.busy) { viewModel.verifyShahkar() }
                    }
                    else -> {
                        Text(
                            data.mobile.number.orEmpty().persianDigits(),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Chip(stringResource(R.string.profile_mobile_verified), Tones.Success)
                        ActionFeedback(action)
                    }
                }
            }
        }

        data.mobile.items.forEach { item ->
            Banner(
                title = item.label,
                text = listOfNotNull(item.message, item.remaining?.let { stringResource(R.string.profile_remaining, it) }).joinToString("\n"),
                tone = if (item.complete) Tones.Success else Tones.Danger,
            )
        }
        Text(
            stringResource(
                if (data.mobile.shahkarEnabled) R.string.profile_mobile_validity_shahkar else R.string.profile_mobile_validity,
                data.mobile.validityDays,
            ).persianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeField(value: String, length: Int, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(OtpCode.digitsOnly(it, length)) },
        label = { Text(stringResource(R.string.profile_code)) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp, textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

// --------------------------------------------------------------- signature

@Composable
private fun SignatureSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    val strokes = rememberSignatureStrokes()

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            if (data.hasSignature) {
                AuthedImage(
                    path = ProfileApi.SIGNATURE_PATH,
                    contentDescription = stringResource(R.string.profile_signature),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                )
                Banner(text = stringResource(R.string.profile_signature_saved), tone = Tones.Success)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignaturePad(strokes, enabled = !action.busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = strokes::undo, enabled = !action.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.profile_signature_undo)) }
                        OutlinedButton(onClick = strokes::clear, enabled = !action.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.profile_signature_clear)) }
                    }
                    ActionFeedback(action)
                    PrimaryButton(stringResource(R.string.profile_signature_save), action.busy) {
                        strokes.toPng()?.let(viewModel::uploadSignature) ?: viewModel.action.fail(UiText.Res(R.string.profile_signature_empty))
                    }
                }
            }
        }
        if (!data.hasSignature) {
            Guidance(listOf(R.string.profile_signature_tip_1, R.string.profile_signature_tip_2))
        }
    }
}

// ------------------------------------------------------------------ avatar

@Composable
private fun AvatarSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by viewModel.avatarVersion.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = PickedFiles.avatarJpeg(context, uri)) {
                is PickedFiles.Result.Ok -> viewModel.uploadAvatar(result.value)
                is PickedFiles.Result.TooLarge -> viewModel.action.fail(UiText.Res(R.string.profile_avatar_too_large))
                else -> viewModel.action.fail(UiText.Res(R.string.profile_file_unreadable))
            }
        }
    }

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Avatar(data.user.fullName, version, Modifier.size(140.dp))
                ActionFeedback(action)
                PrimaryButton(stringResource(R.string.profile_avatar_pick), action.busy) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (data.user.hasAvatar) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !action.busy) {
                        Text(stringResource(R.string.profile_avatar_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Guidance(listOf(R.string.profile_avatar_tip_1, R.string.profile_avatar_tip_2, R.string.profile_avatar_tip_3))
    }

    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.profile_avatar_delete_confirm), onConfirm = {
            confirmDelete = false
            viewModel.deleteAvatar()
        }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// --------------------------------------------------------------- documents

private fun LazyListScope.documentsSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    val documents = data.documents

    if (!documents.enabled) {
        item { EmptyText(stringResource(R.string.profile_documents_disabled)) }
        return
    }

    documents.description?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) } }
    item { Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) { ActionFeedback(action) } }

    items(documents.fields, key = { "field-${it.id}" }) { field -> UploadFieldCard(viewModel, field, action.busy) }

    if (documents.files.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.profile_documents_uploaded)) }
        items(documents.files, key = { "file-${it.id}" }) { file -> UploadedDocumentCard(viewModel, file, action.busy) }
    }
}

@Composable
private fun UploadFieldCard(viewModel: ProfileViewModel, field: UploadField, busy: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = PickedFiles.document(context, uri, field.formats, field.maxKb)) {
                is PickedFiles.Result.Ok -> viewModel.uploadDocument(field.id, result.value)
                is PickedFiles.Result.TooLarge -> viewModel.action.fail(UiText.Res(R.string.profile_file_too_large))
                is PickedFiles.Result.WrongFormat -> viewModel.action.fail(UiText.Res(R.string.profile_file_wrong_format))
                is PickedFiles.Result.Unreadable -> viewModel.action.fail(UiText.Res(R.string.profile_file_unreadable))
            }
        }
    }

    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (field.required) Chip(stringResource(R.string.profile_required), Tones.Danger, Modifier.padding(start = 8.dp))
        }
        Text(
            stringResource(R.string.profile_documents_limits, field.formats.joinToString("، "), field.maxKb).persianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        field.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        OutlinedButton(onClick = { picker.launch(mimeFilter(field.formats)) }, enabled = !busy, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
            Text(stringResource(R.string.profile_documents_pick))
        }
    }
}

/** The picker's filter: images only, PDFs only, or anything. */
private fun mimeFilter(formats: List<String>): String {
    val images = setOf("jpg", "jpeg", "png", "gif", "webp")
    return when {
        formats.isNotEmpty() && formats.all { it in images } -> "image/*"
        formats == listOf("pdf") -> "application/pdf"
        else -> "*/*"
    }
}

@Composable
private fun UploadedDocumentCard(viewModel: ProfileViewModel, file: UploadedDocument, busy: Boolean) {
    var confirmDelete by remember { mutableStateOf(false) }

    PanelCard {
        Text(file.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        file.uploadedLabel?.let {
            Text(stringResource(R.string.profile_documents_uploaded_at, it).persianDigits(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (file.isImage) {
            AuthedImage(
                path = ProfileApi.documentPath(file.id),
                contentDescription = file.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(10.dp)),
            )
        }
        Text(
            stringResource(if (file.canDelete) R.string.profile_documents_can_delete else R.string.profile_documents_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (file.canDelete) {
            TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Text(stringResource(R.string.profile_documents_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.profile_documents_delete_confirm), onConfirm = {
            confirmDelete = false
            viewModel.deleteDocument(file.id)
        }, onDismiss = { confirmDelete = false })
    }
}

// ------------------------------------------------------------- information

private fun LazyListScope.informationSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    item {
        Banner(
            text = stringResource(if (data.infoEditable) R.string.profile_information_note else R.string.profile_information_locked),
            tone = if (data.infoEditable) Tones.Info else Tones.Danger,
        )
    }
    items(data.info, key = { it.key }) { field -> InfoRow(viewModel, field, data.infoEditable, action) }
}

@Composable
private fun InfoRow(viewModel: ProfileViewModel, field: InfoField, editable: Boolean, action: ActionState) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val display: (String?) -> String? = { value -> field.options?.firstOrNull { it.value == value }?.label ?: value }

    PanelCard(onClick = if (editable) ({ viewModel.action.clear(); editing = true }) else null) {
        Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(display(field.value)?.persianDigits() ?: "—", style = MaterialTheme.typography.bodyLarge)
        if (field.hasPending) {
            Text(
                stringResource(R.string.profile_information_pending, display(field.pending)?.persianDigits() ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = Tones.Warning.content,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (editing) {
        InfoEditDialog(field, action, onDismiss = { editing = false }) { value ->
            viewModel.requestChange(field.key, value) { editing = false }
        }
    }
}

@Composable
private fun InfoEditDialog(field: InfoField, action: ActionState, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(field.pending ?: field.value.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!action.busy) onDismiss() },
        title = { Text(field.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = field.options
                if (options != null) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().selectable(selected = value == option.value, onClick = { value = option.value }),
                        ) {
                            RadioButton(selected = value == option.value, onClick = { value = option.value })
                            Text(option.label)
                        }
                    }
                } else {
                    OutlinedTextField(value = value, onValueChange = { value = it.take(500) }, singleLine = field.key != "address", modifier = Modifier.fillMaxWidth())
                }
                ErrorText(action.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = !action.busy) {
                if (action.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.profile_information_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// -------------------------------------------------------------------- bale

@Composable
private fun BaleSection(viewModel: ProfileViewModel, data: ProfileData, action: ActionState) {
    val code by viewModel.baleCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    val bale = data.bale

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_bale_status), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Chip(stringResource(if (bale.connected) R.string.profile_bale_connected else R.string.profile_bale_disconnected), if (bale.connected) Tones.Success else Tones.Neutral)
                }
                val currentCode = code
                when {
                    bale.connected -> {
                        Text(bale.username?.let { "@$it" } ?: stringResource(R.string.profile_bale_connected_text), style = ltr(MaterialTheme.typography.bodyMedium))
                        ActionFeedback(action)
                        OutlinedButton(onClick = viewModel::disconnectBale, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.profile_bale_disconnect), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    currentCode != null -> {
                        Text(stringResource(R.string.profile_bale_code_ready), style = MaterialTheme.typography.bodyMedium)
                        Text(currentCode.command, style = ltr(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { copy(context, currentCode.command); copied = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (copied) R.string.profile_bale_copied else R.string.profile_bale_copy))
                        }
                        (currentCode.botUrl ?: bale.botUrl)?.let { url ->
                            PrimaryButton(stringResource(R.string.profile_bale_open_bot), busy = false) { openUrl(context, url) }
                        }
                        Text(stringResource(R.string.profile_bale_lifetime, currentCode.lifetimeMinutes).persianDigits(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    bale.available -> {
                        Text(stringResource(R.string.profile_bale_intro), style = MaterialTheme.typography.bodyMedium)
                        ActionFeedback(action)
                        PrimaryButton(stringResource(R.string.profile_bale_create), action.busy) { viewModel.createBaleCode() }
                    }
                    else -> Text(stringResource(R.string.profile_bale_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!bale.connected) {
            Guidance(listOf(R.string.profile_bale_step_1, R.string.profile_bale_step_2, R.string.profile_bale_step_3, R.string.profile_bale_warning))
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("bale", text))
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser or Bale app.
    }
}

// --------------------------------------------------------------- agreement

@Composable
private fun AgreementSection(viewModel: ProfileViewModel, action: ActionState, onOpenSection: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.agreement.load(Unit, force = true) }
    val state by viewModel.agreement.state.collectAsStateWithLifecycle()
    val pending by viewModel.agreementCode.collectAsStateWithLifecycle()
    val listen by viewModel.smsListenRequest.collectAsStateWithLifecycle()
    val fileBusy by viewModel.fileBusy.collectAsStateWithLifecycle()
    var accepted by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable(pending?.smsToken) { mutableStateOf("") }

    val currentPending = pending
    SmsCodeListener(request = if (currentPending != null) listen else 0) { message ->
        val length = currentPending?.length ?: return@SmsCodeListener
        OtpCode.extract(message, length)?.let {
            code = it
            viewModel.verifyAgreement(it)
        }
    }

    val agreement = state.data
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            agreement != null -> {
                PanelCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val signature = agreement.signature
                        val prerequisite = agreement.prerequisite
                        when {
                            agreement.signed && signature != null -> {
                                Chip(stringResource(R.string.profile_agreement_signed), Tones.Success)
                                Text(
                                    stringResource(R.string.profile_agreement_signed_text, signature.signerName, signature.dateLabel, signature.timeLabel, signature.signerMobile).persianDigits(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                ActionFeedback(action)
                                PrimaryButton(stringResource(R.string.profile_agreement_pdf), fileBusy) { viewModel.openAgreementPdf() }
                            }
                            !agreement.requiresSignature -> {
                                Chip(stringResource(R.string.profile_agreement_not_required), Tones.Neutral)
                                Text(stringResource(R.string.profile_agreement_not_required_text), style = MaterialTheme.typography.bodyMedium)
                            }
                            prerequisite != null -> Banner(
                                title = prerequisite.title,
                                text = prerequisite.message,
                                tone = Tones.Warning,
                                actionLabel = stringResource(R.string.home_requirement_action),
                                onAction = { prerequisite.tab?.let(onOpenSection) },
                            )
                            currentPending != null -> {
                                Text(currentPending.message, style = MaterialTheme.typography.bodyMedium)
                                CodeField(code, currentPending.length, enabled = !action.busy) {
                                    code = it
                                    if (it.length == currentPending.length) viewModel.verifyAgreement(it)
                                }
                                ActionFeedback(action)
                                PrimaryButton(stringResource(R.string.profile_agreement_sign), action.busy, enabled = code.length == currentPending.length) { viewModel.verifyAgreement(code) }
                                Row {
                                    TextButton(onClick = viewModel::sendAgreementCode, enabled = !action.busy) { Text(stringResource(R.string.login_resend)) }
                                    TextButton(onClick = { accepted = false; viewModel.cancelAgreementCode() }, enabled = !action.busy) { Text(stringResource(R.string.action_cancel)) }
                                }
                            }
                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.selectable(selected = accepted, onClick = { accepted = !accepted }),
                                ) {
                                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                                    Text(stringResource(R.string.profile_agreement_accept), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                ActionFeedback(action)
                                PrimaryButton(stringResource(R.string.profile_agreement_send_code), action.busy, enabled = accepted) { viewModel.sendAgreementCode() }
                            }
                        }
                    }
                }
                PanelCard { HtmlText(agreement.contentHtml, Modifier.fillMaxWidth()) }
            }
            state.loading -> Loading()
            else -> LoadError(state.error, onRetry = viewModel.agreement::reload)
        }
    }
}

// -------------------------------------------------------------------- logs

@Composable
private fun LogsSection(viewModel: ProfileViewModel) {
    LaunchedEffect(Unit) { viewModel.logs.load(Unit, force = true) }
    val state by viewModel.logs.state.collectAsStateWithLifecycle()
    val logs = state.data

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            logs != null && logs.isEmpty() -> EmptyText(stringResource(R.string.profile_logs_empty))
            logs != null -> logs.forEach { log ->
                PanelCard {
                    Text(log.description.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(log.dateLabel, log.timeLabel).joinToString(" · ").persianDigits(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    log.ip?.let { Text(it, style = ltr(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            state.loading -> Loading()
            else -> LoadError(state.error, onRetry = viewModel.logs::reload)
        }
    }
}
