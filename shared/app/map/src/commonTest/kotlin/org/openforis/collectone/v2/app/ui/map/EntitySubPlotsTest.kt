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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.core.sampling.PlotLayout
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.core.sampling.SampleEncoding
import org.openforis.collectone.v2.core.sampling.SubPlotLayout
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.MarkerAnchor
import org.openforis.collectone.v2.map.StyleValue

/**
 * Tests [EntitySubPlots] and [SurveyMapContent]'s sub-plot rendering when a plot is selected:
 * explicit `samples` property, synthesis from [MapLayerItem.sampleDesign], basemap contrast, label
 * placement, fill opacity, ring radius, and framing zoom.
 */
class EntitySubPlotsTest {
  private val center = LatLng(-0.42, 36.95)
  private val squareDesign =
    SampleDesign(
      layout = PlotLayout.SystematicGrid(spacingM = 500.0),
      plotShape = PlotShape.SQUARE,
      plotSizeM = 30.0,
      subPlot = SubPlotLayout.Grid(n = 3, spacingM = 10.0),
      seed = 42L,
    )

  private fun plotPolygon(at: LatLng, design: SampleDesign = squareDesign): Geometry.Polygon {
    val ring = PlotGeometry.boundary(at.toGeoCoord(), design.plotShape, design.plotSizeM)
    val open = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
    return Geometry.Polygon(listOf(open.map { it.toLatLng() }))
  }

  private fun plotEntity(
    id: String,
    at: LatLng = center,
    properties: Map<String, String> = emptyMap(),
    geometry: Geometry = plotPolygon(at),
    layerId: String = "layer-plots",
    datasetId: String = "sample_plots",
  ) =
    GeospatialEntityItem(
      id = id,
      label = "Plot $id",
      datasetId = datasetId,
      datasetName = "Sample plots",
      layerId = layerId,
      geoId = id,
      geometry = geometry,
      areaHectares = 0.09,
      perimeterMeters = 120,
      colorHex = 0xFFF9A825L,
      properties = properties,
      submissions = emptyList(),
      isGenerated = true,
    )

  private fun plotLayer(
    id: String = "layer-plots",
    datasetId: String = "sample_plots",
    colorHex: Long = 0xFFF9A825L,
    design: SampleDesign? = squareDesign,
  ) =
    MapLayerItem(
      id = id,
      label = "Sample plots",
      colorHex = colorHex,
      geometryType = GeometryType.POLYGON,
      isVisible = true,
      datasetId = datasetId,
      sampleDesign = design,
    )

  private fun sceneOf(
    entities: List<GeospatialEntityItem>,
    selectedEntityId: String?,
    layers: List<MapLayerItem> = listOf(plotLayer()),
    basemapType: BasemapType = BasemapType.NORMAL,
  ) =
    SurveyMapScene(
      anchor = SurveyMapAnchor.forSurvey("test"),
      basemapType = basemapType,
      entities = entities,
      selectedEntityId = selectedEntityId,
      pendingIds = emptySet(),
      userGps = 0.5f to 0.5f,
      isFollowingUser = false,
      layers = layers,
    )

  private fun SurveyMap.subPlotFeatures() =
    content.sources
      .first { it.id == SurveyMapContent.ENTITY_SOURCE }
      .features
      .filter { it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_SUBPLOT }

  @Test
  fun build_drawsSubPlotsOnlyForSelectedPlot_andMapsTapBackToEntity() {
    val samplesA = listOf(GeoCoord(-0.4200, 36.9500), GeoCoord(-0.4201, 36.9501))
    val samplesB = listOf(GeoCoord(-0.4300, 36.9600))
    val plotA =
      plotEntity(
        id = "p1",
        properties = mapOf(SamplePlotProperties.SAMPLES to SampleEncoding.encodeSamples(samplesA)),
      )
    val plotB =
      plotEntity(
        id = "p2",
        at = LatLng(-0.43, 36.96),
        properties = mapOf(SamplePlotProperties.SAMPLES to SampleEncoding.encodeSamples(samplesB)),
      )

    val unselected = SurveyMapContent.build(sceneOf(listOf(plotA, plotB), selectedEntityId = null))
    assertTrue(unselected.subPlotFeatures().isEmpty(), "unselected map draws no subplots")

    val selected = SurveyMapContent.build(sceneOf(listOf(plotA, plotB), selectedEntityId = "p1"))
    val subPlots = selected.subPlotFeatures()
    assertEquals(2, subPlots.size)
    assertEquals(
      listOf(SurveyMapIds.subPlot("p1", 0), SurveyMapIds.subPlot("p1", 1)),
      subPlots.map { it.id },
    )
    assertTrue(subPlots.all { SurveyMapIds.entityIdOf(it.id) == "p1" })
    assertTrue(subPlots.all { it.geometry is Geometry.Polygon })

    // Sub-plot layers are hollow lines (halo + layer-colored outline), never filled.
    assertTrue(
      selected.content.layers.filterIsInstance<MapLayer.Fill>().none {
        it.id.contains("subplot")
      }
    )
    val halo =
      assertIs<MapLayer.Line>(selected.content.layers.first { it.id == "entity-subplots-halo" })
    val outline =
      assertIs<MapLayer.Line>(selected.content.layers.first { it.id == "entity-subplots" })
    assertEquals(StyleValue.Constant(Color.White), halo.color)
    assertIs<StyleValue.Match<Color>>(outline.color)
  }

