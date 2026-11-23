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

# Collect One

This directory contains the Kotlin Multiplatform (KMP) and Compose Multiplatform
(CMP) shared modules, Protocol Buffer data models, platform applications
(`androidApp`, `iosApp`, `webApp`, `mobileWebApp`), developer tools, and technical documentation
for **Collect One**.

> [!NOTE]
> iOS is opt-in: `iosApp/` and the iOS targets build only with `-Pcollectone.ios=true`, and CI doesn't build them for now.

## Directory Structure

```text
├── shared/                      # All shared multiplatform schemas & modules
│   ├── protos/                  # Protocol Buffer (.proto) schemas (forms, survey, data)
│   ├── core/                    # Pure KMP runtime library (JVM, iOS, JS, WasmJS)
│   ├── ui/                      # Shared Compose theme & form runner widgets (Android/JVM, iOS, JS, WasmJS)
│   ├── map/                     # Shared map rendering (Mapbox) & geometry
│   ├── app/                     # Shared UI state, ViewModels & components for the mobile and web apps (Android/JVM, WasmJS)
│   │   ├── domain/              # Shared domain models, repository interfaces & use cases (pure Kotlin)
│   │   └── data/                # Shared data layer: local store (SQLDelight), sync, remote & device clients
│   ├── web/                     # Web console pages, state & WebConsoleApp() root (JVM for tests, WasmJS)
│   └── mobile/                  # Mobile app screens, state & MobileApp() root (Android/JVM, WasmJS; iOS opt-in)
│       └── src/commonMain/.../mobile/
│           ├── ui/              # Mobile screens, ViewModels & navigation
│           ├── data/            # Reserved for mobile-only persistence (README only; storage lives in shared/app data/)
│           └── services/        # Reserved for mobile-only services (README only; sync lives in shared/app data/sync/)
├── androidApp/                  # Runnable Android app wrapper (APK/AAB) -> depends on shared/mobile
├── iosApp/                      # Runnable iOS Xcode app wrapper (IPA)   -> embeds shared/mobile
├── webApp/                      # Runnable Web Console app (Wasm)        -> depends on shared/web
├── mobileWebApp/                # Runnable mobile app for the browser (Wasm) -> depends on shared/mobile
│   └── workbench/               # Simulated device, XForms editor & sample data tools around the mobile app
├── server/                      # Backend server (Kotlin/JVM; gRPC + gRPC-Web, invite emails, FCM, Cloud Run)
├── devtools/
│   └── launchpad/               # Browser page for starting, stopping & watching local services
├── e2eTests/                    # Golden-screenshot tests of the web console & mobile app (Playwright)
└── docs/                        # System architecture & Protocol Buffer data model specifications
```

### Shared Modules ([`shared/`](shared/))

-   **[`shared/protos/`](shared/protos/)**: Protocol Buffer (`proto3`) schema
    definitions for forms (`FormDef`, `RecordInstance`, `RecordSchema`,
    `FieldBinding`), survey configurations (`SurveyDef`, `AclEntry`), and
    entity/submission/audit records.
-   **[`shared/core/`](shared/core/)**: Pure Kotlin Multiplatform runtime
    library (`:shared:core`) targeting JVM, iOS, JS
    (IR), and WasmJS:
    -   **XForms XML, TextProto & JSON Serialization**
        (`org.openforis.collectone.v2.core.forms.serialization`): Bidirectional
        conversion between XForms XML (`<h:html>` and `<data>`),
        `openforis.collectone.v1.forms` Protocol Buffer messages, Protocol Buffer Text
        Format (`textproto`), and Canonical Proto3 JSON (`ProtoJsonSerializer`).
    -   **XPath 1.0 + XForms Engine**
        (`org.openforis.collectone.v2.core.forms.xpath`): Full AST compiler and
        evaluator for XForms expressions against strongly-typed
        `RecordInstance` and `FormDef` models.
    -   **Dynamic Form State Model & Evaluation Engine**
        (`org.openforis.collectone.v2.core.forms.engine` &
        `org.openforis.collectone.v2.core.forms.model`): Immutable runtime UI state
        model (`FormState`, `ComponentState`) and reactive 5-stage evaluation
        engine (`FormEngine`, `FormSession`, `CompiledForm`) for dynamic
        repeats, calculations, relevance, constraints, cascading `itemset`s,
        translations, and XForms Entities.
-   **[`shared/ui/`](shared/ui/)**: Compose Multiplatform UI library
    (`:shared:ui`, package
    `org.openforis.collectone.v2.core.forms.ui`) targeting Web (`js` / `wasmJs`),
    Android/JVM (`jvm`), and iOS (`iosArm64` / `iosSimulatorArm64`). Provides
    shared Material 3 theming (`AppTheme`) and the single-question-per-screen
    form experience (`MobileFormRunner`, `FormWizardController`,
    `QuestionControlCard`, and `ControlWidget`).
