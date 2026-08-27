plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.packatrack.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android stubs throw under test).
    testImplementation(libs.org.json)
}
