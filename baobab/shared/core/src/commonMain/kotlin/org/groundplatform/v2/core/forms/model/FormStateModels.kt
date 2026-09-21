/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.model

import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GroupDef
import groundplatform.v2.forms.MediaRef
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.TypedValue

/**
 * Complete, immutable runtime state snapshot of a form and its associated record data.
 *
 * Produced by [org.groundplatform.v2.core.forms.engine.FormEngine] after executing lifecycle
 * actions, reconciling dynamic repeat counts, evaluating topological calculations, masking
 * non-relevant branches, validating constraints, and materializing localized UI components.
 */
data class FormState(
  /** The underlying form definition schema, bindings, translations, and view specification. */
  val formDef: FormDef,
  /**
   * The effective record instance with non-relevant fields, groups, and repeats pruned per the ODK
   * XForms specification. Used for XPath evaluation and final submission output.
   */
  val recordInstance: RecordInstance,
  /**
   * The raw unpruned record node tree preserving user-entered answers across temporary relevancy
   * toggles during an editing session.
   */
  val rawRecordNode: RecordNode,
  /** Currently active language identifier (e.g. `"English"`, `"es-MX"`). */
  val activeLanguage: String,
  /** List of all language identifiers declared in `FormDef.model.translations`. */
  val availableLanguages: List<String>,
  /**
   * Resolved field states indexed by canonical primary instance path (e.g., `"/data/age"` or
   * `"/household/person[2]/name"`).
   */
  val fieldStates: Map<String, FieldState>,
  /** Materialized, repeat-expanded UI view component hierarchy ready for rendering. */
  val rootComponents: List<ComponentState>,
  /** Evaluated ODK Entity dataset operations (creation/update) for the current record state. */
  val entityStates: List<EntityState>,
  /** All active validation errors across currently relevant fields. */
  val validationErrors: List<ValidationError>,
  /** Pending hardware or platform side-effect requests emitted by triggered actions. */
  val pendingRequests: List<PlatformEffectRequest> = emptyList(),
) {
  /** True when all relevant fields pass required and constraint checks. */
  val isValid: Boolean
    get() = validationErrors.isEmpty()

  /** Looks up a [FieldState] by either canonical path (`/data/foo`) or relative path (`foo`). */
  fun findFieldState(path: String): FieldState? {
    fieldStates[path]?.let {
      return it
    }
    val trimmed = path.trimStart('/')
    val rootName =
      formDef.model?.primary_instance?.record_schema?.name?.takeIf { it.isNotEmpty() }
        ?: formDef.form_id.takeIf { it.isNotEmpty() }
        ?: "data"
    return fieldStates["/$rootName/$trimmed"] ?: fieldStates["/data/$trimmed"]
  }
}

/** Evaluated runtime state of a single concrete field instance in the primary record. */
data class FieldState(
  /** Canonical path to this field instance (e.g., `"/household/person[1]/age"`). */
  val canonicalPath: String,
  /** Relative schema path without repeat indices (e.g., `"person/age"`). */
  val relativePath: String,
  /** Associated [FieldBinding] from `FormDef.model.bindings`, if declared. */
  val binding: FieldBinding?,
  /** Resolved data type of the field. */
  val dataType: DataType,
  /** Current effective value of the field (`null` if empty or non-relevant). */
  val value: FieldValue?,
  /** Raw user/calculated value prior to relevancy masking. */
  val rawValue: FieldValue?,
  /** True when the field has no populated answer. */
  val isEmpty: Boolean,
  /**
   * True when both this field's `relevant_expression` and all enclosing group/repeat ancestors
   * evaluate to `true`.
   */
  val isRelevant: Boolean,
  /** True when `required_expression` evaluates to `true` for this field instance. */
  val isRequired: Boolean,
  /** True when user input is disabled (`FieldBinding.read_only` or read-only control type). */
  val isReadOnly: Boolean,
  /** True when this field's value is dynamically computed by a `calculate_expression`. */
  val isCalculated: Boolean,
  /** Validation status (`Valid` or `Invalid` with localized error messages). */
  val validationStatus: ValidationStatus,
)

/** Validation status of a single field instance. */
sealed interface ValidationStatus {
  /** Field satisfies all required, constraint, and range rules (or is currently non-relevant). */
  data object Valid : ValidationStatus

