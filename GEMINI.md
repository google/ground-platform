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

# Collect One Agent Rules & Guidelines

These rules govern all AI-assisted research, design, and code changes within Collect One. Follow them strictly when creating or modifying files in this directory and its subdirectories.

## Project Design & Architectural Reference

* **Primary Source of Truth**: Refer to [`docs/product/prd.md`](docs/product/prd.md) (and supporting specifications under [`docs/product/`](docs/product/) and [`docs/technical/client/architecture.md`](docs/technical/client/architecture.md)) for project-specific product requirements, domain terminology, and system architecture.
* **UX & Content Guidelines**: Follow the Collect One terminology, UX writing rules, and core mental model defined in [`docs/ux/content-guidelines.md`](docs/ux/content-guidelines.md) and [`docs/product/terminology.md`](docs/product/terminology.md). Strictly use Collect One / XForms & XLSForm-aligned terminology across all UI copy, comments, and code:
  * **Survey** (`SurveyDef`): Top-level organizational and ACL container.
  * **Forms** (`FormDef`): Questionnaires / encounter logs (replaces Ground 1.0 "Job").
  * **Data tables / Map layers** (`EntityDatasetDef`): Persistent master data / spatial datasets (replaces Ground 1.0 "Site").
  * **Map features** (`EntityRecord`): Real-world spatial objects rendered on the map (replaces Ground 1.0 "Site").
  * **Questions / Groups / Repeats / Notes**: Form tree elements (replaces Ground 1.0 "Task").
  * **Submissions** (`SubmissionRecord`): Immutable historical records preserving timestamps and inputs.
* **Core Product Principles**: Ensure all implementations uphold *Proportional Complexity* (simple by default, progressive disclosure for advanced features), *Offline-First Resilience* (100% disconnected field execution and atomic local persistence), and *Real-World Usability* (clear, forgiving interactions with high-contrast legibility). Detailed interaction patterns, form–table mappings, and voice/tone rules are documented in [`docs/ux/content-guidelines.md`](docs/ux/content-guidelines.md).

## Open-Source Purity & Confidentiality

* **Strictly Open Source**: Collect One is a fully public, open-source project released under the Apache 2.0 License.
* **No Proprietary Content**: Never include proprietary code, algorithms, internal libraries, confidential information, internal URLs/identifiers (e.g., internal shortlinks, internal bug IDs, employee LDAPs/handles, or internal endpoints), API keys/secrets, or non-public datasets in submitted code, comments, tests, or assets.
  * **Firebase and other client configs**: This includes Firebase web app configs and their API keys. Builds read them from `FIREBASE_WEB_CONFIG` (a Gradle property, environment variable, or `local.properties`), kept outside the repository.
* **Public Dependencies & Assets Only**: Use only publicly available, open-source libraries, standard specifications (XForms, XLSForm, GeoJSON, Protobuf), and public-domain/openly licensed test fixtures and assets.

## License Headers (`Apache 2.0`)

* **New Source Files**: Every newly created source, build, schema, script, or documentation file **must** begin with the standard Apache 2.0 license header using the **current calendar year**:
  ```kotlin
  /*
   * Copyright 2026 The Collect One Authors.
   *
   * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   * in compliance with the License. You may obtain a copy of the License at
   *
   *     https://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */
  ```
  *(Adapt the comment syntax—e.g., `<!-- ... -->` for Markdown/XML/HTML or `# ...` for shell/YAML/properties—to match the file type.)*
* **Quoting**: Use straight double quotes around `"License"` and `"AS IS"`, matching the canonical Apache 2.0 text and the rest of the Collect One repository.
* **Open the block with `/*`, never `/**`**: `/**` makes the header a KDoc comment that binds to the following `package` declaration and leaks the license text into generated API documentation.
* **Existing Files**: **Do not** update or alter the copyright year in existing files when modifying them. Preserve existing license headers as-is.

## Kotlin Multiplatform (KMP) Architecture

