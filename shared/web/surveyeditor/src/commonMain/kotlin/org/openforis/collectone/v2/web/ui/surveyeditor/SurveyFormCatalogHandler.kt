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

import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetIds
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.FormConceptLinkSync
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.FormEditorValidator
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.formeditor.moved

/**
 * [FormCatalogActions] of the Survey editor: adding Forms (blank, imported, or from the library in
 * [libraryContext]), deleting, and reordering them. [onFormImported] and [onFormDeleted] let the
 * owner follow up on the Form's editor.
 */
internal class SurveyFormCatalogHandler(
  private val session: SurveyDraftSession,
  private val libraryContext: StateFlow<FormLibraryContext>,
  private val onFormImported: (formKey: String) -> Unit,
  private val onFormDeleted: (formKey: String) -> Unit,
) : FormCatalogActions {
  override fun addForm() = session.edit {
    val title = FormDatasetLinks.uniqueTitle(defaultNames.form, forms.map { it.form.title })
    val formId = FormIds.newFormId()
    val formKey = newKey("f")
    val datasetKey = newKey("d")
    val blankForm = EditorFormTemplates.blank(formId, title)

    // By default, submissions add to a linked Map layer (if the form captures a location) or Data
    // table (otherwise).
    val linkedDataset =
      FormDatasetLinks.newLinkedDataset(
        form = blankForm,
        key = datasetKey,
        formKey = formKey,
        displayName = title,
        id = DatasetIds.forName(title, datasets.map { it.id }),
      )
    datasets = datasets + linkedDataset
    forms = forms + SurveyEditorForm(formKey, blankForm)
    section = SurveyEditorSection.Form(formKey)
  }

  override fun addFormFromTemplate(templateId: String, openDataset: Boolean): String? {
    val template = libraryContext.value.library.formTemplate(templateId) ?: return null
    var formKey: String? = null
    session.edit {
      val (updated, key) = SurveyFormTemplates.addTemplate(toDraft(), template)
      forms = updated.forms
      datasets = updated.datasets
      nextId = updated.nextKeyId
      val linkedDataset = datasets.firstOrNull { it.linkedFormKey == key }
      section =
        if (openDataset && linkedDataset != null) SurveyEditorSection.Dataset(linkedDataset.key)
        else SurveyEditorSection.Form(key)
      formKey = key
    }
    return formKey
  }

  override fun addFormsForPurpose(
    packId: String,
    starterTemplateId: String,
    programIds: List<String>,
  ): String? {
    val library = libraryContext.value.library
    val pack = library.purposePack(packId) ?: return null
    val starter = pack.starterTemplate(starterTemplateId)
    val templates = starter.formTemplateIds.mapNotNull(library::formTemplate)
    if (templates.isEmpty()) return null
    var firstKey: String? = null
    session.edit {
      var draft = toDraft()
      templates.forEach { template ->
        val (updated, key) = SurveyFormTemplates.addTemplate(draft, template)
        draft = updated
        if (firstKey == null) firstKey = key
      }
      forms = draft.forms
      datasets = draft.datasets
      nextId = draft.nextKeyId
      firstKey?.let { section = SurveyEditorSection.Form(it) }
    }
    session.updateDetails { d ->
      d.copy(
        purposeIds = (d.purposeIds + packId).distinct(),
        programIds = (d.programIds + programIds).distinct(),
      )
    }
    return firstKey
  }

  override fun importForm(imported: ImportedForm): String {
    var formKey = ""
    session.edit {
      formKey = newKey("f")
      val title =
        FormDatasetLinks.uniqueTitle(
          imported.form.title.ifBlank { "Imported form" },
          forms.map { it.form.title },
        )
      val baseId =
        imported.form.formId.takeIf { it.isNotBlank() && it != "data" } ?: FormIds.newFormId()
      val formId = FormDatasetLinks.uniqueId(baseId, forms.map { it.form.formId })
      val datasetIds = datasets.map { it.id }.toSet()
      val questions =
        imported.form.questions.map { q ->
          if (
            q.choiceDatasetId != null && q.choiceDatasetId !in datasetIds && q.choices.isNotEmpty()
          ) {
            q.copy(
              choiceDatasetId = null,
              choiceSource = ChoiceSource.MANUAL,
              allowAddEntity = false,
            )
          } else {
            q
          }
        }
      // Imported `collectone:concept` links stay linked only when the concept is in the dictionary.
      val library = libraryContext.value.library
      var form =
        FormConceptLinkSync.reconcileImported(
          imported.form.copy(formId = formId, title = title, questions = questions)
        ) {
          library.concept(it) != null
        }
      if (form.saveTo.mode == SaveToMode.UPDATE) {
        val requestedId = form.saveTo.targetDatasetId
        val targetIndex =
          datasets.indexOfFirst { it.id == requestedId }.takeIf { it >= 0 }
            ?: datasets.indexOfFirst { !it.isGenerated }.takeIf { it >= 0 }
            ?: datasets.indices.firstOrNull()
        if (targetIndex != null) {
          val targetDataset = datasets[targetIndex]
          val targetId = targetDataset.id
          if (targetId != requestedId) {
            val validProps = targetDataset.properties.map { it.name }.toSet()
            val validMatch =
              imported.idMatchColumn in setOf("name", "label") ||
                imported.idMatchColumn in validProps
            form =
              form.copy(
                saveTo =
                  form.saveTo.copy(
                    idSource =
                      if (validMatch) form.saveTo.idSource else EntityIdSource.SELECTED_FEATURE,
                    idQuestionKey = if (validMatch) form.saveTo.idQuestionKey else null,
                    mappings = form.saveTo.mappings.filter { it.property in validProps },
                  )
              )
          }
          form =
            form.copy(
              saveTo =
                SurveyEditorDerivation.updateSaveTo(form, imported, formKey, datasets, targetId)
            )
          if (targetId == requestedId) {
            datasets =
              datasets.toMutableList().apply {
                set(
                  targetIndex,
                  SurveyEditorDerivation.withMappedProperties(get(targetIndex), form),
                )
              }
          }
        }
      } else {
        val linkIndex =
          imported.createDatasetId?.let { id ->
            datasets
              .indexOfFirst { it.id == id && it.linkedFormKey == null && !it.isGenerated }
              .takeIf { it >= 0 }
          }
        if (linkIndex != null) {
          datasets =
            datasets.toMutableList().apply {
              val linked = get(linkIndex).copy(linkedFormKey = formKey)
              set(linkIndex, FormDatasetLinks.syncedWithForm(linked, form))
            }
        } else {
          val datasetTitle = FormDatasetLinks.uniqueTitle(title, datasets.map { it.displayName })
          val linkedDataset =
            FormDatasetLinks.newLinkedDataset(
              form = form,
              key = newKey("d"),
              formKey = formKey,
              displayName = datasetTitle,
              id =
                imported.createDatasetId
                  ?.takeIf { FormEditorValidator.isValidName(it) }
                  ?.let { FormDatasetLinks.uniqueId(it, datasets.map { d -> d.id }) }
                  ?: DatasetIds.forName(datasetTitle, datasets.map { it.id }),
            )
          datasets = datasets + linkedDataset
        }
      }
      datasets = SaveToRules.inheritConcepts(form, formKey, datasets)
      val normalizedForm =
        form.copy(
          openInGoogleEarth = form.openInGoogleEarth && form.canOpenInGoogleEarth(catalog(formKey))
        )
      forms = forms + SurveyEditorForm(formKey, normalizedForm)
      section = SurveyEditorSection.Form(formKey)
    }
    if (formKey.isNotEmpty()) onFormImported(formKey)
    return formKey
  }

  override fun deleteForm(key: String) {
    session.edit {
      val index = forms.indexOfFirst { it.key == key }
      if (index < 0) return@edit
      // Unlink any datasets linked to this deleted form.
      datasets = datasets.map { if (it.linkedFormKey == key) it.copy(linkedFormKey = null) else it }
      forms = forms.filterNot { it.key == key }
      section =
        forms.getOrNull(index.coerceAtMost(forms.lastIndex))?.let {
          SurveyEditorSection.Form(it.key)
        } ?: SurveyEditorSection.Details
    }
    onFormDeleted(key)
  }

  override fun moveForm(key: String, toIndex: Int) = session.edit {
    forms = forms.moved(forms.indexOfFirst { it.key == key }, toIndex)
  }
}
