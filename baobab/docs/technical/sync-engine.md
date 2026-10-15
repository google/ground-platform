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
When the user modifies or writes data while the device is in airplane mode or lacks network access, the application uses local isolation containment. This flow applies to mobile; the Web Console requires a connection to write.

1. **Local Mutation Transaction:** The client application captures user intent (e.g., creating a record) and commits it immediately to the local cache database.
2. **Sync Outbox Flagging:** The row is flagged locally with an outbox state metadata tag (e.g., `PENDING_SYNC = true`) and captures a local device timestamp.
3. **Reactive UI Update:** The local query emitting the Kotlin `Flow` detects the write transaction immediately. The UI updates natively in milliseconds, offering a completely uninterrupted user experience.
4. **Network Backoff Queueing:** The core sync engine queues a background worker task constrained by system connectivity filters.

### The Online Flow (Re-establishing Connection & Catch-Up)
When the device returns online or the application transitions from a suspended state to the foreground, a unified catch-up sequence initiates.

1. **Watermark Extraction:** The shared synchronization engine queries the local database's sync tracking table to extract the high-water mark token for each sync scope: `last_synced_spanner_timestamp`.
2. **Outbox Drain (Client to Server):** Before pulling updates, the client collects all records marked `PENDING_SYNC = true`. It ships these mutations to the backend over a gRPC call (`Mutate*`). The backend executes a high-speed write transaction in Cloud Spanner, which assigns a definitive `commit_timestamp` utilizing its atomic TrueTime engine.
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

1. **In-memory store:** The web app holds an in-memory store scoped to what's on screen: the survey list while it's visible, and the active survey's definition, entities, and submissions while a survey is open. The store is discarded when the user leaves that view or reloads the page; there is no outbox, because writes require a connection.
2. **Survey list:** When the list becomes visible, the client runs `StreamSurveyOverviews` with no `since_timestamp` to load every overview, then [polls](#foreground-polling) from the resulting watermark until the user navigates away.
3. **Active survey:** On opening a survey, the client calls `GetSurvey` (with `working_copy = true` in the Survey Editor when the overview reports `has_working_copy`), loads `StreamEntities` / `StreamSubmissions` for that `survey_id`, and then polls both from their watermarks.
4. **Large surveys:** When a survey is too large to hold in memory, data tables and map overviews use the query RPCs (`ListSubmissions` / `ListEntities` with `filter`, `order_by`, and `page_token`; `ListEntityClusters` for overview zoom levels). To keep the visible page fresh, the client polls the matching stream starting from the query's `server_watermark` and applies changes to the rows it holds (or re-runs the query when a change affects sorting or paging).
5. **Writes:** Mutations are sent directly with `Mutate*`, followed by an immediate poll to pick up the authoritative result. On failure, the UI reports the error instead of queueing the change.
6. **Tab lifecycle:** When the tab becomes hidden (`visibilitychange`), the client stops polling. When it becomes visible again, it polls immediately from its in-memory watermarks, catching up on anything it missed.

Browsers reach the gRPC service through gRPC-Web, which supports the unary and server-streaming calls used here; client-streaming and bidirectional streaming aren't required.

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
| **One Sync RPC per Resource:** Clients never choose between equivalent `List` and `Stream` calls; query RPCs exist only where the server must filter, sort, page, or aggregate. | **Web Holds No Offline Copy:** The Web Console can't read or write while disconnected, and very large surveys must use paged queries plus polling rather than a full in-memory copy. |
