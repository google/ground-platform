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

import kotlinx.coroutines.Job
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_error_library_managers_only
import org.openforis.collectone.v2.web.resources.org_error_not_found
import org.openforis.collectone.v2.web.resources.org_error_question_labels
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage
import org.openforis.collectone.v2.web.ui.text.organizationMessage
import org.openforis.collectone.v2.web.ui.text.toOrganizationMessage

/**
 * The library half of [OrganizationViewModel]: edits the open organization's Dictionary, Templates,
 * and Purposes through [ManageLibraryUseCase], and derives what those tabs show.
 *
 * It holds no state. It reads the latest stored data through [data] when an intent arrives, and
 * runs writes through [launchSaving], so the page's saving indicator covers them and they stop with
 * the ViewModel. Validation runs before anything is launched, so dialogs get their error back
 * synchronously and stay open; a `null` result means the write was started.
 */
internal class OrganizationLibraryEditor(
  private val manageLibraryUseCase: ManageLibraryUseCase,
  private val resolveLibraryUseCase: ResolveLibraryUseCase,
  private val searchConceptsUseCase: SearchConceptsUseCase,
  private val data: () -> LibraryData,
  private val onDictionaryQueryChange: (String) -> Unit,
  private val launchSaving: (suspend () -> Unit) -> Job,
) : OrganizationLibraryActions {

  /** What library edits and the library tabs read from the local data store. */
  data class LibraryData(
    val organizations: List<Organization>,
    val libraries: Map<String, OrganizationLibrary>,
    val isSignedIn: Boolean,
    val signedInEmail: String,
  )

  /**
   * The library tabs of [openOrganizationId] (empty while none is open): its own entries first,
   * then the global ones. On the synthetic "All users" organization the global library is the one
   * being edited, so it lists only global entries and they're editable; elsewhere global entries
   * are read-only and can only be hidden from the organization's pickers. Hidden entries stay
   * listed so they can be shown again.
   */
  fun uiState(
    data: LibraryData,
    openOrganizationId: String?,
    dictionaryQuery: String,
  ): OrganizationLibraryUiState {
    val organization =
      data.organizations.firstOrNull { it.id == openOrganizationId }
        ?: return OrganizationLibraryUiState(dictionaryQuery = dictionaryQuery)
    val isGlobalLibrary = LibraryIds.isGlobalLibrary(organization.id)
    val canEdit = data.isSignedIn && manageLibraryUseCase.canEdit(organization, data.signedInEmail)
    val global =
      data.libraries[Organization.ALL_USERS_ID] ?: OrganizationLibrary(Organization.ALL_USERS_ID)
    val own = data.libraries[organization.id] ?: OrganizationLibrary(organization.id)
    val hidden = if (isGlobalLibrary) emptySet() else own.settings.hiddenGlobalEntryIds
    fun <T> row(entry: T, isGlobal: Boolean, hideable: Boolean, id: String) =
      LibraryEntryRow(
        entry = entry,
        isGlobal = isGlobal,
        canEdit = canEdit && isGlobal == isGlobalLibrary,
        canHide = canEdit && hideable && isGlobal && !isGlobalLibrary,
        isHidden = isGlobal && id in hidden,
      )
    // Concepts: the resolved library (organization concepts, then global ones).
    val resolved =
      resolveLibraryUseCase(global = global, organization = own.takeUnless { isGlobalLibrary })
    val matches =
      searchConceptsUseCase(
        resolved.concepts,
        dictionaryQuery,
        SearchConceptsUseCase.SearchOptions(
          limit = Int.MAX_VALUE,
          includeDeprecated = true,
          organizationId = organization.id.takeUnless { isGlobalLibrary },
        ),
      )
    val ownTemplates = if (isGlobalLibrary) emptyList() else own.formTemplates
    val ownPacks = if (isGlobalLibrary) emptyList() else own.purposePacks
    return OrganizationLibraryUiState(
      organizationId = organization.id,
      isGlobalLibrary = isGlobalLibrary,
      canEdit = canEdit,
      dictionaryQuery = dictionaryQuery,
      concepts =
        matches.map { row(it.concept, it.concept.isGlobal, hideable = false, it.concept.id) },
      totalConceptCount = resolved.concepts.size,
      formTemplates =
        ownTemplates.map { row(it, isGlobal = false, hideable = false, it.id) } +
          global.formTemplates.map { row(it, isGlobal = true, hideable = true, it.id) },
      purposePacks =
        ownPacks.map { row(it, isGlobal = false, hideable = false, it.id) } +
          global.purposePacks.map { row(it, isGlobal = true, hideable = true, it.id) },
      templateTitles = (ownTemplates + global.formTemplates).associate { it.id to it.title.text },
      resolvedLibrary = resolved,
    )
  }

  /** Why the signed-in user can't edit [organizationId]'s library, or `null` if they can. */
  private fun libraryEditError(organizationId: String): OrganizationMessage? {
    val current = data()
    val organization =
      current.organizations.firstOrNull { it.id == organizationId }
        ?: return organizationMessage(Res.string.org_error_not_found)
    return if (
      current.isSignedIn && manageLibraryUseCase.canEdit(organization, current.signedInEmail)
    ) {
      null
    } else {
      organizationMessage(Res.string.org_error_library_managers_only, organization.name)
    }
  }

  /** [ManageLibraryUseCase.titleError] for [title], as an [OrganizationMessage]. */
  private fun titleError(title: String): OrganizationMessage? =
    manageLibraryUseCase.titleError(title)?.let { OrganizationMessage.Text(it) }

  private fun library(organizationId: String): OrganizationLibrary =
    data().libraries[organizationId] ?: OrganizationLibrary(organizationId)

  override fun setDictionaryQuery(query: String) = onDictionaryQueryChange(query)

  override fun saveConcept(concept: LibraryConcept, isNew: Boolean): OrganizationMessage? {
    libraryEditError(concept.organizationId)?.let {
      return it
    }
    manageLibraryUseCase.conceptError(concept, library(concept.organizationId), isNew)?.let {
      return OrganizationMessage.Text(it)
    }
    launchSaving { manageLibraryUseCase.saveConcept(concept) }
    return null
  }

  override fun deleteConcept(organizationId: String, conceptId: String): OrganizationMessage? {
    libraryEditError(organizationId)?.let {
      return it
    }
    manageLibraryUseCase.deleteConceptError(library(organizationId), conceptId)?.let {
      return it.toOrganizationMessage()
    }
    launchSaving { manageLibraryUseCase.deleteConcept(organizationId, conceptId) }
    return null
  }

  override fun createTemplate(
    organizationId: String,
    title: String,
    description: String,
    questions: List<EditorQuestion>,
  ): OrganizationMessage? =
    createTemplate(organizationId, title, description, questions, emptyList())

  override fun createTemplate(
    organizationId: String,
    title: String,
    description: String,
    questions: List<EditorQuestion>,
    purposePackIds: List<String>,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    val template =
      manageLibraryUseCase.newTemplate(
        library(organizationId),
        organizationId,
        title,
        description,
        questions,
      )
    launchSaving {
      manageLibraryUseCase.saveTemplate(template)
      if (purposePackIds.isNotEmpty()) {
        manageLibraryUseCase.setTemplatePurposePacks(organizationId, template.id, purposePackIds)
      }
    }
    return null
  }

  override fun saveTemplate(template: FormTemplate): OrganizationMessage? {
    (libraryEditError(template.organizationId) ?: titleError(template.title.text))?.let {
      return it
    }
    launchSaving { manageLibraryUseCase.saveTemplate(template) }
    return null
  }

  override fun saveTemplateForm(
    organizationId: String,
    templateId: String?,
    form: EditorForm,
    description: String,
    purposePackIds: List<String>,
  ): OrganizationMessage? {
    val validationError =
      libraryEditError(organizationId)
        ?: when (manageLibraryUseCase.templateFormError(form)) {
          ManageLibraryUseCase.TemplateFormError.MISSING_TITLE -> titleError(form.title)
          ManageLibraryUseCase.TemplateFormError.UNLABELED_QUESTION ->
            organizationMessage(Res.string.org_error_question_labels)
          null -> null
        }
    if (validationError != null) return validationError
    val template =
      manageLibraryUseCase.editedTemplate(
        library(organizationId),
        organizationId,
        templateId,
        form,
        description,
      )
    launchSaving { manageLibraryUseCase.saveTemplate(template, purposePackIds) }
    return null
  }

  override fun updateTemplateDetails(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
    purposePackIds: List<String>?,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    launchSaving {
      manageLibraryUseCase.updateTemplateDetails(
        organizationId,
        templateId,
        title,
        description,
        purposePackIds,
      )
    }
    return null
  }

  override fun setTemplatePurposePacks(
    organizationId: String,
    templateId: String,
    purposePackIds: List<String>,
  ) {
    if (libraryEditError(organizationId) != null) return
    launchSaving {
      manageLibraryUseCase.setTemplatePurposePacks(organizationId, templateId, purposePackIds)
    }
  }

  override fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    launchSaving {
      manageLibraryUseCase.renameTemplate(organizationId, templateId, title, description)
    }
    return null
  }

  override fun deleteTemplate(organizationId: String, templateId: String) {
    if (libraryEditError(organizationId) != null) return
    launchSaving { manageLibraryUseCase.deleteTemplate(organizationId, templateId) }
  }

  override fun createPurposePack(
    organizationId: String,
    title: String,
    description: String,
    icon: String,
    pillar: ImpactPillar?,
    formTemplateIds: List<String>,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    val pack =
      manageLibraryUseCase.newPurposePack(
        library = library(organizationId),
        organizationId = organizationId,
        title = title,
        description = description,
        icon = icon,
        pillar = pillar,
        formTemplateIds = formTemplateIds,
      )
    launchSaving { manageLibraryUseCase.savePurposePack(pack) }
    return null
  }

  override fun updatePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
    icon: String,
    pillar: ImpactPillar?,
    status: LibraryStatus,
    formTemplateIds: List<String>,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    // A pack that's no longer in the library (deleted meanwhile) is saved again as a new one.
    val existing =
      library(organizationId).purposePack(packId)
        ?: PurposePack(
          id = packId,
          organizationId = organizationId,
          title = LocalizedText.en(title.trim()),
        )
    val pack =
      manageLibraryUseCase.editedPurposePack(
        pack = existing,
        title = title,
        description = description,
        icon = icon,
        pillar = pillar,
        status = status,
        formTemplateIds = formTemplateIds,
      )
    launchSaving { manageLibraryUseCase.savePurposePack(pack) }
    return null
  }

  override fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ): OrganizationMessage? {
    (libraryEditError(organizationId) ?: titleError(title))?.let {
      return it
    }
    launchSaving {
      manageLibraryUseCase.renamePurposePack(organizationId, packId, title, description)
    }
    return null
  }

  override fun deletePurposePack(organizationId: String, packId: String) {
    if (libraryEditError(organizationId) != null) return
    launchSaving { manageLibraryUseCase.deletePurposePack(organizationId, packId) }
  }

  override fun setGlobalEntryHidden(
    organizationId: String,
    globalEntryId: String,
    hidden: Boolean,
  ) {
    if (libraryEditError(organizationId) != null) return
    launchSaving {
      manageLibraryUseCase.setGlobalEntryHidden(organizationId, globalEntryId, hidden)
    }
  }
}
