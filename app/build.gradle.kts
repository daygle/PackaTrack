import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.packatrack.app"
    compileSdk = 37

    // Versions come from the build environment first (the release workflow derives both from
    // the pushed tag), then from -P Gradle properties, then from the defaults below.
    val buildVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("VERSION_NAME") as? String)?.takeIf { it.isNotBlank() }
        ?: "1.0.0"
    val buildVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
        ?: (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull()
        ?: 1

    defaultConfig {
        // Play package name, and permanent once the first bundle is uploaded. It deliberately
        // differs from the namespace above: the store identifier is the short form, while the
        // Kotlin/R classes stay in com.packatrack.app. Manifest entries such as .MainActivity
        // resolve against the namespace, so they are unaffected.
        applicationId = "com.packatrack"
        minSdk = 24
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "com.packatrack.app.HiltTestRunner"
    }

    // Release signing: credentials come from environment variables (CI secrets) or, for local
    // builds, from an untracked keystore.properties in the project root. See README "Release
    // signing". When no keystore is configured the release build stays unsigned, which Google
    // Play will not accept.
    val releaseSigningProps = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    fun releaseSigningValue(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: releaseSigningProps.getProperty(name)?.takeIf { it.isNotBlank() }

    val releaseKeystorePath = releaseSigningValue("RELEASE_KEYSTORE_FILE")
    val releaseKeystore = releaseKeystorePath?.let { rootProject.file(it) }
    val releaseSigningConfigured = releaseKeystore != null && releaseKeystore.exists()

    if (releaseKeystore != null && releaseKeystore.exists()) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = releaseSigningValue("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // R8 strips unused code and obfuscates; the resource shrinker drops unused
            // resources. Keep rules live in proguard-rules.pro. The R8 mapping is embedded in
            // the .aab, so Google Play picks up the deobfuscation file automatically.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/INDEX.LIST"
        resources.excludes += "META-INF/DEPENDENCIES"
        jniLibs {
            keepDebugSymbols.add("**/libandroidx.graphics.path.so")
            keepDebugSymbols.add("**/libsqlcipher.so")
        }
    }

    testOptions {
        unitTests {
            // Android framework stubs throw under JVM unit tests; return defaults so pure-logic
            // tests (backup crypto, merge planning) can run without an Android runtime.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:common"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)

    // Backup/encryption tests exercise Room-backed database code directly.
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.ktx)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
