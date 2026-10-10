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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack

/**
 * One entry listed in the organization page's **Dictionary**, **Templates**, or **Purposes** tab.
 *
 * @property isGlobal whether the entry comes from the global (`"All users"`) library.
 * @property canEdit whether the signed-in user can edit or delete it: Managers of its owning
 *   organization only.
 * @property canHide whether the signed-in user can toggle [isHidden]: Managers of an organization
 *   other than `"All users"`, for global templates and Purpose Packs.
 * @property isHidden whether the organization hides this global entry from its pickers.
 */
data class LibraryEntryRow<T>(
  val entry: T,
  val isGlobal: Boolean,
  val canEdit: Boolean,
  val canHide: Boolean = false,
  val isHidden: Boolean = false,
)

/**
 * Library state of the open organization, shown on its **Dictionary**, **Templates**, and
 * **Purposes** tabs. On `"All users"` it's the global library; elsewhere the organization's entries
 * are listed first, then the global ones.
 */
data class OrganizationLibraryUiState(
  val organizationId: String = "",
  /** Whether the open organization is `"All users"`, whose library is the global library. */
  val isGlobalLibrary: Boolean = false,
  /** Whether the signed-in user manages the open organization (and so its library). */
  val canEdit: Boolean = false,
  /** Search text of the Dictionary tab. */
  val dictionaryQuery: String = "",
  /** Concepts matching [dictionaryQuery] (all concepts when it's blank), best matches first. */
  val concepts: List<LibraryEntryRow<LibraryConcept>> = emptyList(),
  /** Number of concepts before filtering by [dictionaryQuery]. */
  val totalConceptCount: Int = 0,
  val formTemplates: List<LibraryEntryRow<FormTemplate>> = emptyList(),
  val purposePacks: List<LibraryEntryRow<PurposePack>> = emptyList(),
  /** Title of every template that can be listed in a Purpose Pack, keyed by template ID. */
  val templateTitles: Map<String, String> = emptyMap(),
) {
  /** Concepts owned by the open organization (all concepts on `"All users"`). */
  val organizationConcepts: List<LibraryEntryRow<LibraryConcept>>
    get() = concepts.filter { it.isGlobal == isGlobalLibrary }

  /** Global concepts listed on another organization's Dictionary tab (read-only there). */
  val globalConcepts: List<LibraryEntryRow<LibraryConcept>>
    get() = if (isGlobalLibrary) emptyList() else concepts.filter { it.isGlobal }

  /**
   * The ID a new concept named [name] gets in this library: `org.<organizationId>.<name>`, or
   * `<vocabulary>.<name>` in the global library.
   */
  fun newConceptId(name: String, vocabulary: String = ""): String =
    if (isGlobalLibrary) LibraryIds.globalConceptId(vocabulary.trim(), name.trim())
    else LibraryIds.organizationEntryId(organizationId, name.trim())

  /**
   * A concept name suggested from its English [label] (`"Cherry delivered (kg)"` →
   * `cherry_delivered_kg`).
   */
  fun suggestedName(label: String): String = LibraryIds.nameFrom(label, fallback = "")
}
