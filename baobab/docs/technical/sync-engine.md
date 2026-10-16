# Architectural Specification: Offline-First Reactive App Architecture
## Technology Stack: Kotlin Multiplatform (KMP), gRPC, and Google Cloud Spanner

This document outlines the architectural blueprint for a highly reactive, offline-first cross-platform application (Android, iOS, and Web) using a shared Kotlin Multiplatform core. The backend relies on **Google Cloud Spanner** for globally consistent mutations and **gRPC** for efficient binary communication.

By prioritizing a **Best-Effort Pull-Based Model**, this architecture completely eliminates the need for Firebase Cloud Messaging (FCM) or Apple Push Notification service (APNs), keeping the infrastructure stateless, secure, and resilient to intermittent network conditions. While an app is in the foreground and online, it repeats the same short pull on a timer to pick up collaborators' changes; no connection is ever held open.

## Core Architectural Pillars

### Single Source of Truth (SSOT)
The user interface layer (Jetpack Compose / SwiftUI / Compose for Web) **never** communicates directly with the network layer or the remote Google Cloud Spanner database. A local store serves as the absolute SSOT, and the UI observes it continuously via native Kotlin `Flow`.
* **Mobile (Android, iOS)**: a persistent local database (Room / SQLDelight) that survives restarts and works offline.
* **Web**: an in-memory store holding only what's on screen (see [Web Client](#web-client)). The browser keeps no offline copy.