  @Test
  fun build_synthesizesSubPlotsFromLayerSampleDesign_whenSamplesPropertyMissing() {
    val plot = plotEntity(id = "p1", properties = mapOf(SamplePlotProperties.PLOT_ID to "P000001"))

    val gridMap = SurveyMapContent.build(sceneOf(listOf(plot), selectedEntityId = "p1"))
    assertEquals(9, gridMap.subPlotFeatures().size)

    val centerLayer = plotLayer(design = squareDesign.copy(subPlot = SubPlotLayout.Center))
    val centerMap =
      SurveyMapContent.build(
        sceneOf(listOf(plot), selectedEntityId = "p1", layers = listOf(centerLayer))
      )
    assertEquals(1, centerMap.subPlotFeatures().size)

    val randomLayer = plotLayer(design = squareDesign.copy(subPlot = SubPlotLayout.Random(5)))
    val randomMapA =
      SurveyMapContent.build(
        sceneOf(listOf(plot), selectedEntityId = "p1", layers = listOf(randomLayer))
      )
    val randomMapB =
      SurveyMapContent.build(
        sceneOf(listOf(plot), selectedEntityId = "p1", layers = listOf(randomLayer))
      )
    assertEquals(5, randomMapA.subPlotFeatures().size)
    assertEquals(randomMapA.subPlotFeatures(), randomMapB.subPlotFeatures())
  }

  @Test
  fun build_omitsSubPlotsWhenDesignDisablesThem() {
    val staleSamples = SampleEncoding.encodeSamples(listOf(center.toGeoCoord()))
    val plot =
      plotEntity(id = "p1", properties = mapOf(SamplePlotProperties.SAMPLES to staleSamples))
    val noneLayer = plotLayer(design = squareDesign.copy(subPlot = SubPlotLayout.None))
    val pointLayer = plotLayer(design = squareDesign.copy(plotShape = PlotShape.POINT))

    assertTrue(
      SurveyMapContent.build(sceneOf(listOf(plot), "p1", layers = listOf(noneLayer)))
        .subPlotFeatures()
        .isEmpty()
    )
    assertTrue(
      SurveyMapContent.build(sceneOf(listOf(plot), "p1", layers = listOf(pointLayer)))
        .subPlotFeatures()
        .isEmpty()
    )
  }

  @Test
  fun build_keepsLayerColorOnSubPlotsOverSatelliteBasemap() {
    val plot = plotEntity(id = "p1")
    val map =
      SurveyMapContent.build(
        sceneOf(
          listOf(plot),
          selectedEntityId = "p1",
          layers = listOf(plotLayer(colorHex = 0xFF00897BL)),
          basemapType = BasemapType.SATELLITE,
        )
      )
    val subPlots = map.subPlotFeatures()
    assertEquals(9, subPlots.size)
    // Selected plot stroke switches to white on satellite, while sub-plot inner rings stay in the
    // layer color so they remain legible over their white halo.
    assertTrue(subPlots.all { it.properties["fill"] == "#00897B" })
  }

  @Test
  fun build_movesLabelBelowSelectedPlotWithSubPlots_andLeavesPlainPlotsCentered() {
    val plotWithSubPlots = plotEntity(id = "p1")
    val mapWithSubPlots =
      SurveyMapContent.build(sceneOf(listOf(plotWithSubPlots), selectedEntityId = "p1"))
    val subPlotMarker = mapWithSubPlots.content.markers.first { it.id == SurveyMapIds.entity("p1") }
    val bounds = plotWithSubPlots.geometry!!.bounds()
    assertEquals(MarkerAnchor.TOP, subPlotMarker.anchor)
    assertEquals(bounds.south, subPlotMarker.position.latitude, 1e-9)
    assertEquals(bounds.center.longitude, subPlotMarker.position.longitude, 1e-9)

    val selectedPolygon =
      mapWithSubPlots.content.sources
        .first { it.id == SurveyMapContent.ENTITY_SOURCE }
        .features
        .first { it.id == SurveyMapIds.entity("p1") }
    assertEquals("selected-subplots", selectedPolygon.properties["fillStyle"])
    assertEquals(
      SurveyMapContent.Variant.SELECTED,
      selectedPolygon.properties[SurveyMapContent.PROP_VARIANT],
    )

    // Without subplots, the label stays centered and the standard selected fill opacity applies.
    val plainLayer = plotLayer(design = null)
    val mapPlain =
      SurveyMapContent.build(
        sceneOf(listOf(plotWithSubPlots), selectedEntityId = "p1", layers = listOf(plainLayer))
      )
    val plainMarker = mapPlain.content.markers.first { it.id == SurveyMapIds.entity("p1") }
    assertEquals(MarkerAnchor.CENTER, plainMarker.anchor)
    val plainPolygon =
      mapPlain.content.sources
        .first { it.id == SurveyMapContent.ENTITY_SOURCE }
        .features
        .first { it.id == SurveyMapIds.entity("p1") }
    assertEquals(SurveyMapContent.Variant.SELECTED, plainPolygon.properties["fillStyle"])
  }

