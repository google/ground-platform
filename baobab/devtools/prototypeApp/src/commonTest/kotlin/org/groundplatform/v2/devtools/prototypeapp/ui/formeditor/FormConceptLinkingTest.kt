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
package org.groundplatform.v2.devtools.prototypeapp.ui.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CodeListItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormLibraryContext
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.testScope
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

/** Linking Form questions to dictionary concepts in the Form editor. */
class FormConceptLinkingTest {
  private val orgId = "kfs"

  private fun concept(
    id: String,
    label: String,
    type: ConceptDataType,
    codes: List<Pair<String, String>> = emptyList(),
    description: String = "",
    organizationId: String = Organization.ALL_USERS_ID,
  ) =
    LibraryConcept(
      id = id,
      organizationId = organizationId,
      label = LocalizedText.en(label),
      dataType = type,
      description = LocalizedText.en(description),
      codeList = codes.map { (code, text) -> CodeListItem(code, LocalizedText.en(text)) },
      status = LibraryStatus.STABLE,
    )

  private val commodity =
    concept(
      "eudr.commodity",
      "Commodity",
      ConceptDataType.SELECT_ONE,
      listOf("coffee" to "Coffee", "cocoa" to "Cocoa"),
      description = "Commodity produced on the plot.",
    )
  private val plotArea = concept("core.plot_area", "Plot area", ConceptDataType.DECIMAL)
  private val plotName = concept("core.plot_name", "Plot name", ConceptDataType.TEXT)
  private val plotId = concept("core.plot_id", "Plot ID", ConceptDataType.TEXT)
  private val plotPhoto = concept("core.plot_photo", "Plot photo", ConceptDataType.MEDIA)
  private val plotBoundary = concept("core.plot_boundary", "Plot boundary", ConceptDataType.POLYGON)
  private val plotOwner = concept("core.plot_owner", "Plot owner", ConceptDataType.TEXT)
  private val shadeTrees =
    concept(
      "org.kfs.shade_tree_count",
      "Shade trees",
      ConceptDataType.INTEGER,
      organizationId = orgId,
    )

  private val library =
    MutableStateFlow(
      FormLibraryContext(
        library =
          ResolvedLibrary(
            organizationId = orgId,
            concepts =
              listOf(
                shadeTrees,
                commodity,
                plotArea,
                plotName,
                plotId,
                plotPhoto,
                plotBoundary,
                plotOwner,
              ),
          ),
        organizationName = "Kahawa Farmers",
        canAddToDictionary = true,
      )
    )

  private val added = mutableListOf<LibraryConcept>()

  private fun editor(vararg questions: EditorQuestion): FormEditorViewModel {
    val form = MutableStateFlow(EditorForm("visit", "Visit", questions.toList()))
    return FormEditorViewModel(
      formFlow = form,
      datasetsFlow = MutableStateFlow(emptyList()),
      updateForm = { transform -> form.update(transform) },
      scope = testScope(),
      libraryFlow = library,
      addOrganizationConcept = { concept ->
        added += concept
        library.update {
          it.copy(library = it.library.copy(concepts = it.library.concepts + concept))
        }
        null
      },
    )
  }

  private fun textQuestion(label: String = "New text question", name: String = "text") =
    EditorQuestion("q1", name, EditorQuestionType.TEXT, label)

  private val FormEditorViewModel.q1: EditorQuestion
    get() = ui.form.questions.first { it.key == "q1" }

  @Test
  fun suggestions_needThreeCharacters() {
    val vm = editor(textQuestion())
    vm.requestConceptSuggestions("q1", "pl")
    assertNull(vm.ui.conceptSuggestions)
    vm.requestConceptSuggestions("q1", "plo")
    assertNotNull(vm.ui.conceptSuggestions)
  }

  @Test
  fun suggestions_areTopFive_typeCompatibleFirst_withSourceBadges() {
    val vm = editor(textQuestion())
    vm.requestConceptSuggestions("q1", "Plot")
    val state = assertNotNull(vm.ui.conceptSuggestions)
    assertEquals("q1", state.questionKey)
    assertFalse(state.isExplicitSearch)
    assertEquals(FormEditorViewModel.MAX_SUGGESTIONS, state.suggestions.size)
    val compatibility = state.suggestions.map { it.isTypeCompatible }
    assertEquals(compatibility.sortedDescending(), compatibility)
    assertTrue(state.suggestions.first().isTypeCompatible)
    assertTrue(state.suggestions.all { it.sourceLabel == "Core" })
    assertEquals("Plot", state.addToDictionaryLabel)
  }

