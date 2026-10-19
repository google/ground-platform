# Architectural Specification: Offline-First Reactive App Architecture
## Technology Stack: Kotlin Multiplatform (KMP), gRPC, and Google Cloud Spanner

This document outlines the architectural blueprint for a highly reactive, offline-first cross-platform application (Android, iOS, and Web) using a shared Kotlin Multiplatform core. The backend relies on **Google Cloud Spanner** for globally consistent mutations and **gRPC** for efficient binary communication.

By prioritizing a **Best-Effort Pull-Based Model**, this architecture completely eliminates the need for Firebase Cloud Messaging (FCM) or Apple Push Notification service (APNs), keeping the infrastructure stateless, secure, and resilient to intermittent network conditions. While an app is in the foreground and online, it repeats the same short pull on a timer to pick up collaborators' changes; no connection is ever held open.

## Core Architectural Pillars

### Single Source of Truth (SSOT)
The user interface layer (Jetpack Compose / SwiftUI / Compose for Web) **never** communicates directly with the network layer or the remote Google Cloud Spanner database. A local store serves as the absolute SSOT, and the UI observes it continuously via native Kotlin `Flow`.
* **Mobile (Android, iOS)**: a persistent local database (Room / SQLDelight) holding the surveys the user explicitly **downloaded**, which survives restarts and works offline (see [Survey Downloads & Working Copies](#survey-downloads--working-copies)).
* **Web**: an in-memory store holding only what's on screen (see [Web Client](#web-client)). The browser keeps no offline copy.

### Delta Synchronization With Sync Tokens
Data synchronization uses short unary `List*` calls in **delta mode**. The client sends the opaque `sync_token` it received last, and the server returns every change committed after it, in commit order and paged. The last page carries a `next_sync_token` for the next call. The same request serves every trigger: reconnecting, coming to the foreground, background sweeps, and [foreground polling](#foreground-polling). No connection is held open between calls, in the foreground or the background.

### Stateless Cloud Backend
The server application layer maintains no state tracking regarding client online/offline status, session tokens, or delta logs. The client provides its own position as an opaque [sync token](#sync-tokens), from which Cloud Spanner computes the changes on demand with index-optimized commit-time queries. Because every request is short and self-contained, any server instance can answer any request, which suits request-scaled hosting such as Cloud Run (see [Why Polling Instead of Open Streams](#why-polling-instead-of-open-streams)).

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

## End-to-End System Flows

### The Offline Flow (User Disconnected)
Every local edit goes through the same path, online or offline, on mobile and on the web. The edit is never sent directly; it is queued and drained in the background.

1. **Fine-Grained Mutation:** The client turns the user's intent into one or more field patches (`SurveyFieldPatch`, `EntityFieldPatch`, `SubmissionFieldPatch`, `OrganizationFieldPatch`, `LibraryFieldPatch`). Each patch sets or clears one key path, or upserts or removes one keyed collection item, so applying it twice gives the same result as applying it once.
2. **Outbox Transaction:** In one local transaction, the client appends the mutation to the outbox and applies it to the local copy. Each mutation gets a `mutation_id` (UUID), the install's `client_id`, and the next `client_seq`.
3. **Coalescing:** A queued (not yet sent) patch to the same key path as a newer patch is replaced by the newer one. Removing a collection item drops queued patches beneath it, and deleting a record that was created locally and never sent cancels both.
4. **Reactive UI Update:** The local query emitting the Kotlin `Flow` detects the write transaction immediately, so the UI updates in milliseconds.
5. **Background Drain:** The sync engine drains the outbox when connectivity allows, with exponential backoff on failures.

### Rebase on Remote Changes
For every synced record, the local store keeps the **base** (the last copy confirmed by the server) and the **pending** outbox mutations that target it. What the UI shows is the pending mutations applied on top of the base, using the same patch functions the server uses (`shared/core`, `org.groundplatform.v2.core.mutation`).

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

### Foreground Polling
While the app is in the foreground and online, the client repeats the [Online Flow](#the-online-flow-re-establishing-connection--catch-up) on a timer for each active sync scope (its downloaded surveys, plus the survey list while it's open), so changes by other collaborators (submissions arriving, edits in the Survey Editor, plot locks) appear within seconds without holding a connection open.

1. **Adaptive interval:** Poll about every 10 seconds while a collaborative view is visible (Survey Editor, live submissions table, CEO plot queue), and about every 60 seconds otherwise. Poll immediately after draining the outbox, so the client's own writes come back with their authoritative `commit_timestamp`.
2. **Cheap when idle:** A poll with nothing new is a single index lookup that returns no records. If servers later provide a suggested interval (for example, to slow clients down under load), clients use the longer of the two.
3. **No overlap:** A scope never has two polls in flight. If a poll is still running when the timer fires, the next one waits for it to finish.
4. **Stop and resume:** Polling stops when the app goes to the background, loses connectivity, or the scope is no longer on screen. The stored sync token already reflects the last applied page, so the next poll (or foreground catch-up) resumes without gaps.
5. **Backoff:** On errors, polling backs off exponentially (with jitter) up to the idle interval, then resumes the normal schedule after a successful poll.

Plot locks (5-minute leases) and survey edits (guarded by `base_version` optimistic concurrency) tolerate this delay. If sub-second updates are ever needed, a lightweight change signal can tell clients *when* to poll without changing the RPCs (see the trade-offs below).

#### Why Polling Instead of Open Streams
Holding a server stream open per foreground client (a "live tail") was considered and rejected for request-scaled hosting such as Cloud Run:
* **Concurrency:** every open stream permanently occupies one of an instance's concurrent request slots (80 by default, 1,000 at most), so instance count grows with *connected users* rather than with actual traffic.
* **Cost:** with request-based billing, an instance is billed whenever any request is in flight, so idle open streams keep instances billed full time and prevent scaling to zero.
* **Timeouts:** requests are cut off after at most 60 minutes, so open streams need reconnect logic anyway.
* **Fan-out across instances:** a write handled by one instance would have to wake streams held by every other instance, which needs a broadcast layer (Spanner change streams consumed by each instance, or Pub/Sub). Polling reads the database directly, so no broadcast is needed.

### Survey Downloads & Working Copies
Survey definitions are large (forms, entity dataset schemas, translations), and devices have limited space, so mobile only syncs surveys the user explicitly **downloads**. Sync splits into two independent parts:

* **Survey list:** `ListSurveyOverviews` lists a lightweight `SurveyOverview` for every survey the user can access as the owner, through an ACL entry, or through `ORGANIZATION` sharing (title, state, counts, thumbnail area, access summary, `version`, `has_working_copy`). On mobile and on the web, the list runs a query when its page opens and [polls](#foreground-polling) in delta mode only while the page is visible; its tokens live in memory. Trashed and purged surveys, and surveys the caller lost access to, arrive as tombstones (`state == DELETED`). Surveys open to anyone with the link or the public aren't listed; clients reach them by ID (from the share URL) or through `SearchPublicSurveys`.
* **Download:** Downloading a survey fetches its saved revision with `GetSurvey` and runs full syncs of `ListEntities` and `ListSubmissions` for its `survey_id`. From then on, the survey's data scopes sync in background sweeps and foreground polls, whether or not the survey list is open. Removing a download drops the survey's bases and tokens once its outbox has drained (or the user discards the pending edits).
* **Survey-level state of downloaded surveys:** The survey list usually isn't open, so clients poll `BatchGetSurveyOverviews` with their downloaded surveys' IDs alongside the data scopes. It returns the caller's overview of each survey, or `NOT_FOUND` (purged) or `PERMISSION_DENIED` (lost access):
  * A higher `version` means a new saved revision: the client re-fetches it with `GetSurvey`.
  * `trash_time` set means the survey is in Trash: it disappears from the device's list, its pending uploads keep draining (the server accepts them, so field data survives a restore), and its local data is removed afterwards.
  * `NOT_FOUND` or `PERMISSION_DENIED`: the survey is marked unavailable. Unsent edits stay in Uploads, where the user can retry them (they succeed if access returns) or download a PDF of the record.
  * `ListEntities` and `ListSubmissions` return the same errors, so a client that misses the batch call still finds out.
* **Working copy:** Organizers edit a working copy of the survey; collectors only ever see the latest saved revision. `GetSurvey` returns exactly one revision, selected by `oneof revision` (latest saved by default, a specific `saved_version`, or `working_copy`). Clients only request the working copy when the overview reports `has_working_copy`.

**Current client implementation** (`GrpcSyncDataSource` with `downloadedSurveysOnly`, enabled by the mobile hosts):

* The downloaded set is device-local: it's stored with the sync state (`ClientSyncState.downloaded_survey_ids`) and never sent to the server. A new install starts with the surveys marked as downloaded by default (`SurveyOverview.is_downloaded`).
* Surveys that aren't downloaded are listed from their overviews, without `GetSurvey`, `ListEntities`, or `ListSubmissions`. Downloading one triggers a sync that fetches it; removing a download drops its definition, data, and tokens once the survey has no outbox entries.
* Not yet implemented: the survey list is caught up on every sync rather than only while it's visible; surveys that return `NOT_FOUND` or `PERMISSION_DENIED` stop syncing but aren't marked unavailable in the UI; a trashed survey with pending uploads stays listed until they drain; organizations and libraries are still synced on mobile.

Organizations and libraries aren't kept on mobile: forms are built in the online web console, and a download stores the few organization settings it needs offline (such as imagery sources). See [`backend/spanner-schema.md`](backend/spanner-schema.md) for how the server stores and serves all of this.

### Web Client
The Web Console is online-only and keeps no offline copy, so it uses the same RPCs with a different lifecycle:

1. **In-memory store:** The web app holds an in-memory store scoped to what's on screen: the survey list while it's visible, and the active survey's definition, entities, and submissions while a survey is open. The store, including its outbox, is discarded when the user reloads the page. While the outbox has entries, the page asks for confirmation before unloading.
2. **Survey list:** Same as on mobile: when the list becomes visible, the client runs `ListSurveyOverviews` in query mode to load every overview, then [polls](#foreground-polling) with the returned `next_sync_token` until the user navigates away.
3. **Active survey:** On opening a survey, the client calls `GetSurvey` (with `working_copy = true` in the Survey Editor when the overview reports `has_working_copy`), loads `ListEntities` / `ListSubmissions` for that `survey_id`, and then polls both with their sync tokens.
4. **Large surveys:** When a survey is too large to hold in memory, data tables and map overviews use the query RPCs (`ListSubmissions` / `ListEntities` with `filter`, `order_by`, and `page_token`; `ListEntityClusters` for overview zoom levels). To keep the visible page fresh, the client polls the scope in delta mode with the query's `next_sync_token` and applies changes to the rows it holds (or re-runs the query when a change affects sorting or paging).
5. **Writes:** Edits use the same outbox as mobile: they're applied locally at once, sent with `Mutate*` in the background, retried on transient failures, and rebased when remote changes arrive. Permanent failures are reported in the Uploads view.
6. **Tab lifecycle:** When the tab becomes hidden (`visibilitychange`), the client stops polling. When it becomes visible again, it polls immediately with its in-memory sync tokens, catching up on anything it missed.

Browsers reach the gRPC service through gRPC-Web. Every call the sync engine makes is unary, so no streaming support is required.

### Mutation Semantics
* **Applied at most once:** The server tracks the highest applied `client_seq` per `(client_id, scope)` (returned by `GetAppliedMutations`). Because clients send each scope's mutations in `client_seq` order with one batch in flight, any mutation at or below that watermark is a replay: it's skipped and returns the record's current state, so a stale retry can't overwrite newer writes. Web clients keep their `client_id` in `localStorage` so reloads reuse it.
* **Last writer wins per field:** Field patches always apply in server commit order. A data patch based on an older `base_revision` reports `AUTO_MERGED`.
* **Guarded replacements:** Whole-record replacements (`upsert_*`) fail with `FAILED_PRECONDITION` when their `base_version` / `base_revision` is set and stale. The client then pulls, rebases, and retries.
* **Error handling:** `UNAVAILABLE` and `DEADLINE_EXCEEDED` retry with backoff. `INVALID_ARGUMENT`, `PERMISSION_DENIED`, and `NOT_FOUND` reject the mutation, remove it from the local copy, and show it as failed in the Uploads view, where the user can retry it or download a PDF of the record.
* **Survey editor autosave:** Editor patches are queued as the organizer types, coalesced per key, and sent to the working copy after about 1.5 seconds without new edits.

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
* **Background:** Utilizes **Jetpack WorkManager** to execute deterministic periodic sweeps (minimum 15-minute intervals) when network connectivity is verified by the OS. If a network drops mid-sync, the worker returns `Result.retry()` to automatically invoke an exponential backoff routine.
* **Foreground:** Observes `ProcessLifecycleOwner`. On `ON_START` (and on connectivity regained while started), it runs a catch-up and starts `pollWhileForeground` in a scope that is cancelled on `ON_STOP`.

#### iOS Setup (`iosMain` / Swift Linkage)
Utilizes **BGTaskScheduler** for opportunistic background processing.
* Because iOS strictly throttles background execution slots to roughly 30-second windows and delivers them pseudo-randomly (3-4 times a day depending on on-device machine learning models), the architecture shifts weight heavily onto the **Foreground Lifecycle Hook**.
* The moment the iOS app transitions to `sceneWillEnterForeground`, it executes the shared `executeCatchUpSync()` asynchronously, wiping out stale gaps immediately before the user can trigger missing interface states, then polls until `sceneDidEnterBackground` stops it.

#### Web Setup (`jsMain` / `wasmJsMain`)
* There is no background sync. Loading and polling are started and stopped by the views that need them (survey list, active survey), as described in [Web Client](#web-client).
* A `visibilitychange` listener stops polling when the tab is hidden and polls immediately with the in-memory sync tokens when it's visible again.

## Key Architectural Trade-Offs

| Advantage | Structural Limitation / Cost |
| :--- | :--- |
| **No Push Infrastructure Overhead:** Completely cuts out Firebase, APNs, push certs, and registration token management databases. | **Delayed Background Processing on iOS:** Updates while the app is closed depend on iOS device heuristics rather than structural timing intervals. |
| **Natural Delta Debouncing:** If 10,000 modifications hit a Spanner partition while the client is away, the client runs one paged catch-up upon returning. | **Burst Backend Traffic:** If a significant event causes millions of users to launch the application concurrently, the backend will face massive concurrent catch-up query demands. |
| **Perfect Chronological Ordering:** Leveraging Spanner's TrueTime engine ensures updates are processed in absolute linear sequence, eliminating race conditions common to pushed messaging channels. | **Chunking / Pagination Overhead Requirement:** If a client goes offline for weeks, data payloads must be systematically broken down into discrete index chunks to stay within background runtime execution boundaries. |
| **Collaboration Without Open Connections:** Foreground polling reuses the catch-up call and sync token, so ordering, resume, and tombstones work identically for offline catch-up and collaborative updates, and servers scale with request volume rather than connected users. | **Seconds of Staleness:** Collaborators see each other's changes after up to one polling interval (about 10 seconds in collaborative views), and idle polls add request volume. If that proves too slow, a lightweight change signal (for example, a per-survey "latest commit" document clients can watch) can trigger polls early without changing the RPCs. |
| **One List RPC per Resource:** Query and delta modes share one call, so a query's last page hands off to polling without a second RPC, and the opaque sync token lets the server change how it tracks positions without changing clients. | **Web Holds No Offline Copy:** The Web Console's outbox is in memory, so edits queued during an outage are lost if the page is reloaded before they're sent, and very large surveys must use paged queries plus polling rather than a full in-memory copy. |
