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

# Ground 2.0 (`baobab/`)

This directory contains the Kotlin Multiplatform (KMP) and Compose Multiplatform
(CMP) shared modules, Protocol Buffer data models, platform applications
(`androidApp`, `iosApp`, `webApp`), developer tools, and technical documentation
for **Ground 2.0**.

## Directory Structure

```text
baobab/
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
        conversion between ODK XForms XML (`<h:html>` and `<data>`),
        `groundplatform.v2.forms` Protocol Buffer messages, Protocol Buffer Text
        Format (`textproto`), and Canonical Proto3 JSON (`ProtoJsonSerializer`).
    -   **XPath 1.0 + ODK XForms Engine**
        (`org.groundplatform.v2.core.forms.xpath`): Full AST compiler and
        evaluator for ODK XForms expressions against strongly-typed
        `RecordInstance` and `FormDef` models.
    -   **Dynamic Form State Model & Evaluation Engine**
        (`org.groundplatform.v2.core.forms.engine` &
        `org.groundplatform.v2.core.forms.model`): Immutable runtime UI state
        model (`FormState`, `ComponentState`) and reactive 5-stage evaluation
        engine (`FormEngine`, `FormSession`, `CompiledForm`) for dynamic
        repeats, calculations, relevance, constraints, cascading `itemset`s,
        translations, and ODK Entities.
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