  @Test
  fun suggestions_showOrganizationNameAsSource_andDescription() {
    val vm = editor(textQuestion())
    vm.requestConceptSuggestions("q1", "shade")
    assertEquals(
      listOf("Kahawa Farmers"),
      vm.ui.conceptSuggestions?.suggestions?.map { it.sourceLabel },
    )
    vm.requestConceptSuggestions("q1", "commodity")
    assertEquals(
      "Commodity produced on the plot.",
      vm.ui.conceptSuggestions?.suggestions?.first()?.description,
    )
  }

  @Test
  fun suggestions_neverLinkByThemselves_andCanBeDismissed() {
    val vm = editor(textQuestion())
    vm.requestConceptSuggestions("q1", "Commodity")
    assertNull(vm.q1.conceptLink)
    vm.dismissConceptSuggestions()
    assertNull(vm.ui.conceptSuggestions)
  }

  @Test
  fun suggestions_offerNoDictionaryAddition_toNonManagers() {
    library.update { it.copy(canAddToDictionary = false) }
    val vm = editor(textQuestion())
    vm.requestConceptSuggestions("q1", "Plot")
    assertNull(vm.ui.conceptSuggestions?.addToDictionaryLabel)
  }

  @Test
  fun linkConcept_defaultStateQuestion_autofillsAndKeepsLabel() {
    val vm = editor(textQuestion(label = "Main crop"))
    vm.requestConceptSuggestions("q1", "Main crop commodity")
    vm.linkConcept("q1", commodity.id)
    val q = vm.q1
    assertEquals(ConceptLink(commodity.id, 1), q.conceptLink)
    assertEquals(EditorQuestionType.SELECT_ONE, q.type)
    assertEquals("commodity", q.name)
    assertEquals("Main crop", q.label)
    assertEquals(listOf("coffee", "cocoa"), q.choices.map { it.code })
    assertNull(vm.ui.conceptSuggestions)
    assertEquals(commodity, vm.ui.conceptOf(q))
  }

  @Test
  fun linkConcept_customizedQuestion_keepsItsSettings() {
    val vm = editor(textQuestion(label = "Crop", name = "crop").copy(hint = "Ask the farmer"))
    vm.linkConcept("q1", commodity.id)
    val q = vm.q1
    assertEquals(EditorQuestionType.TEXT, q.type)
    assertEquals("crop", q.name)
    assertEquals("Ask the farmer", q.hint)
    // The mismatch is a warning, never an issue that blocks publishing.
    assertTrue(vm.ui.warningsFor("q1").any { "expects select one answers" in it.message })
    assertTrue(vm.ui.issuesFor("q1").isEmpty())
  }

  @Test
  fun unlinkAndUseStandardLabel() {
    val vm = editor(textQuestion(label = "Main crop"))
    vm.linkConcept("q1", commodity.id)
    vm.useStandardLabel("q1")
    assertEquals("Commodity", vm.q1.label)
    vm.unlinkConcept("q1")
    assertNull(vm.q1.conceptLink)
    assertTrue(vm.q1.choices.all { it.code == null })
  }

  @Test
  fun openConceptSearch_isExplicit_andFollowsTheQuery() {
    val vm = editor(textQuestion(label = "Area"))
    vm.openConceptSearch("q1")
    val state = assertNotNull(vm.ui.conceptSuggestions)
    assertTrue(state.isExplicitSearch)
    // Typing in the Label field doesn't replace an explicit search.
    vm.requestConceptSuggestions("q1", "xyz")
    assertTrue(vm.ui.conceptSuggestions?.isExplicitSearch == true)
    vm.updateConceptSearchQuery("shade")
    assertEquals(
      listOf(shadeTrees.id),
      vm.ui.conceptSuggestions?.suggestions?.map { it.concept.id },
    )
  }

