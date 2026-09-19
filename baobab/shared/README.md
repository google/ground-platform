<!--
  IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

# Ground 2.0 Shared Multiplatform Modules (`shared/`)

This directory contains all shared Protocol Buffer schemas and Kotlin
Multiplatform (KMP) / Compose Multiplatform (CMP) modules used across the
Ground 2.0 platform applications (`androidApp`, `iosApp`, `webApp`, and
`devtools/formdebugger`).

## Submodules

| Directory | Artifact / Module | Targets | Description |
| :--- | :--- | :--- | :--- |
| [`protos/`](protos/) | *(Proto3 Schemas)* | All Platforms | Canonical `.proto` definitions for forms (`groundplatform.v2.forms`), survey configuration (`groundplatform.v2.survey`), and operational records (`groundplatform.v2.data`). |
| [`core/`](core/) | `org.groundplatform.v2:protoforms` | Android/JVM, iOS, JS, WasmJS | Pure KMP domain, serialization (XForms XML, TextProto, Proto3 JSON), XPath 1.0 + ODK evaluator, and 5-stage reactive `FormEngine` (no UI/Compose dependencies). |
| [`ui/`](ui/) | `org.groundplatform.v2:protoforms-ui` | Android/JVM, iOS, JS, WasmJS | Shared Compose Multiplatform theme (`GroundTheme`), design tokens, and form rendering library (`MobileFormRunner`, `FormWizardController`, `QuestionControlCard`, `ControlWidget`, and `MobilePhoneFrame`). |
| [`mobile/`](mobile/) | `org.groundplatform.v2:mobile` | **Android/JVM & iOS Only** | Shared mobile application module containing mobile-specific services (`services/`), local persistence (`data/`), and mobile Compose screens/ViewModels (`ui/`) shared identically between `androidApp` and `iosApp`. Exports the unified `GroundMobile` XCFramework for Xcode. |

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
     │ (Android & iOS ONLY)│    │  (Web Wasm/JS ONLY)  │
     │  • data/            │    └──────────────────────┘
     │  • services/        │
     │  • ui/              │
     └──▲───────────────▲──┘
        │               │
 ┌──────┴──────┐ ┌──────┴─────┐
 │ androidApp  │ │   iosApp   │
 └─────────────┘ └────────────┘
```
