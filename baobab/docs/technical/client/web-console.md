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

# Web Console Features

The pages of the Ground 2.0 web console ([`shared/web`](../../../shared/web/),
hosted by [`webApp`](../../../webApp/)). Paths in this document are relative to
`shared/web/src/commonMain/kotlin/org/groundplatform/v2/web/` for web-only code
and to `shared/app/src/commonMain/kotlin/org/groundplatform/v2/app/` for code
both apps share (map, data collection, domain, and clients).

## Web Surveys Page (`#surveys`)

The web console's landing page (`#surveys`). Web pages are gated behind a simple
**Sign in with Google** card while the user is signed out.

-   **Survey cards**: Every survey the signed-in user can open, grouped into a
    section per **Organization** (with its logo and the user's role: *You
    manage* or *Member*) followed by **Personal surveys**. Each card shows the
    map thumbnail, title, location, description, an access chip (*Owner*,
    *Organization manager*, *Organization member*, or *Shared with you*), and
    the feature count. Clicking a card opens it in the Web dashboard.
-   **Search and filters**: Free-text search over title, description,
    location, and organization name, plus filter chips for **All**, **My
    surveys**, and one chip per organization the user belongs to.
-   **Closed surveys**: Closed and archived surveys carry a *Closed* /
    *Archived* label. When one's outcome question is due again, people who
    manage it (its owner and its organization's Managers) see a gentle
    **Was this data used?** badge on its card, which opens the **What happened
    with this data?** card as a dialog (Save or Skip). The rule is
    `SurveyOutcomePrompt.isDue` (`domain/model/SurveyOutcomePrompt.kt`): the
    survey is closed or archived, its outcome is missing or *Not yet*, and 90
    days have passed since it was closed or since the last *Not yet* answer,
    whichever is later (so answering *Not yet* again restarts the wait;
    Skip changes nothing). Any other answer hides the badge for good. The
    badge is driven by `SurveyOutcomePromptViewModel` (`ui/viewmodel/`) with an
    injectable clock. The seeded **Household Panel Survey (Past Individuals)**
    was closed on 2026-05-29 without an answer, so its card shows the badge.
-   **Create survey**: Opens a dialog for the title and an optional
    organization (any organization the user is a member of; Managers of that
    organization inherit survey organizer access). **Next** asks **What will
    this data be used for?**: a multi-select card grid of the Purpose Packs
    available in that organization (its own first, then the global ones it
    hasn't hidden; global only for a personal survey), refreshed when the
    organization changes, with optional program chips (e.g. **EU
    Deforestation Regulation (EUDR)**) offered by the selected packs.
    **Create survey** seeds the draft with a copy of each selected pack's
    Form templates (questions, standard field links, choice codes, and a
    linked Map layer or Data table) and records the purposes and programs
    (`SurveyDetails.purposeIds` / `programIds`, mirroring
    `SurveyDef.purpose_ids` / `program_ids`); **Skip** creates an empty
    survey as before (`CreateSurveyUseCase`, `ui/dashboard/CreateSurveyDialog.kt`).
    The new survey opens in the **Survey editor**. The organization page's
    **Create survey** uses the same dialog with the organization fixed.
-   The Web dashboard and Survey editor redirect here when there is no survey
    to open, and **Sign out** from any web page returns here.

## Organizations Pages (`#organizations`, `#organization/<id>`)

Open from **Manage organizations** in the user profile card (avatar in the web
header), or deep-link to `#organizations`. Organizations
are the Ground 2.0 counterpart of Collect Earth Online institutions: a survey
can optionally belong to one, and the organization's **Managers** inherit
Survey organizer access to all of its surveys while **Members** can create
surveys in it and find them in their list.

-   **Organizations list**: *Your organizations* (Managers first, with member,
    survey, and pending-request counts) and *Other organizations* (listed ones
    you can **Request to join**; unlisted organizations are invite-only), with
    search and a **Create organization** dialog (name, description, optional
    **Type** and **Country**, directory listing; the creator becomes its first
    Manager).
-   **Organization page**: Laid out like the survey editor, with a resizable
    left panel that switches between **Organization details**, **Surveys**
    (cards like the Surveys page, plus **Create survey** for members),
    **Impact**, **Members**, **Imagery sources**, and the organization's
    library:
    **Purposes**, **Dictionary**, and **Templates**. Each pane opens with a
    heading that explains what it is for, and the header labels the page as an
    *Organization*.
    -   **Organization details**: Name, description, website, type, country,
        and directory listing. **Type** is an exposed dropdown (government
        agency, cooperative, Indigenous Peoples or local community
        organization, NGO, company, research institution, other, or *Not
        specified*); **Country** is a searchable dropdown over the ISO 3166-1
        alpha-2 list (`domain/model/Countries.kt`) that filters by name or code
        and shows plain text like *Kenya (KE)*. "All users" has neither.
        Managers edit them in place with **Save** / **Discard** and
        can **Delete organization** (its surveys become personal surveys of
        their owners; no data is deleted); everyone else sees them read-only.
        A **Platform-wide numbers** card has the **Keep our data out of
        platform totals** switch (`Organization.exclude_from_platform_aggregates`,
        off by default): Managers flip it, everyone else sees it disabled. The
        organization's own Impact numbers are unaffected. The sample Open Foris
        Community has it on.
    -   **Impact**: Members' view of what the organization's surveys add up to
        (see [Impact Views](#impact-views)), with **Survey** and **Country**
        filters, a **Your indicators** section for the organization's own
        concepts, and **Download PDF summary**. For **"All users"**, the tab is
        the platform-wide view and only its Managers see it.
    -   **Members**: Managers see *Requests to join* (**Approve** / **Decline**),
        can invite people by email as Member or Manager (each invite gets an
        invite link with **Copy link**, **New link**, and **Open as invitee**
        to simulate acceptance, as on the Sharing page), change roles, and
        remove people. Anyone can **Leave organization**. The last Manager
        can't be demoted, removed, or leave; the page says why.
    -   **Dictionary**: A search field (accent- and case-insensitive; labels,
        keywords, and list values in every language, with typo tolerance) over
        the organization's concepts, then the global concepts. Managers **Add
        concept** and edit or delete their organization's concepts in a dialog
        (name, English label and description, data type, unit, list values,
        aggregation, privacy, goals, pillar, and Draft / Stable / Deprecated
        status). Only drafts can be deleted, list values can't be removed, and
        a stable concept's type, unit, or aggregation can't change. Global
        concepts open read-only.
    -   **Templates** and **Purposes**: The organization's Form templates and
        Purpose Packs (Managers can rename or delete them; templates are created
        with the Form editor's **Save as template**), then the global ones, which
        Managers can **Hide** from the organization's pickers. A global purpose
        whose templates are all hidden is hidden too.
    -   **"All users"**: Its library is the global library, so its Managers
        (the platform admins; the sample signed-in user is one) edit
        global concepts, templates, and purposes, and its global imagery
        sources. Everyone else is implicitly a read-only member: it's never
        listed, nobody can request to join or leave, and invites are for
        Managers only.

Code lives in `ui/organization/` (`OrganizationsPage.kt`, `OrganizationPage.kt`,
`OrganizationLibraryPanes.kt` for the library tabs, `OrganizationProfileFields.kt`
for the Type and Country pickers, and `OrganizationPages.kt` for the page's
tabs). Both pages are stateless `(uiState, actions)` screens
driven by `OrganizationViewModel` (`ui/viewmodel/`); the directory, relation,
and member-grouping rules live in `domain/model/OrganizationDirectory.kt`, and
the matching methods on `WebAppState` are one-line delegates to the view
model.

The library model (`domain/model/LibraryModels.kt`: concepts, Form templates,
Purpose Packs, export profiles, library settings, and ID rules) is stored per
organization through `LibraryRepository` (`data/repository/LibraryRepositoryImpl.kt`
on the `LocalStore`). `ResolveLibraryUseCase` combines a survey's organization
library with the global one, `SearchConceptsUseCase` searches it, and
`ManageLibraryUseCase` validates and applies edits. The global library is
seeded from `data/seed/GlobalLibrarySeedData.kt`, a Kotlin copy of the
canonical `shared/assets/library/<vocabulary>.textproto` files;
`GlobalLibrarySeedTest` (`shared/app` jvmTest) fails when they differ, and
running `./gradlew jvmTest -PregenerateLibrarySeed` from `shared/app` rewrites
the files from the Kotlin copy.

## Impact Views

What survey data adds up to, computed in the browser by the shared impact
aggregation job (`shared/core` `org.groundplatform.v2.core.impact`, see
`docs/technical/backend/impact-aggregation.md`), so the web console and the
backend agree. `ComputeImpactUseCase` (`domain/usecase/`) maps every survey's
map features, editor draft (layer columns and form questions linked to
concepts), purposes, outcome, impact events, libraries, and organizations onto
the job's inputs, and describes the rows in plain language (`ImpactSummary`,
`domain/model/ImpactSummaryModels.kt`). `ImpactViewModel` recomputes 300 ms
after the data stops changing, so exporting, creating a receipt, closing a
survey, linking a column, or opting out updates the numbers right away.

-   **Survey Impact** (web dashboard **Impact** tab), **Organization Impact**
    (organization page **Impact** tab, filterable by survey and country), and
    **Platform impact** (the **Impact** tab of **"All users"**, for its
    Managers only; no public dashboard and no map overlays).
-   **Cards**: Unique hectares mapped and plots (plots that share a GeoID count
    once, with the duplicates called out), producers registered (distinct
    producer IDs or names), then one section per goal in plain language (e.g.
    *Deforestation-free supply chains*) with a card per linked global concept:
    counts by code with bars (e.g. commodity), shares (e.g. 67% deforestation
    free), sums, and averages. **Your indicators** lists the organization's own
    concepts; the platform view shows them only under
    **Organization-suggested indicators** (by suggested goal and organization),
    never in the main totals, and leaves out sensitive concepts and opted-out
    organizations.
-   **How the data was used**: Downloads, receipts created and shared (plus
    partner pushes and DOIs when there are any), answers to *What happened with
    this data?* ("Not yet" apart from closed surveys nobody answered), and how
    directly the data contributed (1–5, with a one-line explanation; a
    distribution for organizations and the platform, where only surveys with a
    global purpose count).
-   **Platform view extras**: Countries (fewer than 10 map features shows as
    *Fewer than 10*), how many map areas are kept private (S2 cells below 10 map
    features from 2 organizations), and how many organizations opted out.
-   **Diagnostics and empty states**: A discreet *N linked fields couldn't be
    counted · Why?* line explains skipped links and values that aren't numbers
    or use an unsupported unit; empty states say how to get numbers (collect
    data, pick a purpose, link fields to standard fields).
-   **PDF summary**: **Download PDF summary** saves the same numbers as a PDF
    (`client/pdf/ImpactPdfReports.kt`, using the PDF writer of
    [PDF Reports](../../../shared/app/README.md#pdf-reports)), including the organization filter.
-   **Sample data**: The Kenya coffee parcels link *Commodity*,
    *Deforestation-free*, and the owner to global concepts and *Shade trees*
    (suggests *Ecosystem restoration*) and *Cherry delivered (kg)* to Kenya
    Forest Service concepts (one parcel's *Not weighed* shows a diagnostic). The
    Mekong survey maps the same five plots, so the platform view counts them
    once, and the Open Foris Community is opted out.

The sample map shapes are illustrative, so features pass their recorded
area and a representative point instead of their drawn outline.

## Web Dashboard Page (`#dashboard`)

Open a survey from the **Surveys** page, or deep-link to `#dashboard`.

-   **Top toolbar**: Displays Ground branding and the active survey title as a
    **survey switcher** dropdown (other surveys grouped by organization, plus
    **All surveys…**), with its organization and location below it, a
    **Manage survey** button to enter the **Survey editor**, and user profile
    controls. **Map** and **Impact** tabs below it switch the main area between
    the live map and the survey's numbers (see [Impact Views](#impact-views)).
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
-   **Data collection on web** (`ui/datacollection/WebDataCollectionCard.kt`): The mobile entry
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
    drawing on the main map (`ui/state/WebMapDrawingHost.kt`): **Draw on map**
    in the question's card routes map clicks to it
    (`DataCollectionViewModel.webMapDrawing`, the `CompactMapDrawingHost`), a floating chip at the top of the map shows
    the progress (`2 of at least 3`) with **Undo**, **Done**, and **Cancel
    drawing**, and the in-progress vertices, line, or polygon are drawn above
    the map features in the draft style (`SurveyMapContent.draftGeometry`).
    One click places a point; lines and polygons collect vertices until
    **Done**. While drawing, clicks on features add vertices instead of
    selecting them. Every geometry answer the open form already holds stays on
    the map in a settled "in-flow" style (`DataCollectionUiState.webFormGeometries`:
    same amber hue as the draft but solid outlines, light fill, white vertex
    discs, and a chip with the question title); clicking one of them, or its
    chip, scrolls the panel to that question and highlights its card for a
    moment (`DataCollectionActions.focusWebFormQuestion` → the runner's
    `focusRequest`). Each geometry card also has a **Zoom to fit** button (any
    kind: point, line, or polygon) that centres that question's geometry in the
    visible part of the map, clear of the form panel and the table
    (`DataCollectionUiState.webMapFramingRequest`).
-   **Media capture on web** (`ui/common/MediaCapture.kt`,
    `src/commonMain/resources/media-capture-bridge.js`): Photo, video, and
    audio questions use the shared `MediaCaptureWidget` with
    `PlatformMediaCaptureHandler`, which honours the organizer's per-question
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
-   **Export** (download icon in the table header): **Download CSV** for the
    selected tab, **Download GeoJSON** for Map layers, and the export
    profiles enabled by the survey's purposes (their Purpose Packs'
    `exportProfileIds`), such as **EUDR GeoJSON** for surveys with the EUDR
    due diligence purpose (`ExportSurveyDataUseCase`,
    `ui/dashboard/DashboardExportMenu.kt`). A profile's output fields are found
    through concept links: a property that inherited the field's concept, or
    the property a linked question saves to; geometry concepts use the feature
    geometry. EUDR GeoJSON writes WGS84 coordinates with 6 decimal places,
    boundaries as polygons and point plots as points, and select answers as
    their standard codes. When fields aren't linked, a dialog lists them (and
    point plots over 4 ha) before **Export anyway**; an unlinked area falls
    back to each plot's mapped area. The seeded Kenya coffee survey links its
    parcels' owner to **Producer name**, so its EUDR export shows the warning
    for **Commodity** and **Country**. Every download records an `EXPORT`
    impact event (profile ID when used, feature count, and area, counting
    plots that share a GeoID once; see
    `docs/technical/model/data/04-impact-events.md`).

Code lives in `ui/dashboard/WebDashboardPage.kt`.

## Survey Editor Page (`#survey-editor`)

Open the survey editor from the web dashboard via the **Edit survey** button in
the left-hand survey header (and return to the dashboard with the back arrow in
the survey editor header), or deep-link directly to `#survey-editor`
(`#form-editor` also works).
The left-hand navigation lists:

-   **Survey details**: Title, description, survey ID, organization,
    purposes, and languages, plus summary cards that link to each section.
    -   **Purposes** edits the survey's Purpose Packs and programs with the
        same cards and chips as survey creation. Changing them doesn't add or
        remove Forms; their templates' standard fields rank first in the Form
        editor's label suggestions (`FormLibraryContext.purposeConceptIds`).
    -   **Organization** moves the survey into one of the organizations the
        signed-in user belongs to, or keeps it personal. The owner stays the
        owner; Managers of the organization inherit Survey organizer access.
        Clearing the organization while general access is "Anyone in the
        organization" falls back to Restricted and says so.
-   **Sharing**: Invite people by email with a role (Viewer, Data collector,
    Survey organizer), change or remove roles, and set general access
    (Restricted, Anyone in the organization, Anyone with the link, Public) and
    what data collectors can see. These mirror `acl.proto`.
    -   **Anyone in the organization** is only available for surveys that
        belong to an organization; choosing it without one is a publish-blocking
        issue flagged in the navigation.
    -   For organization surveys, a read-only **Organization managers** card
        lists the people who inherit Survey organizer access from the
        organization.
    -   Who may open the editor is resolved by `domain/model/editor/SurveyAccess.kt`
        (owner → accepted access list → organization Manager → organization
        member under the organization policy → anyone under link/public). The
        dashboard only shows **Manage survey** to people who can manage the
        active survey.
    -   Each new invite gets an invite link (`https://ground.example.org/join/…`)
        with **Copy link** and **New link** actions. Until it's accepted, the
        person is listed by email with an **Invited** badge.
    -   **Open as invitee** previews what the invitee sees after opening the
        link and signing in: they confirm their full name and profile photo.
        Accepting caches both on their entry, and the list then shows them
        by name and photo. The sample data draws placeholder portraits
        (`avatar:<n>`) instead of loading real photo URLs.
-   **Forms**, **Map layers**, **Data tables**: Headings list the survey's
    items. Use **+** to add a new one, and drag items to reorder them within
    their heading (screen readers get **Move up** / **Move down** actions).
    -   **+** next to **Forms** offers **Empty form**, **From template…**, and
        **Import from XML**. **From template…** lists the templates of the
        survey's resolved library (organization first, then global ones not
        hidden) with their source and linked-field counts; choosing one copies
        it into the survey (`SurveyFormTemplates.addTemplate`: new Form ID,
        unique title, its own linked dataset), so later template edits don't
        change it.
    -   **Save as template** in the Form editor toolbar (also in its **⋮**
        menu, where it's disabled with the reason for non-Managers and
        personal surveys) saves a copy of the Form, with its standard field
        links and choice codes, to the survey organization's library as a
        Draft template `org.<organizationId>.<name>` (unique; `_2`, `_3`, …
        when taken). "All users" Managers can save to the global library
        instead, which drops links to organization fields. The template then
        appears in the organization's **Templates** tab and in **From
        template…**.
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

**Close, archive, and reopen** (`SurveyLifecycleUseCase`, mirroring
`SurveyDef.state`): people who can manage the survey find **Close survey** and
**Archive survey** in the top bar's **⋮** menu (Archive only, once closed), each
behind a short confirmation that mentions unpublished changes are discarded.
Closed and archived surveys are read-only: a banner says "This survey is
closed. Reopen it to make changes." with **Reopen** (also in the top bar),
edits are ignored, **Publish changes** stays off, and the web toolbar's
**Collect data** button and the mobile `+` FAB are hidden. Leaving the
published state stamps the survey's closed time, records a `SURVEY_CLOSED`
impact event covering its map features (counting plots that share a GeoID
once), and then shows the inline **What happened with this data?** card
(`ui/common/SurveyOutcomeCard.kt`): multi-select chips for the outcomes (*Not
yet* can't be combined with the others), an optional "Compared with your
previous method, this took…" choice (less, about the same, or more time and
cost), **Save**, and **Skip**. The survey is closed before the card appears,
so closing never waits on it. Saving stores a `SurveyOutcome` (answer time and
the signed-in user) on the survey. Archiving a closed survey keeps its closed
time and doesn't ask again; reopening clears the closed time but keeps the
answer. Survey details ends with a read-only **Activity** list of the survey's
impact events (what happened, date, map features, area, and export profile).

The editor edits the same survey the rest of the app runs on. The Forms, Map
layers, and map features it shows are **derived** from the survey's runtime data
(`domain/model/editor/SurveyEditorDerivation.kt`): each `FormPreviewItem` is
imported back from its published XForms XML (`FormImport.kt`), each
`MapLayerItem` becomes a Map layer dataset whose rows are the layer's map
features (row key = map feature ID, with `id` and `label` properties plus the
feature's other properties). Surveys never edited before therefore open with
all their Forms and layers. Saving **projects** the draft back
(`SurveyEditorProjection.kt`): Forms, Map layer styles, and map features are
written to the store in one transaction, keeping each map feature's Submissions
and removing only the rows the editor deleted since the draft was opened. The
saved draft is stored per survey for what the runtime does not hold (sharing
people, survey area, languages, Data tables); when it is reopened its Map layer
rows are refreshed from the current map features so field edits show up too.

The editor's models are pure Kotlin in `domain/model/editor/`
(`SurveyEditorDraft.kt`, `SurveyEditorModels.kt`, `SampleDesignModels.kt`,
`SurveyAreaGeometry.kt`, `FormPublishing.kt`, `FormImport.kt`,
`SurveyEditorDerivation.kt`, `SurveyEditorProjection.kt`, `SurveyAccess.kt`,
`SampleGeneration.kt`, `MapLayerImporter.kt`, `FormDatasetLinks.kt`), so
repositories and the local data store depend only on the domain. The page is
driven by `SurveyEditorViewModel` (`ui/viewmodel/`), which exposes a `StateFlow`
of `SurveyEditorUiState` (`ui/state/`) and implements `SurveyEditorActions`;
the Compose screens in `ui/surveyeditor/` (`SurveyEditorPage.kt`,
`EntityDatasetEditor.kt`, `SamplingDesignPanel.kt`, `SurveyAreaEditor.kt`,
`InteractiveLayerMap.kt`, `LayerEditorGeometry.kt`) take `(uiState, actions)`
and hold only transient UI state such as dialogs and the selected table row.
`SurveyEditorPage(state: WebAppState, …)` is the thin shell that collects
the view model and renders the shared `WebAppHeader`. Drag-to-reorder for the
navigation and the flow canvas shares `ui/formeditor/DragReorder.kt`.

The view model observes the active survey's draft through
`SurveyEditorRepository` and shows it live until the first edit, after which the
session holds the working copy (`isDirty`), so edits made elsewhere do not
overwrite in-progress work. **Publish changes** saves the working copy with
`saveDraft(surveyId, draft, previous = opened)` and emits
`SurveyEditorEvent.Published`; **Discard** / close throws it away and emits
`SurveyEditorEvent.Closed`; the app shell returns to the dashboard on both.
Switching the active survey resets the session. Sample plot generation runs in
the view model's scope through `GenerateSamplePlotsUseCase` (progress and
per-layer errors are part of the UI state; map features with Submissions block
regenerating, counted from `SurveyRepository`), invites go through
`InviteCollaboratorUseCase`, and place search for the survey area uses
`PlaceRepository` directly. The map inside the Map layer editor and the survey
area **Draw** tab only need `MapFeatureEditor` (add a feature, replace its
vertices), which `SurveyEditorActions` extends and the Draw tab backs with a
scratch `DrawnPartsEditor`.

The layer editor map and the survey area thumbnail are `GroundMap`s from
`shared/map`, like the survey map. Features are drawn as map style layers;
vertex and midpoint handles, labels, and the in-progress drawing are a draw-only
Compose overlay placed with `MapCameraState.project`. Vertex and feature drags
are claimed through a `MapDragHandler`; all other drags pan the map. Click
hit-testing stays in the editor (`hitTestFeature`) so polygons select by
containment and lines and points by a 12 dp tolerance. Without a Mapbox renderer
(JVM desktop) the map module's `PreviewMap` draws a grid instead of imagery.

### Form Editor

Selecting a Form opens the visual Form editor in `ui/formeditor/`
(`FormEditorPage.kt`, `FormSaveToEditor.kt`, `FormWebPreview.kt`); its models,
validation rules, and XForms generator are pure Kotlin in `domain/model/editor/`
(`FormEditorModels.kt`, `FormValidationRules.kt`, `FormIds.kt`). The screens
take `(uiState: FormEditorUiState, actions: FormEditorActions)`, provided by a
`FormEditorViewModel` per Form that `SurveyEditorViewModel.formEditor(key)`
creates over the survey draft (and caches until the survey changes). Edits to a
Form's questions are written back into the draft, and the view model keeps the
Form's linked Map layer or Data table in step with its questions
(`FormDatasetLinks`); selection, preview target, the running preview
(`FormWizardController`, the one non-domain value in the UI state), and the
XML viewer / Advanced-section toggles are editor session state.
`FormEditorViewModel.standalone(form, datasets)` builds one over a plain Form
for tests:

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
    buttons only list forms available on mobile
    (`DataCollectionUiState.webForms` / `mobileForms`). The choice is applied to
    the running survey's forms by form ID from the saved editor draft, like Map
    layer styles. The **Advanced**
    section at the bottom holds the Form's save-to logic
    (`domain/model/editor/FormSaveToModels.kt`, `FormSaveToEditor.kt`). Advanced sections start
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
-   **Standard fields (concept links)**: Questions can be linked to concepts of
    the survey's dictionary, the resolved library of its organization
    (`FormLibraryContext`, built by `SurveyEditorViewModel` from
    `ResolveLibraryUseCase`; see
    `docs/technical/model/library/01-concepts.md`, "Linking Form Fields").
    When typing in a **Label** pauses (after at least 3 characters), up to 5
    suggestions appear under the field (type-compatible ones first, each with its
    type icon, an organization or vocabulary badge, and description). The list
    never takes focus or links by itself; Esc or clicking elsewhere closes it.
    Choosing one links the question (`ConceptLinking.link`): a question still
    in its default state (`ConceptLinking.isDefaultState`: default name, no
    hint, not required, default choices, no validation or display logic) also
    gets the concept's type, a name from its ID, its code list as choices
    (labels in the survey's default language), a unit hint, and a `≥ 0`
    minimum for numbers. The label is always kept; **Use standard label**
    replaces it. A linked question shows a `<Source> · <Label>` chip (its
    tooltip has the definition, unit, and references) with **Unlink**, and a
    link icon on its canvas card. **Link to standard field…** in the Advanced
    section searches the dictionary explicitly. Managers of the survey's
    organization also get **Add "<label>" to <Org> dictionary**, which saves a
    draft organization concept (`ManageLibraryUseCase`) and links it. Manual
    choices of a linked select question each get a **Standard value**
    dropdown mapping them to code-list values
    (`ChoiceItem.properties["ground_code"]`, a `ground_code` column in the
    choice instance). The
    validator's concept warnings (`ConceptLinkValidator`: type mismatch,
    unmatched choices, one concept on several questions, deprecated or
    unknown concepts) show in amber and never block publishing. Deleting a
    linked question asks for confirmation, naming the impact reports it feeds.
    After **Import Form**, `ground:concept` links to concepts in the
    dictionary stay linked (others are kept as foreign attributes and
    reported), and a "We found N fields that match standard definitions" card
    offers links for unlinked questions, with exact and prefix matches
    pre-checked. On publish, links are stored per survey in
    `SurveyConfig.formConceptLinks` (the client's mirror of
    `SurveyDef.form_concept_links`, keyed by field path `/data/<name>`;
    authoritative) and written into the published XForms as
    `ground:concept="<id>@<version>"` bind attributes
    (`FieldBinding.foreign_attributes`). Properties that linked questions save
    to inherit their concepts (`SaveToRules.inheritConcepts`,
    `EntityProperty.conceptLink`). Code is in
    `domain/model/editor/FormConceptLinks.kt` and
    `ui/formeditor/ConceptLinkingUi.kt`.
