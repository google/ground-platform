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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.GlobalLibrarySeedData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchConceptsUseCase.SearchOptions

class SearchConceptsUseCaseTest {
  private val search = SearchConceptsUseCase()
  private val global = GlobalLibrarySeedData.library().concepts

  private fun top(query: String, options: SearchOptions = SearchOptions()): String? =
    search(global, query, options).firstOrNull()?.concept?.id

  @Test
  fun multilingualLabelsKeywordsAndValues_findTheCommodityConcept() {
    assertEquals("eudr.commodity", top("cultivo")) // Spanish keyword.
    assertEquals("eudr.commodity", top("café")) // Spanish/French code-list value label.
    assertEquals("eudr.commodity", top("cây trồng")) // Vietnamese keyword.
    assertEquals("eudr.commodity", top("Mặt hàng")) // Vietnamese label.
    assertEquals("eudr.commodity", top("zao")) // Swahili keyword.
    assertEquals("eudr.commodity", top("Commodity"))
  }

  @Test
  fun matching_foldsCaseAndAccents() {
    assertEquals("eudr.commodity", top("CAFE"))
    assertEquals("eudr.commodity", top("cay trong"))
    assertEquals("core.area_ha", top("superficie"))
    assertEquals("core.admin_area", top("đơn vị hành chính"))
  }

  @Test
  fun prefixAndTokenMatches_rankAboveSubstringMatches() {
    val results = search(global, "trees")
    assertTrue(
      results
        .map { it.concept.id }
        .containsAll(listOf("ferm.trees_planted", "ferm.trees_surviving"))
    )
    assertEquals(ConceptMatchKind.PREFIX, results.first().kind)
    // Word-prefix: "surv" starts the second word of "Trees surviving".
    assertEquals("ferm.trees_surviving", top("surv trees"))
    assertEquals(ConceptMatchKind.TOKENS, search(global, "surv trees").first().kind)
  }

  @Test
  fun fuzzyMatching_isAFallbackForTypos() {
    val results = search(global, "comodity")
    assertEquals("eudr.commodity", results.first().concept.id)
    assertEquals(ConceptMatchKind.FUZZY, results.first().kind)
    assertEquals("ferm.trees_surviving", top("trees survivng"))
    // Short words need an exact (prefix) match; fuzzy needs at least 3 letters.
    assertTrue(search(global, "qz").isEmpty())
    // More than 2 edits don't match.
    assertTrue(search(global, "cmmdtyxx").none { it.concept.id == "eudr.commodity" })
  }

  @Test
  fun fuzzyResults_neverOutrankLexicalOnes_evenWhenBoosted() {
    val typo = LibraryConcept("org.o.plotz", "o", LocalizedText.en("Plotz"), ConceptDataType.TEXT)
    val results = search(global + typo, "plot", SearchOptions(organizationId = "o", limit = 50))
    val kinds = results.map { it.kind }
    val firstFuzzy = kinds.indexOf(ConceptMatchKind.FUZZY).takeIf { it >= 0 } ?: kinds.size
    assertTrue(kinds.drop(firstFuzzy).all { it == ConceptMatchKind.FUZZY })
  }

  @Test
  fun organizationAndPurposeConcepts_areBoosted() {
    fun plotSize(id: String, owner: String) =
      LibraryConcept(id, owner, LocalizedText.en("Plot size"), ConceptDataType.DECIMAL)
    val concepts =
      listOf(
        plotSize("core.plot_size", "g"),
        plotSize("eudr.plot_size", "g"),
        plotSize("org.o.plot_size", "o"),
      )
    // Equal matches keep library order...
    assertEquals("core.plot_size", search(concepts, "plot").first().concept.id)
    // ...unless the concept belongs to the survey's organization or one of its purposes.
    assertEquals(
      "org.o.plot_size",
      search(concepts, "plot", SearchOptions(organizationId = "o")).first().concept.id,
    )
    assertEquals(
      "eudr.plot_size",
      search(concepts, "plot", SearchOptions(boostedConceptIds = setOf("eudr.plot_size")))
        .first()
        .concept
        .id,
    )
    // A boost doesn't outrank a clearly better match.
    val exact = LibraryConcept("core.plot", "g", LocalizedText.en("Plot"), ConceptDataType.TEXT)
    assertEquals(
      "core.plot",
      search(concepts + exact, "plot", SearchOptions(organizationId = "o")).first().concept.id,
    )
  }

  @Test
  fun typeFilter_keepsOnlyCompatibleConcepts() {
    val results =
      search(global, "area", SearchOptions(questionType = EditorQuestionType.TEXT, limit = 50))
    assertTrue(results.none { it.concept.id == "core.area_ha" })
    assertTrue(results.all { it.concept.dataType == ConceptDataType.TEXT })
    val decimal =
      search(global, "", SearchOptions(questionType = EditorQuestionType.INTEGER, limit = 100))
    assertTrue(
      decimal.all { it.concept.dataType in setOf(ConceptDataType.INTEGER, ConceptDataType.DECIMAL) }
    )
    assertTrue(decimal.isNotEmpty())
  }

  @Test
  fun deprecatedConcepts_areHiddenUnlessRequested() {
    val deprecated =
      LibraryConcept(
        "core.old_area",
        global.first().organizationId,
        LocalizedText.en("Old area"),
        ConceptDataType.DECIMAL,
        status = LibraryStatus.DEPRECATED,
      )
    assertTrue(search(global + deprecated, "old area").none { it.concept.id == "core.old_area" })
    assertEquals(
      "core.old_area",
      search(global + deprecated, "old area", SearchOptions(includeDeprecated = true))
        .first()
        .concept
        .id,
    )
  }

  @Test
  fun blankQuery_returnsConceptsInLibraryOrder_upToTheLimit() {
    val results = search(global, "  ", SearchOptions(limit = 3))
    assertEquals(global.take(3).map { it.id }, results.map { it.concept.id })
    assertTrue(results.all { it.kind == ConceptMatchKind.ALL })
    assertEquals(SearchConceptsUseCase.DEFAULT_LIMIT, search(global, "").size)
    assertTrue(search(global, "a", SearchOptions(limit = 0)).isEmpty())
  }

  @Test
  fun results_reportTheMatchedText() {
    val result = search(global, "cultivo").first()
    assertEquals("cultivo", result.matchedText)
    assertEquals(ConceptMatchKind.EXACT, result.kind)
  }
}
