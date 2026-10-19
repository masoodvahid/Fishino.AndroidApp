package rdsco.fishino.app

import android.content.Context
import rdsco.fishino.app.employer.DirectoryTokenVerifier
import rdsco.fishino.app.employer.EmployerDirectory
import rdsco.fishino.app.employer.EmployerStore
import okhttp3.OkHttpClient
import rdsco.fishino.app.update.ApkInstaller
import rdsco.fishino.app.update.GitHubReleases
import rdsco.fishino.app.update.UpdateDownloader
import rdsco.fishino.app.update.UpdateStore
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hand-wired dependencies. Small enough that a DI framework would only add
 * weight; revisit when the app grows.
 */
class AppContainer(context: Context) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val employerStore = EmployerStore(context)

    val employerDirectory = EmployerDirectory(
        client = httpClient,
        directoryUrl = BuildConfig.DIRECTORY_URL,
        productSlug = BuildConfig.PRODUCT_SLUG,
        verifier = DirectoryTokenVerifier(BuildConfig.DIRECTORY_PUBLIC_KEY),
    )

    private val userAgent = "Fishino-Android/${BuildConfig.VERSION_NAME}"

    val githubReleases = GitHubReleases(httpClient, BuildConfig.UPDATE_REPO, userAgent)

    // An APK download may take minutes on a slow line: no overall time limit.
    val updateDownloader = UpdateDownloader(
        client = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build(),
        directory = File(context.cacheDir, "updates"),
        userAgent = userAgent,
    )

    val apkInstaller = ApkInstaller(context.applicationContext)

    val updateStore = UpdateStore(context)
}
