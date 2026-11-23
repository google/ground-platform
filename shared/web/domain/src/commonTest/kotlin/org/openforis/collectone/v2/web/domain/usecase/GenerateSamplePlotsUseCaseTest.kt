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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityDatasetValidator
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.app.testing.FakeClock
import org.openforis.collectone.v2.app.testing.testScope
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SamplingMessage
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase.Result

class GenerateSamplePlotsUseCaseTest {
  private val now = "2026-01-02T03:04:05Z"
  private val useCase =
    GenerateSamplePlotsUseCase(yieldBetweenChunks = {}, clock = FakeClock(Instant.parse(now)))
  private val draft = SurveyEditorSamples.draft()

  /** An empty sample plots layer with a coarse grid over the sample survey area. */
  private val layer =
    EntityDataset(
      key = "d9",
      kind = DatasetKind.MAP_LAYER,
      id = "sample_plots",
      displayName = "Sample plots",
      geometryType = GeometryType.POLYGON,
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
    assertEquals(GeometryType.POLYGON, result.dataset.geometryType)
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
    assertEquals(SamplePlotProperties.PLOT_ID, result.dataset.keyProperty)
    assertEquals(SamplePlotProperties.schema, result.dataset.properties)
    assertEquals(emptyList(), EntityDatasetValidator.validate(result.dataset, emptyList()))

    // Regenerating a layer whose schema had been polluted with derived "id"/"label" columns
    // restores SamplePlotProperties.schema and validates cleanly.
    val pollutedLayer =
      layer.copy(
        keyProperty = "id",
        labelProperty = "label",
        properties =
          listOf(
            EntityProperty("id", "ID", PropertyType.TEXT, required = true),
            EntityProperty("label", "Label", PropertyType.TEXT),
          ) + SamplePlotProperties.schema,
      )
    val healed =
      assertIs<Result.Generated>(
        runDirect { useCase(pollutedLayer, draft.details.surveyArea, draft.datasets) }
      )
    assertEquals(SamplePlotProperties.PLOT_ID, healed.dataset.keyProperty)
    assertEquals(SamplePlotProperties.PLOT_ID, healed.dataset.labelProperty)
    assertEquals(SamplePlotProperties.schema, healed.dataset.properties)
    assertEquals(emptyList(), EntityDatasetValidator.validate(healed.dataset, emptyList()))
  }

  @Test
  fun generate_failsWithoutAnArea_orADesign() {
    val noArea =
      assertIs<Result.Failed>(
        runDirect { useCase(layer, surveyArea = null, datasets = emptyList()) }
      )
    assertEquals(SamplingMessage.AREA_MISSING.message(), noArea.message)
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
    assertEquals(SamplingMessage.NO_DESIGN.message(), noDesign.message)
  }

  @Test
  fun generate_stopsAtTheNextChunkOnceItsCoroutineIsCancelled() {
    var yields = 0
    val cancelling =
      GenerateSamplePlotsUseCase(
        yieldBetweenChunks = { if (++yields == 2) currentCoroutineContext().cancel() },
        clock = FakeClock(Instant.parse(now)),
      )

    val job = testScope().launch { cancelling(layer, draft.details.surveyArea, draft.datasets) }

    assertTrue(job.isCancelled)
    assertEquals(2, yields)
  }
}
