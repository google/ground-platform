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

# ProtoForms Dynamic Form State Model & Evaluation Engine (`org.openforis.collectone.v2.core.forms.engine`)

A pure Kotlin Multiplatform (KMP) runtime state model and reactive evaluation
engine for **ProtoForms** (`openforis.collectone.v1.forms.Form` and `Record`) and the
[XForms specification](https://getodk.github.io/xforms-spec/).


## Overview

While `openforis.collectone.v1.forms.Form` defines the static form schema, bindings,
and UI layout, and `openforis.collectone.v1.forms.Record` stores the raw field values,
multiplatform UI clients (Compose Multiplatform, Android, iOS, and Web) require
a reactive runtime engine that evaluates all dynamic aspects of a form and
produces a ready-to-render UI state tree.

This package and its companion state package
(`org.openforis.collectone.v2.core.forms.model`) provide:

-   **Immutable Runtime State Model (`FormState` & `ComponentState`)**: A
    strongly-typed, immutable snapshot representing the complete evaluated state
    of a form and its `RecordInstance` at a point in time, including the
    hierarchical UI component tree (`ControlState`, `GroupState`,
    `RepeatGroupState`, `RepeatInstanceState`), per-field states indexed by
    path (`fieldStates`), validation errors, resolved multi-locale labels, and
    XForms Entity operations (`entityStates`).

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


## Supported Targets

This library is written in 100% pure Kotlin (`commonMain`) with zero
platform-specific dependencies, supporting all Kotlin Multiplatform targets:

Target     | Source Set   | Description
:--------- | :----------- | :-------------------------
**JVM**    | `jvmMain`    | Android, Server & Desktop
**JS**     | `jsMain`     | Browser & Node.js (IR)
**WasmJS** | `wasmJsMain` | WebAssembly (Browser/Node)
**iOS**    | `iosMain`    | Apple iOS (Arm64 & X64)


## Architecture & Design

### Package Layout

```text
org.openforis.collectone.v2.core.forms
├── model/
│   └── FormStateModels.kt         # Immutable runtime UI & validation state models
└── engine/
    ├── CompiledForm.kt            # Pre-compiled XPath ASTs & topological dependency graph
    ├── FormEngine.kt              # Pure functional 5-stage evaluation & mutation engine
    ├── FormEnvironment.kt         # Pluggable device/user/time preload & XPath context SPI
    ├── FormSession.kt             # Stateful session manager for UI / ViewModel integration
    └── RecordMutator.kt           # Copy-on-write helpers for immutable Record trees
```

### Runtime State Model (`org.openforis.collectone.v2.core.forms.model`)

Every evaluation produces an immutable `FormState` containing:

-   **`recordInstance: RecordInstance`**: The effective record with all user
    answers, preload metadata, event action side-effects, and freshly evaluated
    `calculate` outputs, with non-relevant fields, groups, and repeats pruned.
    `rawRecordNode` keeps the unpruned answers so they survive temporary
    relevance toggles during editing.

-   **`rootComponents: List<ComponentState>`**: The root-level UI component tree
    preserving the hierarchy of the form body, with repeat groups expanded into
    concrete `RepeatInstanceState` entries.

-   **`fieldStates: Map<String, FieldState>`**: Field states indexed by
    canonical instance path (e.g., `"/data/household/head_age"` or
    `"/data/plot[1]/tree[2]/height"`). `findFieldState(path)` also accepts a
    path relative to the root.

-   **`validationErrors: List<ValidationError>` & `isValid: Boolean`**:
    Aggregated `REQUIRED_MISSING`, `CONSTRAINT_VIOLATED`, `RANGE_OUT_OF_BOUNDS`
    and `TYPE_MISMATCH` errors across all *relevant* fields. Per XForms
    semantics, non-relevant fields (`isRelevant == false`) and children of
    non-relevant groups/repeats are automatically excluded.

-   **`entityStates: List<EntityState>`**: Resolved XForms Entity declarations
    (`shouldCreate` / `shouldUpdate`, entity ID, label, and `save_to` property
    map).

-   **`activeLanguage`, `availableLanguages`, `pendingRequests` and
    `didNotConverge`**: The active translation, the declared translations,
    platform side-effects requested by actions (such as `odk:setgeopoint`), and
    whether the evaluation loop stopped before reaching a fixed point.

#### `ComponentState` Hierarchy

Every component exposes `canonicalPath`, `isRelevant`, `label: ResolvedLabel?`
and `appearance`.

| Type                  | Schema Source       | Key Dynamic Properties                                                     |
| :-------------------- | :------------------ | :------------------------------------------------------------------------- |
| `ControlState`        | `ControlDef`        | `fieldState` (value, required, read-only, validation), `hint`, `options`   |
| `GroupState`          | `GroupDef`          | `children: List<ComponentState>`                                           |
| `RepeatGroupState`    | `RepeatDef`         | `instances`, `targetCount`, `canAddInstance`, `canRemoveInstance`          |
| `RepeatInstanceState` | One repeat instance | `repeatIndex` (1-based), `canonicalPath` (e.g. `"/data/plot[1]"`), `children` |

### Five-Stage Evaluation Pipeline (`FormEngine`)

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


## Usage Examples

### Managing an Interactive Form Session (`FormSession`)

For UI clients and ViewModels, `FormSession` coordinates initialization, user
input, repeat manipulation, language switching, and submission validation. It
initializes the form on construction and holds the latest `FormState` in
`state`:

```kotlin
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

// 1. Start a new session (runs defaults, preloads, and first-load events)
val session =
  FormSession(
    formDef = formDef,
    activeLanguage = "English",
    environment =
      FormEnvironment(deviceProperties = mapOf("username" to "surveyor_1")),
  )

// 2. Observe every new state snapshot (returns a function that unregisters)
val unsubscribe = session.addStateListener { state -> render(state.rootComponents) }

// 3. Update a field value (triggers `xforms-value-changed` actions,
//    recalculates dependent fields, and re-evaluates relevance/constraints)
val updatedState = session.updateString("/data/species", "oak")

// 4. Inspect a field's evaluated state
val diameter = updatedState.findFieldState("/data/diameter_cm")
if (diameter != null && diameter.isRelevant) {
  println("Required: ${diameter.isRequired}, status: ${diameter.validationStatus}")
}

// 5. Add or remove repeat instances (indexes are 1-based)
session.addRepeatInstance(repeatPath = "/data/trees")
session.removeRepeatInstance(repeatPath = "/data/trees", repeatIndex = 1)

// 6. Switch the active translation
session.setLanguage("Español")

// 7. Finalize for submission (sets `end` preloads and validates relevant fields)
when (val result = session.finalize()) {
  is FinalizationResult.Success -> submit(result.recordInstance, result.entityStates)
  is FinalizationResult.ValidationFailure -> showErrors(result.errors)
}
```

### Stateless Functional Evaluation (`FormEngine`)

If you manage state externally (e.g., in an MVI store or a server-side
validation service), call the `FormEngine` object directly. Each function takes
the previous `FormState` (or a `FormDef` and record) and returns a new one
without side effects:

```kotlin
import org.openforis.collectone.v2.core.forms.engine.FormEngine

// Check a form up front (cyclic dependencies, malformed XPath) instead of throwing
val validation = FormEngine.validate(formDef)
check(validation.isValid) { "Invalid form: ${validation.problems.map { it.message }}" }

// Evaluate a record snapshot without mutating anything
val formState = FormEngine.evaluate(
  formDef = formDef,
  recordInstance = existingRecord,
  activeLanguage = "Français",
)

println("Is record valid? ${formState.isValid}")
println("Validation errors: ${formState.validationErrors}")
println("Entity operations: ${formState.entityStates.filter { it.isActive }}")

// Apply an edit to a snapshot
val nextState = FormEngine.updateFieldValue(formState, "/data/species", newValue = speciesValue)
```

### Customizing Environment & Preload Providers (`FormEnvironment`)

`FormEnvironment` is a data class: override only the providers you need to
supply platform-specific clocks, UUIDs, device metadata, location, or
SQLite-backed secondary instances (`SecondaryInstanceProvider`):

```kotlin
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment

val environment =
  FormEnvironment(
    clockEpochMillis = { System.currentTimeMillis() },
    uuidGenerator = { java.util.UUID.randomUUID().toString() },
    deviceProperties = mapOf("deviceid" to deviceId, "username" to username),
    secondaryInstanceProvider = sqliteInstanceProvider,
    locationProvider = { lastKnownGeoPoint },
  )
```

`FormEnvironment.DEFAULT` is deterministic (fixed clock, random UUIDs) and is
used when no environment is passed.


## Building and Running Tests

### Java Environment Requirement

Run Gradle from the repository root: the repository is a single Gradle build
with one `./gradlew`. Gradle runs on **Java 21**, pinned in the root build's
`gradle/gradle-daemon-jvm.properties`. Gradle uses an installed JDK 21 if it
finds one and downloads one otherwise; `./gradlew` itself only needs a JDK on
the `PATH` (or `JAVA_HOME`) to start. To change the version for the whole
build, run `./gradlew updateDaemonJvm --jvm-version=<version>`.

### Run JVM Unit Tests

To execute the unit test suite (the `FormEngine*Test` classes and all companion
test suites):

```bash
./gradlew :shared:core:jvmTest
```
