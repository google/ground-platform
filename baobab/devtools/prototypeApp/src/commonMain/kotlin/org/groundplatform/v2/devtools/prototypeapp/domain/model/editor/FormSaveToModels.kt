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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

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
enum class StatusMarkerSymbol(val symbol: String, val label: String) {
  OPEN("○", "Open circle"),
  HALF("◐", "Half circle"),
  CHECK("✓", "Checkmark"),
  ALERT("!", "Exclamation"),
  CROSS("✕", "Cross");

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
enum class StatusConditionSubject(val label: String) {
  /** Matches when the form is submitted (`minSubmissions == 1`) or after `N` submissions. */
  SUBMISSIONS("Submission count"),
  /** Compares an answer to a question in this Form. */
  QUESTION("Question answer"),
  /** Compares a property on the target Map layer or Data table. */
  ENTITY_PROPERTY("Feature property"),
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

  /** Entity properties written when status marker rules are enabled. */
  const val STATUS_PROPERTY = "status"
  const val MARKER_SYMBOL_PROPERTY = "marker-symbol"
  const val MARKER_COLOR_PROPERTY = "marker-color"
  val STATUS_PROPERTIES: Set<String> =
    setOf(STATUS_PROPERTY, MARKER_SYMBOL_PROPERTY, MARKER_COLOR_PROPERTY)

  /** Calculated instance nodes holding the evaluated status label, marker symbol, and color. */
  const val STATUS_FIELD = "__status"
  const val MARKER_SYMBOL_FIELD = "__marker_symbol"
  const val MARKER_COLOR_FIELD = "__marker_color"

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

