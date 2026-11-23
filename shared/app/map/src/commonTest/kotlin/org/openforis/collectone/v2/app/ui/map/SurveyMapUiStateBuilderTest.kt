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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.map.LngLatBounds

class SurveyMapUiStateBuilderTest {
  private val data = seededMapData()

  private fun builder(canSaveMapAreas: Boolean = false) =
    SurveyMapUiStateBuilder(
      ClusterMapFeaturesUseCase(),
      ComputeWayfindingNavigationUseCase(),
      canSaveMapAreas,
    )

  @Test
  fun seededData_hasAnActiveSurveyWithMapFeatures() {
    assertTrue(data.activeSurveyId.isNotBlank())
    assertEquals(data.activeSurveyId, data.activeSurvey?.id)
    assertTrue(data.entities.isNotEmpty())
    // Submissions on features come first, then standalone ones.
    assertEquals(
      data.entities.flatMap { it.submissions } + data.content.standaloneSubmissions,
      data.allSubmissions,
    )
  }

  @Test
  fun activeSurvey_fallsBackToTheFirstSurvey_whenTheActiveIdIsUnknown() {
    assertEquals(data.surveys.first(), data.copy(activeSurveyId = "gone").activeSurvey)
    assertNull(SurveyMapData().activeSurvey)
  }

  @Test
  fun visibleMapEntities_followLayerVisibility() {
    val hiddenLayer = data.entities.first().layerId
    val hidden =
      data.copy(
        content =
          data.content.copy(
            mapLayers =
              data.mapLayers.map { if (it.id == hiddenLayer) it.copy(isVisible = false) else it }
          )
      )
    val state = builder().build(hidden, SurveyMapSession())
    assertEquals(data.entities.size, state.entities.size)
    assertTrue(state.visibleMapEntities.none { it.layerId == hiddenLayer })
    assertEquals(data.entities.count { it.layerId != hiddenLayer }, state.visibleMapEntities.size)
  }

  @Test
  fun countNoun_usesTheLayersNouns_onlyForASingleVisibleNamedDatasetLayer() {
    val layerId = data.entities.first().layerId
    fun withLayers(singular: String, plural: String, othersVisible: Boolean): SurveyMapData =
      data.copy(
        content =
          data.content.copy(
            mapLayers =
              data.mapLayers.map {
                if (it.id == layerId) {
                  it.copy(
                    isVisible = true,
                    sourceType = LayerSourceType.ENTITY_DATASET,
                    singularNoun = singular,
                    pluralNoun = plural,
                  )
                } else {
                  it.copy(isVisible = othersVisible)
                }
              }
          )
      )

    assertEquals(
      MapFeatureNoun.OfLayer("coffee parcel", "coffee parcels"),
      builder()
        .build(
          withLayers("coffee parcel", "coffee parcels", othersVisible = false),
          SurveyMapSession(),
        )
        .activeEntitiesCountNoun,
    )
    assertEquals(
      MapFeatureNoun.Generic,
      builder()
        .build(withLayers("coffee parcel", "", othersVisible = false), SurveyMapSession())
        .activeEntitiesCountNoun,
    )
    val severalLayers =
      data.mapLayers.count { layer -> data.entities.any { it.layerId == layer.id } }
    if (severalLayers > 1) {
      assertEquals(
        MapFeatureNoun.Generic,
        builder()
          .build(
            withLayers("coffee parcel", "coffee parcels", othersVisible = true),
            SurveyMapSession(),
          )
          .activeEntitiesCountNoun,
      )
    }
    assertEquals(
      MapFeatureNoun.Generic,
      builder()
        .build(data.copy(content = data.content.copy(entities = emptyList())), SurveyMapSession())
        .activeEntitiesCountNoun,
    )
  }

  @Test
  fun organizations_splitAllUsersImagery_fromTheSurveyOrganizations() {
    val allUsersSource = ImagerySource(id = "all", name = "All", urlTemplate = "https://a/{z}")
    val orgSource = ImagerySource(id = "org", name = "Org", urlTemplate = "https://o/{z}")
    val allUsers =
      Organization(
        id = Organization.ALL_USERS_ID,
        name = "All users",
        isSynthetic = true,
        imagerySources = listOf(allUsersSource),
      )
    val org = Organization(id = "org-1", name = "Coffee Co-op", imagerySources = listOf(orgSource))
    val survey = data.activeSurvey!!

    fun stateFor(organizationId: String?) =
      builder()
        .build(
          data.copy(
            surveys = listOf(survey.copy(organizationId = organizationId)),
            organizations = listOf(allUsers, org),
          ),
          SurveyMapSession(),
        )

    val inOrg = stateFor("org-1")
    assertEquals("Coffee Co-op", inOrg.activeSurveyOrganizationName)
    assertEquals(listOf(allUsersSource), inOrg.allUsersImagerySources)
    assertEquals(listOf(orgSource), inOrg.activeSurveyOrganizationImagerySources)

    // A survey shared with all users has no organization of its own to name.
    val shared = stateFor(Organization.ALL_USERS_ID)
    assertNull(shared.activeSurveyOrganizationName)
    assertTrue(shared.activeSurveyOrganizationImagerySources.isEmpty())
    assertEquals(listOf(allUsersSource), shared.allUsersImagerySources)
  }

