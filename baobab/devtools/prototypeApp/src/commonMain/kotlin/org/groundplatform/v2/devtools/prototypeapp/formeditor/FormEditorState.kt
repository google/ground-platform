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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController

/**
 * Observable state holder for the Form editor page: the [EditorForm] under edit, the selected
 * screen, and the in-browser flow preview session.
 */
class FormEditorState(initialForm: EditorForm = FormEditorSamples.shadeTreeVisit()) {

  var form: EditorForm by mutableStateOf(initialForm)
    private set

  var selectedKey: String? by mutableStateOf(initialForm.questions.firstOrNull()?.key)
    private set

  /** Live preview runner, or `null` when the preview overlay is closed. */
  var previewController: FormWizardController? by mutableStateOf(null)
    private set

  /** Message shown when the generated XForms cannot be parsed for preview. */
  var previewError: String? by mutableStateOf(null)
    private set

  /** Whether the preview reached a validated submission. */
  var previewSubmitted: Boolean by mutableStateOf(false)
    private set

  var isXmlViewerOpen: Boolean by mutableStateOf(false)

  private var nextKeyId = initialForm.questions.size + 1

  val selectedQuestion: EditorQuestion?
    get() = form.find(selectedKey)

  val selectedIndex: Int
    get() = selectedKey?.let { form.indexOf(it) } ?: -1

  val flowEdges: List<FlowEdge>
    get() = FormFlowGraph.edges(form)

  val pathCount: Long
    get() = FormFlowGraph.countPaths(form)

  val issues: List<EditorIssue>
    get() = FormEditorValidator.validate(form)

  val xformsXml: String
    get() = EditorXFormsGenerator.toXml(form)

  fun issuesFor(key: String): List<EditorIssue> = issues.filter { it.questionKey == key }

  fun select(key: String?) {
    selectedKey = key?.takeIf { form.indexOf(it) >= 0 }
  }

  fun updateTitle(title: String) {
    form = form.copy(title = title)
  }

  fun updateFormId(formId: String) {
    form = form.copy(formId = formId)
  }

  /**
   * Inserts a new [type] question at [atIndex] (or after the selected one, or at the end if none
   * selected) and selects it.
   */
  fun addQuestion(type: EditorQuestionType, atIndex: Int? = null) {
    val key = newKey()
    val question =
      EditorQuestion(
        key = key,
        name = uniqueName(type.defaultNamePrefix),
        type = type,
        label =
          if (type == EditorQuestionType.NOTE) "New note"
          else "New ${type.label.lowercase()} question",
        choices = if (type.hasChoices) defaultChoices() else emptyList(),
      )
    val insertAt =
      when {
        atIndex != null -> atIndex.coerceIn(0, form.questions.size)
        selectedIndex >= 0 -> selectedIndex + 1
        else -> form.questions.size
      }
    form = form.copy(questions = form.questions.toMutableList().apply { add(insertAt, question) })
    selectedKey = key
  }