-   **[`shared/app/`](shared/app/)**: Shared application modules targeting
    Android/JVM and WasmJS, split by layer
    ([module graph](docs/technical/client/modules.md)): the domain model
    (`:shared:app:domain`), the data layer and platform clients
    (`:shared:app:data`), and the UI state, ViewModels, and components that
    the mobile app and the web console share (`:shared:app`). Test fixtures
    for them and their dependents are in `shared/app/testing/`
    (`:shared:app:testing`).
-   **[`shared/mobile/`](shared/mobile/)**: Shared Mobile application module
    (`:shared:mobile`) targeting Android/JVM and WasmJS, with iOS
    opt-in (`-Pcollectone.ios=true`) until `shared/app` gets iOS targets.
    Contains mobile-specific local persistence (`data/`), domain services such
    as offline sync and GPS tracking (`services/`), and mobile Compose screens &
    ViewModels (`ui/`) shared identically by `androidApp` and `iosApp`. Exports
    the `AppMobile` XCFramework for Xcode.

### Platform Applications & Tools

-   **[`androidApp/`](androidApp/)**: Thin Android application entry point that
    hosts `MobileApp()` from `shared/mobile`.
-   **[`iosApp/`](iosApp/)**: Thin Xcode / SwiftUI application entry point that
    embeds `AppMobile.framework` from `shared/mobile`.
-   **[`shared/web/`](shared/web/)**: Web console module
    (`:shared:web`): the console's pages, `WebAppState`, and the
    `WebConsoleApp()` root, built on `shared/app`.
-   **[`webApp/`](webApp/)**: Collect One Web Console application (`wasmJs`) for
    survey administration, map management, and data exploration. A thin host
    for `WebConsoleApp()` from `shared/web`.
-   **[`mobileWebApp/`](mobileWebApp/)**: The mobile app (`MobileApp()`)
    running in the browser with Kotlin/Wasm, inside a simulated device
    workbench by default or full-window with `?chrome=0`, for testing without
    an Android or iOS build. The workbench lives in its `workbench/` library.
    The web console and the mobile app are independent Wasm apps, even when
    they're hosted on the same site.
-   **[`server/`](server/)**: Kotlin/JVM backend server serving the Collect One
    survey, organization, library, data, geo-data, and sync RPCs over native
    gRPC and gRPC-Web, plus in-process transactional invitation emails and FCM
    topic tickles. The web console and mobile apps sync from it; see its README
    for running it locally and deploying it to Cloud Run.
-   **[`devtools/launchpad/`](devtools/launchpad/)**: Browser page for starting,
    stopping, and watching the server, web console, and mobile app (plus the
    Spanner emulator) during local development.
-   **[`e2eTests/`](e2eTests/)**: Golden-screenshot integration tests that drive
    the production builds of the web console and the mobile app in headless
    Chromium against a local server.
-   **[`docs/`](docs/)**: Comprehensive architectural design and data model
    documentation.

## Mental Model & Terminology Mapping to XForms

### The Core Mental Model

-   **Tables = Current State (Persistent Master Data)**: Flat, stateful master
    datasets (`EntityDatasetDef` / `EntityRecord`) where each row represents a
    real-world object (site, plot, asset, or participant).
-   **Forms = Transactions / Events (Encounter Logs)**: Questionnaires
    (`FormDef`) filled out in the field. Completed submissions
    (`SubmissionRecord` / `RecordInstance`) are immutable event records
    preserving GPS, timestamps, and raw inputs.

### For Survey Organizers / Form Designers: Forms & Tables

Organizers define how a form interacts with master tables using standard XLSForm
syntax:

-   **Populate a Table (Create Record)**:
    -   *XForms Concept*: Form configured to create a new entity.
    -   *XLSForm Mapping*:
        -   `entities` sheet: `list_name` specified, `create_condition`
            (optional).
        -   `survey` sheet: Target fields use the `save_to` column to populate
            table attributes.
-   **Update a Table (Update Record)**:
    -   *XForms Concept*: Form configured to update an existing entity.
    -   *XLSForm Mapping*:
        -   `survey` sheet: Select question using
            `select_one_from_file <table_name>.csv`.
        -   `entities` sheet: `entity_id` set to the selected entity's ID.
        -   `survey` sheet: Updated fields mapped to table attributes via
            `save_to`.
-   **Reference a Table (Lookup / Read-Only)**:
    -   *XForms Concept*: Consuming an Entity List or external dataset without
        writing back.
    -   *XLSForm Mapping*:
        -   `survey` sheet: `select_one_from_file <table_name>.csv` used for
            choices/filtering, or pre-filling read-only `calculate` / `note`
            fields with `instance('<table_name>')/root/item[...]`. No `save_to`
            mapping.
