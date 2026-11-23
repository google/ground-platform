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
import kotlin.test.assertTrue
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/** Status marker rules survive publishing a Form and importing it back. */
class StatusRulesImportTest {
  private val plots =
    EditorDataset(
      id = "plots",
      displayName = "Plots",
      isMapLayer = true,
      keyProperty = "plot_id",
      labelProperty = "plot_id",
      properties =
        listOf(
          EditorDatasetProperty("plot_id", "Plot ID", EditorPropertyKind.TEXT),
          EditorDatasetProperty("canopy_pct", "Canopy", EditorPropertyKind.INTEGER),
          EditorDatasetProperty("notes", "Notes", EditorPropertyKind.TEXT),
        ),
      rows = listOf(EditorDatasetRow("P-1", "P-1", mapOf("plot_id" to "P-1"))),
    )

  private fun rule(
    subject: StatusConditionSubject,
    operator: RelevanceOperator = RelevanceOperator.EQUALS,
    value: String = "",
    questionKey: String? = null,
    property: String? = null,
    minSubmissions: Int = 1,
    badge: EditorStatusBadge = EditorStatusBadge.SURVEYED,
  ) = EditorStatusRule(subject, minSubmissions, questionKey, property, operator, value, badge)

  private val form =
    EditorForm(
      formId = "form_check",
      title = "Check",
      questions =
        listOf(
          EditorQuestion("q1", "canopy", EditorQuestionType.NUMBER, "Canopy"),
          EditorQuestion(
            "q2",
            "condition",
            EditorQuestionType.SELECT_ONE,
            "Condition",
            choices = listOf(EditorChoice("good", "Good"), EditorChoice("poor", "Poor")),
          ),
          EditorQuestion(
            "q3",
            "issues",
            EditorQuestionType.SELECT_MULTIPLE,
            "Issues",
            choices = listOf(EditorChoice("pests", "Pests"), EditorChoice("fire", "Fire")),
          ),
          EditorQuestion("q4", "remarks", EditorQuestionType.TEXT, "Remarks"),
        ),
      saveTo =
        EditorSaveTo(
          mode = SaveToMode.UPDATE,
          targetDatasetId = plots.id,
          mappings = listOf(EditorFieldMapping("q1", "canopy_pct")),
          status =
            EditorStatusConfig(
              enabled = true,
              rules =
                listOf(
                  rule(
                    StatusConditionSubject.QUESTION,
                    RelevanceOperator.LESS_THAN,
                    "15",
                    questionKey = "q1",
                    badge = EditorStatusBadge.NEEDS_REVIEW,
                  ),
                  rule(
                    StatusConditionSubject.QUESTION,
                    RelevanceOperator.GREATER_THAN,
                    "90",
                    questionKey = "q1",
                  ),
                  rule(StatusConditionSubject.QUESTION, value = "poor", questionKey = "q2"),
                  rule(
                    StatusConditionSubject.QUESTION,
                    RelevanceOperator.NOT_EQUALS,
                    "good",
                    questionKey = "q2",
                    badge = EditorStatusBadge.FLAGGED,
                  ),
                  rule(
                    StatusConditionSubject.QUESTION,
                    RelevanceOperator.INCLUDES,
                    "fire",
                    questionKey = "q3",
                  ),
                  rule(
                    StatusConditionSubject.QUESTION,
                    RelevanceOperator.IS_ANSWERED,
                    questionKey = "q4",
                    badge = EditorStatusBadge.IN_PROGRESS,
                  ),
                  // A value with an apostrophe is written as a double-quoted literal.
                  rule(
                    StatusConditionSubject.ENTITY_PROPERTY,
                    value = "it's flagged",
                    property = "notes",
                  ),
                  rule(
                    StatusConditionSubject.ENTITY_PROPERTY,
                    RelevanceOperator.IS_ANSWERED,
                    property = "canopy_pct",
                  ),
                  rule(StatusConditionSubject.SUBMISSIONS, minSubmissions = 3),
                  rule(StatusConditionSubject.SUBMISSIONS),
                ),
              defaultBadge = EditorStatusBadge(StatusMarkerSymbol.OPEN, "#80868B", "Not visited"),
            ),
        ),
    )

