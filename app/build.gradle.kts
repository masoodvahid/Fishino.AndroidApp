plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun fishinoProperty(name: String): String =
    (project.findProperty("fishino.$name") as String?)?.trim().orEmpty()

val directoryUrl = fishinoProperty("directoryUrl")
val productSlug = fishinoProperty("productSlug")
val directoryPublicKey = fishinoProperty("directoryPublicKey")
val updateRepo = fishinoProperty("updateRepo")

// Release builds get both from the GitHub release tag (see release.yml).
val appVersionName = fishinoProperty("versionName").ifEmpty { "0.1.0" }
val appVersionCode = fishinoProperty("versionCode").toIntOrNull() ?: 1

// The release key must never change: Android only installs an update that
// is signed with the same key as the installed app. It comes from CI secrets.
val releaseKeystore = System.getenv("FISHINO_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "rdsco.fishino.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "rdsco.fishino.app"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "DIRECTORY_URL", "\"$directoryUrl\"")
        buildConfigField("String", "PRODUCT_SLUG", "\"$productSlug\"")
        buildConfigField("String", "DIRECTORY_PUBLIC_KEY", "\"$directoryPublicKey\"")
    }

    signingConfigs {
        // Public test key (app/test-signing/README.md). Only for "preview".
        create("preview") {
            storeFile = file("test-signing/fishino-test.jks")
            storePassword = "fishino-test"
            keyAlias = "fishino-test"
            keyPassword = "fishino-test"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FISHINO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FISHINO_KEY_ALIAS")
                keyPassword = System.getenv("FISHINO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug builds are signed with each machine's own debug key, so they
        // could never install a release; the updater is off there.
        debug {
            buildConfigField("String", "UPDATE_REPO", "\"\"")
            buildConfigField("String", "UPDATE_ASSET_PATTERN", "\"\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
            buildConfigField("String", "UPDATE_ASSET_PATTERN", "\"^fishino-[0-9][0-9.]*[.]apk$\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Test build attached to GitHub releases until the official key is set
        // up: own package id, so it installs next to (and never replaces) the
        // real app, and a fixed key, so each test release installs over the
        // previous one. Updates itself from the fishino-test-*.apk assets.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            signingConfig = signingConfigs.getByName("preview")
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
            buildConfigField("String", "UPDATE_ASSET_PATTERN", "\"^fishino-test-[0-9][0-9.]*[.]apk$\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

// The app must never ship without the pinned key: without it, it could not
// tell a genuine employer address from a forged one.
val checkFishinoConfig by tasks.registering {
    doLast {
        require(directoryUrl.startsWith("https://") && directoryUrl.endsWith("/")) {
            "fishino.directoryUrl in gradle.properties must be an https URL ending with /"
        }
        require(productSlug.isNotEmpty()) { "fishino.productSlug in gradle.properties is empty" }
        require(directoryPublicKey.isNotEmpty()) {
            "fishino.directoryPublicKey in gradle.properties is empty. Copy data.public_key from " +
                "${directoryUrl}public-key into it."
        }
    }
}

// Enforced for anything that produces an installable app; unit tests do not need it.
val packagingTask = Regex("^(assemble|bundle|install)(Debug|Release|Preview)?$")
tasks.matching { packagingTask.matches(it.name) }
    .configureEach { dependsOn(checkFishinoConfig) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    // SMS User Consent: reads the login code from the SMS after the user allows it.
    implementation(libs.play.services.auth.api.phone)
    // Fingerprint sign-in. BiometricPrompt needs a FragmentActivity; fragment is
    // pinned because biometric 1.1.0 alone pulls a very old one.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.prov)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
