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

# Ground 2.0 Web Console Module

`org.groundplatform.v2:web` contains the Ground 2.0 web console: its pages
(surveys, organizations, dashboard, survey editor, form editor, survey creation
wizard, and impact views), its state (`WebAppState`, with `WebPage` routing),
and its root composable (`WebConsoleApp()`). It's built on the domain, data, and
shared UI in [`../app/`](../app/), and hosted by [`../../webApp/`](../../webApp/).

The module targets WasmJS, plus JVM for the headless Compose UI tests. It has
no Android target: nothing that builds for Android depends on it.

The pages and their behavior are described in
[`docs/technical/client/web-console.md`](../../docs/technical/client/web-console.md).

## Package Structure (`org.groundplatform.v2.web`)

-   **`WebConsoleApp.kt`**: `WebConsoleApp()` (theme, URL hash routing) and
    `WebConsolePages()` (the page switch).
-   **`domain/`**: Domain models and use cases only the web console uses
    (organizations directory, impact summaries, survey outcome prompts, sample
    plot generation, map layer import, survey editor mutations and wizard
    rules, and the organization, library, collaborator, concept search, survey
    lifecycle, and impact use cases). Pure Kotlin, like `app`'s `domain/`.
-   **`client/`**: Web-only clients (the impact PDF report).
-   **`di/`**: `WebUseCases`, which wires the web-only use cases from the
    repositories in `app`'s `AppDataHolder`.
-   **`ui/`**: `WebAppState` (with the session state and actions only the web
    console uses), `WebPage`, the pages by feature (`dashboard/`,
    `organization/`, `surveyeditor/`, `formeditor/`, `wizard/`, `impact/`,
    `onboarding/`, `datacollection/`), the header (`common/WebTopToolbar.kt`),
    the console's ViewModels and UI state (`viewmodel/`, `state/`), and
    web-only components (`common/`, `map/`) with their `expect`/`actual`
    platform pieces in `src/<platform>Main/`.

`LayerDependencyGuardrailTest` (in `jvmTest`) keeps this module from importing
`app.data`, keeps `domain/` free of outer layers, keeps views from reaching
`app.client`, and keeps the module from importing the mobile app or the
workbench.

The `:web-testing` subproject holds test fixtures for this module's tests
(`SurveyEditorTestSupport`, `SurveyLifecycleFixture`).

## Building and Testing

`./gradlew` here runs the Gradle wrapper from [`../ui/`](../ui/).

```bash
# JVM unit and Compose UI tests:
./gradlew jvmTest

# Compile the Wasm target:
./gradlew compileKotlinWasmJs
```

## Dependencies

-   [`../app/`](../app/) (`org.groundplatform.v2:app`)
-   [`../core/`](../core/), [`../ui/`](../ui/), and [`../map/`](../map/)