  /** [EditorForm.saveTo]'s status with each rule's question key replaced by its name. */
  private fun EditorForm.statusByName(): EditorStatusConfig =
    saveTo.status.copy(
      rules = saveTo.status.rules.map { it.copy(questionKey = find(it.questionKey)?.name) }
    )

  private fun assertNoResetNote(imported: ImportedForm) =
    assertTrue(imported.notes.none { it.contains("Status marker") }, imported.notes.toString())

  @Test
  fun everyRuleKind_survivesThePublishedFormDefinition() {
    val definition =
      EditorXFormsGenerator.compile(form, plots, inlineRows = true, datasets = listOf(plots))
    val imported = assertNotNull(FormImport.fromFormDefinition(definition, form.formId))
    assertNoResetNote(imported)
    assertEquals(form.statusByName(), imported.form.statusByName())
  }

  @Test
  fun everyRuleKind_survivesTheXformsXml() {
    val xml = EditorXFormsGenerator.toXml(form, plots, inlineRows = true)
    val imported = assertNotNull(FormImport.fromXml(xml, form.formId))
    assertNoResetNote(imported)
    assertEquals(form.statusByName(), imported.form.statusByName())
  }

  @Test
  fun disabledRules_importDisabled() {
    val plain = form.copy(saveTo = form.saveTo.copy(status = EditorStatusConfig()))
    val definition = EditorXFormsGenerator.compile(plain, plots, inlineRows = true)
    val imported = assertNotNull(FormImport.fromFormDefinition(definition, form.formId))
    assertEquals(EditorStatusConfig(), imported.form.saveTo.status)
  }

  @Test
  fun unrecognizedExpressions_fallBackToTheDefaultRules() {
    assertEquals(
      null,
      StatusRulesImport.parse(
        label = "if(count(/data/x) > 2, 'Done', 'Pending')",
        symbol = "if(count(/data/x) > 2, '✓', '○')",
        color = "if(count(/data/x) > 2, '#3C8D40', '#80868B')",
        questionKey = { null },
      ),
    )
    // Symbol and color chains that don't line up with the labels aren't guessed at.
    assertEquals(
      null,
      StatusRulesImport.parse(
        label = "if(true(), 'Done', 'Pending')",
        symbol = "'✓'",
        color = "'#3C8D40'",
        questionKey = { null },
      ),
    )
  }

  @Test
  fun plotReviewSetup_survivesPublishingAndDerivingTheDraftAgain() {
    val layer =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        geometryType = GeometryType.POINT,
        keyProperty = SamplePlotProperties.PLOT_ID,
        labelProperty = SamplePlotProperties.PLOT_ID,
        properties = SamplePlotProperties.schema,
        rows =
          (1..20).map { n ->
            EntityRow(
              key = "r$n",
              values =
                mapOf(
                  SamplePlotProperties.PLOT_ID to "P$n",
                  SamplePlotProperties.SAMPLE_ORDER to "$n",
                ),
              geometry = listOf(LatLng(-1.0 + n / 1000.0, 36.0)),
            )
          },
        generator = SampleDesignConfig(plotShape = PlotShapeOption.POINT),
      )
    val options =
      PlotReviewOptions(
        fieldValidation = true,
        autoFlagBelowConfidence = 40,
        spotCheckCount = 5,
        showDeskAnswersInField = false,
      )
    var formIds = 0
    val draft =
      PlotReviewWorkflow.apply(
        SurveyEditorDraft.blank("s").copy(datasets = listOf(layer)),
        layer.key,
        options,
      ) {
        "form_${++formIds}"
      }

    // Publish, then derive the editor draft from the published Forms, as a sync does.
    val runtime =
      SurveyEditorProjection.project(
        draft = draft,
        previous = SurveyEditorDraft.blank("s"),
        surveyId = "s",
        forms = emptyList(),
        mapLayers = emptyList(),
        entities = emptyList(),
      )
    val published = draft.forms.associate { it.form.formId to draft.publishedForm(it) }
    val derived =
      SurveyEditorDerivation.derive(
        surveyId = "s",
        survey = null,
        forms = runtime.forms,
        formDefinition = { published[it.id] },
        mapLayers = runtime.mapLayers,
        entities = runtime.entities,
      )
    val derivedLayer = derived.datasets.single { it.id == layer.id }
    val setup = PlotReviewWorkflow.setup(derived, derivedLayer.key)
    assertTrue(setup.isEnabled)
    assertEquals(options, setup.options)
  }
}
