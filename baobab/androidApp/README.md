<!--
  Copyright 2026 The Ground Authors.

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

# Ground 2.0 Android Application

This directory is the runnable Android application entry point (`APK` / `AAB`)
for Ground 2.0.

## Architecture

`androidApp` is intentionally a thin platform wrapper. All application UI,
screens, navigation, ViewModels, and offline data collection logic live in shared
Kotlin Multiplatform modules ([`../shared/mobile/`](../shared/mobile/),
[`../shared/map/`](../shared/map/), and
[`../devtools/prototypeApp/`](../devtools/prototypeApp/)) so that `androidApp`
and [`../iosApp/`](../iosApp/) deliver consistent behavior.

## Contents

-   **`src/androidMain/AndroidManifest.xml`**: Android application manifest,
    permissions (`INTERNET`), theme (`Theme.Material.Light.NoActionBar`), and
    adaptive launcher icon configuration.
-   **`src/androidMain/kotlin/org/groundplatform/v2/android/MainActivity.kt`**:
    Android `ComponentActivity` that enables edge-to-edge rendering, wires
    system back navigation, and mounts `MobileScreenHost` inside `GroundTheme`.
-   **`src/androidMain/res/`**: Ground 2.0 Cloud-Acacia adaptive launcher icon
    vector drawables and mipmap resources.

## Configuring the Mapbox Access Token

Maps in the mobile UI are rendered by `GroundMap` from
[`../shared/map/`](../shared/map/) using the Mapbox Maps SDK for Android, and
place search uses the Mapbox Geocoding API when a public access token (`pk.…`)
is present. Without a token, `GroundMap` falls back to its built-in preview
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

## Building and Installing

Run Gradle commands from `baobab/androidApp/`:

### Build a Debug APK

```sh
./gradlew assembleDebug
```

Output: `build/outputs/apk/debug/androidApp-debug.apk`

To install directly onto a connected Android device or running emulator:

```sh
./gradlew installDebug
```

### Build Android App Bundles (`.aab`)

```sh
# Debug app bundle
./gradlew bundleDebug

# Release app bundle (signed with debug key for internal testing)
./gradlew bundleRelease
```

Outputs:

-   `build/outputs/bundle/debug/androidApp-debug.aab`
-   `build/outputs/bundle/release/androidApp-release.aab`
