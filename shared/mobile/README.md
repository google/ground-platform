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

# Collect One Shared Mobile Module

`:shared:mobile` contains the Collect One mobile app: its screens,
state (`MobileAppState`), and root composable (`MobileApp()`), built on
the domain, data, and shared UI in [`../app/`](../app/). Every mobile host runs
the same `MobileApp()`: `androidApp`, `iosApp`, and the Wasm mobile build
used for testing in a browser.

`MobileAppState` is the mobile app's shell: where the user is (its navigator),
the Main Survey's map or list mode, the drawer and profile panel, and the
reactions to device connectivity. Screens read the feature ViewModels' state
from `app` directly; `app`'s `SessionCoordinator` wires those ViewModels
together and tells the shell what to show.

The module targets Android, JVM (for tests), and WasmJS. iOS targets are
opt-in with `-Pcollectone.ios=true`, and are paused until `shared/app` gets iOS
targets of its own.

## Screens

1.  **Sign In Screen (`MobileRoute.SignIn`)**
    -   Displays the Collect One welcome header, field survey map preview card, and a
        **Sign in with Google** button (only Google authentication enabled for
        now).
2.  **Terms of Service Screen (`MobileRoute.TermsOfService`)**
    -   Displays the scrollable Terms of Service sections, agreement checkbox,
        and **Decline** / **Continue** actions.
3.  **Download Survey Screen (`MobileRoute.DownloadSurvey`)**
    -   Displays the list of all surveys shared with the signed-in user.
    -   Includes a live **Search bar** to filter surveys by **name** (title or
        description), **location** (region, country, or coordinates), or
        **organization** name.
    -   Each survey item displays a **title**, **location**, **organization**
        (when the survey belongs to one), **description**,
        **map thumbnail** (stylized placeholder), and a **`✓ Downloaded`**
        indicator badge on surveys already downloaded for offline use.
