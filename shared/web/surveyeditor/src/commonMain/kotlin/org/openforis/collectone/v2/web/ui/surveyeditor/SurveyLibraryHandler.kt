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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext

/**
 * The Survey editor's side of the organization libraries: the dictionary context the Form editors
 * link questions to, and the two writes they can request (a new concept, a Form saved as a
 * template).
 *
 * Writes are launched in [scope] and not awaited: the methods only validate synchronously and
 * return why a write was refused, and the editor sees the result when [libraries] (the observed
 * store) emits the updated library. Without a [manageLibraryUseCase] every write is refused.
 *
 * @param libraries every stored organization library, keyed by organization ID.
 * @param libraryContext the survey's current dictionary context, as last derived by [contextFor].
 */
internal class SurveyLibraryHandler(
  private val manageLibraryUseCase: ManageLibraryUseCase?,
  private val resolveLibraryUseCase: ResolveLibraryUseCase,
  private val scope: CoroutineScope,
  private val libraries: () -> Map<String, OrganizationLibrary>,
  private val libraryContext: () -> FormLibraryContext,
) {
  /**
   * The survey's resolved library (its organization's, then the global one), who may add to it, and
   * the concepts of its purposes, which rank first in label suggestions.
   */
  fun contextFor(
    details: SurveyDetails,
    organizations: List<Organization>,
    email: String,
    libraries: Map<String, OrganizationLibrary>,
  ): FormLibraryContext {
    val organization = organizations.firstOrNull { it.id == details.organizationId }
    val library = resolveLibraryUseCase.forOrganization(libraries, organization?.id)
    val canManage = manageLibraryUseCase != null
    val defaultLanguage = details.defaultLanguage.ifEmpty { LocalizedText.DEFAULT_LANGUAGE }
    val supportedLanguages =
      (listOf(defaultLanguage) + details.supportedLanguages)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
    val isPlatformAdmin = organizations.hasPlatformAdmin(email)
    return FormLibraryContext(
      library = library,
      organizationName = organization?.name,
      canAddToDictionary =
        canManage && organization != null && (isPlatformAdmin || organization.isManager(email)),
      canSaveToGlobalLibrary = canManage && isPlatformAdmin,
      language = defaultLanguage,
      supportedLanguages = supportedLanguages,
      purposeConceptIds = library.conceptIdsForPurposes(details.purposeIds),
    )
  }

  /**
   * Adds draft [concept] to its organization's dictionary; returns why it can't be added, or `null`
   * (the library updates when the save completes).
   */
  fun addOrganizationConcept(concept: LibraryConcept): String? {
    val manage = manageLibraryUseCase ?: return "This survey has no organization dictionary."
    val organizationLibrary =
      libraries()[concept.organizationId] ?: OrganizationLibrary(concept.organizationId)
    manage.conceptError(concept, organizationLibrary, isNew = true)?.let {
      return it
    }
    scope.launch { manage.saveConcept(concept) }
    return null
  }

  /**
   * Saves a copy of [form] as a template titled [title] in the survey organization's library, or
   * the global library when [toGlobal]. Returns why it can't be saved, or `null` (the library
   * updates when the save completes).
   */
  fun saveFormAsTemplate(
    form: EditorForm,
    title: String,
    description: String,
    toGlobal: Boolean,
  ): String? {
    val manage = manageLibraryUseCase ?: return "Templates can't be saved here."
    val (organizationId, refusal) = templateLibrary(manage, title, toGlobal)
    if (organizationId == null) return refusal
    val library = libraries()[organizationId] ?: OrganizationLibrary(organizationId)
    val template =
      SurveyFormTemplates.templateFrom(
        form = form,
        id = manage.newTemplateId(library, title),
        organizationId = organizationId,
        title = title,
        description = description,
      )
    scope.launch { manage.saveTemplate(template) }
    return null
  }

  /**
   * The ID of the library a template titled [title] would be saved to, or `null` and why it can't:
   * the user may not add to that library, or the title is invalid (checked in that order).
   */
  private fun templateLibrary(
    manage: ManageLibraryUseCase,
    title: String,
    toGlobal: Boolean,
  ): Pair<String?, String?> {
    val context = libraryContext()
    val organizationId =
      if (toGlobal) Organization.ALL_USERS_ID.takeIf { context.canSaveToGlobalLibrary }
      else context.library.organizationId.takeIf { context.canAddToDictionary }
    val refusal =
      when {
        organizationId == null && toGlobal ->
          "Only Managers of All users can add to the global library."
        organizationId == null -> "Only Managers of this survey's organization can save templates."
        else -> manage.titleError(title)
      }
    return if (refusal == null) organizationId to null else null to refusal
  }
}
