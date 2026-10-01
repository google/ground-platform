<!--
  Copyright 2026 The Ground Authors.

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

# Ground 2.0 iOS Application (`iosApp`)

This directory is the runnable iOS application entry point (`Xcode` project) for
Ground 2.0.

## Architecture

Like [`../androidApp/`](../androidApp/), `iosApp` is a thin native host. It
embeds the `GroundMobile` framework compiled from [`../shared/mobile/`](../shared/mobile/)
and mounts `MainViewController()` inside a SwiftUI `UIViewControllerRepresentable`
view (`ContentView.swift`).

## Contents

-   **`iosApp/iOSApp.swift`**: `@main` SwiftUI application lifecycle entry
    point.
-   **`iosApp/ContentView.swift`**: SwiftUI host view wrapping
    `MainViewController()` from `GroundMobile.framework`.
-   **`iosApp/GroundMapboxView.swift`**: the Mapbox map behind `GroundMap`
    (`shared/map`). Implements the Kotlin `MapboxIosView` interface on the
    Mapbox Maps SDK for iOS; registered in `iOSApp.swift`.
-   **`iosApp/Info.plist`**: iOS bundle metadata, location/camera usage
    descriptions, and the Mapbox access token (`MBXAccessToken`).
-   **`project.yml`**: XcodeGen configuration for generating `iosApp.xcodeproj`.

## How to Build and Run (macOS only)

1.  **Build the Kotlin native framework**:
    You need to compile the Kotlin multiplatform code into an iOS framework first.
    ```sh
    cd ../shared/mobile
    ./gradlew linkDebugFrameworkIosSimulatorArm64 --offline
    ```
2.  **Generate the Xcode project**:
    Use [XcodeGen](https://github.com/yonaskolb/XcodeGen) to generate the project file.
    ```sh
    cd ../iosApp
    xcodegen generate
    ```
3.  **Open in Xcode**:
    ```sh
    open iosApp.xcodeproj
    ```
    Select a simulator (e.g., iPhone 15 Pro) and run (Cmd+R).

## Mapbox

Maps in the shared UI are drawn by `GroundMap` from
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
-   **Swift** (`iosApp/GroundMapboxView.swift`): implements the small Kotlin
    `MapboxIosView` interface (`MapboxIos.kt`). Each method passes JSON straight
    to one Mapbox call (`loadStyle`, `addSource`, `addLayer`, `addImage`,
    `setCamera`, `queryRenderedFeatures`) and logs failures instead of
    throwing. It also forwards taps on Mapbox's attribution button, which the
    Compose gesture layer would otherwise swallow.
-   **Registration**: `iOSApp.swift` sets
    `GroundMapIos.shared.viewFactory = GroundMapboxViewFactory()` at launch.
    The `GroundMobile` framework exports `shared/map` so Swift can see these
    types (`shared/mobile/build.gradle.kts`).

If no factory is registered, or the factory returns `nil` because there is no
token, `GroundMap` falls back to its preview renderer: the app still runs, just
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
cd ../shared/map && ./gradlew compileKotlinIosSimulatorArm64 compileKotlinIosArm64
cd ../mobile && ./gradlew compileKotlinIosSimulatorArm64
```

### Status and first run on a Mac

The Kotlin renderer compiles for both iOS targets. Nothing below has been run
yet:

1.  **Regenerate the Xcode project.** The committed
    `iosApp.xcodeproj/project.pbxproj` predates the Mapbox package and
    `GroundMapboxView.swift`; run `xcodegen generate` and commit the result.
2.  **Compile the Swift.** `GroundMapboxView.swift` targets the Mapbox Maps SDK
    for iOS v11 but hasn't been built. The APIs most likely to need small fixes
    are `MapboxMap.loadStyle(_:completion:)` (URL and JSON overloads),
    `QueriedRenderedFeature.layers`, and `ornaments.attributionButton`.
3.  **Link the framework.** `linkDebugFrameworkIosSimulatorArm64` should now
    export `shared/map`; check that Swift sees `GroundMapIos`,
    `MapboxIosViewFactory`, and `MapboxIosView`. The first build after this
    change may need network access, so drop `--offline` if it fails.
4.  **See a map.** No screen in `GroundMobileApp` uses `GroundMap` yet, so a
    map only appears once one does. When it does, check that the basemap
    loads, Compose markers stay aligned while panning and zooming, feature
    taps are reported, and the attribution button opens Mapbox's dialog.
