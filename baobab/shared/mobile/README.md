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

# Ground 2.0 Shared Mobile Module (`shared/mobile`)

`org.groundplatform.v2:mobile` contains all shared **Android + iOS** mobile
functionality—spanning **mobile-specific services and business logic** as well
as **Compose Multiplatform mobile UI and ViewModels**.

Because `androidApp` and `iosApp` implement identical offline field data
collection functionality while `webApp` is a separate survey management and data
exploration console, this module targets **only Mobile platforms** (`jvm`/Android,
`iosArm64`, `iosSimulatorArm64`).

## Internal Package Structure (`org.groundplatform.v2.mobile`)

-   **`data/`**: Mobile-only local persistence (SQLite/Room local database,
    offline submission queue, local entity & survey cache, offline map tile
    storage).
-   **`services/`**: Mobile-specific domain services and background workflows
    (incremental background sync engine, high-accuracy GPS/location tracking,
    polygon walk/geotrace recorder, camera & media file management).
-   **`ui/`**: Mobile Compose screens (map home screen, survey picker drawer,
    sync status bottom sheet, form data collection host), navigation graph, and
    screen ViewModels.
-   **`src/androidMain/`**: Android-specific `actual` implementations
    (permissions, FusedLocationProvider, WorkManager sync).
-   **`src/iosMain/`**: iOS-specific `actual` implementations (`CoreLocation`,
    `BGTaskScheduler`) and `MainViewController()` (`ComposeUIViewController`)
    exported as the unified `GroundMobile.framework` consumed by
    [`../../iosApp/`](../../iosApp/).

## Dependencies

-   [`../core/`](../core/) (`org.groundplatform.v2:protoforms`)
-   [`../ui/`](../ui/) (`org.groundplatform.v2:protoforms-ui`)
