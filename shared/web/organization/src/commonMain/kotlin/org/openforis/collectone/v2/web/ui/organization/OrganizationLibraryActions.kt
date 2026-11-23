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
package org.openforis.collectone.v2.web.ui.organization

import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/**
 * User intents of the organization page's library tabs (Dictionary, Templates, and Purposes). On
 * the synthetic "All users" organization they edit the global library. Implemented by
 * [OrganizationViewModel] through [OrganizationLibraryEditor].
 *
 * Intents that return an [OrganizationMessage] validate synchronously and return why nothing was
 * saved (so the dialog can stay open and show it), or `null` once the write has started. Only
 * active Managers of the organization may edit its library; others get an error back, or the intent
 * is ignored when it returns nothing.
 */
interface OrganizationLibraryActions {
  /** Filters the Dictionary tab by [query] (labels and keywords in every language). */
  fun setDictionaryQuery(query: String)

  /**
   * Creates ([isNew]) or updates [concept] in its organization's library. Returns a validation
   * error message, or `null` when it was saved. Only Managers may call this.
   */
  fun saveConcept(concept: LibraryConcept, isNew: Boolean): OrganizationMessage?

  /** Deletes a draft concept. Returns an error message (e.g. it isn't a draft), or `null`. */
  fun deleteConcept(organizationId: String, conceptId: String): OrganizationMessage?

  /**
   * Creates a new form template in [organizationId]'s library with [title], [description], and
   * optional initial [questions] and associated [purposePackIds]. Returns a validation error
   * message, or `null` when saved.
   */
  fun createTemplate(
    organizationId: String,
    title: String,
    description: String = "",
    questions: List<EditorQuestion> = emptyList(),
  ): OrganizationMessage? =
    createTemplate(organizationId, title, description, questions, emptyList())

  /**
   * Creates a new form template in [organizationId]'s library and associates it with
   * [purposePackIds] in the same transaction. Returns a validation error message, or `null` when
   * saved.
   */
  fun createTemplate(
    organizationId: String,
    title: String,
    description: String = "",
    questions: List<EditorQuestion> = emptyList(),
    purposePackIds: List<String> = emptyList(),
  ): OrganizationMessage? = null

  /**
   * Creates or updates [template] in its organization's library. Returns a validation error
   * message, or `null` when saved.
   */
  fun saveTemplate(template: FormTemplate): OrganizationMessage? = null

  /**
   * Saves a template edited in the Form editor: [form] (whose title is the template's) and
   * [description] replace those of template [templateId] in [organizationId]'s library, or make a
   * new template there when [templateId] is `null`. The template is then listed in exactly the
   * Purpose Packs [purposePackIds]. Returns a validation error message, or `null` when saved.
   */
  fun saveTemplateForm(
    organizationId: String,
    templateId: String?,
    form: EditorForm,
    description: String,
    purposePackIds: List<String>,
  ): OrganizationMessage? = null

  /**
   * Updates template metadata and optionally synchronizes associated Purpose Packs bidirectionally.
   */
  fun updateTemplateDetails(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
    purposePackIds: List<String>? = null,
  ): OrganizationMessage? = null

  /** Sets the Purpose Packs associated with [templateId] in [organizationId]. */
  fun setTemplatePurposePacks(
    organizationId: String,
    templateId: String,
    purposePackIds: List<String>,
  ) {}

  /** Renames a template. Returns a validation error message, or `null`. */
  fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ): OrganizationMessage?

  /** Deletes a template and removes it from the organization's Purpose Packs. */
  fun deleteTemplate(organizationId: String, templateId: String)

  /**
   * Creates a new Purpose Pack in [organizationId]'s library with [title], [description], [icon],
   * [pillar], and associated [formTemplateIds]. Returns a validation error message, or `null`.
   */
  fun createPurposePack(
    organizationId: String,
    title: String,
    description: String = "",
    icon: String = "",
    pillar: ImpactPillar? = null,
    formTemplateIds: List<String> = emptyList(),
  ): OrganizationMessage? = null

  /**
   * Updates an existing Purpose Pack in [organizationId]'s library. Returns a validation error
   * message, or `null`.
   */
  fun updatePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String = "",
    icon: String = "",
    pillar: ImpactPillar? = null,
    status: LibraryStatus = LibraryStatus.STABLE,
    formTemplateIds: List<String> = emptyList(),
  ): OrganizationMessage? = null

  /** Renames a Purpose Pack. Returns a validation error message, or `null`. */
  fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ): OrganizationMessage?

  /** Deletes a Purpose Pack. */
  fun deletePurposePack(organizationId: String, packId: String)

  /** Hides or shows a global template or Purpose Pack in [organizationId]'s pickers. */
  fun setGlobalEntryHidden(organizationId: String, globalEntryId: String, hidden: Boolean)
}
