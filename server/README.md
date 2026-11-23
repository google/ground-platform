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

# Collect One Server

Kotlin/JVM binary that serves the Collect One backend RPCs defined in
[`shared/protos`](../shared/protos/): `SurveyService`, `OrganizationService`,
`LibraryService`, `DataService`, `GeoDataService`, and `SyncService`, plus
in-process transactional invitation emails and FCM topic tickles. The web
console and the mobile apps sync from it as described in
[`docs/technical/sync-engine.md`](../docs/technical/sync-engine.md).

## How It Works

-   **One port, two protocols.** [Armeria](https://armeria.dev) serves native
    gRPC (HTTP/2, including cleartext h2c) and gRPC-Web (for browsers) on
    `$PORT`, plus `GET /healthz` and `GET /health`. The apps use gRPC-Web on
    every platform (`GrpcWebTransport` in `shared/app`).
-   **No extra code generation.** Wire generates the `*Server` interfaces in
    `shared/core`; [`WireGrpcBindings`](src/main/kotlin/org/openforis/collectone/v2/server/app/WireGrpcBindings.kt)
    turns their `@WireRpc` annotations into grpc-java service definitions at
    startup.
-   **Services and store.** One class per gRPC service in
    [`service/`](src/main/kotlin/org/openforis/collectone/v2/server/service/) (`SurveyServiceImpl`,
    `OrganizationServiceImpl`, `LibraryServiceImpl`, `DataServiceImpl`,
    `GeoDataServiceImpl`; `DataServiceImpl` hands plot locks to `PlotLocks` and
    the reporting RPCs to `DataReports`), composed by
    [`UserDataBackend`](src/main/kotlin/org/openforis/collectone/v2/server/service/UserDataBackend.kt),
    implements the RPCs' rules on top of a
    [`UserDataStore`](src/main/kotlin/org/openforis/collectone/v2/server/data/UserDataStore.kt),
    which reads and writes records in atomic transactions with
    store-assigned commit timestamps: a
    [`SpannerUserDataStore`](src/main/kotlin/org/openforis/collectone/v2/server/data/SpannerUserDataStore.kt)
    on Cloud Spanner (its reads in `SpannerUserDataReader`, buffered writes in
    `SpannerUserDataWriter`, and row encoding in `SpannerRows.kt`), or the
    Spanner emulator in development and tests (see
    [Spanner](#spanner-and-the-spanner-debug-web-ui)). An empty database is
    loaded with a `UserDataSeed` (`shared/protos/userdata/user_data_seed.proto`).
    Unit tests use an in-memory fake store (`InMemoryUserDataStore`, under
    `src/test`); `UserDataStoreContractTest` checks that it behaves like the
    Spanner store, and the backend tests also run on Spanner.
-   **Concurrency.** Store calls suspend while the Spanner client's blocking
    I/O runs on `Dispatchers.IO`, so RPC handlers interleave while they wait
    for Spanner. Each `Mutate*` RPC checks replays (`client_seq`), base
    versions, and quotas inside its write transaction, so concurrent requests
    stay correct without relying on serialized RPCs or a single instance
    (`ConcurrentMutationTest`, which races as many attempts as there are RPC
    lanes). Up to `RPC_PARALLELISM` RPC handlers (default 8, from 1 to 64)
    run at once in one instance, so a slow request doesn't hold up the others.
    Deployments still use `--max-instances 1`; see
    [Running More Than One Instance](#running-more-than-one-instance).
-   **Server-owned fields.** The server stamps `audit_info` on surveys,
    entities, and submissions (who from the authenticated caller, when from
    its clock) and `SurveyDef.saved_revision_number` (a new survey starts at 1;
    each save increments it). `MutateSurvey` and `MutateData` ignore any value
    a mutation carries for them (`ServerOwnedFieldsTest`).
-   **Packages.** Under `org.openforis.collectone.v2.server`, each package
    depends only on the ones after it:
    -   `app`: `main`, the Armeria server and its routes, and the Wire to
        grpc-java bindings.
    -   `debug`: the RPC, Message Inspector, and Spanner debug web UIs.
    -   `service`: the gRPC service implementations and the access rules.
    -   `data`: the store, Spanner, the sample seed, and sync token paging.
    -   `geo`: Earth Engine tiles, imagery analytics, and Open Foris Whisp
    geospatial indicator fetching (`WhispClient`).
    -   `messaging`: invitation emails and FCM topic tickles.
    -   `auth`: callers and authentication.
    -   `web`: shared HTTP and RPC plumbing (Server-Sent Events, proto JSON,
        gRPC status mapping).
-   **Sample seed.** The bundled seed
    ([`src/main/resources/seed/user_data_seed.json`](src/main/resources/seed/)),
    the initial data of an empty database and of `POST /debug/reset`, is
    generated from the Kotlin sample data fixtures in `shared/app/testing`.
    After changing the fixtures, regenerate it from the repository root with
    `./gradlew :shared:app:data:jvmTest -PregenerateUserDataSeed`;
    `UserDataSeedSnapshotTest` fails while it's out of date.
-   **Seed survey area backfill.** The seed only loads into an empty
    database, so a database loaded before the sample surveys had survey areas
    keeps surveys without one. At startup the service sets the seed's survey
    area on each stored sample survey that has none in its saved revision or
    working copy (`UserDataBackend.backfillSeedSurveyAreas`). It never
    replaces an area, skips surveys that aren't stored, and changes nothing on
    later starts; each survey it updates gets a new `record_version`, so
    downloaded copies refresh.

## Prerequisites

-   **JDK.** Gradle runs on **Java 21**, pinned in
    `gradle/gradle-daemon-jvm.properties`, and compiles the service with a
    Java 21 toolchain. Gradle uses an installed JDK 21 if it finds one and
    downloads one otherwise; `./gradlew` itself only needs a JDK on the `PATH`
    (or `JAVA_HOME`) to start. For example:

    ```bash
    export JAVA_HOME=/path/to/jdk-21
    java -version
    ```

    The installed binary (`server/build/install/c1-server/bin/c1-server`)
    runs on the `java` from `JAVA_HOME`, or the `PATH`; it needs Java 21 or
    later.

The server does not need an Android SDK. The repository is a single Gradle
build, so run the `./gradlew` commands below from the repository root; pass
`-Pcollectone.serverOnly=true` (or set `collectone.serverOnly=true` in
`local.properties`) when syncing in an IDE or running root tasks without the
client modules or an Android SDK.

## Running Locally

The service stores its data in Spanner, so start the Spanner emulator first (a
pinned, checksum-verified release; Docker on non-Linux hosts), from the
repository root:

```bash
scripts/spanner-emulator.sh &     # gRPC localhost:9010, REST localhost:9020
```

Then, also from the repository root:

```bash
export SPANNER_EMULATOR_HOST=localhost:9010
./gradlew :server:run                     # Listens on http://localhost:8080
PORT=9090 ./gradlew :server:run           # Another port
USER_DATA_SEED=/path/to/seed.json ./gradlew :server:run   # Another seed for an empty database
```

Or build the distribution once and start the binary directly, which starts
faster than `./gradlew :server:run`:

```bash
./gradlew :server:installDist
PORT=8080 SPANNER_EMULATOR_HOST=localhost:9010 server/build/install/c1-server/bin/c1-server
```

The [development launchpad](../devtools/launchpad/README.md) starts the
emulator and the service together.

Tests:

```bash
./gradlew :server:test          # Unit and end-to-end tests, on the in-memory fake store
./gradlew :server:spannerTest   # Tests that need Spanner (the store, the backend on Spanner, the debug UI)
```

`spannerTest` runs on the emulator at `SPANNER_EMULATOR_HOST`, or, when that
isn't set, on one it starts from `scripts/spanner-emulator.sh` on free ports and
stops afterwards. These tests use their own instance and databases, so they
don't touch a development database on the same emulator.

CI ([`.github/workflows/check.yml`](../.github/workflows/check.yml)) runs both
on every pull request.

On startup it prints `Collect One server listening on port 8080 (6 surveys, 4
organizations)`. Check it with `curl http://localhost:8080/health`, which
returns 200 (`/healthz` works locally too, but Cloud Run's front end answers
some paths ending in `z` itself, so deployed checks use `/health`). If another
process already uses the port (for example another dev server on 8080), start
the service with `PORT=<free port>` and use that port in
`USER_DATA_SERVICE_URL`.

## Authentication

Every RPC runs as an authenticated caller, chosen by `AUTH_MODE`:

-   **`dev` (default).** For local development only. Calls that send a Firebase
    ID token (the web apps once signed in) are verified as in `firebase` mode,
    and those users are administrators, so they see all sample data, unless
    the call sends `x-dev-role: user` to simulate a regular user (the web apps'
    development sign-in dialog does). `x-dev-role: admin` makes a user named
    by the `x-dev-user-*` headers an administrator.
    Otherwise calls name a user with the `x-dev-user-id`, `x-dev-user-email`,
    and optional `x-dev-user-name` headers (the email counts as verified), or
    run as the sample organizer, Maya Lin (`uid-maya-lin`), keeping request
    fields such as `AcquirePlotLockRequest.user_id`, so clients with fake
    sign-in keep working.
-   **`firebase`.** Calls must send a Firebase Authentication ID token for the
    project in `FIREBASE_PROJECT_ID` (default `collect-one-dev`) as
    `Authorization: Bearer <token>`; anonymous accounts are rejected. Calls
    without a valid token fail with `UNAUTHENTICATED`. `ADMIN_EMAILS` names
    administrators by verified email (comma-separated).

Before each RPC, the service registers the caller in its users (on the first
call, or when the email or profile changed). For a verified email, it binds
survey ACL entries and organization invites addressed to that email to the
caller's user ID. The caller's user ID also replaces `user_id` request fields.

Each user has an access epoch that increases whenever their ACL entries or
memberships change (including invites bound at sign-in).
Caller-dependent list tokens (`ListSurveyOverviews`, `ListOrganizations`,
`ListLibraries`, and the report lists) carry it, so a client whose access
changed gets `FAILED_PRECONDITION` and runs a full sync.

### Authorization

RPCs check the caller's role (`Access.kt`, following
[Sharing Policies and Access](../docs/technical/backend/spanner-schema.md#sharing-policies-and-access))
and fail with `PERMISSION_DENIED` otherwise:

-   **Survey role:** the highest of owner, the caller's ACL entry, the sharing
    role for `ORGANIZATION` surveys (active members of the survey's
    organization), and the sharing role for `PUBLIC` and `ANYONE_WITH_LINK`
    surveys.
-   **Surveys:** viewers read the survey and its data; only organizers see ACL
    entries, read working copies, and change surveys. Anyone signed in can
    create a survey, which they then own. `ListSurveyOverviews` lists owned,
    ACL, and organization-shared surveys (not link or public ones); a delta
    returns a tombstone for a survey the caller can no longer list.
-   **Data:** data collectors write records, attributed to them as
    `created_by`. Under `OWN_ONLY` peer data visibility, collectors only see
    and change their own records. Entity and submission tokens carry what the
    caller sees (all records or only its own), so a visibility change makes
    stale tokens fail with `FAILED_PRECONDITION`.
-   **Organizations and libraries:** members see their organizations (managers
    and active members see all members); managers change them and their
    library. The global library is readable by everyone.
-   **Reports:** impact events, aggregates, and audit records are limited to
    survey organizers and organization managers.
-   **Administrators** (active managers of the all-users organization,
    `ADMIN_EMAILS`, and every caller in `dev` mode) may do anything, so the
    development setup shows all sample data.

## RPC Debug Web UI

The debug web UIs (`/`, `/debug`, `/debug/rpcs*`, `/debug/messages*`, and
`/debug/spanner*`) aren't authenticated and show every RPC, invitation email,
and database row, so the service serves them only when `DEBUG_UI_ENABLED` is
`true`. It defaults to `true` in `AUTH_MODE=dev` and `false` otherwise; without
it those paths return 404 and the service doesn't record RPCs or Spanner
operations. `/healthz` and `/health` are always served. Never enable the debug
UI on a service that stores real data.

`POST /debug/reset` replaces all data with the seed and starts a new data epoch.
It isn't authenticated either, so it has its own switch, `DEBUG_RESET_ENABLED`,
independent of the debug UI. It defaults to `true` only for local `dev` runs,
and to `false` in `AUTH_MODE=firebase` and on Cloud Run (which sets `K_SERVICE`
on every service), so a deployed service never offers it unless it opts in.

Open `http://localhost:8080/` (or `http://localhost:8080/debug`) in a browser
while the service is running to inspect live incoming gRPC and gRPC-Web calls:

-   **Live stream & controls**: Streams incoming RPCs in real time over
    Server-Sent Events (`/debug/rpcs/stream`), with controls to pause/resume
    updates and clear the in-memory log (`POST /debug/rpcs/clear`).
-   **Filtering & search**: Filter by service (`SurveyService`, `DataService`,
    `OrganizationService`, `LibraryService`), method, gRPC status (`OK` vs.
    errors), or full-text search across request and response Proto JSON
    payloads.
-   **Hide empty delta polls**: Enabled by default so background `List*` delta
    polls that return zero changes don't flood the table while still showing a
    count of hidden polls.
-   **Message Inspector (`/debug/messages`)**: Inspects sent and logged survey
    and organization invitation emails in real time over SSE
    (`/debug/messages/api/stream`), with an HTML email preview, plain-text body,
    and CTA deep links. Set `BREVO_API_KEY` (and optional `EMAIL_FROM`,
    `EMAIL_FROM_NAME`, `PUBLIC_WEB_URL`) to deliver invitations over the Brevo
    HTTP API, or leave `EMAIL_MODE=log` for local inspection.

## Spanner and the Spanner Debug Web UI

The service stores its data in Cloud Spanner and doesn't start unless
`SPANNER_EMULATOR_HOST` (for the emulator) or `SPANNER_INSTANCE_ID` (for Cloud
Spanner) is set. The database is
`SPANNER_PROJECT_ID` / `SPANNER_INSTANCE_ID` / `SPANNER_DATABASE_ID` (defaults
`collect-one-dev` / `dev` / `user-data`). On the emulator, the instance and an
empty database are created at startup. The schema is designed in
[`docs/technical/backend/spanner-schema.md`](../docs/technical/backend/spanner-schema.md).

At startup the service applies
[`src/main/resources/spanner/schema.sql`](src/main/resources/spanner/schema.sql)
at startup (with a proto bundle built from `shared/protos` by the
`generateSpannerDescriptors` task) and, if the database was never loaded, loads
the seed into it. Records are stored as `PROTO` columns, and record IDs must be
UUIDs. Places and the mutation log examples are still served from the seed.

On an existing database, startup adds new tables, columns, indexes, and proto
types. The emulator can't change the proto bundle without breaking existing
generated columns, so when the protos or bundled types change it recreates the
database instead (its data is reset to the seed).

With the service running (see [Running Locally](#running-locally)), open
`http://localhost:8080/debug/spanner` (also linked from the RPC debug UI):

-   **Tables**: Every table, nested under its interleaving parent, with row
    counts. Selecting one shows its columns (types, generated columns), primary
    key, indexes, and deletion policy, and pages through its rows in key order.
    Click a cell to inspect it: `PROTO` columns are decoded with the generated
    Wire classes and shown as Proto JSON, and `ENUM` values by name.
-   **Operations**: A live log of everything the service's Spanner client does
    (transactions, queries, reads, DML, commits), recorded from the client's
    OpenTelemetry spans, with SQL text, durations, status, attributes, events,
    and the other operations in the same trace. The UI's own table browsing
    isn't recorded.
-   **SQL**: Runs read-only SQL in a single-use read-only transaction; these
    queries appear in Operations.
-   **Schema DDL**: The database's DDL statements.

The emulator keeps data in memory, so restarting it wipes the database; the
service loads the seed again when it next starts.

## Pointing Clients at the Service

Each client needs the service's URL. Without one it doesn't start and shows
setup instructions instead; if the service can't be reached, it shows an error
banner with a Retry button. There's no default URL.

| Client | How to set the URL |
| :--- | :--- |
| Web console ([`webApp/`](../webApp/README.md)) | `USER_DATA_SERVICE_URL` in `local.properties` or the dev server's environment; the dev server proxies the service |
| Mobile app in the browser ([`mobileWebApp/`](../mobileWebApp/README.md)) | `USER_DATA_SERVICE_URL` in `local.properties` or the dev server's environment; the dev server proxies the service |
| Android app ([`androidApp/`](../androidApp/README.md)) | `USER_DATA_SERVICE_URL` as a Gradle property, in the environment, or in `local.properties`, at build time (`http://10.0.2.2:8080` reaches the emulator's host) |
| iOS app ([`iosApp/`](../iosApp/README.md)) | `USER_DATA_SERVICE_URL` build setting (`Info.plist`) or environment variable |
| JVM (tests, desktop tools) | `collectone.userDataServiceUrl` system property or `USER_DATA_SERVICE_URL` environment variable |

`local.properties` at the repository root is read by the web console, the browser-hosted mobile
app, and the Android app, so one line points all of them at the same service.
The web dev servers proxy the service from their own machine, so for them use an
address that machine reaches, such as `localhost`; the browser only talks to
the dev server:

```properties
USER_DATA_SERVICE_URL=http://localhost:8080
```

Change it and reload the page; the web dev servers re-read `local.properties` on
every request. (The Android app is built with the value, so for the emulator
override it with `http://10.0.2.2:8080` in `androidApp/local.properties`.)

## Open Foris Whisp Entity Enrichment

When `WHISP_API_KEY` is set in `local.properties` or the server's environment,
`WhispEntityEnricher` asynchronously enriches geospatial entities whenever
`MutateData` creates an entity with geometry or modifies an entity's geometry:

```properties
WHISP_API_KEY=<open-foris-whisp-api-key>
# Optional overrides:
# WHISP_API_URL=https://whisp.openforis.org/api/submit/wkt
# WHISP_PREFIX=whisp_
```

After the `MutateData` transaction commits, the server converts the entity's
`Geometry` to OGC WKT, posts `{"wkt": "..."}` with `X-API-KEY` to the Whisp
endpoint outside the Spanner transaction, merges the returned feature
properties (prefixed with `whisp_`) into `EntityRecord.properties` in a
follow-up transaction (bumping `revision`), and publishes an entity change
notification on `survey_<survey_id>_entities`. Enriched `whisp_*` properties
automatically appear in CSV, GeoJSON, and KML exports.

## Deploying to Cloud Run

Build the image from the repository root, push it to Artifact Registry, and
deploy it:

```bash
IMAGE=<region>-docker.pkg.dev/<project>/<repository>/c1-server
docker build -f server/Dockerfile -t "$IMAGE" .
docker push "$IMAGE"
gcloud run deploy c1-server \
  --image "$IMAGE" \
  --region <region> \
  --use-http2 \
  --max-instances 1 \
  --set-env-vars SPANNER_PROJECT_ID=<project>,SPANNER_INSTANCE_ID=<instance>,SPANNER_DATABASE_ID=<database> \
  --allow-unauthenticated
```

-   `--use-http2` sends HTTP/2 to the container end to end, which native gRPC
    needs. gRPC-Web from browsers works either way.
-   `--set-env-vars` names the Cloud Spanner database, which the service's
    account needs access to.
-   **Firebase Authentication & FCM topic push notifications**:
    -   Set `AUTH_MODE=firebase` and `FIREBASE_PROJECT_ID=<firebase-project-id>`
        to verify Firebase ID tokens on incoming RPCs.
    -   When `AUTH_MODE=firebase` (or `FCM_ENABLED=true` is explicitly set),
        `SurveyTopicNotifier` publishes debounced (500 ms per `(survey_id, scope)`)
        data-only FCM HTTP v1 messages to `survey_<survey_id>_def` and
        `survey_<survey_id>_entities` after survey or entity commits so mobile
        clients wake up and run scoped delta sync without idle polling. Grant
        the Cloud Run runtime service account the Firebase Cloud Messaging API
        Admin role (`roles/cloudmessaging.messages.create`) on the Firebase
        project. Set `FCM_ENABLED=false` to disable FCM publishing explicitly.
-   `--max-instances 1` keeps a single instance until the in-process state
    described in [Running More Than One Instance](#running-more-than-one-instance)
    moves out of the server. Mutation correctness doesn't depend on it (see
    Concurrency under [How It Works](#how-it-works)). `RPC_PARALLELISM` sets how
    many RPC handlers run at once in that instance (default 8).
-   `--allow-unauthenticated` lets browsers reach the service; the service
    authenticates each RPC itself (see [Authentication](#authentication)), so
    set `AUTH_MODE=firebase` before storing real data. `AUTH_MODE=dev` trusts
    `x-dev-user-*` headers and makes callers administrators, and it turns on
    the unauthenticated debug UI (see [RPC Debug Web UI](#rpc-debug-web-ui));
    `DEBUG_UI_ENABLED=false` turns that off. `POST /debug/reset` stays off on
    Cloud Run unless `DEBUG_RESET_ENABLED=true`; set it only on a demo service
    whose data may be wiped by anyone who can reach it.
-   Clients poll with short unary `List*` calls (delta mode, with sync tokens)
    rather than holding connections open, so the default concurrency and request
    timeout are fine (see "Why Polling Instead of Open Streams" in the sync
    doc).
-   Sync tokens are bound to the database's data epoch, which changes when the
    data is reset, so clients then get `FAILED_PRECONDITION` for their old
    tokens and run a full sync.

`server/Dockerfile` is a multi-stage build from the repository root: it compiles
`:server` and `:shared:core` with `-Pcollectone.serverOnly=true` on
`eclipse-temurin:21-jdk` (no Android SDK needed) and copies the installed
distribution into `eclipse-temurin:21-jre`.

## Running More Than One Instance

Mutations stay correct across instances: replay checks, base versions, and
quotas run inside each write transaction. What still ties the service to one
instance is state kept in the server process:

-   **Live notifications.** The Server-Sent Events behind the message and
    survey update streams (`SseBroadcaster`) are held in memory, so clients
    connected to one instance miss events from another. They need a shared
    channel first, such as Pub/Sub or Spanner change streams.
-   **The Trash purge.** The daily job that removes surveys trashed more than
    30 days ago runs inside the server (`main`), so it would run once per
    instance. It needs to move to a single scheduled job, such as Cloud
    Scheduler calling an authenticated endpoint.
-   **Debug recorders.** The RPC, message, and Spanner operation logs of the
    debug UIs become per-instance. That's acceptable, since the debug UIs are
    off in production.

## Load Testing

`./gradlew :server:loadTest` runs `LocalLoadTest` against the local Spanner
emulator only, never a deployed server (staging shares the live database). It
starts a server over a freshly seeded emulator database for each RPC
parallelism, fires concurrent clients' mixed reads and mutations, checks that
no update is lost or applied twice, and writes throughput and latency
percentiles to `build/reports/load/summary.txt`. Tune it with
`-Pload.parallelism=1,8`, `-Pload.clients=16`, and
`-Pload.requestsPerClient=30`.

The emulator runs one read-write transaction at a time and aborts the others,
which the Spanner client retries with backoff, so concurrent mutations show a
long latency tail there that production Spanner's row locking doesn't have.
Compare parallelisms with each other on the emulator, not with production.