  /** Inserts a copy of [key] right after it and selects the copy. */
  fun duplicateQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val original = form.questions[index]
    val copy = original.copy(key = newKey(), name = uniqueName(original.name))
    form = form.copy(questions = form.questions.toMutableList().apply { add(index + 1, copy) })
    selectedKey = copy.key
  }

  /**
   * Removes [key]. Display logic on other questions that depended on it is cleared so the Form
   * stays valid, and the selection moves to the nearest remaining neighbor.
   */
  fun deleteQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val remaining =
      form.questions
        .filterNot { it.key == key }
        .map { if (it.relevance?.sourceQuestionKey == key) it.copy(relevance = null) else it }
    form = form.copy(questions = remaining)
    if (selectedKey == key) {
      selectedKey = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key
    }
  }

  /** Moves [key] by [delta] positions (negative = earlier). */
  fun moveQuestion(key: String, delta: Int) {
    val index = form.indexOf(key)
    val target = index + delta
    if (index < 0 || target !in form.questions.indices) return
    val list = form.questions.toMutableList()
    val item = list.removeAt(index)
    list.add(target, item)
    form = form.copy(questions = list)
  }

  /** Moves [key] so it lands at [targetIndex]. */
  fun moveQuestionTo(key: String, targetIndex: Int) {
    val index = form.indexOf(key)
    if (index < 0) return
    moveQuestion(key, targetIndex.coerceIn(0, form.questions.lastIndex) - index)
  }

  fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion) {
    form = form.copy(questions = form.questions.map { if (it.key == key) transform(it) else it })
  }

  /**
   * Replaces choice [index] of [key]. If the choice value was derived from its label, it follows
   * label edits; dependents comparing against the old value are rewritten to the new one.
   */
  fun updateChoiceLabel(key: String, index: Int, label: String) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val newValue =
      if (old.value == slugify(old.label, old.value)) slugify(label, old.value) else old.value
    updateChoice(key, index, EditorChoice(newValue, label))
  }

  fun updateChoice(key: String, index: Int, choice: EditorChoice) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val choices = question.choices.toMutableList().apply { set(index, choice) }
    form =
      form.copy(
        questions =
          form.questions.map { q ->
            val relevance = q.relevance
            when {
              q.key == key -> q.copy(choices = choices)
              relevance?.sourceQuestionKey == key && relevance.value == old.value ->
                q.copy(relevance = relevance.copy(value = choice.value))
              else -> q
            }
          }
      )
  }

  fun addChoice(key: String) {
    updateQuestion(key) { q ->
      val taken = q.choices.map { it.value }.toSet()
      var n = q.choices.size + 1
      while ("option_$n" in taken) n++
      q.copy(choices = q.choices + EditorChoice("option_$n", "Option $n"))
    }
  }

  fun removeChoice(key: String, index: Int) {
    updateQuestion(key) { q ->
      q.copy(choices = q.choices.filterIndexed { i, _ -> i != index })
    }
  }

  /** Changes the type of [key], seeding choices and dropping display logic that no longer fits. */
  fun changeType(key: String, type: EditorQuestionType) {
    updateQuestion(key) { q ->
      q.copy(
        type = type,
        choices = if (type.hasChoices && q.choices.isEmpty()) defaultChoices() else q.choices,
        required = q.required && !type.isReadOnly,
      )
    }
    // Dependents whose operator is incompatible with the new type fall back to "is answered".
    val allowed = RelevanceOperator.availableFor(type)
    form =
      form.copy(
        questions =
          form.questions.map { q ->
            val relevance = q.relevance
            if (relevance?.sourceQuestionKey == key && relevance.operator !in allowed) {
              q.copy(relevance = relevance.copy(operator = allowed.first(), value = ""))
            } else {
              q
            }
          }
      )
  }

  /** Turns display logic on for [key] using the nearest earlier question as a default source. */
  fun enableRelevance(key: String) {
    val source = form.eligibleRelevanceSources(key).lastOrNull() ?: return
    val operator = RelevanceOperator.availableFor(source.type).first()
    val value = if (operator.needsValue) source.choices.firstOrNull()?.value.orEmpty() else ""
    updateQuestion(key) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  fun disableRelevance(key: String) {
    updateQuestion(key) { it.copy(relevance = null) }
  }

  fun startPreview() {
    previewSubmitted = false
    previewController =
      try {
        previewError = null
        FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xformsXml))
      } catch (e: Exception) {
        previewError = e.message ?: e.toString()
        null
      }
  }

  fun restartPreview() = startPreview()

  fun markPreviewSubmitted() {
    previewSubmitted = true
  }

  fun closePreview() {
    previewController = null
    previewError = null
    previewSubmitted = false
  }

  private fun newKey(): String {
    var candidate: String
    do {
      candidate = "q${nextKeyId++}"
    } while (form.indexOf(candidate) >= 0)
    return candidate
  }

  private fun uniqueName(base: String): String {
    val taken = form.questions.map { it.name }.toSet()
    val stem = base.replace(Regex("_\\d+$"), "")
    if (stem !in taken) return stem
    var n = 2
    while ("${stem}_$n" in taken) n++
    return "${stem}_$n"
  }

  private fun defaultChoices(): List<EditorChoice> =
    listOf(EditorChoice("option_1", "Option 1"), EditorChoice("option_2", "Option 2"))
}
