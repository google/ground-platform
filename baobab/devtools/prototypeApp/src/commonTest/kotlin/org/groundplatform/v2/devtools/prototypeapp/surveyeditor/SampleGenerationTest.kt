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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.groundplatform.v2.core.sampling.GeneratedPlot
import org.groundplatform.v2.core.sampling.SampleDesign
import org.groundplatform.v2.core.sampling.SampleEstimate
import org.groundplatform.v2.core.sampling.SamplingArea
import org.groundplatform.v2.core.sampling.SamplingEngine
import org.groundplatform.v2.core.sampling.SamplingException
import org.groundplatform.v2.core.sampling.SamplingResult
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runDirect
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GenerationRecord
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PlotShapeOption
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleAreaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignInputs
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleGenerationOutcome
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SubPlotMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toGeoCoord
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.EntitiesOverrideSurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.fakeEntityWithSubmissions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.testScope
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

class SampleGenerationTest {
  private val now = "2026-01-02T03:04:05Z"

  private fun newState(
    engine: SamplingEngine? = null,
    submissions: StateFlow<Pair<String, Int>?> = MutableStateFlow(null),
    yieldBetweenChunks: suspend () -> Unit = {},
  ): SurveyEditorViewModel {
    val store = InMemoryLocalStore()
    // Submissions on the plots of (dataset ID, count), as seen through the survey repository.
    val entities =
      submissions
        .map {
          it?.let { (datasetId, count) -> listOf(fakeEntityWithSubmissions(datasetId, count)) }
            ?: emptyList()
        }
        .stateIn(testScope(), SharingStarted.Eagerly, emptyList())
    return surveyEditorViewModel(
      store = store,
      surveyRepository = EntitiesOverrideSurveyRepository(SurveyRepositoryImpl(store), entities),
      samplingEngine = engine,
      yieldBetweenChunks = yieldBetweenChunks,
      now = { now },
    )
  }

  /** Adds a sample plots layer with a coarse grid and 2×2 sub-plots over the seed survey area. */
  private fun SurveyEditorViewModel.addGridLayer(): String {
    val key = addSamplePlotsLayer()
    updateSampleDesign(key) {
      it.copy(gridSpacingM = 1000.0, subPlot = SubPlotMode.GRID, subPlotGridN = 2)
    }
    return key
  }

  private fun SurveyEditorViewModel.dataset(key: String) = ui.datasets.first { it.key == key }

  @Test
  fun addSamplePlotsLayer_usesSurveyAreaAndReservedSchema() {
    val state = newState()
    assertTrue(state.ui.canGenerateSamplePlots)
    val key = state.addSamplePlotsLayer()
    val dataset = state.dataset(key)
    assertEquals(SurveyEditorSection.Dataset(key), state.ui.section)
    assertTrue(dataset.isGenerated)
    assertEquals(SampleAreaSource.SurveyArea, dataset.generator?.areaSource)
    assertEquals(SamplePlotProperties.PLOT_ID, dataset.keyProperty)
    assertEquals(SamplePlotProperties.all, dataset.properties.map { it.name }.toSet())
    assertTrue(dataset.rows.isEmpty())
  }

  @Test
  fun generateSample_writesOneFeaturePerPlot() {
    val state = newState()
    val key = state.addGridLayer()
    assertNotNull(state.estimateSample(key))

    val outcome =
      assertIs<SampleGenerationOutcome.Generated>(runDirect { state.generateSample(key) })

    val dataset = state.dataset(key)
    assertTrue(outcome.plotCount > 5, "plots: ${outcome.plotCount}")
    assertEquals(outcome.plotCount, dataset.rows.size)
    assertEquals(GeometryKind.POLYGON, dataset.geometryKind)
    dataset.rows.forEach { row ->
      val plotId = assertNotNull(row.values[SamplePlotProperties.PLOT_ID])
      assertEquals("$key-$plotId", row.key)
      // Squares are stored open: 4 vertices, without repeating the first.
      assertEquals(4, row.geometry.size)
      assertEquals(4, SamplePlotProperties.geotraceSize(row.values[SamplePlotProperties.SAMPLES]!!))
      assertEquals("1 2 3 4", row.values[SamplePlotProperties.SAMPLE_IDS])
    }
    val orders = dataset.rows.map { it.values[SamplePlotProperties.SAMPLE_ORDER]!!.toInt() }
    assertEquals((1..dataset.rows.size).toList(), orders.sorted())

    val lastRun = assertNotNull(dataset.generator?.lastRun)
    assertEquals(now, lastRun.generatedAt)
    assertEquals(outcome.plotCount, lastRun.featureCount)
    assertEquals(state.currentInputHash(dataset), lastRun.inputHash)
    assertFalse(state.isDesignStale(dataset))
    assertNull(state.ui.generation)
    assertTrue(state.ui.datasetIssues(dataset).none { it.message.contains("generate") })
  }

