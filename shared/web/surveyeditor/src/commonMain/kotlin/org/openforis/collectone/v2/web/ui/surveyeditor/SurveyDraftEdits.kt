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

import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetIds
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.model.editor.toEditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.withRenamedTargetDataset
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.SaveToModeSwitch
import org.openforis.collectone.v2.web.ui.text.EditorDefaultNames
import org.openforis.collectone.v2.web.ui.text.EditorText

/**
 * Mutable working copy of a Survey editor draft for one edit, mirroring the draft's fields plus the
 * editor's selected [section] and [organizationNotice]. See [SurveyDraftSession.edit].
 */
internal class SurveyDraftEdits(
  draft: SurveyEditorDraft,
  section: SurveyEditorSection,
  organizationNotice: EditorText?,
  /** Starting names of the items this edit creates, in the language the editor shows. */
  val defaultNames: EditorDefaultNames = EditorDefaultNames.ENGLISH,
) {
  var details: SurveyDetails = draft.details
  var sharing: SharingSettings = draft.sharing
  var forms: List<SurveyEditorForm> = draft.forms
  var datasets: List<EntityDataset> = draft.datasets
  var section: SurveyEditorSection = section
  var organizationNotice: EditorText? = organizationNotice
  var nextId = draft.nextKeyId

  fun newKey(prefix: String) = "$prefix${nextId++}"

  fun toDraft() =
    SurveyEditorDraft(
      details = details,
      sharing = sharing,
      forms = forms,
      datasets = datasets,
      nextKeyId = nextId,
    )

  val mapLayers: List<EntityDataset>
    get() = datasets.filter { it.kind == DatasetKind.MAP_LAYER }

  fun form(key: String): SurveyEditorForm? = forms.firstOrNull { it.key == key }

  fun dataset(key: String): EntityDataset? = datasets.firstOrNull { it.key == key }

  /** The survey's datasets as seen by the save-to logic of Form [formKey]. */
  fun catalog(formKey: String) = datasets.map { it.toEditorDataset(formKey, forms) }

  fun updateForm(formKey: String, transform: (EditorForm) -> EditorForm) {
    val entry = form(formKey) ?: return
    val updated = transform(entry.form)
    if (updated == entry.form) return
    forms = forms.map { if (it.key == formKey) it.copy(form = updated) else it }
    if (updated.questions != entry.form.questions) syncDatasetsLinkedToForm(formKey)
    // Properties that linked questions save to inherit their concepts.
    if (updated.questions != entry.form.questions || updated.saveTo != entry.form.saveTo) {
      datasets = SaveToRules.inheritConcepts(updated, formKey, datasets)
    }
  }

  fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) {
    val old = dataset(key) ?: return
    val updated = transform(old)
    datasets = datasets.map { if (it.key == key) updated else it }
    if (updated.id != old.id) {
      forms = forms.map { it.copy(form = it.form.withRenamedTargetDataset(old.id, updated.id)) }
    }
  }

  /** Synchronizes the schema of all datasets linked to Form [formKey] with its questions. */
  fun syncDatasetsLinkedToForm(formKey: String) {
    val form = form(formKey)?.form ?: return
    if (datasets.none { it.linkedFormKey == formKey }) return
    datasets = datasets.map { d ->
      if (d.linkedFormKey != formKey) d else FormDatasetLinks.syncedWithForm(d, form)
    }
  }

  /** Creates a dataset linked to Form [formKey]; see [SurveyEditorActions.createDatasetForForm]. */
  fun createDatasetForForm(formKey: String, kind: DatasetKind?, open: Boolean) {
    val form = form(formKey)?.form ?: return
    val resolvedKind = kind ?: FormDatasetLinks.datasetKindFor(form)
    // Unlink any dataset previously linked to this form (and drop it if it has no features yet).
    val remainingDatasets = SaveToModeSwitch.detachLinkedDatasets(datasets, formKey)
    val datasetKey = newKey("d")
    val title =
      FormDatasetLinks.uniqueTitle(
        form.title.ifBlank { defaultNames.dataset(resolvedKind) },
        remainingDatasets.map { it.displayName },
      )
    val dataset =
      FormDatasetLinks.newLinkedDataset(
        form = form,
        key = datasetKey,
        formKey = formKey,
        displayName = title,
        id = DatasetIds.forName(title, remainingDatasets.map { it.id }),
        kind = resolvedKind,
      )
    datasets = remainingDatasets + dataset
    if (open) section = SurveyEditorSection.Dataset(dataset.key)
  }

  /**
   * Creates a dataset of [kind] and binds question [questionIndex] of Form [formKey] to it, keeping
   * the user on the form editor.
   */
  fun createDatasetForChoiceQuestion(
    formKey: String,
    questionIndex: Int,
    kind: DatasetKind,
  ) {
    val form = form(formKey)?.form ?: return
    if (questionIndex !in form.questions.indices) return
    val datasetKey = newKey("d")
    val title =
      FormDatasetLinks.uniqueTitle(
        defaultNames.dataset(kind),
        datasets.map { it.displayName },
      )
    val dataset =
      EntityDataset(
        key = datasetKey,
        kind = kind,
        id = DatasetIds.forName(title, datasets.map { it.id }),
        displayName = title,
        geometryType = GeometryType.POINT,
        keyProperty = "id",
        labelProperty = "name",
        properties =
          listOf(
            EntityProperty("id", "ID", PropertyType.TEXT, required = true),
            EntityProperty("name", "Name", PropertyType.TEXT),
          ),
      )
    datasets = datasets + dataset
    val newChoiceSource =
      if (kind == DatasetKind.MAP_LAYER) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
    updateForm(formKey) { f ->
      f.copy(
        questions =
          f.questions.mapIndexed { idx, q ->
            if (idx == questionIndex) {
              q.copy(choiceSource = newChoiceSource, choiceDatasetId = dataset.id)
            } else q
          }
      )
    }
  }
}
