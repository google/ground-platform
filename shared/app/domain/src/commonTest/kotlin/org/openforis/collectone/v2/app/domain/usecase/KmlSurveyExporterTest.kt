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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class KmlSurveyExporterTest {
  private val useCase = ExportSurveyDataUseCase()

  private val dataset =
    EntityDataset(
      key = "layer_plots",
      kind = DatasetKind.MAP_LAYER,
      id = "coffee_parcels",
      displayName = "Smallholder Coffee Parcels",
      description = "Polygon boundaries & field verification",
      geometryType = GeometryType.POLYGON,
      keyProperty = "id",
      labelProperty = "id",
      properties = emptyList(),
    )

  @Test
  fun kmlExportEmbedsPanelBalloonIframeFormsAndClosedPolygonRings() {
    val polygon =
      Geometry.Polygon(
        rings =
          listOf(
            listOf(
              LatLng(latitude = 8.472438, longitude = -83.579452),
              LatLng(latitude = 8.472438, longitude = -83.578816),
              LatLng(latitude = 8.471805, longitude = -83.578816),
              LatLng(latitude = 8.471805, longitude = -83.579452),
            )
          )
      )
    val entity =
      GeospatialEntityItem(
        id = "plot-101",
        label = "Parcel #101 & \"North\"",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer-coffee-parcels",
        geoId = "ENT-COFF-01",
        geometry = polygon,
        areaHectares = 1.85,
        perimeterMeters = 520,
        colorHex = 0xFF2E7D32L,
        properties =
          mapOf(
            "status" to "In progress",
            "marker-symbol" to "◐",
            "farmer_owner" to "Maria Rojas",
          ),
        submissions = emptyList(),
        syncStatus = SyncStatus.SYNCED,
      )

    val embeddedForm =
      KmlEmbeddedForm(
        id = "form_plot_check",
        title = "Plot Deforestation Check",
        version = "2",
        formDef = FormDef(form_id = "form_plot_check", title = "Plot Deforestation Check"),
      )

    val file =
      useCase.kml(
        surveyId = "survey-demo",
        surveyTitle = "Costa Rica Coffee Survey",
        dataset = dataset,
        entities = listOf(entity),
        forms = listOf(embeddedForm),
        session =
          KmlBalloonSession(
            balloonAppUrl = "https://example.org/balloon/balloon-form.html",
            serviceUrl = "https://api.example.org",
            authToken = "jwt-token",
            collectorName = "Elena",
            collectorEmail = "elena@example.org",
          ),
      )

    assertEquals("Smallholder Coffee Parcels.kml", file.fileName)
    assertEquals("application/vnd.google-earth.kml+xml;charset=utf-8", file.mimeType)
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in file.content)
    assertTrue("https://example.org/balloon/balloon-form.html" in file.content)
    assertTrue("#earth-balloon-content, #balloon-content" in file.content)
    assertTrue("position:fixed;inset:0;width:100vw;height:100vh;" in file.content)
    assertTrue("collectone:balloon-init" in file.content)
    // KML byte order is aabbggrr: 0x2E7D32 -> r=2e, g=7d, b=32 -> ff327d2e
    assertTrue("<color>ff327d2e</color>" in file.content)
    assertTrue("<Folder id=\"layer_plots\">" in file.content)
    assertTrue("<name>Smallholder Coffee Parcels</name>" in file.content)
    assertTrue("<Placemark id=\"plot-101\">" in file.content)
    assertTrue("<styleUrl>#collectone-layer-1-in-progress</styleUrl>" in file.content)
    assertFalse("\$[c1_status]" in file.content)
    assertFalse("\$[c1_marker_symbol]" in file.content)
    assertTrue("<name>◐ Parcel #101 &amp; &quot;North&quot;</name>" in file.content)
    assertTrue(
      "<Data name=\"c1_entity_title\"><value>Parcel #101 &amp; &quot;North&quot;</value></Data>" in
        file.content
    )
    assertTrue("<Data name=\"c1_geo_id\"><value>ENT-COFF-01</value></Data>" in file.content)
    assertTrue("<Data name=\"farmer_owner\"><value>Maria Rojas</value></Data>" in file.content)
    // Outer ring is automatically closed (5 coordinate tuples for a 4-corner polygon).
    assertTrue(
      "-83.579452,8.472438,0 -83.578816,8.472438,0 -83.578816,8.471805,0 -83.579452,8.471805,0 -83.579452,8.472438,0" in
        file.content
    )
  }

  @Test
  fun surveyKmlGroupsPlacemarksIntoOneFolderPerMapLayer() {
    val stationsDataset =
      EntityDataset(
        key = "layer_stations",
        kind = DatasetKind.MAP_LAYER,
        id = "washing_stations",
        displayName = "Washing Stations",
        geometryType = GeometryType.POINT,
        keyProperty = "id",
        labelProperty = "id",
        properties = emptyList(),
      )
    val parcelEntity =
      GeospatialEntityItem(
        id = "plot-1",
        label = "Parcel 01",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer_plots",
        geoId = "P-01",
        geometry = Geometry.Point(LatLng(8.472, -83.579)),
        areaHectares = 1.0,
        perimeterMeters = 400,
        colorHex = 0xFF2E7D32L,
        properties = emptyMap(),
        submissions = emptyList(),
        syncStatus = SyncStatus.SYNCED,
      )
    val stationEntity =
      GeospatialEntityItem(
        id = "station-1",
        label = "Station Alpha",
        datasetId = "washing_stations",
        datasetName = "Washing Stations",
        layerId = "layer_stations",
        geoId = "WS-01",
        geometry = Geometry.Point(LatLng(8.481, -83.571)),
        areaHectares = 0.0,
        perimeterMeters = 0,
        colorHex = 0xFF1565C0L,
        properties = emptyMap(),
        submissions = emptyList(),
        syncStatus = SyncStatus.SYNCED,
      )

    val file =
      useCase.surveyKml(
        surveyId = "survey-demo",
        surveyTitle = "Costa Rica Coffee Survey",
        layers =
          listOf(
            KmlLayerExport(dataset = dataset, entities = listOf(parcelEntity)),
            KmlLayerExport(dataset = stationsDataset, entities = listOf(stationEntity)),
          ),
      )

    assertEquals("Costa Rica Coffee Survey.kml", file.fileName)
    assertTrue("<Folder id=\"layer_plots\">" in file.content)
    assertTrue("<Folder id=\"layer_stations\">" in file.content)
    assertTrue("<Style id=\"collectone-layer-1\">" in file.content)
    assertTrue("<Style id=\"collectone-layer-2\">" in file.content)
    assertTrue("<Placemark id=\"plot-1\">" in file.content)
    assertTrue("<name>Parcel 01</name>" in file.content, file.content)
    assertFalse("<name>○ Parcel 01</name>" in file.content, file.content)
    assertTrue("<Placemark id=\"station-1\">" in file.content)
  }

  @Test
  fun kmlExportOmitsEmptyCircleForUnsetStatusEvenWhenFormsAreEmbedded() {
    val unsetEntity =
      GeospatialEntityItem(
        id = "plot-unset",
        label = "Parcel Unset",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer-coffee-parcels",
        geoId = "ENT-01",
        geometry = Geometry.Point(LatLng(8.472, -83.579)),
        areaHectares = 1.0,
        perimeterMeters = 400,
        colorHex = 0xFF2E7D32L,
        properties = emptyMap(),
        submissions = emptyList(),
        syncStatus = SyncStatus.SYNCED,
      )
    val surveyedEntity =
      unsetEntity.copy(
        id = "plot-surveyed",
        label = "Parcel Surveyed",
        properties =
          mapOf("status" to "Surveyed", "marker-symbol" to "✓", "marker-color" to "#3C8D40"),
      )
    val form =
      KmlEmbeddedForm(
        id = "form_plot_check",
        title = "Plot Check",
        version = "1",
        formDef = FormDef(form_id = "form_plot_check", title = "Plot Check"),
      )

    val file =
      useCase.kml(
        surveyId = "survey-demo",
        surveyTitle = "Demo",
        dataset = dataset,
        entities = listOf(unsetEntity, surveyedEntity),
        forms = listOf(form),
      )

    assertTrue("<name>Parcel Unset</name>" in file.content, file.content)
    assertFalse("<name>○ Parcel Unset</name>" in file.content, file.content)
    assertTrue("<name>✓ Parcel Surveyed</name>" in file.content, file.content)
    assertTrue("<styleUrl>#collectone-layer-1-completed</styleUrl>" in file.content, file.content)
  }

  @Test
  fun kmlExportIncludesSamplingDesignSubPlotsOnlyWhenConfigured() {
    val plotEntity = buildSamplePlotEntity()
    val gridDataset =
      dataset.copy(
        key = "layer_sample_plots",
        id = "sample_plots",
        displayName = "Sample plots",
        properties = SamplePlotProperties.schema,
        generator =
          SampleDesignConfig(
            plotShape = PlotShapeOption.SQUARE,
            plotSizeM = 30.0,
            subPlot = SubPlotMode.GRID,
            subPlotGridN = 2,
            subPlotSpacingM = 10.0,
          ),
      )
    val stationsDataset =
      EntityDataset(
        key = "layer_stations",
        kind = DatasetKind.MAP_LAYER,
        id = "washing_stations",
        displayName = "Washing Stations",
        geometryType = GeometryType.POINT,
        keyProperty = "id",
        labelProperty = "id",
        properties = emptyList(),
      )
    val stationEntity =
      GeospatialEntityItem(
        id = "station-1",
        label = "Station Alpha",
        datasetId = "washing_stations",
        datasetName = "Washing Stations",
        layerId = "layer_stations",
        geoId = "WS-01",
        geometry = Geometry.Point(LatLng(8.481, -83.571)),
        areaHectares = 0.0,
        perimeterMeters = 0,
        colorHex = 0xFF1565C0L,
        properties = emptyMap(),
        submissions = emptyList(),
        syncStatus = SyncStatus.SYNCED,
      )

    val withSubPlots =
      useCase.surveyKml(
        surveyId = "survey-demo",
        surveyTitle = "Costa Rica Coffee Survey",
        layers =
          listOf(
            KmlLayerExport(dataset = gridDataset, entities = listOf(plotEntity)),
            KmlLayerExport(dataset = stationsDataset, entities = listOf(stationEntity)),
          ),
      )

    assertTrue("<MultiGeometry>" in withSubPlots.content, withSubPlots.content)
    assertTrue("""<LineString id="plot-sub-1-subplot-1">""" in withSubPlots.content)
    assertTrue("""<LineString id="plot-sub-1-subplot-4">""" in withSubPlots.content)
    assertTrue("""<Data name="c1_subplot_count"><value>4</value></Data>""" in withSubPlots.content)
    assertTrue("<width>2.0</width>" in withSubPlots.content)
    assertTrue("<width>2.5</width>" in withSubPlots.content)

    val withoutSubPlots =
      useCase.kml(
        surveyId = "survey-demo",
        surveyTitle = "Costa Rica Coffee Survey",
        dataset =
          gridDataset.copy(generator = gridDataset.generator!!.copy(subPlot = SubPlotMode.NONE)),
        entities =
          listOf(plotEntity.copy(properties = mapOf(SamplePlotProperties.PLOT_ID to "P000001"))),
      )
    assertFalse("<MultiGeometry>" in withoutSubPlots.content, withoutSubPlots.content)
    assertFalse("plot-sub-1-subplot-" in withoutSubPlots.content, withoutSubPlots.content)
    assertFalse("c1_subplot_count" in withoutSubPlots.content, withoutSubPlots.content)
  }

  private fun buildSamplePlotEntity(): GeospatialEntityItem {
    val plotPolygon =
      Geometry.Polygon(
        rings =
          listOf(
            listOf(
              LatLng(latitude = 8.472000, longitude = -83.579200),
              LatLng(latitude = 8.472000, longitude = -83.578900),
              LatLng(latitude = 8.472300, longitude = -83.578900),
              LatLng(latitude = 8.472300, longitude = -83.579200),
            )
          )
      )
    val subPlotPoints =
      listOf(
        LatLng(8.472200, -83.579100),
        LatLng(8.472200, -83.579000),
        LatLng(8.472100, -83.579100),
        LatLng(8.472100, -83.579000),
      )
    return GeospatialEntityItem(
      id = "plot-sub-1",
      label = "P000001",
      datasetId = "sample_plots",
      datasetName = "Sample plots",
      layerId = "layer_sample_plots",
      geoId = "P000001",
      geometry = plotPolygon,
      areaHectares = 0.09,
      perimeterMeters = 120,
      colorHex = 0xFFF9A825L,
      properties =
        mapOf(
          SamplePlotProperties.PLOT_ID to "P000001",
          SamplePlotProperties.SAMPLES to SamplePlotProperties.formatGeotrace(subPlotPoints),
          SamplePlotProperties.SAMPLE_IDS to "1 2 3 4",
        ),
      submissions = emptyList(),
      syncStatus = SyncStatus.SYNCED,
      isGenerated = true,
    )
  }
}
