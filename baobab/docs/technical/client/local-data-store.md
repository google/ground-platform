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

# Local Data Store Design

The local data store is the single source of truth of both Open Foris Collect One clients. The UI reads
everything from it and writes everything through it. This note covers how it
works today and how a persistent backend and a sync engine plug in later.

## Today

-   `LocalStore` is the boundary. Reads are `Flow`s per collection; writes run
    in `suspend` transactions that are atomic and can be nested (a nested
    transaction joins the outer one).
-   `InMemoryLocalStore` keeps an immutable snapshot in a `StateFlow`. On web
    it is an ephemeral cache, refilled from sample data on every page load.
-   Map features and their submissions are stored separately and joined on
    read, as a relational backend would store them.
-   `SampleDataSeeder` fills the store when its `seedVersion` differs from
    `SampleDataSeeder.SEED_VERSION`, and again on **Reset**. It is the only code
    that reads the hardcoded sample datasets.
-   Stored models are today's domain models (`SurveyPreviewItem`,
    `GeospatialEntityItem`, `SurveyEditorDraft`, `Organization`, …). They move
    to proto-aligned records (`SurveyDef`, `EntityRecord`, `SubmissionRecord`)
    when the sync engine lands.
-   Organizations are a top-level collection. A survey points at its
    organization by ID (`SurveyPreviewItem.organizationId`); deleting an
    organization clears that pointer on its surveys in the same transaction.

## Persistent Backend

Mobile needs a store that survives restarts and works offline. The chosen
technology is Room 3 on the `androidx.sqlite` drivers, because one Kotlin
Multiplatform schema and DAO layer covers every platform:

| Platform | Driver                                           |
| -------- | ------------------------------------------------ |
| Android  | `BundledSQLiteDriver`                            |
| iOS      | `BundledSQLiteDriver`                            |
| JVM      | `BundledSQLiteDriver` (tests and desktop)        |
| Web      | `WebWorkerSQLiteDriver`, in memory (cache only)  |

Steps when it is added:

-   Add a `RoomLocalStore` implementing `LocalStore`. Collections map to
    tables; `transaction` maps to Room's `useWriterConnection` /
    `immediateTransaction`; observers map to Room's `Flow` queries.
-   Add a subclass of `LocalStoreContractTest` for it. Tests that need real
    suspension use a blocking runner in the platform test source set.
-   Before adopting, confirm the Room 3 release, KSP support for `wasmJs`, and
    how the SQLite web worker is bundled.

## Versioning and Migrations

-   **Seed version** (`StoredPreferences.seedVersion`): bump
    `SampleDataSeeder.SEED_VERSION` when the sample data changes. Stores with an
    older version are reseeded, which replaces local edits. This only applies
    while the store holds sample data.
-   **Schema version**: the persistent backend owns its schema version and Room
    migrations. Migrations must keep the outbox (pending mutations) intact.

## Sync Engine Handoff

The download and sync engine reads and writes the store; the UI never talks to
the network directly.

-   **Download**: Remote surveys, definitions, and records are written into
    the store in transactions. The UI updates through the existing `Flow`s.
-   **Upload**: Local edits are recorded as mutations in the store's outbox in
    the same transaction as the edit. The engine drains the outbox and marks
    mutations uploaded.
-   **Conflicts**: Stored records gain version metadata (for example a server
    revision and a last-modified time). The engine compares versions when
    applying remote changes and when uploading, and records the result in the
    store.
-   **Seed data**: Once real downloads exist, the seeder only runs in demo mode.

## Data Flow

All app data lives in a local data store, the single source of truth
(`shared/app`'s `data/datasource/local/store/`; paths below are relative to
`shared/app/src/commonMain/kotlin/org/groundplatform/v2/app/` unless noted). On web it is an in-memory cache that starts
fresh on every page load; on mobile it will become the persistent offline store.

-   **Reads**: `LocalStore` exposes a `Flow` per collection (surveys, forms, map
    layers, map features with their submissions, standalone submissions,
    geometries, survey configs and editor drafts, mutations, places, offline
    tile packages, and preferences). `AppDataHolder.appData` combines them into
    one `StateFlow`; the session states (`AppSession` and its mobile and web
    subclasses) and the feature view models read from
    the repositories it wires.
-   **Writes**: Repositories (`data/repository/`) write through `suspend`
    functions inside `LocalStore.transaction`, which is atomic and can be
    nested. Use cases that touch several repositories run in one transaction via
    `TransactionRunner`.
-   **Sample data**: `data/seed/SampleDataSeeder` is the only code that reads the
    hardcoded sample datasets (`Sample*Data`, `SurveyEditorSamples`,
    `FormEditorSamples`). It fills the store on first launch and again on
    **Reset**. `SampleDataGuardrailTest` keeps other code from reading them.
-   **Layering**: `LayerDependencyGuardrailTest` checks imports against
    `docs/technical/client/architecture.md`: `domain/` depends on nothing
    outside the domain and shared core, `data/` never imports `di/` or
    presentation packages, only `data/` and `di/` (`AppDataHolder`) may import
    `data.*`, and Views (`ui/` outside `ui/viewmodel/`) never import `data/` or
    `client/` packages.
