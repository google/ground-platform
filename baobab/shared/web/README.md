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

The module targets WasmJS, plus JVM for the headless Compose UI tests and
Android so the prototype host can still build its Android target.

## Package Structure (`org.groundplatform.v2.web`)

-   **`WebConsoleApp.kt`**: `WebConsoleApp()` (theme, URL hash routing) and
    `WebConsolePages()` (the page switch, also used by the prototype host).
-   **`ui/`**: `WebAppState`, `WebPage`, the pages by feature (`dashboard/`,
    `organization/`, `surveyeditor/`, `formeditor/`, `wizard/`, `impact/`,
    `onboarding/`, `datacollection/`), the header (`common/WebTopToolbar.kt`),
    and the console's ViewModels and UI state (`viewmodel/`, `state/`).

`LayerDependencyGuardrailTest` (in `jvmTest`) keeps this module from importing
`app.data`, from reaching `app.client` outside ViewModels, and from importing
the mobile app or the workbench.

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
