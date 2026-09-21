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

# Ground 2.0 (`baobab/`) Agent Rules & Guidelines

These rules govern all AI-assisted research, design, and code changes within `baobab/`. Follow them strictly when creating or modifying files in this directory and its subdirectories.

## 1. Project Design & Architectural Reference

* **Primary Source of Truth**: Refer to [`docs/design/design.md`](docs/design/design.md) (and supporting specifications under [`docs/design/`](docs/design/) and [`docs/model/`](docs/model/)) for project-specific product requirements, domain terminology, and system architecture.
* **Domain Terminology**: Use Ground 2.0 / XLSForm-aligned terminology consistently across code, comments, and UI:
  * **Survey** (`SurveyDef`): Top-level organizational and ACL container.
  * **Form** (`FormDef`): Hierarchical XLSForm-aligned schema (replaces Ground 1.0 "Job").
  * **Question** / **Group** / **Repeat** / **Note**: Form tree elements (replaces Ground 1.0 "Task").
  * **Submission** (`SubmissionRecord` / `RecordInstance`): Versioned data collection record.
  * **Entity Dataset** & **Entity** (`EntityDatasetDef` / `EntityRecord`): Tabular or geospatial lookup/longitudinal entities (replaces Ground 1.0 "Site").
* **Core Product Principles**: Ensure all implementations uphold *Proportional Complexity* (simple by default, progressive disclosure for advanced features), *Offline-First Resilience* (100% disconnected field execution and atomic local persistence), and *Real-World Usability* (clear, forgiving interactions with high-contrast legibility).

## 2. Open-Source Purity & Confidentiality

* **Strictly Open Source**: Ground 2.0 is a fully public, open-source project released under the Apache 2.0 License.
* **No Proprietary Content**: Never include proprietary code, algorithms, internal libraries, confidential information, internal URLs/identifiers (e.g., internal shortlinks, internal bug IDs, employee LDAPs/handles, or internal endpoints), API keys/secrets, or non-public datasets in submitted code, comments, tests, or assets.
* **Public Dependencies & Assets Only**: Use only publicly available, open-source libraries, standard specifications (ODK XForms, XLSForm, GeoJSON, Protobuf), and public-domain/openly licensed test fixtures and assets.

## 3. License Headers (`Apache 2.0`)

* **New Source Files**: Every newly created source, build, schema, script, or documentation file **must** begin with the standard Apache 2.0 license header using the **current calendar year**:
  ```kotlin
  /*
   * Copyright 2026 The Ground Authors.
   *
   * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
   * in compliance with the License. You may obtain a copy of the License at
   *
   *     https://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an 'AS IS' BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */
  ```
  *(Adapt the comment syntax—e.g., `<!-- ... -->` for Markdown/XML/HTML or `# ...` for shell/YAML/properties—to match the file type.)*
* **Existing Files**: **Do not** update or alter the copyright year in existing files when modifying them. Preserve existing license headers as-is.

## 4. Kotlin Multiplatform (KMP) Architecture

* **Shared Code in KMP**: All shared logic, data models, form evaluation, persistence, and shared UI **must** be written using **Kotlin Multiplatform** under [`shared/`](shared/):
  * [`shared/protos/`](shared/protos/): Protocol Buffer (`proto3`) schemas for forms, surveys, entities, submissions, and audit logs.
  * [`shared/core/`](shared/core/): Pure KMP runtime (`commonMain`) targeting Android/JVM, iOS, JS, and WasmJS (serialization, XPath/XForms evaluation, reactive form engine). Keep `commonMain` free of platform-specific imports.
  * [`shared/ui/`](shared/ui/): Shared Compose Multiplatform (`commonMain`) theme (`GroundTheme`) and form/UI components targeting Android/JVM, iOS, JS, and WasmJS.
  * [`shared/mobile/`](shared/mobile/): Shared mobile application core (`commonMain`) targeting Android and iOS (local persistence, offline sync, GNSS tracking, mobile screens, and ViewModels).
* **Thin Platform Wrappers**: Keep [`androidApp/`](androidApp/), [`iosApp/`](iosApp/), and [`webApp/`](webApp/) as thin platform entry points and host integrations. Do not duplicate business logic, state machines, validation, or shared UI components across platform targets; use `expect`/`actual` abstractions only where native OS capabilities are required.

## 5. User Interface: Material Design 3 & Compose Multiplatform

* **Material Design 3 (M3)**: All user interfaces across mobile and web must follow **Material Design 3** guidelines (`androidx.compose.material3.*`).
* **Unmodified Compose Multiplatform**: Use standard **Compose Multiplatform** and Material 3 components out-of-the-box with minimal or no custom structural/behavioral modifications. Prefer standard M3 building blocks (`Scaffold`, `TopAppBar`, `Card`, `OutlinedTextField`, `Button`, `FilterChip`, `ModalBottomSheet`, etc.) over bespoke custom-drawn widgets.
* **Global Theming Over Per-Component Styling**:
  * Always wrap UI surfaces in [`GroundTheme`](shared/ui/src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/GroundTheme.kt) and reference semantic tokens from `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes`.
  * **Prefer global theme definitions** over specifying ad-hoc colors (`Color(0x...)`), custom typography styles, or visual overrides on individual components. When a visual change is needed across components, update the centralized theme in `shared/ui/` rather than passing inline style overrides to individual composables.

## 6. Code Formatting (`ktformat` & `buf`)

* **Kotlin Code (`ktformat`)**: All Kotlin source and Gradle script files (`.kt`, `.kts`) **must** be formatted using `ktformat` (`ktfmt` / Google style with 2-space indentation). Run `ktformat` on any new or modified Kotlin files before completing changes.
* **Protocol Buffers (`buf`)**: All `proto3` schema files (`.proto` under [`shared/protos/`](shared/protos/)) **must** be formatted using `buf` (`buf format -w`). Run `buf format` whenever creating or modifying `.proto` definitions.

