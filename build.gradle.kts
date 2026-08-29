// Top-level build file. Plugins are declared here and applied in modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Pin patched versions of vulnerable buildscript-classpath transitives pulled in by
// the AGP/Kotlin/KSP plugin tooling. These are build-time-only dependencies; the app
// runtime classpath is unaffected. Versions are the smallest releases fixing the
// referenced advisories.
buildscript {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "org.apache.commons:commons-lang3:3.18.0", // GHSA-j288-q9x7-2f5v (uncontrolled recursion)
                "org.bitbucket.b_c:jose4j:0.9.6", // GHSA-3677-xxcr-wjqv (DoS via compressed JWE)
                "org.bouncycastle:bcpkix-jdk18on:1.84", // GHSA-wg6q-6289-32hp (broken crypto algorithm)
                "org.bouncycastle:bcprov-jdk18on:1.84", // GHSA-574f-3g2m-x479, GHSA-c3fc-8qff-9hwx
                "org.bouncycastle:bcutil-jdk18on:1.84", // GHSA-574f-3g2m-x479
                "org.jdom:jdom2:2.0.6.1", // GHSA-2363-cqg2-863c (XXE)
            )
        }
    }
}
