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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository

/**
 * Edits an organization's library in the organization editor's **Dictionary**, **Templates**, and
 * **Purposes** tabs. On the `"All users"` organization these edit the global library.
 *
 * Validation is pure so dialogs can show problems inline; writes go through
 * [LibraryRepository.updateLibrary], which also enforces the ownership rules. Callers check
 * [canEdit] first: only active Managers of the organization may edit its library.
 */
class ManageLibraryUseCase(private val libraryRepository: LibraryRepository) {
  /** Whether [email] may edit [organization]'s library (its active Managers only). */
  fun canEdit(organization: Organization, email: String): Boolean = organization.isManager(email)

  /**
   * The ID of a new concept named [name] in [organizationId]'s library: `org.<organizationId>.
   * <name>`, or `<vocabulary>.<name>` in the global library.
   */
  fun conceptId(organizationId: String, name: String, vocabulary: String = ""): String =
    if (LibraryIds.isGlobalLibrary(organizationId)) {
      LibraryIds.globalConceptId(vocabulary.trim(), name.trim())
    } else {
      LibraryIds.organizationEntryId(organizationId, name.trim())
    }

  /**
   * Why [concept] can't be saved to [library], or `null` if it can. [isNew] is true when creating.
   *
   * Besides the ID and label, enforces the versioning rules: removing a code-list value isn't
   * allowed, and a stable concept's data type, unit, or aggregation can't change (that changes its
   * meaning, which needs a new concept).
   */
  fun conceptError(concept: LibraryConcept, library: OrganizationLibrary, isNew: Boolean): String? {
    LibraryIds.conceptIdError(concept.id, library.organizationId)?.let {
      return it
    }
    if (concept.label.isBlank) return "Enter a label."
    val existing = library.concept(concept.id)
    if (isNew && existing != null) return "A concept with the ID ${concept.id} already exists."
    if (!isNew && existing == null) return "This concept no longer exists."
    if (concept.dataType.hasCodeList && concept.codeList.isEmpty()) {
      return "Add at least one value to the list."
    }
    val codes = concept.codeList.map { it.code }
    if (codes.any { !LibraryIds.isValidName(it) }) {
      return "List values use lowercase letters, numbers, and underscores, for example oil_palm."
    }
    if (codes.toSet().size != codes.size) return "List values must be unique."
    if (concept.codeList.any { it.label.isBlank }) return "Enter a label for every list value."
    if (existing != null) {
      val removed = existing.codeList.map { it.code } - codes.toSet()
      if (removed.isNotEmpty()) {
        return "List values can't be removed (${removed.joinToString()}). Deprecate the concept " +
          "and create a new one instead."
      }
      val changesMeaning =
        existing.dataType != concept.dataType ||
          existing.unit != concept.unit ||
          existing.aggregation != concept.aggregation
      if (existing.status != LibraryStatus.DRAFT && changesMeaning) {
        return "Changing the data type, unit, or aggregation changes what the concept means. " +
          "Create a new concept instead."
      }
    }
    return null
  }

  /**
   * Creates or updates [concept] in its organization's library (after [conceptError]). Updates that
   * change anything but the status increment the version.
   */
  suspend fun saveConcept(concept: LibraryConcept): OrganizationLibrary? =
    libraryRepository.updateLibrary(concept.organizationId) { library ->
      val existing = library.concept(concept.id)
      if (existing == null) {
        library.copy(
          concepts = library.concepts + concept.copy(version = concept.version.coerceAtLeast(1))
        )
      } else {
        val contentChanged =
          existing.copy(status = concept.status, version = concept.version) != concept
        val saved = concept.copy(version = existing.version + if (contentChanged) 1 else 0)
        library.copy(concepts = library.concepts.map { if (it.id == concept.id) saved else it })
      }
    }

  /** Why [conceptId] can't be deleted from [library], or `null` if it can (drafts only). */
  fun deleteConceptError(library: OrganizationLibrary, conceptId: String): String? {
    val concept = library.concept(conceptId) ?: return "This concept no longer exists."
    return if (concept.status == LibraryStatus.DRAFT) null
    else "Only draft concepts can be deleted. Deprecate it instead, so existing links keep working."
  }

  /** Deletes the draft concept [conceptId] from [organizationId]'s library. */
  suspend fun deleteConcept(organizationId: String, conceptId: String): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      if (deleteConceptError(library, conceptId) != null) library
      else library.copy(concepts = library.concepts.filterNot { it.id == conceptId })
    }

  /** Sets the lifecycle status of [conceptId]. */
  suspend fun setConceptStatus(
    organizationId: String,
    conceptId: String,
    status: LibraryStatus,
  ): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      library.copy(
        concepts = library.concepts.map { if (it.id == conceptId) it.copy(status = status) else it }
      )
    }

  /** Why [title] isn't a valid template or Purpose Pack title, or `null`. */
  fun titleError(title: String): String? = if (title.isBlank()) "Enter a title." else null

  /** Renames the template [templateId] and replaces its English description. */
  suspend fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      library.copy(
        formTemplates =
          library.formTemplates.map {
            if (it.id == templateId) {
              it.copy(
                title = it.title.with(LocalizedText.DEFAULT_LANGUAGE, title.trim()),
                description =
                  it.description.with(LocalizedText.DEFAULT_LANGUAGE, description.trim()),
              )
            } else {
              it
            }
          }
      )
    }

  /** Deletes the template [templateId] and removes it from the library's Purpose Packs. */
  suspend fun deleteTemplate(organizationId: String, templateId: String): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      library.copy(
        formTemplates = library.formTemplates.filterNot { it.id == templateId },
        purposePacks =
          library.purposePacks.map { pack ->
            pack.copy(formTemplateIds = pack.formTemplateIds - templateId)
          },
      )
    }

  /** Renames the Purpose Pack [packId] and replaces its English description. */
  suspend fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      library.copy(
        purposePacks =
          library.purposePacks.map {
            if (it.id == packId) {
              it.copy(
                title = it.title.with(LocalizedText.DEFAULT_LANGUAGE, title.trim()),
                description =
                  it.description.with(LocalizedText.DEFAULT_LANGUAGE, description.trim()),
              )
            } else {
              it
            }
          }
      )
    }

  /** Deletes the Purpose Pack [packId]. */
  suspend fun deletePurposePack(organizationId: String, packId: String): OrganizationLibrary? =
    libraryRepository.updateLibrary(organizationId) { library ->
      library.copy(purposePacks = library.purposePacks.filterNot { it.id == packId })
    }

  /**
   * Hides or shows the global template or Purpose Pack [globalEntryId] in [organizationId]'s
   * pickers. The global library itself can't hide entries, and only global IDs can be hidden.
   */
  suspend fun setGlobalEntryHidden(
    organizationId: String,
    globalEntryId: String,
    hidden: Boolean,
  ): OrganizationLibrary? {
    if (LibraryIds.isGlobalLibrary(organizationId) || !LibraryIds.isGlobalId(globalEntryId)) {
      return null
    }
    return libraryRepository.updateLibrary(organizationId) { library ->
      val ids = library.settings.hiddenGlobalEntryIds
      library.copy(
        settings =
          library.settings.copy(
            hiddenGlobalEntryIds = if (hidden) ids + globalEntryId else ids - globalEntryId
          )
      )
    }
  }
}
