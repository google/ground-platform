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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.ExportProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack

/**
 * The library available to one survey: its organization's entries first, then global entries (see
 * `docs/technical/model/library/00-introduction.md`, "Resolution").
 *
 * Deprecated entries are included so existing references keep resolving; pickers and search use the
 * `pickable…` views, which leave them out.
 */
data class ResolvedLibrary(
  /** Organization of the survey, or `null` for a personal survey. */
  val organizationId: String?,
  val concepts: List<LibraryConcept> = emptyList(),
  val formTemplates: List<FormTemplate> = emptyList(),
  val purposePacks: List<PurposePack> = emptyList(),
  val exportProfiles: List<ExportProfile> = emptyList(),
) {
  private val conceptsById: Map<String, LibraryConcept> by lazy { concepts.associateBy { it.id } }

  fun concept(id: String): LibraryConcept? = conceptsById[id]

  fun formTemplate(id: String): FormTemplate? = formTemplates.firstOrNull { it.id == id }

  fun purposePack(id: String): PurposePack? = purposePacks.firstOrNull { it.id == id }

  /** Whether [id] belongs to the survey's organization (rather than the global library). */
  fun isOrganizationEntry(id: String): Boolean =
    organizationId != null && LibraryIds.organizationIdOf(id) == organizationId

  val pickableConcepts: List<LibraryConcept>
    get() = concepts.filter { it.status != LibraryStatus.DEPRECATED }

  val pickableFormTemplates: List<FormTemplate>
    get() = formTemplates.filter { it.status != LibraryStatus.DEPRECATED }

  val pickablePurposePacks: List<PurposePack>
    get() = purposePacks.filter { it.status != LibraryStatus.DEPRECATED }
}

/**
 * Resolves the library of a survey from the global (`"All users"`) library and the survey
 * organization's library. Pure; see [invoke] for the rules.
 */
class ResolveLibraryUseCase {
  /**
   * Resolves the library of a survey in [organizationId] (or a personal survey when `null`) from
   * every stored library, keyed by organization ID.
   */
  fun forOrganization(
    libraries: Map<String, OrganizationLibrary>,
    organizationId: String?,
  ): ResolvedLibrary =
    invoke(
      global = libraries[Organization.ALL_USERS_ID],
      organization = organizationId?.let { libraries[it] ?: OrganizationLibrary(it) },
    )

  /**
   * Resolves the library for a survey in [organization]'s organization, or for a personal survey
   * when [organization] is `null`:
   * - **Order**: organization entries first, then global entries, each in library order.
   * - **No shadowing**: organization entries whose IDs don't carry the `org.<organizationId>.`
   *   prefix, and global entries whose IDs do, are dropped.
   * - **One-way references**: global templates may only link to global concepts, and global Purpose
   *   Packs may only list global templates and export profiles; other references are dropped.
   *   Organization entries may reference global entries.
   * - **Hiding**: global templates and Purpose Packs listed in the organization's
   *   `hiddenGlobalEntryIds` are left out. A global Purpose Pack is also left out when it lists
   *   templates and all of them are hidden; otherwise its hidden templates are removed from its
   *   list. Organization packs are never rewritten. Global concepts can't be hidden.
   * - **Personal surveys** get the global library only, with nothing hidden.
   *
   * Resolving for the `"All users"` organization itself returns the global library once.
   */
  operator fun invoke(
    global: OrganizationLibrary?,
    organization: OrganizationLibrary?,
  ): ResolvedLibrary {
    val globalLibrary = global?.takeIf { it.isGlobal }
    val orgLibrary = organization?.takeUnless { it.isGlobal }
    val hidden = orgLibrary?.settings?.hiddenGlobalEntryIds.orEmpty()

    val globalConcepts = globalLibrary?.concepts.orEmpty().filter { it.isGlobal }
    val globalTemplates =
      globalLibrary
        ?.formTemplates
        .orEmpty()
        .filter { it.isGlobal && it.id !in hidden }
        .map { template ->
          template.copy(
            form =
              template.form.copy(
                questions =
                  template.form.questions.map { q ->
                    val link = q.conceptLink
                    if (link == null || LibraryIds.isGlobalId(link.conceptId)) q
                    else q.copy(conceptLink = null)
                  }
              )
          )
        }
    val globalPacks =
      globalLibrary
        ?.purposePacks
        .orEmpty()
        .filter { it.isGlobal && it.id !in hidden }
        .mapNotNull { pack ->
          val templateIds = pack.formTemplateIds.filter(LibraryIds::isGlobalId)
          val visibleTemplateIds = templateIds.filterNot { it in hidden }
          // A pack built only on hidden templates is hidden too.
          if (templateIds.isNotEmpty() && visibleTemplateIds.isEmpty()) return@mapNotNull null
          pack.copy(
            formTemplateIds = visibleTemplateIds,
            exportProfileIds = pack.exportProfileIds.filter(LibraryIds::isGlobalId),
          )
        }
    val globalProfiles =
      globalLibrary?.exportProfiles.orEmpty().filter { LibraryIds.isGlobalId(it.id) }

    val owns = { id: String ->
      orgLibrary != null && LibraryIds.isValidOrganizationEntryId(id, orgLibrary.organizationId)
    }
    return ResolvedLibrary(
      organizationId = organization?.organizationId,
      concepts = orgLibrary?.concepts.orEmpty().filter { owns(it.id) } + globalConcepts,
      formTemplates = orgLibrary?.formTemplates.orEmpty().filter { owns(it.id) } + globalTemplates,
      purposePacks = orgLibrary?.purposePacks.orEmpty().filter { owns(it.id) } + globalPacks,
      exportProfiles = orgLibrary?.exportProfiles.orEmpty().filter { owns(it.id) } + globalProfiles,
    )
  }
}
