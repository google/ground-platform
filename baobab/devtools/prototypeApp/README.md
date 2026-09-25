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

# Ground 2.0 Mobile UI Prototype App (`devtools/prototypeApp`)

A Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) web application
that embeds a live mobile device preview of the Ground 2.0 Mobile UI alongside a
UX Co-Design Workbench for rapid iteration with UX designers.

## Prototype Screens Included

1.  **Sign In Screen (`PrototypeScreen.SIGN_IN`)**
    -   Displays the Ground welcome header, field survey map preview card, and a
        **Sign in with Google** button (only Google authentication enabled for
        now).
2.  **Terms of Service Screen (`PrototypeScreen.TERMS_OF_SERVICE`)**
    -   Displays the scrollable Terms of Service sections, agreement checkbox,
        and **Decline** / **Agree & Continue** actions.
3.  **Download Survey Screen (`PrototypeScreen.DOWNLOAD_SURVEY`)**
    -   Displays the list of all surveys shared with the signed-in user.
    -   Includes a live **Search bar** to filter surveys by **name** (title or
        description) or **location** (region, country, or coordinates).
    -   Each survey item displays a **title**, **location**, **description**,
        **map thumbnail** (stylized placeholder), and a **`✓ Downloaded`**
        indicator badge on surveys already downloaded for offline use.
4.  **Main Survey UI (`PrototypeScreen.MAIN_SURVEY` in `MainSurveyScreen.kt`)**
    -   **Unified Persistent Bottom Sheet (`SurveyPersistentBottomSheetContent`)**:
        Replaces separate `Map | List` screens with a single multi-stage bottom
        sheet over the live Mapbox canvas:
        -   **Default / Peek State (No site selected)**: Peeks at the bottom of
            the map with the **Search bar** and category filter chips (`All`,
            `Places`, `Map features`) and expands into the
            full searchable list (`BottomSheetSearchableListContent`).
        -   **Selected Site State (`EntityBottomSheetCard`)**: Tapping a site on
            the map or selecting a site in the expanded list transitions the
            bottom sheet in-place to `EntityBottomSheetCard` (with a `← All
            sites` back pill), reusing shared `EntitySummaryHeader`,
            `EntityMetadataAndActionsRow`, and `FormGroupedSubmissionsSection`
            composables.
    -   **Interactive Map View & `Layers` Drawer (`LayerDef`)**: Displays Ground
        geospatial entity geometries with a **`Layers`** control button
        (`MapLayersControlSheet`):
        -   **Map features**: Geospatial Entity Lists (spatial master
            tables) representing target locations/features on the map (`#1`–`#4`
            with GNSS wayfinding HUD). Form submission geometries are not shown
            on the map or in cluster chips.
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
        tile packages + 500 MB storage cap), **Settings** (Metric/Imperial
        units, in-app language locale switcher, and uploaded media cache
        eviction), **Terms of Service**, and **Sign out**.

## Running the Local Development Web Server

From `devtools/prototypeApp/`, start the local `webpack-dev-server` using either
the **WasmJS** or **JS (IR)** target (defaults to port `8091`):

```bash
# Start the WasmJS browser development server on port 8091:
./gradlew wasmJsBrowserDevelopmentRun

# Or with continuous live-reload on source code changes:
./gradlew wasmJsBrowserDevelopmentRun --continuous

# Alternatively, start the JS (IR) browser development server:
./gradlew jsBrowserDevelopmentRun
```

### Choosing Between `wasmJsBrowserDevelopmentRun` and `jsBrowserDevelopmentRun`

-   **`wasmJsBrowserDevelopmentRun` (Kotlin/Wasm — Default)**: Compiles Kotlin
    to WebAssembly GC (`*.wasm`). Provides near-native runtime performance, fast
    incremental linking, and matches the primary production target. When
    debugging in Chrome DevTools, enable **Settings → Preferences → Console →
    Custom formatters** so Wasm GC `Struct` instances render as readable Kotlin
    objects (note that local variable names in the **Scope** pane still include
    a `$` prefix).
-   **`jsBrowserDevelopmentRun` (Kotlin/JS IR — Debugging & Fallback)**:
    Compiles Kotlin to standard JavaScript (`*.js`). Best when stepping through
    complex state or inspecting objects in Chrome DevTools, as Kotlin classes
    and locals map directly to native JS objects and can be evaluated in the
    DevTools Console without Wasm `$` wrappers.

### Custom Port Override

```bash
./gradlew wasmJsBrowserDevelopmentRun -Pport=8095
```

## Running Tests & Building Bundles

```bash
# Run JVM unit tests verifying onboarding flow, search filtering, and download state:
./gradlew jvmTest

# Compile and verify JS and WasmJS targets:
./gradlew check compileKotlinJs compileKotlinWasmJs
```
