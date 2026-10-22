package rdsco.fishino.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.AppUser
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.panel.DocumentItem
import rdsco.fishino.app.panel.HomeData
import rdsco.fishino.app.panel.Profile
import rdsco.fishino.app.panel.RequestItem
import rdsco.fishino.app.panel.RequestStatus
import rdsco.fishino.app.panel.Requirement
import rdsco.fishino.app.ui.login.UiText

private enum class HomeRoute { Home, Documents, Requests }

/**
 * After sign-in: the employee's profile and latest documents and requests,
 * with a full list of each; settings and sign-out at the bottom.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    employer: Employer,
    user: AppUser,
    appVersion: String,
    onCheckUpdates: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(HomeRoute.Home) }
    BackHandler(enabled = route != HomeRoute.Home) { route = HomeRoute.Home }

    when (route) {
        HomeRoute.Home -> HomeContent(viewModel, employer, user, appVersion, onCheckUpdates, onLogout, onOpen = { route = it })
        HomeRoute.Documents -> PagedListScreen(
            title = stringResource(R.string.list_documents_title),
            emptyText = stringResource(R.string.home_documents_empty),
            list = viewModel.documents,
            onBack = { route = HomeRoute.Home },
        ) { DocumentRow(it) }
        HomeRoute.Requests -> PagedListScreen(
            title = stringResource(R.string.list_requests_title),
            emptyText = stringResource(R.string.home_requests_empty),
            list = viewModel.requests,
            onBack = { route = HomeRoute.Home },
        ) { RequestRow(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    viewModel: HomeViewModel,
    employer: Employer,
    user: AppUser,
    appVersion: String,
    onCheckUpdates: (() -> Unit)?,
    onLogout: () -> Unit,
    onOpen: (HomeRoute) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Fresh data each time the home screen is shown (sign-in, back from a list).
    LaunchedEffect(Unit) { viewModel.refresh() }

    val data = state.data

    PullToRefreshBox(
        isRefreshing = state.loading && data != null,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Header(name = data?.profile?.fullName?.ifBlank { null } ?: user.fullName.ifBlank { user.username }, employer = employer.name)
            }

            when {
                data != null -> homeSections(data, onOpen)
                state.loading -> item { Loading() }
                else -> item { LoadError(state.error, onRetry = viewModel::refresh) }
            }

            // A refresh that failed while older data is on screen.
            if (data != null && state.error != null && !state.loading) {
                item { LoadError(state.error, onRetry = viewModel::refresh) }
            }

            item {
                Settings(
                    state = state,
                    appVersion = appVersion,
                    onBiometricChange = viewModel::setBiometric,
                    onCheckUpdates = onCheckUpdates,
                    onLogout = onLogout,
                )
            }
        }
    }
}

private fun LazyListScope.homeSections(data: HomeData, onOpen: (HomeRoute) -> Unit) {
    item { ProfileCard(data.profile) }

    data.requirement?.let { requirement -> item { RequirementBanner(requirement) } }

    if (data.pendingSignatures > 0) {
        item {
            Banner(
                text = stringResource(R.string.home_pending_signatures, data.pendingSignatures).persianDigits(),
                background = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.home_documents),
            onSeeAll = if (data.requirement == null && data.recentDocuments.isNotEmpty()) ({ onOpen(HomeRoute.Documents) }) else null,
        )
    }
    if (data.recentDocuments.isEmpty()) {
        item {
            EmptyText(stringResource(if (data.requirement != null) R.string.home_documents_hidden else R.string.home_documents_empty))
        }
    } else {
        items(data.recentDocuments, key = { "document-${it.id}" }) { DocumentRow(it) }
    }

    // While the profile is incomplete (no mobile number, first password) requests are hidden too.
    val requestsHidden = data.requirement != null && data.requirement.key != "signature"

    item {
        SectionHeader(
            title = stringResource(R.string.home_requests),
            onSeeAll = if (!requestsHidden && data.recentRequests.isNotEmpty()) ({ onOpen(HomeRoute.Requests) }) else null,
        )
    }
    if (data.recentRequests.isEmpty()) {
        item {
            EmptyText(stringResource(if (requestsHidden) R.string.home_documents_hidden else R.string.home_requests_empty))
        }
    } else {
        items(data.recentRequests, key = { "request-${it.id}" }) { RequestRow(it) }
    }
}

@Composable
private fun Header(name: String, employer: String) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(bottom = 4.dp)) {
        Text(stringResource(R.string.home_greeting, name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(employer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileCard(profile: Profile) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.fullName.trim().take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(profile.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOfNotNull(profile.jobTitle, profile.employer).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val fields = listOfNotNull(
            profile.employeeNumber?.let { R.string.home_profile_employee_number to it.persianDigits() },
            profile.nationalCode?.let { R.string.home_profile_national_code to it.persianDigits() },
            profile.mobile?.let { R.string.home_profile_mobile to it.persianDigits() },
            profile.workPlace?.let { R.string.home_profile_work_place to it },
        )

        if (fields.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
            fields.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RequirementBanner(requirement: Requirement) {
    Banner(
        title = requirement.title,
        text = requirement.message,
        background = Warning.background,
        content = Warning.content,
    )
}

@Composable
private fun Banner(text: String, background: Color, content: Color, title: String? = null) {
    Surface(shape = RoundedCornerShape(12.dp), color = background, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = content)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
    }
}

@Composable
private fun DocumentRow(document: DocumentItem) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    document.title.persianDigits(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (document.templateTitle.isNotBlank() && !document.title.startsWith(document.templateTitle)) {
                    Text(document.templateTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                document.needsSignature -> Chip(stringResource(R.string.home_document_needs_signature), Warning)
                document.signed -> Chip(stringResource(R.string.home_document_signed), Success)
            }
        }
    }
}

@Composable
private fun RequestRow(request: RequestItem) {
    Card {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                request.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Chip(request.statusLabel, request.status.tone())
        }
        Text(
            listOfNotNull(
                request.trackingCode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_request_tracking, it) },
                request.updatedLabel,
            ).joinToString(" · ").persianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        request.comment?.let {
            Text(
                stringResource(R.string.home_request_comment, it),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Settings(
    state: HomeUiState,
    appVersion: String,
    onBiometricChange: (Boolean) -> Unit,
    onCheckUpdates: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    Card {
        if (state.biometricEnabled || (state.biometricAvailable && state.loginName.isNotEmpty())) {
            val wanted = state.biometricEnabled || !state.biometricDeclined
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_biometric_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            when {
                                state.biometricEnabled -> R.string.home_biometric_on
                                wanted -> R.string.home_biometric_pending
                                else -> R.string.home_biometric_off
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = wanted, onCheckedChange = onBiometricChange)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
        }

        OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_logout))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.home_version, appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onCheckUpdates != null) {
                TextButton(onClick = onCheckUpdates) { Text(stringResource(R.string.home_check_updates)) }
            }
        }
    }
}

@Composable
private fun <T> PagedListScreen(
    title: String,
    emptyText: String,
    list: PagedList<T>,
    onBack: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    val state by list.state.collectAsStateWithLifecycle()
    LaunchedEffect(list) { list.reload() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.list_back)) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(state.items) { row(it) }

            item {
                when {
                    state.error != null -> LoadError(state.error, onRetry = list::loadMore)
                    state.loading -> Loading()
                    state.hasMore -> LaunchedEffect(state.items.size) { list.loadMore() }
                    state.items.isEmpty() -> EmptyText(emptyText)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

private class Tone(val background: Color, val content: Color)

private val Warning = Tone(Color(0xFFFFF4E5), Color(0xFF8A4B00))
private val Success = Tone(Color(0xFFE8F5EE), Color(0xFF1E7A46))
private val Danger = Tone(Color(0xFFFDECEA), Color(0xFFB42318))
private val Info = Tone(Color(0xFFE3F1F5), Color(0xFF06394A))
private val Neutral = Tone(Color(0xFFF0F2F5), Color(0xFF5B6470))

private fun RequestStatus.tone(): Tone = when (this) {
    RequestStatus.NeedsAction -> Warning
    RequestStatus.Rejected -> Danger
    RequestStatus.Approved -> Success
    RequestStatus.Reviewing -> Info
    RequestStatus.Pending -> Neutral
}

@Composable
private fun Chip(text: String, tone: Tone) {
    if (text.isBlank()) return
    Surface(shape = RoundedCornerShape(50), color = tone.background, modifier = Modifier.padding(start = 8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tone.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
private fun LoadError(error: UiText?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (error) {
                is UiText.Server -> error.text
                is UiText.Res -> stringResource(error.id)
                null -> stringResource(R.string.error_server)
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.home_load_error_retry)) }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp),
    )
}
