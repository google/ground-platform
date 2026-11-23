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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.GlobalLibrarySeedData
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.map.GeometryType

/** Copying templates into surveys and saving survey Forms as templates. */
class SurveyFormTemplatesTest {
  private val kfs = "00000013-31cc-5c95-90ea-379f624a5c72"

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
              EditorQuestionType.NUMBER,
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

  @Test
  fun addTemplate_ofAGeometryForm_addsAMapLayerWhoseFeaturesTakeItsGeometry() {
    val plots =
      template.copy(
        form =
          template.form.copy(
            questions =
              listOf(
                EditorQuestion("q1", "consent", EditorQuestionType.SELECT_ONE, "Consent"),
                EditorQuestion("q2", "plot", EditorQuestionType.POLYGON, "Plot"),
                EditorQuestion("q3", "producer_name", EditorQuestionType.TEXT, "Producer"),
              )
          )
      )
    val (draft, key) = SurveyFormTemplates.addTemplate(draft(), plots) { "form_new" }
    val layer = draft.datasets.single()

    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(GeometryType.POLYGON, layer.geometryType)
    assertEquals(key, layer.linkedFormKey)
    // The plot polygon is each feature's geometry, not an (always empty) property.
    assertEquals(listOf("id", "consent", "producer_name"), layer.properties.map { it.name })
    assertEquals("id", layer.keyProperty)
    // Features are labeled by the producer's name rather than by the consent answer.
    assertEquals("producer_name", layer.labelProperty)
  }

  @Test
  fun addTemplate_everyGlobalTemplate_getsAMatchingDatasetWithUniqueIds() {
    val templates = GlobalLibrarySeedData.library().formTemplates
    var draft = draft()
    templates.forEachIndexed { index, template ->
      val (next, key) = SurveyFormTemplates.addTemplate(draft, template) { "form_$index" }
      draft = next
      val form = draft.forms.single { it.key == key }.form
      val dataset = draft.datasets.single { it.linkedFormKey == key }
      val geometry = form.primaryGeometryQuestion
      assertEquals(
        if (geometry != null) DatasetKind.MAP_LAYER else DatasetKind.DATA_TABLE,
        dataset.kind,
        template.id,
      )
      assertEquals(FormDatasetLinks.geometryTypeFor(form), dataset.geometryType, template.id)
      // Every answer but the feature geometry has a property of the same name.
      val expected =
        form.questions
          .filter { it.type != EditorQuestionType.NOTE && it.key != geometry?.key }
          .map { it.name }
      assertEquals(expected, dataset.properties.map { it.name } - "id", template.id)
      assertTrue(dataset.property(dataset.keyProperty) != null, template.id)
      val label = assertNotNull(dataset.property(dataset.labelProperty), template.id)
      val labelType = form.questions.firstOrNull { it.name == label.name }?.type
      assertTrue(
        labelType == null ||
          labelType == EditorQuestionType.TEXT ||
          labelType == EditorQuestionType.SELECT_ONE,
        "${template.id} is labeled by a $labelType answer",
      )
      assertEquals(EditorSaveTo(), form.saveTo, template.id)
      assertEquals(template.id, form.templateOrigin?.templateId, template.id)
    }
    assertEquals(templates.size, draft.datasets.map { it.id }.toSet().size)
    assertEquals(templates.size, draft.datasets.map { it.displayName }.toSet().size)
    assertEquals(templates.size, draft.forms.map { it.form.formId }.toSet().size)
  }

  @Test
  fun edited_makesTheFormPortable_andKeepsTheTemplatesIdentity() {
    val stored =
      template.copy(
        id = "plot_check",
        description = LocalizedText.of("en" to "Old", "fr" to "Ancien"),
        version = 3,
      )
    val edited =
      stored.form.copy(
        formId = "survey_form_id",
        title = "Should be replaced",
        saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = "plots"),
        templateOrigin = TemplateOrigin("other_template", 1),
        questions =
          stored.form.questions +
            EditorQuestion(
              "q3",
              "shade_trees",
              EditorQuestionType.NUMBER,
              "Shade trees",
              conceptLink = ConceptLink("org.$kfs.shade_tree_count"),
            ),
      )

