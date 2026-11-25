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

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.map.RasterTileLayer
import org.openforis.collectone.v2.map.VectorLineOverlay

/**
 * The pure map builders of the module: [SurveyMapContent], [SurveyMapIds], [SurveyBasemaps],
 * [EntityGeometry], and [framingInsets]. Golden screenshots cover how the result looks; these cover
 * what is drawn and how taps map back to domain ids.
 */
class SurveyMapContentBuildersTest {
  private val a = LatLng(0.0, 0.0)
  private val b = LatLng(0.0, 2.0)
  private val c = LatLng(2.0, 2.0)

  private fun SurveyMap.features(source: String) =
    content.sources.first { it.id == source }.features

  private fun SurveyMap.hoverFeatures() =
    features(SurveyMapContent.OVERLAY_SOURCE).filter {
      it.properties[SurveyMapContent.PROP_KIND] in
        setOf(
          SurveyMapHover.KIND_POINT,
          SurveyMapHover.KIND_LINE,
          SurveyMapHover.KIND_POLYGON,
        )
    }

  @Test
  fun main_drawsEveryVisibleFeature_andTheUserChip() {
    val f = MapViewModelFixture()
    val map = SurveyMapContent.main(f.uiState, pendingIds = emptySet(), showNavigation = false)
    val drawnIds =
      map.features(SurveyMapContent.ENTITY_SOURCE).mapNotNull { SurveyMapIds.entityIdOf(it.id) }
    assertEquals(
      f.uiState.visibleMapEntities.filter { it.geometry != null }.map { it.id }.toSet(),
      drawnIds.toSet(),
    )
    assertEquals(
      SurveyMarker.UserChip(SurveyMarker.UserChipMode.CENTERED),
      map.markers[SurveyMapIds.USER],
    )
    assertNotNull(map.content.userLocation)
  }

  @Test
  fun main_drawsTheSelectedFeatureLast_andEmphasized() {
    val f = MapViewModelFixture()
    f.viewModel.selectEntity(SEEDED_ENTITY_ID)
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    val shapes =
      map.features(SurveyMapContent.ENTITY_SOURCE).filter {
        SurveyMapIds.entityIdOf(it.id) != null && !it.id.contains('#')
      }
    assertEquals(SurveyMapIds.entity(SEEDED_ENTITY_ID), shapes.last().id)
    val label = map.markers.getValue(SurveyMapIds.entity(SEEDED_ENTITY_ID))
    assertTrue(
      (label as? SurveyMarker.PinLabel)?.selected
        ?: assertIs<SurveyMarker.GeometryPill>(label).selected
    )
  }

  @Test
  fun main_withoutAHoveredFeature_drawsNoHoverFeatures() {
    val f = MapViewModelFixture()
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    assertTrue(map.hoverFeatures().isEmpty())
  }

  @Test
  fun main_drawsTheHoveredFeatureAgainInTheOverlay_withoutTouchingTheEntities() {
    val f = MapViewModelFixture()
    val plain = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    val map =
      SurveyMapContent.main(
        f.uiState,
        emptySet(),
        showNavigation = false,
        hoveredEntityId = SEEDED_ENTITY_ID,
      )

    val hover = map.hoverFeatures().single()
    assertEquals(SurveyMapIds.entity(SEEDED_ENTITY_ID), hover.id)
    val entityFeature =
      map.features(SurveyMapContent.ENTITY_SOURCE).first {
        it.id == SurveyMapIds.entity(SEEDED_ENTITY_ID)
      }
    assertEquals(entityFeature.geometry, hover.geometry)
    // A hover layer draws it: every hover kind has a layer filtering on it.
    assertTrue(map.content.layers.any { it.filter?.matches(hover) == true })
    // Hovering resends only the small overlay source, never the entities.
    assertEquals(
      plain.features(SurveyMapContent.ENTITY_SOURCE),
      map.features(SurveyMapContent.ENTITY_SOURCE),
    )
    assertEquals(plain.content.layers, map.content.layers)
  }

  @Test
  fun main_doesNotDrawHoverOverTheSelectedFeature() {
    val f = MapViewModelFixture()
    f.viewModel.selectEntity(SEEDED_ENTITY_ID)
    val map =
      SurveyMapContent.main(
        f.uiState,
        emptySet(),
        showNavigation = false,
        hoveredEntityId = SEEDED_ENTITY_ID,
      )
    assertTrue(map.hoverFeatures().isEmpty())
  }

  @Test
  fun main_whileClustering_drawsBalloonsInsteadOfFeatures() {
    val f = MapViewModelFixture()
    repeat(4) { f.viewModel.zoomOutMap() }
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    assertTrue(map.features(SurveyMapContent.ENTITY_SOURCE).isEmpty())
    val balloons = map.markers.filterKeys { SurveyMapIds.clusterIdOf(it) != null }
    assertEquals(f.uiState.mapFeatureClusters.size, balloons.size)
    assertTrue(balloons.values.all { it is SurveyMarker.ClusterBalloon })
  }

