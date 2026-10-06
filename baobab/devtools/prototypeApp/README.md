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
    status chip (`marker-symbol` and status text on the `marker-color`), the
    feature's **Collect data** buttons (one per form linked to its dataset,
    disabled once completed), then its properties arranged vertically, with
    references to other records shown as links. **Show in table** opens the
    bottom table on the feature's row. **History** lists its submissions
    grouped by form; opening one shows it in the tab, in a document-style view.
-   **Data collection on web** (`WebDataCollectionCard.kt`): The mobile entry
    points are adapted to the web paradigm. **Collect data** in the top toolbar
    (the counterpart of the mobile `+` FAB) lists the survey's forms; a
    feature's card has the same **Collect data** buttons as the mobile sheet.
    Either opens the form in the right-hand panel, in place of the details
    card, using the compact layout (`shared/ui` `CompactFormRunner`): every
    question is a collapsible card stacked vertically, with an icon at the left
    showing whether it is answered (✓), skipped (⊘), pending (○), or needs
    attention (!). The cards render at the compact density
    (`FormDensity.COMPACT`), which keeps the mobile widgets as they are except
    that numeric questions and range sliders have no stepper buttons and text
    fields are slightly shorter. All cards start expanded; a card collapses on its own once
    its question is answered (once focus leaves it, for typed answers) or
    skipped, and can be re-opened at any time. Optional questions have a
    **Skip** action, the header shows `n of m answered` with **Expand all** /
    **Collapse all**, and **Submit** in the footer runs the same validation and
    submission flow as mobile; the mobile wizard's "Review & Submit" step is
    not shown as a card and is not counted in the progress. A form opened from
    the toolbar that needs a target feature first shows a picker: click a
    matching feature on the map, in the left-hand list, or in the picker's own
    searchable list; the questions then appear and the target step is left out
    of the stack. There is no field GPS in a browser, so every geometry question
    (`geopoint`, `geotrace`, `geoshape`, "GPS only" or not) is answered by
    drawing on the main map (`WebMapDrawing.kt`): **Draw on map** in the
    question's card routes map clicks to it (`PrototypeAppState.webMapDrawing`,
    the `CompactMapDrawingHost`), a floating chip at the top of the map shows
    the progress (`2 of at least 3`) with **Undo**, **Done**, and **Cancel
    drawing**, and the in-progress vertices, line, or polygon are drawn above
    the map features in the draft style (`SurveyMapContent.draftGeometry`).
    One click places a point; lines and polygons collect vertices until
    **Done**. While drawing, clicks on features add vertices instead of
    selecting them. Every geometry answer the open form already holds stays on
    the map in a settled "in-flow" style (`PrototypeAppState.webFormGeometries`:
    same amber hue as the draft but solid outlines, light fill, white vertex
    discs, and a chip with the question title); clicking one of them, or its
    chip, scrolls the panel to that question and highlights its card for a
    moment (`PrototypeAppState.focusWebFormQuestion` → the runner's
    `focusRequest`).
