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

# Local Data Store Design

The local data store is the single source of truth of both Open Foris Collect One clients. The UI reads
everything from it and writes everything through it. This note covers how it
works, how the sync engine's state is kept on the device, and the database
schema.

## Working Copy

-   `LocalStore` is the boundary. Reads are `Flow`s per collection; writes run
    in `suspend` transactions that are atomic and can be nested (a nested
    transaction joins the outer one).
-   `InMemoryLocalStore` keeps an immutable snapshot in a `StateFlow`. It is
    the working copy on every platform; on mobile (and in the mobile app in the
    browser) `PersistentLocalStore` keeps the sync state from it on the device
    ([Sync State on the Device](#sync-state-on-the-device)).
-   Map features and their submissions are stored separately and joined on
    read, as a relational backend would store them.
-   `SampleDataSeeder` fills the store when its `seedVersion` differs from
    `SampleDataSeeder.SEED_VERSION`, and again on **Reset**. It is the only code
    that reads the hardcoded sample datasets.
-   The collections the UI observes hold domain models (`SurveyPreviewItem`,
    `GeospatialEntityItem`, `SurveyEditorDraft`, `Organization`, …). The sync
    engine also keeps the last server copy of each synced record as a proto
    (`SurveyDef`, `EntityRecord`, `SubmissionRecord`) and projects it into
    these collections ([Sync Engine Handoff](#sync-engine-handoff)).
-   Organizations are a top-level collection. A survey points at its
    organization by ID (`SurveyPreviewItem.organizationId`); deleting an
    organization clears that pointer on its surveys in the same transaction.

## Sync State on the Device

Mobile clients, including the mobile app in the browser (`mobileWebApp`), keep
the sync engine's state between launches in a local SQLite database: the
outbox, the server bases, the install's `client_id` and next `client_seq`, the
opaque sync token of each `List*` scope, and the surveys downloaded to the
device. The domain collections the UI observes stay in the in-memory working
copy and are not stored; on launch they are rebuilt from the bases and the
outbox.

-   **`PersistentLocalStore`** (`data/datasource/local/persistence/`) wraps the
    in-memory store. Once writing through, every transaction that changes the
    sync state also writes those changes to the database before it commits.
    If the database write fails, the transaction fails and the working copy is
    unchanged, so an outbox append and the base update it depends on are stored
    together or not at all. Only the rows a transaction touched are written.
-   **`SyncStatePersistence`** (`data/sync/`) restores the stored state on
    launch (it loads the bases and outbox, then rebuilds the domain
    collections by applying the pending mutations on top of the bases) and then
    starts writing through. Until then nothing is written, so the app can
    start from a clean working copy. Clearing the store while writing through
    (signing out, switching users) also clears the database.
-   **Account**: the database records whose state it holds: the signed-in
    account's email with real accounts, or blank for sample and development
    accounts. `UserDataSyncController` restores only the signed-in account's
    state; starting for another account replaces it, so one account never sees
    another's offline edits, even if the app closed before signing out.
-   **`SqlDelightSyncStateStore`** reads and writes the database
    (`SyncStateStore` is its interface). The Koin `dataModule` binds
    `LocalStore` to a `PersistentLocalStore` over it when the host's
    `platformModule(localDatabase = ...)` provides a `SqlDriverFactory` (the
    Android app, the mobile app in the browser, and iOS); hosts without one
    (the web console, tests, the workbench's in-memory runs) keep everything
    in memory with `InMemoryLocalStore`. The restore and write-through start
    once per graph, when its `AppDataHolder` starts the initial sync; sessions
    rebuilt over the same graph (for example after an Android configuration
    change) don't restore again.
-   **Closing**: the stores share one `LocalDatabaseConnection`, which the
    host's `platformModule(localDatabase = ...)` provides. When the Koin graph
    stops (for example when the Android app switches to another user data
    service and calls `stopKoin`), its `onClose` hooks cancel the app scope,
    which stops the sync engine and background writes, and close the database.
-   **Migration from the sync state file**: older versions saved the whole
    state as one `ClientSyncState` proto file
    (`shared/protos/userdata/client_sync_state.proto`) after every change. On
    the first launch with the database, `SyncStatePersistence.restore` moves the
    file's contents into the (empty) database in one transaction and deletes
    the file; nothing writes it anymore. An unreadable file is deleted. The
    file never held real accounts' state for restoring, so it moves in as the
    sample and development accounts' state.

Map areas saved for offline use are stored in the same database
([Offline Map Areas](#offline-map-areas)). The other device-only collections
(places, preferences, and the `Uploads` history) aren't
stored yet.

## Signed-In Account

Mobile clients keep the account signed in on the device in the same database
(`signed_in_session`), so relaunching (offline too) goes straight back into
the app instead of asking to sign in and accept the Terms of Service again.

-   **What's stored**: the account's email, display name, organization, and
    photo URL, and the email of the account that accepted the Terms of Service
    since signing in (`terms_accepted_email`). No ID tokens, refresh tokens, or other credentials: the auth client
    keeps those as it always has (Firebase in the browser's storage), and
    `FakeAuthClient` has none.
-   **Restoring**: `AuthRepositoryImpl.restoreSession` runs once per graph,
    before the initial sync. The auth client's own session wins. When the
    client has none to restore, because it keeps no session
    (`FakeAuthClient`) or because Firebase couldn't be loaded (offline at
    startup), the stored account is restored into the client
    (`AuthClient.restoreSession`). The mobile app (`MobileAppSession`) then
    starts on the survey list, or on the terms if they weren't accepted, or on
    sign-in when nobody is signed in.
-   **Back online**: the next call that needs an ID token loads Firebase again.
    Its session replaces the restored one: the same account stays signed in
    (its data is kept: `UserDataSyncController` only starts over when the
    account or simulated role changes), and the app signs out only when
    Firebase has no session for the device any more.
-   **Terms per account**: the acceptance applies only to the account that
    accepted (`terms_accepted_email`). When another account is signed in (a
    simulated role switch, or the auth service restoring a different account
    than the stored one), it must accept the terms again.
-   **Signing out** (or the auth service ending the session) deletes the row,
    so the next launch asks to sign in and accept the terms again. The web
    console keeps the row in the browser's `localStorage` instead
    (`webConsoleSignedInSessionStore`), only to keep the terms accepted across
    page reloads: it never signs anyone in by itself
    (`SignedInSessionStore.restoresAccount` is false), so Firebase alone decides
    who is signed in. Other hosts without a local database (tests) keep the
    account in memory only (`InMemorySignedInSessionStore`).

## Active Survey

Mobile clients keep the survey the user has open in the same database
(`active_survey`), so a relaunch, a reload of the mobile app in the browser at
`#survey`, or Android restoring the app after process death, reopens that
survey instead of another one.

-   **What's stored**: the ID of the survey the user last opened
    (`SurveyRepository.setActiveSurveyId`, through `ActiveSurveyStore`).
    Defaults the app picks itself, such as the survey the initial download
    makes active, aren't stored.
-   **Restoring**: once the initial sync has put the local data in place
    (restored from the device or downloaded), `UserDataSyncController` calls
    `SurveyRepository.restoreActiveSurvey`, which makes the stored survey
    active again if it is still stored locally, and
    `SurveyRepository.observeOpenSurveyId` then reports it. When the data was
    restored from the device, this happens before
    `UserDataSyncController.hasLoadedLocalData` turns true, which is before
    anything is downloaded.
-   **Launch rules**: past onboarding, the mobile app chooses where to start
    from the data on the device alone, as soon as `hasLoadedLocalData` is
    true, and never waits for the network (`MobileLaunchViewModel`,
    `launchRoute`): the Main Survey when the survey the user had open is still
    on the device; otherwise the surveys on the device when any is downloaded;
    otherwise Download survey, with an offline banner while the device has no
    connection. A route from before the launch (the URL fragment on a reload,
    the back stack after process death) comes back only if it doesn't need
    another survey (`MobileLaunch.mayRestore`): the Main Survey, its pages,
    and its form need a survey open, and otherwise the app starts at its start
    route.
-   **Forgetting**: removing the survey's download, deleting it, signing out,
    or the auth service ending the session deletes the row. Hosts without a
    local database (the web console, tests, the workbench) keep it in memory
    only (`InMemoryActiveSurveyStore`).

## Offline Map Areas

Mobile clients keep the map areas the user saved for offline use in the same
database (`offline_map_area` and `offline_map_area_layer`), so the list of saved
areas, and what each layer's imagery takes, survive relaunches. In the mobile
app in the browser the downloads are simulated, but the saved areas are stored
the same way, so they also survive a reload.

-   **What's stored**: each area's ID, name (a place name, or the position the
    UI names it after when no place name was found), bounds, and creation
    time; and per imagery layer (Satellite, Terrain) its status (`SAVING`,
    `READY`, `PAUSED`, `FAILED`) and size. Download progress and the reason a
    download stopped are live state that `OfflineMapRepositoryImpl` keeps in
    memory; the tiles themselves are kept by the map SDK
    (`OfflineRegionClient`), one tile region per layer.
-   **Restoring**: on first use, layers left `SAVING` (the app stopped
    mid-download) become `PAUSED`, and the user resumes them from the list.
    With a native region client, `READY` layers whose region is gone become
    `PAUSED`, and regions no layer refers to are removed.
-   **Kept on sign-out**: saved areas belong to the device, not the account.
    Signing out, switching accounts, and clearing the sync state
    (`SqlDelightSyncStateStore` deletes only the sync tables it names) keep
    them, because the imagery they describe stays on the device. Hosts
    without a local database (the web console, tests, the workbench's
    in-memory runs) keep them in memory only (`InMemoryOfflineMapAreaStore`).

## Persistent Backend

The local database uses [SQLDelight](https://sqldelight.github.io/sqldelight/),
which generates type-safe Kotlin from the SQL schema in
`shared/app/data/src/commonMain/sqldelight/` for every platform. Queries are
generated as suspend functions (`generateAsync`), because the browser's driver
is asynchronous; the same code runs unchanged on the synchronous drivers.

| Platform | Driver (`SqlDriverFactory`)                                   | Storage                                    |
| -------- | ------------------------------------------------------------- | ------------------------------------------ |
| Android  | `AndroidSqlDriverFactory` (`AndroidSqliteDriver`)             | The app's private databases directory      |
| JVM      | `JdbcSqlDriverFactory` (`JdbcSqliteDriver`)                   | In memory (tests) or a file (desktop)      |
| Web      | `BrowserSqlDriverFactory` (`WebWorkerDriver`)                 | OPFS, else IndexedDB (see below)           |
| iOS      | `NativeSqlDriverFactory` (`NativeSqliteDriver`), not built in CI | The app's sandbox                        |

### Schema

Records the app treats as opaque are stored as encoded protos (`BLOB`); only
the keys used to find, replace, or delete them are columns.

| Table               | Key                           | Columns                                                   |
| ------------------- | ----------------------------- | --------------------------------------------------------- |
| `outbox_mutation`   | `mutation_id`                 | `client_seq` (indexed, send order), `record` (`PendingMutationRecord`) |
| `survey_base`       | `survey_id`                   | `record` (`SurveyBaseRecord`: saved revision, working copy, ACL) |
| `entity_base`       | `survey_id`, `entity_id`      | `record` (`EntityRecord`)                                 |
| `submission_base`   | `survey_id`, `submission_id`  | `record` (`SubmissionRecord`)                             |
| `organization_base` | `organization_id`             | `record` (`Organization`)                                 |
| `library_base`      | `organization_id`             | `record` (`OrganizationLibraryRecord`)                    |
| `sync_state`        | single row (`id = 0`)         | `client_id`, `next_client_seq`, `sync_tokens` (`SyncTokensRecord`) |
| `downloaded_survey` | `survey_id`                   |                                                           |
| `stored_account`    | single row (`id = 0`)         | `account` (whose state is stored; blank for sample accounts) |
| `signed_in_session` | single row (`id = 0`)         | `email`, `display_name`, `organization`, `photo_url`, `terms_accepted_email` ([Signed-In Account](#signed-in-account)) |
| `active_survey`     | single row (`id = 0`)         | `survey_id` ([Active Survey](#active-survey))             |
| `offline_map_area`  | `id`                          | `name` (or `name_lat`, `name_lng`), `west`, `south`, `east`, `north`, `created_at` ([Offline Map Areas](#offline-map-areas)) |
| `offline_map_area_layer` | `area_id`, `imagery_type` | `status`, `size_bytes`                                    |

The composite keys of the entity and submission bases also serve deleting all
of a survey's bases when the survey is deleted.

### Web

The mobile app in the browser runs SQLite in a web worker
(`shared/app/data/web/sqlite-worker/`), which implements the message protocol of
SQLDelight's `WebWorkerDriver` with the official SQLite WebAssembly build
(`@sqlite.org/sqlite-wasm`). `mobileWebApp`'s `webpack.config.d/sqliteWorker.js`
lets webpack bundle the worker and the SQLite WebAssembly module as separate
assets. SQLDelight's own worker uses
sql.js, which can only keep a whole database in memory, so persisting it means
rewriting the whole file after every write; SQLite's own build writes only the
pages a transaction changes. The worker picks its storage when it starts:

1.  **Origin Private File System** through SQLite's `opfs-sahpool` VFS, in
    every current browser. It needs no cross-origin isolation headers.
2.  **IndexedDB**, when the browser has no OPFS (older browsers and some
    private browsing modes): an in-memory database whose bytes are saved to
    IndexedDB after every committed write.
3.  **Memory only**, when another tab already holds the database: the second
    tab's changes last until it is closed, so the tabs can't overwrite each
    other's data.

Writes are answered only after they are stored. The web console (`webApp`)
doesn't pass a database and keeps its outbox in memory by design
([Web Client](../sync-engine.md#web-client)).

## Versioning and Migrations

-   **Seed version** (`StoredPreferences.seedVersion`): bump
    `SampleDataSeeder.SEED_VERSION` when the sample data changes. Stores with an
    older version are reseeded, which replaces local edits. This only applies
    while the store holds sample data.
-   **Schema version**: SQLite's `user_version`. A new database gets the current
    schema. To change the schema, edit the `.sq` files and add a migration
    `<version>.sqm` next to them (SQLDelight numbers versions from 1, so the
    first migration is `1.sqm`, from version 1 to 2); the database version is
    the number of migrations plus one. On open, a database with an older version
    runs the migrations from its version, and `user_version` is updated in the
    same transaction. Android and iOS drivers run them while opening; the JVM
    and browser drivers run them in `openLocalDatabase`. A database newer than
    the app fails to open.
-   **Keep the outbox**: migrations must keep `outbox_mutation` and the bases
    intact; never drop and recreate them. Because records are encoded protos,
    adding fields to `PendingMutationRecord` or the base records needs no
    migration (proto3 skips unknown fields and defaults missing ones).
-   **Verify migrations**: the schema of every version is committed in
    `shared/app/data/src/commonMain/sqldelight/databases/` (`<version>.db`), and
    `./gradlew :shared:app:data:verifySqlDelightMigration` checks every migration
    against it. After changing the schema, run `./gradlew
    :shared:app:data:generateCommonMainLocalDatabaseSchema` (on its own, not with the
    verification) to add the new version's file.
-   **Migrations so far**: `1.sqm` (version 1 to 2) adds `signed_in_session`;
    `2.sqm` (version 2 to 3) adds `active_survey`; `3.sqm` (version 3 to 4)
    replaces `signed_in_session.has_accepted_terms` with `terms_accepted_email`,
    tying a stored acceptance to the stored account; `4.sqm` (version 4 to 5)
    adds `offline_map_area` and `offline_map_area_layer`.

## Sync Engine Handoff

The download and sync engine reads and writes the store; the UI never talks to
the network directly.

-   **Download**: Remote surveys, definitions, and records are written into
    the store in transactions. The UI updates through the existing `Flow`s.
-   **Upload**: Local edits are recorded as fine-grained mutations in the
    store's outbox in the same transaction as the edit. The engine drains the
    outbox and removes mutations once the server confirms them.
-   **Bases and rebase**: The store keeps the last server copy (base) of every
    synced record as a proto (`SurveyDef`, `EntityRecord`, `SubmissionRecord`,
    `Organization`, `OrganizationLibraryRecord`). When a newer copy arrives,
    the engine replaces the base and re-applies pending mutations on top of it
    with the shared patch functions in `shared/core`, then projects the result
    into the domain collections the UI observes. See
    [Rebase on Remote Changes](../sync-engine.md#rebase-on-remote-changes).
-   **Sync state**: Sync tokens, the install's `client_id`, and the next
    `client_seq` are stored with the data and updated in the same
    transactions, so they survive restarts on mobile.
-   **Seed data**: Once real downloads exist, the seeder only runs in demo mode.

## Data Flow

All app data lives in a local data store, the single source of truth
(`shared/app/data`'s `data/datasource/local/store/`; paths below are relative to
`org/openforis/collectone/v2/app/` in the common source set of `shared/app`
or, for `data/` and `client/`, `shared/app/data`, unless noted). In the web console it is an in-memory cache that starts
fresh on every page load; on mobile, and in the mobile app in the browser, the
sync state behind it is kept on the device
([Sync State on the Device](#sync-state-on-the-device)).

-   **Reads**: `LocalStore` exposes a `Flow` per collection (surveys, forms, map
    layers, map features with their submissions, standalone submissions,
    geometries, survey configs and editor drafts, mutations, places, and
    preferences). `AppDataHolder.appData` combines them into
    one `StateFlow`; the client shells (`MobileAppState`, `WebAppState`) and the
    feature view models read from the repositories it wires.
-   **Writes**: Repositories (`data/repository/`) write through `suspend`
    functions inside `LocalStore.transaction`, which is atomic and can be
    nested. Use cases that touch several repositories run in one transaction via
    `TransactionRunner`.
-   **Sample data**: `data/seed/SampleDataSeeder` is the only code that reads the
    hardcoded sample datasets (`Sample*Data`, `SurveyEditorSamples`,
    `FormEditorSamples`). It fills the store on first launch and again on
    **Reset**. `SampleDataGuardrailTest` keeps other code from reading them.
-   **Layering**: `LayerDependencyArchitectureTest` checks imports against
    `docs/technical/client/architecture.md`: `domain/` depends on nothing
    outside the domain and shared core, `data/` never imports `di/` or
    presentation packages, only `data/` and `di/` (`AppDataHolder`) may import
    `data.*`, and Views (`ui/` files other than `*ViewModel.kt`) never import
    `data/` or `client/` packages.
-   **Feature view models**: Screens in `ui/<feature>/` of `shared/app`,
    `shared/mobile`, and `shared/web` observe per-feature view models (in
    `shared/app`, next to the feature's views in `ui/<feature>/`; wired by Koin,
    `WebAppState`, and `MobileAppState`), each exposing a
    `StateFlow` of an immutable UI state (in the same package) plus an actions interface
    the screen calls. Screens take `(uiState, actions)` and hold no state of
    their own.
    `OnboardingViewModel` (Sign In → Terms of Service → Download survey) reads
    the account from `AuthRepository` (over the platform's `AuthClient`) and the
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
    list's search query and collapsed datasets; and the `Uploads` drawer
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
    `WebMapDrawingHost` (`ui/datacollection/`) that edits it while drawing, and
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
-   **Backends**: `InMemoryLocalStore` holds the working copy everywhere.
    `PersistentLocalStore` adds the device's database for the sync state; it
    passes `LocalStoreContractTest` over SQLite (`PersistentLocalStoreContractTest`
    also checks that the database matches the working copy after each test).