  @Test
  fun generateSample_isDeterministicForTheSameSeed() {
    val a =
      newState().let { s ->
        s.addGridLayer().let {
          runDirect { s.generateSample(it) }
          s.dataset(it)
        }
      }
    val b =
      newState().let { s ->
        s.addGridLayer().let {
          runDirect { s.generateSample(it) }
          s.dataset(it)
        }
      }
    assertEquals(a.rows.map { it.geometry }, b.rows.map { it.geometry })
  }

  @Test
  fun pointPlots_useTheCenterAsGeometry() {
    val state = newState()
    val key = state.addGridLayer()
    state.updateSampleDesign(key) { it.copy(plotShape = PlotShapeOption.POINT) }
    runDirect { state.generateSample(key) }
    val dataset = state.dataset(key)
    assertEquals(GeometryKind.POINT, dataset.geometryKind)
    assertTrue(dataset.rows.all { it.geometry.size == 1 })
  }

  @Test
  fun regenerate_keepsUserColumnsByPlotId() {
    val state = newState()
    val key = state.addGridLayer()
    state.updateDataset(key) {
      it.copy(properties = it.properties + EntityProperty("notes", "Notes"))
    }
    runDirect { state.generateSample(key) }
    val row = state.dataset(key).rows.first()
    state.updateCell(key, row.key, "notes", "Steep slope")

    assertIs<SampleGenerationOutcome.Generated>(runDirect { state.regenerateSample(key) })

    val regenerated = state.dataset(key).rows.first { it.key == row.key }
    assertEquals("Steep slope", regenerated.values["notes"])
  }

  @Test
  fun isDesignStale_tracksDesignAndSurveyAreaChanges() {
    val state = newState()
    val key = state.addGridLayer()
    assertFalse(state.isDesignStale(state.dataset(key)), "never generated")
    runDirect { state.generateSample(key) }
    assertFalse(state.isDesignStale(state.dataset(key)))

    state.updateSampleDesign(key) { it.copy(gridSpacingM = 900.0) }
    assertTrue(state.isDesignStale(state.dataset(key)))
    state.updateSampleDesign(key) { it.copy(gridSpacingM = 1000.0) }
    assertFalse(state.isDesignStale(state.dataset(key)))

    val area = assertNotNull(state.ui.details.surveyArea)
    state.setSurveyArea(
      area.copy(parts = listOf(area.parts.single().map { LatLng(it.lat + 0.01, it.lng) }))
    )
    assertTrue(state.isDesignStale(state.dataset(key)))
  }

  @Test
  fun generatedGeometry_isLocked() {
    val state = newState()
    val key = state.addGridLayer()
    runDirect { state.generateSample(key) }
    val row = state.dataset(key).rows.first()
    state.updateGeometry(key, row.key, listOf(LatLng(0.0, 0.0)))
    assertEquals(row.geometry, state.dataset(key).rows.first { it.key == row.key }.geometry)
  }

  @Test
  fun regenerate_isBlockedOnceSubmissionsReferenceThePlots() {
    val submissions = MutableStateFlow<Pair<String, Int>?>(null)
    val state = newState(submissions = submissions)
    val key = state.addGridLayer()
    runDirect { state.generateSample(key) }
    val before = state.dataset(key).rows
    submissions.value = state.dataset(key).id to 2

    assertNotNull(state.ui.regenerateBlockedReason(state.dataset(key)))
    val outcome = runDirect { state.regenerateSample(key) }

    assertIs<SampleGenerationOutcome.Blocked>(outcome)
    assertTrue(outcome.message.contains("2 submissions"))
    assertEquals(before, state.dataset(key).rows)
  }

