package rdsco.fishino.app

import android.content.Context
import rdsco.fishino.app.employer.DirectoryTokenVerifier
import rdsco.fishino.app.employer.EmployerDirectory
import rdsco.fishino.app.employer.EmployerStore
import okhttp3.OkHttpClient
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
}
