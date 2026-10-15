# Architectural Specification: Offline-First Reactive App Architecture
## Technology Stack: Kotlin Multiplatform (KMP), gRPC, and Google Cloud Spanner

This document outlines the architectural blueprint for a highly reactive, offline-first cross-platform application (Android and iOS) using a shared Kotlin Multiplatform core. The backend relies on **Google Cloud Spanner** for globally consistent mutations and **gRPC** for efficient binary communication. 

By prioritizing a **Best-Effort Pull-Based Model**, this architecture completely eliminates the need for Firebase Cloud Messaging (FCM) or Apple Push Notification service (APNs), keeping the infrastructure stateless, secure, and resilient to intermittent network conditions.

---

## 1. Core Architectural Pillars

### Single Source of Truth (SSOT)
The user interface layer (Jetpack Compose / SwiftUI) **never** communicates directly with the network layer or the remote Google Cloud Spanner database. The local database (Room / SQLDelight) serves as the absolute SSOT. The UI observes database tables continuously via native Kotlin `Flow`.

### Bounded Delta Synchronization
Instead of maintaining persistent, fragile streaming HTTP sockets while backgrounded, data synchronization relies on short-lived, **bounded gRPC streams**. The client requests changes occurring strictly within a time-window bounded by its last known state and the present server time.

### Stateless Cloud Backend
The server application layer maintains no state tracking regarding client online/offline status, session tokens, or delta logs. The client provides its own temporal coordinate (`last_synced_spanner_timestamp`), allowing Cloud Spanner to calculate and stream mutations on demand via index-optimized time queries.

---

## 2. End-to-End System Flows

### A. The Offline Flow (User Disconnected)
When the user modifies or writes data while the device is in airplane mode or lacks network access, the application uses local isolation containment.

1. **Local Mutation Transaction:** The client application captures user intent (e.g., creating a record) and commits it immediately to the local cache database.
2. **Sync Outbox Flagging:** The row is flagged locally with an outbox state metadata tag (e.g., `PENDING_SYNC = true`) and captures a local device timestamp.
3. **Reactive UI Update:** The local query emitting the Kotlin `Flow` detects the write transaction immediately. The UI updates natively in milliseconds, offering a completely uninterrupted user experience.
4. **Network Backoff Queueing:** The core sync engine queues a background worker task constrained by system connectivity filters.

### B. The Online Flow (Re-establishing Connection & Catch-Up)
When the device returns online or the application transitions from a suspended state to the foreground, a unified catch-up sequence initiates.

1. **Watermark Extraction:** The shared synchronization engine queries the local database's sync tracking table to extract the single high-water mark token: `last_synced_spanner_timestamp`.
2. **Outbox Drain (Client to Server):** Before pulling updates, the client collects all records marked `PENDING_SYNC = true`. It ships these mutations to the backend over a gRPC call. The backend executes a high-speed write transaction in Cloud Spanner, which assigns a definitive `commit_timestamp` utilizing its atomic TrueTime engine.
3. **Delta Request (Server to Client):** The client initiates a bounded gRPC stream request, sending its `last_synced_spanner_timestamp` to the server.
4. **Cloud Spanner Index Evaluation:** The backend service executes a highly optimized index lookup against Spanner tables:
   ```sql
   SELECT * FROM AppDataTables WHERE spanner_commit_timestamp > @lastClientSyncTime ORDER BY spanner_commit_timestamp ASC;
   ```
5. **Draining and Advancing:** The server streams the rows back down the gRPC channel. For every row emitted, the shared core engine:
   * Upserts the row into the local database table.
   * Updates the `last_synced_spanner_timestamp` to match the record's authoritative server timestamp.
6. **Graceful Teardown:** Once the delta is exhausted, the server fires an `onCompleted()` signal, and the gRPC connection safely tears down, freeing mobile radio bands and saving battery power.

---

## 3. Kotlin Multiplatform Code Topology

To enforce strict code reuse and maintain a single location for synchronization logic, the implementation splits the *execution engine* from the *scheduling primitives*.

### Shared Business Core (`commonMain`)
All gRPC stubs, schema parsing, delta tracking, and transactional local operations are written once in the shared core.

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
}
```

### Platform Schedulers (`androidMain` and `iosMain`)
Because Android and iOS enforce conflicting background paradigms, scheduling hooks are thin native wrappers passing invocation control into the shared engine.

#### Android Setup (`androidMain`)
Utilizes **Jetpack WorkManager** to execute deterministic periodic sweeps (minimum 15-minute intervals) when network connectivity is verified by the OS.
* If a network drops mid-sync, the worker returns `Result.retry()` to automatically invoke an exponential backoff routine.

#### iOS Setup (`iosMain` / Swift Linkage)
Utilizes **BGTaskScheduler** for opportunistic background processing.
* Because iOS strictly throttles background execution slots to roughly 30-second windows and delivers them pseudo-randomly (3-4 times a day depending on on-device machine learning models), the architecture shifts weight heavily onto the **Foreground Lifecycle Hook**. 
* The moment the iOS app transitions to `sceneWillEnterForeground`, it executes the shared `executeCatchUpSync()` asynchronously, wiping out stale gaps immediately before the user can trigger missing interface states.

---

## 4. Key Architectural Trade-Offs

| Advantage | Structural Limitation / Cost |
| :--- | :--- |
| **No Push Infrastructure Overhead:** Completely cuts out Firebase, APNs, push certs, and registration token management databases. | **Delayed Background Processing on iOS:** Updates while the app is closed depend on iOS device heuristics rather than structural timing intervals. |
| **Natural Stream Debouncing:** If 10,000 modifications hit a Spanner partition while the client is away, the client runs exactly one single pull transaction upon returning. | **Burst Backend Traffic:** If a significant event causes millions of users to launch the application concurrently, the backend will face massive concurrent catch-up query demands. |
| **Perfect Chronological Ordering:** Leveraging Spanner's TrueTime engine ensures updates are processed in absolute linear sequence, eliminating race conditions common to pushed messaging channels. | **Chunking / Pagination Overhead Requirement:** If a client goes offline for weeks, data payloads must be systematically broken down into discrete index chunks to stay within background runtime execution boundaries. |
