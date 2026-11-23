<!--
  Copyright 2026 The Collect One Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

# Collect One Web Console Module

`:shared:web` contains the Collect One web console: its pages
(surveys, organizations, the Impact page with organizations' published numbers,
dashboard, survey editor, form editor, and survey creation wizard), its session
state (`WebAppState`), and its root composable
(`WebConsoleApp()`). It's built on the domain, data, and shared UI in
[`../app/`](../app/), and hosted by [`../../webApp/`](../../webApp/).

The module targets WasmJS, plus JVM for the headless Compose UI tests. It has
no Android target: nothing that builds for Android depends on it.

The pages and their behavior are described in
[`docs/technical/client/web-console.md`](../../docs/technical/client/web-console.md).

## Modules and Packages (`org.openforis.collectone.v2.web`)

The console is split into Gradle modules (see
[`docs/technical/client/modules.md`](../../docs/technical/client/modules.md#web-console-modules)).
Packages keep their names across modules.

-   **`:shared:web`** (this directory), the console shell: `WebConsoleApp.kt`
    (`WebConsoleApp()`, theme and routing), the navigation (`navigation/`),
    `ui/WebAppState` (the session state and actions only the web console uses,
    and the store of the ViewModels its pages share), `ui/WebSessionViewModels.kt`
    (`ProvideWebSession`, `sessionViewModel()`), the dashboard
    (`ui/dashboard/`, with `ui/datacollection/` and `ui/map/`), the header
    (`ui/header/`), onboarding (`ui/onboarding/`), and `di/` (`webModules`,
    `webViewModelModule`, which includes each feature's Koin module).
-   **[`domain/`](domain/)** (`:shared:web:domain`): domain models and use
    cases only the web console uses (organizations directory, impact
    summaries, survey outcome prompts, sample plot generation, map layer
    import, survey editor mutations and wizard rules, and the organization,
    library, collaborator, concept search, survey lifecycle, and impact use
    cases). Pure Kotlin; builds only on `:shared:app:domain`.
-   **[`resources/`](resources/)** (`:shared:web:resources`): the console's
    strings in all 9 locales and their generated `Res` accessors.
-   **[`ui-common/`](ui-common/)** (`:shared:web:ui-common`): shared
    composables and browser bridges (`ui/common/`), the text mappers of the web
    domain (`ui/text/`), and the survey outcome card (`ui/surveyoutcome/`).
-   **Feature modules**, each with its ViewModel, UI state, views, tests, and
    Koin module (in `di/`): [`formeditor/`](formeditor/),
    [`surveyeditor/`](surveyeditor/), [`wizard/`](wizard/),
    [`surveys/`](surveys/), [`organization/`](organization/),
    [`impact/`](impact/) (with the impact PDF report in `client/pdf/`), and
    [`timeseries/`](timeseries/).

`ModuleGraphTest` (in `jvmTest`) pins what each web module builds on.
`LayerDependencyArchitectureTest` checks every web module: no `app.data`
imports, a pure `domain/`, views that reach neither `app.client` nor importers,
each ViewModel and UI state in its feature's package, and no imports of the
mobile app or the workbench.

The `:shared:web:testing` subproject holds test fixtures for the web modules'
tests (`SurveyEditorTestSupport`, `SurveyLifecycleFixture`); it builds on
`:shared:web:surveyeditor`.

## Building and Testing

Run these from the repository root (the repository is a single Gradle build).

```bash
# JVM unit and Compose UI tests of the shell (a feature module's tests run with
# its own task, e.g. :shared:web:surveyeditor:jvmTest):
./gradlew :shared:web:jvmTest

# Compile the Wasm target:
./gradlew :shared:web:compileKotlinWasmJs
```

### Golden Screenshot Tests

The web console includes Compose golden-screenshot tests in `src/jvmTest/kotlin/org/openforis/collectone/v2/web/ThemeScreenshotTest.kt` that render 15 key screens (plus component sheets) in both light and dark themes and compare them against checked-in golden images in [`src/jvmTest/resources/goldens/`](src/jvmTest/resources/goldens/).

To run and verify the goldens:

```bash
# Run all web tests including goldens:
./gradlew :shared:web:jvmTest

# Run only the golden screenshot test:
./gradlew :shared:web:jvmTest --tests org.openforis.collectone.v2.web.ThemeScreenshotTest
```

When an intentional UI change alters how screens look, regenerate the golden images before committing:

```bash
# Regenerate all web golden images:
UPDATE_GOLDENS=true ./gradlew :shared:web:jvmTest --tests org.openforis.collectone.v2.web.ThemeScreenshotTest
```

To export clean PNG copies to an arbitrary directory for visual inspection or design reviews:

```bash
THEME_SCREENSHOT_DIR=/tmp/web-screenshots ./gradlew :shared:web:jvmTest --tests org.openforis.collectone.v2.web.ThemeScreenshotTest
```

## Dependencies

-   [`../app/`](../app/) (`:shared:app`)
-   [`../core/`](../core/), [`../ui/`](../ui/), and [`../map/`](../map/)
