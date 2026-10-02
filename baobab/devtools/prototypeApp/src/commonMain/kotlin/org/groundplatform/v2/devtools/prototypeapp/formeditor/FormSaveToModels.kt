/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

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
) {
  /** Whether the Form does anything other than the default (add a new feature or row). */
  val isCustomized: Boolean
    get() = mode != SaveToMode.CREATE

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
    )
}

/** Value type of an [EditorDatasetProperty], mirroring the Survey editor's property types. */
enum class EditorPropertyKind(val label: String) {
  TEXT("Text"),
  INTEGER("Integer"),
  DECIMAL("Decimal"),
  BOOLEAN("Yes / no"),
  DATE("Date"),
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
) {
  val featureNoun: String
    get() = if (isMapLayer) "map feature" else "table row"

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

/** Shared rules for save-to logic, used by the UI, the validator, and the XForms generator. */
object SaveToRules {
  /** `entities:saveto` value that updates a map feature's geometry. */
  const val GEOMETRY_PROPERTY = "geometry"

  /** Field the app pre-fills with the ID of the feature a Form was opened from. */
  const val TARGET_ENTITY_FIELD = "target_entity"

  /**
   * ODK XForms Entities spec: `saveto` may not be `name` or `label` (case-insensitive) or start
   * with `__`, which are reserved for system properties.
   */
  fun isReservedProperty(name: String): Boolean =
    name.lowercase() in setOf("name", "label") || name.startsWith("__")

  fun createLabel(hasGeometry: Boolean): String =
    if (hasGeometry) "Add new map feature" else "Add new table row"

  fun updateLabel(target: EditorDataset?): String =
    when {
      target == null -> "Update existing map feature or table row"
      target.isMapLayer -> "Update existing map feature"
      else -> "Update existing table row"
    }

  /** Short description of what a submission does, shown under the flow's end node. */
  fun outcome(form: EditorForm, target: EditorDataset?): String =
    when {
      form.saveTo.mode == SaveToMode.UPDATE ->
        if (target?.isMapLayer == false) "Updates a table row" else "Updates a map feature"
      target == null -> "Submission only"
      target.isMapLayer -> "Adds a map feature"
      else -> "Adds a table row"
    }

  /** The dataset a submission of [form] writes to, if any. */
  fun saveTarget(form: EditorForm, datasets: List<EditorDataset>): EditorDataset? =
    when (form.saveTo.mode) {
      SaveToMode.CREATE -> datasets.firstOrNull { it.isLinkedToThisForm }
      SaveToMode.UPDATE -> datasets.firstOrNull { it.id == form.saveTo.targetDatasetId }
    }

  /** Questions whose answers can be saved to a dataset property. */
  fun savableQuestions(form: EditorForm): List<EditorQuestion> =
    form.questions.filter { it.type != EditorQuestionType.NOTE }

  /** Properties [question] can update in [target]; geometry first for Location questions. */
  fun propertyOptions(question: EditorQuestion, target: EditorDataset): List<String> {
    val geometry =
      if (target.isMapLayer && question.type == EditorQuestionType.LOCATION) {
        listOf(GEOMETRY_PROPERTY)
      } else {
        emptyList()
      }
    return geometry + target.updatableProperties.map { it.name }
  }

  /** Whether an answer to a [type] question fits a [kind] property without conversion. */
  fun isCompatible(type: EditorQuestionType, kind: EditorPropertyKind): Boolean =
    when (type) {
      EditorQuestionType.INTEGER ->
        kind in
          setOf(EditorPropertyKind.INTEGER, EditorPropertyKind.DECIMAL, EditorPropertyKind.TEXT)
      EditorQuestionType.DECIMAL ->
        kind in setOf(EditorPropertyKind.DECIMAL, EditorPropertyKind.TEXT)
      EditorQuestionType.DATE -> kind in setOf(EditorPropertyKind.DATE, EditorPropertyKind.TEXT)
      EditorQuestionType.SELECT_ONE ->
        kind in setOf(EditorPropertyKind.TEXT, EditorPropertyKind.BOOLEAN)
      else -> kind == EditorPropertyKind.TEXT
    }

  /** Warning for a mapping whose types don't line up, or `null` if they do. */
  fun compatibilityWarning(
    question: EditorQuestion,
    property: String,
    target: EditorDataset,
  ): String? {
    if (property == GEOMETRY_PROPERTY) return null
    val kind = target.property(property)?.kind ?: return null
    if (isCompatible(question.type, kind)) return null
    return "${question.type.label} answers may not fit the ${kind.label.lowercase()} property \"$property\"."
  }

  /**
   * Default mappings for updating [target]: questions map to properties with the same name, and the
   * first Location question updates a map feature's geometry. [skipKey] (e.g. the question that
   * identifies the feature) is left out.
   */
  fun autoMap(
    form: EditorForm,
    target: EditorDataset,
    skipKey: String? = null,
  ): List<EditorFieldMapping> {
    val geometryKey = form.primaryGeometryQuestion?.key
    return savableQuestions(form)
      .filter { it.key != skipKey }
      .map { q ->
        val property =
          when {
            target.isMapLayer && q.key == geometryKey -> GEOMETRY_PROPERTY
            target.updatableProperties.any { it.name == q.name } -> q.name
            else -> null
          }
        EditorFieldMapping(q.key, property)
      }
  }

  /**
   * Question that most likely holds a feature ID in [target], for the "Answer to a question"
   * default.
   */
  fun defaultIdQuestion(form: EditorForm, target: EditorDataset): EditorQuestion? {
    val candidates =
      form.questions.filter {
        it.type in
          setOf(
            EditorQuestionType.TEXT,
            EditorQuestionType.INTEGER,
            EditorQuestionType.SELECT_ONE,
          )
      }
    return candidates.firstOrNull { it.name == target.keyProperty } ?: candidates.firstOrNull()
  }

  /**
   * XPath for the ID of the feature to update, or `null` if the logic is incomplete. ODK Entities
   * lists expose the entity ID in the `name` column.
   */
  fun entityIdExpression(form: EditorForm, target: EditorDataset): String? {
    val saveTo = form.saveTo
    return when (saveTo.idSource) {
      EntityIdSource.SELECTED_FEATURE -> "/data/$TARGET_ENTITY_FIELD"
      EntityIdSource.QUESTION -> {
        val question = form.find(saveTo.idQuestionKey) ?: return null
        val column = target.columnFor(saveTo.idMatchProperty) ?: return null
        val answer = "/data/${question.name}"
        if (column == "name") answer
        else "instance('${target.id}')/root/item[$column = $answer]/name"
      }
    }
  }
}

/** Form-level validation of [EditorSaveTo] against the survey's Map layers and Data tables. */
object SaveToValidator {
  fun validate(form: EditorForm, datasets: List<EditorDataset>): List<EditorIssue> {
    val saveTo = form.saveTo
    if (saveTo.mode != SaveToMode.UPDATE) return emptyList()
    val issues = mutableListOf<EditorIssue>()
    val target = datasets.firstOrNull { it.id == saveTo.targetDatasetId }
    if (target == null) {
      issues +=
        EditorIssue(
          null,
          if (saveTo.targetDatasetId == null)
            "Choose the map layer or data table this form updates."
          else "The map layer or data table \"${saveTo.targetDatasetId}\" no longer exists.",
        )
      return issues
    }
    when (saveTo.idSource) {
      EntityIdSource.SELECTED_FEATURE ->
        form.questions
          .filter { it.name == SaveToRules.TARGET_ENTITY_FIELD }
          .forEach {
            issues +=
              EditorIssue(
                it.key,
                "Name \"${SaveToRules.TARGET_ENTITY_FIELD}\" is reserved for the ${target.featureNoun} being updated.",
              )
          }
      EntityIdSource.QUESTION -> {
        val question = form.find(saveTo.idQuestionKey)
        if (question == null || question.type == EditorQuestionType.NOTE) {
          issues +=
            EditorIssue(
              null,
              "Choose the question that identifies the ${target.featureNoun} to update.",
            )
        }
        if (target.columnFor(saveTo.idMatchProperty) == null) {
          issues += EditorIssue(null, "Choose the property the answer is matched against.")
        }
      }
    }
    val claimed = mutableSetOf<String>()
    var mappedCount = 0
    saveTo.mappings.forEach { mapping ->
      val question = form.find(mapping.questionKey) ?: return@forEach
      val property = mapping.property ?: return@forEach
      if (question.type == EditorQuestionType.NOTE) return@forEach
      mappedCount++
      when {
        property == SaveToRules.GEOMETRY_PROPERTY -> {
          if (!target.isMapLayer || question.type != EditorQuestionType.LOCATION) {
            issues +=
              EditorIssue(
                question.key,
                "Only a Location question can update a map feature's geometry.",
              )
          }
        }
        target.property(property) == null ->
          issues +=
            EditorIssue(
              question.key,
              "Updates \"$property\", which isn't a property of ${target.displayName}.",
            )
        target.updatableProperties.none { it.name == property } ->
          issues += EditorIssue(question.key, "\"$property\" can't be updated by a form.")
      }
      if (!claimed.add(property)) {
        issues += EditorIssue(question.key, "Another question already updates \"$property\".")
      }
    }
    if (mappedCount == 0) {
      issues += EditorIssue(null, "Choose at least one field to update.")
    }
    return issues
  }
}
