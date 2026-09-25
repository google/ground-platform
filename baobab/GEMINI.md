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

# Ground 2.0 Agent Rules & Guidelines

These rules govern all AI-assisted research, design, and code changes within Ground 2.0. Follow them strictly when creating or modifying files in this directory and its subdirectories.

## 1. Project Design & Architectural Reference

* **Primary Source of Truth**: Refer to [`docs/product/prd.md`](docs/product/prd.md) (and supporting specifications under [`docs/product/`](docs/product/) and [`docs/architecture.md`](docs/architecture.md)) for project-specific product requirements, domain terminology, and system architecture.
* **UX & Content Guidelines**: Follow the Ground 2.0 terminology, UX writing rules, and core mental model defined in [`docs/ux/content-guidelines.md`](docs/ux/content-guidelines.md) and [`docs/product/terminology.md`](docs/product/terminology.md). Strictly use Ground 2.0 / XForms & XLSForm-aligned terminology across all UI copy, comments, and code:
  * **Survey** (`SurveyDef`): Top-level organizational and ACL container.
  * **Forms** (`FormDef`): Questionnaires / encounter logs (replaces Ground 1.0 "Job").
  * **Tables / Layers** (`EntityDatasetDef`): Persistent master data / spatial datasets (replaces Ground 1.0 "Site").
  * **Map features** (`EntityRecord`): Real-world spatial objects rendered on the map (replaces Ground 1.0 "Site").
  * **Questions / Groups / Repeats / Notes**: Form tree elements (replaces Ground 1.0 "Task").
  * **Submissions** (`SubmissionRecord`): Immutable historical records preserving timestamps and inputs.
* **Core Product Principles**: Ensure all implementations uphold *Proportional Complexity* (simple by default, progressive disclosure for advanced features), *Offline-First Resilience* (100% disconnected field execution and atomic local persistence), and *Real-World Usability* (clear, forgiving interactions with high-contrast legibility). Detailed interaction patterns, form–table mappings, and voice/tone rules are documented in [`docs/ux/content-guidelines.md`](docs/ux/content-guidelines.md).

## 2. Open-Source Purity & Confidentiality

* **Strictly Open Source**: Ground 2.0 is a fully public, open-source project released under the Apache 2.0 License.
* **No Proprietary Content**: Never include proprietary code, algorithms, internal libraries, confidential information, internal URLs/identifiers (e.g., internal shortlinks, internal bug IDs, employee LDAPs/handles, or internal endpoints), API keys/secrets, or non-public datasets in submitted code, comments, tests, or assets.
* **Public Dependencies & Assets Only**: Use only publicly available, open-source libraries, standard specifications (XForms, XLSForm, GeoJSON, Protobuf), and public-domain/openly licensed test fixtures and assets.

## 3. License Headers (`Apache 2.0`)

* **New Source Files**: Every newly created source, build, schema, script, or documentation file **must** begin with the standard Apache 2.0 license header using the **current calendar year**:
  ```kotlin
  /*
   * Copyright 2026 The Ground Authors.
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
* **Quoting**: Use straight double quotes around `"License"` and `"AS IS"`, matching the canonical Apache 2.0 text and the rest of the `ground-internal` repository.
* **Open the block with `/*`, never `/**`**: `/**` makes the header a KDoc comment that binds to the following `package` declaration and leaks the license text into generated API documentation.
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

## 6. ProtoForms Schema & XForms Compatibility Boundary

### Normative references

ProtoForms ([`shared/protos/forms/`](shared/protos/forms/), package `groundplatform.v2.forms`) is a Protocol Buffer encoding of the XForms data model. When adding or changing anything in that package, consult these in order of precedence:

1. **[ODK XForms Specification](https://getodk.github.io/xforms-spec/)** — the primary target. This is the de facto standard for field data collection: a pragmatic subset of W3C XForms plus widely-implemented extensions (`jr:` / `odk:` namespaces, `itemset`, `constraint`/`jr:constraintMsg`, `jr:preload`, `setvalue`/`setgeopoint`, entities).
2. **[ODK Entities](https://docs.getodk.org/entities-intro/)** — the entity/dataset model (`entities` block, `save_to`, `entity_id`, create/update conditions, offline branch/trunk versioning).
3. **[XLSForm](https://xlsform.org/)** — the authoring format most form designers actually use. Anything expressible in XLSForm should survive the round trip into ProtoForms and back.
4. **[OpenRosa](https://docs.getodk.org/openrosa/)** — form list, form submission, and metadata (`<orx:meta>`, `instanceID`, `deviceID`) conventions.
5. **[W3C XForms 1.0](https://www.w3.org/TR/xforms/)** and **[XPath 1.0](https://www.w3.org/TR/1999/REC-xpath-19991116/)** — the underlying model and expression language. Use these to resolve semantics the ODK spec leaves unstated (type coercion, node-set comparison, axis proximity, `round()`/`substring()` edge cases).

### Compatibility, not purity

* **Interoperate with real implementations.** The bar is that a form authored in XLSForm, served by **ODK Central**, and filled in **ODK Collect** or **Enketo** round-trips through ProtoForms without losing information or changing meaning. Where the W3C spec and what **JavaRosa** actually does diverge, **match the real-world behavior** and leave a comment explaining the divergence and citing both sources.
* **Widely-implemented extensions belong in ProtoForms.** Constructs in the `jr:` / `odk:` namespaces, and ODK Entities, are part of the interoperable surface even though they are not in W3C XForms. Representing them here is correct.
* **Ground-specific constructs stay out.** Anything meaningful only to Ground — map layer configuration, survey ACLs, sync bookkeeping, UI preferences, launch behavior — belongs in [`shared/protos/survey/`](shared/protos/survey/) (`SurveyDef`, `FormLaunchConfig`, `MapConfig`), [`shared/protos/data/`](shared/protos/data/) (`SubmissionRecord`, `EntityRecord`, `AuditRecord`), or application-level KMP models. The test to apply is **not** "is this in the W3C spec?" but **"would another XForms tool understand this, or is it only meaningful to Ground?"**
* **Round-trip fidelity is the acceptance criterion.** New schema surface needs a round-trip test in [`shared/core/src/commonTest/.../serialization/`](shared/core/src/commonTest/kotlin/org/groundplatform/v2/core/forms/serialization/) proving XML → proto → XML preserves the construct. Unrecognized attributes and elements should be preserved rather than dropped.
* **Cite the spec in code.** When implementing spec-defined behavior, reference the specific section in a comment (e.g. `// XPath 1.0 section 4.4: ties round toward positive infinity`) so the next reader can check the implementation against the source.

## 7. Code Formatting (`ktformat` & `buf`)

* **Kotlin Code (`ktformat`)**: All Kotlin source and Gradle script files (`.kt`, `.kts`) **must** be formatted using `ktformat` (`ktfmt` / Google style with 2-space indentation). Run `ktformat` on any new or modified Kotlin files before completing changes.
* **Protocol Buffers (`buf`)**: All `proto3` schema files (`.proto` under [`shared/protos/`](shared/protos/)) **must** be formatted using `buf` (`buf format -w`). Run `buf format` whenever creating or modifying `.proto` definitions.

