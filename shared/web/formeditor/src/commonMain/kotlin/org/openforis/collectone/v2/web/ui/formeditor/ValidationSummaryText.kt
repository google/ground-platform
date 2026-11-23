/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.formeditor

import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern
import org.openforis.collectone.v2.app.domain.model.editor.ValidationCondition
import org.openforis.collectone.v2.app.domain.model.editor.ValidationRules
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.isoDateText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_validation_and
import org.openforis.collectone.v2.web.resources.formeditor_validation_date_not_in_future
import org.openforis.collectone.v2.web.resources.formeditor_validation_date_not_in_past
import org.openforis.collectone.v2.web.resources.formeditor_validation_date_on_or_after
import org.openforis.collectone.v2.web.resources.formeditor_validation_date_on_or_before
import org.openforis.collectone.v2.web.resources.formeditor_validation_length_at_least
import org.openforis.collectone.v2.web.resources.formeditor_validation_length_at_most
import org.openforis.collectone.v2.web.resources.formeditor_validation_length_between
import org.openforis.collectone.v2.web.resources.formeditor_validation_must
import org.openforis.collectone.v2.web.resources.formeditor_validation_number_at_least
import org.openforis.collectone.v2.web.resources.formeditor_validation_number_at_most
import org.openforis.collectone.v2.web.resources.formeditor_validation_number_between
import org.openforis.collectone.v2.web.resources.formeditor_validation_pattern_custom
import org.openforis.collectone.v2.web.resources.formeditor_validation_pattern_digits
import org.openforis.collectone.v2.web.resources.formeditor_validation_pattern_email
import org.openforis.collectone.v2.web.resources.formeditor_validation_pattern_letters
import org.openforis.collectone.v2.web.resources.formeditor_validation_selections_at_least
import org.openforis.collectone.v2.web.resources.formeditor_validation_selections_at_most
import org.openforis.collectone.v2.web.resources.formeditor_validation_selections_between

/**
 * The validation of a question of [type] in the user's language, e.g. "Must be between 0 and 120."
 * or "Select at least 2 options.", or `null` when [validation] sets no rule.
 */
internal fun validationSummaryText(
  type: EditorQuestionType,
  validation: EditorValidation?,
): UiText? {
  val conditions = ValidationRules.conditions(type, validation)
  if (conditions.isEmpty()) return null
  // A selection count is the only condition of a select multiple question, and a sentence.
  if (conditions.size == 1)
    selectionCountSentence(conditions.single())?.let {
      return it
    }
  val joined =
    conditions.map(::phrase).reduce { first, second ->
      Res.string.formeditor_validation_and.toUiText(first, second)
    }
  return Res.string.formeditor_validation_must.toUiText(joined)
}

private fun selectionCountSentence(condition: ValidationCondition?): UiText? =
  when (condition) {
    is ValidationCondition.SelectionsBetween ->
      Res.string.formeditor_validation_selections_between.toUiText(condition.min, condition.max)
    is ValidationCondition.SelectionsAtLeast ->
      Res.plurals.formeditor_validation_selections_at_least.toUiText(condition.min, condition.min)
    is ValidationCondition.SelectionsAtMost ->
      Res.plurals.formeditor_validation_selections_at_most.toUiText(condition.max, condition.max)
    else -> null
  }

/** [condition] as a phrase that completes "Must …", e.g. "be between 0 and 120". */
private fun phrase(condition: ValidationCondition): UiText =
  when (condition) {
    is ValidationCondition.NumberBetween ->
      Res.string.formeditor_validation_number_between.toUiText(condition.min, condition.max)
    is ValidationCondition.NumberAtLeast ->
      Res.string.formeditor_validation_number_at_least.toUiText(condition.min)
    is ValidationCondition.NumberAtMost ->
      Res.string.formeditor_validation_number_at_most.toUiText(condition.max)
    is ValidationCondition.LengthBetween ->
      Res.string.formeditor_validation_length_between.toUiText(condition.min, condition.max)
    is ValidationCondition.LengthAtLeast ->
      Res.plurals.formeditor_validation_length_at_least.toUiText(condition.min, condition.min)
    is ValidationCondition.LengthAtMost ->
      Res.plurals.formeditor_validation_length_at_most.toUiText(condition.max, condition.max)
    is ValidationCondition.MatchesPattern ->
      when (condition.pattern) {
        TextPattern.EMAIL -> Res.string.formeditor_validation_pattern_email
        TextPattern.DIGITS -> Res.string.formeditor_validation_pattern_digits
        TextPattern.LETTERS -> Res.string.formeditor_validation_pattern_letters
        TextPattern.CUSTOM -> Res.string.formeditor_validation_pattern_custom
      }.toUiText()
    ValidationCondition.NotInFuture ->
      Res.string.formeditor_validation_date_not_in_future.toUiText()
    ValidationCondition.NotInPast -> Res.string.formeditor_validation_date_not_in_past.toUiText()
    is ValidationCondition.OnOrAfter ->
      Res.string.formeditor_validation_date_on_or_after.toUiText(isoDateText(condition.date))
    is ValidationCondition.OnOrBefore ->
      Res.string.formeditor_validation_date_on_or_before.toUiText(isoDateText(condition.date))
    is ValidationCondition.SelectionsBetween,
    is ValidationCondition.SelectionsAtLeast,
    is ValidationCondition.SelectionsAtMost -> checkNotNull(selectionCountSentence(condition))
  }
