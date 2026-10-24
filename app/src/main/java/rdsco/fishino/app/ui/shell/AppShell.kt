package rdsco.fishino.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import rdsco.fishino.app.ui.common.EmployerLogoImage
import rdsco.fishino.app.ui.home.persianDigits
import rdsco.fishino.app.ui.theme.ThemeMode
import rdsco.fishino.app.ui.privacy.PrivacyScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FabPosition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.AppUser
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.ui.common.ImageCache
import rdsco.fishino.app.ui.common.PageGradient
import rdsco.fishino.app.ui.common.LocalPanel
import rdsco.fishino.app.notify.MessageNotifier
import rdsco.fishino.app.ui.documents.DocumentScreen
import rdsco.fishino.app.ui.documents.DocumentViewerScreen
import rdsco.fishino.app.ui.documents.DocumentViewModel
import rdsco.fishino.app.ui.home.Avatar
import rdsco.fishino.app.ui.inbox.InboxScreen
import rdsco.fishino.app.ui.inbox.InboxViewModel
import rdsco.fishino.app.ui.home.DocumentsScreen
import rdsco.fishino.app.ui.home.HomeActions
import rdsco.fishino.app.ui.home.HomeScreen
import rdsco.fishino.app.ui.home.HomeViewModel
import rdsco.fishino.app.ui.home.RequestsScreen
import rdsco.fishino.app.ui.news.NewsDetailScreen
import rdsco.fishino.app.ui.news.NewsListScreen
import rdsco.fishino.app.ui.news.NewsViewModel
import rdsco.fishino.app.ui.profile.ProfileScreen
import rdsco.fishino.app.ui.profile.ProfileSectionScreen
import rdsco.fishino.app.ui.profile.ProfileViewModel
import rdsco.fishino.app.ui.requests.FormFillScreen
import rdsco.fishino.app.ui.requests.FormsScreen
import rdsco.fishino.app.ui.requests.RequestDetailScreen
import rdsco.fishino.app.ui.requests.RequestsViewModel
import rdsco.fishino.app.ui.profile.ProfileSection as Section

/** The signed-in screens' view models, one set per employer and user. */
class ShellViewModels(
    val home: HomeViewModel,
    val news: NewsViewModel,
    val requests: RequestsViewModel,
    val profile: ProfileViewModel,
    val inbox: InboxViewModel,
    val document: DocumentViewModel,
)

private data class MenuItem(val screen: Screen, val label: Int, val icon: ImageVector)

/** The employee panel's menu, as on the web; nothing of the admin panel. */
private val menu = listOf(
    MenuItem(Screen.Home, R.string.menu_home, Icons.Filled.Home),
    MenuItem(Screen.Documents, R.string.menu_documents, Icons.AutoMirrored.Filled.List),
    MenuItem(Screen.Requests, R.string.menu_requests, Icons.Filled.Edit),
    MenuItem(Screen.News, R.string.menu_news, Icons.Filled.Notifications),
    MenuItem(Screen.Profile, R.string.menu_profile, Icons.Filled.Person),
)

