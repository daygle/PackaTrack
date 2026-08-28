# Implementation Plan - Fix Lint Warnings

This plan addresses several Lint warnings related to Battery Life, Vector Images, Gradle Dependencies, Plurals, and Unused Resources.

## User Review Required

> [!IMPORTANT]
> - **Battery Optimization**: I will change the action to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` which takes the user to a list rather than showing a direct prompt. This is required for Play Store compliance.
> - **Plurals**: I will consolidate `day_count_single` and `day_count_plural` into a single `<plurals name="day_count">`.

## Proposed Changes

### Configuration & Resources

#### [MODIFY] [libs.versions.toml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/gradle/libs.versions.toml)
- Update `androidx-fragment-ktx` from `1.8.2` to `1.9.0`.

#### [MODIFY] [img_empty_parcels_v2.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/drawable/img_empty_parcels_v2.xml)
- Resize vector from `240dp` to `200dp`.

#### [MODIFY] [strings.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/values/strings.xml)
- Convert `shipment_summary`, `day_count`, and `couriers_count_label` to `<plurals>`.

#### [DELETE] [ic_launcher_background.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/drawable/ic_launcher_background.xml)
#### [DELETE] [ic_launcher_foreground.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/drawable/ic_launcher_foreground.xml)
#### [DELETE] [ic_launcher_foreground_modern.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/drawable/ic_launcher_foreground_modern.xml)
#### [DELETE] [img_empty_parcels.xml](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/res/drawable/img_empty_parcels.xml)

---

### UI Components

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/java/com/packatrack/app/ui/settings/SettingsScreen.kt)
- Change `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/java/com/packatrack/app/ui/home/HomeScreen.kt)
- Update usages of `shipment_summary` and `day_count` to use `pluralStringResource`.

#### [MODIFY] [DetailScreen.kt](file:///C:/Users/glen/AndroidStudioProjects/PackaTrack/app/src/main/java/com/packatrack/app/ui/detail/DetailScreen.kt)
- Update usages of `day_count` and `couriers_count_label` to use `pluralStringResource`.

## Verification Plan

### Automated Tests
- Run `./gradlew lint` to verify that the reported warnings are gone.
- Run unit tests to ensure no regressions in logic.

### Manual Verification
- Check the Settings screen to see if the "Fix" button for battery optimization now opens the settings list.
- Verify that the parcel list and detail screens show correct singular/plural text (e.g., "1 day" vs "2 days").
- Verify that the "Empty Parcels" image is still displayed correctly.
