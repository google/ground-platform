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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * [SurveyRepositoryImpl]'s per-survey writes act on the active survey only, and its sync status
 * updates keep a map feature's status consistent with its submissions'.
 */
class SurveyRepositoryImplTest {
  private val store = InMemoryLocalStore()
  private val repository = SurveyRepositoryImpl(store)

  private fun submission(
    id: String,
    entityId: String = "",
    status: SyncStatus = SyncStatus.SYNCED,
  ) =
    SubmissionPreviewItem(
      id = id,
      entityId = entityId,
      formId = "form",
      formTitle = "Plot visit",
      formVersion = "1",
      collectorName = "Ana",
      collectorEmail = "ana@example.org",
      fields = emptyList(),
      syncStatus = status,
    )

  private fun entity(id: String, vararg submissions: SubmissionPreviewItem) =
    GeospatialEntityItem(
      id = id,
      label = id,
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "layer",
      geoId = id,
      geometry = null,
      areaHectares = 0.0,
      perimeterMeters = 0,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = submissions.toList(),
    )

  private fun layer(id: String, visible: Boolean) =
    MapLayerItem(
      id = id,
      label = id,
      colorHex = 0xFF2E7D32L,
      geometryType = null,
      isVisible = visible,
    )

  private fun survey(id: String) =
    SurveyPreviewItem(
      id = id,
      title = id,
      description = "",
      location = "",
      isDownloaded = true,
      entityCount = 0,
    )

  /** Two surveys, `active` being the active one. */
  private fun givenActiveSurvey() = runNow {
    repository.setSurveys(listOf(survey("active"), survey("other")))
    repository.setActiveSurveyId("other")
    repository.setMapLayers(listOf(layer("other-layer", visible = true)))
    repository.setActiveSurveyId("active")
  }

  @Test
  fun toggleLayerVisibility_flipsOnlyThatLayerOfTheActiveSurvey() {
    givenActiveSurvey()
    runNow {
      repository.setMapLayers(listOf(layer("a", visible = true), layer("b", visible = false)))
      repository.toggleLayerVisibility("b")
    }

    assertEquals(listOf(true, true), runDirect { repository.getMapLayers() }.map { it.isVisible })
    runNow { repository.setActiveSurveyId("other") }
    assertEquals(listOf(true), runDirect { repository.getMapLayers() }.map { it.isVisible })
  }

  @Test
  fun setAllLayersVisible_hidesEveryLayer() {
    givenActiveSurvey()
    runNow {
      repository.setMapLayers(listOf(layer("a", visible = true), layer("b", visible = true)))
      repository.setAllLayersVisible(false)
    }

    assertEquals(listOf(false, false), runDirect { repository.getMapLayers() }.map { it.isVisible })
  }

