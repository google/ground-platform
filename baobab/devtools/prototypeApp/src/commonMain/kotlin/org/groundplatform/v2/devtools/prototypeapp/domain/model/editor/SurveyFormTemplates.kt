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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText

/**
 * Copying Form templates into surveys and saving survey Forms as templates (see
 * `docs/technical/model/library/02-templates-and-purpose-packs.md`, "Form Templates"). Pure.
 */
object SurveyFormTemplates {
  /**
   * [draft] with a copy of [template]'s Form added, plus the Map layer or Data table its
   * submissions add to (like **Blank form**), and the Form's key. The copy gets a new Form ID and a
   * title unique in the survey; it keeps the template's questions, concept links, and choice codes,
   * and later edits to the template don't change it. Choices loaded from a dataset the survey
   * doesn't have fall back to the template's own choices.
   */
  fun addTemplate(
    draft: SurveyEditorDraft,
    template: FormTemplate,
    newFormId: () -> String = FormIds::newFormId,
  ): Pair<SurveyEditorDraft, String> {
    val language = draft.details.defaultLanguage
    val baseTitle =
      template.title.get(language).ifBlank { template.form.title }.ifBlank { "New form" }
    val title = FormDatasetLinks.uniqueTitle(baseTitle, draft.forms.map { it.form.title })
    val datasetIds = draft.datasets.map { it.id }
    val questions =
      template.form.questions.map { q ->
        if (q.choiceDatasetId != null && q.choiceDatasetId !in datasetIds) {
          q.copy(choiceDatasetId = null, choiceSource = ChoiceSource.MANUAL, allowAddEntity = false)
        } else {
          q
        }
      }
    val form =
      template.form.copy(
        formId = newFormId(),
        title = title,
        questions = questions,
        saveTo = EditorSaveTo(),
      )
    var nextKeyId = draft.nextKeyId
    val formKey = "f${nextKeyId++}"
    val datasetKey = "d${nextKeyId++}"
    val properties = FormDatasetLinks.linkedProperties(form)
    val dataset =
      EntityDataset(
        key = datasetKey,
        kind = FormDatasetLinks.datasetKindFor(form),
        id = FormDatasetLinks.uniqueId(slugify(title), datasetIds),
        displayName = FormDatasetLinks.uniqueTitle(title, draft.datasets.map { it.displayName }),
        geometryKind = FormDatasetLinks.geometryKindFor(form),
        keyProperty = properties.first().name,
        labelProperty = properties.getOrNull(1)?.name ?: properties.first().name,
        linkedFormKey = formKey,
        properties = properties,
      )
    val updated =
      draft.copy(
        forms = draft.forms + SurveyEditorForm(formKey, form),
        datasets = SaveToRules.inheritConcepts(form, formKey, draft.datasets + dataset),
        nextKeyId = nextKeyId,
      )
    return updated to formKey
  }

  /**
   * A template [id] of [organizationId]'s library holding a copy of survey Form [form], for **Save
   * as template**. Where the Form saves its submissions is survey-specific, so it's reset (a survey
   * adding the template gets its own dataset); questions loaded from a survey dataset keep their
   * listed choices. Concept links and choice codes are kept, except that global templates only keep
   * links to global concepts.
   */
  fun templateFrom(
    form: EditorForm,
    id: String,
    organizationId: String,
    title: String,
    description: String,
  ): FormTemplate {
    val isGlobal = LibraryIds.isGlobalLibrary(organizationId)
    val questions =
      form.questions.map { q ->
        val portable =
          if (q.choiceDatasetId != null) {
            q.copy(
              choiceDatasetId = null,
              choiceSource = ChoiceSource.MANUAL,
              allowAddEntity = false,
            )
          } else {
            q
          }
        val link = portable.conceptLink
        if (isGlobal && link != null && !LibraryIds.isGlobalId(link.conceptId)) {
          portable.copy(conceptLink = null, choices = portable.choices.map { it.copy(code = null) })
        } else {
          portable
        }
      }
    return FormTemplate(
      id = id,
      organizationId = organizationId,
      title = LocalizedText.en(title.trim()),
      description =
        description.trim().takeIf { it.isNotEmpty() }?.let(LocalizedText::en) ?: LocalizedText(),
      form =
        form.copy(
          formId = LibraryIds.nameOf(id),
          title = title.trim(),
          questions = questions,
          saveTo = EditorSaveTo(),
        ),
      status = LibraryStatus.DRAFT,
    )
  }

  /** Number of [form]'s concept links that a global template would drop (organization concepts). */
  fun organizationLinkCount(form: EditorForm): Int =
    form.questions.count { q ->
      q.conceptLink?.let { !LibraryIds.isGlobalId(it.conceptId) } == true
    }
}
