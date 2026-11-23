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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.formatHexColorCss
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.ui.datacollection.geoPointForm
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.map.RasterTileLayer
import org.openforis.collectone.v2.map.VectorLineOverlay

class SurveyMapContentTest {
  private val state = TestAppSession(sampleDataHolder())

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  private fun SurveyMap.overlayFeatures() =
    content.sources.first { it.id == SurveyMapContent.OVERLAY_SOURCE }.features

  private val savedSatellite =
    SavedImageryArea(
      "a1",
      LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40),
      DataSize.ofMegabytes(38),
    )
  private val savedTerrain =
    SavedImageryArea(
      "a2",
      LngLatBounds(west = 37.00, south = -0.45, east = 37.05, north = -0.40),
      DataSize.ofMegabytes(10),
    )

  /**
   * A map of no entities on [basemap] with the two saved areas, each drawn when its switch is on.
   */
  private fun savedImageryMap(
    basemap: BasemapType,
    drawSatellite: Boolean,
    drawTerrain: Boolean,
  ): SurveyMap =
    SurveyMapContent.build(
      SurveyMapScene(
        anchor = state.viewModels.surveyMap.uiState.value.anchor,
        basemapType = basemap,
        entities = emptyList(),
        selectedEntityId = null,
        pendingIds = emptySet(),
        userGps = 0.5f to 0.5f,
        isFollowingUser = false,
        savedImagery =
          SavedImageryOnMap(
            satellite = if (drawSatellite) listOf(savedSatellite) else emptyList(),
            terrain = if (drawTerrain) listOf(savedTerrain) else emptyList(),
            drawSatellite = drawSatellite,
            drawTerrain = drawTerrain,
          ),
      )
    )

  private val SurveyMap.overlays
    get() = (content.basemap as Basemap.MapboxStyle).overlays

  @Test
  fun savedImagery_onTerrain_drawsSatelliteImageryInsideItsAreas() {
    for (drawTerrain in listOf(false, true)) {
      val map = savedImageryMap(BasemapType.NORMAL, drawSatellite = true, drawTerrain = drawTerrain)
      val raster = map.overlays.filterIsInstance<RasterTileLayer>().single()
      assertEquals(SurveyBasemaps.SATELLITE_TILESET, raster.tilesetUrl)
      assertEquals(savedSatellite.bounds, raster.bounds)
      assertEquals(16, raster.maxZoom)
      // The terrain is the basemap itself: outlined only.
      assertTrue(map.overlays.none { it is VectorLineOverlay })
    }
  }

  @Test
  fun savedImagery_onSatellite_drawsContoursInsideTerrainAreas() {
    for (drawSatellite in listOf(false, true)) {
      val map =
        savedImageryMap(BasemapType.SATELLITE, drawSatellite = drawSatellite, drawTerrain = true)
      val contours = map.overlays.filterIsInstance<VectorLineOverlay>().single()
      assertEquals(SurveyBasemaps.TERRAIN_TILESET, contours.tilesetUrl)
      assertEquals("contour", contours.sourceLayer)
      assertEquals(savedTerrain.bounds, contours.bounds)
      assertNotNull(contours.emphasis)
      // The imagery is the basemap itself: outlined only.
      assertTrue(map.overlays.none { it is RasterTileLayer })
    }
  }

  @Test
  fun savedImagery_switchedOff_drawsNothing() {
    for (basemap in BasemapType.entries) {
      val map = savedImageryMap(basemap, drawSatellite = false, drawTerrain = false)
      assertTrue(map.overlays.isEmpty())
      assertTrue(map.overlayFeatures().isEmpty())
    }
  }

  @Test
  fun savedImagery_outlinesEachArea_byItsKind() {
    for (basemap in BasemapType.entries) {
      val outlines =
        savedImageryMap(basemap, drawSatellite = true, drawTerrain = true).overlayFeatures()
      assertEquals(
        setOf("saved-satellite", "saved-terrain"),
        outlines.map { it.properties.getValue(SurveyMapContent.PROP_KIND) }.toSet(),
      )
      outlines.forEach { assertTrue(it.geometry is Geometry.LineString) }
    }
  }

  @Test
  fun selectionMode_outlinesSavedAreasWhoseSwitchIsOff() {
    val uiState =
      state.viewModels.surveyMap.uiState.value.copy(
        savedSatelliteAreas = listOf(savedSatellite),
        isDownloadedSatelliteVisible = false,
      )
    fun outlines(selecting: Boolean) =
      SurveyMapContent.main(
          uiState.copy(isSelectingOfflineArea = selecting),
          pendingIds = emptySet(),
          showNavigation = false,
        )
        .overlayFeatures()
        .filter { it.properties[SurveyMapContent.PROP_KIND] == "saved-satellite" }
    assertTrue(outlines(selecting = false).isEmpty())
    assertEquals(1, outlines(selecting = true).size)
  }

  @Test
  fun main_carriesOnePinIconPerDistinctPointPin() {
    val points =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities.filter {
        it.geometryType == GeometryType.POINT
      }
    assertTrue(points.isNotEmpty(), "sample survey should have point features")

    val map = state.viewModels.mainSurveyMap(showNavigation = true)

    val expectedIds =
      points
        .map { ent ->
          val layer =
            state.viewModels.dashboard.uiState.value.mapLayers.firstOrNull {
              it.id == (ent).layerId
            }
          AppPin.iconId(
            formatHexColorCss(ent.mapColorHex(layer)),
            layer?.iconName,
            ent.id in state.viewModels.dashboard.uiState.value.pendingUploadEntityIds,
          )
        }
        .toSet()
    assertEquals(expectedIds, map.content.icons.map { it.id }.toSet())
    map.content.icons.forEach { assertTrue(it.svg.startsWith("<svg")) }
    map
      .entityFeatures()
      .filter { it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POINT }
      .forEach { assertTrue(it.properties.getValue("pin") in expectedIds) }
  }

  @Test
  fun main_drawsFeaturesInTheirLayerColor_notTheirStatusColor() {
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val entitiesById =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities.associateBy { it.id }
    val shapes =
      map.entityFeatures().filter {
        it.properties[SurveyMapContent.PROP_KIND] != SurveyMapContent.KIND_VERTEX
      }
    assertTrue(shapes.isNotEmpty())
    // The sample data has features whose status color differs from their layer's color.
    assertTrue(
      shapes.any { feature ->
        val entity = entitiesById.getValue(SurveyMapIds.entityIdOf(feature.id)!!)
        entity.markerColorHex !=
          entity.mapColorHex(
            state.viewModels.dashboard.uiState.value.mapLayers.firstOrNull {
              it.id == (entity).layerId
            }
          )
      }
    )
    shapes.forEach { feature ->
      val entity = entitiesById.getValue(SurveyMapIds.entityIdOf(feature.id)!!)
      val layer =
        assertNotNull(
          state.viewModels.dashboard.uiState.value.mapLayers.firstOrNull {
            it.id == (entity).layerId
          },
          "no layer for ${entity.id}",
        )
      val layerCss = formatHexColorCss(layer.colorHex)
      assertEquals(layerCss, feature.properties["stroke"], entity.id)
      if (feature.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POLYGON) {
        assertEquals(layerCss, feature.properties["fill"], entity.id)
      }
    }
  }

  @Test
  fun main_pinsShowTheirLayerIcon() {
    val layer =
      state.viewModels.dashboard.uiState.value.mapLayers.first {
        it.geometryType == GeometryType.POINT && it.iconName != null
      }
    val layerEntityIds =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .filter { it.layerId == layer.id }
        .map { it.id }
        .toSet()
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val pins =
      map.entityFeatures().filter {
        it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POINT &&
          SurveyMapIds.entityIdOf(it.id) in layerEntityIds
      }
    assertTrue(pins.isNotEmpty())
    pins.forEach { assertTrue("|${layer.iconName}|" in it.properties.getValue("pin")) }
  }

  @Test
  fun main_labelsUseTheLayerColor() {
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val entitiesById =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities.associateBy { it.id }
    map.markers.forEach { (id, marker) ->
      val entity = SurveyMapIds.entityIdOf(id)?.let { entitiesById[it] } ?: return@forEach
      val expected =
        Color(
          entity.mapColorHex(
            state.viewModels.dashboard.uiState.value.mapLayers.firstOrNull {
              it.id == (entity).layerId
            }
          )
        )
      val expectedLabel = entity.label.substringBefore(" •").ifBlank { entity.id }
      val expectedStatus =
        if (entity.hasMarkerSymbol) {
          SurveyMarker.StatusBadge(entity.markerSymbol, Color(entity.markerColorHex))
        } else {
          null
        }
      when (marker) {
        is SurveyMarker.PinLabel -> {
          assertEquals(expectedLabel, marker.label)
          assertEquals(expected, marker.color)
          assertEquals(expectedStatus, marker.status)
        }
        is SurveyMarker.GeometryPill -> {
          assertEquals(expectedLabel, marker.label)
          assertEquals(expected, marker.color)
          assertEquals(expectedStatus, marker.status)
        }
        else -> {}
      }
    }
  }

  @Test
  fun main_chipsShowStatusInTheStatusColorOnTheLayerColor() {
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val badges =
      map.markers.values.mapNotNull {
        when (it) {
          is SurveyMarker.PinLabel -> it.color to it.status
          is SurveyMarker.GeometryPill -> it.color to it.status
          else -> null
        }
      }
    assertTrue(badges.any { (_, status) -> status != null }, "sample data has status symbols")
    // Chip and badge colors differ for at least one feature: layer color vs. status color.
    assertTrue(badges.any { (chip, status) -> status != null && status.color != chip })
  }

  @Test
  fun build_fallsBackToTheEntityColorWithoutItsLayer() {
    val entity =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities.first {
        it.geometryType == GeometryType.POINT
      }
    val map =
      SurveyMapContent.build(
        SurveyMapScene(
          anchor = state.viewModels.surveyMap.uiState.value.anchor,
          basemapType = state.viewModels.surveyMap.uiState.value.selectedBasemapType,
          entities = listOf(entity),
          selectedEntityId = null,
          pendingIds = emptySet(),
          userGps = 0.5f to 0.5f,
          isFollowingUser = false,
        )
      )
    val pin = map.entityFeatures().single().properties.getValue("pin")
    assertEquals(AppPin.iconId(formatHexColorCss(entity.colorHex), null, isPending = false), pin)
  }

  @Test
  fun main_drawsEveryVisibleEntityWithGeometry() {
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val drawn = map.entityFeatures().mapNotNull { SurveyMapIds.entityIdOf(it.id) }.toSet()
    val expected =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .filter { it.geometryType != null }
        .map { it.id }
    assertEquals(expected.toSet(), drawn)
    // Every layer references an existing source (MapContent validates this on construction).
    assertTrue(map.content.layers.isNotEmpty())
  }

  @Test
  fun main_selectedEntityIsDrawnLastAndEmphasized() {
    val target =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities.first {
        it.geometryType == GeometryType.POLYGON
      }
    state.viewModels.surveyMap.selectEntity(target.id)

    val features = state.viewModels.mainSurveyMap(showNavigation = true).entityFeatures()
    val polygons = features.filter {
      it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POLYGON
    }
    assertEquals(SurveyMapIds.entity(target.id), polygons.last().id)
    val variant = polygons.last().properties.getValue(SurveyMapContent.PROP_VARIANT)
    assertTrue(variant.startsWith(SurveyMapContent.Variant.SELECTED))
  }

  @Test
  fun main_onlyFeatureLabelsHideOnCollision() {
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
    val (labels, others) = map.content.markers.partition { SurveyMapIds.entityIdOf(it.id) != null }
    assertTrue(labels.isNotEmpty())
    assertTrue(labels.all { it.collides })
    assertTrue(others.none { it.collides })
  }

  @Test
  fun main_showsUserChipAndSelectedPlace() {
    val place = state.dataHolder.appData.value.places.first()
    state.searchableListActions.selectPlace(place.id)

    val map = state.viewModels.mainSurveyMap(showNavigation = true)

    val user = map.markers[SurveyMapIds.USER] as SurveyMarker.UserChip
    assertEquals(SurveyMarker.UserChip(SurveyMarker.UserChipMode.GPS), user)
    assertTrue(map.markers.keys.any { SurveyMapIds.isPlace(it) })
    assertEquals(map.markers.keys, map.content.markers.map { it.id }.toSet())
  }

  @Test
  fun ids_mapVerticesBackToTheirEntity() {
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.vertex("e1", 3)))
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.entity("e1")))
    assertEquals("c1", SurveyMapIds.clusterIdOf(SurveyMapIds.cluster("c1")))
    assertEquals(null, SurveyMapIds.entityIdOf("navigation-line"))
  }

  @Test
  fun entityGeometry_boundsMatchTheStoredGeometry() {
    for (entity in state.viewModels.dataCollection.uiState.value.entities) {
      val geometry = entity.geometry ?: continue
      val bounds = assertNotNull(EntityGeometry.bounds(entity))
      geometry.coordinates.forEach { assertTrue(it in bounds, "${entity.id}: $it outside $bounds") }
      if (geometry is Geometry.Point) {
        assertEquals(bounds.west, bounds.east)
        assertEquals(bounds.south, bounds.north)
      }
    }
  }

  @Test
  fun desiredMapCamera_followsGpsAndZoomDelta() {
    val camera = state.viewModels.surveyMap.desiredMapCamera()
    val gps = state.viewModels.surveyMap.uiState.value.anchor.toLatLng(0.5, 0.5)
    assertNear(gps.latitude, camera.center.latitude)
    assertNear(gps.longitude, camera.center.longitude)

    state.viewModels.surveyMap.zoomInMap()
    assertNear(
      state.viewModels.surveyMap.uiState.value.anchor.zoom + 0.75,
      state.viewModels.surveyMap.desiredMapCamera().zoom,
    )
  }

  @Test
  fun syncMapCamera_roundTripsAndStopsFollowing() {
    assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    val start = state.viewModels.surveyMap.desiredMapCamera()
    val moved =
      CameraPosition(
        LatLng(start.center.latitude + 0.002, start.center.longitude - 0.001),
        start.zoom + 1.0,
      )

    state.viewModels.surveyMap.syncMapCamera(moved)

    assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    val desired = state.viewModels.surveyMap.desiredMapCamera()
    assertNear(moved.center.latitude, desired.center.latitude, 1e-6)
    assertNear(moved.center.longitude, desired.center.longitude, 1e-6)
    assertNear(moved.zoom, desired.zoom, 1e-5)
  }

  @Test
  fun syncMapCamera_zoomOnlyKeepsFollowing() {
    val start = state.viewModels.surveyMap.desiredMapCamera()
    state.viewModels.surveyMap.syncMapCamera(start.copy(zoom = start.zoom - 0.5))
    assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertNear(-0.5, state.viewModels.surveyMap.uiState.value.mapZoomDelta.toDouble(), 1e-5)
  }

  @Test
  fun framingInsets_keepPanelsClearAndScaleDownOnSmallMaps() {
    val large = framingInsets(DpSize(1000.dp, 800.dp), bottom = 200.dp, right = 300.dp)
    assertEquals(48.dp, large.left)
    assertEquals(348.dp, large.right)
    assertEquals(248.dp, large.bottom)

    val small = framingInsets(DpSize(200.dp, 200.dp), bottom = 300.dp)
    assertTrue(small.top + small.bottom <= 160.dp + 0.01.dp)
    assertTrue(small.bottom > small.top)
  }

  @Test
  fun build_isValidMapContentForEveryForm() {
    val maps =
      listOf(
        state.viewModels.mainSurveyMap(showNavigation = false),
        SurveyMapContent.geoPointForm(
          uiState = state.viewModels.surveyMap.uiState.value,
          dataCollection = state.viewModels.dataCollection.uiState.value,
          pendingIds = state.viewModels.dashboard.uiState.value.pendingUploadEntityIds,
          isFollowingUser = true,
        ),
      )
    maps.forEach { map ->
      assertEquals(map.markers.keys, map.content.markers.map { it.id }.toSet())
      assertNotNull(map.content.userLocation)
    }
  }

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
}
