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

import org.openforis.collectone.v2.app.domain.model.editor.ChoiceColors
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoiceImage
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.editor.slugify
import org.openforis.collectone.v2.app.domain.usecase.ChoiceListImporter

/** Values of the two options a new select question starts with. */
private val PLACEHOLDER_CHOICE_VALUES = listOf("option_1", "option_2")

/** The two options a new select question starts with. */
internal fun defaultChoices(): List<EditorChoice> =
  listOf(EditorChoice("option_1", "Option 1"), EditorChoice("option_2", "Option 2"))

/**
 * [ChoiceActions] of the Form editor: a select question's choices, the dataset they come from, and
 * cascading parents, applied to the Form of [session]. Datasets for choice questions are created
 * through [createDatasetForChoiceQuestionCallback] when the editor is part of a survey.
 */
internal class FormChoiceHandler(
  private val session: FormDraftSession,
  private val createDatasetForChoiceQuestionCallback:
    ((questionIndex: Int, kind: DatasetKind) -> Unit)?,
) : ChoiceActions {
  private val updateTargets: List<EditorDataset>
    get() = session.datasets.filterNot { it.isLinkedToThisForm }

  override fun setChoiceSource(key: String, source: ChoiceSource) =
    session.updateQuestion(key) { q ->
      when (source) {
        ChoiceSource.MANUAL ->
          q.copy(
            choiceSource = ChoiceSource.MANUAL,
            choiceDatasetId = null,
            allowAddEntity = false,
            choices = if (q.choices.isEmpty()) defaultChoices() else q.choices,
          )
        ChoiceSource.MAP_LAYER,
        ChoiceSource.DATA_TABLE -> {
          val wantMapLayer = source == ChoiceSource.MAP_LAYER
          val candidates =
            updateTargets
              .filter { it.isMapLayer == wantMapLayer }
              .ifEmpty { session.datasets.filter { it.isMapLayer == wantMapLayer } }
          val currentId = q.choiceDatasetId?.takeIf { id -> candidates.any { it.id == id } }
          val nextId = currentId ?: candidates.firstOrNull()?.id
          val nextDataset = candidates.firstOrNull { it.id == nextId }
          val keepAllowAdd =
            q.allowAddEntity &&
              nextDataset != null &&
              !nextDataset.isGenerated &&
              (nextDataset.hasCreationForm || nextDataset.key.isEmpty())
          q.copy(
            choiceSource = source,
            choiceDatasetId = nextId,
            allowAddEntity = keepAllowAdd,
          )
        }
      }
    }

  override fun setChoiceDataset(key: String, datasetId: String?) =
    session.updateQuestion(key) { q ->
      val dataset = datasetId?.let { id -> session.datasets.firstOrNull { it.id == id } }
      val source =
        when {
          datasetId == null -> ChoiceSource.MANUAL
          dataset != null ->
            if (dataset.isMapLayer) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
          q.choiceSource.isDataset -> q.choiceSource
          else -> ChoiceSource.MAP_LAYER
        }
      val keepAllowAdd =
        q.allowAddEntity &&
          datasetId != null &&
          (dataset == null ||
            (!dataset.isGenerated && (dataset.hasCreationForm || dataset.key.isEmpty())))
      q.copy(
        choiceSource = source,
        choiceDatasetId = datasetId,
        allowAddEntity = keepAllowAdd,
      )
    }

  override fun setAllowAddEntity(key: String, allow: Boolean) =
    session.updateQuestion(key) { q ->
      if (!q.type.hasChoices || !q.usesDatasetChoices) q else q.copy(allowAddEntity = allow)
    }

  /**
   * Updates a choice's label in the active language. In the survey's default language,
   * `choice.value` (the XForms `<value>` stored in submissions) auto-tracks `slugify(label)` only
   * while it still matches the previous auto-slugged label, preserving custom values once edited
   * manually; in secondary languages only `labelTranslations` changes.
   */
  override fun updateChoiceLabel(key: String, index: Int, label: String) {
    val question = session.form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val state = session.uiState
    if (state.isDefaultLanguageSelected) {
      val newValue =
        if (old.value == slugify(old.label, old.value)) slugify(label, old.value) else old.value
      updateChoice(key, index, old.copy(value = newValue, label = label))
    } else {
      updateChoice(key, index, old.withLabelIn(state.activeLanguage, state.defaultLanguage, label))
    }
  }

  /**
   * Replaces choice [index] on question [key] and cascades any `value` rename into the display
   * logic (`EditorRelevance`) of downstream questions and sections, cascading-select children's
   * choice parent values (`EditorChoice.parentValue`), and feature status rules
   * (`EditorStatusRule`) that matched the previous value.
   */
  override fun updateChoice(key: String, index: Int, choice: EditorChoice) {
    val question = session.form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val choices = question.choices.toMutableList().apply { set(index, choice) }
    val renamed = old.value != choice.value
    session.mutateForm { current ->
      val form = current.withChoiceValueRenamed(key, old.value, choice.value)
      val cascadeChildKeys =
        if (renamed) form.cascadeChildrenOf(key).map { it.key }.toSet() else emptySet()
      form.copy(
        questions =
          form.questions.map { q ->
            val updated = if (q.key == key) q.copy(choices = choices) else q
            if (q.key in cascadeChildKeys) {
              updated.copy(
                choices =
                  updated.choices.map { c ->
                    if (c.parentValue == old.value) c.copy(parentValue = choice.value) else c
                  }
              )
            } else {
              updated
            }
          },
        saveTo =
          form.saveTo.copy(
            status =
              form.saveTo.status.copy(
                rules =
                  form.saveTo.status.rules.map { rule ->
                    if (
                      rule.subject == StatusConditionSubject.QUESTION &&
                        rule.questionKey == key &&
                        rule.value == old.value
                    ) {
                      rule.copy(value = choice.value)
                    } else {
                      rule
                    }
                  }
              )
          ),
      )
    }
  }

  override fun addChoice(key: String) =
    session.updateQuestion(key) { q ->
      val taken = q.choices.map { it.value }.toSet()
      var n = q.choices.size + 1
      while ("option_$n" in taken) n++
      q.copy(choices = q.choices + EditorChoice("option_$n", "Option $n"))
    }

  override fun removeChoice(key: String, index: Int) =
    session.updateQuestion(key) { q ->
      q.copy(choices = q.choices.filterIndexed { i, _ -> i != index })
    }

  override fun setChoiceColor(key: String, index: Int, colorHex: String?) {
    val color = colorHex?.uppercase()?.takeIf(ChoiceColors::isValidHex)
    if (colorHex != null && color == null) return
    session.updateChoiceAt(key, index) { it.copy(colorHex = color) }
  }

  override fun setChoiceImage(key: String, index: Int, image: EditorChoiceImage?): Boolean {
    if (session.isReadOnly) return false
    if (
      image != null &&
        (image.sizeBytes > EditorChoiceImage.MAX_BYTES || !image.mimeType.startsWith("image/"))
    ) {
      return false
    }
    if (session.form.find(key)?.choices?.getOrNull(index) == null) return false
    session.updateChoiceAt(key, index) { it.copy(image = image) }
    return true
  }

  override fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind) {
    if (session.isReadOnly) return
    if (createDatasetForChoiceQuestionCallback != null) {
      createDatasetForChoiceQuestionCallback.invoke(questionIndex, kind)
    } else {
      session.mutateForm { form ->
        form.copy(
          questions =
            form.questions.mapIndexed { idx, q ->
              if (idx == questionIndex) {
                q.copy(
                  choiceSource =
                    if (kind == DatasetKind.MAP_LAYER) ChoiceSource.MAP_LAYER
                    else ChoiceSource.DATA_TABLE,
                  choiceDatasetId = "new_dataset",
                )
              } else q
            }
        )
      }
    }
  }

  override fun createDatasetForChoiceQuestion(key: String, kind: DatasetKind) {
    val idx = session.observedForm.questions.indexOfFirst { it.key == key }
    if (idx >= 0) {
      createDatasetForChoiceQuestion(idx, kind)
    }
  }

  override fun updateQuestionParentKey(questionIndex: Int, parentQuestionKey: String) {
    val targetKey = session.observedForm.questions.getOrNull(questionIndex)?.key ?: return
    updateQuestionParentKey(targetKey, parentQuestionKey)
  }

  override fun updateQuestionParentKey(key: String, parentQuestionKey: String) {
    session.mutateForm { form ->
      form.copy(
        questions =
          form.questions.map { q ->
            if (q.key == key) q.copy(parentQuestionKey = parentQuestionKey) else q
          }
      )
    }
  }

  override fun updateChoiceParentValue(questionIndex: Int, choiceIndex: Int, parentValue: String) {
    val targetKey = session.observedForm.questions.getOrNull(questionIndex)?.key ?: return
    updateChoiceParentValue(targetKey, choiceIndex, parentValue)
  }

  override fun updateChoiceParentValue(key: String, choiceIndex: Int, parentValue: String) {
    session.updateChoiceAt(key, choiceIndex) { it.copy(parentValue = parentValue) }
  }

  override fun importQuestionChoices(
    questionIndex: Int,
    importedChoices: List<EditorChoice>,
    append: Boolean,
  ) {
    val targetKey = session.observedForm.questions.getOrNull(questionIndex)?.key ?: return
    importQuestionChoices(targetKey, importedChoices, append)
  }

  override fun importQuestionChoices(
    key: String,
    importedChoices: List<EditorChoice>,
    append: Boolean,
  ) {
    if (importedChoices.isEmpty()) return
    session.mutateForm { form ->
      form.copy(
        questions =
          form.questions.map { q ->
            if (q.key == key) {
              val shouldAppend = append && !q.hasOnlyPlaceholderChoices()
              val newChoices =
                if (shouldAppend) {
                  val existingValues = q.choices.map { it.value }.toSet()
                  q.choices + importedChoices.filterNot { it.value in existingValues }
                } else {
                  importedChoices
                }
              q.copy(choices = newChoices)
            } else q
          }
      )
    }
  }

  override fun importChoicesFromFile(
    key: String,
    text: String,
    fileName: String,
  ): ChoiceFileImport {
    val question = session.form.find(key) ?: return ChoiceFileImport.NothingToImport
    val parsed = ChoiceListImporter.parseChoices(text, fileName)
    if (parsed.isEmpty()) return ChoiceFileImport.NothingToImport
    if (question.choices.isNotEmpty() && !question.hasOnlyPlaceholderChoices()) {
      return ChoiceFileImport.NeedsConfirmation(parsed)
    }
    importQuestionChoices(key, parsed, append = false)
    return ChoiceFileImport.Replaced
  }

  /** Whether the question still has just the two options a new select question starts with. */
  private fun EditorQuestion.hasOnlyPlaceholderChoices(): Boolean =
    choices.map { it.value } == PLACEHOLDER_CHOICE_VALUES
}
