<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Collect One Shared App Module

`org.groundplatform.v2:app` holds the code that the mobile app and the web
console share: the domain model, the data layer, device and network clients,
and the UI pieces both apps use. It targets Android, JVM, and WasmJS. iOS
targets will be added when iOS work resumes.

Only code both apps use belongs here. Web-only domain models, use cases, and
UI live in [`../web/`](../web/) and mobile-only state in
[`../mobile/`](../mobile/) (or the [workbench](../../mobileWebApp/workbench/)
when only the mobile workbench uses it), so each app's build includes only
what it uses. The exceptions are the data layer (one local store shared by
both apps, which only this module may import) and the small hooks the
workbench sets on any session (`SampleDataRepository`, `SyncStatusSimulator`).

## Package Structure (`org.groundplatform.v2.app`)

-   **`domain/`**: Domain models, repository interfaces, and use cases. Pure
    Kotlin with no Compose, data, or platform imports.
-   **`data/`**: Repository implementations, data sources (local store, remote,
    device), and sample data (`data/seed/`). Only `data/` and `di/` may import
    `data.*`.
-   **`client/`**: Platform clients (auth, location, network, PDF export, places
    search), with `expect`/`actual` implementations in `src/<platform>Main/`.
-   **`di/`**: The composition root (`AppDataHolder`): the local store,
    repositories, and the use cases both apps use. Each app wires its own
    use cases from these repositories (`WebUseCases` in `shared/web`).
-   **`ui/AppSession.kt`**: The base class of `MobileAppState` and
    `WebAppState`, holding only the session state and actions both apps use.
-   **`ui/`**: Shared presentation code: UI state (`ui/state/`), shared
    ViewModels (`ui/viewmodel/`), and components used by both apps (`ui/common/`,
    `ui/map/`, `ui/datacollection/`).

`LayerDependencyGuardrailTest` and `SampleDataGuardrailTest` (in `jvmTest`)
enforce these layer rules.

## Local Data Store

How the store, repositories, sample data, and feature view models fit
together is described in
[`docs/technical/client/local-data-store.md`](../../docs/technical/client/local-data-store.md).

## PDF Reports

Map features and submissions can be exported as PDF reports. The PDFs are
generated on the device, in shared Kotlin (`client/pdf/`), so they work offline
and look the same on web and mobile.

-   **Mobile**: **Share PDF** (entity actions row, QR code dialog, and the
    submission view) opens a sheet with the generated file (page count and
    size). Tap the file to preview it. **Share** opens the system share sheet
    (WhatsApp, Gmail, Drive, Bluetooth, and others) through the Web Share API.
    Where the browser can't share files, the sheet offers **Download** instead.
-   **Web dashboard**: **Download PDF** saves the file right away, with no
    share sheet. It's in the overflow menu (⋮) of the map feature card and of an
    opened submission, and in the QR code dialog. A snackbar confirms the
    download.
