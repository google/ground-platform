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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary

/**
 * The dictionary available to the Form editor: the survey's resolved library (its organization's
 * concepts, then global ones) and who may add to it.
 */
data class FormLibraryContext(
  val library: ResolvedLibrary = ResolvedLibrary(organizationId = null),
  /** Name of the survey's organization, or `null` for a personal survey. */
  val organizationName: String? = null,
  /** Whether the signed-in user manages the survey's organization (and so its dictionary). */
  val canAddToDictionary: Boolean = false,
  /** Language that concept labels and code-list values are shown in (the survey's default). */
  val language: String = LocalizedText.DEFAULT_LANGUAGE,
  /** Concepts used by the survey's purposes, ranked first in suggestions. */
  val purposeConceptIds: Set<String> = emptySet(),
) {
  /** The concept [conceptId] in the resolved library, or `null`. */
  fun concept(conceptId: String?): LibraryConcept? = conceptId?.let { library.concept(it) }

  /**
   * Badge naming where [concept] comes from: the organization's name for its own concepts, or the
   * vocabulary (e.g. "EUDR") for global ones.
   */
  fun sourceLabel(concept: LibraryConcept): String =
    if (library.isOrganizationEntry(concept.id)) organizationName ?: "Organization"
    else vocabularyLabel(LibraryIds.vocabularyOf(concept.id).orEmpty())

  companion object {
    private val VOCABULARY_LABELS =
      mapOf(
        "core" to "Core",
        "eudr" to "EUDR",
        "ferm" to "FERM",
        "pame" to "PAME",
        "iplc" to "IPLC",
        "lulc" to "LULC",
        "timber" to "Timber",
      )

    /** Display name of a global vocabulary (`eudr` → `EUDR`). */
    fun vocabularyLabel(vocabulary: String): String =
      VOCABULARY_LABELS[vocabulary] ?: vocabulary.uppercase()
  }
}

/** One concept suggested for a question's label. */
data class ConceptSuggestion(
  val concept: LibraryConcept,
  /** Organization name or vocabulary badge ([FormLibraryContext.sourceLabel]). */
  val sourceLabel: String,
  /** Label in the survey's language. */
  val label: String,
  /** One-line description in the survey's language. */
  val description: String,
  /** Whether the concept fits the question's current type. */
  val isTypeCompatible: Boolean,
)

/**
 * The open suggestion list for question [questionKey]: from typing its label (non-modal, under the
 * Label field) or from **Link to standard field…** ([isExplicitSearch], with its own search field).
 */
data class ConceptSuggestionsState(
  val questionKey: String,
  val query: String,
  val suggestions: List<ConceptSuggestion>,
  val isExplicitSearch: Boolean = false,
  /**
   * Label offered as "＋ Add '<label>' to <Org> dictionary", or `null` when the user can't add to
   * the dictionary (not a Manager, personal survey, or blank label).
   */
  val addToDictionaryLabel: String? = null,
)

/** A question of a just-imported Form that matches a concept. */
data class ImportMatch(
  val questionKey: String,
  val questionLabel: String,
  val concept: LibraryConcept,
  val sourceLabel: String,
  /** Whether the match is exact or near-exact (pre-checked). */
  val isStrong: Boolean,
  val isChecked: Boolean = isStrong,
)
