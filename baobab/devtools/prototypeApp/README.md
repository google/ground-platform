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

# Ground 2.0 UI Prototype App (`devtools/prototypeApp`)

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
            the map with the **Search bar** and expands into the full
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

## Web Dashboard Page (`#dashboard`)

Switch to the web application via the **Web app** option in the debug tools menu (bug icon in the header), or
deep-link to `http://localhost:8091/#dashboard`. It shares state with the mobile
prototype, so selections and survey changes carry over.

-   **Top toolbar**: Displays Ground branding, the active survey title
    with its location below it, a **Manage survey** button to enter the
    **Survey editor**, and user profile controls.
-   **Left panel**: The searchable list of map features and places. Each map
    feature is a single line: a geometry icon (point, line, polygon, or none
    for data table records), its label, and its marker circle (filled
    with the `marker-color`, holding the `marker-symbol`). Hover a row to see its status text. A small
    chevron tab centered on the panel's right edge collapses it; when collapsed,
    the same tab at the map's left edge expands it. Drag the panel's right
    separator (8 dp, with a drag handle beside the tab) to resize it (240–560 dp,
    300 dp by default).
-   **Map**: The live survey map fills the main area. Selecting a map feature
    (in the list, on the map, or in a table) fits the whole feature into the
    uncovered part of the map (points stop at about 16z); records
    without geometry don't move the map. Click empty map to clear the
    selection. A floating **Map / Satellite** toggle sits in the upper-left
    corner; the scale bar stays in the lower-left.
-   **Details card**: The selected feature's details float in the upper-right
    corner (`WebEntityDetailsCard`) in two tabs. **Data** shows a compact
    status chip (`marker-symbol` and status text on the `marker-color`), then
    its properties arranged vertically, with references to other records shown
    as links. **Show in table** opens the bottom table on the feature's row.
    **History** lists its submissions grouped by form; opening one shows it in
    the tab, in a document-style view.
-   **Data tables**: A collapsible panel docks to the bottom of the map with
    one tab per entity dataset (map layers and data tables), with the selected
    record highlighted. The first column is a compact status chip. It never
    opens on its own: expand it with its arrow button, a tab, or the card's
    **Show in table** button. Click a row to select that record. Submissions
    are never shown in tables, since their data can be hierarchical.

Code lives in `WebDashboardPage.kt`.

## Survey Editor Page (`#survey-editor`)

Open the survey editor from the web dashboard via the **Edit survey** button in
the left-hand survey header (and return to the dashboard with the back arrow in
the survey editor header or via the debug tools menu), or deep-link
directly to `http://localhost:8091/#survey-editor` (`#form-editor` also works).
The left-hand navigation lists:

-   **Survey details**: Title, description, survey ID, and languages, plus
    summary cards that link to each section.
-   **Sharing**: Invite people by email with a role (Viewer, Data collector,
    Survey organizer), change or remove roles, and set general access
    (Restricted, Anyone with the link, Public) and what data collectors can
    see. These mirror `acl.proto`.
    -   Each new invite gets an invite link (`https://ground.example.org/join/…`)
        with **Copy link** and **New link** actions. Until it's accepted, the
        person is listed by email with an **Invited** badge.
    -   **Open as invitee** previews what the invitee sees after opening the
        link and signing in: they confirm their full name and profile photo.
        Accepting caches both on their entry, and the list then shows them
        by name and photo. The prototype draws placeholder portraits
        (`avatar:<n>`) instead of loading real photo URLs.
-   **Forms**, **Map layers**, **Data tables**: Headings list the survey's
    items. Use **+** to add a new one, and drag items to reorder them within
    their heading (screen readers get **Move up** / **Move down** actions).
    -   Each **Map layer** opens an entity editor with:
        -   An interactive map with a live Satellite / Terrain basemap (web
            builds). Drag to pan; scroll, double-click, or use **+ / −** to
            zoom; **Fit** frames all features. Click a feature to select it
            (synced with the table). Drag the selected feature's vertices to
            reshape it, drag its ○ midpoint handles to insert a vertex, or drag
            inside it to move it. **Add point / line / polygon** draws a new
            feature by clicking on the map; double-click (or click the first
            polygon vertex) to finish.
        -   An editable feature table, including geometry as `lat, lng; …`.
        -   Style controls for color, stroke, fill, and default visibility.
    -   Each **Data table** opens a spreadsheet-style entity editor.
    -   Both share the dataset settings (ID, key and label properties, adding
        in the field) and property schema editing. They mirror
        `EntityDatasetDef` / `EntityRecord`.

