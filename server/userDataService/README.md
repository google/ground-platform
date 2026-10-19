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

# User Data Service

Kotlin/JVM binary that serves the Collect One user data RPCs defined in
[`shared/protos`](../../shared/protos/): `SurveyService`, `OrganizationService`,
`LibraryService`, and `DataService`. The web console and the mobile apps sync
from it as described in
[`docs/technical/sync-engine.md`](../../docs/technical/sync-engine.md). Other
services will live next to it under `server/`.

## How It Works

-   **One port, two protocols.** [Armeria](https://armeria.dev) serves native
    gRPC (HTTP/2, including cleartext h2c) and gRPC-Web (for browsers) on
    `$PORT`, plus `GET /healthz`. The apps use gRPC-Web on every platform
    (`GrpcWebTransport` in `shared/app`).
-   **No extra code generation.** Wire generates the `*Server` interfaces in
    `shared/core`; [`WireGrpcBindings`](src/main/kotlin/org/openforis/collectone/v2/server/userdata/WireGrpcBindings.kt)
    turns their `@WireRpc` annotations into grpc-java service definitions at
    startup.
-   **Backend and store.** [`UserDataBackend`](src/main/kotlin/org/openforis/collectone/v2/server/userdata/UserDataBackend.kt)
    implements the RPCs' rules on top of a
    [`UserDataStore`](src/main/kotlin/org/openforis/collectone/v2/server/userdata/UserDataStore.kt),
    which reads and writes records in atomic transactions with
    store-assigned commit timestamps: a
    [`SpannerUserDataStore`](src/main/kotlin/org/openforis/collectone/v2/server/userdata/SpannerUserDataStore.kt)
    on Cloud Spanner, or the Spanner emulator in development and tests (see
    [Spanner](#spanner-and-the-spanner-debug-web-ui)). An empty database is
    loaded with a `UserDataSeed` (`shared/protos/userdata/user_data_seed.proto`).
    Unit tests use an in-memory fake store (`InMemoryUserDataStore`, under
    `src/test`); `UserDataStoreContractTest` checks that it behaves like the
    Spanner store, and the backend tests also run on Spanner.
-   **Sample seed.** The bundled seed
    ([`src/main/resources/seed/user_data_seed.json`](src/main/resources/seed/)),
    the initial data of an empty database and of `POST /debug/reset`, is
    generated from the Kotlin sample data fixtures in `shared/app/testing`.
    After changing the fixtures, regenerate it from `shared/app/` with
    `./gradlew jvmTest -PregenerateUserDataSeed`; `UserDataSeedSnapshotTest`
    fails while it's out of date.

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

    The installed binary (`build/install/user-data-service/bin/user-data-service`)
    runs on the `java` from `JAVA_HOME`, or the `PATH`; it needs Java 21 or
    later.
-   **Android SDK.** The service depends on `shared/core`, whose build applies
    the Android Gradle plugin, so Gradle needs an Android SDK even though the
    service doesn't use it. Set `ANDROID_HOME`, or put
    `sdk.dir=/path/to/Android/Sdk` in `shared/core/local.properties`, as for the
    other Gradle builds.

`./gradlew` here runs the Gradle wrapper from
[`../../shared/ui/`](../../shared/ui/), so this build doesn't check in its own
`gradle-wrapper.jar`.

## Running Locally

The service stores its data in Spanner, so start the Spanner emulator first (a
pinned, checksum-verified release; Docker on non-Linux hosts), from the
repository root:

```bash
scripts/spanner-emulator.sh &     # gRPC localhost:9010, REST localhost:9020
```

Then, from this directory:

```bash
export SPANNER_EMULATOR_HOST=localhost:9010
./gradlew run                     # Listens on http://localhost:8080
PORT=9090 ./gradlew run           # Another port
USER_DATA_SEED=/path/to/seed.json ./gradlew run   # Another seed for an empty database
```

Or build the distribution once and start the binary directly, which starts
faster than `./gradlew run`:

```bash
./gradlew installDist
PORT=8080 SPANNER_EMULATOR_HOST=localhost:9010 build/install/user-data-service/bin/user-data-service
```

The [development launchpad](../../devtools/launchpad/README.md) starts the
emulator and the service together.

Tests:

```bash
./gradlew test          # Unit and end-to-end tests, on the in-memory fake store
./gradlew spannerTest   # Tests that need Spanner (the store, the backend on Spanner, the debug UI)
```

`spannerTest` runs on the emulator at `SPANNER_EMULATOR_HOST`, or, when that
isn't set, on one it starts from `scripts/spanner-emulator.sh` on free ports and
stops afterwards. These tests use their own instance and databases, so they
don't touch a development database on the same emulator.

CI ([`.github/workflows/check.yml`](../../.github/workflows/check.yml)) runs both
on every pull request.

On startup it prints `User data service listening on port 8080 (6 surveys, 4
organizations)`. Check it with `curl http://localhost:8080/healthz`, which
returns 200. If another process already uses the port (for example another dev
server on 8080), start the service with `PORT=<free port>` and use that port in
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
    project in `FIREBASE_PROJECT_ID` (default `gnd-dev`) as
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
[Sharing Policies and Access](../../docs/technical/backend/spanner-schema.md#sharing-policies-and-access))
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

## Spanner and the Spanner Debug Web UI

The service stores its data in Cloud Spanner and doesn't start unless
`SPANNER_EMULATOR_HOST` (for the emulator) or `SPANNER_INSTANCE_ID` (for Cloud
Spanner) is set. The database is
`SPANNER_PROJECT_ID` / `SPANNER_INSTANCE_ID` / `SPANNER_DATABASE_ID` (defaults
`collect-one-dev` / `dev` / `user-data`). On the emulator, the instance and an
empty database are created at startup. The schema is designed in
[`docs/technical/backend/spanner-schema.md`](../../docs/technical/backend/spanner-schema.md).

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
| Web console ([`webApp/`](../../webApp/README.md)) | `USER_DATA_SERVICE_URL` in `local.properties` or the dev server's environment; the dev server proxies the service |
| Mobile app in the browser ([`mobileWebApp/`](../../mobileWebApp/README.md)) | `USER_DATA_SERVICE_URL` in `local.properties` or the dev server's environment; the dev server proxies the service |
| Android app ([`androidApp/`](../../androidApp/README.md)) | `USER_DATA_SERVICE_URL` as a Gradle property, in the environment, or in `local.properties`, at build time (`http://10.0.2.2:8080` reaches the emulator's host) |
| JVM (tests, desktop tools) | `groundplatform.userDataServiceUrl` system property or `USER_DATA_SERVICE_URL` environment variable |

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

## Deploying to Cloud Run

Build the image locally, push it to Artifact Registry, and deploy it:

```bash
IMAGE=<region>-docker.pkg.dev/<project>/<repository>/user-data-service
./gradlew installDist
docker build -t "$IMAGE" .
docker push "$IMAGE"
gcloud run deploy user-data-service \
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
-   `--max-instances 1` keeps a single instance until running several against
    one database has been verified.
-   `--allow-unauthenticated` lets browsers call the service. Requests aren't
    authenticated yet; restrict access before storing real data.
-   Clients poll with short unary `List*` calls (delta mode, with sync tokens)
    rather than holding connections open, so the default concurrency and request
    timeout are fine (see "Why Polling Instead of Open Streams" in the sync
    doc).
-   Sync tokens are bound to the database's data epoch, which changes when the
    data is reset, so clients then get `FAILED_PRECONDITION` for their old
    tokens and run a full sync.

The Dockerfile only packages the output of `./gradlew installDist`, because
building `shared/core` needs the Android SDK.