  /** Field violates one or more validation rules. */
  data class Invalid(val errors: List<ValidationError>) : ValidationStatus
}

/** Category of validation failure on a form field. */
enum class ValidationErrorKind {
  /** A relevant field with `isRequired == true` has an empty answer. */
  REQUIRED_MISSING,

  /** A relevant, non-empty field evaluated `constraint_expression` to `false`. */
  CONSTRAINT_VIOLATED,

  /** Numeric value falls outside `RangeConfig` start/end/step bounds. */
  RANGE_OUT_OF_BOUNDS,

  /** Value does not conform to the declared [DataType]. */
  TYPE_MISMATCH,
}

/** A single validation error attached to a concrete field instance. */
data class ValidationError(
  /** Canonical path of the invalid field (e.g., `"/household/person[1]/age"`). */
  val fieldPath: String,
  /** Kind of validation rule violated. */
  val kind: ValidationErrorKind,
  /** Localized and `<output>`-interpolated human-readable error message. */
  val message: String,
)

/**
 * Materialized UI component node within the dynamic form view tree.
 *
 * Unlike the static [groundplatform.v2.forms.ViewComponent] protobuf definition, [ComponentState]
 * expands repeat groups into concrete [RepeatInstanceState] items and holds evaluated relevancy,
 * localized/interpolated labels, dynamic itemset choices, and bound [FieldState]s.
 */
sealed interface ComponentState {
  /** Canonical instance path associated with this component (or synthetic path if unbound). */
  val canonicalPath: String

  /** True if this component and all of its enclosing ancestors are relevant (visible). */
  val isRelevant: Boolean

  /** Resolved, localized, and output-interpolated primary label. */
  val label: ResolvedLabel?

  /** Space-separated appearance tokens (e.g., `"minimal"`, `"field-list"`, `"quick"`). */
  val appearance: String

  /** Materialized interactive control widget (`CONTROL_INPUT`, `CONTROL_SELECT_ONE`, etc.). */
  data class ControlState(
    override val canonicalPath: String,
    val controlDef: ControlDef,
    val fieldState: FieldState,
    override val isRelevant: Boolean,
    override val label: ResolvedLabel?,
    val hint: ResolvedLabel?,
    override val appearance: String,
    /**
     * Selectable choice options for `SELECT_ONE`, `SELECT_MULTIPLE`, or `RANK` controls, populated
     * from either static `ControlDef.choices` or a dynamically filtered/randomized `ItemsetDef`.
     */
    val options: List<ResolvedChoiceOption>,
    /** Evaluated external application launch intent configuration, if declared. */
    val resolvedIntent: ResolvedIntent?,
  ) : ComponentState

  /** Materialized visual or logical group container (`GroupDef`). */
  data class GroupState(
    override val canonicalPath: String,
    val groupDef: GroupDef,
    override val isRelevant: Boolean,
    override val label: ResolvedLabel?,
    override val appearance: String,
    val resolvedIntent: ResolvedIntent?,
    val children: List<ComponentState>,
  ) : ComponentState

  /** Materialized repeat group container (`RepeatDef`) holding $0..N$ repeat instances. */
  data class RepeatGroupState(
    override val canonicalPath: String,
    val repeatDef: RepeatDef,
    override val isRelevant: Boolean,
    override val label: ResolvedLabel?,
    override val appearance: String,
    /**
     * Dynamically evaluated repeat instance count when `RepeatDef.count_expression` is non-empty,
     * or `null` when repeat count is user-controlled.
     */
    val targetCount: Int?,
    /** True if the user is allowed to manually add repeat instances in the UI. */
    val canAddInstance: Boolean,
    /** True if the user is allowed to manually delete repeat instances in the UI. */
    val canRemoveInstance: Boolean,
    /** Ordered list of materialized repeat item instances (`[1]..[N]`). */
    val instances: List<RepeatInstanceState>,
  ) : ComponentState
}

/** A single materialized iteration (`1`-based index) of a [ComponentState.RepeatGroupState]. */
data class RepeatInstanceState(
  /** Canonical path to this repeat item instance (e.g., `"/household/person[2]"`). */
  val canonicalPath: String,
  /** 1-based repeat position index (`1..N`). */
  val repeatIndex: Int,
  /** Resolved per-instance header label (supporting positional `<output>` interpolation). */
  val label: ResolvedLabel?,
  /** True if this repeat instance is relevant. */
  val isRelevant: Boolean,
  /** Child components rendered inside this repeat instance. */
  val children: List<ComponentState>,
)