Code lives in `surveyeditor/` (`SurveyEditorModels.kt`, `SurveyEditorState.kt`,
`SurveyEditorPage.kt`, `EntityDatasetEditor.kt`, `InteractiveLayerMap.kt`,
`LayerEditorGeometry.kt`). Drag-to-reorder for the navigation and the flow canvas shares
`formeditor/DragReorder.kt`.

The layer editor map and the survey area thumbnail are `GroundMap`s from
`shared/map`, like the survey map. Features are drawn as map style layers;
vertex and midpoint handles, labels, and the in-progress drawing are a draw-only
Compose overlay placed with `MapCameraState.project`. Vertex and feature drags
are claimed through a `MapDragHandler`; all other drags pan the map. Click
hit-testing stays in the editor (`hitTestFeature`) so polygons select by
containment and lines and points by a 12 dp tolerance. Without a Mapbox renderer
(JVM desktop) the map module's `PreviewMap` draws a grid instead of imagery.

### Form Editor

Selecting a Form opens the visual Form editor in `formeditor/`
(`FormEditorModels.kt`, `FormEditorState.kt`, `FormEditorPage.kt`):

-   **Flow canvas**: Shows a mini preview of every question screen, from
    `Start` to `Review & submit`. Arrows show each possible transition. Solid
    arrows mean the next screen is always shown, dashed arrows lead to a
    conditional screen, and arcs show where screens get skipped when their
    display logic is false. The header shows how many potential paths the
    Form has.
-   **Properties panel**: Edits the selected question's type, label, hint,
    name, required flag, choices, and display logic (`relevant`).
-   **Add / delete / duplicate / reorder**: Use **Add question**, which inserts
    after the selected screen, plus the actions in the panel. Drag a screen
    card along the canvas to reorder it: other screens slide aside to show
    where it lands, and the canvas scrolls at its edges. The `←` / `→`
    buttons on the selected card also move it one step.
-   **XForms XML**: Shows the generated ODK-compatible XForms and confirms it
    parses with the Ground form engine.
-   **Preview flow**: Runs the generated Form in a device frame with the shared
    `MobileFormRunner`. The side panel lists the current path, which updates as
    display logic changes.

## Running the Local Development Web Server

From `devtools/prototypeApp/`, start the local `webpack-dev-server` for the
**WasmJS** target (defaults to port `8091`):

```bash
# Start the WasmJS browser development server on port 8091:
./gradlew wasmJsBrowserDevelopmentRun

# Or with continuous live-reload on source code changes:
./gradlew wasmJsBrowserDevelopmentRun --continuous
```

Kotlin/Wasm compiles to WebAssembly GC (`*.wasm`) and is the only web target.
When debugging in Chrome DevTools, enable **Settings → Preferences → Console →
Custom formatters** so Wasm GC `Struct` instances render as readable Kotlin
objects (note that local variable names in the **Scope** pane still include a
`$` prefix).

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

## Deploying to Firebase Hosting Preview Channels

To create or update a temporary deployment on Firebase Hosting in `gnd-dev`, run the provided deploy script from `devtools/prototypeApp`:

```bash
# Build and update the default 'prototype-app' preview channel (7-day expiration):
./scripts/deploy-preview.sh

# Or specify a custom channel name or expiration period:
./scripts/deploy-preview.sh --channel my-feature --expires 14d

# Or skip the Gradle build if you have already run wasmJsBrowserDistribution:
./scripts/deploy-preview.sh --skip-build
```

The script:
1. Compiles the production Kotlin/Wasm bundle via `./gradlew wasmJsBrowserDistribution`.
2. Mints a short-lived service account credential with automatic cleanup via `trap`.
3. Deploys exclusively to the designated preview channel, keeping the live site untouched.
4. Updates the preview URL in-place and extends its expiration window.

