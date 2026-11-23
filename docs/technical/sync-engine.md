# Architectural Specification: Offline-First Reactive App Architecture
## Technology Stack: Kotlin Multiplatform (KMP), gRPC, and Google Cloud Spanner

This document outlines the architectural blueprint for a highly reactive, offline-first cross-platform application (Android, iOS, and Web) using a shared Kotlin Multiplatform core. The backend relies on **Google Cloud Spanner** for globally consistent mutations and **gRPC** for efficient binary communication.

By prioritizing a **Hybrid Topic-Tickle + Pull-Based Model**, this architecture keeps the Cloud Spanner database and gRPC service 100% stateless with respect to device tokens and client sessions while avoiding blind periodic polling on mobile. Mobile clients subscribe to two Firebase Cloud Messaging (FCM) topics per downloaded survey (`survey_<survey_id>_def` for survey/form definition changes and `survey_<survey_id>_entities` for map feature and plot-lock changes). When the backend commits a relevant write, it publishes a lightweight data-only "tickle" to the corresponding topic, waking or prompting the client to run a targeted unary delta pull for that scope. Because OS power management (Android Doze / App Standby, iOS Low Power Mode) can throttle background silent pushes, the client pairs topic tickles with deterministic catch-up sweeps on app foreground, network reconnect, and post-outbox drain.

## Core Architectural Pillars