4.  **Main Survey UI (`MobileRoute.MainSurvey` in `MainSurveyScreen.kt`, with
    one file per component next to it in `ui/navigation/`)**
    -   **Floating Top Search Bar (`MobileMapSearchBar`)**: Floating pill bar at
        the top of the map canvas containing the navigation drawer menu button,
        live search input (`Search...`), clear action, and user profile avatar.
    -   **Unified Persistent Bottom Sheet (`SurveyPersistentBottomSheetContent`)**:
        Replaces separate `Map | List` screens with a single multi-stage bottom
        sheet over the live Mapbox canvas:
        -   **Default / Peek State (No site selected)**: Peeks at the bottom of
            the map with the **survey title and area name** in the card heading
            and expands into the full
            searchable list (`BottomSheetSearchableListContent`), with one line
            per map feature (`EntityListRow`: geometry icon, label, and marker
            circle, filled with the `marker-color` and holding the
            `marker-symbol`; long-press for the status text).
        -   **Selected Site State (`EntityBottomSheetCard`)**: Tapping a site on
            the map or in the list fits the whole feature into the map above the sheet and replaces
            the sheet's contents with its details, at about half the screen's
            height. The header shows a status chip (`marker-symbol` and status
            text on the `marker-color`). Details open on the feature's properties; **Submissions
            (n)** is one tap away, and a submission opens in a document-style
            view. Offline, only submissions stored on the device are listed.
            A **pending uploads** chip opens `Uploads` filtered to the feature.
    -   **Interactive Map View & `Layers` Drawer (`LayerDef`)**: Displays the
        geospatial entity geometries with a **`Layers`** control button
        (`LayersControlSheet`):
        -   **Map features**: Geospatial Entity Lists (spatial master
            tables) representing target locations/features on the map (`#1`–`#4`
            with GNSS wayfinding HUD). Form submission geometries are not shown
            on the map or in cluster chips.
        -   **Clustering**: Map features group into cluster balloons when zoomed
            out below about 13z.
        -   **Basemap**: **Satellite** or **Terrain** (the outdoors map with
            contours, hillshade, and roads).
        -   **Downloaded imagery** (devices that can save map areas):
            **Downloaded satellite** and **Downloaded terrain** switches (on by
            default; disabled with "No saved areas") outline the saved areas of
            each kind, satellite dashed and terrain solid, and draw what the
            basemap can't show: saved satellite imagery inside its areas over
            Terrain, contour lines inside terrain areas over Satellite. **Save an
            area** starts the area selection mode.
    -   **Area selection mode (`ui/offlinemaps/OfflineAreaSelectionOverlay.kt`,
        `OfflineAreaSelectionViewModel`)**: Saves a map area for offline use
        without leaving `MobileRoute.MainSurvey`, so the map stays mounted with
        its camera. `MobileAppState.startOfflineAreaSelection()` closes the
        drawer, the profile panel, and any drawer page first; the bottom sheet,
        search bar, chips, and buttons hide while a draw-only `Canvas` frame
        (pan and zoom pass through) shows over a scrim, with saved areas
        outlined. When the camera settles, the frame's corners are unprojected
        into bounds; after 400 ms the estimate and the place name run in
        parallel, and a new move cancels them. A frame over 50 km² turns the
        outline to the error color at once, without a request. The bottom card
        shows the name ("Finding place name…", or "Area near" coordinates),
        **Satellite** and **Terrain** chips (the last one can't be turned off;
        totals update without a new request), "About 48 MB · 12.4 GB free" or
        why the area can't be saved, then the saving progress with **Hide**
        (saving continues in the app's scope) and **Cancel**, **Done**, or
        **Try again**. Close, Back, and Hide return to where the mode started
        (the map or Offline maps). Devices that can't save areas
        (`OfflineRegionMode.UNAVAILABLE`, Android and iOS until their native
        clients land) don't show the ways in; the browser build simulates
        downloads and says so.
    -   **Site-First Entity Bottom Sheet & `simplestyle-spec` Marker Progression**:
        -   All forms linked to an entity dataset follow a unified **`1:N`
            relationship** with entities.
        -   Entity points, lines, and polygons render `simplestyle-spec` style
            properties (`marker-symbol`, `marker-color`, `stroke`, `fill`)
            updated dynamically via XLSForm `save_to` bindings across a 3-stage
            workflow progression:
            -   **`○` (Empty Circle)**: Initialized / pending baseline state.
            -   **`◐` (Half-Filled Circle)**: Intermediate / in-progress state
                after initial form submission.
            -   **`✓` (Checkmark)**: Final `"completed"` state after follow-up /
                verification submission.
    -   **Hamburger / Navigation Drawer (`MainSurveyNavigationDrawerOverlay`)**:
        Provides options for **Surveys**, **Offline maps**
        (`ManageOfflineMapsSubScreen`, `OfflineMapsViewModel`: the map areas
        saved on the device, newest first, each with a row per layer showing
        its size, progress and **Cancel**, **Resume**, or **Try again**; Show on
        map and removing an area or a layer, after a confirmation saying how
        much it frees; the measured device storage; **Save an area**; and, in
        the browser build, a notice that downloads are simulated), **Settings**
        (Metric/Imperial units, in-app language locale switcher, device storage chart, and
        uploaded media cache eviction), **Terms of Service**, and **Sign out**.

## Internal Package Structure (`org.openforis.collectone.v2.mobile`)

-   **`data/`**: Reserved for mobile-only local persistence; it holds only a
    README today. The local store, outbox and sync engine live in `shared/app`
    (`app.data`).
-   **`services/`**: Reserved for mobile-specific background services; it holds
    only a README today. Location, storage and push clients live in
    `shared/app` (`app.client`).
-   **`ui/`**: Mobile Compose screens (map home screen, survey picker drawer,
    sync status bottom sheet, form data collection host), navigation graph, and
    screen ViewModels.
-   **`src/androidMain/`**: Android-specific `actual` implementations
    (permissions, FusedLocationProvider, WorkManager sync).
-   **`src/iosMain/`**: iOS-specific `actual` implementations (`CoreLocation`,
    `BGTaskScheduler`) and `MainViewController()` (`ComposeUIViewController`)
    exported as the unified `AppMobile.framework` consumed by
    [`../../iosApp/`](../../iosApp/).

## Building and Testing

Run these from the repository root (the repository is a single Gradle build).

```bash
# JVM unit and Compose UI tests:
./gradlew :shared:mobile:jvmTest
```

### Golden Screenshot Tests

The shared mobile module includes Compose golden-screenshot tests in `src/jvmTest/kotlin/org/openforis/collectone/v2/mobile/ThemeScreenshotTest.kt` that render 15 key screens in both light and dark themes and compare them against checked-in golden images in [`src/jvmTest/resources/goldens/`](src/jvmTest/resources/goldens/).

To run and verify the goldens:

```bash
# Run all mobile tests including goldens:
./gradlew :shared:mobile:jvmTest

# Run only the golden screenshot test:
./gradlew :shared:mobile:jvmTest --tests org.openforis.collectone.v2.mobile.ThemeScreenshotTest
```

When an intentional UI change alters how screens look, regenerate the golden images before committing:

```bash
# Regenerate all mobile golden images:
UPDATE_GOLDENS=true ./gradlew :shared:mobile:jvmTest --tests org.openforis.collectone.v2.mobile.ThemeScreenshotTest
```

To export clean PNG copies to an arbitrary directory for visual inspection or design reviews:

```bash
THEME_SCREENSHOT_DIR=/tmp/mobile-screenshots ./gradlew :shared:mobile:jvmTest --tests org.openforis.collectone.v2.mobile.ThemeScreenshotTest
```

## Dependencies

-   [`../core/`](../core/) (`:shared:core`)
-   [`../ui/`](../ui/) (`:shared:ui`)
