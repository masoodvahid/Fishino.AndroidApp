package rdsco.fishino.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

enum class ApkCheck { Ok, WrongPackage, NotNewer, WrongSignature, Unreadable }

/**
 * Hands a downloaded APK to Android's package installer. Android itself
 * refuses an update signed with a different key; [check] catches that (and
 * other mistakes) first so the user gets a clear message instead of the
 * installer's generic "App not installed".
 */
class ApkInstaller(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun check(apk: File): ApkCheck {
        val archive = archiveInfo(apk) ?: return ApkCheck.Unreadable
        if (archive.packageName != context.packageName) return ApkCheck.WrongPackage

        val installed = installedInfo() ?: return ApkCheck.Unreadable
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return ApkCheck.NotNewer

        val archiveSigners = signers(archive)
        val installedSigners = signers(installed)

        // Some older devices do not report signatures of an APK file; the
        // installer still enforces the match in that case.
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners != installedSigners) {
            return ApkCheck.WrongSignature
        }

        return ApkCheck.Ok
    }

    /**
     * Android 8+ asks the user once to allow this app to install updates.
     * False when the build does not declare REQUEST_INSTALL_PACKAGES (store
     * builds): Android then throws instead of answering.
     */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        try {
            packageManager.canRequestPackageInstalls()
        } catch (e: SecurityException) {
            false
        }

    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)

        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(apk: File): PackageInfo? = runCatching {
        packageManager.getPackageArchiveInfo(apk.path, signatureFlag())
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedInfo(): PackageInfo? = runCatching {
        packageManager.getPackageInfo(context.packageName, signatureFlag())
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }

        return signatures.orEmpty().filterNotNull().toSet()
    }
}
