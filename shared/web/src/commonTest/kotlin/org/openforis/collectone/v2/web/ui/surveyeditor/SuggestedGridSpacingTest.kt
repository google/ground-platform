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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.domain.model.editor.LatLng
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.web.domain.model.editor.SuggestedGridSpacing
import org.openforis.collectone.v2.web.ui.viewmodel.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.viewmodel.surveyEditorViewModel
import org.openforis.collectone.v2.web.ui.viewmodel.ui

class SuggestedGridSpacingTest {

  /** A square survey area about [sideKm] km across, centered near the equator. */
  private fun squareArea(sideKm: Double): SurveyArea {
    val dLat = sideKm / 111.32 / 2
    val dLng = dLat / cos(0.5 * PI / 180)
    return SurveyArea(
      name = "Square",
      parts =
        listOf(
          listOf(
            LatLng(0.5 - dLat, 36.0 - dLng),
            LatLng(0.5 - dLat, 36.0 + dLng),
            LatLng(0.5 + dLat, 36.0 + dLng),
            LatLng(0.5 + dLat, 36.0 - dLng),
          )
        ),
    )
  }

  private fun SurveyEditorViewModel.setSurveyArea(area: SurveyArea) = updateDetails {
    it.copy(surveyArea = area)
  }

  private fun SurveyEditorViewModel.design(key: String) =
    ui.datasets.first { it.key == key }.generator!!

  @Test
  fun forAreaM2_roundsToNiceSpacingForAboutTargetPlots() {
    // 100 km² / 100 plots = 1 km² per plot → 1 km spacing.
    assertEquals(1000.0, SuggestedGridSpacing.forAreaM2(100e6))
    // 1 km² / 100 plots → 100 m.
    assertEquals(100.0, SuggestedGridSpacing.forAreaM2(1e6))
    // √(10 km² / 100) ≈ 316 m, which rounds to 250 m rather than 500 m.
    assertEquals(250.0, SuggestedGridSpacing.forAreaM2(10e6))
    assertNull(SuggestedGridSpacing.forAreaM2(0.0))
    assertNull(SuggestedGridSpacing.forAreaM2(Double.NaN))
    assertEquals(SuggestedGridSpacing.MIN_SPACING_M, SuggestedGridSpacing.forAreaM2(1.0))
  }

  @Test
  fun forAreaM2_growsWithTheArea() {
    val spacings =
      listOf(1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10).map {
        SuggestedGridSpacing.forAreaM2(it)!!
      }
    assertEquals(spacings.sorted(), spacings)
    assertTrue(spacings.first() < spacings.last())
  }

  @Test
  fun newLayer_spacingFollowsTheSurveyArea() {
    val state = surveyEditorViewModel()
    state.setSurveyArea(squareArea(sideKm = 2.0))
    val key = state.addSamplePlotsLayer()
    // 4 km² / 100 plots → 200 m.
    assertEquals(200.0, state.design(key).gridSpacingM)
    assertTrue(state.design(key).gridSpacingAuto)

    state.setSurveyArea(squareArea(sideKm = 20.0))
    assertEquals(2000.0, state.design(key).gridSpacingM)
  }

  @Test
  fun spacingSetByHand_isKeptWhenTheAreaChanges_untilMatchedAgain() {
    val state = surveyEditorViewModel()
    state.setSurveyArea(squareArea(sideKm = 2.0))
    val key = state.addSamplePlotsLayer()

    state.updateSampleDesign(key) { it.copy(gridSpacingM = 75.0) }
    assertFalse(state.design(key).gridSpacingAuto)
    state.setSurveyArea(squareArea(sideKm = 20.0))
    assertEquals(75.0, state.design(key).gridSpacingM)

    state.updateSampleDesign(key) { it.copy(gridSpacingAuto = true) }
    assertEquals(2000.0, state.design(key).gridSpacingM)
  }

  @Test
  fun layerWithPlots_keepsItsSpacingOnUnrelatedEdits() {
    val state = surveyEditorViewModel()
    state.setSurveyArea(squareArea(sideKm = 2.0))
    val key = state.addSamplePlotsLayer()
    runDirect { state.generateSample(key) }
    val generated = state.design(key)
    assertNotEquals(null, generated.lastRun)

    state.updateDetails { it.copy(description = "Edited") }
    assertEquals(generated.gridSpacingM, state.design(key).gridSpacingM)
    assertFalse(state.isDesignStale(state.ui.datasets.first { it.key == key }))
  }
}
