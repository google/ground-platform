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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_status_rule_condition
import org.openforis.collectone.v2.app.resources.domain_status_rule_condition_value
import org.openforis.collectone.v2.app.resources.domain_status_rule_min_submissions
import org.openforis.collectone.v2.app.resources.domain_status_rule_missing_question
import org.openforis.collectone.v2.app.resources.domain_status_rule_property_fallback
import org.openforis.collectone.v2.app.resources.domain_status_rule_when_submitted

/** Localized summary of [rule]'s condition, e.g. `At least 2 submissions`. */
fun statusRuleSummaryText(
  form: EditorForm,
  target: EditorDataset?,
  rule: EditorStatusRule,
): UiText =
  when (rule.subject) {
    StatusConditionSubject.SUBMISSIONS ->
      if (rule.minSubmissions <= 1) Res.string.domain_status_rule_when_submitted.toUiText()
      else
        Res.plurals.domain_status_rule_min_submissions.toUiText(
          rule.minSubmissions,
          rule.minSubmissions,
        )
    StatusConditionSubject.QUESTION -> {
      val q = form.find(rule.questionKey)
      if (q == null) Res.string.domain_status_rule_missing_question.toUiText()
      else conditionText(q.name, rule)
    }
    StatusConditionSubject.ENTITY_PROPERTY -> {
      val propName: Any =
        target?.property(rule.property)?.name
          ?: rule.property
          ?: Res.string.domain_status_rule_property_fallback
      conditionText(propName, rule)
    }
  }

private fun conditionText(subject: Any, rule: EditorStatusRule): UiText =
  if (!rule.operator.needsValue) {
    Res.string.domain_status_rule_condition.toUiText(subject, rule.operator.labelRes)
  } else {
    Res.string.domain_status_rule_condition_value.toUiText(
      subject,
      rule.operator.labelRes,
      rule.value,
    )
  }
