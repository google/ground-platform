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

The local data store is the prototype's single source of truth. The UI reads
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
    `GeospatialEntityItem`, `SurveyEditorDraft`, …). They move to proto-aligned
    records (`SurveyDef`, `EntityRecord`, `SubmissionRecord`) when the sync
    engine lands.

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
