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
package org.openforis.collectone.v2.web.ui.surveyeditor

import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.web.domain.model.editor.SaveToModeSwitch

/**
 * [FormDatasetLinkActions] of the Survey editor: the datasets Forms add features to or update, and
 * datasets created for choice questions. Changes made from a Form are recorded on its undo stack.
 */
internal class SurveyFormDatasetLinkHandler(private val session: SurveyDraftSession) :
  FormDatasetLinkActions {
  override fun createDatasetForForm(formKey: String, kind: DatasetKind?, open: Boolean) =
    session.editForForm(formKey) { createDatasetForForm(formKey, kind, open) }

  override fun createDatasetForChoiceQuestion(
    formKey: String,
    questionIndex: Int,
    kind: DatasetKind,
  ) = session.editForForm(formKey) { createDatasetForChoiceQuestion(formKey, questionIndex, kind) }

  override fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind) {
    val currentFormKey =
      (session.section as? SurveyEditorSection.Form)?.key
        ?: session.draft.forms.firstOrNull()?.key
        ?: return
    createDatasetForChoiceQuestion(currentFormKey, questionIndex, kind)
  }

  override fun createFormForDataset(datasetKey: String, open: Boolean) {
    val activeFormKey = if (!open) (session.section as? SurveyEditorSection.Form)?.key else null
    val block: SurveyDraftEdits.() -> Unit = {
      val dataset = dataset(datasetKey)
      if (dataset != null) {
        val formKey = newKey("f")
        val title =
          FormDatasetLinks.uniqueTitle("${dataset.displayName} form", forms.map { it.form.title })
        val form =
          EditorForm(
            formId = FormIds.newFormId(),
            title = title,
            questions = FormDatasetLinks.questionsFor(dataset),
          )
        // Update dataset to link to this form.
        updateDataset(datasetKey) { it.copy(linkedFormKey = formKey) }
        forms = forms + SurveyEditorForm(formKey, form)
        if (open) section = SurveyEditorSection.Form(formKey)
      }
    }
    if (activeFormKey != null) {
      session.editForForm(activeFormKey, block)
    } else {
      session.edit(block)
    }
  }

  override fun setFormSaveToMode(formKey: String, mode: SaveToMode) {
    if (session.draft.forms.none { it.key == formKey }) return
    session.editForForm(formKey) {
      val entry = form(formKey) ?: return@editForForm
      // Pick the update target while the form's own dataset is still linked, so it isn't chosen.
      var updatedSaveTo = SaveToModeSwitch.saveToFor(entry.form, catalog(formKey), mode)
      when (mode) {
        SaveToMode.UPDATE -> {
          datasets = SaveToModeSwitch.detachLinkedDatasets(datasets, formKey)
          val targetDataset = datasets.firstOrNull { it.id == updatedSaveTo.targetDatasetId }
          if (targetDataset != null && updatedSaveTo.mappings.none { it.property != null }) {
            val mappings =
              SaveToModeSwitch.defaultUpdateMappings(
                entry.form,
                updatedSaveTo.idQuestionKey,
                targetDataset,
              )
            updatedSaveTo = updatedSaveTo.copy(mappings = mappings)
          }
          updateForm(formKey) {
            val next = it.copy(saveTo = updatedSaveTo)
            next.copy(
              openInGoogleEarth =
                next.openInGoogleEarth && next.canOpenInGoogleEarth(catalog(formKey))
            )
          }
          val updatedForm = form(formKey)?.form
          if (targetDataset != null && updatedForm != null) {
            datasets = datasets.map { d ->
              if (d.key == targetDataset.key) {
                SurveyEditorDerivation.withMappedProperties(d, updatedForm)
              } else {
                d
              }
            }
            datasets = SaveToRules.inheritConcepts(updatedForm, formKey, datasets)
          }
        }
        SaveToMode.CREATE -> {
          updateForm(formKey) { it.copy(saveTo = updatedSaveTo, openInGoogleEarth = false) }
          if (datasets.none { it.linkedFormKey == formKey }) {
            createDatasetForForm(formKey, kind = null, open = false)
          }
        }
      }
    }
  }

  override fun unlinkDataset(datasetKey: String) {
    val targetFormKey =
      session.draft.datasets.firstOrNull { it.key == datasetKey }?.linkedFormKey
        ?: (session.section as? SurveyEditorSection.Form)?.key
    if (targetFormKey != null) {
      session.editForForm(targetFormKey) {
        updateDataset(datasetKey) { it.copy(linkedFormKey = null) }
      }
    } else {
      session.updateDataset(datasetKey) { it.copy(linkedFormKey = null) }
    }
  }
}