-   **Log Only (Standard Survey)**:
    -   *XForms Concept*: Traditional standalone XForm.
    -   *XLSForm Mapping*: Standard `survey` and `choices` sheets only. No
        `entities` sheet.

### For Data Collectors (Map UI & Field Workflow)

The map layer drawer displays geospatial entity layers rather than nesting
layers under form menus:

-   **Map features**:
    -   *XForms Concept*: Geospatial Entity Lists (spatial master tables)
        attached to the survey.
    -   *Field Interaction*: Represents the target locations/features on the
        map. Tapping a site pin opens its current status, submission history,
        and launches available actions (e.g., `[ + Inspect Site ]`, `[ + Update
        Info ]`).
    -   *Why*: Avoids duplicating the layer across multiple forms that interact
        with the same site, enabling a natural "site-first" workflow without
        cluttering the map with raw form submission geometries.

## Local Configuration & API Keys

No API keys, Firebase configs, service account credentials, or project-specific
deployment targets are checked into this repository. Keep local settings in a
gitignored `local.properties` file at the repository root (or supply them as
environment variables or Gradle `-P<KEY>=<VALUE>` properties; precedence is
Gradle property → environment variable → `local.properties`).

### Example `local.properties`

```properties
# Backend endpoint for webApp, mobileWebApp, and androidApp
USER_DATA_SERVICE_URL=http://localhost:8080

# Mapbox public token (required for native maps, map labels, and Places search)
MAPBOX_ACCESS_TOKEN=pk.your_mapbox_public_token_here

# Firebase Authentication web app config (single-line JSON; omit for local dev fake sign-in)
FIREBASE_WEB_CONFIG={"apiKey":"...","authDomain":"...","projectId":"...","appId":"..."}

# Firebase Hosting preview deploy scripts (webApp/scripts/deploy-preview.sh, mobileWebApp/scripts/deploy-preview.sh)
FIREBASE_PROJECT_ID=your-gcp-project-id
FIREBASE_SA_EMAIL=firebase-adminsdk@your-gcp-project-id.iam.gserviceaccount.com

# Optional server integrations (also read from environment variables by server/)
EE_SERVICE_ACCOUNT_FILE=/path/to/earth-engine-service-account.json
EE_PROJECT_ID=your-gcp-project-id
BREVO_API_KEY=xkeysib-...
EMAIL_FROM=noreply@example.org
PUBLIC_WEB_URL=https://collectone.example.org
WHISP_API_KEY=your-whisp-api-key
```

### Reference of Configuration Keys

| Key | Used by | Purpose |
| :--- | :--- | :--- |
| `USER_DATA_SERVICE_URL` | `webApp`, `mobileWebApp`, `androidApp`, `iosApp` | Backend gRPC / gRPC-Web URL (`http://localhost:8080` locally; HTTPS URL in deployed builds). |
| `MOBILE_WEB_URL` | `webApp` | Optional mobile web app URL for non-production (`dev`/`staging`) builds. Shows a **Mobile** link in the web header and automatically redirects mobile browsers (bypass with `?desktop=1`). Defaults to `http://localhost:8093` in local `dev`; set to `none` to disable. |
| `MAPBOX_ACCESS_TOKEN` | `webApp`, `mobileWebApp`, `androidApp`, `iosApp` | Public Mapbox token (`pk.…`) for map rendering, labels, and Places search. |
| `FIREBASE_WEB_CONFIG` | `webApp`, `mobileWebApp` | Single-line Firebase web app JSON config for Google sign-in; omit or set `none` to use local fake sign-in. |
| `DEV_SIGN_IN`, `APP_ENV`, `PASSLIST` | `webApp`, `mobileWebApp`, `server` | Optional build/runtime toggles: dev role switcher (`true`/`false`), explicit environment (`dev`/`staging`/`prod`), and comma-separated email/domain allowlist. |
| `FIREBASE_PROJECT_ID`, `FIREBASE_SA_EMAIL` | `webApp/scripts/deploy-preview.sh`, `mobileWebApp/scripts/deploy-preview.sh`, `server` | Target GCP/Firebase project ID (also `--project`) and service account email (also `--sa-email`) for preview deploys; `FIREBASE_PROJECT_ID` also configures Firebase token verification and FCM on `server/`. |
| `AUTH_MODE`, `ADMIN_EMAILS` | `server` | Server auth mode (`dev` default, or `firebase` requiring Firebase ID tokens) and comma-separated admin emails. |
| `EE_SERVICE_ACCOUNT_FILE` / `EE_SERVICE_ACCOUNT_JSON`, `EE_PROJECT_ID` | `server` | Google Earth Engine service account key (file path or raw JSON) and GCP project ID for satellite imagery tiles and time-series analytics. |
| `BREVO_API_KEY`, `EMAIL_MODE`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `PUBLIC_WEB_URL` | `server` | Transactional invitation email delivery (`brevo` when `BREVO_API_KEY` is set, otherwise `log` to `/debug/messages`) and base URL for invite links. |
| `WHISP_API_KEY`, `WHISP_API_URL`, `WHISP_PREFIX` | `server` | Open Foris Whisp API credentials and endpoint for asynchronous geospatial entity enrichment. |
| `AGSTACK_REGISTRY_URL`, `AGSTACK_API_KEY`, `AGSTACK_AUTH_TOKEN` | `server` | Optional AgStack GeoID registry credentials. |
| `SPANNER_PROJECT_ID`, `SPANNER_INSTANCE_ID`, `SPANNER_DATABASE_ID` | `server` | Cloud Spanner target (omit to use the in-memory store, or set `SPANNER_EMULATOR_HOST` for the local emulator; see [`server/README.md`](server/README.md)). |

