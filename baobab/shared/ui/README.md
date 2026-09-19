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

# ProtoForms Compose Multiplatform Form UI (`org.groundplatform.v2:protoforms-ui`)

A reusable **Compose Multiplatform** UI library
([`org.groundplatform.v2.core.forms.ui`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md))
for rendering and executing ProtoForms / ODK XForms definitions one question at
a time across **Android (`jvm`)**, **iOS (`iosArm64`, `iosSimulatorArm64`)**,
and **Web (`js`, `wasmJs`)**.

--------------------------------------------------------------------------------

## Overview

`protoforms-ui` builds directly on top of the pure KMP `FormEngine` and
`FormSession` in `org.groundplatform.v2:protoforms`:

-   **Single-Question-Per-Screen Mobile Navigation
    ([`FormWizardController`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/FormWizardController.kt))**:
    Automatically flattens hierarchical `FormState.rootComponents` into an
    ordered sequence of relevant mobile screens (`QuestionStep`,
    `FieldListGroupStep` for `appearance="field-list"`, `RepeatHubStep` for
    adding/removing repeat instances, and `SummaryStep` for final review and
    submission).
-   **Reactive Step Re-Evaluation**: Preserves the user's current question
    position (`stepKey`) whenever an answer dynamically alters downstream
    `relevant` expressions, `jr:count` repeat counts, cascading `itemset`
    options, or `<output>` label interpolations.
-   **Reusable Multiplatform Control Widgets
    ([`QuestionControlCard` & `ControlWidget`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/ControlWidgets.kt))**:
    Provides mobile-optimized Material 3 widgets for all ProtoForms control
    types (`CONTROL_INPUT`, `CONTROL_SELECT_ONE`, `CONTROL_SELECT_MULTIPLE`,
    `CONTROL_RANGE`, `CONTROL_RANK`, `CONTROL_UPLOAD`, `CONTROL_TRIGGER`) and
    data types (`TYPE_STRING`, `TYPE_INT32`, `TYPE_INT64`, `TYPE_DOUBLE`,
    `TYPE_BOOLEAN`, `TYPE_DATE`, `TYPE_TIME`, `TYPE_DATETIME`, `TYPE_GEOPOINT`,
    `TYPE_GEOTRACE`, `TYPE_GEOSHAPE`).
-   **Embeddable Mobile Phone Preview
    ([`MobilePhoneFrame`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/MobileFormRunner.kt))**:
    Wraps `MobileFormRunner` in a mobile viewport frame (`400×720`) so web and
    desktop applications (such as `devtools/formdebugger`) can execute mobile
    forms inline with bidirectional live record synchronization.

See the detailed package design and API reference in
[`src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md).

--------------------------------------------------------------------------------

## Quick Start

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MobileFormRunner

@Composable
fun SurveyScreen(
  formDef: FormDef,
  initialRecord: RecordInstance? = null,
  onRecordChanged: (RecordInstance) -> Unit,
) {
  val controller = remember(formDef) {
    FormWizardController(
      formDef = formDef,
      existingRecord = initialRecord,
      onRecordUpdated = { record, _ -> onRecordChanged(record) },
    )
  }

  MobileFormRunner(
    controller = controller,
    onSubmitted = { success ->
      println("Submitted valid record: ${success.recordInstance}")
    },
  )
}
```
