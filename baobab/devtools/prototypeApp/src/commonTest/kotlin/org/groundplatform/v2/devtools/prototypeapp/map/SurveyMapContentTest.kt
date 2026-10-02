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
import org.groundplatform.v2.devtools.prototypeapp.geometryKind
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
          GroundPin.iconId(
            ent.markerColorCss.ifEmpty { "#2E7D32" },
            ent.markerSymbol,
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

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
}
