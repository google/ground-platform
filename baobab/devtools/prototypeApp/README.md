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

# Ground 2.0 Mobile UI Prototype App (`devtools/prototypeApp`)

A Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) web application
that embeds a live mobile device preview of the Ground 2.0 Mobile UI alongside a
UX Co-Design Workbench for rapid iteration with UX designers.

## Prototype Screens Included

1.  **Splash / Loading Screen (`PrototypeScreen.SPLASH`)**
    -   Displays the Ground emblem, title, tagline, and workspace initialization
        progress bar with a prototype action to continue to Sign In.
2.  **Sign In Screen (`PrototypeScreen.SIGN_IN`)**
    -   Displays the Ground welcome header, field survey map preview card, and a
        **Sign in with Google** button (only Google authentication enabled for
        now).
3.  **Terms of Service Screen (`PrototypeScreen.TERMS_OF_SERVICE`)**
    -   Displays the scrollable Terms of Service sections, agreement checkbox,
        and **Decline** / **Agree & Continue** actions.
4.  **Download Survey Screen (`PrototypeScreen.DOWNLOAD_SURVEY`)**
    -   Displays the list of all surveys shared with the signed-in user.
    -   Includes a live **Search bar** to filter surveys by **name** (title or
        description) or **location** (region, country, or coordinates).
    -   Each survey item displays a **title**, **location**, **description**,
        **map thumbnail** (stylized placeholder), and a **`✓ Downloaded`**
        indicator badge on surveys already downloaded for offline use.
5.  **Main Survey UI (`PrototypeScreen.MAIN_SURVEY` in `MainSurveyScreen.kt`)**
    -   **Map & List View Switcher**: Top segmented control switching between
        **Map View** (`SurveyMapView`) and **List View** (`SurveyListView`).
    -   **Interactive Map View & `Layers` Button (`LayerDef`)**: Displays Ground
        geospatial entities (`EntityType.GEOSPATIAL` polygons and points with
        reference badges `#1`–`#4` and GNSS wayfinding HUD) and a **`Layers`**
        control button (`MapLayersControlSheet`) to toggle which entity layers
        are visible on the map.
    -   **Entity Bottom Sheet (`1:1` vs `1:N` Submission Model)**:
        -   Clicking any geospatial entity on the map opens
            `EntityBottomSheetCard` showing the entity label, dataset,
            `SubmissionModel` badge, deterministic `GeoID`, area/perimeter
            metrics, `Summary PDF` export badge, and baseline properties.
        -   **`1:1` (`SubmissionModel.SINGLE_1_TO_1`) with data**: Renders the
            submission data directly inline inside the bottom sheet card
            (`OneToOneInlineSubmissionCard`).
        -   **`1:N` (`SubmissionModel.MULTIPLE_1_TO_N`)**: Renders a
            chronological list of submissions (data collector name and
            timestamp) that can be clicked to inspect the **Full Submission
            Details** (`SubmissionFullDetailsCard`).
    -   **Searchable List View (`SurveyListView`)**: Search bar and filter tabs
        (`All`, `Forms`, `Entities`, `Submissions`) across the survey's forms,
        geospatial entities, and field submissions.
    -   **Hamburger / Navigation Drawer (`MainSurveyNavigationDrawerOverlay`)**:
        Provides options to **Switch / download surveys**, **Manage offline
        maps** (Mapbox vector/raster tile packages + 500 MB storage cap),
        **Settings** (Metric/Imperial units, in-app language locale switcher,
        and uploaded media cache eviction), **Terms of Service**, and **Sign
        out**.

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