  @Test
  fun cancelGeneration_keepsPreviousPlots() {
    lateinit var state: SurveyEditorViewModel
    state = newState(yieldBetweenChunks = { state.cancelGeneration() })
    val key = state.addGridLayer()

    val outcome = runDirect { state.generateSample(key) }

    assertEquals(SampleGenerationOutcome.Cancelled, outcome)
    assertTrue(state.dataset(key).rows.isEmpty())
    assertNull(state.dataset(key).generator?.lastRun)
    assertNull(state.ui.generation)
  }

  @Test
  fun engineFailure_isReportedAsDatasetIssue() {
    val state = newState(engine = FailingEngine("Can't fit 50 plots 900 m apart"))
    val key = state.addGridLayer()

    val outcome = runDirect { state.generateSample(key) }

    assertEquals(SampleGenerationOutcome.Failed("Can't fit 50 plots 900 m apart."), outcome)
    assertEquals("Can't fit 50 plots 900 m apart.", state.ui.generationError(key))
    assertTrue(
      state.ui.datasetIssues(state.dataset(key)).any { it.message.startsWith("Can't fit") }
    )
    assertTrue(state.dataset(key).rows.isEmpty())
    assertNull(state.ui.generation)
  }

  @Test
  fun generateSample_failsWithoutSurveyArea() {
    val state = newState()
    val key = state.addGridLayer()
    state.setSurveyArea(null)
    val outcome = assertIs<SampleGenerationOutcome.Failed>(runDirect { state.generateSample(key) })
    assertTrue(outcome.message.startsWith("Set a survey area"))
  }

  @Test
  fun inputHash_changesWithDesignOnly() {
    val area =
      SamplingArea.fromSurveyArea(
        listOf(listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.1), LatLng(0.1, 0.1)).map { it.toGeoCoord() })
      )
    val config = SampleDesignConfig()
    assertEquals(SampleDesignInputs.hash(area, config), SampleDesignInputs.hash(area, config))
    assertEquals(
      SampleDesignInputs.hash(area, config),
      SampleDesignInputs.hash(area, config.copy(lastRun = GenerationRecord(now, "x", 1))),
    )
    assertNotEquals(
      SampleDesignInputs.hash(area, config),
      SampleDesignInputs.hash(area, config.copy(seed = config.seed + 1)),
    )
  }

  @Test
  fun importMapLayer_createsLayerFromGeoJson() {
    val state = newState()
    val geojson =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {"name": "North", "zone": 1},
         "geometry": {"type": "Polygon", "coordinates": [[[36.92,-0.40],[36.95,-0.40],[36.95,-0.42],[36.92,-0.40]]]}},
        {"type": "Feature", "properties": {"name": "South", "zone": 2},
         "geometry": {"type": "Polygon", "coordinates": [[[36.92,-0.43],[36.95,-0.43],[36.95,-0.44],[36.92,-0.43]]]}},
        {"type": "Feature", "properties": {"name": "Well"},
         "geometry": {"type": "Point", "coordinates": [36.93, -0.41]}}
      ]}
      """
        .trimIndent()
    val preview = MapLayerImportPreview.of("zones.geojson", geojson)
    val plan = assertNotNull(preview.plan)
    assertEquals(GeometryKind.POLYGON, plan.geometryKind)
    assertEquals(1, plan.skippedFeatures)

    val key = state.importMapLayer(plan)

    val dataset = state.dataset(key)
    assertEquals(DatasetKind.MAP_LAYER, dataset.kind)
    assertEquals(2, dataset.rows.size)
    assertEquals(3, dataset.rows.first().geometry.size)
    assertEquals(SurveyEditorSection.Dataset(key), state.ui.section)
    // The imported polygons can now provide strata.
    assertTrue(state.ui.strataLayers.any { it.key == key })
  }

  @Test
  fun importPreview_withoutFeaturesHasNoPlan() {
    val preview =
      MapLayerImportPreview.of("empty.geojson", """{"type":"FeatureCollection","features":[]}""")
    assertNull(preview.plan)
  }

  private class FailingEngine(private val message: String) : SamplingEngine {
    override fun estimate(area: SamplingArea, design: SampleDesign) = SampleEstimate(1.0, 50)

    override fun generate(
      area: SamplingArea,
      design: SampleDesign,
      onProgress: (Double) -> Unit,
      isCancelled: () -> Boolean,
    ): SamplingResult = SamplingResult.Failure(message)

    override fun generateChunks(
      area: SamplingArea,
      design: SampleDesign,
      chunkSize: Int,
    ): Sequence<List<GeneratedPlot>> = sequence { throw SamplingException(message) }
  }
}
