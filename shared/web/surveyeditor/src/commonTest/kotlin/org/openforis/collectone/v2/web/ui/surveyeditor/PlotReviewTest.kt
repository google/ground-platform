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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.publishedFormXml
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.map.GeometryType

/** Plot review from the sample design: the editor actions and what the review Forms do. */
class PlotReviewTest {
  @Test
  fun plotReview_addsUpdatesAndRemovesTheReviewForms() {
    val editor = surveyEditorViewModel()
    val key = editor.addSamplePlotsLayer()
    val formCount = editor.ui.forms.size
    val issueCount = editor.ui.issueCount

    editor.updatePlotReview(key, PlotReviewOptions())
    val deskOnly = PlotReviewWorkflow.setup(editor.ui.draft, key)
    assertTrue(deskOnly.isEnabled)
    assertNull(deskOnly.fieldFormKey)
    assertEquals(formCount + 1, editor.ui.forms.size)
    assertTrue(editor.ui.hasUnpublishedChanges)

    editor.updatePlotReview(key, deskOnly.options.copy(fieldValidation = true))
    val withField = PlotReviewWorkflow.setup(editor.ui.draft, key)
    val fieldKey = assertNotNull(withField.fieldFormKey)
    assertEquals(deskOnly.deskFormKey, withField.deskFormKey)
    assertEquals(formCount + 2, editor.ui.forms.size)
    assertEquals(issueCount, editor.ui.issueCount)
    val fieldEditor = editor.formEditor(fieldKey)
    assertEquals(FormAvailability.MOBILE, fieldEditor.uiState.value.form.availability)

    editor.removePlotReview(key)
    assertEquals(formCount, editor.ui.forms.size)
    assertFalse(PlotReviewWorkflow.setup(editor.ui.draft, key).isEnabled)
    // The removed Form's editor goes with it.
    assertFalse(editor.formEditor(fieldKey) === fieldEditor)
  }

  private val plots =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "sample_plots",
      displayName = "Sample plots",
      geometryType = GeometryType.POLYGON,
      keyProperty = SamplePlotProperties.PLOT_ID,
      labelProperty = SamplePlotProperties.PLOT_ID,
      properties = SamplePlotProperties.schema,
      rows =
        (1..3).map { n ->
          EntityRow(
            key = "r$n",
            values =
              mapOf(
                SamplePlotProperties.PLOT_ID to "P$n",
                SamplePlotProperties.SAMPLE_ORDER to "$n",
              ),
          )
        },
      generator = SampleDesignConfig(),
    )

  /** Published review Forms of [plots] with field validation and 1 spot check. */
  private val reviewed =
    PlotReviewWorkflow.apply(
      SurveyEditorDraft.blank("s").copy(datasets = listOf(plots)),
      plots.key,
      PlotReviewOptions(fieldValidation = true, autoFlagBelowConfidence = 50, spotCheckCount = 1),
    )

  private fun publishedXml(formKey: String?) =
    reviewed.publishedFormXml(reviewed.forms.single { it.key == formKey })

  /** The plot properties a submission of [xml] with [answers] writes. */
  private fun submit(xml: String, answers: (FormWizardController) -> Unit): Map<String, String> {
    val controller = FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xml))
    answers(controller)
    val result = assertIs<FinalizationResult.Success>(controller.finalizeForm())
    val state = result.entityStates.single()
    assertTrue(state.shouldUpdate)
    return state.properties.mapValues { (_, value: TypedValue) -> value.string_value.orEmpty() }
  }

  private fun deskCheck(plot: String, confidence: Int, needsVisit: Boolean = false) =
    submit(publishedXml(PlotReviewWorkflow.setup(reviewed, plots.key).deskFormKey)) {
      it.updateString("/data/target_entity", plot)
      it.updateString("/data/${PlotReviewWorkflow.DESK_LAND_COVER}", "cropland")
      it.updateInt("/data/${PlotReviewWorkflow.DESK_CONFIDENCE}", confidence)
      it.updateString(
        "/data/${PlotReviewWorkflow.NEEDS_FIELD_CHECK}",
        if (needsVisit) PlotReviewWorkflow.YES else PlotReviewWorkflow.NO,
      )
      if (needsVisit) it.updateString("/data/${PlotReviewWorkflow.FLAG_REASON}", "Dense canopy")
    }

  @Test
  fun deskCheck_marksPlotsDoneOrForTheField() {
    val done = deskCheck("P2", confidence = 90)
    assertEquals("Desk verified", done["status"])
    assertEquals("✓", done["marker-symbol"])
    assertEquals("cropland", done[PlotReviewWorkflow.DESK_LAND_COVER])

    val needsField = PlotReviewWorkflow.NEEDS_FIELD_VALIDATION
    // Flagged by the interpreter: the crew sees why.
    val flagged = deskCheck("P2", confidence = 90, needsVisit = true)
    assertEquals(needsField.label, flagged["status"])
    assertEquals("!", flagged["marker-symbol"])
    assertEquals("Dense canopy", flagged[PlotReviewWorkflow.FLAG_REASON])
    // Below the confidence threshold.
    assertEquals(needsField.label, deskCheck("P2", confidence = 30)["status"])
    // A random spot check (the plot with sample_order 1), even when the interpreter is sure.
    assertEquals(needsField.label, deskCheck("P1", confidence = 95)["status"])
  }

  @Test
  fun fieldVerification_marksPlotsVerifiedOrNotReachable() {
    val fieldXml = publishedXml(PlotReviewWorkflow.setup(reviewed, plots.key).fieldFormKey)
    val verified =
      submit(fieldXml) {
        it.updateString("/data/target_entity", "P1")
        it.updateString("/data/${PlotReviewWorkflow.PLOT_REACHED}", PlotReviewWorkflow.YES)
        it.updateString("/data/${PlotReviewWorkflow.FIELD_LAND_COVER}", "forest_land")
      }
    assertEquals("Field verified", verified["status"])
    assertEquals("forest_land", verified[PlotReviewWorkflow.FIELD_LAND_COVER])

    val notReached =
      submit(fieldXml) {
        it.updateString("/data/target_entity", "P3")
        it.updateString("/data/${PlotReviewWorkflow.PLOT_REACHED}", PlotReviewWorkflow.NO)
      }
    assertEquals("Not reachable", notReached["status"])
    assertEquals("✕", notReached["marker-symbol"])
  }
}
