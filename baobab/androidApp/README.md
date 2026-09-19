<!--
  IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

# Ground 2.0 Android Application (`androidApp`)

This directory is the runnable Android application entry point (`APK` / `AAB`)
for Ground 2.0.

## Architecture

`androidApp` is intentionally a thin platform wrapper. All application UI,
screens, navigation, ViewModels, and offline data collection logic are
implemented in [`../shared/mobile/`](../shared/mobile/) (`org.groundplatform.v2:mobile`)
so that `androidApp` and [`../iosApp/`](../iosApp/) deliver identical
functionality.

## Contents

-   **`src/main/AndroidManifest.xml`**: Android application manifest,
    permissions, and deep-link configuration.
-   **`src/main/kotlin/org/groundplatform/v2/android/MainActivity.kt`**:
    Android `ComponentActivity` that mounts `GroundMobileApp()` from
    `shared/mobile`.
