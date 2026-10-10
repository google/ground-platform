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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.GROUND_XFORMS_NAMESPACE
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CodeListItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization

/** Concept links of Form questions: storage, XForms, `save_to`, autofill, and warnings. */
class FormConceptLinksTest {
  private val commodity =
    LibraryConcept(
      id = "eudr.commodity",
      organizationId = Organization.ALL_USERS_ID,
      label = LocalizedText.of("en" to "Commodity", "es" to "Producto"),
      dataType = ConceptDataType.SELECT_ONE,
      codeList =
        listOf(
          CodeListItem("coffee", LocalizedText.of("en" to "Coffee", "es" to "Café")),
          CodeListItem("cocoa", LocalizedText.of("en" to "Cocoa", "es" to "Cacao")),
        ),
      status = LibraryStatus.STABLE,
    )

  private val area =
    LibraryConcept(
      id = "core.plot_area",
      organizationId = Organization.ALL_USERS_ID,
      label = LocalizedText.en("Plot area"),
      dataType = ConceptDataType.DECIMAL,
      unit = "har",
      version = 2,
      status = LibraryStatus.STABLE,
    )

  private val concepts = listOf(commodity, area).associateBy { it.id }

  private fun question(
    key: String,
    name: String,
    type: EditorQuestionType = EditorQuestionType.TEXT,
    label: String = "New question",
    conceptLink: ConceptLink? = null,
    choices: List<EditorChoice> = emptyList(),
  ) = EditorQuestion(key, name, type, label, choices = choices, conceptLink = conceptLink)

  private fun form(vararg questions: EditorQuestion) =
    EditorForm(formId = "visit", title = "Visit", questions = questions.toList())

  // --- ConceptLink ---

  @Test
  fun conceptLink_encodesAndParsesVersionedIds() {
    assertEquals("eudr.commodity@3", ConceptLink("eudr.commodity", 3).encoded)
    assertEquals(ConceptLink("eudr.commodity", 3), ConceptLink.parse("eudr.commodity@3"))
    assertEquals(ConceptLink("eudr.commodity", 1), ConceptLink.parse(" eudr.commodity "))
    assertNull(ConceptLink.parse(""))
    assertNull(ConceptLink.parse("eudr.commodity@x"))
    assertNull(ConceptLink.parse("eudr.commodity@0"))
    assertNull(ConceptLink.parse("two words"))
  }

  // --- ground:concept attributes ---

  @Test
  fun bindAttributes_keepsForeignAttributesAndAddsGroundConcept() {
    val acme = EditorForeignAttribute("https://example.org/acme", "acme:hint-style", "bold")
    val q =
      question("q1", "commodity", conceptLink = ConceptLink("eudr.commodity", 1))
        .copy(foreignAttributes = listOf(acme))
    val attributes = ConceptLinkAttributes.bindAttributes(q)
    assertEquals(
      listOf(
        Triple("https://example.org/acme", "acme:hint-style", "bold"),
        Triple(GROUND_XFORMS_NAMESPACE, "ground:concept", "eudr.commodity@1"),
      ),
      attributes.map { Triple(it.namespace_uri, it.qualified_name, it.value_) },
    )
  }

  @Test
  fun bindAttributes_linkReplacesAPreservedGroundConcept() {
    val stale = EditorForeignAttribute(GROUND_XFORMS_NAMESPACE, "g:concept", "old.concept@1")
    val q =
      question("q1", "commodity", conceptLink = ConceptLink("eudr.commodity", 2))
        .copy(foreignAttributes = listOf(stale))
    assertEquals(
      listOf("eudr.commodity@2"),
      ConceptLinkAttributes.bindAttributes(q).map { it.value_ },
    )
  }

  @Test
  fun publishedXml_carriesGroundConcept_andImportRestoresLinksCodesAndForeignAttributes() {
    val acme = EditorForeignAttribute("https://example.org/acme", "acme:hint-style", "bold")
    val source =
      form(
        question(
            "q1",
            "commodity",
            EditorQuestionType.SELECT_ONE,
            "Main crop",
            ConceptLink("eudr.commodity", 1),
            listOf(EditorChoice("cafe", "Café", code = "coffee"), EditorChoice("other", "Other")),
          )
          .copy(foreignAttributes = listOf(acme)),
        question("q2", "notes", label = "Notes"),
      )
    val entry = SurveyEditorForm("f1", source)
    val xml = SurveyEditorSamples.draft().copy(forms = listOf(entry)).publishedFormXml(entry)
    assertTrue("xmlns:ground=\"$GROUND_XFORMS_NAMESPACE\"" in xml, xml)
    assertTrue("ground:concept=\"eudr.commodity@1\"" in xml, xml)
    assertTrue("acme:hint-style=\"bold\"" in xml, xml)

    val imported = assertNotNull(FormImport.fromXml(xml, "visit")).form
    val byName = imported.questions.associateBy { it.name }
    val commodityQuestion = byName.getValue("commodity")
    assertEquals(ConceptLink("eudr.commodity", 1), commodityQuestion.conceptLink)
    assertEquals(listOf("coffee", null), commodityQuestion.choices.map { it.code })
    assertEquals(listOf(acme), commodityQuestion.foreignAttributes)
    assertNull(byName.getValue("notes").conceptLink)
  }