### Single Source of Truth (SSOT)
The user interface layer (Jetpack Compose / SwiftUI / Compose for Web) **never** communicates directly with the network layer or the remote Google Cloud Spanner database. A local store serves as the absolute SSOT, and the UI observes it continuously via native Kotlin `Flow`.
* **Mobile (Android, iOS)**: a persistent local database (SQLDelight) holding the surveys the user explicitly **downloaded**, which survives restarts and works offline (see [Survey Downloads & Working Copies](#survey-downloads--working-copies)). The mobile app in the browser keeps the same database in the browser's storage, so its offline edits and last synced data survive reloads too ([Local Data Store](client/local-data-store.md#sync-state-on-the-device)).
* **Web**: an in-memory store holding only what's on screen (see [Web Client](#web-client)). The browser keeps no offline copy.

### Delta Synchronization With Sync Tokens
Data synchronization uses short unary `List*` calls in **delta mode**. The client sends the opaque `sync_token` it received last, and the server returns every change committed after it, in commit order and paged. The last page carries a `next_sync_token` for the next call. The same request serves every trigger: [FCM topic tickles](#fcm-topic-tickles-mobile), reconnecting, coming to the foreground, background sweeps, and [foreground polling](#foreground-polling). No connection is held open between calls, in the foreground or the background.

### Stateless Cloud Backend
The server application layer maintains no state tracking regarding client online/offline status, device push registration tokens, session tokens, or delta logs. Mobile clients manage their own FCM topic subscriptions directly with FCM based on their locally downloaded survey IDs, and the server publishes fire-and-forget topic messages after committing mutations. When fetching data, the client provides its own position as an opaque [sync token](#sync-tokens), from which Cloud Spanner computes the changes on demand with index-optimized commit-time queries. Because every request is short and self-contained, any server instance can answer any request, which suits request-scaled hosting such as Cloud Run (see [Why Topic Tickles + Unary Pulls Instead of Open Streams](#why-topic-tickles--unary-pulls-instead-of-open-streams)).

### List RPCs: Query and Delta Modes
Each synced resource has exactly one `List*` RPC: `ListSurveyOverviews`, `ListEntities`, `ListSubmissions`, `ListOrganizations`, `ListLibraries`, `ListImpactEvents`, and `ListAuditRecords`. Whether `sync_token` is set selects the mode:

| Mode | Request | Response | Used for |
| :--- | :--- | :--- | :--- |
| **Query** (`sync_token` unset) | Scope fields (e.g. `survey_id`), optional `filter` / `order_by` / `query`, `page_size`, `page_token` | Matching records. Every page reads the same snapshot, pinned in `page_token`; the last page carries `next_sync_token` for the scope at that snapshot. | Initial and full syncs (a query with no filter), data tables, search |
| **Delta** (`sync_token` set) | The same scope fields the token was issued for, `page_size`, `page_token` | Every record in scope changed after the token, in commit order, always including tombstones (deleted records and records the caller lost access to). The last page carries `next_sync_token`. | Catch-up and polling |

* **A full sync is a query with no filter.** Mobile runs one per scope, stores `next_sync_token`, and polls in delta mode from then on.
* **Data scopes are whole surveys.** `ListEntities` and `ListSubmissions` sync by `survey_id` only. Sub-survey filters (`dataset_id` and `s2_cell_tokens` for entities, `form_id` and `entity_id` for submissions) are query-only, so a downloaded survey always syncs completely. Quotas keep surveys small enough for a device (at most 10,000 predefined entities).
* **Delta mode is scope-only.** `filter`, `order_by`, and `query` are rejected with `INVALID_ARGUMENT`, so a record can't silently leave a filtered result set. A client keeping a filtered table fresh polls the whole scope and applies the changes that affect the rows it holds.
* **At least once.** A record can appear both in a query page and in the delta that follows (for example, when it changed after the snapshot). Applying a record is idempotent, so duplicates are harmless.
* **Cheap when idle.** A poll with nothing new returns no records and a fresh `next_sync_token`.

The remaining query RPCs cover lookups, search, and aggregation the client can't do from data it holds: `GetSurvey`, `BatchGetSurveyOverviews` (the current state of specific surveys, used for downloaded surveys), `ListEntityClusters`, `GetImpactAggregates`, `SearchConcepts`, `SearchOrganizations` (the public directory of organizations the caller may ask to join; `ListOrganizations` lists the caller's own organizations), and `SearchPublicSurveys` (the public directory of surveys).

### Sync Tokens
A sync token is an opaque string. Clients store it and send it back unchanged; they never parse, compare, or construct one, and never reason about server time.

* **Contents (server-defined):** a format version, the scope it was issued for (resource and scope fields), the position in commit order (commit timestamp plus record ID as a tie-breaker), and the server's data epoch. The server can change the encoding, for example to Spanner change-stream offsets, without changing clients.
* **Scope binding:** a token sent with different scope fields is rejected with `INVALID_ARGUMENT`.
* **Invalidation:** when the server can't serve changes from a token, it fails with `FAILED_PRECONDITION`: the data was reset, the caller's own access changed (survey list and organization tokens), or the survey's peer data visibility changed (entity and submission tokens). Deleted records keep permanent tombstones, so tokens never expire just because they're old, and a device that was offline for months catches up with a delta. The client discards the token and runs a full sync for that scope, replacing its bases (records missing from the result are removed). Pending outbox mutations are kept and rebased onto the new bases.
* **Storage:** clients advance a token only after applying a whole page in one local transaction. Mobile persists the tokens of its downloaded surveys' data scopes (`ClientSyncState.sync_tokens`); survey list tokens live in memory while the list is open.
* **Record freshness:** when the same record arrives more than once (from a `List*` page, a `Mutate*` response, or a replay), clients keep the copy with the higher version and ignore copies with an equal or lower one, whatever their `commit_timestamp`. Every write to a synced record strictly increases its version: `revision` for entities and submissions (including deletes and plot lock changes), `version` for organizations and libraries (exactly one per request), and `record_version` for surveys, shared by the saved revision, the working copy, and the ACL and copied to `SurveyOverview`. A survey's `version` and `saved_revision_number` keep identifying the saved revision collectors use, so working copy edits don't change them. `commit_timestamp` stays on records for display only. Full syncs are the exception: their results replace the bases whatever the versions, since a server data reset can lower them.

### Batched Catch-Up
A catch-up touches many scopes: organizations, libraries, survey overviews, impact events, and the entities and submissions of every synced survey. Instead of one `List*` call per scope, clients send them all in one `SyncService.SyncChanges` call:

* **Cursors in, pages out:** the request carries one cursor per scope (the scope, its stored `sync_token`, and a `page_token` while paging a full sync). The response has one `ScopeChanges` per cursor, in order, with exactly the records, `next_page_token`, and `next_sync_token` the scope's `List*` RPC returns. Tokens are interchangeable between `SyncChanges` and the `List*` RPCs.
* **Independent failures:** a scope that fails reports its `google.rpc.Code` (`error_code`) on its own `ScopeChanges` instead of failing the call: `FAILED_PRECONDITION` for a token to discard (the client full-syncs that scope in its next round), `NOT_FOUND` or `PERMISSION_DENIED` for a survey that's gone or no longer shared.
* **Paging:** scopes with another page are sent again, alone, in a follow-up round; caught-up scopes drop out. An idle poll is a single call.
* **Inlined definitions:** with `include_survey_definitions`, each `survey_overviews` page also carries the saved revision and ACL of every listed survey (as `GetSurvey` returns them), so changed surveys don't need follow-up `GetSurvey` calls. Working copies are never inlined: the Survey Editor fetches them with `GetSurvey` (`working_copy`) when `has_working_copy` is set, so draft edits don't flow through polling.
* **New surveys:** on the web, surveys that the overviews just added have no data tokens yet; their entities and submissions are fetched in one more `SyncChanges` call.
* **Server:** the service answers each cursor with its own `List*` handler (and `GetSurvey` for inlined definitions), so behavior and tokens can't drift from the `List*` RPCs. Clients fall back to one `List*` call per scope when an older deployment answers `UNIMPLEMENTED`.

Mobile keeps two calls outside the batch: `BatchGetSurveyOverviews` for its downloaded surveys (they're refreshed by `record_version`, not a sync token) and, while the Download survey screen is open, `ListSurveyOverviews` for the catalog (its token lives in memory).

## End-to-End System Flows

### The Offline Flow (User Disconnected)
Every local edit goes through the same path, online or offline, on mobile and on the web. The edit is never sent directly; it is queued and drained in the background.

1. **Sparse Proto Mutation (`delete` + `upsert`):** The client turns the user's intent into a mutation carrying two sparse instances of the target domain proto (`delete` as a structural deletion mask where any non-default scalar, non-empty list, or leaf keyed item signals removal, and `upsert` for values to set or merge). Applying `delete` first and then recursively merging `upsert` is deterministic and idempotent, so applying the mutation twice gives the same result as applying it once.
2. **Outbox Transaction:** In one local transaction, the client appends the mutation to the outbox and applies it to the local copy. Each mutation gets a `mutation_id` (UUID), the install's `client_id`, and the next `client_seq`.
3. **Coalescing:** A queued (not yet sent) mutation to the same field or keyed collection item as a newer mutation is replaced by the newer one. Removing a collection item drops queued edits beneath it, and deleting a record that was created locally and never sent cancels both.
4. **Reactive UI Update:** The local query emitting the Kotlin `Flow` detects the write transaction immediately, so the UI updates in milliseconds.
5. **Background Drain:** The sync engine drains the outbox when connectivity allows. A failed send, and a failed sync, are retried with capped exponential backoff with jitter (about 2 seconds, doubling to at most 60 seconds), which resets after a success. Coming back online and returning to the foreground (on the web, the tab becoming visible) skip the backoff and sync right away (`SyncCoordinator.retryNow`), so edits made offline are sent within moments of the connection returning, without a reload.

### Rebase on Remote Changes
For every synced record, the local store keeps the **base** (the last copy confirmed by the server) and the **pending** outbox mutations that target it. What the UI shows is the pending mutations applied on top of the base, using the same patch functions the server uses (`shared/core`, `org.openforis.collectone.v2.core.mutation`).

When a delta page or a `Mutate*` response delivers a newer copy of a record, the client, in one transaction:

1. Replaces the base with the server copy (or marks it deleted for a tombstone), unless the stored base already has the same or a higher version (see [Record freshness](#sync-tokens)).
2. Drops pending mutations the server has confirmed (acknowledged by response or reported by `GetAppliedMutations`) and pending patches that no longer change anything.
3. Re-applies the remaining pending mutations on top of the new base.
4. Stores the response's `next_sync_token` once the whole page is applied.

If a record was deleted remotely, its pending patches are rejected and shown in the Uploads view, where the user can discard or retry them.

### The Online Flow (Re-establishing Connection & Catch-Up)
When the device returns online or the application transitions from a suspended state to the foreground, a unified catch-up sequence initiates.

1. **Token Lookup:** The shared synchronization engine reads the stored sync token for each scope (each downloaded survey's entities and submissions). A scope without a token gets a full sync (a query with no filter).
2. **Outbox Drain (Client to Server):** Before pulling updates, the client calls `GetAppliedMutations` and drops outbox mutations the server already applied (for example, when a response was lost). It then sends the remaining mutations in order, batching contiguous mutations for the same RPC target (`MutateData` per survey, `MutateSurvey` per survey and revision, `MutateOrganization` and `MutateLibrary` per organization), with at most one batch in flight per target. The backend executes a write transaction in Cloud Spanner, which assigns a definitive `commit_timestamp` utilizing its atomic TrueTime engine.
3. **Delta Request (Server to Client):** The client calls the scope's `List*` RPC with its sync token, and `BatchGetSurveyOverviews` for its downloaded surveys (see [Survey Downloads & Working Copies](#survey-downloads--working-copies)).
4. **Cloud Spanner Index Evaluation:** The backend decodes the token and runs an index lookup on commit time, for example:
   ```sql
   SELECT * FROM AppDataTables
   WHERE scope = @scope AND (commit_ts, id) > (@tokenCommitTs, @tokenId)
   ORDER BY commit_ts, id LIMIT @pageSize;
   ```
5. **Applying Pages:** For every page, the shared core engine, in one local transaction, upserts each record (or deletes it for a tombstone, `is_deleted = true`), rebases pending mutations, and stores the cursor for the next page. The last page's `next_sync_token` becomes the scope's stored token.
6. **Done:** Each call is a short unary request, so nothing stays open after the last page, which frees mobile radios and saves battery.

### FCM Topic Tickles (Mobile)
Rather than polling on a 60-second timer while idle in the foreground or relying solely on coarse 15-minute OS background sweeps, mobile clients subscribe to **two Firebase Cloud Messaging (FCM) topics per downloaded survey** (bridged to APNs on iOS). This lets the client know both *when* a downloaded survey changed and *which RPC* to run, without storing a single device token in Cloud Spanner:

| Topic | Published When | Payload (`data` map) | Client Action on Receipt |
| :--- | :--- | :--- | :--- |
| `survey_<survey_id>_def` | A survey's saved definition (forms, dataset schemas, map config, lifecycle state), trash/restore/purge status, or ACL/sharing rules change (`MutateSurvey` when `!working_copy` or when saving a new revision or updating ACLs, `AcceptSurveyInvite`, or trash purge). Working-copy editor autosaves do **not** publish to this topic. | `{"survey_id": "<uuid>", "scope": "def", "record_version": "<n>"}` | If local `surveyBase(survey_id).record_version < n`, run `BatchGetSurveyOverviews([survey_id])` and, when a new saved revision is reported, `GetSurvey(survey_id)`. Does **not** call `ListEntities` or `ListSubmissions`. |
| `survey_<survey_id>_entities` | Map features or plot locks change (`MutateData` with entity creations, updates, or deletes, `AcquirePlotLock`, or `ReleasePlotLock`). Submission-only writes do **not** publish to this topic unless they also mutate an entity. | `{"survey_id": "<uuid>", "scope": "entities", "commit_ts": "<epoch_ms>"}` | Run `ListEntities(survey_id, sync_token)` in delta mode only. Does **not** call `BatchGetSurveyOverviews`, `GetSurvey`, or `ListSubmissions`. |

1. **Client-managed topic lifecycle:** Whenever the local downloaded set (`ClientSyncState.downloaded_survey_ids`) changes—on survey download, download removal, or when a survey is trashed or marked unavailable—the client calls `subscribeToTopic` or `unsubscribeFromTopic` for both `survey_<survey_id>_def` and `survey_<survey_id>_entities`.
2. **Data-only silent push with collapse keys:** Messages carry only the lightweight `data` payload above (no `notification` block and no sensitive survey content), with `collapse_key` set to the topic name (`survey_<survey_id>_def` or `survey_<survey_id>_entities`) on Android and `apns-collapse-id` + `content-available: 1` on iOS so burst writes while a device is offline collapse into a single pending wakeup per scope.
3. **Server-side debouncing for entities:** Because batch imports or rapid field edits can produce many entity commits in a short window, the backend coalesces `survey_<survey_id>_entities` publishes over a short trailing window (for example, 2 seconds per survey) while publishing `survey_<survey_id>_def` immediately on save.
4. **Foreground vs. background handling:** If the app is in the foreground when a topic message arrives, `SyncCoordinator.requestScopedSync(surveyId, scope, minRecordVersion)` runs the targeted delta pull immediately through the single-flight sync mutex. If the app is in the background, the push handler schedules a one-off expedited/connected `WorkManager` job (`BGAppRefreshTask` on iOS) for `(survey_id, scope)`.
5. **Hybrid resilience:** Because mobile OSes throttle silent background pushes when battery is low or the app is rarely opened, the client retains full incremental catch-up (`pullIncrementalCatchUp`) on app foreground (`ON_START` / `sceneWillEnterForeground`), network reconnect, and post-outbox drain. Sync tokens guarantee that whether a pull is triggered by an FCM tickle, a foreground transition, or a fallback poll, the exact same idempotent delta logic runs.

### Foreground Polling
While the Web Console is in the foreground and online—or on mobile when the Download survey catalog is open, a collaborative view is active, or push subscriptions are unavailable (for example, on devices without Google Play Services)—the client repeats the [Online Flow](#the-online-flow-re-establishing-connection--catch-up) on a timer for each active sync scope:

1. **Adaptive interval & push suppression:** Poll about every 10 seconds while a collaborative view is visible (Survey Editor, live submissions table, CEO plot queue) or while the mobile Download survey catalog is open. On the Web Console (or on mobile when FCM topic subscriptions are unavailable), poll about every 60 seconds otherwise. On mobile with active FCM topic subscriptions, the 60-second idle foreground poll is suppressed because `survey_<survey_id>_def` and `survey_<survey_id>_entities` tickles trigger targeted pulls directly. Always pull after draining the outbox, so the client's own writes come back with their authoritative `commit_timestamp`. A poll in a collaborative view catches up only the open survey's `ListEntities` / `ListSubmissions` (plus the account-wide organization, library, survey list, and impact event scopes), not the data of every survey; switching surveys catches up the newly opened one right away. A catch-up on foreground, reconnect, or an idle poll is skipped when a full pull (or the initial download) finished less than 5 seconds earlier and the outbox sent nothing.
2. **Cheap when idle:** A poll with nothing new is a single index lookup that returns no records. If servers later provide a suggested interval (for example, to slow clients down under load), clients use the longer of the two.
3. **No overlap:** A scope never has two pulls in flight. If a sync is still running when a timer or FCM tickle fires, the next one coalesces behind the active run.
4. **Stop and resume:** Polling stops when the app goes to the background, loses connectivity, or the scope is no longer on screen. The stored sync token already reflects the last applied page, so the next pull (or foreground catch-up) resumes without gaps.
5. **Backoff:** On errors, polling backs off exponentially (with jitter) up to the idle interval, then resumes the normal schedule after a successful sync.

#### Why Topic Tickles + Unary Pulls Instead of Open Streams
Holding a server stream open per foreground client (a "live tail") was considered and rejected for request-scaled hosting such as Cloud Run:
* **Concurrency:** every open stream permanently occupies one of an instance's concurrent request slots (80 by default, 1,000 at most), so instance count grows with *connected users* rather than with actual traffic.
* **Cost:** with request-based billing, an instance is billed whenever any request is in flight, so idle open streams keep instances billed full time and prevent scaling to zero.
* **Timeouts:** requests are cut off after at most 60 minutes, so open streams need reconnect logic anyway.
* **Fan-out across instances:** a write handled by one instance would have to wake streams held by every other instance, which needs an in-cluster broadcast layer. Publishing a fire-and-forget FCM topic tickle after commit offloads fan-out to FCM while keeping every gRPC data read a short, stateless unary request against Cloud Spanner.

### Survey Downloads & Working Copies
Survey definitions are large (forms, entity dataset schemas, translations), and devices have limited space, so mobile only syncs surveys the user explicitly **downloads**. Sync splits into two independent parts:

* **Survey list:** `ListSurveyOverviews` lists a lightweight `SurveyOverview` for every survey the user can access as the owner, through an ACL entry, or through `ORGANIZATION` sharing (title, state, counts, thumbnail area, access summary, `version`, `has_working_copy`). On mobile and on the web, the list runs a query when its page opens and [polls](#foreground-polling) in delta mode only while the page is visible; its tokens live in memory. Trashed and purged surveys, and surveys the caller lost access to, arrive as tombstones (`state == DELETED`). Surveys open to anyone with the link or the public aren't listed; clients reach them by ID (from the share URL) or through `SearchPublicSurveys`.
* **Download:** Downloading a survey fetches its saved revision with `GetSurvey`, runs full syncs of `ListEntities` and `ListSubmissions` for its `survey_id`, and subscribes the device to the `survey_<survey_id>_def` and `survey_<survey_id>_entities` FCM topics. From then on, the survey's definition and entity scopes are updated immediately when topic tickles arrive and during foreground/reconnect catch-up sweeps, whether or not the survey list is open. Removing a download unsubscribes from both FCM topics and drops the survey's bases and tokens once its outbox has drained (or the user discards the pending edits).
* **Survey-level state of downloaded surveys:** The survey list usually isn't open, so clients check `BatchGetSurveyOverviews` with their downloaded surveys' IDs during catch-up sweeps—and for a single `survey_id` whenever a `survey_<survey_id>_def` FCM tickle arrives with a higher `record_version`. It returns the caller's overview of each survey, or `NOT_FOUND` (purged) or `PERMISSION_DENIED` (lost access):
  * A higher `version` means a new saved revision: the client re-fetches it with `GetSurvey`.
  * `trash_time` set means the survey is in Trash: it disappears from the device's list, its pending uploads keep draining (the server accepts them, so field data survives a restore), and its local data is removed afterwards.
  * `NOT_FOUND` or `PERMISSION_DENIED`: the survey is marked unavailable. Unsent edits stay in Uploads, where the user can retry them (they succeed if access returns) or download a PDF of the record.
  * `ListEntities` and `ListSubmissions` return the same errors, so a client that misses the batch call still finds out.
* **Working copy:** Organizers edit a working copy of the survey; collectors only ever see the latest saved revision. `GetSurvey` returns exactly one revision, selected by `oneof revision` (latest saved by default, a specific `saved_version`, or `working_copy`). Clients only request the working copy when the overview reports `has_working_copy`.

**Current client implementation** (`GrpcSyncDataSource` with `downloadedSurveysOnly`, enabled by the mobile hosts):

* The downloaded set is device-local: it's stored with the sync state (`ClientSyncState.downloaded_survey_ids`) and never sent to the server. A new install starts with the surveys marked as downloaded by default (`SurveyOverview.is_downloaded`).
* Surveys that aren't downloaded are listed from their overviews, without `GetSurvey`, `ListEntities`, or `ListSubmissions`. Downloading one triggers a sync that fetches it; removing a download drops its definition, data, and tokens once the survey has no outbox entries.
* The survey list (the Download survey screen) is caught up only while it's open, polling at the collaborative interval; closing it discards its token. Surveys that return `NOT_FOUND` or `PERMISSION_DENIED` are marked **Unavailable** and stop syncing. Libraries aren't synced on mobile.
* Surveys the list doesn't include can be downloaded from the Download survey screen: searching shows matching public surveys (`SearchPublicSurveys`), and pasting a share link or survey ID looks the survey up (`BatchGetSurveyOverviews`). Downloading one adds it to the downloaded set, and `BatchGetSurveyOverviews` keeps it current from then on.
* Not yet implemented: opening share links directly in the app (Android App Links, iOS Universal Links); a trashed survey with pending uploads stays listed until they drain; organizations are still synced on mobile, since the app shows organization names and imagery from them.

Organizations and libraries aren't kept on mobile: forms are built in the online web console, and a download stores the few organization settings it needs offline (such as imagery sources). See [`backend/spanner-schema.md`](backend/spanner-schema.md) for how the server stores and serves all of this.

### Web Client
The Web Console is online-only and keeps no offline copy, so it uses the same RPCs with a different lifecycle:

1. **In-memory store:** The web app holds an in-memory store scoped to what's on screen: the survey list while it's visible, and the active survey's definition, entities, and submissions while a survey is open. The store, including its outbox, is discarded when the user reloads the page. While the outbox has entries, the page asks for confirmation before unloading.
2. **Survey list:** Same as on mobile: when the list becomes visible, the client runs `ListSurveyOverviews` in query mode to load every overview, then [polls](#foreground-polling) with the returned `next_sync_token` until the user navigates away.
3. **Active survey:** On opening a survey, the client calls `GetSurvey` (with `working_copy = true` in the Survey Editor when the overview reports `has_working_copy`), loads `ListEntities` / `ListSubmissions` for that `survey_id`, and then polls both with their sync tokens.
4. **Large surveys:** When a survey is too large to hold in memory, data tables and map overviews use the query RPCs (`ListSubmissions` / `ListEntities` with `filter`, `order_by`, and `page_token`; `ListEntityClusters` for overview zoom levels). To keep the visible page fresh, the client polls the scope in delta mode with the query's `next_sync_token` and applies changes to the rows it holds (or re-runs the query when a change affects sorting or paging).
5. **Writes:** Edits use the same outbox as mobile: they're applied locally at once, sent with `Mutate*` in the background, retried on transient failures, and rebased when remote changes arrive. Permanent failures are reported in the Uploads view.
6. **Tab lifecycle:** When the tab becomes hidden (`visibilitychange`), the client stops polling. When it becomes visible again, it polls immediately with its in-memory sync tokens, catching up on anything it missed.

Browsers reach the gRPC service through gRPC-Web. Every call the sync engine makes is unary (catch-ups use [`SyncChanges`](#batched-catch-up)), so no streaming support is required.

### Mutation Semantics
* **Applied at most once:** The server tracks the highest applied `client_seq` per `(client_id, scope)` (returned by `GetAppliedMutations`). Because clients send each scope's mutations in `client_seq` order with one batch in flight, any mutation at or below that watermark is a replay: it's skipped and returns the record's current state, so a stale retry can't overwrite newer writes. Web clients keep their `client_id` in `localStorage` so reloads reuse it.
* **Last writer wins per field:** Sparse field and keyed-collection mutations always apply in server commit order. A data mutation based on an older `base_revision` reports `AUTO_MERGED`.
* **Guarded replacements:** Whole-record replacements (`upsert` with its root record ID populated and `delete` unset) fail with `FAILED_PRECONDITION` when their `base_version` / `base_revision` is set and stale. The client then pulls, rebases, and retries.
* **Error handling:** `UNAVAILABLE` and `DEADLINE_EXCEEDED` retry with backoff. `INVALID_ARGUMENT`, `PERMISSION_DENIED`, and `NOT_FOUND` reject the mutation, remove it from the local copy, and show it as failed in the Uploads view, where the user can retry it or download a PDF of the record.
* **Survey editor autosave:** Editor mutations are queued as the organizer types, coalesced per field or keyed collection item, and sent to the working copy after about 1.5 seconds without new edits.

## Kotlin Multiplatform Code Topology

To enforce strict code reuse and maintain a single location for synchronization logic, the implementation splits the *execution engine* from the *scheduling primitives*.

### Shared Business Core (`commonMain`)
All gRPC stubs, schema parsing, delta tracking, and transactional local operations are written once in the shared core. The same function serves catch-up, background sweeps, and foreground polling.

```kotlin
class SyncEngine(
    private val localDb: AppDatabase,
    private val dataService: DataServiceClient
) {
    suspend fun executeCatchUpSync(surveyId: String): SyncResult {
        return try {
            var syncToken = localDb.syncDao().getSyncToken(surveyId) // null: full sync
            var pageToken = ""
            do {
                val page = dataService.ListEntities(
                    ListEntitiesRequest(survey_id = surveyId, sync_token = syncToken.orEmpty(), page_token = pageToken)
                )
                localDb.withTransaction {
                    page.entities.forEach { localDb.dataDao().upsert(it.toLocalEntity()) }
                    if (page.next_sync_token.isNotEmpty()) {
                        localDb.syncDao().setSyncToken(surveyId, page.next_sync_token)
                    }
                }
                pageToken = page.next_page_token
            } while (pageToken.isNotEmpty())
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
    }

    /** Polls while [isForeground] is true; cancelled with the calling scope. */
    suspend fun pollWhileForeground(isForeground: StateFlow<Boolean>, interval: () -> Duration) {
        isForeground.collectLatest { foreground ->
            while (foreground) {
                executeCatchUpSync()
                delay(interval())
            }
        }
    }
}
```

### Platform Schedulers (`androidMain`, `iosMain`, and `jsMain` / `wasmJsMain`)
Because Android, iOS, and browsers enforce conflicting lifecycle paradigms, scheduling hooks are thin native wrappers passing invocation control into the shared engine.

#### Android Setup (`androidMain`)
* **Push Wakeups & Topic Subscriptions:** Observes `downloaded_survey_ids` and subscribes/unsubscribes `survey_<survey_id>_def` and `survey_<survey_id>_entities` via `FirebaseMessaging`. When a data-only FCM message arrives in `CollectOneFirebaseMessagingService`:
  * If the app is in the foreground, it calls `SyncCoordinator.requestScopedSync(surveyId, scope, minRecordVersion)` directly.
  * If the app is in the background, it enqueues a unique `ScopedSyncWorker` (`ExistingWorkPolicy.REPLACE`) constrained to `NetworkType.CONNECTED` to run the targeted delta pull (`pullSurveyDefinitionCatchUp` or `pullSurveyEntitiesCatchUp`).
* **Background Sweep Fallback:** Utilizes **Jetpack WorkManager** for deterministic periodic sweeps when network connectivity is verified by the OS. If a network drops mid-sync, the worker returns `Result.retry()` to automatically invoke an exponential backoff routine.
* **Foreground:** Observes `ProcessLifecycleOwner`. On `ON_START` (and on connectivity regained while started), it runs a full catch-up and starts `pollWhileForeground` (which suppresses the 60s idle poll when FCM topic subscriptions are active) in a scope that is cancelled on `ON_STOP`.

#### iOS Setup (`iosMain` / Swift Linkage)
* **Push Wakeups & BGTaskScheduler:** Bridges FCM topic subscriptions (`survey_<survey_id>_def` and `survey_<survey_id>_entities`) to APNs silent notifications (`content-available: 1`) alongside **BGTaskScheduler** for opportunistic background processing.
* Because iOS strictly throttles silent pushes and background execution slots to roughly 30-second windows depending on on-device power heuristics, the architecture pairs silent push wakeups with the **Foreground Lifecycle Hook**.
* The moment the iOS app transitions to `sceneWillEnterForeground`, it executes the shared catch-up sync asynchronously, wiping out any stale gaps immediately before the user can trigger missing interface states.

#### Web Setup (`jsMain` / `wasmJsMain`)
* There is no background sync or FCM topic subscription. Loading and polling are started and stopped by the views that need them (survey list, active survey), as described in [Web Client](#web-client).
* A `visibilitychange` listener stops polling when the tab is hidden and polls immediately with the in-memory sync tokens when it's visible again.

## Key Architectural Trade-Offs

| Advantage | Structural Limitation / Cost |
| :--- | :--- |
| **Stateless Topic Push (Zero Token DB):** Subscribing to `survey_<id>_def` and `survey_<id>_entities` in FCM gives mobile clients near-instant targeted updates without storing device registration tokens or session state in Cloud Spanner. | **OS Silent-Push Throttling:** Android Doze/App Standby and iOS APNs rate-limit silent background pushes (`content-available: 1`), requiring foreground/reconnect catch-up sweeps as a deterministic safety net. |
| **Scoped Wakeups Avoid Wasted RPCs:** Separating `_def` and `_entities` topics means an entity edit or plot lock only triggers `ListEntities`, and a form/survey publish only triggers `BatchGetSurveyOverviews` + `GetSurvey`. | **Coarse Topic Membership Visibility:** Any authenticated client that knows a `survey_id` could subscribe to its FCM topic, so topic messages must remain strictly data-free "tickles" (`survey_id`, `scope`, version/timestamp) with all actual data gated by authenticated gRPC calls. |
| **Natural Delta Debouncing:** Server-side debouncing on `_entities` topic publishes, FCM `collapse_key` coalescing, and sync-token delta queries ensure that 10,000 modifications result in one paged catch-up upon returning. | **Burst Backend Traffic:** If a significant event causes many subscribers of a large survey to wake up simultaneously on a topic publish, the backend serves concurrent delta queries (mitigated by short index-backed reads and client jitter). |
| **Perfect Chronological Ordering:** Leveraging Spanner's TrueTime engine ensures updates are processed in absolute linear sequence, eliminating race conditions common to pushed payload channels. | **Chunking / Pagination Overhead Requirement:** If a client goes offline for weeks, data payloads must be systematically broken down into discrete index chunks to stay within background runtime execution boundaries. |
| **Collaboration Without Open Connections:** Topic tickles and foreground polling reuse the exact same catch-up call and sync token, so ordering, resume, and tombstones work identically across offline catch-up and live updates, and servers scale with request volume rather than connected users. | **Web Holds No Offline Copy:** The Web Console's outbox is in memory, so edits queued during an outage are lost if the page is reloaded before they're sent, and very large surveys must use paged queries plus polling rather than a full in-memory copy. |
