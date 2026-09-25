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

# ProtoForms Dynamic Form State Model & Evaluation Engine (`org.groundplatform.v2.core.forms.engine`)

A pure Kotlin Multiplatform (KMP) runtime state model and reactive evaluation
engine for **ProtoForms** (`groundplatform.v2.forms.Form` and `Record`) and the
[XForms specification](https://getodk.github.io/xforms-spec/).

--------------------------------------------------------------------------------

## Overview

While `groundplatform.v2.forms.Form` defines the static form schema, bindings,
and UI layout, and `groundplatform.v2.forms.Record` stores the raw field values,
multiplatform UI clients (Compose Multiplatform, Android, iOS, and Web) require
a reactive runtime engine that evaluates all dynamic aspects of a form and
produces a ready-to-render UI state tree.

This package and its companion state package
(`org.groundplatform.v2.core.forms.model`) provide:

-   **Immutable Runtime State Model (`FormState` & `ElementState`)**: A
    strongly-typed, immutable snapshot representing the complete evaluated state
    of a form and its `Record` at a point in time, including hierarchical UI
    element states (`DataFieldState`, `GroupState`, `RepeatGroupState`,
    `RepeatInstanceState`), flat O(1) path lookup maps (`elementsByPath`),
    validation states, resolved multi-locale labels, and XForms Entity
    mutations.

-   **Ahead-of-Time XPath Compilation & Topological DAG (`CompiledForm`)**:
    Pre-compiles all XPath expressions (`relevant`, `calculate`, `required`,
    `constraint`, `count_expression`, `nodeset_filter`, `<output>`
    interpolations, and
    `Action` triggers) once per `Form` and orders calculated fields
    topologically using static dependency analysis.

-   **Pure Functional Evaluation Pipeline (`FormEngine`)**: Evaluates dynamic
    repeat instance synthesis (`jr:count`), `calculate` cascades, visibility and
    validation rules, cascading `itemset` choice filtering, localized text/media
    resolution, and XForms Entities (`EntityAction`) without side effects.

-   **Stateful Session Coordinator (`FormSession`)**: Manages the lifecycle of a
    form-filling session (`ODK_INSTANCE_FIRST_LOAD`, `ODK_INSTANCE_LOAD`,
    `ODK_NEW_REPEAT`, `XFORMS_VALUE_CHANGED`, and submission finalization),
    exposing observable state transitions for UI ViewModels.

--------------------------------------------------------------------------------

## Supported Targets

This library is written in 100% pure Kotlin (`commonMain`) with zero
platform-specific dependencies, supporting all Kotlin Multiplatform targets:

Target     | Source Set   | Description
:--------- | :----------- | :-------------------------
**JVM**    | `jvmMain`    | Android, Server & Desktop
**JS**     | `jsMain`     | Browser & Node.js (IR)
**WasmJS** | `wasmJsMain` | WebAssembly (Browser/Node)
**iOS**    | `iosMain`    | Apple iOS (Arm64 & X64)

--------------------------------------------------------------------------------

## Architecture & Design

### 1. Package Layout

```text
org.groundplatform.v2.core.forms
├── model/
│   └── FormStateModels.kt         # Immutable runtime UI & validation state models
└── engine/
    ├── CompiledForm.kt            # Pre-compiled XPath ASTs & topological dependency graph
    ├── FormEngine.kt              # Pure functional 5-stage evaluation & mutation engine
    ├── FormEnvironment.kt         # Pluggable device/user/time preload & XPath context SPI
    ├── FormSession.kt             # Stateful session manager for UI / ViewModel integration
    └── RecordMutator.kt           # Copy-on-write helpers for immutable Record trees
```

### 2. Runtime State Model (`org.groundplatform.v2.core.forms.model`)

Every evaluation produces an immutable `FormState` containing:

-   **`record: Record`**: The updated protobuf record containing all user
    answers, preload metadata, event action side-effects, and freshly evaluated
    `calculate` outputs.

-   **`elements: List<ElementState>`**: The root-level UI element tree
    preserving the hierarchy of `Form.elements` with repeat groups expanded into
    concrete `RepeatInstanceState` entries.

-   **`elementsByPath: Map<String, ElementState>`**: Flat lookup map indexed by
    canonical instance path (e.g., `"/data/household/head_age"` or
    `"/data/plot[1]/tree[2]/height"`) for $O(1)$ access.

-   **`validationErrors: List<ValidationError>` & `isValid: Boolean`**:
    Aggregated `REQUIRED_MISSING` and `CONSTRAINT_VIOLATED` errors across all
    *relevant* fields. Per XForms semantics, non-relevant fields
    (`isRelevant == false`) and children of non-relevant groups/repeats are
    automatically excluded from validation errors.

-   **`evaluatedEntities: List<EvaluatedEntity>`**: Resolved XForms Entity
    declarations (`CREATE` / `UPDATE`, entity ID, label, and `save_to` property
    map) when `EntitySpec.enabled_expression` evaluates to `true`.

#### `ElementState` Hierarchy

| `ElementState`        | Schema Source         | Key Dynamic Properties      |
: Subtype               :                       :                             :
| :-------------------- | :-------------------- | :-------------------------- |
| `DataFieldState`      | `FieldElement`        | `value`, `isRelevant`,      |
:                       :                       : `isRequired`, `isReadOnly`, :
:                       :                       : `label`, `hint`, `help`,    :
:                       :                       : `options\:                  :
:                       :                       : List<EvaluatedOption>`,     :
:                       :                       : `validation\:               :
:                       :                       : ValidationResult`           :
| `GroupState`          | `FieldGroup`          | `isRelevant`, `label`,      |
:                       : (`repeat == null`)    : `hint`, `children\:         :
:                       :                       : List<ElementState>`         :
| `RepeatGroupState`    | `FieldGroup` (`repeat | `isRelevant`, `label`,      |
:                       : != null`)             : `instances\:                :
:                       :                       : List<RepeatInstanceState>`, :
:                       :                       : `canAddInstance`,           :
:                       :                       : `canRemoveInstance`,        :
:                       :                       : `targetCount`               :
| `RepeatInstanceState` | Concrete repeat       | `index` (1-based), `path`   |
:                       : iteration `1..N`      : (e.g., `"/data/plot[1]"`),  :
:                       :                       : `label`, `children`,        :
:                       :                       : `canRemove`                 :

### 3. Five-Stage Evaluation Pipeline (`FormEngine`)

`FormEngine.evaluate()` executes a deterministic 5-stage pipeline on every
initial load or mutation:

1.  **Stage 1 — Repeat Instance Count Synchronization (`jr:count`)**: Evaluates
    dynamic `RepeatDef.count_expression` in structural
    outer-to-inner order. When the target count exceeds existing instances in
    `Record`, new `RecordNode` instances are synthesized with schema defaults
    and `ODK_NEW_REPEAT` actions are fired.

2.  **Stage 2 — Relevance, Required, ReadOnly & Constraint Pass**: Traverses the
    UI element tree top-down. If an ancestor `GroupState` or `RepeatGroupState`
    evaluates `relevant_expression` to `false`, all descendant elements inherit
    `isRelevant = false` and bypass required/constraint checks.

3.  **Stage 3 — Topological `calculate` Pass**: Evaluates all `FieldBinding`
    `calculate_expression`s in topological dependency order. Fields with
    `once(...)` or preload rules preserve non-empty values when configured.

    > Note that relevance is evaluated *before* calculations within a single
    > pass. Stages 1–3 therefore run inside a convergence loop (bounded by
    > `MAX_CONVERGENCE_PASSES`) that repeats until the pruned record, the
    > relevance map, and the dynamic repeat counts all stop changing. This is
    > what allows a `relevant_expression` to depend on a `calculate_expression`
    > (and vice versa): the first pass sees a stale value, and a subsequent pass
    > settles it. A form whose calculations and relevance cannot reach a fixed
    > point within `MAX_CONVERGENCE_PASSES` is left at the last computed state.

4.  **Stage 4 — Dynamic Choice (`itemset`) & Text Resolution Pass**: Evaluates
    `ItemsetDef.nodeset_filter` against the in-memory
    `ModelDef.secondary_instances`
    or a custom `SecondaryInstanceProvider`, resolves active locale translations
    (`TranslationEntry`), and interpolates inline `<output value="..."/>`
    expressions in labels, hints, help text, and error messages.

5.  **Stage 5 — XForms Entities Pass**: Evaluates `Form.entities` (`EntitySpec`),
    Checking `enabled_expression`, resolving `id_expression` and
    `label_expression`, and collecting `save_to` property values from relevant
    fields.

--------------------------------------------------------------------------------

## Usage Examples

### 1. Managing an Interactive Form Session (`FormSession`)

For UI clients and ViewModels, `FormSession` coordinates initialization, user
input, repeat manipulation, locale switching, and submission validation:

```kotlin
import groundplatform.v2.forms.Form
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.engine.DefaultFormEnvironment
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.DataFieldState

// 1. Create and initialize a new form session
val session = FormSession(
  form = formProto,
  environment = DefaultFormEnvironment(
    username = "surveyor_1",
    deviceId = "device-abc-123",
  ),
  initialLocale = "en",
)

val initialState = session.initialize()

// 2. Update a field value (automatically triggers XFORMS_VALUE_CHANGED actions,
//    recalculates dependent fields, and re-evaluates relevance/constraints)
val updatedState = session.updateValue(
  path = "/data/species",
  newValue = FieldValue(scalar_value = TypedValue(string_value = "oak")),
)

// 3. Inspect a specific field's evaluated UI state in O(1) time
val diameterField = updatedState.elementsByPath["/data/diameter_cm"] as? DataFieldState
if (diameterField != null && diameterField.isRelevant) {
  println("Label: ${diameterField.label?.text}")
  println("Required: ${diameterField.isRequired}")
  println("Valid: ${diameterField.validation.isValid}")
}

// 4. Add or remove repeat instances (when not locked by no_add_remove / jr:count)
session.addRepeatInstance(repeatPath = "/data/trees")
session.removeRepeatInstance(repeatPath = "/data/trees", index = 1)

// 5. Switch active translation locale on the fly
session.setLocale("es")

// 6. Finalize for submission (evaluates END PreloadConfig timestamps and
//    returns the full list of validation errors if any relevant field is invalid)
val (finalState, errors) = session.finalizeForSubmission()
if (errors.isEmpty()) {
  val recordToSubmit = finalState.record
}
```

### 2. Stateless Functional Evaluation (`FormEngine` & `CompiledForm`)

If you manage state externally (e.g., in a Redux/MVI store or server-side
validation service), use `CompiledForm` and `FormEngine` directly as pure
functions:

```kotlin
import org.groundplatform.v2.core.forms.engine.CompiledForm
import org.groundplatform.v2.core.forms.engine.FormEngine

val engine = FormEngine()

// Compile form once and cache across evaluations
val compiledForm: CompiledForm = engine.compile(formProto)

// Evaluate a Record snapshot at any time without mutating state
val formState = engine.evaluate(
  compiledForm = compiledForm,
  record = existingRecordProto,
  locale = "fr",
)

println("Is record valid? ${formState.isValid}")
println("Validation errors: ${formState.validationErrors}")
println("Evaluated entities: ${formState.evaluatedEntities}")
```

### 3. Customizing Environment & Preload Providers (`FormEnvironment`)

Implement `FormEnvironment` to supply platform-specific UUIDs, timestamps,
device metadata, or SQLite-backed secondary instances
(`SecondaryInstanceProvider`):

```kotlin
import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import groundplatform.v2.forms.PreloadConfig
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.engine.FormEnvironment
import org.groundplatform.v2.core.forms.xpath.SecondaryInstanceProvider

class AndroidFormEnvironment(
  override val secondaryInstanceProvider: SecondaryInstanceProvider?,
) : FormEnvironment {
  override fun now(): Instant = Instant.ofEpochSecond(System.currentTimeMillis() / 1000)
  override fun today(): Date = Date(year = 2026, month = 9, day = 18)
  override fun currentTimeOfDay(): TimeOfDay = TimeOfDay(hours = 14, minutes = 30)
  override fun generateUid(): String = "uuid:${java.util.UUID.randomUUID()}"
  override fun resolvePreload(preload: PreloadConfig): TypedValue? = null
}
```

--------------------------------------------------------------------------------

## Building and Running Tests

### Java Environment Requirement

Gradle and the Kotlin toolchain require **Java 21**. Set `JAVA_HOME` before
running Gradle commands:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Run JVM Unit Tests

To execute the unit test suite (`FormEngineUnitTest` and all companion test
suites):

```bash
./gradlew jvmTest
```
