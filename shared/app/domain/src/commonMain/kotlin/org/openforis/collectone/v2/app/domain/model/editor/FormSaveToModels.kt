/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.app.domain.model.editor

/**
 * What a submitted Form does to its Map layer or Data table, compiled to an ODK XForms Entities
 * declaration (`<entities:entity create="1">` or `<entities:entity update="1">`, per the ODK XForms
 * Entities specification).
 */
enum class SaveToMode {
  /** Each submission adds a new map feature or table row (the default). */
  CREATE,
  /** Each submission updates properties of an existing map feature or table row. */
  UPDATE,
}

/** How an [SaveToMode.UPDATE] Form finds the map feature or table row to update. */
enum class EntityIdSource {
  /**
   * The feature the data collector opened the Form from (or picked from a list in the Form). Stored
   * in the `/data/target_entity` field that the app pre-fills on launch.
   */
  SELECTED_FEATURE,
  /** The answer to a question is matched against a property of the Map layer or Data table. */
  QUESTION,
}

/** Saves the answer to question [questionKey] into dataset property [property] (`null` = skip). */
data class EditorFieldMapping(val questionKey: String, val property: String?)

/** Symbol displayed inside a map feature or table row's status marker and status chip. */
enum class StatusMarkerSymbol(val symbol: String) {
  OPEN("○"),
  HALF("◐"),
  CHECK("✓"),
  ALERT("!"),
  CROSS("✕");

  companion object {
    fun fromSymbol(symbol: String): StatusMarkerSymbol =
      entries.firstOrNull { it.symbol == symbol } ?: CHECK
  }
}

/**
 * Bundled status outcome written to an entity's `marker-symbol`, `marker-color`, and `status`
 * properties when a condition matches (or as the fallback when none match).
 */
data class EditorStatusBadge(
  val symbol: StatusMarkerSymbol = StatusMarkerSymbol.CHECK,
  val colorHex: String = "#3C8D40",
  val label: String = "Surveyed",
) {
  companion object {
    val SURVEYED = EditorStatusBadge(StatusMarkerSymbol.CHECK, "#3C8D40", "Surveyed")
    val IN_PROGRESS = EditorStatusBadge(StatusMarkerSymbol.HALF, "#F9BF40", "In progress")
    val NEEDS_REVIEW = EditorStatusBadge(StatusMarkerSymbol.ALERT, "#F37C22", "Needs review")
    val FLAGGED = EditorStatusBadge(StatusMarkerSymbol.CROSS, "#D13135", "Flagged")
    val PENDING = EditorStatusBadge(StatusMarkerSymbol.OPEN, "#80868B", "Pending")
  }
}

/** What a status rule condition inspects when determining a feature's status. */
enum class StatusConditionSubject {
  /** Matches when the form is submitted (`minSubmissions == 1`) or after `N` submissions. */
  SUBMISSIONS,
  /** Compares an answer to a question in this Form. */
  QUESTION,
  /** Compares a property on the target Map layer or Data table. */
  ENTITY_PROPERTY,
}

/**
 * One conditional status rule, evaluated top-to-bottom. The first rule whose condition holds sets
 * the feature's status marker symbol, color, and label to [badge].
 */
data class EditorStatusRule(
  val subject: StatusConditionSubject = StatusConditionSubject.SUBMISSIONS,
  /**
   * Minimum number of submissions required when [subject] is [StatusConditionSubject.SUBMISSIONS].
   */
  val minSubmissions: Int = 1,
  /** Question compared when [subject] is [StatusConditionSubject.QUESTION]. */
  val questionKey: String? = null,
  /** Dataset property compared when [subject] is [StatusConditionSubject.ENTITY_PROPERTY]. */
  val property: String? = null,
  val operator: RelevanceOperator = RelevanceOperator.EQUALS,
  val value: String = "",
  val badge: EditorStatusBadge = EditorStatusBadge.SURVEYED,
)

/**
 * Optional status marker rules for the Map layer or Data table this Form writes to. Disabled by
 * default; when enabled, defaults to marking features as `✓ Surveyed` on submission and `○ Pending`
 * otherwise.
 */
data class EditorStatusConfig(
  val enabled: Boolean = false,
  val rules: List<EditorStatusRule> = defaultRules(),
  val defaultBadge: EditorStatusBadge = EditorStatusBadge.PENDING,
) {
  companion object {
    fun defaultRules(): List<EditorStatusRule> =
      listOf(
        EditorStatusRule(
          subject = StatusConditionSubject.SUBMISSIONS,
          minSubmissions = 1,
          badge = EditorStatusBadge.SURVEYED,
        )
      )
  }
}

