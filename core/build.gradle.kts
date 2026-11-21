plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.packatrack.core"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // The core tracking engine emits diagnostic logs via android.util.Log,
            // whose stubs throw under JVM unit tests. Return defaults so pure-logic
            // tests can exercise the parsers without an Android runtime.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Entity annotations only; the Room compiler runs in :data, where the @Database lives.
    implementation(libs.room.runtime)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android stubs throw under test).
    testImplementation(libs.org.json)
}