/**
 * The signed-in app: a top bar with the menu button and a side menu that
 * opens and closes with it, from the left where the menu button is (the
 * rest of the layout stays right-to-left). Inner screens add a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppShell(
    services: PanelServices,
    viewModels: ShellViewModels,
    employer: Employer,
    employerLogo: ImageBitmap?,
    user: AppUser,
    appVersion: String,
    onCheckUpdates: (() -> Unit)?,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    /** A screen asked for from outside (a phone notification), and how to mark it handled. */
    pendingOpen: String?,
    onPendingOpenHandled: () -> Unit,
) {
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen>, String>(
            save = { list -> list.map { it.route } },
            restore = { routes -> mutableStateListOf<Screen>().apply { addAll(routes.mapNotNull(Screen::parse).ifEmpty { listOf(Screen.Home) }) } },
        ),
    ) { mutableStateListOf<Screen>(Screen.Home) }

    val current = stack.last()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val avatarVersion by viewModels.profile.avatarVersion.collectAsStateWithLifecycle()
    val unread by services.unread.collectAsStateWithLifecycle()
    val home by viewModels.home.state.collectAsStateWithLifecycle()
    val channels = home.data?.channels.orEmpty()

    fun open(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun openRoot(screen: Screen) {
        stack.clear()
        stack.add(Screen.Home)
        if (screen != Screen.Home) stack.add(screen)
        scope.launch { drawer.close() }
    }

    /** After a new request: the form screens leave the stack, the request opens. */
    fun openSubmitted(id: Long) {
        while (stack.size > 1 && (stack.last() is Screen.FormFill || stack.last() == Screen.Forms)) stack.removeAt(stack.lastIndex)
        stack.add(Screen.RequestDetail(id))
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    LaunchedEffect(pendingOpen) {
        if (pendingOpen == MessageNotifier.OPEN_INBOX) {
            openRoot(Screen.Inbox)
            onPendingOpenHandled()
        }
    }

    NotificationPermission()

    // Nothing of this user stays in memory or in the cache after sign-out.
    DisposableEffect(Unit) {
        onDispose {
            ImageCache.clear()
            scope.launch { services.files.clear() }
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && stack.size > 1) { back() }

    // The drawer opens from the start side: left-to-right only for the drawer itself.
    CompositionLocalProvider(LocalPanel provides services, LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // The employer's logo; the close button at the top left, by the screen edge.
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EmployerLogoImage(employerLogo, contentDescription = employer.name, height = 52.dp, maxWidth = 180.dp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { drawer.close() } }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.menu_close))
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openRoot(Screen.Profile) }
                                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.fullName, avatarVersion, Modifier.size(52.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(user.fullName.ifBlank { user.username }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(employer.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        InboxBox(unread) { openRoot(Screen.Inbox) }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
                        Spacer(Modifier.height(8.dp))

                        val root = stack.lastOrNull { it.isRoot } ?: Screen.Home
                        menu.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(item.label)) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                selected = root == item.screen,
                                onClick = { openRoot(item.screen) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        }

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_privacy)) },
                            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            selected = current == Screen.Privacy,
                            onClick = { openRoot(Screen.Privacy) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.home_logout)) },
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawer.close() }
                                confirmLogout = true
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )

                        if (channels.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                                ChannelsButton(channels)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ThemeChoice(themeMode, onThemeMode)
                        Spacer(Modifier.height(8.dp))
                        if (onCheckUpdates != null) {
                            TextButton(onClick = onCheckUpdates, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.home_check_updates))
                            }
                        }
                        // Fishino, small, at the foot of the menu.
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Image(painterResource(R.drawable.fishino_logo), contentDescription = null, modifier = Modifier.size(28.dp))
                            Text(
                                stringResource(R.string.menu_powered_by, appVersion).persianDigits(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                modifier = Modifier.background(PageGradient),
                topBar = {
                    if (current == Screen.Home) {
                        HomeHeader(
                            name = home.data?.profile?.fullName?.takeIf { it.isNotBlank() } ?: user.fullName.ifBlank { user.username },
                            employerName = employer.name,
                            employerLogo = employerLogo,
                            unread = unread,
                            onInbox = { openRoot(Screen.Inbox) },
                            onMenu = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } },
                            onLogout = { confirmLogout = true },
                        )
                    } else {
                        TopAppBar(
                        title = { Text(title(current), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            if (!current.isRoot) {
                                IconButton(onClick = ::back) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back))
                                }
                            }
                        },
                        // As on the home screen: sign-out, messages, then the menu at the left edge.
                        actions = {
                            IconButton(onClick = { confirmLogout = true }) {
                                Icon(painterResource(R.drawable.ic_power), contentDescription = stringResource(R.string.home_logout), tint = SignOutRed)
                            }
                            IconButton(onClick = { openRoot(Screen.Inbox) }) {
                                BadgedBox(badge = { if (unread > 0) Badge() }) {
                                    Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.inbox_title))
                                }
                            }
                            IconButton(onClick = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                    }
                },
                floatingActionButton = {
                    if (current != Screen.Home && current.isRoot && channels.isNotEmpty()) ChannelsButton(channels)
                },
                // Bottom left: the end side in right-to-left.
                floatingActionButtonPosition = FabPosition.End,
                containerColor = Color.Transparent,
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                    ScreenContent(current, viewModels, employer.apiBaseUrl, appVersion, ::open, ::openSubmitted, ::openRoot)
                }
            }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            text = { Text(stringResource(R.string.menu_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text(stringResource(R.string.home_logout)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    viewModels: ShellViewModels,
    apiBaseUrl: String,
    appVersion: String,
    open: (Screen) -> Unit,
    openSubmitted: (Long) -> Unit,
    openRoot: (Screen) -> Unit,
) {
    val openSection: (String) -> Unit = { tab -> open(Screen.ProfileSection(tab)) }

    when (screen) {
        Screen.Home -> HomeScreen(
            viewModel = viewModels.home,
            requestsViewModel = viewModels.requests,
            appVersion = appVersion,
            actions = HomeActions(
                openDocuments = { openRoot(Screen.Documents) },
                openDocument = { open(Screen.DocumentDetail(it)) },
                openRequests = { openRoot(Screen.Requests) },
                openRequest = { open(Screen.RequestDetail(it)) },
                newRequest = { open(Screen.Forms) },
                openForm = { open(Screen.FormFill(it)) },
                openNews = { openRoot(Screen.News) },
                openNewsItem = { open(Screen.NewsDetail(it)) },
                openProfileSection = openSection,
            ),
        )
        Screen.Documents -> DocumentsScreen(viewModels.home) { open(Screen.DocumentDetail(it)) }
        Screen.Inbox -> InboxScreen(viewModels.inbox) { link ->
            when (link.type) {
                "request" -> open(Screen.RequestDetail(link.id))
                "report" -> open(Screen.DocumentDetail(link.id))
            }
        }
        is Screen.DocumentDetail -> DocumentScreen(viewModels.document, screen.id, onOpenView = { open(Screen.DocumentView(it)) }, onOpenProfileSection = openSection)
        is Screen.DocumentView -> DocumentViewerScreen(viewModels.document, screen.id, apiBaseUrl, openSection)
        Screen.Requests -> RequestsScreen(viewModels.home, onNewRequest = { open(Screen.Forms) }, onOpen = { open(Screen.RequestDetail(it)) })
        Screen.News -> NewsListScreen(viewModels.news) { open(Screen.NewsDetail(it)) }
        Screen.Profile -> ProfileScreen(viewModels.profile, openSection)
        is Screen.NewsDetail -> NewsDetailScreen(viewModels.news, screen.id)
        is Screen.RequestDetail -> RequestDetailScreen(viewModels.requests, screen.id)
        Screen.Forms -> FormsScreen(viewModels.requests, onOpen = { open(Screen.FormFill(it)) }, onOpenProfileSection = openSection)
        is Screen.FormFill -> FormFillScreen(viewModels.requests, screen.id, onSubmitted = openSubmitted, onOpenProfileSection = openSection)
        is Screen.ProfileSection -> ProfileSectionScreen(viewModels.profile, screen.tab, openSection)
        Screen.Privacy -> PrivacyScreen()
    }
}

@Composable
private fun title(screen: Screen): String = stringResource(
    when (screen) {
        Screen.Home -> R.string.app_name
        Screen.Inbox -> R.string.inbox_title
        is Screen.DocumentDetail -> R.string.document_title
        is Screen.DocumentView -> R.string.document_title
        Screen.Documents -> R.string.list_documents_title
        Screen.Requests -> R.string.list_requests_title
        Screen.News -> R.string.menu_news
        Screen.Profile -> R.string.menu_profile
        is Screen.NewsDetail -> R.string.menu_news
        is Screen.RequestDetail -> R.string.requests_detail_title
        Screen.Forms -> R.string.requests_new
        is Screen.FormFill -> R.string.requests_new
        is Screen.ProfileSection -> Section.of(screen.tab).title
        Screen.Privacy -> R.string.menu_privacy
    },
)
