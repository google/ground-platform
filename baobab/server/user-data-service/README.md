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
    `shared/core`; [`WireGrpcBindings`](src/main/kotlin/org/groundplatform/v2/server/userdata/WireGrpcBindings.kt)
    turns their `@WireRpc` annotations into grpc-java service definitions at
    startup.
-   **In-memory state.** [`UserDataBackend`](src/main/kotlin/org/groundplatform/v2/server/userdata/UserDataBackend.kt)
    holds all data in memory, loaded at startup from a `UserDataSeed`
    (`shared/protos/userdata/user_data_seed.proto`). Changes are lost on
    restart and aren't shared between instances.
-   **Sample seed.** The bundled seed
    ([`src/main/resources/seed/user_data_seed.json`](src/main/resources/seed/))
    is generated from the Kotlin sample data fixtures in `shared/app/testing`.
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
    other baobab builds.

`./gradlew` here runs the Gradle wrapper from
[`../../shared/ui/`](../../shared/ui/), so this build doesn't check in its own
`gradle-wrapper.jar`.

## Running Locally

From this directory:

```bash
./gradlew run                     # Listens on http://localhost:8080
PORT=9090 ./gradlew run           # Another port
USER_DATA_SEED=/path/to/seed.json ./gradlew run   # Another seed
./gradlew test                    # Unit and end-to-end tests
```

Or build the distribution once and start the binary directly, which starts
faster than `./gradlew run`:

```bash
./gradlew installDist
PORT=8080 build/install/user-data-service/bin/user-data-service
```

On startup it prints `User data service listening on port 8080 (6 surveys, 4
organizations)`. Check it with `curl http://localhost:8080/healthz`, which
returns 200. If another process already uses the port (for example another dev
server on 8080), start the service with `PORT=<free port>` and use that port in
`USER_DATA_SERVICE_URL`.

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

The service connects to Cloud Spanner when `SPANNER_EMULATOR_HOST` (for the
emulator) or `SPANNER_INSTANCE_ID` (for Cloud Spanner) is set. The database is
`SPANNER_PROJECT_ID` / `SPANNER_INSTANCE_ID` / `SPANNER_DATABASE_ID` (defaults
`collect-one-dev` / `dev` / `user-data`). On the emulator, the instance and an
empty database are created at startup. The schema is designed in
[`docs/technical/backend/spanner-schema.md`](../../docs/technical/backend/spanner-schema.md);
RPCs are still served from memory until the Spanner store lands.

Run the emulator (a pinned, checksum-verified release; Docker on non-Linux
hosts) and start the service against it, from `baobab/`:

```bash
scripts/spanner-emulator.sh &     # gRPC localhost:9010, REST localhost:9020
cd server/user-data-service
SPANNER_EMULATOR_HOST=localhost:9010 ./gradlew run
```

Then open `http://localhost:8080/debug/spanner` (also linked from the RPC debug
UI):

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

The emulator keeps data in memory, so restarting it wipes the database. Tests
that need it (`SpannerDebugTest`) are skipped unless `SPANNER_EMULATOR_HOST` is
set:

```bash
SPANNER_EMULATOR_HOST=localhost:9010 ./gradlew test
```

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

`baobab/local.properties` is read by the web console, the browser-hosted mobile
app, and the Android app, so one line points all of them at the same service.
The web dev servers proxy the service from the Cloudtop, so for them use an
address the Cloudtop reaches, such as `localhost`; the browser only talks to
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
  --allow-unauthenticated
```

-   `--use-http2` sends HTTP/2 to the container end to end, which native gRPC
    needs. gRPC-Web from browsers works either way.
-   `--max-instances 1` keeps a single copy of the in-memory state; remove it
    once the service is backed by a database.
-   `--allow-unauthenticated` lets browsers call the service. Requests aren't
    authenticated yet; restrict access before storing real data.
-   Clients poll with short unary `List*` calls (delta mode, with sync tokens)
    rather than holding connections open, so the default concurrency and request
    timeout are fine (see "Why Polling Instead of Open Streams" in the sync
    doc).
-   Sync tokens are bound to a random data epoch chosen when the service starts,
    so after a restart (which reloads the seed) clients get
    `FAILED_PRECONDITION` for their old tokens and run a full sync.

The Dockerfile only packages the output of `./gradlew installDist`, because
building `shared/core` needs the Android SDK.
