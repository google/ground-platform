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
package org.groundplatform.v2.devtools.prototypeapp.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatHexColorCss
import org.groundplatform.v2.devtools.prototypeapp.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorState
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng

class SurveyMapContentTest {
  private val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun main_carriesOnePinIconPerDistinctPointPin() {
    val points = state.visibleMapEntities.filter { it.geometryKind == EntityGeometryKind.POINT }
    assertTrue(points.isNotEmpty(), "sample survey should have point features")

    val map = SurveyMapContent.main(state, showNavigation = true)

    val expectedIds =
      points
        .map { ent ->
          val layer = state.mapLayerFor(ent)
          GroundPin.iconId(
            formatHexColorCss(ent.mapColorHex(layer)),
            layer?.iconName,
            ent.id in state.pendingUploadEntityIds,
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
    val map = SurveyMapContent.main(state, showNavigation = true)
    val entitiesById = state.visibleMapEntities.associateBy { it.id }
    val shapes =
      map.entityFeatures().filter {
        it.properties[SurveyMapContent.PROP_KIND] != SurveyMapContent.KIND_VERTEX
      }
    assertTrue(shapes.isNotEmpty())
    // The sample data has features whose status color differs from their layer's color.
    assertTrue(
      shapes.any { feature ->
        val entity = entitiesById.getValue(SurveyMapIds.entityIdOf(feature.id)!!)
        entity.markerColorHex != entity.mapColorHex(state.mapLayerFor(entity))
      }
    )
    shapes.forEach { feature ->
      val entity = entitiesById.getValue(SurveyMapIds.entityIdOf(feature.id)!!)
      val layer = assertNotNull(state.mapLayerFor(entity), "no layer for ${entity.id}")
      val layerCss = formatHexColorCss(layer.colorHex)
      assertEquals(layerCss, feature.properties["stroke"], entity.id)
      if (feature.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POLYGON) {
        assertEquals(layerCss, feature.properties["fill"], entity.id)
      }
    }
  }

  @Test
  fun main_pinsShowTheirLayerIcon() {
    val layer = state.mapLayers.first { it.geometryTypeLabel == "Point" && it.iconName != null }
    val layerEntityIds =
      state.visibleMapEntities.filter { it.layerId == layer.id }.map { it.id }.toSet()
    val map = SurveyMapContent.main(state, showNavigation = true)
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
    val map = SurveyMapContent.main(state, showNavigation = true)
    val entitiesById = state.visibleMapEntities.associateBy { it.id }
    map.markers.forEach { (id, marker) ->
      val entity = SurveyMapIds.entityIdOf(id)?.let { entitiesById[it] } ?: return@forEach
      val expected = Color(entity.mapColorHex(state.mapLayerFor(entity)))
      val expectedStatus =
        if (entity.hasMarkerSymbol) {
          SurveyMarker.StatusBadge(entity.markerSymbol, Color(entity.markerColorHex))
        } else {
          null
        }
      when (marker) {
        is SurveyMarker.PinLabel -> {
          assertEquals(expected, marker.color)
          assertEquals(expectedStatus, marker.status)
        }
        is SurveyMarker.GeometryPill -> {
          assertEquals(expected, marker.color)
          assertEquals(expectedStatus, marker.status)
        }
        else -> {}
      }
    }
  }

  @Test
  fun main_chipsShowStatusInTheStatusColorOnTheLayerColor() {
    val map = SurveyMapContent.main(state, showNavigation = true)
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
    val entity = state.visibleMapEntities.first { it.geometryKind == EntityGeometryKind.POINT }
    val map =
      SurveyMapContent.build(
        anchor = state.mapAnchor,
        basemapType = state.selectedBasemapType,
        showOfflineSector = false,
        entities = listOf(entity),
        selectedEntityId = null,
        pendingIds = emptySet(),
        clusters = null,
        selectedClusterId = null,
        clusterHeader = { "" },
        navigation = null,
        userGps = 0.5f to 0.5f,
        isFollowingUser = false,
        place = null,
        layers = emptyList(),
      )
    val pin = map.entityFeatures().single().properties.getValue("pin")
    assertEquals(GroundPin.iconId(formatHexColorCss(entity.colorHex), null, isPending = false), pin)
  }

  @Test
  fun main_usesThePublishedSurveyEditorLayerStyle() {
    val editor = SurveyEditorState(state.activeSurveyEditorDraft)
    val plots = editor.mapLayers.first { it.id == "shade_monitoring_plots" }
    editor.updateDataset(plots.key) {
      it.copy(style = it.style.copy(colorHex = "#AD1457", iconName = "flag"))
    }
    state.saveSurveyEditorDraft(state.activeSurveyId, editor.toDraft())

    val layer = state.mapLayers.first { it.datasetId == "shade_monitoring_plots" }
    assertEquals(0xFFAD1457, layer.colorHex)
    assertEquals("flag", layer.iconName)
    val layerEntityIds =
      state.visibleMapEntities.filter { it.layerId == layer.id }.map { it.id }.toSet()
    assertTrue(layerEntityIds.isNotEmpty())
    val map = SurveyMapContent.main(state, showNavigation = true)
    map
      .entityFeatures()
      .filter {
        SurveyMapIds.entityIdOf(it.id) in layerEntityIds &&
          it.properties[SurveyMapContent.PROP_KIND] != SurveyMapContent.KIND_VERTEX
      }
      .forEach { assertEquals("#AD1457", it.properties["stroke"]) }
  }

  @Test
  fun main_drawsEveryVisibleEntityWithGeometry() {
    val map = SurveyMapContent.main(state, showNavigation = true)
    val drawn = map.entityFeatures().mapNotNull { SurveyMapIds.entityIdOf(it.id) }.toSet()
    val expected =
      state.visibleMapEntities.filter { it.geometryKind != EntityGeometryKind.NONE }.map { it.id }
    assertEquals(expected.toSet(), drawn)
    // Every layer references an existing source (MapContent validates this on construction).
    assertTrue(map.content.layers.isNotEmpty())
  }

  @Test
  fun main_selectedEntityIsDrawnLastAndEmphasized() {
    val target = state.visibleMapEntities.first { it.geometryKind == EntityGeometryKind.POLYGON }
    state.selectEntity(target.id)

    val features = SurveyMapContent.main(state, showNavigation = true).entityFeatures()
    val polygons = features.filter {
      it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_POLYGON
    }
    assertEquals(SurveyMapIds.entity(target.id), polygons.last().id)
    val variant = polygons.last().properties.getValue(SurveyMapContent.PROP_VARIANT)
    assertTrue(variant.startsWith(SurveyMapContent.Variant.SELECTED))
  }

  @Test
  fun main_labelsGeneratedEntitiesOnlyWhenSelected() {
    state.addRandomSites(count = 20)
    state.resetMapZoom()
    val generated = state.visibleMapEntities.first { EntityGeometry.isGenerated(it) }

    val before = SurveyMapContent.main(state, showNavigation = true)
    assertFalse(SurveyMapIds.entity(generated.id) in before.markers)

    state.selectEntity(generated.id)
    val after = SurveyMapContent.main(state, showNavigation = true)
    assertTrue(SurveyMapIds.entity(generated.id) in after.markers)
  }

  @Test
  fun main_showsUserChipAndSelectedPlace() {
    val place = state.places.first()
    state.selectPlace(place.id)

    val map = SurveyMapContent.main(state, showNavigation = true)

    val user = map.markers[SurveyMapIds.USER] as SurveyMarker.UserChip
    assertEquals("⊕ You (GPS)", user.text)
    assertTrue(map.markers.keys.any { SurveyMapIds.isPlace(it) })
    assertEquals(map.markers.keys, map.content.markers.map { it.id }.toSet())
  }

  @Test
  fun ids_mapVerticesBackToTheirEntity() {
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.vertex("e1", 3)))
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.entity("e1")))
    assertEquals("c1", SurveyMapIds.clusterIdOf(SurveyMapIds.cluster("c1")))
    assertEquals(null, SurveyMapIds.entityIdOf("offline-sector"))
  }

  @Test
  fun entityGeometry_boundsMatchTheDrawnShape() {
    val anchor = state.mapAnchor
    for (entity in state.entities) {
      val geometry = EntityGeometry.of(entity, anchor) ?: continue
      val bounds = EntityGeometry.bounds(entity, anchor)
      geometry.coordinates.forEach { assertTrue(it in bounds, "${entity.id}: $it outside $bounds") }
      if (geometry is Geometry.Point) {
        assertEquals(bounds.west, bounds.east)
        assertEquals(bounds.south, bounds.north)
      }
    }
  }

  @Test
  fun desiredMapCamera_followsGpsAndZoomDelta() {
    val camera = state.desiredMapCamera()
    val gps = state.mapAnchor.toLatLng(0.5, 0.5)
    assertNear(gps.latitude, camera.center.latitude)
    assertNear(gps.longitude, camera.center.longitude)

    state.zoomInMap()
    assertNear(state.mapAnchor.zoom + 0.75, state.desiredMapCamera().zoom)
  }

  @Test
  fun syncMapCamera_roundTripsAndStopsFollowing() {
    assertTrue(state.isCameraFollowingUser)
    val start = state.desiredMapCamera()
    val moved =
      CameraPosition(
        LatLng(start.center.latitude + 0.002, start.center.longitude - 0.001),
        start.zoom + 1.0,
      )

    state.syncMapCamera(moved)

    assertFalse(state.isCameraFollowingUser)
    val desired = state.desiredMapCamera()
    assertNear(moved.center.latitude, desired.center.latitude, 1e-6)
    assertNear(moved.center.longitude, desired.center.longitude, 1e-6)
    assertNear(moved.zoom, desired.zoom, 1e-5)
  }

  @Test
  fun syncMapCamera_zoomOnlyKeepsFollowing() {
    val start = state.desiredMapCamera()
    state.syncMapCamera(start.copy(zoom = start.zoom - 0.5))
    assertTrue(state.isCameraFollowingUser)
    assertNear(-0.5, state.mapZoomDelta.toDouble(), 1e-5)
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
        SurveyMapContent.main(state, showNavigation = false),
        SurveyMapContent.geoPointForm(state, isFollowingUser = true),
      )
    maps.forEach { map ->
      assertEquals(map.markers.keys, map.content.markers.map { it.id }.toSet())
      assertNotNull(map.content.userLocation)
    }
  }

  @Test
  fun imagerySources_fromAllUsersAndSurveyOrgCanBeToggledAndRenderedOnBasemap() {
    val allUsersSource = state.allUsersImagerySources.single()
    val surveyOrgSource = state.activeSurveyOrganizationImagerySources.single()
    assertTrue(allUsersSource.isValidXyzUrl)
    assertTrue(surveyOrgSource.isValidXyzUrl)

    val beforeBasemap =
      SurveyMapContent.main(state, showNavigation = false).content.basemap
        as org.groundplatform.v2.map.Basemap.RasterTiles
    val baseLayerCount = beforeBasemap.layers.size

    // Toggle both "All users" and survey-specific Organization imagery layers on.
    state.toggleImagerySource(allUsersSource.id)
    state.toggleImagerySource(surveyOrgSource.id)
    assertTrue(state.isImagerySourceEnabled(allUsersSource.id))
    assertTrue(state.isImagerySourceEnabled(surveyOrgSource.id))

    val afterBasemap =
      SurveyMapContent.main(state, showNavigation = false).content.basemap
        as org.groundplatform.v2.map.Basemap.RasterTiles
    assertEquals(baseLayerCount + 2, afterBasemap.layers.size)
    assertEquals(
      listOf(allUsersSource.urlTemplate, surveyOrgSource.urlTemplate),
      afterBasemap.layers.takeLast(2).map { it.urlTemplate },
    )

    // Add a new XYZ tile source to "All users" with offline download permitted toggled off.
    val err =
      state.addOrganizationImagerySource(
        organizationId = state.allUsersOrganization!!.id,
        name = "USGS Topo",
        urlTemplate =
          "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}",
        allowOfflineDownload = false,
      )
    assertEquals(null, err)
    val added = state.allUsersImagerySources.first { it.name == "USGS Topo" }
    assertFalse(added.allowOfflineDownload)
    state.setOrganizationImagerySourceOfflineAllowed(
      organizationId = state.allUsersOrganization!!.id,
      sourceId = added.id,
      allowOfflineDownload = true,
    )
    assertTrue(state.allUsersImagerySources.first { it.id == added.id }.allowOfflineDownload)
  }

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
}
