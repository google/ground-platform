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

# ProtoForms Compose Multiplatform Form UI (`org.groundplatform.v2:protoforms-ui`)

A reusable **Compose Multiplatform** UI library
([`org.groundplatform.v2.core.forms.ui`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md))
for rendering and executing ProtoForms / XForms definitions one question at
a time across **Android (`jvm`)**, **iOS (`iosArm64`, `iosSimulatorArm64`)**,
and **Web (`js`, `wasmJs`)**.


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
-   **Compact Web Layout
    ([`CompactFormRunner`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/CompactFormRunner.kt))**:
    Renders the same `FormWizardController` session as a vertical stack of
    collapsible question cards (`CompactQuestionCard`) that fits a web side
    panel. Each card shows a status icon (answered ✓, skipped ⊘, pending ○, or
    needs attention !), reuses `ControlWidget` for its input, and optional
    questions can be skipped. Every card starts expanded; in interactive
    sessions a card collapses on its own once its question becomes answered
    (deferred while the collector is still typing in it), and manual toggles or
    **Expand all** / **Collapse all** always win afterwards. The wizard's final
    "Review & Submit" step is never rendered as a card: the footer's Submit
    button and validation banner cover it, and progress counts exclude it. A
    `readOnly` mode renders the inputs without reacting and keeps all cards
    expanded, for form designers' previews. Expansion, skip marks and the
    auto-collapse bookkeeping live in a `CompactFormLayoutState`, beside the
    form data. Geometry questions (`geopoint`, `geotrace`, `geoshape`) take
    their input through `CompactGeometryInput`: `Device` keeps the mobile GPS
    widgets, while `MapDrawing` renders a "draw on the map" request
    ([`CompactMapDrawing.kt`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/CompactMapDrawing.kt))
    with **Draw on map** / **Done** / **Undo** / **Clear** actions; the host
    implements `CompactMapDrawingHost` to route its map clicks into the
    question with `addMapDrawingVertex` (a point is placed by one click, lines
    and polygons collect vertices until **Done**). The web dashboard uses this
    for every geometry question, "GPS only" or not, since a browser has no
    field GPS; a `null` host renders the request inert for read-only previews.
    Hosts that report `canFrameGeometry` also get a **Zoom to fit** icon button
    on the card once something is drawn, which calls `frameGeometry(path,
    kind)` so the host can centre the geometry in its map.
    A host can also bring a question into view with `focusRequest`
    (`CompactFocusRequest(path, token)`): the card expands, scrolls into view
    through the enclosing scroll container, and is highlighted for a moment; a
    new token re-fires the same path (the web dashboard uses this when one of
    the form's geometries is clicked on the map).
-   **Form Density
    ([`FormDensity` / `LocalFormDensity`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/FormDensity.kt))**:
    A composition local that tells the shared widgets which layout hosts them.
    The mobile runner uses the default `COMFORTABLE` density; `CompactFormRunner`
    provides `COMPACT` around its whole stack (interactive and read-only alike,
    so the Form designer's web preview matches the live web form). The compact
    density changes exactly two things: numeric questions drop their `-1` /
    `+1` and `-0.5` / `+0.5` stepper buttons (range sliders their `- step` /
    `+ step` buttons) and keep just the text field or slider
    (`showNumericSteppers`), and free-text fields (string, integer,
    decimal, date, time) use the slightly shorter `GroundOutlinedTextField`
    ([`GroundOutlinedTextField.kt`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/GroundOutlinedTextField.kt):
    `bodyMedium` text, ~48 dp minimum height instead of Material's 56 dp, a
    plain `OutlinedTextField` on mobile). Every other widget, card and chrome
    dimension is identical in both densities.
-   **Media Capture Questions
    ([`MediaCaptureWidget`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/MediaCaptureWidgets.kt))**:
    Photo, video, and audio questions (`<upload mediatype="image/*|video/*|audio/*">`)
    render one card on mobile and in the compact web layout. Capture is
    delegated to the host's `LocalMediaCaptureHandler`
    ([`MediaCaptureHost.kt`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/MediaCaptureHost.kt));
    the default `SimulatedMediaCaptureHandler` answers with placeholder
    media so forms stay answerable in tests and previews. The organizer's
    source mode is explicit in the UI: a question with the ODK `new` /
    `new-front` appearance (`MediaCaptureSpec.requireNewCapture`) is badged
    **Capture only** with a lock, offers only the live capture action and live
    retakes, and shows "This question needs a live photo, which this
    device/browser can't capture." when the handler reports no capture
    support; otherwise the card is badged **Capture or upload** with the
    primary **Take photo** / **Record video** / **Record audio** action and a
    secondary **Choose from device** upload (plus **Upload** next to the retake
    action once answered). Hosts decide availability per kind and source
    through `MediaCaptureHandler.supports(kind, source)`; a capture request is
    never silently served by a file picker.

See the detailed package design and API reference in
[`src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md`](src/commonMain/kotlin/org/groundplatform/v2/core/forms/ui/README.md).


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