  @Test
  fun addLabelToDictionary_createsADraftOrganizationConceptAndLinksIt() {
    val vm =
      editor(
        EditorQuestion(
          "q1",
          "harvest",
          EditorQuestionType.SELECT_ONE,
          "Harvest season",
          choices = listOf(EditorChoice("main", "Main"), EditorChoice("fly", "Fly crop")),
        )
      )
    assertNull(vm.addLabelToDictionary("q1"))
    val concept = added.single()
    assertEquals("org.kfs.harvest_season", concept.id)
    assertEquals(orgId, concept.organizationId)
    assertEquals(LibraryStatus.DRAFT, concept.status)
    assertEquals(ConceptDataType.SELECT_ONE, concept.dataType)
    assertEquals(listOf("main", "fly"), concept.codeList.map { it.code })
    assertEquals(ConceptLink(concept.id, 1), vm.q1.conceptLink)
    assertEquals(listOf("main", "fly"), vm.q1.choices.map { it.code })
    assertTrue(vm.ui.warningsFor("q1").isEmpty())
  }

  @Test
  fun addLabelToDictionary_isRefusedWithoutManagerRole() {
    library.update { it.copy(canAddToDictionary = false) }
    val vm = editor(textQuestion(label = "Harvest season"))
    assertNotNull(vm.addLabelToDictionary("q1"))
    assertTrue(added.isEmpty())
    assertNull(vm.q1.conceptLink)
  }

  @Test
  fun setChoiceCode_mapsManualChoices_andClearsTheWarning() {
    val vm =
      editor(
        EditorQuestion(
          "q1",
          "crop",
          EditorQuestionType.SELECT_ONE,
          "Crop",
          choices = listOf(EditorChoice("a", "Arabica"), EditorChoice("b", "Cacao beans")),
        )
      )
    vm.linkConcept("q1", commodity.id)
    assertTrue(vm.ui.warningsFor("q1").any { "Match 2 choices" in it.message })
    vm.setChoiceCode("q1", 0, "coffee")
    vm.setChoiceCode("q1", 1, "cocoa")
    assertEquals(listOf("coffee", "cocoa"), vm.q1.choices.map { it.code })
    assertTrue(vm.ui.warningsFor("q1").isEmpty())
    vm.setChoiceCode("q1", 1, null)
    assertNull(vm.q1.choices[1].code)
  }

  @Test
  fun deleteWarning_onlyForLinkedQuestions() {
    val vm = editor(textQuestion(label = "Crop", name = "crop"))
    assertNull(vm.ui.deleteWarningFor(vm.q1))
    vm.linkConcept("q1", commodity.id)
    val warning = assertNotNull(vm.ui.deleteWarningFor(vm.q1))
    assertTrue("Commodity" in warning, warning)
  }

  @Test
  fun importMatches_preCheckStrongMatches_andLinkOnlyCheckedOnes() {
    val vm =
      editor(
        EditorQuestion("q1", "area", EditorQuestionType.DECIMAL, "Plot area"),
        EditorQuestion("q2", "count", EditorQuestionType.INTEGER, "Trees (shade)"),
        EditorQuestion("q3", "remarks", EditorQuestionType.TEXT, "Zzyzx qwv"),
        EditorQuestion(
          "q4",
          "linked",
          EditorQuestionType.TEXT,
          "Plot name",
          conceptLink = ConceptLink(plotName.id),
        ),
      )
    vm.suggestImportMatches()
    val matches = vm.ui.importMatches.associateBy { it.questionKey }
    assertEquals(setOf("q1", "q2"), matches.keys)
    assertEquals(plotArea.id, matches.getValue("q1").concept.id)
    assertTrue(matches.getValue("q1").isChecked)
    assertEquals(shadeTrees.id, matches.getValue("q2").concept.id)
    assertFalse(matches.getValue("q2").isStrong)
    assertFalse(matches.getValue("q2").isChecked)

    vm.setImportMatchChecked("q2", true)
    vm.setImportMatchChecked("q1", false)
    vm.linkImportMatches()
    val byKey = vm.ui.form.questions.associateBy { it.key }
    assertNull(byKey.getValue("q1").conceptLink)
    assertEquals(ConceptLink(shadeTrees.id), byKey.getValue("q2").conceptLink)
    assertTrue(vm.ui.importMatches.isEmpty())
  }
}