  @Test
  fun updateEntitySyncStatus_marksTheFeatureAndItsSubmissions() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(listOf(entity("e1", submission("s1", "e1"), submission("s2", "e1"))))
      repository.updateEntitySyncStatus("e1", SyncStatus.UPLOADING)
    }

    val updated = runDirect { repository.getEntities() }.single()
    assertEquals(SyncStatus.UPLOADING, updated.syncStatus)
    assertEquals(
      listOf(SyncStatus.UPLOADING, SyncStatus.UPLOADING),
      updated.submissions.map { it.syncStatus },
    )
  }

  @Test
  fun updateEntitySyncStatus_ofAnUnknownFeature_changesNothing() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(listOf(entity("e1")))
      repository.updateEntitySyncStatus("missing", SyncStatus.FAILED)
    }

    assertEquals(SyncStatus.SYNCED, runDirect { repository.getEntities() }.single().syncStatus)
  }

  @Test
  fun updateSubmissionSyncStatus_ofALinkedSubmission_rederivesTheFeatureStatus() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(listOf(entity("e1", submission("s1", "e1"), submission("s2", "e1"))))
      repository.updateSubmissionSyncStatus("s2", SyncStatus.FAILED)
    }

    val updated = runDirect { repository.getEntities() }.single()
    assertEquals(SyncStatus.FAILED, updated.syncStatus)
    assertEquals(
      listOf(SyncStatus.SYNCED, SyncStatus.FAILED),
      updated.submissions.map { it.syncStatus },
    )
  }

  @Test
  fun updateSubmissionSyncStatus_ofAStandaloneSubmission_updatesOnlyIt() {
    givenActiveSurvey()
    runNow {
      repository.setStandaloneSubmissions(listOf(submission("s1"), submission("s2")))
      repository.setEntities(listOf(entity("e1", submission("s3", "e1"))))
      repository.updateSubmissionSyncStatus("s1", SyncStatus.UPLOADING)
    }

    assertEquals(
      listOf(SyncStatus.UPLOADING, SyncStatus.SYNCED),
      runDirect { repository.getStandaloneSubmissions() }.map { it.syncStatus },
    )
    assertEquals(SyncStatus.SYNCED, runDirect { repository.getEntities() }.single().syncStatus)
  }

  @Test
  fun retryFailedUploadsForEntity_requeuesOnlyTheFailedSubmissions() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(
        listOf(entity("e1", submission("s1", "e1", SyncStatus.FAILED), submission("s2", "e1")))
      )
      repository.retryFailedUploadsForEntity("e1")
    }

    val updated = runDirect { repository.getEntities() }.single()
    assertEquals(SyncStatus.UPLOADING, updated.syncStatus)
    assertEquals(
      listOf(SyncStatus.UPLOADING, SyncStatus.SYNCED),
      updated.submissions.map { it.syncStatus },
    )
  }

  @Test
  fun retryFailedUploadsForEntity_ofAFailedFeatureWithoutSubmissions_requeuesTheFeature() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(listOf(entity("e1").copy(syncStatus = SyncStatus.FAILED)))
      repository.retryFailedUploadsForEntity("e1")
    }

    assertEquals(SyncStatus.UPLOADING, runDirect { repository.getEntities() }.single().syncStatus)
  }

  @Test
  fun markAllSynced_marksEveryFeatureAndSubmission() {
    givenActiveSurvey()
    runNow {
      repository.setEntities(listOf(entity("e1", submission("s1", "e1", SyncStatus.FAILED))))
      repository.setStandaloneSubmissions(listOf(submission("s2", status = SyncStatus.UPLOADING)))
      repository.markAllSynced()
    }

    val entity = runDirect { repository.getEntities() }.single()
    assertEquals(SyncStatus.SYNCED, entity.syncStatus)
    assertEquals(SyncStatus.SYNCED, entity.submissions.single().syncStatus)
    assertEquals(
      SyncStatus.SYNCED,
      runDirect { repository.getStandaloneSubmissions() }.single().syncStatus,
    )
  }

  @Test
  fun setSurveyStateAndOutcome_updateTheListEntry() {
    givenActiveSurvey()
    val outcome =
      SurveyOutcome(
        outcomes = setOf(SurveyOutcomeKind.NOT_YET),
        answeredAt = "2026-01-03T00:00:00Z",
        answeredBy = "ana@example.org",
      )

    runNow {
      repository.setSurveyState(
        "other",
        SurveyLifecycleState.CLOSED,
        closedAt = "2026-01-02T00:00:00Z",
      )
      repository.setSurveyOutcome("other", outcome)
    }

    val other = runDirect { repository.getSurveys() }.first { it.id == "other" }
    assertEquals(SurveyLifecycleState.CLOSED, other.state)
    assertEquals("2026-01-02T00:00:00Z", other.closedAt)
    assertEquals(outcome, other.outcome)
  }

  @Test
  fun setSurveyState_ofAnUnknownSurvey_returnsNull() {
    givenActiveSurvey()

    assertNull(runNow { repository.setSurveyState("missing", SurveyLifecycleState.CLOSED, null) })
  }

  @Test
  fun deleteSurvey_ofTheOpenSurvey_closesIt() {
    givenActiveSurvey()

    runNow { repository.deleteSurvey("active") }

    assertNull(repository.observeOpenSurveyId().value)
    assertEquals(listOf("other"), runDirect { repository.getSurveys() }.map { it.id })
  }

  @Test
  fun observeSurveyContent_combinesTheSurveysStoredParts() {
    givenActiveSurvey()
    runNow {
      repository.setMapLayers(listOf(layer("a", visible = true)))
      repository.setEntities(listOf(entity("e1")))
      repository.setStandaloneSubmissions(listOf(submission("s1")))
    }

    val content = runNow { repository.observeSurveyContent("active").first() }

    assertEquals(listOf("a"), content.mapLayers.map { it.id })
    assertEquals(listOf("e1"), content.entities.map { it.id })
    assertEquals(listOf("s1"), content.standaloneSubmissions.map { it.id })
    assertEquals(emptyList(), content.forms)
  }

  @Test
  fun setEntitiesAndLayerVisibility_refreshSurveyThumbnailFeatures() {
    givenActiveSurvey()
    val pointEntity =
      entity("e1").copy(layerId = "a", geometry = Geometry.Point(LatLng(-3.385, 36.682)))
    runNow {
      repository.setMapLayers(listOf(layer("a", visible = true), layer("b", visible = true)))
      repository.setEntities(listOf(pointEntity))
    }

    val afterAdd = runDirect { repository.getSurveys() }.first { it.id == "active" }
    assertEquals(1, afterAdd.entityCount)
    assertEquals(listOf("e1"), afterAdd.thumbnail.features.map { it.id })
    val urlWithFeature = afterAdd.thumbnail.staticImageUrl("pk.test")

    val secondPoint =
      entity("e2").copy(layerId = "b", geometry = Geometry.Point(LatLng(-3.395, 36.692)))
    runNow { repository.upsertEntities(listOf(secondPoint)) }

    val afterUpsert = runDirect { repository.getSurveys() }.first { it.id == "active" }
    assertEquals(2, afterUpsert.entityCount)
    assertEquals(listOf("e1", "e2"), afterUpsert.thumbnail.features.map { it.id })
    val urlWithTwoFeatures = afterUpsert.thumbnail.staticImageUrl("pk.test")
    assertNotEquals(urlWithFeature, urlWithTwoFeatures)

    // Hiding layer "b" refreshes the survey thumbnail to show only visible layer "a".
    runNow { repository.toggleLayerVisibility("b") }
    val afterHide = runDirect { repository.getSurveys() }.first { it.id == "active" }
    assertEquals(listOf("e1"), afterHide.thumbnail.features.map { it.id })
    assertTrue(afterHide.thumbnail.staticImageUrl("pk.test") != urlWithTwoFeatures)
  }
}
