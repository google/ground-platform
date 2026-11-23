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

import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.message

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

  /**
   * [datasets] with concepts inherited from [form]'s linked questions (`save_to` inheritance; see
   * `docs/technical/model/library/01-concepts.md`): each property a linked question saves to gets
   * the question's concept. Covers the dataset [form] (key [formKey]) adds features to, whose
   * properties are named after its questions, and the dataset it updates through its field
   * mappings. Properties of unlinked questions keep their concept.
   */
  fun inheritConcepts(
    form: EditorForm,
    formKey: String,
    datasets: List<EntityDataset>,
  ): List<EntityDataset> {
    val isUpdate = form.saveTo.mode == SaveToMode.UPDATE
    return datasets.map { dataset ->
      val propertyConcepts: Map<String, ConceptLink> =
        when {
          isUpdate && dataset.id == form.saveTo.targetDatasetId ->
            form.saveTo.mappings
              .mapNotNull { m ->
                val link = form.find(m.questionKey)?.conceptLink
                if (m.property != null && link != null) m.property to link else null
              }
              .toMap()
          !isUpdate && dataset.linkedFormKey == formKey ->
            form.questions.mapNotNull { q -> q.conceptLink?.let { q.name to it } }.toMap()
          else -> emptyMap()
        }
      if (propertyConcepts.isEmpty()) {
        dataset
      } else {
        dataset.copy(
          properties =
            dataset.properties.map { p ->
              propertyConcepts[p.name]?.let { p.copy(conceptLink = it) } ?: p
            }
        )
      }
    }
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
  fun isCompatible(
    type: EditorQuestionType,
    kind: EditorPropertyKind,
    decimalPlaces: Int = 0,
  ): Boolean =
    when (type) {
      EditorQuestionType.NUMBER ->
        if (decimalPlaces <= 0) {
          kind in
            setOf(EditorPropertyKind.INTEGER, EditorPropertyKind.DECIMAL, EditorPropertyKind.TEXT)
        } else {
          kind in setOf(EditorPropertyKind.DECIMAL, EditorPropertyKind.TEXT)
        }
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
  ): DomainMessage? {
    if (property == GEOMETRY_PROPERTY) return null
    val kind = target.property(property)?.kind ?: return null
    if (isCompatible(question.type, kind, question.decimalPlaces)) return null
    return EditorIssueMessage.SAVE_TO_TYPE_MISMATCH.message(
      DomainMessage.Label(question.type),
      DomainMessage.Lowercase(DomainMessage.Label(kind)),
      property,
    )
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
        (it.type == EditorQuestionType.NUMBER && it.decimalPlaces <= 0) ||
          it.type in setOf(EditorQuestionType.TEXT, EditorQuestionType.SELECT_ONE)
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
        val answer = form.instancePath(question)
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
          ref = form.instancePath(question),
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
            form.instancePath(q)
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
}