* **Shared Code in KMP**: All shared logic, data models, form evaluation, persistence, and shared UI **must** be written using **Kotlin Multiplatform** under [`shared/`](shared/):
  * [`shared/protos/`](shared/protos/): Protocol Buffer (`proto3`) schemas for forms, surveys, entities, submissions, and audit logs.
  * [`shared/core/`](shared/core/): Pure KMP runtime (`commonMain`) targeting JVM (consumed by both `:server` and Android), iOS, JS, and WasmJS (serialization, XPath/XForms evaluation, reactive form engine). Keep `commonMain` free of platform-specific imports and Android plugins.
  * [`shared/ui/`](shared/ui/): Shared Compose Multiplatform (`commonMain`) theme (`AppTheme`) and form/UI components targeting Android/JVM, iOS, JS, and WasmJS.
  * [`shared/map/`](shared/map/): Shared map rendering (Mapbox) used by both apps, with its geometry types (`LatLng`, `LngLatBounds`, `Geometry`) in the Compose-free [`shared/map/geometry/`](shared/map/geometry/) (`:shared:map:geometry`) that the domain builds on.
  * [`shared/app/`](shared/app/): Shared application core (`commonMain`) targeting Android/JVM and WasmJS, split by layer, and its presentation by feature, into modules ([`docs/technical/client/modules.md`](docs/technical/client/modules.md)): [`shared/app/domain/`](shared/app/domain/) (`:shared:app:domain`: domain models, repository interfaces, use cases; pure Kotlin), [`shared/app/data/`](shared/app/data/) (`:shared:app:data`: repositories, data sources, the SQLDelight database, sync, platform clients, and their Koin modules), [`shared/app/resources/`](shared/app/resources/) (`:shared:app:resources`: the UI strings of all 9 locales), [`shared/app/ui-common/`](shared/app/ui-common/) (`:shared:app:ui-common`: text mappers, shared composables, navigation models, ViewModel helpers), the feature modules [`shared/app/map/`](shared/app/map/), [`shared/app/datacollection/`](shared/app/datacollection/), [`shared/app/dashboard/`](shared/app/dashboard/), [`shared/app/onboarding/`](shared/app/onboarding/), [`shared/app/settings/`](shared/app/settings/), and [`shared/app/listsearch/`](shared/app/listsearch/) (each feature's ViewModel, UI state, views, and Koin module), and `shared/app/` itself (`:shared:app`: the session shell and the composition root). Packages are the same across modules; put new code in the lowest of them that can hold it, and a feature's ViewModel, UI state, and views in its `ui/<feature>/` package.
  * [`shared/web/`](shared/web/): The web console (`commonMain`): pages, `WebAppState`, and the `WebConsoleApp()` root hosted by `webApp`, with its web-only domain models and use cases in [`shared/web/domain/`](shared/web/domain/) (`:shared:web:domain`, pure Kotlin on `:shared:app:domain`). Targets JVM (for tests) and WasmJS.
  * [`shared/mobile/`](shared/mobile/): The mobile app (`commonMain`): screens, `MobileAppState`, and the `MobileApp()` root shared by every mobile host. Targets Android/JVM and WasmJS; iOS is opt-in (`-Pcollectone.ios=true`) until `shared/app` gets iOS targets.
  * **Module placement**: Put code in the lowest module every user of it depends on. Code only the web console uses belongs in `shared/web`, code only the mobile app uses in `shared/mobile` (or in [`mobileWebApp/workbench/`](mobileWebApp/workbench/) when only the workbench uses it), and only code both apps use in `shared/app`. The web console and the mobile app are independent Wasm apps and never import each other.
* **Thin Platform Wrappers**: Keep [`androidApp/`](androidApp/), [`iosApp/`](iosApp/), [`webApp/`](webApp/), and [`mobileWebApp/`](mobileWebApp/) (the mobile app in the browser, for testing, with its device `workbench/` library) as thin platform entry points and host integrations. Do not duplicate business logic, state machines, validation, or shared UI components across platform targets; use `expect`/`actual` abstractions only where native OS capabilities are required.
* **iOS is opt-in**: iOS targets (`iosApp/` and the `ios*` source sets) build only with `-Pcollectone.ios=true` and aren't built in CI for now. Keep iOS source sets compiling by intent, but don't block changes on them.
* **Developer Tools**: [`devtools/launchpad/`](devtools/launchpad/) runs the local development launchpad. Keep developer tooling out of the shipped apps.

## User Interface: Material Design 3 & Compose Multiplatform

* **Material Design 3 (M3)**: All user interfaces across mobile and web must follow **Material Design 3** guidelines (`androidx.compose.material3.*`).
* **Unmodified Compose Multiplatform**: Use standard **Compose Multiplatform** and Material 3 components out-of-the-box with minimal or no custom structural/behavioral modifications. Prefer standard M3 building blocks (`Scaffold`, `TopAppBar`, `Card`, `OutlinedTextField`, `Button`, `FilterChip`, `ModalBottomSheet`, etc.) over bespoke custom-drawn widgets.
* **Global Theming Over Per-Component Styling**:
  * Always wrap UI surfaces in [`AppTheme`](shared/ui/src/commonMain/kotlin/org/openforis/collectone/v2/core/forms/ui/AppTheme.kt) and reference semantic tokens from `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes`.
  * **Prefer global theme definitions** over specifying ad-hoc colors (`Color(0x...)`), custom typography styles, or visual overrides on individual components. When a visual change is needed across components, update the centralized theme in `shared/ui/` rather than passing inline style overrides to individual composables.
  * **Theme colors by token name**: A color from the light or dark scheme (including the `Medium` and `High` contrast schemes) must be referenced by its `MaterialTheme.colorScheme` token name (or, outside composition, the matching role of `AppLight*ColorScheme` / `AppDark*ColorScheme`), never re-typed as a literal. The same applies to the M3 fixed roles (`LocalAppFixedColors` / `AppFixedColors`) and the Collect One static colors (`AppStaticColors.Orange`, `Red`, `Purple`, `Blue`, `Green`, `Yellow`) in `AppTheme.kt`. Domain code that stores colors as `#RRGGBB` strings keeps them equal to these tokens through tests. Other one-off colors that are not theme roles (accents such as the orange status accents, choice and data-series colors, map overlays, brand logos, illustrations, and other static graphics) may keep literal values. `ThemeColorTokenGuardrailTest` in `shared/ui` fails on new literals that equal a theme color; its allowlist may only shrink.
  * **Text styles by role**: Use a `MaterialTheme.typography` role directly instead of an inline `TextStyle` or a `copy()` that only restates the role's own values.

## ProtoForms Schema & XForms Compatibility Boundary

### Normative references

ProtoForms ([`shared/protos/forms/`](shared/protos/forms/), package `openforis.collectone.v1.forms`) is a Protocol Buffer encoding of the XForms data model. When adding or changing anything in that package, consult these in order of precedence:

1. **[ODK XForms Specification](https://getodk.github.io/xforms-spec/)** — the primary target. This is the de facto standard for field data collection: a pragmatic subset of W3C XForms plus widely-implemented extensions (`jr:` / `odk:` namespaces, `itemset`, `constraint`/`jr:constraintMsg`, `jr:preload`, `setvalue`/`setgeopoint`, entities).
2. **[ODK Entities](https://docs.getodk.org/entities-intro/)** — the entity/dataset model (`entities` block, `save_to`, `entity_id`, create/update conditions, offline branch/trunk versioning).
3. **[XLSForm](https://xlsform.org/)** — the authoring format most form designers actually use. Anything expressible in XLSForm should survive the round trip into ProtoForms and back.
4. **[OpenRosa](https://docs.getodk.org/openrosa/)** — form list, form submission, and metadata (`<orx:meta>`, `instanceID`, `deviceID`) conventions.
5. **[W3C XForms 1.0](https://www.w3.org/TR/xforms/)** and **[XPath 1.0](https://www.w3.org/TR/1999/REC-xpath-19991116/)** — the underlying model and expression language. Use these to resolve semantics the ODK spec leaves unstated (type coercion, node-set comparison, axis proximity, `round()`/`substring()` edge cases).

### Compatibility, not purity

* **Interoperate with real implementations.** The bar is that a form authored in XLSForm, served by **ODK Central**, and filled in **ODK Collect** or **Enketo** round-trips through ProtoForms without losing information or changing meaning. Where the W3C spec and what **JavaRosa** actually does diverge, **match the real-world behavior** and leave a comment explaining the divergence and citing both sources.
* **Widely-implemented extensions belong in ProtoForms.** Constructs in the `jr:` / `odk:` namespaces, and ODK Entities, are part of the interoperable surface even though they are not in W3C XForms. Representing them here is correct.
* **Collect One-specific constructs stay out.** Anything meaningful only to Collect One — map layer configuration, survey ACLs, sync bookkeeping, UI preferences, launch behavior — belongs in [`shared/protos/survey/`](shared/protos/survey/) (`SurveyDef`, `FormLaunchConfig`, `MapConfig`), [`shared/protos/data/`](shared/protos/data/) (`SubmissionRecord`, `EntityRecord`, `AuditRecord`), or application-level KMP models. The test to apply is **not** "is this in the W3C spec?" but **"would another XForms tool understand this, or is it only meaningful to Collect One?"**
* **Round-trip fidelity is the acceptance criterion.** New schema surface needs a round-trip test in [`shared/core/src/commonTest/.../serialization/`](shared/core/src/commonTest/kotlin/org/openforis/collectone/v2/core/forms/serialization/) proving XML → proto → XML preserves the construct. Unrecognized attributes and elements should be preserved rather than dropped.
* **Cite the spec in code.** When implementing spec-defined behavior, reference the specific section in a comment (e.g. `// XPath 1.0 section 4.4: ties round toward positive infinity`) so the next reader can check the implementation against the source.

## Code Formatting (`ktformat` & `buf`)

* **Kotlin Code (`ktformat`)**: All Kotlin source and Gradle script files (`.kt`, `.kts`) **must** be formatted using `ktformat` (`ktfmt` / Google style with 2-space indentation). Run `ktformat` on any new or modified Kotlin files before completing changes. The canonical check is [`scripts/ktfmt.sh`](scripts/ktfmt.sh), which pins the ktfmt version and runs across the whole repository: run `scripts/ktfmt.sh --fix` to format and `scripts/ktfmt.sh` to verify. CI and `scripts/ktfmt.sh` fail on unformatted files.
* **Protocol Buffers (`buf`)**: All `proto3` schema files (`.proto` under [`shared/protos/`](shared/protos/)) **must** be formatted using `buf` (`buf format -w`). Run `buf format` whenever creating or modifying `.proto` definitions. The canonical check is [`scripts/buf-format.sh`](scripts/buf-format.sh), which pins the buf version: run `scripts/buf-format.sh --fix` to format and `scripts/buf-format.sh` to verify. CI and `scripts/buf-format.sh` fail on unformatted files.
* **Static analysis (`detekt`) & coverage (`Kover`)**: `server`, `shared/core`, the `shared/app` modules (`:shared:app`, `:shared:app:domain`, `:shared:app:data`, `:shared:app:ui-common`, `:shared:app:map`, `:shared:app:datacollection`, `:shared:app:dashboard`, `:shared:app:onboarding`, `:shared:app:settings`, `:shared:app:listsearch`), and `shared/web` (with `:shared:web:domain`) apply detekt with the shared rules in [`config/detekt/detekt.yml`](config/detekt/detekt.yml). Run `./gradlew detekt` from the repository root (or `./gradlew :server:detekt` for one module); it fails on new findings only, because existing ones are listed in each module's `detekt-baseline.xml`. The `collectone.detekt` convention plugin in [`build-logic/`](build-logic/) configures it. Fix new findings rather than adding them to the baseline. Kover (the `collectone.kover` convention plugin, which also excludes generated code) measures the JVM tests of every module with tested logic; the root project merges them, so `./gradlew :koverHtmlReportJvmTests` (or `:koverXmlReportJvmTests`) writes one report to `build/reports/kover/` and `./gradlew :<module>:koverHtmlReportJvmTests` reports one module. Use the `JvmTests` tasks: the ones without the suffix also run the Android unit tests, which CI doesn't run. New modules with tests apply `collectone.kover`, which adds them to the root merged report. CI runs `:koverVerifyJvmTests`, which fails when the merged line coverage drops below the minimum set there; when a change raises coverage, raise the minimum to the new line coverage rounded down, minus 1. The Playwright goldens in [`e2eTests/`](e2eTests/) run in [`.github/workflows/goldens.yml`](.github/workflows/goldens.yml) on pull requests that are ready for review (or labeled `run-goldens`) and on `main`, not on drafts.
* **Dependency versions & shared build configuration**: Every module reads plugin and library versions from the shared catalog [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Add or bump versions there, never inline in a `build.gradle.kts`. The convention plugins in [`build-logic/`](build-logic/) hold the Gradle configuration modules share (targets, Android SDK levels, detekt, Kover, wasm app bundling); put configuration shared by modules there instead of copying it between `build.gradle.kts` files.

