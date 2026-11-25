/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.GeoPoint
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MapClusterFeatureItem
import org.openforis.collectone.v2.app.domain.model.MapFeatureKind
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.maxFramingZoom
import org.openforis.collectone.v2.app.domain.model.toClusterFeatures
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_features_count
import org.openforis.collectone.v2.app.ui.datacollection.formGeometryBounds
import org.openforis.collectone.v2.app.ui.datacollection.maxFramingZoom
import org.openforis.collectone.v2.app.ui.map.EntityGeometry
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapIds
import org.openforis.collectone.v2.app.ui.map.SurveyMarker
import org.openforis.collectone.v2.app.ui.map.mainSurveyMap
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.wayfindingBadgeText
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** The survey map: layers, camera, zoom, clustering, wayfinding, and map content ids. */
class MobileSurveyMapTest {
  @Test
  fun toggleLayerVisibility_filtersVisibleMapEntitiesAndDeselectsHiddenEntity() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    assertEquals(5, state.viewModels.surveyMap.uiState.value.visibleMapEntities.size)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.selectedEntityId)

    state.viewModels.surveyMap.selectEntity("00000007-640e-5ed7-9874-4d6b6717f21d")
    assertEquals(
      "00000007-640e-5ed7-9874-4d6b6717f21d",
      state.viewModels.dataCollection.uiState.value.selectedEntityId,
    )

    // Hide the "Smallholder Coffee Parcels" layer (`layer-coffee-parcels`)
    state.searchableListActions.toggleLayerVisibility("layer-coffee-parcels")

    // The 3 entities on `layer-coffee-parcels` should hide
    assertEquals(2, state.viewModels.surveyMap.uiState.value.visibleMapEntities.size)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.selectedEntity)

    // Re-enable layer
    state.searchableListActions.toggleLayerVisibility("layer-coffee-parcels")
    assertEquals(5, state.viewModels.surveyMap.uiState.value.visibleMapEntities.size)
  }

  @Test
  fun mapLayersAndBasemap_toggleIndependentlyViaLayersSheet() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    // 3 entity dataset layers; submissions are not shown as map layers
    assertEquals(3, state.viewModels.dashboard.uiState.value.entityDatasetLayers.size)
    assertEquals(3, state.viewModels.dashboard.uiState.value.visibleEntityDatasetLayers.size)

    // Toggle off the "Smallholder Coffee Parcels" map layer (`layer-coffee-parcels`)
    state.searchableListActions.toggleLayerVisibility("layer-coffee-parcels")
    assertEquals(2, state.viewModels.dashboard.uiState.value.visibleEntityDatasetLayers.size)

    // Selecting a submission geometry resolves its parent entity and submission
    state.viewModels.dataCollection.selectSubmissionGeometry("geom-sub-shade-201-w3")
    assertEquals(
      "0000000a-56d7-5c1d-9457-7457267120a3",
      state.viewModels.dataCollection.uiState.value.selectedEntity?.id,
    )
    assertEquals(
      "00000026-cca2-5594-b83f-02266f40048e",
      state.viewModels.dataCollection.uiState.value.selectedSubmission?.id,
    )

    // Select between Terrain (NORMAL) and Satellite basemaps
    assertEquals(BasemapType.NORMAL, state.viewModels.surveyMap.uiState.value.selectedBasemapType)
    state.viewModels.surveyMap.toggleBasemapType()
    assertEquals(
      BasemapType.SATELLITE,
      state.viewModels.surveyMap.uiState.value.selectedBasemapType,
    )
  }

  @Test
  fun mapCamera_autoCentersOnUserGpsByDefault_andShowsRecenterWhenDragged() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    // By default, camera follows user and keeps user's GPS location at center (0.50, 0.50)
    assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(0.50f, state.viewModels.surveyMap.uiState.value.userScreenNormalizedX, 0.0001f)
    assertEquals(0.50f, state.viewModels.surveyMap.uiState.value.userScreenNormalizedY, 0.0001f)

    // When the user's GPS position moves while isCameraFollowingUser is true,
    // the map automatically pans so the user's GPS dot stays centered at (0.50, 0.50)
    state.viewModels.surveyMap.updateUserGpsLocation(newNormalizedX = 0.64f, newNormalizedY = 0.38f)
    assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(
      0.50f,
      state.viewModels.surveyMap.uiState.value.userGpsNormalizedX +
        state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftX,
      0.0001f,
    )
    assertEquals(
      0.50f,
      state.viewModels.surveyMap.uiState.value.userGpsNormalizedY +
        state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftY,
      0.0001f,
    )

    // Dragging (panning) the map disengages auto-centering (showing the "Recenter" button)
    state.viewModels.surveyMap.panMap(deltaNormalizedX = 0.12f, deltaNormalizedY = -0.09f)
    assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(0.62f, state.viewModels.surveyMap.uiState.value.userScreenNormalizedX, 0.0001f)
    assertEquals(0.41f, state.viewModels.surveyMap.uiState.value.userScreenNormalizedY, 0.0001f)

    // Clicking "Recenter" re-engages auto-centering and brings the GPS location back to center
    state.viewModels.surveyMap.recenterMapOnUser()
    assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapPanOffsetX, 0.0001f)
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapPanOffsetY, 0.0001f)
    assertEquals(
      0.50f,
      state.viewModels.surveyMap.uiState.value.userGpsNormalizedX +
        state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftX,
      0.0001f,
    )
    assertEquals(
      0.50f,
      state.viewModels.surveyMap.uiState.value.userGpsNormalizedY +
        state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftY,
      0.0001f,
    )
  }

  @Test
  fun recenterMapOnEntity_adjustsPanOffset_centeringEntityAtTargetScreenY() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    val entity = state.viewModels.dataCollection.uiState.value.entities.first()
    val (lng, lat) = state.viewModels.surveyMap.resolveEntityLngLat(entity)
    val (entityX, entityY) =
      state.viewModels.surveyMap.uiState.value.anchor.toNormalized(LatLng(lat, lng)).let {
        it.first.toFloat() to it.second.toFloat()
      }

    assertEquals(0L, state.viewModels.surveyMap.uiState.value.entitySelectionEpoch)
    state.viewModels.surveyMap.selectEntity(entity.id)
    assertEquals(1L, state.viewModels.surveyMap.uiState.value.entitySelectionEpoch)
    assertEquals(entity.id, state.viewModels.dataCollection.uiState.value.selectedEntityId)

    // Recenter at standard 0.50f center
    state.viewModels.surveyMap.recenterMapOnEntity(entity, targetScreenY = 0.50f)
    assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(
      LocationLockState.PANNED,
      state.viewModels.surveyMap.uiState.value.locationLockState,
    )
    assertEquals(
      0.50f,
      entityX + state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftX,
      0.0001f,
    )
    assertEquals(
      0.50f,
      entityY + state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftY,
      0.0001f,
    )

    // Recenter adjusting for reduced viewport above expanded table (e.g. 0.26f)
    state.viewModels.surveyMap.recenterMapOnEntity(entity, targetScreenY = 0.26f)
    assertEquals(
      0.50f,
      entityX + state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftX,
      0.0001f,
    )
    assertEquals(
      0.26f,
      entityY + state.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftY,
      0.0001f,
    )

    // Selecting entity again increments entitySelectionEpoch
    state.viewModels.surveyMap.selectEntity(entity.id)
    assertEquals(2L, state.viewModels.surveyMap.uiState.value.entitySelectionEpoch)

    // Selecting from list also increments entitySelectionEpoch
    state.searchableListActions.selectEntityFromList(entity.id)
    assertEquals(3L, state.viewModels.surveyMap.uiState.value.entitySelectionEpoch)
  }

  @Test
  fun mapZoomControls_supportZoomInZoomOutAndReset() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertEquals("15.3z", state.viewModels.surveyMap.uiState.value.effectiveMapZoomLabel)
    val initialScale = state.viewModels.surveyMap.uiState.value.mapScaleBarSpec
    assertTrue(initialScale.distanceMeters > 0)
    assertTrue(initialScale.barWidthDp in 44f..114f)

    // Zoom in (+0.75) -> 16.1z
    state.viewModels.surveyMap.zoomInMap()
    assertEquals(0.75f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertEquals("16.1z", state.viewModels.surveyMap.uiState.value.effectiveMapZoomLabel)
    state.viewModels.surveyMap.zoomInMap()
    state.viewModels.surveyMap.zoomInMap()
    val zoomedInScale = state.viewModels.surveyMap.uiState.value.mapScaleBarSpec
    assertTrue(zoomedInScale.distanceMeters < initialScale.distanceMeters)

    // Zoom out
    state.viewModels.surveyMap.resetMapZoom()
    state.viewModels.surveyMap.zoomOutMap()
    state.viewModels.surveyMap.zoomOutMap()
    val zoomedOutScale = state.viewModels.surveyMap.uiState.value.mapScaleBarSpec
    assertTrue(zoomedOutScale.distanceMeters > initialScale.distanceMeters)

    // Reset zoom restores 0f delta (15.3z)
    state.viewModels.surveyMap.resetMapZoom()
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertEquals("15.3z", state.viewModels.surveyMap.uiState.value.effectiveMapZoomLabel)
    assertEquals(initialScale, state.viewModels.surveyMap.uiState.value.mapScaleBarSpec)
  }

  @Test
  fun straightLineNavigation_toEntityAndSubmission_computesBearingDistanceAndStepsUser() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    assertEquals(null, state.viewModels.surveyMap.uiState.value.activeNavigation)
    state.viewModels.surveyMap.updateUserGpsLocation(0.50f, 0.50f)

    // 1. Start straight-line navigation to a Geospatial Entity
    state.viewModels.surveyMap.startNavigationToEntity("00000007-640e-5ed7-9874-4d6b6717f21d")
    val entityNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertEquals(NavigationTargetKind.ENTITY, entityNav.targetKind)
    assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", entityNav.targetId)
    assertTrue(
      state.viewModels.surveyMap.uiState.value.isNavigatingToEntity(
        "00000007-640e-5ed7-9874-4d6b6717f21d"
      )
    )
    assertFalse(state.viewModels.surveyMap.uiState.value.isNavigatingToSubmission("sub-nyr-104-a"))
    assertTrue(entityNav.vector.distanceMeters > 0)
    assertTrue(entityNav.vector.bearingDegrees in 0..359)
    assertTrue(
      entityNav.vector.distance.unit in setOf(MeasurementUnit.METER, MeasurementUnit.KILOMETER)
    )

    // Verify the survey map draws the navigation line, target, and distance pill
    val entityNavMap = state.viewModels.mainSurveyMap(showNavigation = true)
    assertTrue(
      entityNavMap.overlayIds().containsAll(listOf("navigation-line", "navigation-target"))
    )
    val navPill = entityNavMap.markers[SurveyMapIds.NAVIGATION] as SurveyMarker.NavigationPill
    assertEquals(wayfindingBadgeText(entityNav.vector), navPill.text)

    // Verify Imperial unit formatting on active straight-line navigation
    state.viewModels.settings.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)
    val imperialNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertTrue(
      imperialNav.vector.distance.unit in setOf(MeasurementUnit.FOOT, MeasurementUnit.MILE),
      "Expected an Imperial distance but got: ${imperialNav.vector.distance}",
    )
    state.viewModels.settings.updateMeasurementUnits(MeasurementUnitSystem.METRIC)

    // 2. Simulate walking closer toward the navigation target until arrival
    val initialDist = entityNav.vector.distanceMeters
    state.viewModels.surveyMap.stepUserTowardNavigationTarget(stepFraction = 0.5f)
    val closerNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertTrue(closerNav.vector.distanceMeters < initialDist)

    repeat(10) { state.viewModels.surveyMap.stepUserTowardNavigationTarget(stepFraction = 0.5f) }
    val arrivedNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertTrue(arrivedNav.vector.isArrived)
    assertEquals(0, arrivedNav.vector.estimatedWalkMinutes)

    // 3. Switch straight-line navigation to a Form Submission
    state.viewModels.surveyMap.startNavigationToSubmission("00000026-cca2-5594-b83f-02266f40048e")
    val subNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertEquals(NavigationTargetKind.SUBMISSION, subNav.targetKind)
    assertEquals("00000026-cca2-5594-b83f-02266f40048e", subNav.targetId)
    assertTrue(
      state.viewModels.surveyMap.uiState.value.isNavigatingToSubmission(
        "00000026-cca2-5594-b83f-02266f40048e"
      )
    )
    assertFalse(
      state.viewModels.surveyMap.uiState.value.isNavigatingToEntity(
        "00000007-640e-5ed7-9874-4d6b6717f21d"
      )
    )
    assertNotNull(
      state.viewModels.surveyMap.distanceAndBearingToSubmission(
        "00000026-cca2-5594-b83f-02266f40048e"
      )
    )

    assertTrue(
      "navigation-line" in state.viewModels.mainSurveyMap(showNavigation = true).overlayIds()
    )

    // 4. Toggle / Stop straight-line navigation
    state.viewModels.surveyMap.toggleNavigationToSubmission("00000026-cca2-5594-b83f-02266f40048e")
    assertEquals(null, state.viewModels.surveyMap.uiState.value.activeNavigation)
    val stoppedMap = state.viewModels.mainSurveyMap(showNavigation = true)
    assertFalse("navigation-line" in stoppedMap.overlayIds())
    assertFalse(SurveyMapIds.NAVIGATION in stoppedMap.markers)

    // 5. Verify distance/wayfinding badge is computed ONLY for geometry fields in a submission
    val wave3Sub =
      assertNotNull(
        state.viewModels.dataCollection.uiState.value.allSubmissions.firstOrNull {
          it.id == "00000026-cca2-5594-b83f-02266f40048e"
        }
      )
    val geomField =
      assertNotNull(
        wave3Sub.fields.firstOrNull { it.questionName == "audit/canopy_sample_polygon" }
      )
    val nonGeomField =
      assertNotNull(wave3Sub.fields.firstOrNull { it.questionName == "surviving_saplings_count" })
    assertTrue(state.viewModels.surveyMap.isSubmissionFieldGeometry(wave3Sub.id, geomField))
    assertFalse(state.viewModels.surveyMap.isSubmissionFieldGeometry(wave3Sub.id, nonGeomField))
    assertNotNull(
      state.viewModels.surveyMap.distanceAndBearingToSubmissionField(wave3Sub.id, geomField)
    )
    assertNull(
      state.viewModels.surveyMap.distanceAndBearingToSubmissionField(wave3Sub.id, nonGeomField)
    )
  }

  @Test
  fun mapZoomOut_clustersMapFeaturesByMarkerSymbolIncludingNoSymbolGroupInClusterBalloon() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    // 1. At default zoom (mapZoomDelta == 0f), clustering is inactive; only the 5 entities are
    // included (form submission geometries are not displayed on the map or in cluster chips)
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertFalse(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    assertEquals(emptyList(), state.viewModels.surveyMap.uiState.value.mapFeatureClusters)
    assertEquals(
      5,
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .toClusterFeatures(state.viewModels.surveyMap.uiState.value.anchor)
        .size,
    )
    assertEquals(
      5,
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .toClusterFeatures(state.viewModels.surveyMap.uiState.value.anchor)
        .count {
          it.hasMarkerSymbol
        },
    )
    assertEquals(
      0,
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .toClusterFeatures(state.viewModels.surveyMap.uiState.value.anchor)
        .count {
          !it.hasMarkerSymbol
        },
    )

    // 2. Zooming out up to 2.25 levels (about 13z) keeps features unclustered; zooming out once
    // more (-3.0f) activates clustering and groups nearby features spatially
    repeat(3) { state.viewModels.surveyMap.zoomOutMap() }
    assertEquals(-2.25f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertFalse(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    assertEquals(emptyList(), state.viewModels.surveyMap.uiState.value.mapFeatureClusters)
    state.viewModels.surveyMap.zoomOutMap()
    assertEquals(-3.0f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertTrue(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    val initialClusters = state.viewModels.surveyMap.uiState.value.mapFeatureClusters
    assertTrue(initialClusters.size > 1)
    assertEquals(5, initialClusters.sumOf { it.totalCount })

    // 3. Zoom out further (-4.5f) so local clusters cover a broader area and merge into a single
    // survey-wide cluster balloon
    repeat(2) { state.viewModels.surveyMap.zoomOutMap() }
    assertEquals(-4.5f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertEquals(1, state.viewModels.surveyMap.uiState.value.mapFeatureClusters.size)
    val allCluster = state.viewModels.surveyMap.uiState.value.mapFeatureClusters.first()
    assertEquals(5, allCluster.totalCount)
    assertTrue(
      allCluster.diameterNormalized > initialClusters.maxOf { it.diameterNormalized },
      "Survey-wide cluster at -4.5f should cover a broader spatial area than local clusters at -3.0f",
    )

    // Verify grouping by marker symbol for entity geometries
    assertEquals(3, allCluster.symbolGroups.size)
    assertEquals(listOf("✓", "◐", "○"), allCluster.symbolGroups.map { it.markerSymbol })
    assertEquals(mapOf("✓" to 2, "◐" to 2, "○" to 1), allCluster.countsByMarkerSymbol)
    assertEquals(2, allCluster.countForSymbol("✓"))
    assertEquals(2, allCluster.countForSymbol("◐"))
    assertEquals(1, allCluster.countForSymbol("○"))
    assertEquals(0, allCluster.noMarkerSymbolCount)

    // 4. Verify an entity without a "marker-symbol" property groups into the no-marker-symbol
    // ("") group
    val rawUnmarkedEntity =
      state.viewModels.dataCollection.uiState.value.entities
        .first()
        .copy(
          id = "entity-unmarked-999",
          label = "Unmarked Plot 999",
          properties = mapOf("marker-color" to "#1565C0"),
        )
    assertFalse(rawUnmarkedEntity.hasMarkerSymbol)
    assertEquals("", rawUnmarkedEntity.rawMarkerSymbol)
    val customGrouped =
      groupClusterFeaturesByMarkerSymbol(
        state.viewModels.surveyMap.uiState.value.visibleMapEntities.toClusterFeatures(
          state.viewModels.surveyMap.uiState.value.anchor
        ) +
          MapClusterFeatureItem(
            id = rawUnmarkedEntity.id,
            kind = MapFeatureKind.ENTITY,
            label = rawUnmarkedEntity.label,
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            markerSymbol = rawUnmarkedEntity.rawMarkerSymbol,
            colorHex = rawUnmarkedEntity.colorHex,
            colorCss = rawUnmarkedEntity.markerColorCss,
          )
      )
    assertEquals(1, customGrouped.first { it.isNoSymbolGroup }.count)

    // 5. Verify feature count + feature states with 0 form submission geometries
    assertEquals(5, allCluster.siteCount)
    assertEquals(
      UiText.Plural(Res.plurals.app_map_features_count, 5, listOf(5)),
      state.viewModels.surveyMap.uiState.value.clusterCountText(allCluster.siteCount),
    )
    assertEquals(0, allCluster.submissionGeometryCount)
    assertEquals(listOf("✓", "◐", "○"), allCluster.siteSymbolGroups.map { it.markerSymbol })
    assertEquals(listOf(2, 2, 1), allCluster.siteStatusChipGroups.map { it.count })

    // When clustering is active, individual entities are omitted from the map
    val clusteredMap = state.viewModels.mainSurveyMap(showNavigation = true)
    assertTrue(clusteredMap.entityFeatures().isEmpty())
    assertTrue(clusteredMap.markers.keys.none { SurveyMapIds.entityIdOf(it) != null })

    // 6. Selecting the cluster balloon immediately zooms and centers to expand it
    state.viewModels.surveyMap.selectCluster(allCluster.id)
    assertEquals("5 map features", state.notices.notice.value)
    assertTrue(state.viewModels.surveyMap.uiState.value.mapZoomDelta > -4.5f)
    assertTrue(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    assertTrue(state.viewModels.surveyMap.uiState.value.mapFeatureClusters.size > 1)
    assertEquals(
      5,
      state.viewModels.surveyMap.uiState.value.mapFeatureClusters.sumOf { it.totalCount },
    )
    assertNull(state.viewModels.surveyMap.uiState.value.selectedClusterId)

    // 7. Resetting zoom exits clustering and restores individual features
    state.viewModels.surveyMap.resetMapZoom()
    assertEquals(0f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertFalse(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    assertNull(state.viewModels.surveyMap.uiState.value.selectedClusterId)
    val unclusteredMap = state.viewModels.mainSurveyMap(showNavigation = true)
    assertTrue(unclusteredMap.markers.keys.none { SurveyMapIds.clusterIdOf(it) != null })
    assertTrue(unclusteredMap.entityFeatures().isNotEmpty())
  }

  @Test
  fun resolveEntityLngLatBounds_coversLinesAndPolygonsAndCollapsesForPoints() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    val kinds = state.viewModels.dataCollection.uiState.value.entities.groupBy { it.geometryType }

    for (kind in listOf(GeometryType.POLYGON, GeometryType.LINE_STRING)) {
      val entity = kinds.getValue(kind).first()
      val bounds = checkNotNull(EntityGeometry.bounds(entity))
      val (lng, lat) = state.viewModels.surveyMap.resolveEntityLngLat(entity)
      assertTrue(LatLng(lat, lng) in bounds, "$kind bounds contain its position")
      assertTrue(bounds.east > bounds.west, "$kind bounds have a width")
    }

    kinds[GeometryType.POINT]?.firstOrNull()?.let { point ->
      val bounds = EntityGeometry.bounds(point)
      val (lng, lat) = state.viewModels.surveyMap.resolveEntityLngLat(point)
      assertEquals(LngLatBounds(west = lng, south = lat, east = lng, north = lat), bounds)
    }
  }

  @Test
  fun syncMapZoomDelta_followsFittedCameraAndExitsClustering() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    repeat(4) { state.viewModels.surveyMap.zoomOutMap() }
    assertTrue(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    state.viewModels.surveyMap.selectCluster(
      state.viewModels.surveyMap.uiState.value.mapFeatureClusters.first().id
    )

    // Fitting a selected feature zooms the camera in; clustering follows the camera.
    state.viewModels.surveyMap.syncMapZoomDelta(1.2f)
    assertEquals(1.2f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
    assertFalse(state.viewModels.surveyMap.uiState.value.isMapClusteringActive)
    assertEquals(null, state.viewModels.surveyMap.uiState.value.selectedClusterId)

    // NaN (no camera change, e.g. re-centering only) leaves the zoom untouched.
    state.viewModels.surveyMap.syncMapZoomDelta(Float.NaN)
    assertEquals(1.2f, state.viewModels.surveyMap.uiState.value.mapZoomDelta)
  }

  @Test
  fun surveyMap_marksOnlyEntitiesWithOutboxMutationsAsPending() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    val pendingIds = state.viewModels.dashboard.uiState.value.pendingUploadEntityIds
    val features =
      state.viewModels.mainSurveyMap(showNavigation = true).entityFeatures().associateBy { it.id }

    val entities = state.viewModels.surveyMap.uiState.value.visibleMapEntities
    // The old logic keyed off the ○ workflow status, so these would have been drawn as synced.
    assertTrue(entities.any { !it.isNotStarted && it.id in pendingIds })
    entities.forEach { ent ->
      val feature = assertNotNull(features[SurveyMapIds.entity(ent.id)])
      val variant = feature.properties.getValue(SurveyMapContent.PROP_VARIANT)
      assertEquals(
        ent.id in pendingIds,
        variant == SurveyMapContent.Variant.PENDING ||
          variant == SurveyMapContent.Variant.SELECTED_PENDING,
        "Unexpected variant $variant for ${ent.id} (markerSymbol=${ent.markerSymbol})",
      )
    }
  }

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  private fun SurveyMap.overlayIds() =
    content.sources.first { it.id == SurveyMapContent.OVERLAY_SOURCE }.features.map { it.id }

  @Test
  fun surveyMapIds_formGeometryIdsRoundTrip_andDontCollideWithOtherIds() {
    val path = "/data/site/plot_boundary"
    val id = SurveyMapIds.formGeometry(path)
    assertEquals(path, SurveyMapIds.formGeometryPathOf(id))
    assertEquals(path, SurveyMapIds.formGeometryPathOf(SurveyMapIds.formGeometryVertex(path, 3)))
    assertNull(SurveyMapIds.entityIdOf(id))
    assertNull(SurveyMapIds.clusterIdOf(id))
    assertFalse(SurveyMapIds.isPlace(id))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.entity("form-geometry:x")))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.cluster("c1")))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapContent.DRAFT_SHAPE_ID))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.USER))
  }

  @Test
  fun formGeometryBounds_coverAllVertices_andAreNullWhenEmpty() {
    assertNull(formGeometryBounds(emptyList()))
    val bounds =
      assertNotNull(
        formGeometryBounds(
          listOf(
            GeoPoint(latitude = 1.0, longitude = 10.0),
            GeoPoint(latitude = -2.0, longitude = 12.0),
            GeoPoint(latitude = 0.5, longitude = 8.0),
          )
        )
      )
    assertEquals(LngLatBounds(west = 8.0, south = -2.0, east = 12.0, north = 1.0), bounds)
    assertEquals(16.0, MapDrawingKind.POINT.maxFramingZoom)
    assertEquals(17.0, MapDrawingKind.LINE.maxFramingZoom)
    assertEquals(17.0, MapDrawingKind.POLYGON.maxFramingZoom)
  }
}