  @Test
  fun sizeM_followsDesign_andCapsSpacingWithoutDesign() {
    assertEquals(
      4.0,
      EntitySubPlots.sizeM(
        squareDesign.copy(plotSizeM = 40.0, subPlot = SubPlotLayout.Center),
        listOf(center),
      ),
    )
    assertEquals(
      2.0,
      EntitySubPlots.sizeM(
        squareDesign.copy(plotSizeM = 40.0, subPlot = SubPlotLayout.Grid(3, spacingM = 4.0)),
        listOf(center),
      ),
    )
    assertEquals(
      4.0,
      EntitySubPlots.sizeM(
        squareDesign.copy(plotSizeM = 100.0, subPlot = SubPlotLayout.Center),
        listOf(center),
      ),
    )
    // Without a design, a tight 2 m pair is capped at 1.0 m side length so boxes never overlap.
    val tightPair = listOf(LatLng(0.0, 0.0), LatLng(2.0 / 111_320.0, 0.0))
    assertEquals(1.0, EntitySubPlots.sizeM(null, tightPair), 1e-2)
    assertEquals(EntitySubPlots.DEFAULT_SIZE_M, EntitySubPlots.sizeM(null, listOf(center)))
  }

  @Test
  fun groundSquare_matchesPlotGeometrySubPlotBoxForBothSquareAndCirclePlots() {
    val expected =
      PlotGeometry.subPlotBox(center.toGeoCoord(), 3.0).dropLast(1).map { it.toLatLng() }
    val square = EntitySubPlots.groundSquare(center, sizeM = 3.0)
    assertEquals(4, square.size)
    assertEquals(expected, square)

    // Circular plots also render hollow square sub-plot boxes, matching Google Earth KML.
    val circleDesign =
      squareDesign.copy(plotShape = PlotShape.CIRCLE, subPlot = SubPlotLayout.Center)
    val circlePlot = plotEntity(id = "c1", geometry = plotPolygon(center, circleDesign))
    val circleMap =
      SurveyMapContent.build(
        sceneOf(
          listOf(circlePlot),
          selectedEntityId = "c1",
          layers = listOf(plotLayer(design = circleDesign)),
        )
      )
    val subPlotPolygon = assertIs<Geometry.Polygon>(circleMap.subPlotFeatures().single().geometry)
    assertEquals(4, subPlotPolygon.rings.single().size)
  }

  @Test
  fun maxFramingZoom_zoomsCloserOnlyForPolygonPlotsWithSubPlots() {
    val plotWithDesign = plotEntity(id = "p1")
    val layerWithDesign = plotLayer(design = squareDesign)
    val plainLayer = plotLayer(design = null)
    val pointPlot = plotEntity(id = "pt", geometry = Geometry.Point(center))

    val stateWithDesign = SurveyMapUiState(mapLayers = listOf(layerWithDesign))
    assertEquals(
      EntitySubPlots.SUBPLOT_MAX_FRAMING_ZOOM,
      stateWithDesign.maxFramingZoom(plotWithDesign),
    )

    val statePlain = SurveyMapUiState(mapLayers = listOf(plainLayer))
    assertEquals(17.0, statePlain.maxFramingZoom(plotWithDesign))
    assertEquals(16.0, statePlain.maxFramingZoom(pointPlot))

    val importedPlotWithSamples =
      plotEntity(
        id = "p2",
        properties =
          mapOf(
            SamplePlotProperties.SAMPLES to
              SampleEncoding.encodeSamples(listOf(center.toGeoCoord()))
          ),
      )
    assertEquals(
      EntitySubPlots.SUBPLOT_MAX_FRAMING_ZOOM,
      statePlain.maxFramingZoom(importedPlotWithSamples),
    )
  }

  @Test
  fun ids_mapSubPlotsBackToTheirEntity() {
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.subPlot("e1", 4)))
  }
}
