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
import kotlin.test.assertNull
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization

/** Copying templates into surveys and saving survey Forms as templates. */
class SurveyFormTemplatesTest {
  private val kfs = "org-kenya-forest-service"

  private val template =
    FormTemplate(
      id = "plot_check",
      organizationId = Organization.ALL_USERS_ID,
      title = LocalizedText.of("en" to "Plot check", "es" to "Control de parcela"),
      form =
        EditorForm(
          formId = "plot_check",
          title = "Plot check",
          questions =
            listOf(
              EditorQuestion(
                "q1",
                "commodity",
                EditorQuestionType.SELECT_ONE,
                "Commodity",
                choices = listOf(EditorChoice("coffee", "Coffee", code = "coffee")),
                conceptLink = ConceptLink("eudr.commodity"),
              ),
              EditorQuestion(
                "q2",
                "species",
                EditorQuestionType.SELECT_ONE,
                "Species",
                choices = listOf(EditorChoice("grevillea", "Grevillea")),
                choiceDatasetId = "tree_species",
                choiceSource = ChoiceSource.DATA_TABLE,
              ),
            ),
        ),
      status = LibraryStatus.STABLE,
    )

  private fun draft() = SurveyEditorDraft.blank("survey-x", title = "X")

  @Test
  fun addTemplate_copiesTheFormWithANewIdAndALinkedDataset() {
    val (draft, key) = SurveyFormTemplates.addTemplate(draft(), template) { "form_new" }
    val entry = draft.forms.single()
    assertEquals(key, entry.key)
    assertEquals("form_new", entry.form.formId)
    assertEquals("Plot check", entry.form.title)
    assertEquals(EditorSaveTo(), entry.form.saveTo)
    // Choices from a dataset the survey doesn't have fall back to the listed ones.
    val species = entry.form.questions[1]
    assertNull(species.choiceDatasetId)
    assertEquals(ChoiceSource.MANUAL, species.choiceSource)
    assertEquals(listOf("grevillea"), species.choices.map { it.value })

    val dataset = draft.datasets.single()
    assertEquals(key, dataset.linkedFormKey)
    assertEquals(DatasetKind.DATA_TABLE, dataset.kind)
    assertEquals("plot_check", dataset.id)
    assertEquals(
      ConceptLink("eudr.commodity"),
      dataset.properties.first { it.name == "commodity" }.conceptLink,
    )
    assertEquals(draft().nextKeyId + 2, draft.nextKeyId)
  }

  @Test
  fun addTemplate_twice_makesTitlesAndDatasetIdsUnique_inTheSurveyLanguage() {
    val spanish = draft().let { it.copy(details = it.details.copy(defaultLanguage = "es")) }
    val (once, _) = SurveyFormTemplates.addTemplate(spanish, template) { "form_1" }
    val (twice, _) = SurveyFormTemplates.addTemplate(once, template) { "form_2" }
    assertEquals(
      listOf("Control de parcela", "Control de parcela 2"),
      twice.forms.map { it.form.title },
    )
    assertEquals(2, twice.datasets.map { it.id }.toSet().size)
    assertEquals(2, twice.forms.map { it.key }.toSet().size)
  }

  @Test
  fun addTemplate_copiesAreIsolatedFromLaterTemplateEdits() {
    val (draft, _) = SurveyFormTemplates.addTemplate(draft(), template) { "form_1" }
    val edited =
      template.copy(
        form =
          template.form.copy(questions = template.form.questions.map { it.copy(label = "Changed") })
      )
    assertEquals(
      listOf("Commodity", "Species"),
      draft.forms.single().form.questions.map { it.label },
    )
    assertEquals(listOf("Changed", "Changed"), edited.form.questions.map { it.label })
  }

  @Test
  fun templateFrom_keepsLinksAndCodes_andResetsSurveySpecificSettings() {
    val form =
      template.form.copy(
        questions =
          template.form.questions +
            EditorQuestion(
              "q3",
              "shade_trees",
              EditorQuestionType.INTEGER,
              "Shade trees",
              conceptLink = ConceptLink("org.$kfs.shade_tree_count"),
            ),
        saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = "plots"),
      )
    val saved =
      SurveyFormTemplates.templateFrom(form, "org.$kfs.plot_check", kfs, " Plot check ", "Yearly")
    assertEquals("plot_check", saved.form.formId)
    assertEquals("Plot check", saved.form.title)
    assertEquals(LocalizedText.en("Yearly"), saved.description)
    assertEquals(EditorSaveTo(), saved.form.saveTo)
    assertEquals(LibraryStatus.DRAFT, saved.status)
    assertEquals(setOf("eudr.commodity", "org.$kfs.shade_tree_count"), saved.conceptIds)
    assertEquals("coffee", saved.form.questions[0].choices.single().code)
    assertNull(saved.form.questions[1].choiceDatasetId)
    assertEquals(1, SurveyFormTemplates.organizationLinkCount(form))

    // Global templates may only link to global concepts.
    val global =
      SurveyFormTemplates.templateFrom(
        form,
        "plot_check",
        Organization.ALL_USERS_ID,
        "Plot check",
        "",
      )
    assertEquals(setOf("eudr.commodity"), global.conceptIds)
    assertEquals(LocalizedText(), global.description)
  }
}