  @Test
  fun main_withNavigation_drawsTheLineAndPill_andTheUsersBearing() {
    val f = MapViewModelFixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    f.viewModel.startNavigationToEntity(SEEDED_ENTITY_ID)
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = true)
    val overlayIds = map.features(SurveyMapContent.OVERLAY_SOURCE).map { it.id }
    assertTrue("navigation-line" in overlayIds)
    assertTrue("navigation-target" in overlayIds)
    assertIs<SurveyMarker.NavigationPill>(map.markers[SurveyMapIds.NAVIGATION])
    val user = assertIs<SurveyMarker.UserChip>(map.markers[SurveyMapIds.USER])
    assertEquals(SurveyMarker.UserChipMode.NAVIGATING, user.mode)
    assertTrue(user.bearing.isNotBlank())

    val hidden = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    assertNull(hidden.markers[SurveyMapIds.NAVIGATION])
  }

  @Test
  fun main_showsTheSelectedPlace_andAGpsChipWhenPanned() {
    val f = MapViewModelFixture()
    f.viewModel.selectPlace(assertNotNull(f.viewModel.findPlaceById(SEEDED_PLACE_ID)))
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    assertIs<SurveyMarker.Place>(map.markers[SurveyMapIds.place(SEEDED_PLACE_ID)])
    assertEquals(
      SurveyMarker.UserChip(SurveyMarker.UserChipMode.GPS),
      map.markers[SurveyMapIds.USER],
    )
  }

  @Test
  fun pendingFeatures_areMarkedPending() {
    val f = MapViewModelFixture()
    val map = SurveyMapContent.main(f.uiState, setOf(SEEDED_ENTITY_ID), showNavigation = false)
    val label = map.markers.getValue(SurveyMapIds.entity(SEEDED_ENTITY_ID))
    assertTrue(
      (label as? SurveyMarker.PinLabel)?.pending
        ?: assertIs<SurveyMarker.GeometryPill>(label).pending
    )
  }

  @Test
  fun entityRefForm_drawsOnlyTheCandidates() {
    val f = MapViewModelFixture()
    val candidate = f.uiState.entities.first { it.id == SEEDED_ENTITY_ID }
    val map =
      SurveyMapContent.entityRefForm(f.uiState, listOf(candidate), SEEDED_ENTITY_ID, emptySet())
    assertEquals(
      setOf(SEEDED_ENTITY_ID),
      map
        .features(SurveyMapContent.ENTITY_SOURCE)
        .mapNotNull { SurveyMapIds.entityIdOf(it.id) }
        .toSet(),
    )
    val geoPoint =
      SurveyMapContent.geoPointForm(f.uiState, null, emptySet(), isFollowingUser = false)
    assertTrue(geoPoint.features(SurveyMapContent.ENTITY_SOURCE).isNotEmpty())
  }

  @Test
  fun draftFeatures_closeAPolygonOnlyOnceComplete() {
    val twoVertices =
      SurveyMapContent.draftFeatures(DraftGeometry(MapDrawingKind.POLYGON, listOf(a, b)))
    assertIs<Geometry.LineString>(
      twoVertices.first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }.geometry
    )
    assertEquals(
      listOf(SurveyMapContent.draftVertexId(0), SurveyMapContent.draftVertexId(1)),
      twoVertices.filter { it.id != SurveyMapContent.DRAFT_SHAPE_ID }.map { it.id },
    )

    val complete = DraftGeometry(MapDrawingKind.POLYGON, listOf(a, b, c))
    assertTrue(complete.isComplete)
    assertIs<Geometry.Polygon>(
      SurveyMapContent.draftFeatures(complete)
        .first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }
        .geometry
    )

    // A point draws only its vertex.
    val point = SurveyMapContent.draftFeatures(DraftGeometry(MapDrawingKind.POINT, listOf(a)))
    assertEquals(listOf(SurveyMapContent.draftVertexId(0)), point.map { it.id })
  }

  @Test
  fun formGeometries_mapBackToTheirQuestion() {
    val line = FormGeometryOverlay("/data/track", "Track", MapDrawingKind.LINE, listOf(a, b))
    val features = SurveyMapContent.formGeometryFeatures(line)
    assertEquals(3, features.size)
    assertTrue(features.all { SurveyMapIds.formGeometryPathOf(it.id) == "/data/track" })

    val (marker, label) = assertNotNull(SurveyMapContent.formGeometryMarker(line))
    assertEquals(LatLng(0.0, 1.0), marker.position)
    assertEquals(SurveyMarker.FormQuestion("Track", MapDrawingKind.LINE), label)

    val untitledPoint = FormGeometryOverlay("/data/p", "", MapDrawingKind.POINT, listOf(a))
    assertEquals(1, SurveyMapContent.formGeometryFeatures(untitledPoint).size)
    assertEquals(
      SurveyMarker.FormQuestion("/data/p", MapDrawingKind.POINT),
      SurveyMapContent.formGeometryMarker(untitledPoint)?.second,
    )

    val empty = line.copy(vertices = emptyList())
    assertTrue(SurveyMapContent.formGeometryFeatures(empty).isEmpty())
    assertNull(SurveyMapContent.formGeometryMarker(empty))
  }

  @Test
  fun ids_roundTripToDomainIds() {
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.vertex("e1", 3)))
    assertEquals("e1", SurveyMapIds.entityIdOf(SurveyMapIds.entity("e1")))
    assertNull(SurveyMapIds.entityIdOf(SurveyMapIds.cluster("e1")))
    assertEquals("c1", SurveyMapIds.clusterIdOf(SurveyMapIds.cluster("c1")))
    assertNull(SurveyMapIds.clusterIdOf(SurveyMapIds.USER))
    assertTrue(SurveyMapIds.isPlace(SurveyMapIds.place("p1")))
    assertEquals(
      "/d/q",
      SurveyMapIds.formGeometryPathOf(SurveyMapIds.formGeometryVertex("/d/q", 2)),
    )
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.savedArea("a", "satellite")))
  }

  @Test
  fun savedImagery_drawsEachTypeOnlyOverTheOtherBasemap() {
    val area =
      SavedImageryArea("a", LngLatBounds(west = 0.0, south = 0.0, east = 1.0, north = 1.0), null)
    val imagery =
      SavedImageryOnMap(
        satellite = listOf(area),
        terrain = listOf(area),
        drawSatellite = true,
        drawTerrain = true,
      )
    val overTerrain = SurveyBasemaps.withSavedImagery(SurveyBasemaps.Streets, imagery)
    assertIs<RasterTileLayer>(overTerrain.overlays.single())
    val overSatellite = SurveyBasemaps.withSavedImagery(SurveyBasemaps.Satellite, imagery)
    assertIs<VectorLineOverlay>(overSatellite.overlays.single())

    val off = imagery.copy(drawSatellite = false, drawTerrain = false)
    assertSame(SurveyBasemaps.Streets, SurveyBasemaps.withSavedImagery(SurveyBasemaps.Streets, off))
  }

  @Test
  fun imagerySources_skipUnresolvedEarthEngineLayers() {
    val xyz = ImagerySource(id = "x", name = "X", urlTemplate = "https://x/{z}/{x}/{y}")
    val ee = ImagerySource(id = "ee", name = "EE", type = ImagerySourceType.EARTH_ENGINE)
    val base: Basemap.MapboxStyle = SurveyBasemaps.Streets
    assertSame(base, SurveyBasemaps.withImagerySources(base, emptyList()))
    assertEquals(
      listOf("https://x/{z}/{x}/{y}"),
      SurveyBasemaps.withImagerySources(base, listOf(xyz, ee)).overlays.map {
        (it as RasterTileLayer).urlTemplate
      },
    )
    assertEquals(
      listOf("https://x/{z}/{x}/{y}", "https://ee/{z}"),
      SurveyBasemaps.withImagerySources(base, listOf(xyz, ee), mapOf("ee" to "https://ee/{z}"))
        .overlays
        .map { (it as RasterTileLayer).urlTemplate },
    )
  }

  @Test
  fun satelliteBasemap_isUsedForTheSatelliteType() {
    val f = MapViewModelFixture()
    f.viewModel.selectBasemapType(BasemapType.SATELLITE)
    val map = SurveyMapContent.main(f.uiState, emptySet(), showNavigation = false)
    assertEquals(
      SurveyBasemaps.Satellite.styleUrl,
      (map.content.basemap as Basemap.MapboxStyle).styleUrl,
    )
  }

  @Test
  fun labelPosition_isHalfwayAlongALine_andTheRepresentativePointOtherwise() {
    assertEquals(b, EntityGeometry.labelPosition(Geometry.LineString(listOf(a, b, c))))
    assertEquals(a, EntityGeometry.labelPosition(Geometry.Point(a)))
    // A degenerate line of one repeated point labels at its end.
    assertEquals(a, EntityGeometry.labelPosition(Geometry.LineString(listOf(a, a))))
  }

  @Test
  fun vertices_ofLinesAndPolygons_butNotPoints() {
    assertEquals(listOf(a, b), EntityGeometry.vertices(Geometry.LineString(listOf(a, b))))
    assertEquals(
      listOf(a, b, c, a),
      EntityGeometry.vertices(Geometry.Polygon(listOf(listOf(a, b, c, a)))),
    )
    assertTrue(EntityGeometry.vertices(Geometry.Point(a)).isEmpty())
  }

  @Test
  fun framingInsets_keepMarginsOnLargeMaps_andScaleDownOnSmallOnes() {
    val large = framingInsets(DpSize(1000.dp, 800.dp), bottom = 200.dp, right = 100.dp)
    assertEquals(48.dp, large.left)
    assertEquals(148.dp, large.right)
    assertEquals(248.dp, large.bottom)

    val small = framingInsets(DpSize(100.dp, 100.dp), bottom = 200.dp)
    assertTrue(small.top + small.bottom <= 80.dp + 0.01.dp)
    assertTrue(small.left + small.right <= 80.dp + 0.01.dp)
  }
}