    val saved = SurveyFormTemplates.edited(stored, edited, " Plot audit ", " New ")

    assertEquals("plot_check", saved.id)
    assertEquals(LibraryStatus.STABLE, saved.status)
    assertEquals(3, saved.version)
    // English text is replaced; other translations are kept.
    assertEquals(LocalizedText.of("en" to "Plot audit", "es" to "Control de parcela"), saved.title)
    assertEquals(LocalizedText.of("en" to "New", "fr" to "Ancien"), saved.description)
    assertEquals("plot_check", saved.form.formId)
    assertEquals("Plot audit", saved.form.title)
    assertEquals(EditorSaveTo(), saved.form.saveTo)
    assertNull(saved.form.templateOrigin)
    assertNull(saved.form.questions[1].choiceDatasetId)
    assertEquals(ChoiceSource.MANUAL, saved.form.questions[1].choiceSource)
    // A global template only keeps links to global concepts.
    assertEquals(setOf("eudr.commodity"), saved.conceptIds)

    val organizationTemplate =
      SurveyFormTemplates.edited(stored.copy(organizationId = kfs), edited, "Plot audit", "")
    assertEquals(
      setOf("eudr.commodity", "org.$kfs.shade_tree_count"),
      organizationTemplate.conceptIds,
    )
    // A blank description removes the English one.
    assertEquals(LocalizedText.of("fr" to "Ancien"), organizationTemplate.description)
  }

  @Test
  fun templateFrom_dropsTheTemplateTheFormWasCopiedFrom() {
    val copied = template.form.copy(templateOrigin = TemplateOrigin("plot_check", 1))
    assertNull(
      SurveyFormTemplates.templateFrom(copied, "org.$kfs.mine", kfs, "Mine", "").form.templateOrigin
    )
  }

  @Test
  fun computeUpdateDiff_isNullForFreshFormsAcrossAllGlobalTemplates() {
    val templates = GlobalLibrarySeedData.library().formTemplates
    templates.forEachIndexed { index, sourceTemplate ->
      val (seededDraft, key) =
        SurveyFormTemplates.addTemplate(draft(), sourceTemplate) { "form_$index" }
      val form = seededDraft.forms.single { it.key == key }.form
      val firstDiff = templates.firstNotNullOfOrNull { candidate ->
        SurveyFormTemplates.computeUpdateDiff(form, candidate)
      }
      assertNull(
        firstDiff,
        "Fresh form from '${sourceTemplate.id}' falsely matched '${firstDiff?.templateId}'",
      )
    }
  }

  @Test
  fun computeUpdateDiff_requiresHigherTemplateVersionWhenOriginIsRecorded() {
    val (seededDraft, key) = SurveyFormTemplates.addTemplate(draft(), template) { "form_1" }
    val form = seededDraft.forms.single { it.key == key }.form

    // User deletes a question on their own form while template is still at v1 -> no update diff.
    val userEditedForm = form.copy(questions = form.questions.drop(1)).withModificationFrom(form)
    assertNull(SurveyFormTemplates.computeUpdateDiff(userEditedForm, template))

    // Template is updated to v2 with a new question and without "commodity".
    val v2Template =
      template.copy(
        version = 2,
        form =
          template.form.copy(
            questions =
              template.form.questions.drop(1) +
                EditorQuestion(
                  "q3",
                  "area_ha",
                  EditorQuestionType.NUMBER,
                  "Area (ha)",
                  conceptLink = ConceptLink("core.area_ha"),
                )
          ),
      )

    val diff = assertNotNull(SurveyFormTemplates.computeUpdateDiff(form, v2Template))
    assertEquals("plot_check", diff.templateId)
    assertEquals(2, diff.templateVersion)
    assertEquals(listOf("area_ha"), diff.addedQuestions.map { it.name })
    assertEquals(listOf("commodity"), diff.removedQuestions.map { it.name })
  }
}