  @Test
  fun sessionFlags_reachTheUiState() {
    val state =
      builder(canSaveMapAreas = true)
        .build(
          data,
          SurveyMapSession(hiddenDownloadedImagery = setOf(OfflineImageryType.TERRAIN)),
        )
    assertTrue(state.canSaveMapAreas)
    assertTrue(state.isDownloadedSatelliteVisible)
    assertFalse(state.isDownloadedTerrainVisible)
    assertFalse(builder().build(data, SurveyMapSession()).canSaveMapAreas)
  }

  @Test
  fun clusters_areEmptyZoomedIn_andMemoizedOnTheirInputs() {
    val builder = builder()
    assertTrue(builder.clustersFor(data, SurveyMapSession()).isEmpty())

    val zoomedOut = SurveyMapSession(mapZoomDelta = -3f)
    val clusters = builder.clustersFor(data, zoomedOut)
    assertTrue(clusters.isNotEmpty())
    assertEquals(
      data.entities.count { it.layerId in data.visibleLayerIds },
      clusters.sumOf { it.totalCount },
    )
    // Session changes that don't affect clustering reuse the result.
    assertSame(clusters, builder.clustersFor(data, zoomedOut.copy(isLayersSheetOpen = true)))
    assertNotSame(clusters, builder.clustersFor(data, zoomedOut.copy(mapZoomDelta = -4f)))
  }

  @Test
  fun findPlaceById_prefersTheSelectedPlace_thenTheNavigationTarget_thenLocalPlaces() {
    val local = data.localPlaces.first { it.id == SEEDED_PLACE_ID }
    assertEquals(local, findPlaceById(SEEDED_PLACE_ID, data, SurveyMapSession()))

    val target = local.copy(name = "Navigated")
    val selected = local.copy(name = "Selected")
    assertEquals(
      target,
      findPlaceById(SEEDED_PLACE_ID, data, SurveyMapSession(navigationTargetPlace = target)),
    )
    assertEquals(
      selected,
      findPlaceById(
        SEEDED_PLACE_ID,
        data,
        SurveyMapSession(lastSelectedPlace = selected, navigationTargetPlace = target),
      ),
    )
    // Snapshots resolve even after the search results no longer contain the place.
    val noPlaces = data.copy(localPlaces = emptyList())
    assertEquals(
      target,
      findPlaceById(SEEDED_PLACE_ID, noPlaces, SurveyMapSession(navigationTargetPlace = target)),
    )
    assertNull(findPlaceById(SEEDED_PLACE_ID, noPlaces, SurveyMapSession()))
  }

  @Test
  fun readyLayers_keepOnlyFinishedLayersOfTheType() {
    val bounds = LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40)
    fun area(id: String, vararg layers: OfflineAreaLayer) =
      OfflineMapArea(
        id,
        OfflineAreaName.Named(id),
        bounds,
        Instant.fromEpochSeconds(0),
        layers.toList(),
      )
    val areas =
      listOf(
        area(
          "ready",
          OfflineAreaLayer(OfflineImageryType.SATELLITE, OfflineAreaLayerStatus.READY),
          OfflineAreaLayer(OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.READY),
        ),
        area(
          "saving",
          OfflineAreaLayer(OfflineImageryType.SATELLITE, OfflineAreaLayerStatus.SAVING),
          OfflineAreaLayer(OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.READY),
        ),
        area(
          "terrainOnly",
          OfflineAreaLayer(OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.READY),
        ),
      )
    assertEquals(
      listOf("ready"),
      areas.readyLayers(OfflineImageryType.SATELLITE).map { it.id },
    )
    assertEquals(
      listOf("ready", "saving", "terrainOnly"),
      areas.readyLayers(OfflineImageryType.TERRAIN).map { it.id },
    )
  }
}