/** Form-level "save to" logic, edited in the Form properties' Advanced section. */
data class EditorSaveTo(
  val mode: SaveToMode = SaveToMode.CREATE,
  /** [EditorDataset.id] of the Map layer or Data table updated in [SaveToMode.UPDATE]. */
  val targetDatasetId: String? = null,
  val idSource: EntityIdSource = EntityIdSource.SELECTED_FEATURE,
  /** Question whose answer identifies the feature when [idSource] is [EntityIdSource.QUESTION]. */
  val idQuestionKey: String? = null,
  /** Dataset property the [idQuestionKey] answer is matched against. */
  val idMatchProperty: String? = null,
  /** Question to property mappings used in [SaveToMode.UPDATE]. */
  val mappings: List<EditorFieldMapping> = emptyList(),
  /** Conditional status marker and label rules, off by default. */
  val status: EditorStatusConfig = EditorStatusConfig(),
) {
  /** Whether the Form does anything other than the default (add a new feature or row). */
  val isCustomized: Boolean
    get() = mode != SaveToMode.CREATE || status.enabled

  fun propertyFor(questionKey: String): String? =
    mappings.firstOrNull { it.questionKey == questionKey }?.property

  fun withMapping(questionKey: String, property: String?): EditorSaveTo =
    copy(
      mappings =
        mappings.filterNot { it.questionKey == questionKey } +
          EditorFieldMapping(questionKey, property)
    )

  /** Drops references to question [questionKey], e.g. after it's deleted. */
  fun withoutQuestion(questionKey: String): EditorSaveTo =
    copy(
      idQuestionKey = idQuestionKey.takeIf { it != questionKey },
      mappings = mappings.filterNot { it.questionKey == questionKey },
      status =
        status.copy(
          rules =
            status.rules.filterNot {
              it.subject == StatusConditionSubject.QUESTION && it.questionKey == questionKey
            }
        ),
    )
}

/** Value type of an [EditorDatasetProperty], mirroring the Survey editor's property types. */
enum class EditorPropertyKind {
  TEXT,
  INTEGER,
  DECIMAL,
  BOOLEAN,
  DATE,
}

data class EditorDatasetProperty(val name: String, val label: String, val kind: EditorPropertyKind)

/** One map feature or table row. [name] is its entity ID (the ODK Entities `name` column). */
data class EditorDatasetRow(
  val name: String,
  val label: String,
  val values: Map<String, String> = emptyMap(),
)

/**
 * The Form editor's read-only view of a Map layer or Data table in the survey, so the Form editor
 * doesn't depend on the Survey editor's models.
 */
data class EditorDataset(
  /** Dataset ID used in XForms (`entities:entity/@dataset`, `instance('<id>')`). */
  val id: String,
  val displayName: String,
  val isMapLayer: Boolean,
  /** Property holding each feature's ID. */
  val keyProperty: String,
  /** Property shown as each feature's label. */
  val labelProperty: String,
  val properties: List<EditorDatasetProperty>,
  val rows: List<EditorDatasetRow> = emptyList(),
  /** Whether this dataset is the one the Form being edited adds features to. */
  val isLinkedToThisForm: Boolean = false,
  /** Stable editor-only key in the Survey editor (`EntityDataset.key`). */
  val key: String = "",
  /** Key of the Form whose submissions add new features/rows to this dataset, if any. */
  val linkedFormKey: String? = null,
  /** Display title of the Form whose submissions add new features/rows to this dataset, if any. */
  val linkedFormTitle: String? = null,
  /** Whether this dataset's features are generated from a statistical sample design. */
  val isGenerated: Boolean = false,
) {
  /** Whether another Form in the survey adds new features/rows to this dataset. */
  val hasCreationForm: Boolean
    get() = linkedFormKey != null && !isLinkedToThisForm

  /** Whether a new Form can be generated to add features/rows to this dataset. */
  val canCreateForm: Boolean
    get() = linkedFormKey == null && !isGenerated

  fun property(name: String?): EditorDatasetProperty? = properties.firstOrNull { it.name == name }

  /** Properties a Form may write with `entities:saveto`. */
  val updatableProperties: List<EditorDatasetProperty>
    get() = properties.filter { it.name != keyProperty && !SaveToRules.isReservedProperty(it.name) }

  /** Properties a question's answer can be matched against to find a feature. */
  val matchableProperties: List<EditorDatasetProperty>
    get() = properties.filter { columnFor(it.name) != null }

  /**
   * Column of the dataset's secondary instance holding [property], or `null` if it isn't available.
   * ODK Entities lists expose the entity ID as `name` and its label as `label`.
   */
  fun columnFor(property: String?): String? =
    when {
      property == null -> null
      property == keyProperty -> "name"
      property == labelProperty -> "label"
      property(property) == null || SaveToRules.isReservedProperty(property) -> null
      else -> property
    }
}