  // --- Survey-level links ---

  @Test
  fun surveyLevelLinks_roundTripByFieldPath() {
    val linked =
      form(
        question("q1", "commodity", conceptLink = ConceptLink("eudr.commodity", 1)),
        question("q2", "notes"),
        question("q3", "area", EditorQuestionType.DECIMAL, conceptLink = ConceptLink(area.id, 2)),
      )
    val links = assertNotNull(FormConceptLinkSync.toFormConceptLinks(linked))
    assertEquals("visit", links.form_id)
    assertEquals(
      mapOf("/data/commodity" to ("eudr.commodity" to 1), "/data/area" to (area.id to 2)),
      links.field_concepts.mapValues { (_, ref) -> ref.concept_id to ref.version },
    )
    val unlinked = linked.copy(questions = linked.questions.map { it.copy(conceptLink = null) })
    assertEquals(linked, FormConceptLinkSync.applyLinks(unlinked, links))
    assertNull(FormConceptLinkSync.toFormConceptLinks(unlinked))
    assertEquals(listOf(links), FormConceptLinkSync.toFormConceptLinks(listOf(linked, unlinked)))
  }

  @Test
  fun surveyLevelLinks_areAuthoritativeOverQuestionLinks() {
    val linked = form(question("q1", "commodity", conceptLink = ConceptLink("eudr.commodity", 1)))
    val other = form(question("q1", "commodity", conceptLink = ConceptLink("eudr.other", 1)))
    val links = FormConceptLinkSync.toFormConceptLinks(linked)
    assertEquals(linked, FormConceptLinkSync.applyLinks(other, links))
    assertEquals(other, FormConceptLinkSync.applyLinks(other, null))
  }

  @Test
  fun reconcileImported_keepsKnownLinks_andTurnsUnknownOnesIntoForeignAttributes() {
    val imported =
      form(
        question("q1", "area", EditorQuestionType.DECIMAL, conceptLink = ConceptLink(area.id, 2)),
        question("q2", "species", conceptLink = ConceptLink("acme.species", 4)),
      )
    val reconciled = FormConceptLinkSync.reconcileImported(imported) { it in concepts }
    assertEquals(ConceptLink(area.id, 2), reconciled.questions[0].conceptLink)
    val species = reconciled.questions[1]
    assertNull(species.conceptLink)
    assertEquals(
      listOf(EditorForeignAttribute(GROUND_XFORMS_NAMESPACE, "ground:concept", "acme.species@4")),
      species.foreignAttributes,
    )
    // The validator reports the unknown concept; it's re-exported unchanged.
    assertEquals(
      listOf("Linked to \"acme.species\", which isn't in this survey's dictionary."),
      ConceptLinkValidator.validate(reconciled) { concepts[it] }.map { it.message },
    )
    assertEquals(
      listOf("acme.species@4"),
      ConceptLinkAttributes.bindAttributes(species).map { it.value_ },
    )
  }

  // --- save_to inheritance ---

