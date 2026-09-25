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

# Ground 2.0

This directory contains the Kotlin Multiplatform (KMP) and Compose Multiplatform
(CMP) shared modules, Protocol Buffer data models, platform applications
(`androidApp`, `iosApp`, `webApp`), developer tools, and technical documentation
for **Ground 2.0**.

## Directory Structure

```text
├── shared/                      # All shared multiplatform schemas & modules
│   ├── protos/                  # Protocol Buffer (.proto) schemas (forms, survey, data)
│   ├── core/                    # Pure KMP runtime library (Android/JVM, iOS, JS, WasmJS)
│   ├── ui/                      # Shared Compose theme & form runner widgets (Android/JVM, iOS, JS, WasmJS)
│   └── mobile/                  # Shared Mobile services, data & Compose UI (Android & iOS ONLY)
│       └── src/commonMain/.../mobile/
│           ├── data/            # Local SQLite/Room persistence, offline queues & tile cache
│           ├── services/        # Offline sync engine, GPS/geotrace tracking & media management
│           └── ui/              # Mobile screens, ViewModels & navigation
├── androidApp/                  # Runnable Android app wrapper (APK/AAB) -> depends on shared/mobile
├── iosApp/                      # Runnable iOS Xcode app wrapper (IPA)   -> embeds shared/mobile
├── webApp/                      # Runnable Web Console app (Wasm/JS)     -> depends on shared/core & shared/ui
├── devtools/
│   ├── formdebugger/            # Interactive Compose Web workbench for XForms, ProtoForms & XPath
│   └── prototypeApp/            # Interactive Compose Web UX prototype embedding Ground Mobile UI
└── docs/                        # System architecture & Protocol Buffer data model specifications
```

### Shared Modules ([`shared/`](shared/))

-   **[`shared/protos/`](shared/protos/)**: Protocol Buffer (`proto3`) schema
    definitions for forms (`FormDef`, `RecordInstance`, `RecordSchema`,
    `FieldBinding`), survey configurations (`SurveyDef`, `AclEntry`), and
    entity/submission/audit records.
-   **[`shared/core/`](shared/core/)**: Pure Kotlin Multiplatform runtime
    library (`org.groundplatform.v2:protoforms`) targeting JVM/Android, iOS, JS
    (IR), and WasmJS:
    -   **XForms XML, TextProto & JSON Serialization**
        (`org.groundplatform.v2.core.forms.serialization`): Bidirectional
        conversion between XForms XML (`<h:html>` and `<data>`),
        `groundplatform.v2.forms` Protocol Buffer messages, Protocol Buffer Text
        Format (`textproto`), and Canonical Proto3 JSON (`ProtoJsonSerializer`).
    -   **XPath 1.0 + XForms Engine**
        (`org.groundplatform.v2.core.forms.xpath`): Full AST compiler and
        evaluator for XForms expressions against strongly-typed
        `RecordInstance` and `FormDef` models.
    -   **Dynamic Form State Model & Evaluation Engine**
        (`org.groundplatform.v2.core.forms.engine` &
        `org.groundplatform.v2.core.forms.model`): Immutable runtime UI state
        model (`FormState`, `ComponentState`) and reactive 5-stage evaluation
        engine (`FormEngine`, `FormSession`, `CompiledForm`) for dynamic
        repeats, calculations, relevance, constraints, cascading `itemset`s,
        translations, and XForms Entities.
-   **[`shared/ui/`](shared/ui/)**: Compose Multiplatform UI library
    (`org.groundplatform.v2:protoforms-ui`, package
    `org.groundplatform.v2.core.forms.ui`) targeting Web (`js` / `wasmJs`),
    Android/JVM (`jvm`), and iOS (`iosArm64` / `iosSimulatorArm64`). Provides
    shared Material 3 theming (`GroundTheme`) and the single-question-per-screen
    form experience (`MobileFormRunner`, `MobilePhoneFrame`,
    `FormWizardController`, `QuestionControlCard`, and `ControlWidget`).
-   **[`shared/mobile/`](shared/mobile/)**: Shared Mobile application module
    (`org.groundplatform.v2:mobile`) targeting **only Android/JVM and iOS**.
    Contains mobile-specific local persistence (`data/`), domain services such
    as offline sync and GPS tracking (`services/`), and mobile Compose screens &
    ViewModels (`ui/`) shared identically by `androidApp` and `iosApp`. Exports
    the `GroundMobile` XCFramework for Xcode.

### Platform Applications & Tools

-   **[`androidApp/`](androidApp/)**: Thin Android application entry point that
    hosts `GroundMobileApp()` from `shared/mobile`.
-   **[`iosApp/`](iosApp/)**: Thin Xcode / SwiftUI application entry point that
    embeds `GroundMobile.framework` from `shared/mobile`.
-   **[`webApp/`](webApp/)**: Ground 2.0 Web Console application (`wasmJs` /
    `js`) for survey administration, map management, and data exploration,
    built directly on `shared/core` and `shared/ui`.
-   **[`devtools/formdebugger/`](devtools/formdebugger/)**: Single-page Compose
    Multiplatform Web application for interactive bidirectional editing of
    `FormDef` and `RecordInstance` (`XML` ↔ `textproto` / `JSON`), real-time
    XPath expression evaluation, and an embedded mobile form runner (`▶ RUN`)
    powered by `shared/ui`.
-   **[`devtools/prototypeApp/`](devtools/prototypeApp/)**: Interactive Compose
    Multiplatform Web application that embeds a live preview of the Ground 2.0
    Mobile UI (`Splash / Loading`, `Sign In`, `Terms of Service`, and `Download
    survey` screens) for UX co-design.
-   **[`docs/`](docs/)**: Comprehensive architectural design and data model
    documentation.

## Mental Model & Terminology Mapping to XForms

### 1. The Core Mental Model

-   **Tables = Current State (Persistent Master Data)**: Flat, stateful master
    datasets (`EntityDatasetDef` / `EntityRecord`) where each row represents a
    real-world object (site, plot, asset, or participant).
-   **Forms = Transactions / Events (Encounter Logs)**: Questionnaires
    (`FormDef`) filled out in the field. Completed submissions
    (`SubmissionRecord` / `RecordInstance`) are immutable event records
    preserving GPS, timestamps, and raw inputs.

### 2. For Survey Organizers / Form Designers: Forms & Tables

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

### 3. For Data Collectors (Map UI & Field Workflow)

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

## Running Core Library Tests

From `shared/core/`:

```bash
# Run JVM unit and round-trip tests
./gradlew jvmTest

# Compile and check JS and WasmJS targets
./gradlew check compileKotlinJs compileKotlinWasmJs
```

## Form Debugger Web App

See [`devtools/formdebugger/README.md`](devtools/formdebugger/README.md) for
instructions on running the local development web server, live-reload testing,
and building production web bundles.
