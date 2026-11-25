<!--
  Copyright 2026 The Collect One Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Collect One iOS Application (`iosApp`)

This directory is the runnable iOS application entry point (`Xcode` project) for
Collect One.

## Architecture

Like [`../androidApp/`](../androidApp/), `iosApp` is a thin native host. It
embeds the `AppMobile` framework compiled from [`../shared/mobile/`](../shared/mobile/)
and mounts `MainViewController()` inside a SwiftUI `UIViewControllerRepresentable`
view (`ContentView.swift`).

## Contents

-   **`iosApp/iOSApp.swift`**: `@main` SwiftUI application lifecycle entry
    point and `UIApplicationDelegate` adapter for silent APNs/FCM remote
    notifications and `BGAppRefreshTask` catch-up sync.
-   **`iosApp/ContentView.swift`**: SwiftUI host view wrapping
    `MainViewController()` from `AppMobile.framework`.
-   **`iosApp/AppMapboxView.swift`**: the Mapbox map behind `AppMap`
    (`shared/map`). Implements the Kotlin `MapboxIosView` interface on the
    Mapbox Maps SDK for iOS; registered in `iOSApp.swift`.
-   **`iosApp/Info.plist`**: iOS bundle metadata, background modes
    (`remote-notification`, `fetch`), `BGTaskSchedulerPermittedIdentifiers`,
    `USER_DATA_SERVICE_URL`, and the Mapbox access token (`MBXAccessToken`).
-   **`project.yml`**: XcodeGen configuration for generating `iosApp.xcodeproj`.

## How to Build and Run (macOS only)

1.  **Build the Kotlin native framework**:
    Compile the Kotlin multiplatform code into an iOS framework first (`-Pcollectone.ios=true` enables the iOS targets in `shared/app` and `shared/mobile`):
    ```sh
    # From the repository root:
    ./gradlew -Pcollectone.ios=true :shared:mobile:linkDebugFrameworkIosSimulatorArm64
    ```