-   **Feature view models**: Screens in `ui/<feature>/` of `shared/app`,
    `shared/mobile`, and `shared/web` observe per-feature view models in
    `ui/viewmodel/` (wired by `di/AppDataHolder`, `WebAppState`, and
    `MobileAppState`), each exposing a
    `StateFlow` of an immutable UI state (`ui/state/`) plus an actions interface
    the screen calls. Screens take `(uiState, actions)` and hold no state of
    their own.
    `OnboardingViewModel` (Sign In → Terms of Service → Download survey) reads
    the account from `AuthRepository` (over `FakeAuthClient`) and the
    survey directory from `SurveyRepository` and `OrganizationRepository`, and
    publishes navigation and notices as `OnboardingEvent`s that the app shell
    applies. `SettingsViewModel` (Settings, Sign In language selector, Offline
    maps) reads and writes preferences through `SettingsRepository`, tile
    packages through `SurveyRepository`, and models the device storage breakdown
    with `EstimateDeviceStorageUseCase`; the light/dark theme is session state
    it owns. `SurveyMapViewModel` (the survey map viewport: camera and GPS
    following, basemap and organization imagery, zoomed-out clustering,
    straight-line wayfinding, and the selected map feature, cluster, or place)
    observes survey content and organizations through `SurveyRepository` and
    `OrganizationRepository`, writes the device location through
    `LocationRepository`, searches places through `PlaceRepository`, and
    models clustering with `ClusterMapFeaturesUseCase` and wayfinding with
    `ComputeWayfindingNavigationUseCase`; outcomes beyond the map (list
    selection, drawer, notices) are `SurveyMapEvent`s the app shell applies.
    `DashboardViewModel` (the web dashboard's panel layout, data tables, layer
    selection, and entity details pane; the web Surveys page; the searchable
    list's filter tabs and collapsed datasets; and the `Uploads` drawer
    sub-screen) observes surveys, map features, and layers through
    `SurveyRepository`, organizations through `OrganizationRepository`, the
    signed-in user through `AuthRepository`, and the mutation log through
    `MutationRepository`; it creates surveys with `CreateSurveyUseCase` and
    uploads with `SyncMutationsUseCase`. Switching the active survey, showing
    the `Uploads` sub-screen, and notices are `DashboardEvent`s the shell
    applies. `OrganizationViewModel` (the web organizations directory and the
    organization page's details, surveys, members, and imagery sources)
    observes organizations through `OrganizationRepository`, surveys through
    `SurveyRepository`, and the signed-in user through `AuthRepository`; it
    creates organizations with `CreateOrganizationUseCase`, invites people
    with `InviteOrganizationMemberUseCase`, and edits imagery sources with
    `ManageImagerySourcesUseCase`, while simple membership changes pass
    straight to the repository. Page switches and turning a removed imagery
    source off on the map are `OrganizationEvent`s the shell applies; the
    resizable left panel's width stays with `DashboardViewModel`, shared with
    the dashboard. `SurveyEditorViewModel` (the Survey editor: the working
    draft, section navigation and panel width, publishing and discarding,
    sharing, Map layer / Data table editing, sample plot generation) observes
    the active survey's draft through `SurveyEditorRepository`, Submission
    counts through `SurveyRepository`, organizations through
    `OrganizationRepository`, and the signed-in user through `AuthRepository`;
    it generates sample plots with `GenerateSamplePlotsUseCase` and handles
    invites with `InviteCollaboratorUseCase`, and hands each Form to a
    `FormEditorViewModel` (`FormEditorUiState` / `FormEditorActions`). Leaving
    the editor after publishing or discarding is a `SurveyEditorEvent` the
    shell applies. `DataCollectionViewModel` (data collection: the selected
    record shown in the entity bottom sheet and the dashboard's details card,
    the open Form and its `entityref` picker step, the web dashboard's
    draw-on-map session, and the GeoID QR code and PDF export dialogs)
    observes survey content through `SurveyRepository`, the collector through
    `AuthRepository`, units through `SettingsRepository`, and the GPS fix
    through `LocationRepository`; it opens Forms with `LaunchFormUseCase`
    (resolves the `FormDef`, adds the `entityref` step, pre-fills
    entity-reference questions) and records them with
    `CompleteFormSubmissionUseCase`. It is the one owner of record selection
    (`selectedEntityId` / `selectedSubmissionId`): `SurveyMapViewModel` keeps
    only the highlighted feature, updated from `DataCollectionEvent`s, and
    selections made on the map flow back through the shell. Three pieces of UI
    infrastructure are allowed in its state and dependencies by exception:
    the shared `FormWizardController` (Compose snapshot state), the
    `WebMapDrawingHost` (`ui/state/`) that edits it while drawing, and
    `PdfExportClient` (`client/pdf/`), the platform file delivery.
    `WorkbenchViewModel` (`mobileWebApp/workbench`, the workbench around the
    mobile app in the browser: simulated device form factor and orientation, the XForms `<h:html>` editor and example survey
    switcher, and debug/simulation tools such as cycling sync status and
    resetting to the sample data) observes surveys and survey content through
    `SurveyRepository` and reseeds via `SampleDataRepository`; cross-feature
    outcomes are published as `WorkbenchEvent`s that the shell applies.
-   **Survey switching**: Every survey's data is in the store, so switching
    surveys keeps edits. Use **Reset** to go back to the sample data.
-   **Survey editor**: `SurveyEditorRepositoryImpl` serves the stored draft
    (`SurveyEditorDraft`) when there is one, refreshed from the survey's current
    map features, and otherwise derives one from the survey's Forms, Map layers,
    and map features. Saving stores the draft and projects it onto the survey
    in the same transaction: Forms (and their XForms in
    `SurveyConfig.formXmlById`), Map layer styles, map features (Submissions
    kept), and the title and description shown in the survey list. The seed
    only stores drafts for the two sample surveys with extras that cannot be
    derived (`SampleSurveyEditorData`: sharing people, survey area,
    languages, Data tables).
-   **Backends**: `InMemoryLocalStore` is the only backend today. A persistent
    backend (Room on `androidx.sqlite`) must pass `LocalStoreContractTest`.
