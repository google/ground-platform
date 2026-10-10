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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.GROUND_XFORMS_NAMESPACE
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForeignAttribute
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormImport
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.publishedFormXml

/** The Survey editor gives its Form editors the survey's dictionary. */
class SurveyEditorConceptLinksTest {
  private val store = seededStore()
  private val libraries = LibraryRepositoryImpl(store)
  private val viewModel = seededSurveyEditorViewModel(store, testScope(), libraries)

  @Test
  fun formEditor_linksAgainstTheSurveyOrganizationsResolvedLibrary() {
    val editor = viewModel.formEditor(viewModel.ui.forms.first().key)
    val library = editor.ui.library
    assertEquals(
      PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE,
      library.library.organizationId,
    )
    assertEquals("Kenya Forest Service", library.organizationName)
    // The signed-in user manages the Kenya Forest Service.
    assertTrue(library.canAddToDictionary)
    assertNotNull(library.concept("core.area_ha"))
    assertEquals("Core", library.sourceLabel(assertNotNull(library.concept("core.area_ha"))))
  }

  @Test
  fun addLabelToDictionary_savesADraftConceptToTheOrganizationLibrary() {
    val editor = viewModel.formEditor(viewModel.ui.forms.first().key)
    editor.addQuestion(EditorQuestionType.TEXT, null)
    val key = assertNotNull(editor.ui.selectedKey)
    editor.updateQuestion(key) { it.copy(label = "Canopy closure notes") }
    assertNull(editor.addLabelToDictionary(key))

    val id = "org.${PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE}.canopy_closure_notes"
    val saved = runNow {
      libraries.getLibrary(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
    }
      .concept(id)
    assertEquals(LibraryStatus.DRAFT, assertNotNull(saved).status)
    val question = editor.ui.form.questions.first { it.key == key }
    assertEquals(ConceptLink(id, 1), question.conceptLink)
    assertTrue(editor.ui.warningsFor(key).isEmpty())
  }

  @Test
  fun importForm_keepsKnownLinks_preservesUnknownOnes_andSuggestsMatches() {
    val source =
      EditorForm(
        formId = "plot_check",
        title = "Plot check",
        questions =
          listOf(
            EditorQuestion(
              "q1",
              "area",
              EditorQuestionType.DECIMAL,
              "Size",
              conceptLink = ConceptLink("core.area_ha", 1),
            ),
            EditorQuestion(
              "q2",
              "acme_field",
              EditorQuestionType.TEXT,
              "Xylo qrv",
              conceptLink = ConceptLink("acme.species", 3),
            ),
            EditorQuestion("q3", "geoid", EditorQuestionType.TEXT, "GeoID"),
          ),
      )
    val entry = SurveyEditorForm("f1", source)
    val xml = SurveyEditorSamples.draft().copy(forms = listOf(entry)).publishedFormXml(entry)
    val imported = assertNotNull(FormImport.fromXml(xml, "plot_check"))

    val formKey = viewModel.importForm(imported)
    val form = assertNotNull(viewModel.ui.form(formKey)).form
    val byName = form.questions.associateBy { it.name }
    assertEquals(ConceptLink("core.area_ha", 1), byName.getValue("area").conceptLink)
    val species = byName.getValue("acme_field")
    assertNull(species.conceptLink)
    assertEquals(
      listOf(EditorForeignAttribute(GROUND_XFORMS_NAMESPACE, "ground:concept", "acme.species@3")),
      species.foreignAttributes,
    )
    val editor = viewModel.formEditor(formKey)
    assertTrue(editor.ui.warningsFor(species.key).any { "acme.species" in it.message })
    // Warnings never count as issues.
    assertTrue(editor.ui.issuesFor(species.key).isEmpty())

    val match = editor.ui.importMatches.single()
    assertEquals(byName.getValue("geoid").key, match.questionKey)
    assertEquals("core.geoid", match.concept.id)
    assertTrue(match.isChecked)
    editor.linkImportMatches()
    assertEquals(
      ConceptLink("core.geoid", 1),
      viewModel.ui.form(formKey)?.form?.questions?.first { it.name == "geoid" }?.conceptLink,
    )

    // The linked dataset's properties inherit the concepts.
    val dataset = viewModel.ui.datasets.first { it.linkedFormKey == formKey }
    val properties = dataset.properties.associateBy { it.name }
    assertEquals(ConceptLink("core.area_ha", 1), properties["area"]?.conceptLink)
    assertEquals(ConceptLink("core.geoid", 1), properties["geoid"]?.conceptLink)
    assertFalse(properties["acme_field"]?.conceptLink != null)
  }
}