2.  **Generate the Xcode project**:
    Use [XcodeGen](https://github.com/yonaskolb/XcodeGen) to generate the project file.
    ```sh
    cd iosApp
    xcodegen generate
    ```
3.  **Open in Xcode**:
    ```sh
    open iosApp.xcodeproj
    ```
    Set `USER_DATA_SERVICE_URL` (e.g., `http://localhost:8080`) in the scheme's environment variables or Xcode build settings, select a simulator (e.g., iPhone 15 Pro), and run (Cmd+R).

### Build a Development `.ipa` for the Mobile Web Workbench

To package a development `.ipa` (and optional Apple OTA manifest `collect-one-ios.plist` served at `/collect-one-ios.html` in the mobile web workbench):

```sh
# Ad-hoc signed build for one-tap Safari install over HTTPS:
./iosApp/scripts/build-ipa.sh \
  --method ad-hoc \
  --team-id <APPLE_TEAM_ID> \
  --stage-base-url https://mobile.example.org

# Unsigned build (no Apple Developer certificate; sideload via AltStore, SideStore, Sideloadly, or Xcode):
./iosApp/scripts/build-ipa.sh --method unsigned
```

Output: `iosApp/build/outputs/ipa/collect-one.ipa` (staged automatically by the Firebase deploy scripts when present).

## Push Notifications & Background Sync

`AppMobileSyncIos` (`shared/mobile/src/iosMain/`) bridges iOS push notifications and background tasks into the shared `SyncCoordinator`:

-   **Topic subscriptions (`IosPushTopicBridge`)**: When `FirebaseMessaging` is configured in the host app, assign an `IosPushTopicBridge` implementation to `AppMobileSyncIos.shared.topicBridge` at startup. `SurveyTopicSubscriptionManager` automatically subscribes downloaded surveys to `survey_<survey_id>_def` and `survey_<survey_id>_entities` and suppresses idle 3-second foreground polling. Without a bridge, the app automatically falls back to foreground polling.
-   **Silent push wakeups (`content-available: 1`)**: `AppDelegate.application(_:didReceiveRemoteNotification:fetchCompletionHandler:)` forwards the data payload (`survey_id`, `scope`, `record_version`) to `AppMobileSyncIos.shared.handleRemoteNotification`, which runs a scoped delta sync before invoking the UIKit completion handler.
-   **Opportunistic background refresh (`BGAppRefreshTask`)**: Registered for `org.openforis.collectone.v2.sync.refresh` and forwarded to `AppMobileSyncIos.shared.runBackgroundCatchUp`.

## Firebase Crashlytics (Optional)

`project.yml` adds `FirebaseCrashlytics` from `firebase-ios-sdk` and configures a conditional
`Upload Crashlytics dSYMs` post-build script. At launch, `iOSApp.swift` registers
`IosFirebaseCrashlyticsBridge` on `AppMobileSyncIos.shared.crashReporterBridge` and calls
`AppMobileSyncIos.shared.configureLogging`:

-   Supply a local, gitignored `iosApp/iosApp/GoogleService-Info.plist` (and regenerate
    `iosApp.xcodeproj` with `xcodegen generate` on macOS) to enable Crashlytics and dSYM uploads.
-   Collection is enabled in non-`DEBUG` builds when `GoogleService-Info.plist` is bundled, and
    degrades to a no-op when the plist is absent.

## Mapbox

Maps in the shared UI are drawn by `AppMap` from
[`../shared/map/`](../shared/map/). On iOS it renders with the Mapbox Maps SDK
for iOS, which XcodeGen adds as a Swift package (`project.yml`, pinned to the
same version as the Android SDK in `shared/map`).

### How it fits together

The SDK is a Swift API, and Kotlin/Native can only call C and Objective-C, so
the work is split:

-   **Kotlin** (`shared/map/src/iosMain/`): `PlatformMap.ios.kt` turns
    `MapContent` into Mapbox Style Specification JSON, applies changes as a
    diff, follows `MapCameraState`, and handles all gestures in Compose. It
    hosts the native view in a non-interactive `UIKitView`, so touches always
    go to Compose. SVG icons are rasterized to PNG with Skia.
-   **Swift** (`iosApp/AppMapboxView.swift`): implements the small Kotlin
    `MapboxIosView` interface (`MapboxIos.kt`). Each method passes JSON straight
    to one Mapbox call (`loadStyle`, `addSource`, `addLayer`, `addImage`,
    `setCamera`, `queryRenderedFeatures`) and logs failures instead of
    throwing. It also forwards taps on Mapbox's attribution button, which the
    Compose gesture layer would otherwise swallow.
-   **Registration**: `iOSApp.swift` sets
    `AppMapIos.shared.viewFactory = AppMapboxViewFactory()` at launch.
    The `AppMobile` framework exports `shared/map` so Swift can see these
    types (`shared/mobile/build.gradle.kts`).

If no factory is registered, or the factory returns `nil` because there is no
token, `AppMap` falls back to its preview renderer: the app still runs, just
without basemap imagery.

### Access token

The SDK needs a public access token (`pk.…`). `Info.plist` reads it into
`MBXAccessToken` from the `MAPBOX_ACCESS_TOKEN` build setting, which is empty in
`project.yml`. Supply it locally and never commit it, e.g.:

```sh
xcodebuild -scheme iosApp MAPBOX_ACCESS_TOKEN=pk.… build
```

or set `MAPBOX_ACCESS_TOKEN` under the target's Build Settings in Xcode.

### Checking the Kotlin side on Linux

Kotlin/Native can compile iOS klibs on Linux, which type-checks the Kotlin
renderer and its UIKit and Skia calls (it can't link the framework):

```sh
# From the repository root:
./gradlew :shared:map:compileKotlinIosSimulatorArm64 :shared:map:compileKotlinIosArm64
./gradlew -Pcollectone.ios=true :shared:app:compileKotlinIosSimulatorArm64 :shared:app:compileKotlinIosArm64
./gradlew -Pcollectone.ios=true :shared:mobile:compileKotlinIosSimulatorArm64 :shared:mobile:compileKotlinIosArm64
```

### Status and first run on a Mac

The Kotlin renderer compiles for both iOS targets. Nothing below has been run
yet:

1.  **Regenerate the Xcode project.** The committed
    `iosApp.xcodeproj/project.pbxproj` predates the Mapbox package and
    `AppMapboxView.swift`; run `xcodegen generate` and commit the result.
2.  **Compile the Swift.** `AppMapboxView.swift` targets the Mapbox Maps SDK
    for iOS v11 but hasn't been built. The APIs most likely to need small fixes
    are `MapboxMap.loadStyle(_:completion:)` (URL and JSON overloads),
    `QueriedRenderedFeature.layers`, and `ornaments.attributionButton`.
3.  **Link the framework.** `linkDebugFrameworkIosSimulatorArm64` should now
    export `shared/map`; check that Swift sees `AppMapIos`,
    `MapboxIosViewFactory`, and `MapboxIosView`. The first build after this
    change may need network access, so drop `--offline` if it fails.
4.  **See a map.** No screen in `MobileApp` uses `AppMap` yet, so a
    map only appears once one does. When it does, check that the basemap
    loads, Compose markers stay aligned while panning and zooming, feature
    taps are reported, and the attribution button opens Mapbox's dialog.
