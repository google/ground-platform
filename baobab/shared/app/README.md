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

# Ground 2.0 Shared App Module

`org.groundplatform.v2:app` holds the code that the mobile app and the web
console share: the domain model, the data layer, device and network clients,
and the UI pieces both apps use. It targets Android, JVM, and WasmJS. iOS
targets will be added when iOS work resumes.

## Package Structure (`org.groundplatform.v2.app`)

-   **`domain/`**: Domain models, repository interfaces, and use cases. Pure
    Kotlin with no Compose, data, or platform imports.
-   **`data/`**: Repository implementations, data sources (local store, remote,
    device), and sample data (`data/seed/`). Only `data/` and `di/` may import
    `data.*`.
-   **`client/`**: Platform clients (auth, location, network, PDF export, places
    search), with `expect`/`actual` implementations in `src/<platform>Main/`.
-   **`di/`**: The composition root (`AppDataHolder`) and the shared `AppSession`
    that the app states are built on.
-   **`ui/`**: Shared presentation code: UI state (`ui/state/`), shared
    ViewModels (`ui/viewmodel/`), and components used by both apps (`ui/common/`,
    `ui/map/`, `ui/datacollection/`).

`LayerDependencyGuardrailTest` and `SampleDataGuardrailTest` (in `jvmTest`)
enforce these layer rules.

## Test Fixtures (`testing/`)

The `:app-testing` subproject (`org.groundplatform.v2:app-testing`) contains
fixtures shared by this module's tests and by the tests of modules built on it:
seeded in-memory stores (`seededStore()`, `runNow`), survey and form editor
samples, `DataCollectionFixture`, `SurveyLifecycleFixture`, and `testScope()`.
Depend on it from `commonTest` only.

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