-   **Map feature report**: Workflow status chip (`marker-symbol` drawn as a
    vector shape on the `marker-color`), details, a location figure with a north arrow,
    scale bar, and vertex coordinates, properties (references show the related
    record's label), and every submission with its responses.
-   **Submission report**: Map feature or coordinates, data collector, time,
    form version, and every response, grouped by XForms group. Reports leave
    out upload (sync) status and submission counts.
-   **Implementation**: `PdfDocumentWriter` is a small PDF 1.4 writer with no
    dependencies. It uses the standard Helvetica and Courier fonts, which don't
    need embedding. `PdfReportLayout` handles text wrapping, page breaks, and
    `Page n of N` footers. `RecordPdfReports` builds the two reports.
    `DataCollectionViewModel` generates them and hands them to
    `PdfExportClient` (`client/pdf/`), the only platform-specific code:
    `pdf-export-bridge.js` on web and the temp directory on the JVM.
-   **Receipt activity**: Each generated report records a **Receipt created**
    activity record, and sharing or saving it records **Receipt shared**
    ([impact measurement](../../docs/product/impact-measurement.md), "Data Was
    Used" events). A record holds the survey, its organization and Purpose
    Packs, and the map feature count and area (1 feature for a map feature
    report or a submission on one; 0 for a standalone submission). It holds no
    geometry or personal details. Records made offline stay on the device and
    upload with the next **Sync all** on the `Uploads` screen, which shows
    `N activity records waiting to sync` (and **Sync all**) while any are
    pending, even with an empty outbox. Previewing a PDF records nothing.
-   **Limitation**: The standard fonts only cover Windows-1252 (Western European)
    characters. Common symbols are replaced with ASCII (`≤` → `<=`, `📷` →
    `[photo]`); other scripts render as `?`. Embedding a Unicode font (for
    example Noto Sans) is the next step.

## User Data Service

The app syncs `LocalStore` from the
[user data service](../../server/userDataService/README.md) through
`GrpcSyncDataSource`, over gRPC-Web (`client/grpc/GrpcWebTransport.kt` and
`RemoteUserDataServices.kt`) on every platform. `AppDataHolder` takes the
service's base URL as `userDataServiceUrl`; when it's null, nothing is synced
(tests seed stores directly). The app entry points wrap the app in
`RequireUserDataService`, which shows setup instructions instead of starting
when `platformUserDataServiceUrl()` (`di/UserDataServiceConfig.kt`) finds no
URL. Sync failures appear in `AppDataHolder.userDataSyncState` and are shown
with `UserDataSyncErrorBanner`, whose Retry calls `retryUserDataSync()`.
The URL comes from:

-   **WasmJS:** `window.GROUND_USER_DATA_SERVICE_URL` from
    `user-data-service-config.js`. In development it's the page's own origin,
    because the dev server proxies the service set by `USER_DATA_SERVICE_URL`;
    production bundles bake in an absolute URL. It can't be set from the page
    URL.
-   **JVM:** the `groundplatform.userDataServiceUrl` system property or the
    `USER_DATA_SERVICE_URL` environment variable.
-   **Android:** the `groundplatform.userDataServiceUrl` system property, which
    `androidApp` sets from its `USER_DATA_SERVICE_URL` build setting.

## Test Fixtures (`testing/`)

The `:app-testing` subproject (`org.groundplatform.v2:app-testing`) contains
fixtures shared by this module's tests and by the tests of modules built on it:
the sample data (`data/seed/Sample*Data`, `GlobalLibrarySeedData`), seeded
in-memory stores (`seededStore()`, `sampleDataHolder()`, `runNow`), the
read-only `SeedUserDataService` fake with `sampleSyncDataSource()`, survey and
form editor samples, `DataCollectionFixture`, and `testScope()`.
Depend on it from `commonTest` only.

`UserDataSeedGenerator` builds the user data service's sample seed from the same
fixtures. After changing them, rewrite the service's seed snapshot with
`./gradlew jvmTest -PregenerateUserDataSeed`.

## Web Hosts

The WasmJS clients call JavaScript bridges in `src/wasmJsMain/resources/`
(`pdf-export-bridge.js`, `media-capture-bridge.js`). Each web host copies them
into its bundle and loads them from its `index.html`.

## Building and Testing

`./gradlew` here runs the Gradle wrapper from [`../ui/`](../ui/), so this
module doesn't check in its own `gradle-wrapper.jar`.

```bash
# JVM unit and Compose UI tests:
./gradlew jvmTest

# Compile every target:
./gradlew compileKotlinJvm compileKotlinWasmJs compileDebugKotlinAndroid
```

Rewrite the global library seed files (`shared/assets/library/*.textproto`)
from the Kotlin copy with `./gradlew jvmTest -PregenerateLibrarySeed`.

## Dependencies

-   [`../core/`](../core/) (`org.groundplatform.v2:protoforms`)
-   [`../ui/`](../ui/) (`org.groundplatform.v2:protoforms-ui`)
-   [`../map/`](../map/) (`org.groundplatform.v2:map`)
