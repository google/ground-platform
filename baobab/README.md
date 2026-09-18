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

# Ground 2.0

This directory contains the Protocol Buffer data models, Kotlin Multiplatform
(KMP) core runtime library, documentation, and developer tools for **Ground 2.0**.

## Directory Structure

-   **`core/protos/`**: Protocol Buffer (`.proto`) schema definitions for forms
    (`FormDef`, `RecordInstance`, `RecordSchema`, `FieldBinding`), survey
    configurations (`SurveyDef`, `AclEntry`), and entity/submission/audit
    records.
-   **`core/lib/`**: Kotlin Multiplatform library
    (`org.groundplatform.v2:protoforms`) targeting JVM, JS (IR), WasmJS, and
    iOS:
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
-   **`core/ui/`**: Compose Multiplatform UI library
    (`org.groundplatform.v2:protoforms-ui`, package
    `org.groundplatform.v2.core.forms.ui`) targeting Web (`js` / `wasmJs`),
    Android/JVM (`jvm`), and iOS (`iosArm64` / `iosSimulatorArm64`). Implements
    a single-question-per-screen mobile form experience (`MobileFormRunner`,
    `MobilePhoneFrame`, `FormWizardController`, `QuestionControlCard`, and
    `ControlWidget` for all `ControlType` and `DataType` combinations).
-   **`devtools/formdebugger/`**: Single-page Kotlin Multiplatform Compose Web
    application for interactive bidirectional editing of FormDef (XML ↔
    TextProto / JSON), RecordInstance (XML ↔ TextProto / JSON), real-time XPath
    expression evaluation, and an embedded mobile form runner (`▶ RUN`) powered
    by `core/ui` with live synchronization to the RecordInstance and XPath
    panels.
-   **`docs/`**: Comprehensive architectural design and data model
    documentation.

## Running Core Library Tests

From `core/lib/`:

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
