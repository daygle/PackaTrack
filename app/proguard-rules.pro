# ============================================================================
# PackaTrack R8 configuration (release builds only; debug is never minified).
#
# Room, SQLCipher and WorkManager do publish consumer rules today, and the sections
# for them below repeat those rules verbatim. They are repeated on purpose: the
# entire data layer depends on them, and if a future library update ever stopped
# shipping proguard.txt the only symptom would be a release build failing at
# runtime - the encrypted database refusing to open, or nothing loading at all.
# Applying an identical rule twice is a no-op, so the repetition costs nothing.
#
# The remaining sections cover gaps those libraries do NOT cover.
# ============================================================================

# --- Stack traces -----------------------------------------------------------
# Keep line numbers so a release crash can be de-obfuscated against
# app/build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room -------------------------------------------------------------------
# Room looks up the generated AppDatabase_Impl through Class.forName(), so the
# database class and its generated implementation must keep their exact names.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
-dontwarn androidx.room.paging.**
-dontwarn androidx.lifecycle.LiveData

# --- SQLCipher --------------------------------------------------------------
# JNI resolves net.zetetic.database.* classes and their descriptor types by name.
# Renaming either side breaks the encrypted database at runtime.
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }
-keepclasseswithmembernames class net.zetetic.database.** {
    native <methods>;
}
-dontwarn net.zetetic.database.**

# --- WorkManager ------------------------------------------------------------
# Workers are created by class name, and the name is persisted in WorkManager's
# database, which outlives an app update. Stable names mean work enqueued by an
# older build still resolves to the right class after the app is updated.
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers public class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class androidx.work.WorkerParameters

# --- Google API client (Gmail Smart Import) ---------------------------------
# This stack publishes no consumer rules at all. google-http-client serialises the
# Gmail models by reflecting over their declared fields, so the model classes, their
# field names and the generic signatures all have to survive shrinking.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,EnclosingMethod,InnerClasses

-keep class com.google.api.services.gmail.** { *; }
-keep class com.google.api.client.util.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.googleapis.extensions.android.gms.auth.** { *; }

# --- OkHttp -----------------------------------------------------------------
# OkHttp 5.5.1 publishes no rules. It probes for optional TLS providers that this
# app does not bundle, which R8 would otherwise report as missing classes.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# google-http-client ships an Apache HTTP transport this app never uses (all
# traffic goes through OkHttp and google-http-client's NetHttpTransport).
-dontwarn org.apache.http.**
