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

# Collect One Shared Multiplatform Modules (`shared/`)

This directory contains all shared Protocol Buffer schemas and Kotlin
Multiplatform (KMP) / Compose Multiplatform (CMP) modules used across the
Collect One platform applications (`androidApp`, `iosApp`, `webApp`, and
`mobileWebApp`).

## Submodules

| Directory | Artifact / Module | Targets | Description |
| :--- | :--- | :--- | :--- |
| [`protos/`](protos/) | *(Proto3 Schemas)* | All Platforms | Canonical `.proto` definitions for forms (`openforis.collectone.v1.forms`), survey configuration (`openforis.collectone.v1.survey`), and operational records (`openforis.collectone.v1.data`). |
| [`core/`](core/) | `org.openforis.collectone.v2:protoforms` | Android/JVM, iOS, JS, WasmJS | Pure KMP domain, serialization (XForms XML, TextProto, Proto3 JSON), XPath 1.0 + XForms evaluator, and 5-stage reactive `FormEngine` (no UI/Compose dependencies). |
| [`ui/`](ui/) | `org.openforis.collectone.v2:protoforms-ui` | Android/JVM, iOS, JS, WasmJS | Shared Compose Multiplatform theme (`GroundTheme`), design tokens, and form rendering library (`MobileFormRunner`, `FormWizardController`, `QuestionControlCard`, and `ControlWidget`). |
| [`map/`](map/) | `org.openforis.collectone.v2:map` | Android/JVM, iOS, WasmJS | Compose Multiplatform map (`GroundMap`): immutable `MapContent`, hoisted `MapCameraState`, typed `MapEvent`s, and per-platform native renderers behind a single `expect`. No domain dependencies. |
| [`mobile/`](mobile/) | `org.openforis.collectone.v2:mobile` | Android/JVM, WasmJS, iOS (opt-in) | Shared mobile application module containing mobile-specific services (`services/`), local persistence (`data/`), and mobile Compose screens/ViewModels (`ui/`) shared identically between `androidApp` and `iosApp`. Exports the unified `GroundMobile` XCFramework for Xcode. |

## Dependency Layering

```text
          ┌──────────────────────────────────────┐
          │            shared/protos             │
          └──────────────────▲───────────────────┘
                             │ (Wire codegen)
          ┌──────────────────┴───────────────────┐
          │             shared/core              │
          │     (Android/JVM, iOS, JS, Wasm)     │
          └──────────────────▲───────────────────┘
                             │
          ┌──────────────────┴───────────────────┐
          │              shared/ui               │
          │     (Android/JVM, iOS, JS, Wasm)     │
          └─────────▲──────────────────▲─────────┘
                    │                  │
     ┌──────────────┴──────┐    ┌──────┴───────────────┐
     │    shared/mobile    │    │        webApp        │
     │ (Android, iOS, Wasm)│    │  (Web Wasm/JS ONLY)  │
     │  • data/            │    └──────────────────────┘
     │  • services/        │
     │  • ui/              │
     └──▲───────────────▲──┘
        │               │
 ┌──────┴──────┐ ┌──────┴─────┐
 │ androidApp  │ │   iosApp   │
 └─────────────┘ └────────────┘
```