/**
 * Localized and `<output>`-interpolated text and multi-modal media for a prompt label, hint, or
 * choice option.
 */
data class ResolvedLabel(
  /** Primary localized and interpolated display text. */
  val text: String,
  /** Optional abbreviated phrasing (`short` form) for compact views or summary tables. */
  val shortText: String? = null,
  /** Optional enumerator guidance hint (`guidance` form) for expandable help panels. */
  val guidanceText: String? = null,
  /** Optional localized multi-modal media attachments (image, big-image, audio, video). */
  val media: MediaRef? = null,
)

/** A resolved selectable option for `SELECT_ONE`, `SELECT_MULTIPLE`, or `RANK` controls. */
data class ResolvedChoiceOption(
  /** Underlying machine-readable choice code stored in the record when selected. */
  val value: String,
  /** Localized and interpolated display label and media for this option. */
  val label: ResolvedLabel,
  /** Custom key-value properties attached to this choice item or secondary instance row. */
  val properties: Map<String, String> = emptyMap(),
)

/** Evaluated external Android/iOS/Web intent configuration with XPath parameters resolved. */
data class ResolvedIntent(
  /** Platform intent URI or action string. */
  val intentUri: String,
  /** Key-value extras with XPath expressions evaluated in the component's context. */
  val parameters: Map<String, String>,
  /** Mapping from returned intent result keys to target field paths in the primary instance. */
  val responseMappings: Map<String, String>,
)

/**
 * Evaluated ODK Entity creation or update operation derived from `ModelDef.entities` and
 * `FieldBinding.entity_saveto` mappings for the current record state.
 */
data class EntityState(
  /** Target Entity Dataset name (e.g., `"trees"`, `"households"`). */
  val dataset: String,
  /** Underlying protobuf declaration. */
  val declaration: EntityDeclaration,
  /** True if `create_condition` evaluates to `true` (or defaults to `true` when configured). */
  val shouldCreate: Boolean,
  /** True if `update_condition` evaluates to `true`. */
  val shouldUpdate: Boolean,
  /** Evaluated RFC 4122 v4 UUID of the target entity (`entity_id_expression`). */
  val entityId: String,
  /** Evaluated human-readable label for the entity (`label_expression`). */
  val label: String,
  /** Evaluated optimistic locking base server version (`sync_metadata.base_version_expression`). */
  val baseVersion: Int? = null,
  /** Evaluated trunk version (`sync_metadata.trunk_version_expression`). */
  val trunkVersion: Int? = null,
  /** Evaluated offline branch ID (`sync_metadata.branch_id_expression`). */
  val branchId: String? = null,
  /**
   * Resolved entity property values collected from both `EntityDeclaration.property_mappings` and
   * relevant fields with `FieldBinding.entity_saveto`.
   */
  val properties: Map<String, TypedValue> = emptyMap(),
) {
  /** True if either [shouldCreate] or [shouldUpdate] is active. */
  val isActive: Boolean
    get() = shouldCreate || shouldUpdate
}

/** Hardware or platform side-effect requested by a declarative form action. */
sealed interface PlatformEffectRequest {
  /**
   * Requests an asynchronous GPS coordinate fix from the client hardware (`ACTION_SET_GEOPOINT`) to
   * be written to [targetFieldPath].
   */
  data class SetGeopointRequest(val targetFieldPath: String) : PlatformEffectRequest
}

/** Outcome of attempting to finalize and validate a form session via `FormEngine.finalize`. */
sealed interface FinalizationResult {
  /**
   * All relevant required and constraint rules passed. Provides the pruned [recordInstance] (with
   * `end_time` metadata populated) and active [entityStates].
   */
  data class Success(
    val recordInstance: RecordInstance,
    val entityStates: List<EntityState>,
    val state: FormState,
  ) : FinalizationResult

  /**
   * One or more relevant fields failed validation. Provides the list of [errors] and the updated
   * [state] containing all highlighted field errors.
   */
  data class ValidationFailure(val errors: List<ValidationError>, val state: FormState) :
    FinalizationResult
}
