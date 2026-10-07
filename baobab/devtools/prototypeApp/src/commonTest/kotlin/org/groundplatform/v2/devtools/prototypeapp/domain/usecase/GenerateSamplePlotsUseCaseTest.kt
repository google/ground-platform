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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runDirect
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplingAreaResult
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SubPlotMode
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GenerateSamplePlotsUseCase.Result

class GenerateSamplePlotsUseCaseTest {
  private val now = "2026-01-02T03:04:05Z"
  private val useCase = GenerateSamplePlotsUseCase(yieldBetweenChunks = {}, now = { now })
  private val draft = SurveyEditorSamples.draft()

  /** An empty sample plots layer with a coarse grid over the sample survey area. */
  private val layer =
    EntityDataset(
      key = "d9",
      kind = DatasetKind.MAP_LAYER,
      id = "sample_plots",
      displayName = "Sample plots",
      geometryKind = GeometryKind.POLYGON,
      keyProperty = SamplePlotProperties.PLOT_ID,
      labelProperty = SamplePlotProperties.PLOT_ID,
      properties = SamplePlotProperties.schema,
      generator =
        SampleDesignConfig(gridSpacingM = 1000.0, subPlot = SubPlotMode.GRID, subPlotGridN = 2),
    )

  @Test
  fun generate_replacesRowsAndRecordsTheRun() {
    val progress = mutableListOf<Pair<Int, Int>>()
    val result =
      assertIs<Result.Generated>(
        runDirect {
          useCase(
            layer,
            draft.details.surveyArea,
            draft.datasets,
            onProgress = { n, e -> progress += n to e },
          )
        }
      )
    assertTrue(result.plotCount > 5, "plots: ${result.plotCount}")
    assertEquals(result.plotCount, result.dataset.rows.size)
    assertEquals(GeometryKind.POLYGON, result.dataset.geometryKind)
    val lastRun = assertNotNull(result.dataset.generator?.lastRun)
    assertEquals(now, lastRun.generatedAt)
    assertEquals(result.plotCount, lastRun.featureCount)
    assertEquals(
      useCase.inputHash(layer, draft.details.surveyArea, draft.datasets),
      lastRun.inputHash,
    )
    assertEquals(0, progress.first().first)
    assertEquals(result.plotCount, progress.last().first)
    assertNotNull(useCase.estimate(layer, draft.details.surveyArea, draft.datasets))
  }

  @Test
  fun generate_failsWithoutAnArea_orADesign() {
    val noArea =
      assertIs<Result.Failed>(
        runDirect { useCase(layer, surveyArea = null, datasets = emptyList()) }
      )
    assertTrue("survey area" in noArea.message)
    assertIs<SamplingAreaResult.Unavailable>(
      useCase.samplingArea(layer.generator!!, null, emptyList())
    )
    assertNull(useCase.inputHash(layer, null, emptyList()))
    assertNull(useCase.estimate(layer, null, emptyList()))

    val noDesign =
      assertIs<Result.Failed>(
        runDirect {
          useCase(layer.copy(generator = null), draft.details.surveyArea, draft.datasets)
        }
      )
    assertEquals(GenerateSamplePlotsUseCase.NO_DESIGN, noDesign.message)
  }

  @Test
  fun generate_stopsWhenCancelled() {
    val result = runDirect {
      useCase(layer, draft.details.surveyArea, draft.datasets, isCancelled = { true })
    }
    assertEquals(Result.Cancelled, result)
  }
}
