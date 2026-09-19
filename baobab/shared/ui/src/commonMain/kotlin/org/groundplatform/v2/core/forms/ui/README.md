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

# ProtoForms Compose Multiplatform UI Package (`org.groundplatform.v2.core.forms.ui`)

This package implements the **Compose Multiplatform** UI component library
(`org.groundplatform.v2:protoforms-ui`) for executing ProtoForms and ODK XForms
surveys across **Android (`jvm`)**, **iOS (`iosArm64`, `iosSimulatorArm64`)**,
and **Web (`js`, `wasmJs`)**.

It is designed around a **single-question-per-screen** mobile experience while
remaining completely decoupled from platform-specific navigation frameworks so
that the same components can be rendered full-screen on a mobile device or
embedded inside web developer tools such as `devtools/formdebugger`.

--------------------------------------------------------------------------------

## 1. Architectural Design

```
┌────────────────────────────────────────────────────────────────────────────┐
│                org.groundplatform.v2:protoforms (shared/core)              │
│   • FormDef, RecordInstance, ComponentState, FormState, FormSession        │
│   • FormEngine (5-stage reactive pipeline: repeats, relevance, calc, etc.) │
└──────────────────────────────────────▲─────────────────────────────────────┘
                                       │
┌──────────────────────────────────────┴─────────────────────────────────────┐
│             org.groundplatform.v2:protoforms-ui (shared/ui)                │
│                                                                            │
│  1. FormWizardController                                                   │
│     • Flattens FormState.rootComponents into List<FormWizardStep>          │
│     • Anchors current step by stepKey across dynamic relevance changes     │
│     • Enforces per-screen required/constraint validation on Next           │
│     • Bridges typed UI mutations (updateString, updateDate, etc.)          │
│                                                                            │
│  2. MobileFormRunner & MobilePhoneFrame                                    │
│     • Top App Bar (Title, Progress Bar, Language Switcher, Step Drawer)    │
│     • Step Content Viewport (QuestionStep / FieldList / RepeatHub / Review)│
│     • Bottom Navigation Bar (← Back, Step Counter, Next → / Submit ✓)      │
│                                                                            │
│  3. QuestionControlCard & ControlWidget                                    │
│     • Renders localized label, hint, media indicators, validation banner   │
│     • Dispatches by ControlType + DataType to Material 3 input widgets     │
└────────────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

1.  **Tree-to-Wizard Flattening (`FormWizardStep`)**: An ODK XForms / ProtoForms
    `FormDef` is a hierarchical tree of nested groups, repeat groups
    (`RepeatGroupState`), and controls (`ControlState`), whereas a mobile
    field-collection UI presents one question screen at a time.
    `FormWizardController.flattenToWizardSteps()` walks the active
    `FormState.rootComponents` tree and produces a linear list of relevant
    steps:

    -   **`FormWizardStep.QuestionStep`**: A single relevant question
        (`ComponentState.ControlState`) along with its ancestor section
        `breadcrumbs` (e.g., `["Site Details", "Canopy Measurements #2"]`) and
        optional `RepeatStepContext`.
    -   **`FormWizardStep.FieldListGroupStep`**: A group with
        `appearance="field-list"`, which per the ODK XForms specification
        renders all of its relevant child questions together on a single screen.
    -   **`FormWizardStep.RepeatHubStep`**: A repeat management card placed at
        the end of each `RepeatGroupState` that lets the user inspect existing
        repeat instances, delete an instance, or tap **`+ Add Another ...`** to
        append a new repeat instance and jump directly to its first question.
    -   **`FormWizardStep.SummaryStep`**: The final review screen listing all
        relevant questions and their current answers (with tap-to-edit jump
        links), evaluated ODK Entities (`EntityState`), and whole-form
        validation status.

2.  **Anchor-Preserving Dynamic Relevance**: Whenever the user edits an answer,
    downstream `relevant` and `jr:count` expressions are immediately
    re-evaluated by `FormSession`. Steps before or after the active screen may
    be inserted or removed. `FormWizardController` tracks the active step's
    canonical `stepKey` (e.g., `"question:/data/branches[1]/branch_length_m"`)
    and re-anchors `currentStepIndex` so the user never loses their place
    mid-entry.

3.  **Per-Screen Validation vs. Whole-Form Submission**:

    -   Tapping **`Next →`** validates only the controls on the current screen
        (`isCurrentStepValid()`). If a required field is empty or a `constraint`
        fails, an inline error banner is displayed with an option to fix the
        answer or explicitly **`Skip →`** while continuing drafts.
    -   Tapping **`Submit ✓`** on the `SummaryStep` invokes
        `FormSession.finalize()`, which updates `end` timestamp preloads
        (`RecordMetadata.end_time`), re-evaluates any dependent calculations,
        and performs whole-form validation across all relevant fields.

4.  **Control & DataType Dispatch Matrix (`ControlWidget`)**:

| `ControlType` /           | Composable Widget      | Supported Features &    |
: `DataType`                :                        : Appearances             :
| :------------------------ | :--------------------- | :---------------------- |
| `isReadOnly == true`      | `ReadOnlyValueBox`     | Displays calculated     |
:                           :                        : (`calculate`) or        :
:                           :                        : read-only values with   :
:                           :                        : `CALC` / `READ-ONLY`    :
:                           :                        : badges                  :
| `CONTROL_INPUT` +         | `StringInputWidget`    | Single-line and         |
: `TYPE_STRING`             :                        : `multiline` text entry  :
:                           :                        : with character count    :
:                           :                        : and quick clear         :
| `CONTROL_INPUT` +         | `IntegerInputWidget`   | Numeric validation with |
: `TYPE_INT32` /            :                        : `-1` / `+1` stepper     :
: `TYPE_INT64`              :                        : buttons                 :
| `CONTROL_INPUT` +         | `DecimalInputWidget`   | Floating-point          |
: `TYPE_DOUBLE`             :                        : validation with `-0.5`  :
:                           :                        : / `+0.5` stepper        :
:                           :                        : buttons                 :
| `CONTROL_INPUT` +         | `BooleanInputWidget`   | Two-card toggle (`Yes   |
: `TYPE_BOOLEAN`            :                        : (True)` / `No (False)`) :
| `CONTROL_INPUT` +         | `DateInputWidget`      | `YYYY-MM-DD` input with |
: `TYPE_DATE`               :                        : `Today`, `-1 Day`, `+1  :
:                           :                        : Day`, and `+1 Month`    :
:                           :                        : shortcuts               :
| `CONTROL_INPUT` +         | `TimeInputWidget`      | `HH:MM:SS` input with   |
: `TYPE_TIME`               :                        : `Now` shortcut          :
| `CONTROL_INPUT` +         | `TimestampInputWidget` | ISO/Epoch timestamp     |
: `TYPE_DATETIME`           :                        : display with `Capture   :
:                           :                        : Timestamp` button       :
| `CONTROL_INPUT` +         | `GeoPointInputWidget`  | Latitude, longitude,    |
: `TYPE_GEOPOINT`           :                        : altitude, accuracy      :
:                           :                        : inputs + GPS preset     :
:                           :                        : simulator               :
| `CONTROL_INPUT` +         | `GeoVertexListWidget`  | Vertex list editor for  |
: `TYPE_GEOTRACE` /         :                        : polylines (`GEOTRACE`)  :
: `TYPE_GEOSHAPE`           :                        : and closed polygons     :
:                           :                        : (`GEOSHAPE`)            :
| `CONTROL_SELECT_ONE`      | `SelectOneWidget`      | Single-choice radio     |
:                           :                        : cards, `likert`         :
:                           :                        : horizontal scale, and   :
:                           :                        : `quick` auto-advance    :
| `CONTROL_SELECT_MULTIPLE` | `SelectMultipleWidget` | Multi-choice checkbox   |
:                           :                        : cards backed by         :
:                           :                        : `TypedValueList`        :
| `CONTROL_RANGE`           | `RangeControlWidget`   | Slider + `- step` / `+  |
:                           :                        : step` buttons           :
:                           :                        : respecting              :
:                           :                        : `RangeConfig` (`start`, :
:                           :                        : `end`, `step`)          :
| `CONTROL_RANK`            | `RankControlWidget`    | Priority-ordered list   |
:                           :                        : with `↑` / `↓`          :
:                           :                        : reordering buttons      :
| `CONTROL_UPLOAD`          | `UploadControlWidget`  | Media attachment        |
:                           :                        : filename input &        :
:                           :                        : simulated capture       :
:                           :                        : (`image/*`, `audio/*`,  :
:                           :                        : `video/*`)              :
| `CONTROL_TRIGGER`         | `TriggerControlWidget` | Confirmation button     |
:                           :                        : toggling `"OK"` value   :

--------------------------------------------------------------------------------

## 2. Usage Guide

### A. Full-Screen Mobile Usage (Android / iOS / Web)

Create a [`FormWizardController`](FormWizardController.kt) for a `FormDef` (and
optional existing `RecordInstance`) and pass it to
[`MobileFormRunner`](MobileFormRunner.kt):

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MobileFormRunner

@Composable
fun SurveyCollectionScreen(
  formDef: FormDef,
  existingRecord: RecordInstance? = null,
  onDraftSaved: (RecordInstance) -> Unit,
  onFormSubmitted: (RecordInstance) -> Unit,
  onClose: () -> Unit,
) {
  val controller = remember(formDef) {
    FormWizardController(
      formDef = formDef,
      existingRecord = existingRecord,
      onRecordUpdated = { record, _ ->
        // Called on every answer edit, repeat addition/removal, or calculation update
        onDraftSaved(record)
      },
    )
  }

  MobileFormRunner(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    onSubmitted = { finalizationSuccess ->
      onFormSubmitted(finalizationSuccess.recordInstance)
    },
    onClose = onClose,
  )
}
```

### B. Embedded Phone Viewport in Web / Desktop Tools (`MobilePhoneFrame`)

When embedding the mobile form runner inside a larger web or desktop layout
(such as the ProtoForms Form Debugger's **`▶ RUN`** panel), wrap
`MobileFormRunner` inside [`MobilePhoneFrame`](MobileFormRunner.kt):

```kotlin
import androidx.compose.runtime.Composable
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.core.forms.ui.MobilePhoneFrame

@Composable
fun EmbeddedFormPreview(controller: FormWizardController, onClose: () -> Unit) {
  MobilePhoneFrame(deviceLabel = "Mobile Form Runner (Single-Question View)") {
    MobileFormRunner(
      controller = controller,
      onClose = onClose,
    )
  }
}
```

### C. Programmatic Navigation & Custom Host Controls

Host applications can also drive or inspect `FormWizardController` directly:

```kotlin
// Jump directly to a specific field path (e.g. from an external inspector or error list)
controller.jumpToField("/data/branches[2]/branch_length_m")

// Add a new repeat instance and navigate straight to its first question
controller.addRepeatInstanceAndOpen("/data/branches")

// Switch active form translation language
controller.setLanguage("fr")

// Trigger finalization and validation programmatically
val result = controller.finalizeForm()
```

--------------------------------------------------------------------------------

## 3. Building and Running Tests

From `shared/ui/`:

```bash
# Run JVM unit tests for FormWizardController and step navigation
./gradlew jvmTest

# Compile JavaScript (IR) and WebAssembly (WasmJS) targets
./gradlew compileKotlinJs compileKotlinWasmJs
```
