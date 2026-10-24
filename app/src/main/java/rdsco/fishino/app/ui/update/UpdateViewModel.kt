package rdsco.fishino.app.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rdsco.fishino.app.update.ApkCheck
import rdsco.fishino.app.update.ApkInstaller
import rdsco.fishino.app.update.AppVersion
import rdsco.fishino.app.update.DownloadResult
import rdsco.fishino.app.update.GitHubReleases
import rdsco.fishino.app.update.Release
import rdsco.fishino.app.update.ReleaseLookup
import rdsco.fishino.app.update.UpdateDownloader
import rdsco.fishino.app.update.UpdateStore
import java.io.File

enum class UpdateFailure { Network, NoApk, Corrupt, WrongSignature, NotNewer }

sealed interface UpdateState {
    data object Idle : UpdateState
    /** Only for a check the user asked for; automatic checks stay silent. */
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Failed(val reason: UpdateFailure, val release: Release?) : UpdateState
}

/**
 * Update flow: latest GitHub release → user agrees → download (size and
 * SHA-256 checked) → package name, version and signing key checked →
 * Android's installer, where the user confirms. Nothing is installed without
 * the user's confirmation.
 */
class UpdateViewModel(
    private val releases: GitHubReleases,
    private val downloader: UpdateDownloader,
    private val installer: ApkInstaller,
    private val store: UpdateStore,
    currentVersionName: String,
) : ViewModel() {

    private val currentVersion = AppVersion.parse(currentVersionName)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    val enabled: Boolean get() = releases.enabled && currentVersion != null

    init {
        // A previous update has been installed (or abandoned): drop its APK.
        viewModelScope.launch(Dispatchers.IO) { downloader.clear() }
    }

    /** On app start: at most once per [CHECK_INTERVAL_MS], silent unless there is an update. */
    fun autoCheck() {
        if (!enabled || job?.isActive == true) return

        job = viewModelScope.launch {
            if (System.currentTimeMillis() - store.lastCheckAt() < CHECK_INTERVAL_MS) return@launch
            lookup(manual = false)
        }
    }

    fun checkNow() {
        if (!enabled || job?.isActive == true) return

        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            lookup(manual = true)
        }
    }

    private suspend fun lookup(manual: Boolean) {
        val next = when (val result = releases.latest()) {
            is ReleaseLookup.Found -> {
                store.markChecked()
                when {
                    currentVersion != null && result.release.version > currentVersion -> UpdateState.Available(result.release)
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            }
            ReleaseLookup.NoRelease -> {
                store.markChecked()
                if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
            // Not marked as checked: the APK may still be building, try again next launch.
            ReleaseLookup.NoApk -> if (manual) UpdateState.Failed(UpdateFailure.NoApk, null) else UpdateState.Idle
            ReleaseLookup.Failed -> if (manual) UpdateState.Failed(UpdateFailure.Network, null) else UpdateState.Idle
        }

        _state.value = next
    }

    fun download() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            var shownPercent = 0

            val result = downloader.download(release) { progress ->
                val percent = (progress * 100).toInt()
                if (percent != shownPercent) {
                    shownPercent = percent
                    _state.value = UpdateState.Downloading(release, progress)
                }
            }

            _state.value = when (result) {
                is DownloadResult.Done -> verified(release, result.file)
                DownloadResult.Corrupt -> UpdateState.Failed(UpdateFailure.Corrupt, release)
                DownloadResult.Failed -> UpdateState.Failed(UpdateFailure.Network, release)
            }
        }
    }

    private suspend fun verified(release: Release, file: File): UpdateState {
        val check = withContext(Dispatchers.IO) { installer.check(file) }
        if (check == ApkCheck.Ok) return UpdateState.ReadyToInstall(release, file)

        withContext(Dispatchers.IO) { file.delete() }

        val reason = when (check) {
            ApkCheck.WrongSignature, ApkCheck.WrongPackage -> UpdateFailure.WrongSignature
            ApkCheck.NotNewer -> UpdateFailure.NotNewer
            else -> UpdateFailure.Corrupt
        }

        // Retrying cannot fix a bad signature or an older version.
        return UpdateState.Failed(reason, release.takeIf { reason == UpdateFailure.Corrupt })
    }

    fun canInstall(): Boolean = enabled && installer.canInstall()

    /** The installer when allowed, otherwise the settings page that allows it. */
    fun installIntent(): Intent? {
        if (!enabled) return null
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return null
        if (!ready.file.exists()) {
            _state.value = UpdateState.Failed(UpdateFailure.Corrupt, ready.release)
            return null
        }

        return if (installer.canInstall()) installer.installIntent(ready.file) else installer.permissionSettingsIntent()
    }

    fun cancelDownload() {
        val downloading = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        _state.value = UpdateState.Available(downloading.release)
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