  @Test
  fun inheritConcepts_createMode_givesLinkedDatasetPropertiesTheQuestionConcepts() {
    val linked =
      form(
        question("q1", "commodity", conceptLink = ConceptLink("eudr.commodity", 1)),
        question("q2", "notes"),
      )
    val dataset =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "visits",
        displayName = "Visits",
        keyProperty = "id",
        labelProperty = "commodity",
        linkedFormKey = "f1",
        properties =
          listOf(
            EntityProperty("id", "ID"),
            EntityProperty("commodity", "Commodity"),
            EntityProperty("notes", "Notes"),
          ),
      )
    val unrelated = dataset.copy(key = "d2", id = "other", linkedFormKey = "f2")
    val result = SaveToRules.inheritConcepts(linked, "f1", listOf(dataset, unrelated))
    assertEquals(
      listOf(null, ConceptLink("eudr.commodity", 1), null),
      result[0].properties.map { it.conceptLink },
    )
    assertEquals(unrelated, result[1])
  }

  @Test
  fun inheritConcepts_updateMode_followsFieldMappings() {
    val linked =
      form(
          question("q1", "area", EditorQuestionType.DECIMAL, conceptLink = ConceptLink(area.id, 2))
        )
        .copy(
          saveTo =
            EditorSaveTo(
              mode = SaveToMode.UPDATE,
              targetDatasetId = "plots",
              mappings = listOf(EditorFieldMapping("q1", "area_ha")),
            )
        )
    val plots =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "plots",
        displayName = "Plots",
        keyProperty = "id",
        labelProperty = "id",
        properties = listOf(EntityProperty("id", "ID"), EntityProperty("area_ha", "Area")),
      )
    val result = SaveToRules.inheritConcepts(linked, "f1", listOf(plots))
    assertEquals(listOf(null, ConceptLink(area.id, 2)), result[0].properties.map { it.conceptLink })
  }

  // --- Linking and autofill ---

  @Test
  fun link_defaultStateQuestion_fillsTypeNameChoicesHintAndConstraint() {
    val fresh =
      question(
        "q1",
        "text",
        label = "Main crop",
      )
    assertTrue(ConceptLinking.isDefaultState(fresh))
    val linked = ConceptLinking.link(fresh, commodity, takenNames = setOf("text"), language = "es")
    assertEquals(ConceptLink("eudr.commodity", 1), linked.conceptLink)
    assertEquals(EditorQuestionType.SELECT_ONE, linked.type)
    assertEquals("commodity", linked.name)
    assertEquals("Main crop", linked.label)
    assertEquals(
      listOf(Triple("coffee", "Café", "coffee"), Triple("cocoa", "Cacao", "cocoa")),
      linked.choices.map { Triple(it.value, it.label, it.code) },
    )

    val number = question("q2", "amount", EditorQuestionType.DECIMAL, label = "Plot size")
    val linkedNumber = ConceptLinking.link(number, area, setOf("plot_area"), "en")
    assertEquals(EditorQuestionType.DECIMAL, linkedNumber.type)
    assertEquals("plot_area_2", linkedNumber.name)
    assertEquals("Unit: ha", linkedNumber.hint)
    assertEquals("0", linkedNumber.validation?.min)
    assertEquals(ConceptLink(area.id, 2), linkedNumber.conceptLink)
  }

  @Test
  fun link_customizedQuestion_onlyLinksAndMatchesChoiceCodes() {
    val custom =
      question(
          "q1",
          "main_crop",
          EditorQuestionType.SELECT_ONE,
          "Main crop",
          choices =
            listOf(
              EditorChoice("cafe", "Coffee"),
              EditorChoice("cacao", "Cacao"),
              EditorChoice("rice", "Rice"),
            ),
        )
        .copy(required = true)
    assertFalse(ConceptLinking.isDefaultState(custom))
    val linked = ConceptLinking.link(custom, commodity, setOf("main_crop"), "en")
    assertEquals("main_crop", linked.name)
    assertEquals(EditorQuestionType.SELECT_ONE, linked.type)
    assertEquals(listOf("cafe", "cacao", "rice"), linked.choices.map { it.value })
    // "Coffee" matches by English label, "Cacao" by the Spanish one.
    assertEquals(listOf("coffee", "cocoa", null), linked.choices.map { it.code })
    assertTrue(linked.required)
  }

  @Test
  fun isDefaultState_isFalseOnceSettingsChange() {
    val fresh = question("q1", "text")
    assertTrue(ConceptLinking.isDefaultState(fresh))
    assertFalse(ConceptLinking.isDefaultState(fresh.copy(name = "crop")))
    assertFalse(ConceptLinking.isDefaultState(fresh.copy(hint = "Ask the farmer")))
    assertFalse(ConceptLinking.isDefaultState(fresh.copy(required = true)))
    assertFalse(ConceptLinking.isDefaultState(fresh.copy(validation = EditorValidation(min = "1"))))
    val select =
      question(
        "q2",
        "choice",
        EditorQuestionType.SELECT_ONE,
        choices =
          listOf(EditorChoice("option_1", "Option 1"), EditorChoice("option_2", "Option 2")),
      )
    assertTrue(ConceptLinking.isDefaultState(select))
    assertFalse(
      ConceptLinking.isDefaultState(select.copy(choices = select.choices + EditorChoice("x", "X")))
    )
  }

  // --- Warnings ---

  @Test
  fun validator_warnsAboutTypeMismatchUnmappedChoicesAndDuplicates() {
    val checked =
      form(
        question("q1", "crop", EditorQuestionType.TEXT, conceptLink = ConceptLink(commodity.id)),
        question(
          "q2",
          "crop2",
          EditorQuestionType.SELECT_ONE,
          conceptLink = ConceptLink(commodity.id),
          choices = listOf(EditorChoice("cafe", "Café", code = "coffee"), EditorChoice("x", "X")),
        ),
        question("q3", "notes"),
      )
    val warnings = ConceptLinkValidator.validate(checked) { concepts[it] }
    assertTrue(warnings.all { it.isWarning })
    assertEquals(
      listOf(
        "q1" to "\"Commodity\" expects select one answers, but this is a text question.",
        "q1" to "\"Commodity\" is linked to more than one question in this form.",
        "q2" to "\"Commodity\" is linked to more than one question in this form.",
        "q2" to "Match 1 choice to \"Commodity\" values so answers can be added up: X.",
      ),
      warnings.map { it.questionKey to it.message },
    )
  }

  @Test
  fun validator_isQuietForWellLinkedForms() {
    val linked =
      ConceptLinking.link(question("q1", "text", label = "Crop"), commodity, emptySet(), "en")
    assertEquals(emptyList(), ConceptLinkValidator.validate(form(linked)) { concepts[it] })
  }
}
