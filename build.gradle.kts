import javax.inject.Inject

// Top-level build file. Plugins are declared here and applied in modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Pin patched versions of vulnerable buildscript-classpath transitives pulled in by
// the AGP/Kotlin/KSP plugin tooling. These are build-time-only dependencies; the app
// runtime classpath is unaffected. Versions are the smallest releases fixing the
// referenced advisories.
buildscript {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "org.apache.commons:commons-lang3:3.21.0", // GHSA-j288-q9x7-2f5v (uncontrolled recursion)
                "org.bitbucket.b_c:jose4j:0.9.7", // GHSA-3677-xxcr-wjqv (DoS via compressed JWE)
                "org.bouncycastle:bcpkix-jdk18on:1.86", // GHSA-wg6q-6289-32hp (broken crypto algorithm)
                "org.bouncycastle:bcprov-jdk18on:1.86", // GHSA-574f-3g2m-x479, GHSA-c3fc-8qff-9hwx
                "org.bouncycastle:bcutil-jdk18on:1.86", // GHSA-574f-3g2m-x479
                "org.apache.httpcomponents:httpclient:4.5.14", // GHSA-7r82-7xv7-xcpj / CVE-2020-13956 (XSS, fixed in 4.5.13) - tooling declares 4.5.6
                "org.jdom:jdom2:2.0.6.1", // GHSA-2363-cqg2-863c (XXE)
                "com.google.guava:guava:33.7.2-jre", // GHSA-xxph-c9ww-hj94 / CVE-2026-102554 (deserialization OOM), -jre for build tooling
            )
        }
    }
}

// AGP also resolves its lint tooling (lint-gradle -> sdk-common, sdklib, httpmime) per project,
// outside the buildscript classpath, so the forces above don't reach it and the vulnerable
// versions it declares still end up in the dependency graph. A component metadata rule applies
// to every resolution in a project (detached configurations included), raising each pinned
// module to the patched version wherever it is declared. Modules that aren't already in a graph
// are untouched, so the app runtime classpath is unaffected.
@CacheableRule
abstract class RequirePatchedVersions @Inject constructor(
    private val pins: Map<String, String>,
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                forEach { dependency ->
                    pins["${dependency.group}:${dependency.name}"]?.let { patched ->
                        dependency.version { require(patched) }
                    }
                }
            }
        }
    }
}

val patchedVersions = buildscript.configurations["classpath"].resolutionStrategy.forcedModules
    // Guava ships separate -jre and -android artifacts; promoting the -jre build into every graph
    // would swap the app's Android flavor, so it gets its own per-configuration rule below.
    .filterNot { it.group == "com.google.guava" && it.name == "guava" }
    .associate { "${it.group}:${it.name}" to checkNotNull(it.version) }

allprojects {
    dependencies.components.all(RequirePatchedVersions::class.java) {
        params(patchedVersions)
    }
}

// Raise Guava to the release that fixes GHSA-xxph-c9ww-hj94 (patched in 33.7.2) without switching a
// graph from one published flavor to the other: Android variant classpaths stay on -android, while
// lint, annotation processors and other JVM-only classpaths stay on -jre.
val guavaVersion = "33.7.2"

allprojects {
    configurations.configureEach {
        val configurationName = name
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.guava" && requested.name == "guava") {
                val usesAndroidFlavor = requested.version?.endsWith("-android") == true ||
                    configurationName.contains("CompileClasspath") ||
                    configurationName.contains("RuntimeClasspath")
                useVersion("$guavaVersion-${if (usesAndroidFlavor) "android" else "jre"}")
            }
        }
    }
}