Platform-specific setup:
- **Android**: place your Firebase `google-services.json` (gitignored) in `androidApp/` if using Firebase Auth; see [`androidApp/README.md`](androidApp/README.md).
- **iOS**: pass `--team-id <APPLE_TEAM_ID>` to `iosApp/scripts/build-ipa.sh` for signed builds; see [`iosApp/README.md`](iosApp/README.md).
- **Server**: see [`server/README.md`](server/README.md) for the full list of runtime environment variables and Cloud Run deployment instructions.

## Building

The repository is a single Gradle build: run `./gradlew` from the repository
root. `./gradlew jvmTest` runs the JVM tests of every module,
`./gradlew detekt` runs detekt in the modules that apply it, and
`./gradlew :<module>:<task>` (for example `./gradlew :server:run`) runs a task
in one module. Shared build configuration lives in the convention plugins in
[`build-logic/`](build-logic/).

## Test Coverage

Kover measures the coverage of the JVM tests. `./gradlew :koverHtmlReportJvmTests`
runs the tests and writes one report merging every covered module (listed in the
root [`build.gradle.kts`](build.gradle.kts)) to `build/reports/kover/htmlJvmTests/`;
`./gradlew :<module>:koverHtmlReportJvmTests` reports one module. (The tasks
without the `JvmTests` suffix also run the Android unit tests.) Generated code (Wire
messages, the SQLDelight database, Compose resource accessors) is left out.
`./gradlew :koverVerifyJvmTests` fails when the merged line coverage drops below the
minimum set in the root `build.gradle.kts`; raise the minimum when coverage goes
up.

## Continuous Integration

[`.github/workflows/check.yml`](.github/workflows/check.yml) runs on every push
to `main` and every pull request, drafts included: formatting and license
checks, the JVM tests with the merged coverage report and `:koverVerifyJvmTests`,
detekt, and the server's Spanner tests. Each run uploads the coverage report as
the `coverage-report` artifact and shows the totals in the run summary.

[`.github/workflows/goldens.yml`](.github/workflows/goldens.yml) runs the
Playwright golden-screenshot tests in [`e2eTests/`](e2eTests/), which take
longer: on pushes to `main`, and on pull requests once they are ready for
review (marked ready, and on every later push). To run them on a draft, add the
`run-goldens` label; to run them on any branch, start the workflow from the
Actions tab.

## Running Core Library Tests

From the repository root:

```bash
# Run JVM unit and round-trip tests
./gradlew :shared:core:jvmTest

# Compile JS and WasmJS targets
./gradlew :shared:core:compileKotlinJs :shared:core:compileKotlinWasmJs
```

## Running UI Golden-Screenshot Tests

The web console (`shared/web`) and mobile app (`shared/mobile`) include headless Compose Desktop golden-screenshot tests that render full app screens in both light and dark themes and verify them against checked-in golden images (`src/jvmTest/resources/goldens/`).

```bash
# Run web console golden tests:
./gradlew :shared:web:jvmTest --tests org.openforis.collectone.v2.web.ThemeScreenshotTest

# Run mobile app golden tests:
./gradlew :shared:mobile:jvmTest --tests org.openforis.collectone.v2.mobile.ThemeScreenshotTest

# Regenerate golden images when UI changes intentionally:
UPDATE_GOLDENS=true ./gradlew :shared:web:jvmTest --tests org.openforis.collectone.v2.web.ThemeScreenshotTest
UPDATE_GOLDENS=true ./gradlew :shared:mobile:jvmTest --tests org.openforis.collectone.v2.mobile.ThemeScreenshotTest
```
