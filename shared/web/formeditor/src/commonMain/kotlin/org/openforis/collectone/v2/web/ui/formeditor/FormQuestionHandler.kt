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

import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.web.ui.text.EditorDefaultNames

/**
 * [QuestionActions] and [SectionActions] of the Form editor: inserting, duplicating, reordering,
 * retyping, and editing questions and sections, applied to the Form of [session].
 *
 * Structural transforms come from `FormStructureEdits.kt`; this handler resolves selection
 * transitions, localized label/hint edits, and default relevance/validation settings around them.
 */
internal class FormQuestionHandler(
  private val session: FormDraftSession,
  private val keys: QuestionKeyAllocator,
  private val defaultNames: () -> EditorDefaultNames,
) : QuestionActions, SectionActions {

  override fun addQuestion(type: EditorQuestionType, atIndex: Int?) {
    addQuestion(type, atIndex, sectionId = null, afterSectionId = null)
  }

  override fun addQuestion(
    type: EditorQuestionType,
    atIndex: Int?,
    sectionId: String?,
    afterSectionId: String?,
  ) {
    val form = session.form
    val key = newKey()
    val question =
      EditorQuestion(
        key = key,
        name = uniqueName(type.defaultNamePrefix),
        type = type,
        label = defaultNames().question(type),
        choices = if (type.hasChoices) defaultChoices() else emptyList(),
        decimalPlaces = if (type.isNumeric) EditorQuestion.DEFAULT_DECIMAL_PLACES else 0,
        sectionId = sectionId ?: form.sectionIdForInsertAt(atIndex),
      )
    val selectedIndex = session.selectedKey?.let(form::indexOf) ?: -1
    // Without an explicit position, a new question goes right after the selected one.
    val insertAt =
      when {
        atIndex != null -> atIndex.coerceIn(0, form.questions.size)
        selectedIndex >= 0 -> selectedIndex + 1
        else -> form.questions.size
      }
    session.mutateForm(selectAfter = { key }) {
      it.insertingQuestion(question, insertAt, afterSectionId = afterSectionId)
    }
  }

  override fun duplicateQuestion(key: String) {
    val form = session.form
    val index = form.indexOf(key)
    if (index < 0) return
    val original = form.questions[index]
    val copy = original.copy(key = newKey(), name = uniqueName(original.name))
    session.mutateForm(selectAfter = { copy.key }) { it.insertingQuestion(copy, index + 1) }
  }

  override fun duplicateQuestionBranch(key: String) {
    val branchKeys = session.form.branchKeys(key)
    if (branchKeys.size <= 1) {
      duplicateQuestion(key)
      return
    }
    var firstCopyKey: String? = null
    session.mutateForm(selectAfter = { firstCopyKey }) { currentForm ->
      val copies = currentForm.copiesOf(currentForm.questions.filter { it.key in branchKeys }, keys)
      firstCopyKey = copies.firstOrNull()?.key
      val insertAt = currentForm.questions.indexOfLast { it.key in branchKeys } + 1
      currentForm.copy(
        questions = currentForm.questions.toMutableList().apply { addAll(insertAt, copies) }
      )
    }
  }

  override fun deleteQuestion(key: String) {
    val form = session.form
    val index = form.indexOf(key)
    if (index < 0) return
    val remaining = form.questionsWithout(setOf(key))
    // Deleting the selected question selects the one that takes its place (or the new last one).
    val nextSelectedKey =
      if (session.selectedKey == key) {
        remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key
      } else {
        session.selectedKey
      }
    session.mutateForm(selectAfter = { nextSelectedKey }) { it.removingQuestion(key) }
  }

  override fun moveQuestion(key: String, delta: Int) {
    val form = session.form
    val index = form.indexOf(key)
    if (index < 0 || index + delta !in form.questions.indices) return
    session.mutateForm { it.movingQuestion(index, delta) }
  }

  override fun moveQuestionTo(key: String, targetIndex: Int, targetSectionId: String?) {
    val fromIndex = session.form.indexOf(key)
    if (fromIndex < 0) return
    session.mutateForm { it.movingQuestionTo(fromIndex, targetIndex, targetSectionId) }
  }

  override fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion) =
    session.updateQuestion(key, transform)

  override fun updateQuestionLabel(key: String, label: String) {
    val state = session.uiState
    updateQuestion(key) { it.withLabelIn(state.activeLanguage, state.defaultLanguage, label) }
  }

  override fun updateQuestionHint(key: String, hint: String) {
    val state = session.uiState
    updateQuestion(key) { it.withHintIn(state.activeLanguage, state.defaultLanguage, hint) }
  }

  override fun changeType(key: String, type: EditorQuestionType) = session.mutateForm {
    it.retypingQuestion(key, type, ::defaultChoices)
  }

  override fun updateCapture(key: String, capture: GeometryCapture) =
    updateQuestion(key) { it.copy(capture = capture) }

  override fun updateMediaSource(key: String, source: MediaSource) =
    updateQuestion(key) { it.copy(mediaSource = source) }

  override fun updateDecimalPlaces(key: String, decimalPlaces: Int) {
    val coerced = decimalPlaces.coerceIn(0, EditorQuestion.MAX_DECIMAL_PLACES)
    updateQuestion(key) { it.copy(decimalPlaces = coerced) }
  }

  override fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation) =
    updateQuestion(key) { q ->
      val updated = transform(q.validation ?: EditorValidation())
      q.copy(validation = updated.takeIf { it != EditorValidation() })
    }

  override fun enableRelevance(key: String) {
    val source = session.form.eligibleRelevanceSources(key).lastOrNull() ?: return
    val operator = RelevanceOperator.availableFor(source.type).first()
    val value =
      if (operator.needsValue) {
        source.resolvedChoices(session.datasets).firstOrNull()?.value.orEmpty()
      } else {
        ""
      }
    updateQuestion(key) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  override fun disableRelevance(key: String) = updateQuestion(key) { it.copy(relevance = null) }

  // --- Sections ---

  override fun selectSection(sectionId: String?) = session.selectSection(sectionId)

  override fun duplicateSection(sectionId: String) {
    if (session.form.section(sectionId) == null) return
    var newSecId = ""
    session.mutateForm { currentForm ->
      val duplicated = currentForm.duplicatingSection(sectionId, keys)
      if (duplicated != null) {
        newSecId = duplicated.second
        duplicated.first
      } else {
        currentForm
      }
    }
    if (newSecId.isNotEmpty()) {
      session.selectSection(newSecId)
    }
  }

  override fun addSection(
    title: String,
    repeatable: Boolean,
    atIndex: Int?,
    afterSectionId: String?,
  ): String {
    val newId = session.observedForm.newSectionId()
    val newSection = EditorSection(id = newId, title = title, repeatable = repeatable)
    val selectedKey = session.selectedKey
    session.mutateForm { form ->
      form.insertingSection(
        newSection = newSection,
        atIndex = atIndex,
        selectedKey = selectedKey,
        afterSectionId = afterSectionId,
      )
    }
    return newId
  }

  override fun updateSection(sectionId: String, transform: (EditorSection) -> EditorSection) =
    session.mutateForm { form ->
      form.copy(sections = form.sections.map { if (it.id == sectionId) transform(it) else it })
    }

  override fun deleteSection(sectionId: String) = session.mutateForm { form ->
    form.copy(
      sections = form.sections.filterNot { it.id == sectionId },
      questions =
        form.questions.map { q -> if (q.sectionId == sectionId) q.copy(sectionId = "") else q },
      deletedQuestions =
        form.deletedQuestions.map { q ->
          if (q.sectionId == sectionId) q.copy(sectionId = "") else q
        },
    )
  }

  override fun setQuestionSection(questionIndex: Int, sectionId: String) {
    val targetKey = session.observedForm.questions.getOrNull(questionIndex)?.key ?: return
    setQuestionSection(targetKey, sectionId)
  }

  override fun setQuestionSection(key: String, sectionId: String) = session.mutateForm { form ->
    form.assigningSection(key, sectionId)
  }

  override fun moveSection(sectionId: String, delta: Int) = session.mutateForm { form ->
    form.movingSection(sectionId, delta)
  }

  override fun enableSectionRelevance(sectionId: String) {
    val form = session.form
    if (form.section(sectionId)?.relevance != null) return
    // Like a question's, the default condition tests the closest question asked before it.
    val source = form.eligibleSectionRelevanceSources(sectionId).lastOrNull() ?: return
    val operator = RelevanceOperator.availableFor(source.type).first()
    val value =
      if (operator.needsValue) {
        source.resolvedChoices(session.datasets).firstOrNull()?.value.orEmpty()
      } else {
        ""
      }
    updateSection(sectionId) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  override fun disableSectionRelevance(sectionId: String) =
    updateSection(sectionId) { it.copy(relevance = null) }

  private fun newKey(): String = keys.allocate { candidate ->
    session.form.allQuestions.any { it.key == candidate }
  }

  private fun uniqueName(base: String): String =
    uniqueName(nameStem(base), session.form.allQuestions.map { it.name }.toSet())
}
