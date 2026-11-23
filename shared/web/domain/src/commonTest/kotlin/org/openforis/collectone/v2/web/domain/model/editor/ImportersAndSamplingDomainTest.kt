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
package org.openforis.collectone.v2.web.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.GenerationRecord
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SampleAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.io.GeoFeature
import org.openforis.collectone.v2.core.geo.io.GeoGeometry
import org.openforis.collectone.v2.core.geo.io.GeoReadResult
import org.openforis.collectone.v2.core.sampling.GeneratedPlot
import org.openforis.collectone.v2.core.sampling.SamplingArea
import org.openforis.collectone.v2.core.sampling.Stratum
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class ImportersAndSamplingDomainTest {
  private val sampleRing =
    listOf(
      LatLng(-1.0, 36.0),
      LatLng(-1.0, 36.05),
      LatLng(-1.05, 36.05),
      LatLng(-1.05, 36.0),
    )

  @Test
  fun csvAndDataTableImporter_parseQuotesDelimitersTypesDatesAndKeys() {
    val emptyResult = DataTableImporter.plan("empty.csv", "  \n ")
    assertNull(emptyResult.plan)
    assertTrue(emptyResult.notes.isNotEmpty())

    val headerOnlyResult = DataTableImporter.plan("header.csv", "id,name\n")
    assertNull(headerOnlyResult.plan)
    assertTrue(headerOnlyResult.notes.isNotEmpty())

    val csvText =
      "\uFEFFid;name;code;count;area;active;planted_on\r\n" +
        "1;\"Plot \"\"Alpha\"\"\";007;12;3.5;true;03/04/2025\r\n" +
        "1;\"Plot\nBeta\";008;15;4.25;false;05/06/2025;extra_ragged_cell\r\n"

    val previewDayFirst =
      DataTableImportPreview.of("species.table.csv", csvText, DateOrder.DAY_FIRST)
    val plan = assertNotNull(previewDayFirst.plan)
    assertEquals("species.table", plan.name)
    assertEquals("row_id", plan.keyProperty)
    assertEquals("name", plan.labelProperty)
    assertEquals(listOf("planted_on"), plan.ambiguousDateColumns)
    assertTrue(previewDayFirst.notes.size >= 2)

    val propByName = plan.properties.associateBy { it.name }
    assertEquals(PropertyType.TEXT, propByName.getValue("code").type)
    assertEquals(PropertyType.INTEGER, propByName.getValue("count").type)
    assertEquals(PropertyType.DECIMAL, propByName.getValue("area").type)
    assertEquals(PropertyType.BOOLEAN, propByName.getValue("active").type)
    assertEquals(PropertyType.DATE, propByName.getValue("planted_on").type)
    assertEquals("2025-04-03", plan.rows.first()["planted_on"])
    assertEquals("R-0001", plan.rows.first()["row_id"])

    val previewMonthFirst = previewDayFirst.withDateOrder(DateOrder.MONTH_FIRST)
    assertEquals("2025-03-04", assertNotNull(previewMonthFirst.plan).rows.first()["planted_on"])

    val uniqueIdPlan =
      assertNotNull(DataTableImporter.plan("plots.csv", "id,label\nP1,North\nP2,South").plan)
    assertEquals("id", uniqueIdPlan.keyProperty)
    assertEquals("id", uniqueIdPlan.labelProperty)
  }

  @Test
  fun importDates_supportsIsoSlashDashMonthNamesOrdinalsAndLeapYears() {
    assertEquals(DateOrder.MONTH_FIRST, DateOrder.forRegion("us"))
    assertEquals(DateOrder.MONTH_FIRST, DateOrder.forRegion("PH"))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion("KE"))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion(null))

    val unambiguous =
      assertNotNull(
        ImportDates.column(
          listOf(
            "2025-04-03",
            "25/12/2025",
            "3 Sept 2025",
            "April 3rd, 2024",
            "03/04/25",
            "03/04/99",
          ),
          DateOrder.MONTH_FIRST,
        )
      )
    assertFalse(unambiguous.ambiguous)
    assertEquals(
      listOf(
        "2025-04-03",
        "2025-12-25",
        "2025-09-03",
        "2024-04-03",
        "2025-04-03",
        "1999-04-03",
      ),
      unambiguous.isoValues,
    )

    val monthFirst =
      assertNotNull(ImportDates.column(listOf("12/25/2025", "01/02/2025"), DateOrder.DAY_FIRST))
    assertFalse(monthFirst.ambiguous)
    assertEquals(listOf("2025-12-25", "2025-01-02"), monthFirst.isoValues)

    assertNotNull(ImportDates.column(listOf("2024-02-29"), DateOrder.DAY_FIRST))
    assertNull(ImportDates.column(listOf("2025-02-29"), DateOrder.DAY_FIRST))
    assertNull(ImportDates.column(listOf("1.2.25"), DateOrder.DAY_FIRST))
    assertNull(ImportDates.column(listOf("   "), DateOrder.DAY_FIRST))
  }

  @Test
  fun mapLayerImporter_selectsMostSpecificGeometrySanitizesNamesAndCountsSkippedFeatures() {
    assertNull(MapLayerImporter.plan("empty.geojson", GeoReadResult(emptyList())))

    val pt = GeoFeature(GeoGeometry.Point(GeoCoord(36.0, -1.0)), mapOf("id" to "P1"))
    val line =
      GeoFeature(
        GeoGeometry.LineString(listOf(GeoCoord(36.0, -1.0), GeoCoord(36.1, -1.1))),
        mapOf("id" to "L1"),
      )
    val poly1 =
      GeoFeature(
        GeoGeometry.Polygon(
          listOf(GeoCoord(36.0, -1.0), GeoCoord(36.1, -1.0), GeoCoord(36.0, -1.1))
        ),
        mapOf("123 Code" to "A", "geometry" to "g1", "name" to "Parcel A"),
      )
    val poly2 =
      GeoFeature(
        GeoGeometry.Polygon(
          listOf(GeoCoord(36.2, -1.0), GeoCoord(36.3, -1.0), GeoCoord(36.2, -1.1))
        ),
        mapOf("123 Code" to "B", "geometry" to "g2", "name" to "Parcel B"),
      )

    val plan =
      assertNotNull(
        MapLayerImporter.plan("parcels.geojson", GeoReadResult(listOf(pt, line, poly1, poly2)))
      )
    assertEquals("parcels", plan.name)
    assertEquals(GeometryType.POLYGON, plan.geometryType)
    assertEquals(2, plan.skippedFeatures)
    assertEquals("feature_id", plan.keyProperty)
    assertEquals("name", plan.labelProperty)
    assertTrue(plan.properties.any { it.name == "p_123_code" })
    assertTrue(plan.properties.any { it.name == "geometry_" })
    assertEquals("F-0001", plan.rows.first().first["feature_id"])

    val geoJson =
      """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"id":"U1"},"geometry":{"type":"Point","coordinates":[36.8,-1.2]}}]}"""
    val preview = MapLayerImportPreview.of("points.geojson", geoJson)
    val previewPlan = assertNotNull(preview.plan)
    assertEquals(GeometryType.POINT, previewPlan.geometryType)
    assertEquals("id", previewPlan.keyProperty)
  }

  @Test
  fun suggestedGridSpacing_updatesAutoLayersWhenAreaChangesAndRespectsLastRun() {
    assertNull(SuggestedGridSpacing.forAreaM2(0.0))
    assertNull(SuggestedGridSpacing.forAreaM2(-10.0))
    assertNull(SuggestedGridSpacing.forAreaM2(Double.NaN))
    assertEquals(10.0, SuggestedGridSpacing.forAreaM2(100.0))
    assertEquals(50_000.0, SuggestedGridSpacing.forAreaM2(1e15))

    val surveyArea = SurveyArea("Area", listOf(sampleRing))
    val genLayer =
      EntityDataset(
        key = "d_plots",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        keyProperty = SamplePlotProperties.PLOT_ID,
        labelProperty = SamplePlotProperties.PLOT_ID,
        properties = SamplePlotProperties.schema,
        generator = SampleDesignConfig(gridSpacingM = 100.0, gridSpacingAuto = true),
      )
    val before = SurveyEditorDraft.blank(surveyId = "s1").copy(datasets = listOf(genLayer))
    val afterWithArea = before.copy(details = before.details.copy(surveyArea = surveyArea))
    val updated = SuggestedGridSpacing.applyTo(before, afterWithArea)
    val newSpacing = updated.datasets.single().generator!!.gridSpacingM
    assertNotEquals(100.0, newSpacing)

    val withLastRun =
      updated.copy(
        datasets =
          listOf(
            updated.datasets.single().let {
              it.copy(
                generator =
                  it.generator!!.copy(
                    gridSpacingM = 123.0,
                    lastRun = GenerationRecord("2026-01-01T00:00:00Z", "h", 10),
                  )
              )
            }
          )
      )
    assertEquals(
      123.0,
      SuggestedGridSpacing.applyTo(withLastRun, withLastRun)
        .datasets
        .single()
        .generator!!
        .gridSpacingM,
    )
  }

  @Test
  fun sampleDesignInputsAndPlotRows_computeDeterministicHashAndCarryOverCustomColumns() {
    assertNull(SampleGenerationProgress("d1", plotsSoFar = 5, expectedPlots = 0).fraction)
    assertEquals(0.5f, SampleGenerationProgress("d1", plotsSoFar = 5, expectedPlots = 10).fraction)
    assertEquals(1.0f, SampleGenerationProgress("d1", plotsSoFar = 15, expectedPlots = 10).fraction)

    val samplingArea = SamplingArea(listOf(Stratum("s1", sampleRing.map { it.toGeoCoord() })))
    val cfg1 =
      SampleDesignConfig(
        method = SampleMethod.STRATIFIED_RANDOM,
        allocation = AllocationMode.CUSTOM,
        customCounts = linkedMapOf("b" to 20, "a" to 10),
      )
    val cfg2 = cfg1.copy(customCounts = linkedMapOf("a" to 10, "b" to 20))
    assertEquals(
      SampleDesignInputs.hash(samplingArea, cfg1),
      SampleDesignInputs.hash(samplingArea, cfg2),
    )

    val prevRows =
      listOf(
        EntityRow(
          key = "d1-P001",
          values = mapOf(SamplePlotProperties.PLOT_ID to "P001", "custom_note" to "Keep me"),
        )
      )
    val generatedPlots =
      listOf(
        GeneratedPlot(
          plotId = "P001",
          stratum = "default",
          sampleOrder = 1,
          inclusionWeightHa = 2.5,
          clusterId = "",
          center = GeoCoord(36.0, -1.0),
          boundary =
            listOf(
              GeoCoord(36.0, -1.0),
              GeoCoord(36.01, -1.0),
              GeoCoord(36.01, -1.01),
              GeoCoord(36.0, -1.0),
            ),
          samples = listOf(GeoCoord(36.005, -1.005)),
        )
      )
    val rows = SamplePlotRows.toRows("d1", generatedPlots, prevRows)
    assertEquals("Keep me", rows.single().values["custom_note"])
    assertEquals(3, rows.single().geometry.size)
  }

  @Test
  fun samplingAreas_resolvesSurveyAreaAndStrataLayer() {
    val surveyArea = SurveyArea("Area", listOf(sampleRing))
    assertIs<SamplingAreaResult.Unavailable>(
      SamplingAreas.samplingArea(SampleDesignConfig(), surveyArea = null, datasets = emptyList())
    )
    assertIs<SamplingAreaResult.Ready>(
      SamplingAreas.samplingArea(
        SampleDesignConfig(),
        surveyArea = surveyArea,
        datasets = emptyList(),
      )
    )

    val strataConfig =
      SampleDesignConfig(areaSource = SampleAreaSource.StrataLayer("d_strata", "zone"))
    val pointLayer =
      EntityDataset(
        key = "d_strata",
        kind = DatasetKind.MAP_LAYER,
        id = "strata",
        displayName = "Strata",
        geometryType = GeometryType.POINT,
        keyProperty = "id",
        labelProperty = "id",
        properties = listOf(EntityProperty("id", "ID")),
      )
    assertIs<SamplingAreaResult.Unavailable>(
      SamplingAreas.samplingArea(strataConfig, surveyArea = null, datasets = listOf(pointLayer))
    )
    val populatedPolyLayer =
      pointLayer.copy(
        geometryType = GeometryType.POLYGON,
        rows =
          listOf(
            EntityRow("r1", mapOf("zone" to "Lowland"), sampleRing),
            EntityRow("r2", mapOf("zone" to "Upland"), sampleRing),
          ),
      )
    assertIs<SamplingAreaResult.Ready>(
      SamplingAreas.samplingArea(
        strataConfig,
        surveyArea = null,
        datasets = listOf(populatedPolyLayer),
      )
    )
    assertEquals(
      listOf("Lowland", "Upland"),
      SamplingAreas.strataIds(strataConfig, listOf(populatedPolyLayer)),
    )
  }
}
