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
package org.openforis.collectone.v2.web.ui.formeditor

import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.editor.ConceptLinking
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.web.domain.usecase.ConceptMatchKind
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_error_dictionary_managers_only
import org.openforis.collectone.v2.web.resources.formeditor_error_enter_label
import org.openforis.collectone.v2.web.resources.formeditor_error_notes_cant_be_linked
import org.openforis.collectone.v2.web.resources.formeditor_error_question_not_found

/**
 * [ConceptLinkActions] of the Form editor: suggesting, linking, and adding dictionary concepts for
 * questions, applied to the Form of [session]. Suggestions and import matches live in [linkState];
 * the dictionary comes from [libraryFlow] and new concepts go through [addOrganizationConcept].
 */
internal class FormConceptLinkHandler(
  private val session: FormDraftSession,
  private val linkState: ConceptLinkState,
  private val libraryFlow: StateFlow<FormLibraryContext>,
  private val searchConcepts: SearchConceptsUseCase,
  private val addOrganizationConcept: (LibraryConcept) -> String?,
) : ConceptLinkActions {
  private val library: FormLibraryContext
    get() = libraryFlow.value

  /** Top concepts for [query] for [question]: type-compatible ones first. */
  private fun suggestionsFor(question: EditorQuestion, query: String): List<ConceptSuggestion> {
    val context = library
    val activeLang = session.uiState.activeLanguage
    val results =
      searchConcepts(
        context.library.concepts,
        query,
        SearchConceptsUseCase.SearchOptions(
          limit = SUGGESTION_POOL,
          organizationId = context.library.organizationId,
          boostedConceptIds = context.purposeConceptIds,
        ),
      )
    return results
      .map { it.concept }
      .sortedBy { !it.dataType.isCompatibleWith(question.type) }
      .take(FormEditorViewModel.MAX_SUGGESTIONS)
      .map { concept ->
        ConceptSuggestion(
          concept = concept,
          sourceLabel = context.sourceLabel(concept),
          label = concept.label.get(activeLang),
          description = concept.description.get(activeLang),
          isTypeCompatible = concept.dataType.isCompatibleWith(question.type),
        )
      }
  }

  private fun addToDictionaryLabel(query: String): String? =
    query.trim().takeIf {
      it.isNotEmpty() && library.canAddToDictionary && library.library.organizationId != null
    }

  override fun requestConceptSuggestions(key: String, query: String) {
    if (session.isReadOnly) return
    val question = session.form.find(key) ?: return
    val current = linkState.conceptSuggestions
    if (current?.isExplicitSearch == true && current.questionKey == key) return
    val trimmed = query.trim()
    if (trimmed.length < FormEditorViewModel.MIN_SUGGESTION_QUERY || question.conceptLink != null) {
      if (current?.questionKey == key) linkState.conceptSuggestions = null
      return
    }
    val suggestions = suggestionsFor(question, trimmed)
    linkState.conceptSuggestions =
      if (suggestions.isEmpty() && addToDictionaryLabel(trimmed) == null) null
      else
        ConceptSuggestionsState(
          questionKey = key,
          query = trimmed,
          suggestions = suggestions,
          addToDictionaryLabel = addToDictionaryLabel(trimmed),
        )
  }

  override fun openConceptSearch(key: String) {
    if (session.isReadOnly) return
    val question = session.form.find(key) ?: return
    val state = session.uiState
    val activeLabel = question.displayLabel(state.activeLanguage, state.defaultLanguage)
    linkState.conceptSuggestions =
      ConceptSuggestionsState(
        questionKey = key,
        query = activeLabel,
        suggestions = suggestionsFor(question, activeLabel),
        isExplicitSearch = true,
        addToDictionaryLabel = addToDictionaryLabel(activeLabel),
      )
  }

  override fun updateConceptSearchQuery(query: String) {
    val current = linkState.conceptSuggestions ?: return
    val question = session.form.find(current.questionKey) ?: return
    val state = session.uiState
    val activeLabel = question.displayLabel(state.activeLanguage, state.defaultLanguage)
    linkState.conceptSuggestions =
      current.copy(
        query = query,
        suggestions = suggestionsFor(question, query),
        addToDictionaryLabel = addToDictionaryLabel(activeLabel),
      )
  }

  override fun dismissConceptSuggestions() {
    linkState.conceptSuggestions = null
  }

  override fun linkConcept(key: String, conceptId: String) {
    val concept = library.concept(conceptId) ?: return
    val takenNames = session.form.questions.map { it.name }.toSet()
    val state = session.uiState
    session.updateQuestion(key) { q ->
      ConceptLinking.link(
        question = q,
        concept = concept,
        takenNames = takenNames,
        language = state.defaultLanguage,
        supportedLanguages = state.supportedLanguages,
      )
    }
    linkState.conceptSuggestions = null
  }

  override fun unlinkConcept(key: String) =
    session.updateQuestion(key) { q ->
      q.copy(conceptLink = null, choices = q.choices.map { it.copy(code = null) })
    }

  override fun useStandardLabel(key: String) {
    val question = session.form.find(key) ?: return
    val concept = library.concept(question.conceptLink?.conceptId) ?: return
    val state = session.uiState
    session.updateQuestion(key) {
      it.withLabelIn(
        state.activeLanguage,
        state.defaultLanguage,
        concept.label.get(state.activeLanguage),
      )
    }
  }

  override fun addLabelToDictionary(key: String): FormEditorMessage? {
    if (session.isReadOnly) return null
    val question =
      session.form.find(key)
        ?: return FormEditorMessage.Resource(Res.string.formeditor_error_question_not_found)
    val organizationId = library.library.organizationId
    if (!library.canAddToDictionary || organizationId == null) {
      return FormEditorMessage.Resource(Res.string.formeditor_error_dictionary_managers_only)
    }
    val label = question.label.trim()
    if (label.isEmpty()) return FormEditorMessage.Resource(Res.string.formeditor_error_enter_label)
    val dataType =
      ConceptLinking.conceptTypeFor(question.type, question.decimalPlaces)
        ?: return FormEditorMessage.Resource(Res.string.formeditor_error_notes_cant_be_linked)
    val name = LibraryIds.nameFrom(label)
    val codeList =
      if (dataType.hasCodeList && !question.usesDatasetChoices) {
        question.choices.map { choice ->
          CodeListItem(
            code =
              choice.code ?: LibraryIds.nameFrom(choice.value.ifBlank { choice.label }, "value"),
            label = LocalizedText.en(choice.label.ifBlank { choice.value }),
          )
        }
      } else {
        emptyList()
      }
    val concept =
      LibraryConcept(
        id = LibraryIds.organizationEntryId(organizationId, name),
        organizationId = organizationId,
        label = LocalizedText.en(label),
        dataType = dataType,
        description =
          LocalizedText.en(question.hint.trim()).takeUnless { question.hint.isBlank() }
            ?: LocalizedText(),
        codeList = codeList,
        aggregation =
          when {
            dataType.hasCodeList -> ConceptAggregation.COUNT_BY_CODE
            question.type.isNumeric -> ConceptAggregation.SUM
            else -> ConceptAggregation.NONE
          },
        status = LibraryStatus.DRAFT,
      )
    addOrganizationConcept(concept)?.let {
      return FormEditorMessage.Text(it)
    }
    session.updateQuestion(key) { q ->
      q.copy(
        conceptLink = ConceptLink.to(concept),
        choices =
          if (codeList.isEmpty()) q.choices
          else q.choices.mapIndexed { i, c -> c.copy(code = codeList.getOrNull(i)?.code) },
      )
    }
    linkState.conceptSuggestions = null
    return null
  }

  override fun setChoiceCode(key: String, index: Int, code: String?) =
    session.updateChoiceAt(key, index) { it.copy(code = code?.takeIf { c -> c.isNotBlank() }) }

  /**
   * Scans unlinked non-Note questions after an XForms/IDM import for candidate dictionary concepts:
   * searches by question label first (boosting concepts from the survey's selected purpose packs),
   * falls back to the de-underscored XForms node `name`, drops low-confidence fuzzy hits below
   * [MIN_IMPORT_FUZZY_SCORE], and deduplicates by concept ID so two imported fields are never
   * auto-offered the same standard concept.
   */
  override fun suggestImportMatches() {
    if (session.isReadOnly) return
    val context = library
    val matches =
      session.form.questions
        .filter { it.conceptLink == null && it.type != EditorQuestionType.NOTE }
        .mapNotNull { question ->
          val best =
            searchConcepts(
                context.library.concepts,
                question.label,
                SearchConceptsUseCase.SearchOptions(
                  questionType = question.type,
                  limit = 1,
                  organizationId = context.library.organizationId,
                  boostedConceptIds = context.purposeConceptIds,
                ),
              )
              .firstOrNull()
              ?: searchConcepts(
                  context.library.concepts,
                  question.name.replace('_', ' '),
                  SearchConceptsUseCase.SearchOptions(questionType = question.type, limit = 1),
                )
                .firstOrNull()
              ?: return@mapNotNull null
          if (best.kind == ConceptMatchKind.FUZZY && best.score < MIN_IMPORT_FUZZY_SCORE) {
            return@mapNotNull null
          }
          ImportMatch(
            questionKey = question.key,
            questionLabel = question.label,
            concept = best.concept,
            sourceLabel = context.sourceLabel(best.concept),
            isStrong = best.kind == ConceptMatchKind.EXACT || best.kind == ConceptMatchKind.PREFIX,
          )
        }
        .distinctBy { it.concept.id }
    linkState.importMatches = matches
  }

  override fun setImportMatchChecked(key: String, checked: Boolean) {
    linkState.importMatches =
      linkState.importMatches.map {
        if (it.questionKey == key) it.copy(isChecked = checked) else it
      }
  }

  override fun linkImportMatches() {
    val checked = linkState.importMatches.filter { it.isChecked }
    if (checked.isNotEmpty()) {
      val state = session.uiState
      session.mutateForm { currentForm ->
        var updatedQuestions = currentForm.questions
        for (match in checked) {
          val concept = library.concept(match.concept.id) ?: continue
          val takenNames = updatedQuestions.map { it.name }.toSet()
          updatedQuestions = updatedQuestions.map { q ->
            if (q.key == match.questionKey) {
              ConceptLinking.link(
                question = q,
                concept = concept,
                takenNames = takenNames,
                language = state.defaultLanguage,
                supportedLanguages = state.supportedLanguages,
              )
            } else {
              q
            }
          }
        }
        currentForm.copy(questions = updatedQuestions)
      }
      linkState.conceptSuggestions = null
    }
    dismissImportMatches()
  }

  override fun dismissImportMatches() {
    linkState.importMatches = emptyList()
  }

  private companion object {
    const val SUGGESTION_POOL = 25

    /** Weakest fuzzy match offered after an import (a single typo in a label word). */
    const val MIN_IMPORT_FUZZY_SCORE = 18.0
  }
}

/** Where [FormConceptLinkHandler] keeps the concept suggestions and import matches on offer. */
internal interface ConceptLinkState {
  /** Concepts suggested for the question being labeled, or `null` when none are shown. */
  var conceptSuggestions: ConceptSuggestionsState?

  /** Concepts suggested for an imported Form's unlinked questions. */
  var importMatches: List<ImportMatch>
}
