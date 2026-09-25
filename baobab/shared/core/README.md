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

# Ground 2.0 Core Runtime Library (`shared/core`)

`org.groundplatform.v2:protoforms` is the pure Kotlin Multiplatform (KMP) domain
and form runtime library for Ground 2.0. It has **no UI or Compose
dependencies** and compiles to **Android/JVM (`jvm`)**, **iOS (`iosArm64`,
`iosSimulatorArm64`, `iosX64`)**, **WebAssembly (`wasmJs`)**, and **JavaScript
(`js`)**.

## Key Packages

-   **[`org.groundplatform.v2.core.forms.serialization`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/serialization/README.md)**:
    Bidirectional lossless conversion between XForms XML (`<h:html>` and
    `<data>`), `groundplatform.v2.forms` Protocol Buffer messages (`FormDef`,
    `RecordInstance`), Protocol Buffer Text Format (`textproto`), and Canonical
    Proto3 JSON (`ProtoJsonSerializer`).
-   **[`org.groundplatform.v2.core.forms.xpath`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/xpath/README.md)**:
    Full XPath 1.0 + XForms AST lexer, parser, dependency analyzer, and
    evaluator operating directly against `RecordInstance` and `FormDef` models.
-   **[`org.groundplatform.v2.core.forms.engine`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/engine/README.md)**
    & **`org.groundplatform.v2.core.forms.model`**: Immutable runtime state
    models (`FormState`, `ComponentState`) and the 5-stage reactive evaluation
    pipeline (`FormEngine`, `FormSession`, `CompiledForm`) supporting dynamic
    repeats, calculations, relevance, constraints, cascading `itemset`s,
    multilingual translations, and XForms Entities.

## Building and Running Tests

From `shared/core/`:

```bash
# Run JVM unit and round-trip tests
./gradlew jvmTest

# Compile and verify JS and WasmJS targets
./gradlew check compileKotlinJs compileKotlinWasmJs
```
