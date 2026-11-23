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
package org.openforis.collectone.v2.app.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText

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
        templateOrigin = TemplateOrigin.of(template),
      )
    var nextKeyId = draft.nextKeyId
    val formKey = "f${nextKeyId++}"
    val datasetKey = "d${nextKeyId++}"
    val dataset =
      FormDatasetLinks.newLinkedDataset(
        form = form,
        key = datasetKey,
        formKey = formKey,
        displayName = FormDatasetLinks.uniqueTitle(title, draft.datasets.map { it.displayName }),
        id = DatasetIds.forName(title, datasetIds),
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
  ): FormTemplate =
    FormTemplate(
      id = id,
      organizationId = organizationId,
      title = LocalizedText.en(title.trim()),
      description =
        description.trim().takeIf { it.isNotEmpty() }?.let(LocalizedText::en) ?: LocalizedText(),
      form = portableForm(form, LibraryIds.nameOf(id), title, organizationId),
      status = LibraryStatus.DRAFT,
    )

  /**
   * [template] after editing its Form in the Form editor (the organization's **Templates** tab):
   * [form] becomes the template's Form, made portable like [templateFrom] does, keeping the
   * template's Form ID. [title] (which the Form's title follows) and [description] replace the
   * English text, keeping other translations; a blank [description] removes the English one. ID,
   * status, and version are kept.
   */
  fun edited(
    template: FormTemplate,
    form: EditorForm,
    title: String = form.title,
    description: String = template.description.get(LocalizedText.DEFAULT_LANGUAGE),
  ): FormTemplate =
    template.copy(
      title = template.title.with(LocalizedText.DEFAULT_LANGUAGE, title.trim()),
      description = template.description.with(LocalizedText.DEFAULT_LANGUAGE, description.trim()),
      form = portableForm(form, template.form.formId, title, template.organizationId),
    )

  /**
   * [form] as a template of [organizationId] stores it: with [formId] and [title], no survey
   * save-to settings (a survey adding it gets its own dataset), no record of another template it
   * was copied from, and choices loaded from a survey dataset replaced by their listed ones.
   * Concept links and choice codes are kept, except that global templates only keep links to global
   * concepts (global entries can't reference organization entries).
   */
  private fun portableForm(
    form: EditorForm,
    formId: String,
    title: String,
    organizationId: String,
  ): EditorForm {
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
    return form.copy(
      formId = formId,
      title = title.trim(),
      questions = questions,
      saveTo = EditorSaveTo(),
      templateOrigin = null,
    )
  }

  /** Number of [form]'s concept links that a global template would drop (organization concepts). */
  fun organizationLinkCount(form: EditorForm): Int =
    form.questions.count { q ->
      q.conceptLink?.let { !LibraryIds.isGlobalId(it.conceptId) } == true
    }

  /**
   * Suggested question additions and removals when a regulatory or library [template] has been
   * updated relative to an existing survey [form].
   */
  data class TemplateUpdateDiff(
    val templateId: String,
    val templateTitle: String,
    val addedQuestions: List<EditorQuestion>,
    val removedQuestions: List<EditorQuestion>,
    val templateVersion: Int = 1,
  ) {
    val hasChanges: Boolean
      get() = addedQuestions.isNotEmpty() || removedQuestions.isNotEmpty()
  }

  /**
   * Computes a human-in-the-loop [TemplateUpdateDiff] between [form] and [template] when [form] was
   * created from an earlier version of [template] (or, for legacy forms without a recorded
   * [EditorForm.templateOrigin], shares its form ID or title).
   */
  fun computeUpdateDiff(
    form: EditorForm,
    template: FormTemplate,
    language: String = LocalizedText.DEFAULT_LANGUAGE,
  ): TemplateUpdateDiff? {
    val templateTitle = template.title.get(language).ifBlank { template.form.title }
    val origin = form.templateOrigin
    val matchesTemplate =
      if (origin != null) {
        origin.templateId == template.id && template.version > origin.templateVersion
      } else {
        form.formId == template.form.formId ||
          form.formId == template.id ||
          form.title.equals(templateTitle, ignoreCase = true) ||
          form.title.equals(template.form.title, ignoreCase = true)
      }
    if (!matchesTemplate) return null

    val formConcepts = form.questions.mapNotNull { it.conceptLink?.conceptId }.toSet()
    val templateConcepts = template.form.questions.mapNotNull { it.conceptLink?.conceptId }.toSet()
    val formNames = form.questions.map { it.name }.toSet()
    val templateNames = template.form.questions.map { it.name }.toSet()

    val added =
      template.form.questions.filter { tq ->
        val cId = tq.conceptLink?.conceptId
        if (cId != null) cId !in formConcepts && tq.name !in formNames else tq.name !in formNames
      }
    val removed =
      form.questions.filter { fq ->
        val cId = fq.conceptLink?.conceptId
        cId != null && cId !in templateConcepts && fq.name !in templateNames
      }

    val diff =
      TemplateUpdateDiff(
        templateId = template.id,
        templateTitle = templateTitle,
        addedQuestions = added,
        removedQuestions = removed,
        templateVersion = template.version,
      )
    return diff.takeIf { it.hasChanges }
  }
}