### Bounded Delta Synchronization
Data synchronization relies on short-lived, **bounded gRPC streams**. The client sends its last known state (`since_timestamp`), the server streams every change committed after it in commit order, and the stream ends once the client is caught up. The same request serves every trigger: reconnecting, coming to the foreground, background sweeps, and [foreground polling](#foreground-polling). Persistent streaming connections are never held, in the foreground or the background.

### Stateless Cloud Backend
The server application layer maintains no state tracking regarding client online/offline status, session tokens, or delta logs. The client provides its own temporal coordinate (`last_synced_spanner_timestamp`), allowing Cloud Spanner to calculate and stream mutations on demand via index-optimized time queries. Because every request is short and self-contained, any server instance can answer any request, which suits request-scaled hosting such as Cloud Run (see [Why Polling Instead of Open Streams](#why-polling-instead-of-open-streams)).

### Sync RPCs vs. Query RPCs
Every API call falls into one of two categories:

| Kind | Purpose | Examples | Who serves the UI |
| :--- | :--- | :--- | :--- |
| **Sync** | "Send me everything in this scope that changed after T." One RPC per synced resource. | `StreamSurveyOverviews`, `StreamEntities`, `StreamSubmissions`, `StreamOrganizations`, `StreamLibraries`, `StreamImpactEvents`, `StreamAuditRecords` | The local store, reactively |
| **Query** | Lookup, filtering, sorting, paging, or aggregation the client can't do from data it holds. | `GetSurvey`, `ListEntities`, `ListSubmissions`, `ListEntityClusters`, `GetImpactAggregates`, `ListOrganizations` (public directory), `SearchConcepts` | The response directly, or the local store after the client caches it |

There are no unary `List*` duplicates of sync streams: catch-up always uses the stream. Query responses that return records include a `server_watermark`; a client that wants to keep those results fresh polls the matching sync stream starting from `since_timestamp = server_watermark`.

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

When a delta stream or a `Mutate*` response delivers a newer copy of a record, the client, in one transaction:

1. Replaces the base with the server copy (or marks it deleted for a tombstone).
2. Drops pending mutations the server has confirmed (acknowledged by response or reported by `GetAppliedMutations`) and pending patches that no longer change anything.
3. Re-applies the remaining pending mutations on top of the new base.
4. Advances the stream's persisted watermark.

If a record was deleted remotely, its pending patches are rejected and shown in the Uploads view, where the user can discard or retry them.

### The Online Flow (Re-establishing Connection & Catch-Up)
When the device returns online or the application transitions from a suspended state to the foreground, a unified catch-up sequence initiates.

1. **Watermark Extraction:** The shared synchronization engine queries the local database's sync tracking table to extract the high-water mark token for each sync scope: `last_synced_spanner_timestamp`.
2. **Outbox Drain (Client to Server):** Before pulling updates, the client calls `GetAppliedMutations` and drops outbox mutations the server already applied (for example, when a response was lost). It then sends the remaining mutations in order, batching contiguous mutations for the same RPC target (`MutateData` per survey, `MutateSurvey` per survey and revision, `MutateOrganization` and `MutateLibrary` per organization), with at most one batch in flight per target. The backend executes a write transaction in Cloud Spanner, which assigns a definitive `commit_timestamp` utilizing its atomic TrueTime engine.
3. **Delta Request (Server to Client):** The client initiates a delta gRPC stream request, sending its `last_synced_spanner_timestamp` to the server.
4. **Cloud Spanner Index Evaluation:** The backend service executes a highly optimized index lookup against Spanner tables:
   ```sql
   SELECT * FROM AppDataTables WHERE spanner_commit_timestamp > @lastClientSyncTime ORDER BY spanner_commit_timestamp ASC;
   ```
5. **Draining and Advancing:** The server streams the rows back down the gRPC channel. For every row emitted, the shared core engine:
   * Upserts the row into the local database table (or deletes it when the row is a tombstone, `is_deleted = true`).
   * Updates the `last_synced_spanner_timestamp` to match the record's authoritative server timestamp.
6. **Graceful Teardown:** Once the delta is exhausted, the server fires an `onCompleted()` signal, and the gRPC connection safely tears down, freeing mobile radio bands and saving battery power.

### Foreground Polling
While the app is in the foreground and online, the client repeats the [Online Flow](#the-online-flow-re-establishing-connection--catch-up) on a timer for each active sync scope, so changes by other collaborators (submissions arriving, edits in the Survey Editor, plot locks) appear within seconds without holding a connection open.

1. **Adaptive interval:** Poll about every 10 seconds while a collaborative view is visible (Survey Editor, live submissions table, CEO plot queue), and about every 60 seconds otherwise. Poll immediately after draining the outbox, so the client's own writes come back with their authoritative `commit_timestamp`.
2. **Cheap when idle:** A poll with nothing new is a single index lookup that returns an empty stream. If servers later provide a suggested interval (for example, to slow clients down under load), clients use the longer of the two.
3. **No overlap:** A scope never has two polls in flight. If a poll is still running when the timer fires, the next one waits for it to finish.
4. **Stop and resume:** Polling stops when the app goes to the background, loses connectivity, or the scope is no longer on screen. The watermark already reflects the last applied record, so the next poll (or foreground catch-up) resumes without gaps.
5. **Backoff:** On errors, polling backs off exponentially (with jitter) up to the idle interval, then resumes the normal schedule after a successful poll.

Plot locks (5-minute leases) and survey edits (guarded by `base_version` optimistic concurrency) tolerate this delay. If sub-second updates are ever needed, a lightweight change signal can tell clients *when* to poll without changing the RPCs (see the trade-offs below).

#### Why Polling Instead of Open Streams
Holding a server stream open per foreground client (a "live tail") was considered and rejected for request-scaled hosting such as Cloud Run:
* **Concurrency:** every open stream permanently occupies one of an instance's concurrent request slots (80 by default, 1,000 at most), so instance count grows with *connected users* rather than with actual traffic.
* **Cost:** with request-based billing, an instance is billed whenever any request is in flight, so idle open streams keep instances billed full time and prevent scaling to zero.
* **Timeouts:** requests are cut off after at most 60 minutes, so open streams need reconnect logic anyway.
* **Fan-out across instances:** a write handled by one instance would have to wake streams held by every other instance, which needs a broadcast layer (Spanner change streams consumed by each instance, or Pub/Sub). Polling reads the database directly, so no broadcast is needed.

### Survey Downloads & Working Copies
Survey definitions are large (forms, entity dataset schemas, translations), so they're synced in two tiers:
* **Catalog:** `StreamSurveyOverviews` syncs a lightweight `SurveyOverview` for every survey the user can access (title, state, counts, thumbnail area, access summary, `version`, `has_working_copy`). Revoked access and deleted surveys arrive as tombstones.
* **Full definition:** `GetSurvey` fetches the complete `SurveyDef` for a survey the user opens or downloads. When a downloaded survey's overview arrives with a new `version`, the client re-fetches it with `GetSurvey`. Entities and submissions for downloaded surveys sync with `StreamEntities` / `StreamSubmissions`, scoped by `survey_id`.
* **Working copy:** Organizers edit a working copy of the survey; collectors only ever see the latest saved revision. `GetSurvey` returns exactly one revision, selected by `oneof revision` (latest saved by default, a specific `saved_version`, or `working_copy`). Clients only request the working copy when the overview reports `has_working_copy`.

### Web Client
The Web Console is online-only and keeps no offline copy, so it uses the same RPCs with a different lifecycle:

1. **In-memory store:** The web app holds an in-memory store scoped to what's on screen: the survey list while it's visible, and the active survey's definition, entities, and submissions while a survey is open. The store, including its outbox, is discarded when the user reloads the page. While the outbox has entries, the page asks for confirmation before unloading.
2. **Survey list:** When the list becomes visible, the client runs `StreamSurveyOverviews` with no `since_timestamp` to load every overview, then [polls](#foreground-polling) from the resulting watermark until the user navigates away.
3. **Active survey:** On opening a survey, the client calls `GetSurvey` (with `working_copy = true` in the Survey Editor when the overview reports `has_working_copy`), loads `StreamEntities` / `StreamSubmissions` for that `survey_id`, and then polls both from their watermarks.
4. **Large surveys:** When a survey is too large to hold in memory, data tables and map overviews use the query RPCs (`ListSubmissions` / `ListEntities` with `filter`, `order_by`, and `page_token`; `ListEntityClusters` for overview zoom levels). To keep the visible page fresh, the client polls the matching stream starting from the query's `server_watermark` and applies changes to the rows it holds (or re-runs the query when a change affects sorting or paging).
5. **Writes:** Edits use the same outbox as mobile: they're applied locally at once, sent with `Mutate*` in the background, retried on transient failures, and rebased when remote changes arrive. Permanent failures are reported in the Uploads view.
6. **Tab lifecycle:** When the tab becomes hidden (`visibilitychange`), the client stops polling. When it becomes visible again, it polls immediately from its in-memory watermarks, catching up on anything it missed.

Browsers reach the gRPC service through gRPC-Web, which supports the unary and server-streaming calls used here; client-streaming and bidirectional streaming aren't required.

### Mutation Semantics
* **Idempotent per mutation:** The server applies each `mutation_id` at most once. Replays are skipped and return the record's current state, so a stale retry can't overwrite newer writes. It also tracks the highest applied `client_seq` per `(client_id, scope)`, returned by `GetAppliedMutations`.
* **Last writer wins per field:** Field patches always apply in server commit order. A data patch based on an older `base_revision` reports `AUTO_MERGED`.
* **Guarded replacements:** Whole-record replacements (`upsert_*`) fail with `FAILED_PRECONDITION` when their `base_version` / `base_revision` is set and stale. The client then pulls, rebases, and retries.
* **Error handling:** `UNAVAILABLE` and `DEADLINE_EXCEEDED` retry with backoff. `INVALID_ARGUMENT`, `PERMISSION_DENIED`, and `NOT_FOUND` reject the mutation, remove it from the local copy, and show it as failed in the Uploads view.
* **Survey editor autosave:** Editor patches are queued as the organizer types, coalesced per key, and sent to the working copy after about 1.5 seconds without new edits.

## Kotlin Multiplatform Code Topology

To enforce strict code reuse and maintain a single location for synchronization logic, the implementation splits the *execution engine* from the *scheduling primitives*.

### Shared Business Core (`commonMain`)
All gRPC stubs, schema parsing, delta tracking, and transactional local operations are written once in the shared core. The same function serves catch-up, background sweeps, and foreground polling.

```kotlin
class SpannerSyncEngine(
    private val localDb: AppDatabase,
    private val spannerStub: SpannerSyncCoroutineStub
) {
    suspend fun executeCatchUpSync(): SyncResult {
        return try {
            val lastSync = localDb.syncDao().getLastSyncedTimestamp() ?: 0L
            val request = SyncRequest.newBuilder().setLastSyncTimestamp(lastSync).build()

            // Process the bounded stream
            spannerStub.catchUpDeltaStream(request).collect { deltaRecord ->
                localDb.withTransaction {
                    localDb.dataDao().upsert(deltaRecord.toLocalEntity())
                    localDb.syncDao().updateLastSyncedTimestamp(deltaRecord.spannerCommitTimestamp)
                }
            }
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
* A `visibilitychange` listener stops polling when the tab is hidden and polls immediately from the in-memory watermarks when it's visible again.

## Key Architectural Trade-Offs

| Advantage | Structural Limitation / Cost |
| :--- | :--- |
| **No Push Infrastructure Overhead:** Completely cuts out Firebase, APNs, push certs, and registration token management databases. | **Delayed Background Processing on iOS:** Updates while the app is closed depend on iOS device heuristics rather than structural timing intervals. |
| **Natural Stream Debouncing:** If 10,000 modifications hit a Spanner partition while the client is away, the client runs exactly one single pull transaction upon returning. | **Burst Backend Traffic:** If a significant event causes millions of users to launch the application concurrently, the backend will face massive concurrent catch-up query demands. |
| **Perfect Chronological Ordering:** Leveraging Spanner's TrueTime engine ensures updates are processed in absolute linear sequence, eliminating race conditions common to pushed messaging channels. | **Chunking / Pagination Overhead Requirement:** If a client goes offline for weeks, data payloads must be systematically broken down into discrete index chunks to stay within background runtime execution boundaries. |
| **Collaboration Without Open Connections:** Foreground polling reuses the catch-up stream and watermark, so ordering, resume, and tombstones work identically for offline catch-up and collaborative updates, and servers scale with request volume rather than connected users. | **Seconds of Staleness:** Collaborators see each other's changes after up to one polling interval (about 10 seconds in collaborative views), and idle polls add request volume. If that proves too slow, a lightweight change signal (for example, a per-survey "latest commit" document clients can watch) can trigger polls early without changing the RPCs. |
| **One Sync RPC per Resource:** Clients never choose between equivalent `List` and `Stream` calls; query RPCs exist only where the server must filter, sort, page, or aggregate. | **Web Holds No Offline Copy:** The Web Console's outbox is in memory, so edits queued during an outage are lost if the page is reloaded before they're sent, and very large surveys must use paged queries plus polling rather than a full in-memory copy. |
