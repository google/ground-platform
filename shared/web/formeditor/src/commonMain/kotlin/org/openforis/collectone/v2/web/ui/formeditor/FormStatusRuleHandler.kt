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
package org.openforis.collectone.v2.web.ui.formeditor

import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusBadge
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusConfig
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject

/**
 * [StatusRuleActions] of the Form editor: the status markers a Form that updates features sets on
 * them, applied to the Form of [session].
 */
internal class FormStatusRuleHandler(private val session: FormDraftSession) : StatusRuleActions {
  override fun setStatusEnabled(enabled: Boolean) = session.mutateForm { form ->
    val current = form.saveTo.status
    val rules =
      if (enabled && current.rules.isEmpty()) EditorStatusConfig.defaultRules() else current.rules
    form.copy(saveTo = form.saveTo.copy(status = current.copy(enabled = enabled, rules = rules)))
  }

  /**
   * Appends a new status rule, inserting it immediately before a trailing catch-all
   * (`minSubmissions <= 1`) when present because status rules evaluate first-match-wins and any
   * rule placed after a catch-all would be unreachable.
   */
  override fun addStatusRule() {
    val current = session.form.saveTo.status
    val savable = SaveToRules.savableQuestions(session.form)
    val defaultQuestion = savable.firstOrNull { it.type.hasChoices } ?: savable.firstOrNull()
    val newRule =
      if (defaultQuestion != null) {
        val op = RelevanceOperator.availableFor(defaultQuestion.type).first()
        val valDefault =
          if (op.needsValue) {
            defaultQuestion.resolvedChoices(session.datasets).firstOrNull()?.value
              ?: if (defaultQuestion.type.isNumeric) "1" else "yes"
          } else {
            ""
          }
        EditorStatusRule(
          subject = StatusConditionSubject.QUESTION,
          questionKey = defaultQuestion.key,
          operator = op,
          value = valDefault,
          badge = EditorStatusBadge.IN_PROGRESS,
        )
      } else {
        EditorStatusRule(
          subject = StatusConditionSubject.SUBMISSIONS,
          minSubmissions = 2,
          badge = EditorStatusBadge.SURVEYED,
        )
      }
    val list = current.rules.toMutableList()
    val lastIsCatchAll =
      list.lastOrNull()?.let {
        it.subject == StatusConditionSubject.SUBMISSIONS && it.minSubmissions <= 1
      } == true
    val insertIndex = if (lastIsCatchAll) list.lastIndex else list.size
    list.add(insertIndex, newRule)
    session.mutateForm { it.copy(saveTo = it.saveTo.copy(status = current.copy(rules = list))) }
  }

  override fun updateStatusRule(index: Int, transform: (EditorStatusRule) -> EditorStatusRule) =
    session.mutateForm { form ->
      val current = form.saveTo.status
      if (index !in current.rules.indices) return@mutateForm form
      val updated = current.rules.mapIndexed { i, r -> if (i == index) transform(r) else r }
      form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = updated)))
    }

  override fun removeStatusRule(index: Int) = session.mutateForm { form ->
    val current = form.saveTo.status
    if (index !in current.rules.indices) return@mutateForm form
    val updated = current.rules.filterIndexed { i, _ -> i != index }
    form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = updated)))
  }

  override fun moveStatusRule(index: Int, delta: Int) = session.mutateForm { form ->
    val current = form.saveTo.status
    val targetIndex = index + delta
    if (index !in current.rules.indices || targetIndex !in current.rules.indices) {
      return@mutateForm form
    }
    val list = current.rules.toMutableList()
    val item = list.removeAt(index)
    list.add(targetIndex, item)
    form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = list)))
  }

  override fun updateDefaultStatusBadge(transform: (EditorStatusBadge) -> EditorStatusBadge) =
    session.mutateForm { form ->
      val current = form.saveTo.status
      form.copy(
        saveTo =
          form.saveTo.copy(status = current.copy(defaultBadge = transform(current.defaultBadge)))
      )
    }
}
