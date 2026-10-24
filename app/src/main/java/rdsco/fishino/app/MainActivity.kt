package rdsco.fishino.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.notify.MessageNotifier
import rdsco.fishino.app.ui.documents.DocumentViewModel
import rdsco.fishino.app.ui.home.HomeViewModel
import rdsco.fishino.app.ui.inbox.InboxViewModel
import rdsco.fishino.app.ui.news.NewsViewModel
import rdsco.fishino.app.ui.profile.ProfileViewModel
import rdsco.fishino.app.ui.requests.RequestsViewModel
import rdsco.fishino.app.ui.shell.AppShell
import rdsco.fishino.app.ui.shell.ShellViewModels
import rdsco.fishino.app.ui.login.LoginScreen
import rdsco.fishino.app.ui.login.LoginViewModel
import rdsco.fishino.app.ui.onboarding.EmployerCodeScreen
import rdsco.fishino.app.ui.onboarding.EmployerCodeViewModel
import rdsco.fishino.app.ui.theme.FishinoTheme
import rdsco.fishino.app.ui.theme.ThemeMode
import rdsco.fishino.app.ui.update.UpdateDialog
import rdsco.fishino.app.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface EmployerState {
    data object Loading : EmployerState
    data class Ready(val employer: Employer?) : EmployerState
}

/** A FragmentActivity: the biometric prompt is shown through a fragment. */
class MainActivity : FragmentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeOpenRequest(intent)
    }

    /** A phone notification asks to open the inbox (after signing in). */
    private fun takeOpenRequest(intent: Intent?) {
        intent?.getStringExtra(MessageNotifier.EXTRA_OPEN)?.let { (application as FishinoApp).container.pendingOpen.value = it }
        intent?.removeExtra(MessageNotifier.EXTRA_OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FishinoApp).container
        takeOpenRequest(intent)

        setContent {
            val themeMode by container.themePrefs.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System-bar icons follow the app's theme, not only the phone's.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LaunchedEffect(Unit) { container.employerLogo.load() }
            val employerLogo by container.employerLogo.image.collectAsStateWithLifecycle()

            FishinoTheme(themeMode) {
                val scope = rememberCoroutineScope()
                val updates: UpdateViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            UpdateViewModel(
                                releases = container.githubReleases,
                                downloader = container.updateDownloader,
                                installer = container.apkInstaller,
                                store = container.updateStore,
                                // "0.2.0-test" for test builds: compare the numbers only.
                                currentVersionName = BuildConfig.VERSION_NAME.substringBefore('-'),
                            )
                        }
                    },
                )
                LaunchedEffect(Unit) { updates.autoCheck() }

                val employerFlow = remember { container.employerStore.employer.map<Employer?, EmployerState> { EmployerState.Ready(it) } }
                val state by employerFlow.collectAsStateWithLifecycle(initialValue = EmployerState.Loading)

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (val current = state) {
                        EmployerState.Loading -> Unit
                        is EmployerState.Ready -> {
                            val employer = current.employer

                            if (employer == null) {
                                val onboarding: EmployerCodeViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer { EmployerCodeViewModel(container.employerDirectory, container.employerStore, container.employerLogo) }
                                    },
                                )
                                EmployerCodeScreen(onboarding)
                            } else {
                                val session by container.sessionManager.session.collectAsStateWithLifecycle()
                                val signedIn = session

                                if (signedIn == null) {
                                    val login: LoginViewModel = viewModel(
                                        // A new view model (and API address) per employer.
                                        key = "login:${employer.apiBaseUrl}",
                                        factory = viewModelFactory {
                                            initializer {
                                                LoginViewModel(
                                                    api = container.authApi(employer.apiBaseUrl),
                                                    apiBaseUrl = employer.apiBaseUrl,
                                                    sessions = container.sessionManager,
                                                    prefs = container.loginPrefs,
                                                    vault = container.credentialVault,
                                                    messageSync = container.messageSync,
                                                    logo = container.employerLogo,
                                                )
                                            }
                                        },
                                    )
                                    LoginScreen(
                                        viewModel = login,
                                        employerName = employer.name,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        onChangeEmployer = {
                                            scope.launch {
                                                container.loginPrefs.forgetAccount()
                                                runCatching { container.credentialVault.clearAll() }
                                                container.messageSync.stop()
                                                container.employerLogo.clear()
                                                container.employerStore.clear()
                                            }
                                        },
                                    )
                                } else {
                                    // Per employer and user: nothing of another account is shown.
                                    val key = "${employer.apiBaseUrl}:${signedIn.user.id}"
                                    val services = remember(employer.apiBaseUrl) { container.panelServices(employer.apiBaseUrl) }
                                    val pendingOpen by container.pendingOpen.collectAsStateWithLifecycle()
                                    val viewModels = ShellViewModels(
                                        home = viewModel(key = "home:$key", factory = viewModelFactory { initializer { HomeViewModel(services) } }),
                                        news = viewModel(key = "news:$key", factory = viewModelFactory { initializer { NewsViewModel(services) } }),
                                        requests = viewModel(key = "requests:$key", factory = viewModelFactory { initializer { RequestsViewModel(services) } }),
                                        inbox = viewModel(key = "inbox:$key", factory = viewModelFactory { initializer { InboxViewModel(services) } }),
                                        document = viewModel(key = "document:$key", factory = viewModelFactory { initializer { DocumentViewModel(services) } }),
                                        profile = viewModel(
                                            key = "profile:$key",
                                            factory = viewModelFactory {
                                                initializer {
                                                    ProfileViewModel(services, employer.apiBaseUrl, container.credentialVault, container.loginPrefs)
                                                }
                                            },
                                        ),
                                    )
                                    AppShell(
                                        services = services,
                                        viewModels = viewModels,
                                        employer = employer,
                                        employerLogo = employerLogo,
                                        user = signedIn.user,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        onCheckUpdates = if (updates.enabled) updates::checkNow else null,
                                        onLogout = container.sessionManager::signOut,
                                        themeMode = themeMode,
                                        onThemeMode = { mode -> scope.launch { container.themePrefs.set(mode) } },
                                        pendingOpen = pendingOpen,
                                        onPendingOpenHandled = { container.pendingOpen.value = null },
                                    )
                                }
                            }
                        }
                    }
                }

                UpdateDialog(updates, currentVersion = BuildConfig.VERSION_NAME)
            }
        }
    }
}
