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

# Ground 2.0 Shared Mobile Module

`org.groundplatform.v2:mobile` contains the Ground 2.0 mobile app: its screens,
state (`MobileAppState`), and root composable (`GroundMobileApp()`), built on
the domain, data, and shared UI in [`../app/`](../app/). Every mobile host runs
the same `GroundMobileApp()`: `androidApp`, `iosApp`, and the Wasm mobile build
used for testing in a browser.

`MobileAppState` holds the session state and actions only the mobile app uses
(onboarding, downloaded surveys, uploads, offline maps, settings, GPS and
wayfinding, and the map controls); what both apps use stays in `app`'s
`AppSession`.

The module targets Android, JVM (for tests), and WasmJS. iOS targets are
opt-in with `-Pground.ios=true`, and are paused until `shared/app` gets iOS
targets of its own.

## Screens

1.  **Sign In Screen (`AppScreen.SIGN_IN`)**
    -   Displays the Ground welcome header, field survey map preview card, and a
        **Sign in with Google** button (only Google authentication enabled for
        now).
2.  **Terms of Service Screen (`AppScreen.TERMS_OF_SERVICE`)**
    -   Displays the scrollable Terms of Service sections, agreement checkbox,
        and **Decline** / **Agree & Continue** actions.
3.  **Download Survey Screen (`AppScreen.DOWNLOAD_SURVEY`)**
    -   Displays the list of all surveys shared with the signed-in user.
    -   Includes a live **Search bar** to filter surveys by **name** (title or
        description), **location** (region, country, or coordinates), or
        **organization** name.
    -   Each survey item displays a **title**, **location**, **organization**
        (when the survey belongs to one), **description**,
        **map thumbnail** (stylized placeholder), and a **`✓ Downloaded`**
        indicator badge on surveys already downloaded for offline use.
4.  **Main Survey UI (`AppScreen.MAIN_SURVEY` in `MainSurveyScreen.kt`)**
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
    -   **Interactive Map View & `Layers` Drawer (`LayerDef`)**: Displays Ground
        geospatial entity geometries with a **`Layers`** control button
        (`LayersControlSheet`):
        -   **Map features**: Geospatial Entity Lists (spatial master
            tables) representing target locations/features on the map (`#1`–`#4`
            with GNSS wayfinding HUD). Form submission geometries are not shown
            on the map or in cluster chips.
        -   **Clustering**: Map features group into cluster balloons when zoomed
            out below about 13z.
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
        Provides options for **Surveys**, **Offline maps** (Mapbox vector/raster
        tile packages, user-friendly device storage breakdown chart showing total, free,
        downloaded imagery, and data storage, plus 500 MB storage guardrail), **Settings**
        (Metric/Imperial units, in-app language locale switcher, device storage chart, and
        uploaded media cache eviction), **Terms of Service**, and **Sign out**.

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
