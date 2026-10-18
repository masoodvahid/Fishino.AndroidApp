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

android {
    namespace = "ir.fishino.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.fishino.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DIRECTORY_URL", "\"$directoryUrl\"")
        buildConfigField("String", "PRODUCT_SLUG", "\"$productSlug\"")
        buildConfigField("String", "DIRECTORY_PUBLIC_KEY", "\"$directoryPublicKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
tasks.matching { task -> listOf("assemble", "bundle", "install").any { task.name.startsWith(it) } && !task.name.contains("Test") }
    .configureEach { dependsOn(checkFishinoConfig) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.prov)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