  /** Properties [question] can update in [target]; geometry first for geometry questions. */
  fun propertyOptions(question: EditorQuestion, target: EditorDataset): List<String> {
    val geometry =
      if (target.isMapLayer && question.type.isGeometry) {
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
   * first geometry question updates a map feature's geometry. [skipKey] (e.g. the question that
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

  /** Comparison operators supported for a dataset property of [kind]. */
  fun operatorsForProperty(kind: EditorPropertyKind): List<RelevanceOperator> =
    when (kind) {
      EditorPropertyKind.INTEGER,
      EditorPropertyKind.DECIMAL,
      EditorPropertyKind.DATE ->
        listOf(
          RelevanceOperator.EQUALS,
          RelevanceOperator.NOT_EQUALS,
          RelevanceOperator.GREATER_THAN,
          RelevanceOperator.LESS_THAN,
          RelevanceOperator.IS_ANSWERED,
        )
      EditorPropertyKind.TEXT,
      EditorPropertyKind.BOOLEAN ->
        listOf(
          RelevanceOperator.EQUALS,
          RelevanceOperator.NOT_EQUALS,
          RelevanceOperator.IS_ANSWERED,
        )
    }

  /** XPath comparison expression for [ref] using [operator] and [value]. */
  fun comparisonExpression(
    ref: String,
    operator: RelevanceOperator,
    value: String,
    isNumeric: Boolean,
  ): String {
    val literal =
      if (isNumeric && value.toDoubleOrNull() != null) {
        value.trim()
      } else {
        xpathStringLiteral(value)
      }
    return when (operator) {
      RelevanceOperator.EQUALS -> "$ref = $literal"
      RelevanceOperator.NOT_EQUALS -> "$ref != $literal"
      RelevanceOperator.GREATER_THAN -> "$ref > $literal"
      RelevanceOperator.LESS_THAN -> "$ref < $literal"
      RelevanceOperator.INCLUDES -> "selected($ref, $literal)"
      RelevanceOperator.IS_ANSWERED -> "string-length($ref) > 0"
    }
  }

  /**
   * XPath boolean expression for [rule], or `null` if the rule's condition is incomplete.
   *
   * For [StatusConditionSubject.SUBMISSIONS]:
   * - `minSubmissions == 1` evaluates to `true()` because finalizing the form creates at least 1
   *   submission.
   * - `minSubmissions > 1` checks `number(instance('<dataset>')/root/item[name = <id>]/__version)`
   *   in [SaveToMode.UPDATE] (and `false()` in [SaveToMode.CREATE], since a newly created feature
   *   has only 1 submission).
   */
  fun statusConditionExpression(
    form: EditorForm,
    target: EditorDataset?,
    rule: EditorStatusRule,
  ): String? =
    when (rule.subject) {
      StatusConditionSubject.SUBMISSIONS ->
        when {
          rule.minSubmissions < 1 -> null
          rule.minSubmissions == 1 -> "true()"
          form.saveTo.mode == SaveToMode.UPDATE && target != null -> {
            val idExpr = entityIdExpression(form, target)?.takeIf { it.isNotEmpty() } ?: return null
            "number(instance('${target.id}')/root/item[name = $idExpr]/__version) >= ${rule.minSubmissions}"
          }
          else -> "false()"
        }
      StatusConditionSubject.QUESTION -> {
        val question =
          form.find(rule.questionKey)?.takeIf { it.type != EditorQuestionType.NOTE } ?: return null
        if (rule.operator !in RelevanceOperator.availableFor(question.type)) return null
        if (rule.operator.needsValue && rule.value.isBlank()) return null
        if (
          question.type.isNumeric && rule.operator.needsValue && rule.value.toDoubleOrNull() == null
        ) {
          return null
        }
        comparisonExpression(
          ref = "/data/${question.name}",
          operator = rule.operator,
          value = rule.value,
          isNumeric = question.type.isNumeric,
        )
      }
      StatusConditionSubject.ENTITY_PROPERTY -> {
        val dataset = target ?: return null
        val prop = dataset.property(rule.property) ?: return null
        val column = dataset.columnFor(rule.property) ?: return null
        if (rule.operator !in operatorsForProperty(prop.kind)) return null
        if (rule.operator.needsValue && rule.value.isBlank()) return null
        val isNumeric =
          prop.kind == EditorPropertyKind.INTEGER || prop.kind == EditorPropertyKind.DECIMAL
        if (isNumeric && rule.operator.needsValue && rule.value.toDoubleOrNull() == null) {
          return null
        }
        val ref =
          if (form.saveTo.mode == SaveToMode.UPDATE) {
            val idExpr =
              entityIdExpression(form, dataset)?.takeIf { it.isNotEmpty() } ?: return null
            "instance('${dataset.id}')/root/item[name = $idExpr]/$column"
          } else {
            val q =
              form.questions.firstOrNull {
                it.name == prop.name && it.type != EditorQuestionType.NOTE
              } ?: return null
            "/data/${q.name}"
          }
        comparisonExpression(ref, rule.operator, rule.value, isNumeric)
      }
    }

  /**
   * Nested `if(cond, then, else)` XPath `calculate` expression evaluating [EditorStatusConfig]
   * top-to-bottom and falling back to [EditorStatusConfig.defaultBadge].
   */
  fun statusCalculateExpression(
    form: EditorForm,
    target: EditorDataset?,
    selector: (EditorStatusBadge) -> String,
  ): String {
    val status = form.saveTo.status
    val fallback = xpathStringLiteral(selector(status.defaultBadge))
    return status.rules.foldRight(fallback) { rule, elseBranch ->
      val cond = statusConditionExpression(form, target, rule) ?: return@foldRight elseBranch
      val thenBranch = xpathStringLiteral(selector(rule.badge))
      "if($cond, $thenBranch, $elseBranch)"
    }
  }

  /** Human-readable summary of [rule]'s condition. */
  fun statusRuleSummary(form: EditorForm, target: EditorDataset?, rule: EditorStatusRule): String =
    when (rule.subject) {
      StatusConditionSubject.SUBMISSIONS ->
        if (rule.minSubmissions <= 1) "When form is submitted"
        else "At least ${rule.minSubmissions} submissions"
      StatusConditionSubject.QUESTION -> {
        val q = form.find(rule.questionKey)
        if (q == null) "Missing question"
        else if (!rule.operator.needsValue) "${q.name} ${rule.operator.label}"
        else "${q.name} ${rule.operator.label} \"${rule.value}\""
      }
      StatusConditionSubject.ENTITY_PROPERTY -> {
        val propName = target?.property(rule.property)?.name ?: rule.property ?: "property"
        if (!rule.operator.needsValue) "$propName ${rule.operator.label}"
        else "$propName ${rule.operator.label} \"${rule.value}\""
      }
    }

  private fun xpathStringLiteral(value: String): String =
    if (value.contains('\'')) "\"$value\"" else "'$value'"
}

/** Form-level validation of [EditorSaveTo] against the survey's Map layers and Data tables. */
object SaveToValidator {
  fun validate(form: EditorForm, datasets: List<EditorDataset>): List<EditorIssue> {
    val saveTo = form.saveTo
    val issues = mutableListOf<EditorIssue>()
    val target = SaveToRules.saveTarget(form, datasets)
    if (saveTo.mode == SaveToMode.UPDATE) {
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
            if (!target.isMapLayer || !question.type.isGeometry) {
              issues +=
                EditorIssue(
                  question.key,
                  "Only a Point, Line, or Polygon question can update a map feature's geometry.",
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
      if (mappedCount == 0 && !saveTo.status.enabled) {
        issues += EditorIssue(null, "Choose at least one field to update.")
      }
    }
    if (saveTo.status.enabled) {
      issues += validateStatus(form, target)
    }
    return issues
  }

  private fun validateStatus(form: EditorForm, target: EditorDataset?): List<EditorIssue> {
    val status = form.saveTo.status
    val issues = mutableListOf<EditorIssue>()
    if (status.defaultBadge.label.isBlank()) {
      issues += EditorIssue(null, "Enter a default status label.")
    }
    status.rules.forEach { rule ->
      if (rule.badge.label.isBlank()) {
        issues += EditorIssue(null, "Every status rule needs a status label.")
      }
      when (rule.subject) {
        StatusConditionSubject.SUBMISSIONS -> {
          if (rule.minSubmissions < 1) {
            issues += EditorIssue(null, "Submission count must be at least 1.")
          }
        }
        StatusConditionSubject.QUESTION -> {
          val question = form.find(rule.questionKey)
          when {
            question == null || question.type == EditorQuestionType.NOTE ->
              issues += EditorIssue(null, "Choose a question for the status rule.")
            rule.operator !in RelevanceOperator.availableFor(question.type) ->
              issues +=
                EditorIssue(
                  question.key,
                  "\"${rule.operator.label}\" doesn't apply to ${question.type.label} in status rule.",
                )
            rule.operator.needsValue && rule.value.isBlank() ->
              issues +=
                EditorIssue(
                  question.key,
                  "Status rule for \"${question.name}\" needs a value to compare against.",
                )
            question.type.isNumeric &&
              rule.operator.needsValue &&
              rule.value.toDoubleOrNull() == null ->
              issues +=
                EditorIssue(
                  question.key,
                  "Status rule value for \"${question.name}\" must be a number.",
                )
          }
        }
        StatusConditionSubject.ENTITY_PROPERTY -> {
          val prop = target?.property(rule.property)
          val isNumeric =
            prop?.kind == EditorPropertyKind.INTEGER || prop?.kind == EditorPropertyKind.DECIMAL
          when {
            target == null || prop == null || target.columnFor(rule.property) == null ->
              issues +=
                EditorIssue(
                  null,
                  "Choose a ${target?.featureNoun ?: "feature"} property for the status rule.",
                )
            form.saveTo.mode == SaveToMode.CREATE &&
              form.questions.none {
                it.name == prop.name && it.type != EditorQuestionType.NOTE
              } ->
              issues +=
                EditorIssue(
                  null,
                  "No question writes to property \"${prop.name}\" for the status rule.",
                )
            rule.operator !in SaveToRules.operatorsForProperty(prop.kind) ->
              issues +=
                EditorIssue(
                  null,
                  "\"${rule.operator.label}\" doesn't apply to ${prop.kind.label.lowercase()} property \"${prop.name}\".",
                )
            rule.operator.needsValue && rule.value.isBlank() ->
              issues +=
                EditorIssue(
                  null,
                  "Status rule for property \"${prop.name}\" needs a value to compare against.",
                )
            isNumeric && rule.operator.needsValue && rule.value.toDoubleOrNull() == null ->
              issues +=
                EditorIssue(
                  null,
                  "Status rule value for property \"${prop.name}\" must be a number.",
                )
          }
        }
      }
    }
    return issues
  }
}
