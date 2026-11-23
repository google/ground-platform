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

import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.message

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
            message =
              saveTo.targetDatasetId?.let {
                EditorIssueMessage.SAVE_TO_TARGET_DELETED.message(it)
              } ?: EditorIssueMessage.SAVE_TO_TARGET_MISSING.message(),
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
                  message =
                    if (target.isMapLayer) {
                      EditorIssueMessage.SAVE_TO_RESERVED_NAME_MAP_LAYER.message(
                        SaveToRules.TARGET_ENTITY_FIELD
                      )
                    } else {
                      EditorIssueMessage.SAVE_TO_RESERVED_NAME_DATA_TABLE.message(
                        SaveToRules.TARGET_ENTITY_FIELD
                      )
                    },
                )
            }
        EntityIdSource.QUESTION -> {
          val question = form.find(saveTo.idQuestionKey)
          if (question == null || question.type == EditorQuestionType.NOTE) {
            issues +=
              EditorIssue(
                null,
                message =
                  if (target.isMapLayer) {
                    EditorIssueMessage.SAVE_TO_ID_QUESTION_MISSING_MAP_LAYER.message()
                  } else {
                    EditorIssueMessage.SAVE_TO_ID_QUESTION_MISSING_DATA_TABLE.message()
                  },
              )
          }
          if (target.columnFor(saveTo.idMatchProperty) == null) {
            issues +=
              EditorIssue(
                null,
                message = EditorIssueMessage.SAVE_TO_MATCH_PROPERTY_MISSING.message(),
              )
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
                  message = EditorIssueMessage.SAVE_TO_GEOMETRY_QUESTION_REQUIRED.message(),
                )
            }
          }
          target.property(property) == null ->
            issues +=
              EditorIssue(
                question.key,
                message =
                  EditorIssueMessage.SAVE_TO_UNKNOWN_PROPERTY.message(
                    property,
                    target.displayName,
                  ),
              )
          target.updatableProperties.none { it.name == property } ->
            issues +=
              EditorIssue(
                question.key,
                message = EditorIssueMessage.SAVE_TO_PROPERTY_NOT_UPDATABLE.message(property),
              )
        }
        if (!claimed.add(property)) {
          issues +=
            EditorIssue(
              question.key,
              message = EditorIssueMessage.SAVE_TO_PROPERTY_ALREADY_UPDATED.message(property),
            )
        }
      }
      if (mappedCount == 0 && !saveTo.status.enabled) {
        issues +=
          EditorIssue(
            null,
            message = EditorIssueMessage.SAVE_TO_NO_FIELDS.message(),
          )
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
      issues +=
        EditorIssue(
          null,
          message = EditorIssueMessage.STATUS_DEFAULT_LABEL_MISSING.message(),
        )
    }
    status.rules.forEach { rule ->
      if (rule.badge.label.isBlank()) {
        issues +=
          EditorIssue(
            null,
            message = EditorIssueMessage.STATUS_RULE_LABEL_MISSING.message(),
          )
      }
      when (rule.subject) {
        StatusConditionSubject.SUBMISSIONS -> {
          if (rule.minSubmissions < 1) {
            issues +=
              EditorIssue(
                null,
                message = EditorIssueMessage.STATUS_RULE_MIN_SUBMISSIONS.message(),
              )
          }
        }
        StatusConditionSubject.QUESTION -> {
          val question = form.find(rule.questionKey)
          when {
            question == null || question.type == EditorQuestionType.NOTE ->
              issues +=
                EditorIssue(
                  null,
                  message = EditorIssueMessage.STATUS_RULE_QUESTION_MISSING.message(),
                )
            rule.operator !in RelevanceOperator.availableFor(question.type) ->
              issues +=
                EditorIssue(
                  question.key,
                  message =
                    EditorIssueMessage.STATUS_RULE_OPERATOR_NOT_APPLICABLE.message(
                      DomainMessage.Label(rule.operator),
                      DomainMessage.Label(question.type),
                    ),
                )
            rule.operator.needsValue && rule.value.isBlank() ->
              issues +=
                EditorIssue(
                  question.key,
                  message = EditorIssueMessage.STATUS_RULE_VALUE_MISSING.message(question.name),
                )
            question.type.isNumeric &&
              rule.operator.needsValue &&
              rule.value.toDoubleOrNull() == null ->
              issues +=
                EditorIssue(
                  question.key,
                  message = EditorIssueMessage.STATUS_RULE_VALUE_NOT_NUMBER.message(question.name),
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
                  message =
                    when (target?.isMapLayer) {
                      true -> EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING_MAP_LAYER
                      false -> EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING_DATA_TABLE
                      null -> EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING
                    }.message(),
                )
            form.saveTo.mode == SaveToMode.CREATE &&
              form.questions.none { it.name == prop.name && it.type != EditorQuestionType.NOTE } ->
              issues +=
                EditorIssue(
                  null,
                  message = EditorIssueMessage.STATUS_RULE_PROPERTY_UNWRITTEN.message(prop.name),
                )
            rule.operator !in SaveToRules.operatorsForProperty(prop.kind) ->
              issues +=
                EditorIssue(
                  null,
                  message =
                    EditorIssueMessage.STATUS_RULE_PROPERTY_OPERATOR_NOT_APPLICABLE.message(
                      DomainMessage.Label(rule.operator),
                      DomainMessage.Lowercase(DomainMessage.Label(prop.kind)),
                      prop.name,
                    ),
                )
            rule.operator.needsValue && rule.value.isBlank() ->
              issues +=
                EditorIssue(
                  null,
                  message =
                    EditorIssueMessage.STATUS_RULE_PROPERTY_VALUE_MISSING.message(prop.name),
                )
            isNumeric && rule.operator.needsValue && rule.value.toDoubleOrNull() == null ->
              issues +=
                EditorIssue(
                  null,
                  message =
                    EditorIssueMessage.STATUS_RULE_PROPERTY_VALUE_NOT_NUMBER.message(prop.name),
                )
          }
        }
      }
    }
    return issues
  }
}
