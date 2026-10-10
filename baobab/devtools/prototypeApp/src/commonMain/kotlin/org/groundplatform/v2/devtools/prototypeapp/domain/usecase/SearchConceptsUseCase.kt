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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType

/** How a [ConceptSearchResult] matched the query, strongest first. */
enum class ConceptMatchKind {
  /** A label, keyword, or code-list value equals the query. */
  EXACT,
  /** A label, keyword, or code-list value starts with the query. */
  PREFIX,
  /** Every query word starts a word of a label, keyword, or code-list value. */
  TOKENS,
  /** A label, keyword, or code-list value contains the query. */
  SUBSTRING,
  /** Every query word is within a small edit distance of a word (typo tolerance). */
  FUZZY,
  /** The query was blank, so every concept matches. */
  ALL,
}

/** One concept found by [SearchConceptsUseCase], with what matched and how strongly. */
data class ConceptSearchResult(
  val concept: LibraryConcept,
  val score: Double,
  val kind: ConceptMatchKind,
  /** The label, keyword, or code-list value that matched (empty for [ConceptMatchKind.ALL]). */
  val matchedText: String,
)

/**
 * Lexical search over a resolved library's concepts, run in the client (see
 * `docs/technical/model/library/00-introduction.md`). Pure.
 *
 * Matching folds case and accents and looks at labels, keywords, and code-list value labels in
 * every language, plus the concept ID's name. Exact, prefix, word-prefix, and substring matches
 * come first; fuzzy matching (up to 1 edit for words of 4 or fewer letters, 2 for longer ones) is a
 * fallback for concepts with no lexical match. Ranking boosts organization concepts and
 * [SearchOptions.boostedConceptIds] (for example concepts of the survey's Purpose Packs).
 */
class SearchConceptsUseCase {
  /** Search parameters. */
  data class SearchOptions(
    /** When set, only concepts that can be linked to a question of this type are returned. */
    val questionType: EditorQuestionType? = null,
    /** Maximum number of results. */
    val limit: Int = DEFAULT_LIMIT,
    /** Whether deprecated concepts are included (they're hidden from pickers by default). */
    val includeDeprecated: Boolean = false,
    /** Organization whose concepts are boosted, usually the survey's. */
    val organizationId: String? = null,
    /** Concepts ranked higher, for example those used by the survey's Purpose Packs. */
    val boostedConceptIds: Set<String> = emptySet(),
  )

  operator fun invoke(
    concepts: List<LibraryConcept>,
    query: String,
    options: SearchOptions = SearchOptions(),
  ): List<ConceptSearchResult> {
    val candidates = concepts.filter { concept ->
      (options.includeDeprecated || !concept.isDeprecated) &&
        (options.questionType == null || concept.dataType.isCompatibleWith(options.questionType))
    }
    val normalizedQuery = LibraryText.tokens(query).joinToString(" ")
    if (normalizedQuery.isEmpty()) {
      return candidates.take(options.limit.coerceAtLeast(0)).map {
        ConceptSearchResult(it, 0.0, ConceptMatchKind.ALL, "")
      }
    }
    val queryTokens = normalizedQuery.split(' ')
    return candidates
      .mapIndexedNotNull { index, concept ->
        match(concept, normalizedQuery, queryTokens)?.let { (kind, base, text) ->
          Ranked(ConceptSearchResult(concept, base + boost(concept, options), kind, text), index)
        }
      }
      // Fuzzy matches are a fallback: they always rank after lexical ones.
      .sortedWith(
        compareBy<Ranked> { it.result.kind == ConceptMatchKind.FUZZY }
          .thenByDescending { it.result.score }
          .thenBy { it.order }
      )
      .take(options.limit.coerceAtLeast(0))
      .map { it.result }
  }

  private data class Ranked(val result: ConceptSearchResult, val order: Int)

  private data class Field(
    val text: String,
    val folded: String,
    val tokens: List<String>,
    val weight: Double,
  )