-   **Media capture on web** (`PrototypeMediaCapture.kt`,
    `src/commonMain/resources/media-capture-bridge.js`): Photo, video, and
    audio questions use the shared `MediaCaptureWidget` with the prototype's
    `PrototypeMediaCaptureHandler`, which honours the organizer's per-question
    source mode (**Capture only** vs **Capture or upload**, the ODK `new`
    appearance). A **capture** request is served by, in order: the native
    camera / recorder app via HTML Media Capture (`<input type="file"
    capture>`) on mobile browsers (`prefersFileInputCapture()`: coarse pointer
    or Android / iOS user agent); otherwise an in-page live capture overlay
    (`openLive()`: `getUserMedia` + `MediaRecorder`) on desktop browsers, with
    a live camera preview, **Capture** / **Start recording** → **Stop**,
    **Switch camera** when several cameras exist, an audio level meter and
    elapsed time for recordings, and a **Use** / **Retake** confirm step
    (photos are JPEG, downscaled to `orx:max-pixels`; clips are `webm`, or
    whatever `MediaRecorder.isTypeSupported` allows); otherwise the request
    fails with "This browser can't capture a photo directly." A capture
    request is never degraded to a file picker, so **Capture only** holds on
    desktop browsers where the `capture` attribute is ignored. The plain file
    picker (`open()` without `capture`) is used only for **Choose from device**
    on questions that allow uploads. `chooseCapturePath` is the pure decision
    function; off the web (JVM tests) the handler falls back to the shared
    `SimulatedMediaCaptureHandler`.
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
-   **Geometry questions**: **Point**, **Line**, and **Polygon** question types
    (`EditorQuestionType.isGeometry`) bind to XForms `geopoint`, `geotrace`,
    and `geoshape`. The first geometry question becomes a new map feature's
    geometry, and its type sets the linked Map layer's point / line / polygon
    kind. Each geometry question has a **Capture** setting
    (`GeometryCapture`): **GPS only** (the default; no body `appearance`, so
    the shared form runner locks the answer to the device's GPS fix or walk)
    or **GPS or draw on map** (`appearance="placement-map"`, letting
    collectors place or draw the geometry on the map as well). The web
    dashboard can only draw on the map, so a Form that is **Available on web**
    with a GPS-only geometry question is invalid: the validator emits a
    per-question error, the question's Capture field and the **Available on
    web** row in Form settings explain it, and the web canvas shows an error
    banner. **Fix all** (`makeGeometryQuestionsWebCompatible`) switches every
    GPS-only question to **GPS or draw on map**. Like other issues, this blocks
    publishing until it's resolved.
-   **Media questions**: **Photo**, **Video**, and **Audio** question types
    (`EditorQuestionType.isMedia`) bind to XForms `binary` and render as an
    `<upload mediatype="image/*" | "video/*" | "audio/*">` control. Each media
    question has a **Media source** setting (`MediaSource`): **Capture or
    upload** (the ODK default; no body `appearance`, so the shared form runner
    offers the camera / microphone and "Choose from device") or **Capture
    only** (`appearance="new"`, so collectors must take a fresh photo, video,
    or recording and can't upload an existing file). Switching between media
    types keeps the setting; switching to any other type resets it.
-   **Add / delete / duplicate / reorder**: Use **Add question**, which inserts
    after the selected screen, plus the actions in the panel. Drag a screen
    card along the canvas to reorder it: other screens slide aside to show
    where it lands, and the canvas scrolls at its edges. The `←` / `→`
    buttons on the selected card also move it one step.
-   **XForms XML**: Shows the generated ODK-compatible XForms and confirms it
    parses with the Ground form engine.
-   **Mobile / Web toggle**: The toolbar's segmented toggle switches the
    canvas between the mobile flow above and the **Web** layout
    (`FormWebPreview.kt`): the Form as collectors see it in the web dashboard's
    right-hand panel, i.e. a read-only stack of the compact question cards at
    the same compact density as the live web form (no numeric steppers,
    slightly shorter text fields), rebuilt from the generated XForms on every
    edit. Geometry questions render
    as the dashboard's "draw on the map" request cards
    (`CompactGeometryInput.MapDrawing`). Clicking a card selects
    that question in the properties panel (the selected card is outlined);
    clicking the empty canvas selects the Form.
-   **Preview flow**: Runs the generated Form in a device frame with the shared
    `MobileFormRunner`, or, with **Web** selected, in a browser-like frame with
    the interactive compact layout in the right-hand panel. The side panel
    lists the current path, which updates as display logic changes.
-   **Form properties**: Click `Start`, `Review & submit`, or empty canvas
    (or **Form settings** in the toolbar) to edit the Form itself.
    **Availability** has two switches, **Available on mobile** and **Available
    on web**, for where collectors can open the Form from that platform's data
    collection entry points; each row's caption spells out the effect
    ("Collectors can open this form in the web dashboard" / "Hidden from
    collectors in the mobile app"). New Forms start mobile-only
    (`FormAvailability.MOBILE` in `domain/model/FormModels.kt`); turn on
    **Available on web** to offer the Form in the web dashboard too. Turning
    both off is allowed and shows a
    warning. While the Form is off for the platform the canvas is previewing, a
    **Not available on …** banner appears above the canvas with an **Enable**
    action, and the preview area is greyed out and non-interactive.
    On the dashboard, the **Collect data** menu and the feature cards' buttons
    only list forms available on web; the mobile `+` FAB sheet and bottom-sheet
    buttons only list forms available on mobile (`PrototypeAppState.webForms` /
    `mobileForms`). The choice is applied to the running survey's forms by form
    ID from the saved editor draft, like Map layer styles. The **Advanced**
    section at the bottom holds the Form's save-to logic
    (`FormSaveToModels.kt`, `FormSaveToEditor.kt`). Advanced sections start
    collapsed (or open when customized or invalid). Expanding or collapsing one
    does the same to all of them for the rest of the session.
-   **Save-to logic**: By default each submission adds a new map feature (if
    the Form has a Point, Line, or Polygon question) or table row to the Form's linked Map
    layer or Data table. Choose **Update existing map feature / table row** to
    pick another Map layer or Data table, how the feature is found (the feature
    the Form was opened from, via `/data/target_entity`, or a question's answer
    matched against a property), and which questions update which properties.
    Switching to updates deletes the Form's linked dataset if it has no
    features yet, otherwise it's kept and unlinked. The generator emits an ODK
    XForms Entities declaration (`entities:entity` with `create="1"` or
    `update="1"`, plus `entities:saveto` binds). Previews and published Forms
    embed the target's features in its secondary instance; the exported XML
    references `jr://file-csv/<dataset>.csv` instead.

## Local Data Store

All app data lives in a local data store, the single source of truth
(`data/datasource/local/store/`). On web it is an in-memory cache that starts
fresh on every page load; on mobile it will become the persistent offline store.

-   **Reads**: `LocalStore` exposes a `Flow` per collection (surveys, forms, map
    layers, map features with their submissions, standalone submissions,
    geometries, survey configs and editor drafts, mutations, places, offline
    tile packages, and preferences). `SurveyAppViewModel.appData` combines them
    into one `StateFlow`; `PrototypeAppState` and the view model's `uiState`
    read from it.
-   **Writes**: Repositories (`data/repository/`) write through `suspend`
    functions inside `LocalStore.transaction`, which is atomic and can be
    nested. Use cases that touch several repositories run in one transaction via
    `TransactionRunner`.
-   **Sample data**: `data/seed/SampleDataSeeder` is the only code that reads the
    hardcoded sample datasets (`PrototypeFake*Data`, `SurveyEditorSamples`,
    `FormEditorSamples`). It fills the store on first launch and again on
    **Reset**. `SampleDataGuardrailTest` keeps other code from reading them.
-   **Survey switching**: Every survey's data is in the store, so switching
    surveys keeps edits. Use **Reset** to go back to the sample data.
-   **Survey editor**: The editor loads the active survey's draft
    (`SurveyEditorDraft`) from the store and saves each change back. Generated
    XForms are published to the survey's `SurveyConfig.formXmlById`, and the
    title and description are shown in the survey list.
-   **Backends**: `InMemoryLocalStore` is the only backend today. A persistent
    backend (Room on `androidx.sqlite`) must pass `LocalStoreContractTest`.

## PDF Reports

Map features and submissions can be exported as PDF reports. The PDFs are
generated on the device, in shared Kotlin (`pdf/`), so they work offline and
look the same on web and mobile.

-   **Mobile**: **Share PDF** (entity actions row, QR code dialog, and the
    submission view) opens a sheet with the generated file (page count and
    size). Tap the file to preview it. **Share** opens the system share sheet
    (WhatsApp, Gmail, Drive, Bluetooth, and others) through the Web Share API.
    Where the browser can't share files, the sheet offers **Download** instead.
-   **Web dashboard**: **Download PDF** saves the file right away, with no
    share sheet. It's in the overflow menu (⋮) of the map feature card and of an
    opened submission, and in the QR code dialog. A snackbar confirms the
    download.
-   **Map feature report**: Workflow status chip (`marker-symbol` drawn as a
    vector shape on the `marker-color`), details, a location figure with a north arrow,
    scale bar, and vertex coordinates, properties (references show the related
    record's label), and every submission with its responses.
-   **Submission report**: Map feature or coordinates, data collector, time,
    form version, and every response, grouped by XForms group. Reports leave
    out upload (sync) status and submission counts.
-   **Implementation**: `PdfDocumentWriter` is a small PDF 1.4 writer with no
    dependencies. It uses the standard Helvetica and Courier fonts, which don't
    need embedding. `PdfReportLayout` handles text wrapping, page breaks, and
    `Page n of N` footers. `RecordPdfReports` builds the two reports. Delivery is
    the only platform-specific code (`PlatformPdfExport`): `pdf-export-bridge.js`
    on web and the temp directory on the JVM.
-   **Limitation**: The standard fonts only cover Windows-1252 (Western European)
    characters. Common symbols are replaced with ASCII (`≤` → `<=`, `📷` →
    `[photo]`); other scripts render as `?`. Embedding a Unicode font (for
    example Noto Sans) is the next step.

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

