<!--
  Copyright 2026 The Collect One Authors.

  Licensed under the Apache License, Version 2.0 (the 'License');
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an 'AS IS' BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Collect One Android Application

This directory is the runnable Android application entry point (`APK` / `AAB`)
for Collect One.

## Architecture

`androidApp` is intentionally a thin platform wrapper. All application UI,
screens, navigation, ViewModels, and offline data collection logic live in shared
KMP modules ([`../shared/mobile/`](../shared/mobile/),
[`../shared/app/`](../shared/app/), and [`../shared/map/`](../shared/map/)) so
that `androidApp` and [`../iosApp/`](../iosApp/) deliver consistent behavior.

## Contents

-   **`src/androidMain/AndroidManifest.xml`**: Android application manifest,
    permissions (`INTERNET`), theme (`Theme.Material.Light.NoActionBar`), and
    adaptive launcher icon configuration.
-   **`src/androidMain/kotlin/org/openforis/collectone/v2/android/MainActivity.kt`**:
    Android `ComponentActivity` that enables edge-to-edge rendering, wires
    system back navigation, and mounts `MobileScreenHost` inside `AppTheme`.
-   **`src/androidMain/kotlin/org/openforis/collectone/v2/android/AndroidGeoLocationSource.kt`**:
    the device location for location questions, from the platform
    `LocationManager` without Google Play Services: GPS for precise fixes and
    the network provider for a quick rough fix, keeping the better and fresher
    one (`LocationFixSelector`). It listens only while a location question is
    on screen and the app is visible. `MainActivity` asks for the location
    permission when the first location question opens, not at launch, and
    explains why first (`LocationPermissionRationaleDialog` in `shared/mobile`)
    if the user denied it before. Google's fused location provider would be
    faster but needs Play Services; it could be added later as an optional
    build variant behind the same `GeoLocationSource` interface.
-   **`src/androidMain/kotlin/org/openforis/collectone/v2/android/MobileSyncRuntime.kt`**,
    **`CollectOneFirebaseMessagingService.kt`**, and **`ScopedSyncWorker.kt`**:
    Process-wide `AppDataHolder` wiring, Firebase Cloud Messaging (FCM) topic
    subscription delegate (`survey_<survey_id>_def` and
    `survey_<survey_id>_entities`), and WorkManager background catch-up worker.
-   **`src/androidMain/res/`**: Collect One Cloud-Acacia adaptive launcher icon
    vector drawables and mipmap resources.

## Configuring the Mapbox Access Token

Maps in the mobile UI are rendered by `AppMap` from
[`../shared/map/`](../shared/map/) using the Mapbox Maps SDK for Android, and
place search uses the Mapbox Geocoding API when a public access token (`pk.…`)
is present. Without a token, `AppMap` falls back to its built-in preview
renderer (`PreviewMap`).

Keep your Mapbox token in a local `local.properties` file (either
`androidApp/local.properties` or `../local.properties`), which is listed in
[`.gitignore`](../.gitignore) and never committed to source control:

```properties
sdk.dir=/path/to/Android/Sdk
MAPBOX_ACCESS_TOKEN=pk.your_mapbox_public_token_here
```

`build.gradle.kts` resolves the token in the following order and injects it as
`@string/mapbox_access_token` at build time:

-   `MAPBOX_ACCESS_TOKEN` or `mapbox.access.token` in `local.properties` (or
    `../local.properties`)
-   `-PMAPBOX_ACCESS_TOKEN=pk.…` or `-Pmapbox.access.token=pk.…` Gradle
    project property
-   `MAPBOX_ACCESS_TOKEN` environment variable

## Connecting to the Server

The app loads surveys, organizations, and data from the
[Collect One server](../server/README.md) and doesn't start
without one: it shows setup instructions instead. Set the service URL at build
time (first match wins):

-   `-PUSER_DATA_SERVICE_URL=…` Gradle project property
-   `USER_DATA_SERVICE_URL` environment variable
-   `USER_DATA_SERVICE_URL` in `local.properties` (or `../local.properties`)

For a service running on your machine (`./gradlew :server:run` from
the repository root), the Android emulator reaches the host at
`10.0.2.2`:

```properties
USER_DATA_SERVICE_URL=http://10.0.2.2:8080
```

On a physical device, use your machine's LAN address, or forward the port with
`adb reverse tcp:8080 tcp:8080` and use `http://localhost:8080`. Debug builds
allow plain `http://` URLs; release builds require `https://`, such as a Cloud
Run deployment.

## Configuring Firebase Cloud Messaging (Optional)

When Firebase is configured, the mobile app subscribes to two FCM topics per
downloaded survey (`survey_<survey_id>_def` and `survey_<survey_id>_entities`)
and suppresses idle 3-second foreground polling, relying on silent FCM data
tickles (plus foreground resume and connectivity-restore catch-up) to fetch
scoped deltas. When Firebase is not configured, the app automatically falls back
to foreground polling.

To keep the repository free of committed `google-services.json` files or API
keys, pass `FIREBASE_WEB_CONFIG` (the same JSON object used by `webApp` and
`mobileWebApp`, containing `projectId`, `appId`, `apiKey`, and
`messagingSenderId`) via `local.properties`, a `-PFIREBASE_WEB_CONFIG=…` Gradle
property, or the `FIREBASE_WEB_CONFIG` environment variable:

```properties
FIREBASE_WEB_CONFIG={"projectId":"your-firebase-project","appId":"1:1234567890:android:abcdef","apiKey":"your-api-key","messagingSenderId":"1234567890"}
```

`MobileSyncRuntime` initializes `FirebaseApp` at startup from this configuration
whenever no default `FirebaseApp` is already initialized.

## Building and Installing

Run Gradle commands from the repository root:

### Build a Debug APK

```sh
./gradlew :androidApp:assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

To install directly onto a connected Android device or running emulator:

```sh
./gradlew :androidApp:installDebug
```

### Build Android App Bundles (`.aab`)

```sh
# Debug app bundle
./gradlew :androidApp:bundleDebug

# Release app bundle (signed with debug key for internal testing)
./gradlew :androidApp:bundleRelease
```

Outputs:

-   `build/outputs/bundle/debug/androidApp-debug.aab`
-   `build/outputs/bundle/release/androidApp-release.aab`