  private fun fieldsOf(concept: LibraryConcept): List<Field> = buildList {
    fun add(text: String, weight: Double) {
      if (text.isBlank()) return
      val tokens = LibraryText.tokens(text)
      add(Field(text, tokens.joinToString(" "), tokens, weight))
    }
    concept.label.values.values.forEach { add(it, LABEL_WEIGHT) }
    concept.keywords.forEach { keyword ->
      keyword.values.values.forEach { add(it, KEYWORD_WEIGHT) }
    }
    concept.codeList.forEach { item ->
      item.label.values.values.forEach { add(it, CODE_WEIGHT) }
      add(item.code.replace('_', ' '), CODE_WEIGHT)
    }
    add(LibraryIds.nameOf(concept.id).replace('_', ' '), ID_WEIGHT)
  }

  /** Best (kind, score, matched text) of [concept], or `null` when nothing matches. */
  private fun match(
    concept: LibraryConcept,
    query: String,
    queryTokens: List<String>,
  ): Triple<ConceptMatchKind, Double, String>? {
    val fields = fieldsOf(concept)
    var best: Triple<ConceptMatchKind, Double, String>? = null
    fun consider(kind: ConceptMatchKind, score: Double, field: Field) {
      val weighted = score * field.weight
      if (best == null || weighted > best!!.second) best = Triple(kind, weighted, field.text)
    }
    for (field in fields) {
      when {
        field.folded == query -> consider(ConceptMatchKind.EXACT, EXACT_SCORE, field)
        field.folded.startsWith(query) -> consider(ConceptMatchKind.PREFIX, PREFIX_SCORE, field)
        queryTokens.all { q -> field.tokens.any { it.startsWith(q) } } ->
          consider(ConceptMatchKind.TOKENS, TOKENS_SCORE, field)
        field.folded.contains(query) -> consider(ConceptMatchKind.SUBSTRING, SUBSTRING_SCORE, field)
      }
    }
    if (best != null) return best
    for (field in fields) {
      var total = 0
      val allWordsMatch = queryTokens.all { q ->
        val distance = field.tokens.minOfOrNull { fuzzyDistance(q, it) } ?: Int.MAX_VALUE
        if (distance <= maxEdits(q)) {
          total += distance
          true
        } else {
          false
        }
      }
      if (allWordsMatch) {
        consider(ConceptMatchKind.FUZZY, FUZZY_SCORE - total * FUZZY_PENALTY_PER_EDIT, field)
      }
    }
    return best
  }

  /**
   * Distance between query word [q] and field word [word], also comparing [q] with [word]'s prefix
   * of the same length so partially typed words with a typo still match.
   */
  private fun fuzzyDistance(q: String, word: String): Int {
    if (q.length < MIN_FUZZY_LENGTH) return if (q == word) 0 else Int.MAX_VALUE
    val max = maxEdits(q)
    val whole = LibraryText.editDistance(q, word, max)
    val prefix =
      if (word.length > q.length) LibraryText.editDistance(q, word.take(q.length), max) else whole
    return minOf(whole, prefix)
  }

  private fun maxEdits(q: String): Int = if (q.length <= 4) 1 else 2

  private fun boost(concept: LibraryConcept, options: SearchOptions): Double {
    var boost = 0.0
    if (
      options.organizationId != null &&
        LibraryIds.organizationIdOf(concept.id) == options.organizationId
    ) {
      boost += ORGANIZATION_BOOST
    }
    if (concept.id in options.boostedConceptIds) boost += PURPOSE_BOOST
    return boost
  }

  companion object {
    const val DEFAULT_LIMIT = 10

    private const val EXACT_SCORE = 100.0
    private const val PREFIX_SCORE = 80.0
    private const val TOKENS_SCORE = 60.0
    private const val SUBSTRING_SCORE = 40.0
    private const val FUZZY_SCORE = 25.0
    private const val FUZZY_PENALTY_PER_EDIT = 5.0
    private const val MIN_FUZZY_LENGTH = 3

    private const val LABEL_WEIGHT = 1.0
    private const val KEYWORD_WEIGHT = 0.9
    private const val CODE_WEIGHT = 0.7
    private const val ID_WEIGHT = 0.8

    private const val ORGANIZATION_BOOST = 10.0
    private const val PURPOSE_BOOST = 8.0
  }
}
