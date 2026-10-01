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

# Ground Map

A Compose Multiplatform map for Android, iOS, WasmJS, and JVM. Apps describe
what to draw as immutable data, and a thin per-platform renderer draws it
with that platform's native map SDK.

Artifact: `org.groundplatform.v2:map`. This module has no knowledge of
surveys, forms, or entities. Apps convert their domain state into
`MapContent`.

## Concepts

| Type | Role |
| :--- | :--- |
| `MapContent` | Immutable description of everything drawn: basemap, GeoJSON sources, style layers, icons, markers, user location. Rebuilt whenever app state changes. |
| `Basemap` | The imagery under the content, chosen by the app: a hosted Mapbox style (`MapboxStyle`), XYZ raster tiles from any server (`RasterTiles`), or `None`. `GroundMap` draws its attribution. |
| `MapCameraState` | Hoisted, observable camera. Gestures and the app both write to it (`move`, `animateTo`, `fitBounds`). `project` / `unproject` position Compose overlays. |
| `MapEvent` | What happened: `FeatureTapped`, `MarkerTapped`, `BackgroundTapped`, `CameraIdle`. |
| `GroundMap` | The composable that ties them together. |

The camera is deliberately not part of `MapContent`. Both the user and the
app move it, so it lives in a mutable state holder, like
`LazyListState` or maps-compose `CameraPositionState`.

## Usage

```kotlin
val camera = rememberMapCameraState(CameraPosition(LatLng(-1.29, 36.82), zoom = 12.0))
val content = remember(sites, selectedId) {
  MapContent(
    sources = listOf(GeoJsonSource("sites", sites.map { it.toMapFeature(selectedId) })),
    layers = listOf(
      MapLayer.Fill("site-fill", "sites", color = StyleValue.Constant(Color(0xFF1E8E3E))),
      MapLayer.Line(
        "site-outline", "sites",
        color = StyleValue.Match("selected", mapOf("true" to Color.Yellow), Color.White),
      ),
    ),
  )
}
GroundMap(content, camera, onEvent = { event ->
  when (event) {
    is MapEvent.FeatureTapped -> select(event.featureId)
    is MapEvent.BackgroundTapped -> select(null)
    else -> Unit
  }
})
```

Tap events carry the tapped position (`FeatureTapped.at`,
`BackgroundTapped.at`), so apps can run their own hit-testing with
`cameraState.project(event.at)` when they need different rules.

### Static maps and editing

-   `gesturesEnabled = false` makes a static map, e.g. a thumbnail in a
    scrolling list: no pan, zoom, or tap events.
-   `dragHandler` lets an editor take over one-finger drags. After touch slop
    `MapDragHandler.onDragStart(position)` is called with the down position;
    return a `MapDrag` to receive `onDrag` / `onDragEnd` for that gesture
    (moving a vertex, say), or `null` to pan the map as usual.
-   Editing chrome such as vertex handles is best drawn in a draw-only Compose
    `Canvas` above `GroundMap`, placed with `cameraState.project`. Without
    `pointerInput` it doesn't block the map's gestures.

## Platform renderers

`GroundMap` delegates to one `internal expect fun PlatformMap`. Each renderer
applies the ops from `diffMapContent(old, new)` to its SDK, binds
`MapCameraState` while on screen, and reports gestures and taps. It contains
no product logic.

`MapboxStyleSpec` translates `MapContent` into the Mapbox Style Specification
(style, GeoJSON, layer, and expression JSON) once, in common code, so the
Mapbox renderers only hand that JSON to their SDK.

The web renderer keeps the Mapbox GL JS map display-only in a `div` below the
transparent Compose canvas, aligned with the composable's layout every frame.
Gestures run in Compose (`mapGestures`, shared with `PreviewMap`) and write
`MapCameraState`, which the map follows. `animateTo` interpolates in common
code when no renderer handles it, and Mapbox shares `WebMercator`'s 512 dp
world, so Compose markers stay aligned with the map. The host page must load
Mapbox GL JS (v1 or later); without it the web renderer falls back to
`PreviewMap`. With v1, `RasterTiles` and `None` basemaps need no access
token.

The Android renderer works the same way: a display-only `MapView` below a
Compose gesture layer. Mapbox Android also measures in dp with 512 dp tiles,
so zoom passes through unchanged. The host app supplies the access token
(`com.mapbox.common.MapboxOptions.accessToken` or a `mapbox_access_token` string resource),
which the SDK requires for every basemap; without one the renderer falls
back to `PreviewMap`. The SDK comes from Mapbox's public Maven repository
(see `settings.gradle.kts`); apps that build the Android target need the
same repository.

The iOS renderer is the same again, except that the Mapbox Maps SDK for iOS
is a Swift API that Kotlin/Native can't call. The app implements the small
`MapboxIosView` interface in Swift (`iosApp/iosApp/GroundMapboxView.swift`)
and registers a factory in `GroundMapIos.viewFactory` at launch. Every call
carries Style Specification JSON from `MapboxStyleSpec`, so the Swift side is
a pass-through. SVG icons are rasterized with Skia, which Compose already
ships on iOS. Without a registered factory or an access token
(`MBXAccessToken`), the renderer falls back to `PreviewMap`.

| Source set | Renderer | Status |
| :--- | :--- | :--- |
| `jvmMain` | `PreviewMap`: Compose Canvas, no basemap imagery | Final |
| `wasmJsMain` | Mapbox GL JS (`MapboxGlJs.kt` bindings) | Done |
| `androidMain` | Mapbox Maps SDK for Android (`MapView` in `AndroidView`, display-only like the web) | Done; not yet run on a device |
| `iosMain` | Mapbox Maps SDK for iOS through `MapboxIosView`, implemented in Swift by `iosApp` and hosted in a `UIKitView` | Code complete; not yet compiled or run (needs macOS) |

## Development

```bash
# From shared/map/
./gradlew jvmTest wasmJsTest
```

`gradlew` here forwards to the Gradle wrapper in `shared/core`, so this build
doesn't add another copy of the binary `gradle-wrapper.jar`.
