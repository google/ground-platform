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
package org.openforis.collectone.v2.server.service

import com.squareup.wire.ofEpochSecond
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetImpactAggregatesRequest
import openforis.collectone.v1.data.GetMediaDownloadUrlRequest
import openforis.collectone.v1.data.ImpactAggregate
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.PlaceRecord
import openforis.collectone.v1.data.PrepareMediaUploadRequest
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.userdata.SurveySeed
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore

/**
 * The reporting RPCs of `DataService` ([DataReports]) and its media URLs: who sees impact events
 * and mutation logs, how impact aggregates add up, and place search. Calls without a caller run as
 * the development caller, which has administrator access.
 */
class DataReportsTest {
  private val backend = UserDataBackend(InMemoryUserDataStore(seed))

  private suspend fun <T> asCaller(caller: Caller, block: suspend () -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      block()
    }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val error = assertFailsWith<StatusException> { block() }
    assertEquals(Status.Code.PERMISSION_DENIED, error.status.code)
  }

  @Test
  fun places_matchTheirNameOrRegion_ignoringCaseAndSurroundingSpace() = runTest {
    suspend fun search(query: String) =
      backend.ListPlaces(ListPlacesRequest(query = query)).places.map { it.id }

    assertEquals(listOf("nairobi", "nyeri", "hanoi"), search(""))
    assertEquals(listOf("nairobi", "nyeri"), search("  KENYA "))
    assertEquals(listOf("hanoi"), search("han"))
    assertEquals(emptyList(), search("lima"))
  }

  @Test
  fun impactAggregates_addUpLiveFeatures_ofActiveSurveys_inTheRequestedScope() = runTest {
    suspend fun aggregate(request: GetImpactAggregatesRequest) =
      backend.GetImpactAggregates(request).aggregates.single()

    val global = aggregate(GetImpactAggregatesRequest())
    val survey = aggregate(GetImpactAggregatesRequest(survey_id = SURVEY))
    val organization = aggregate(GetImpactAggregatesRequest(organization_id = OTHER_ORGANIZATION))

    // Deleted features and the features of trashed or deleted surveys don't count.
    assertEquals(ImpactAggregate.ScopeType.GLOBAL, global.scope_type)
    assertEquals(3L to 6.5, global.feature_count to global.area_ha)
    assertEquals(ImpactAggregate.ScopeType.SURVEY, survey.scope_type)
    assertEquals(SURVEY, survey.scope_id)
    assertEquals(2L to 3.5, survey.feature_count to survey.area_ha)
    assertEquals(ImpactAggregate.ScopeType.ORGANIZATION, organization.scope_type)
    assertEquals(OTHER_ORGANIZATION, organization.scope_id)
    assertEquals(1L to 3.0, organization.feature_count to organization.area_ha)
  }

  @Test
  fun impactAggregates_onlyCountSurveysTheCallerMayReportOn() = runTest {
    val asViewer = asCaller(VIEWER) { backend.GetImpactAggregates(GetImpactAggregatesRequest()) }
    val asOrganizer =
      asCaller(ORGANIZER) { backend.GetImpactAggregates(GetImpactAggregatesRequest()) }

    assertEquals(0L, asViewer.aggregates.single().feature_count)
    assertEquals(2L, asOrganizer.aggregates.single().feature_count)
  }

  @Test
  fun mutationLogs_listOnlyTheSurveysTheCallerCanView() = runTest {
    suspend fun logIds(request: ListMutationLogsRequest = ListMutationLogsRequest()) =
      backend.ListMutationLogs(request).mutation_logs.map { it.id }

    assertEquals(setOf("log-survey", "log-other", "log-unscoped"), logIds().toSet())
    assertEquals(listOf("log-survey"), asCaller(VIEWER) { logIds() })
    assertEquals(
      listOf("log-survey"),
      asCaller(VIEWER) { logIds(ListMutationLogsRequest(survey_id = SURVEY)) },
    )
    assertDenied { asCaller(VIEWER) { logIds(ListMutationLogsRequest(survey_id = OTHER_SURVEY)) } }
  }

  @Test
  fun impactEvents_needAScopeTheCallerCanSee_unlessTheCallerIsAnAdministrator() = runTest {
    val scoped = ImpactEvent(event_id = testId("event-scoped"), survey_id = SURVEY)
    val unscoped = ImpactEvent(event_id = testId("event-unscoped"))

    assertDenied {
      asCaller(VIEWER) {
        backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(unscoped)))
      }
    }
    assertDenied {
      asCaller(VIEWER) {
        backend.RecordImpactEvents(
          RecordImpactEventsRequest(events = listOf(scoped.copy(survey_id = OTHER_SURVEY)))
        )
      }
    }
    asCaller(VIEWER) {
      backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(scoped)))
    }
    backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(unscoped)))

    // Viewers record events but don't see reports; organizers see their survey's.
    suspend fun eventIds() =
      backend.ListImpactEvents(ListImpactEventsRequest()).events.map { it.event_id }
    assertEquals(emptyList(), asCaller(VIEWER) { eventIds() })
    assertEquals(listOf(scoped.event_id), asCaller(ORGANIZER) { eventIds() })
    assertEquals(listOf(scoped.event_id, unscoped.event_id), eventIds())
  }

  @Test
  fun recordingNoImpactEvents_writesNothing() = runTest {
    val response = backend.RecordImpactEvents(RecordImpactEventsRequest())

    assertEquals(emptyList(), response.events)
    assertEquals(emptyList(), backend.ListImpactEvents(ListImpactEventsRequest()).events)
  }

  @Test
  fun mediaUploads_needADataCollector_andDownloadsAViewer() = runTest {
    assertDenied {
      asCaller(VIEWER) {
        backend.PrepareMediaUpload(
          PrepareMediaUploadRequest(survey_id = SURVEY, sha256_hash = "ab")
        )
      }
    }
    val upload =
      asCaller(COLLECTOR) {
        backend.PrepareMediaUpload(
          PrepareMediaUploadRequest(survey_id = SURVEY, sha256_hash = "ab")
        )
      }
    val download =
      asCaller(VIEWER) {
        backend.GetMediaDownloadUrl(
          GetMediaDownloadUrlRequest(survey_id = SURVEY, media_uri = upload.media_uri)
        )
      }

    assertEquals("gs://collect-one-media/$SURVEY/ab", upload.media_uri)
    assertEquals(
      "https://storage.collect-one.example.org/download/$SURVEY/${upload.media_uri}",
      download.download_url,
    )
  }

  private companion object {
    val ORGANIZATION = testId("org-reports")
    val OTHER_ORGANIZATION = testId("org-reports-other")
    val SURVEY = testId("survey-reports")
    val OTHER_SURVEY = testId("survey-reports-other")
    val TRASHED_SURVEY = testId("survey-reports-trashed")
    val DELETED_SURVEY = testId("survey-reports-deleted")

    private fun caller(name: String) =
      Caller(userId = "uid-$name", email = "$name@example.org", emailVerified = true)

    val VIEWER = caller("viewer")
    val COLLECTOR = caller("collector")
    val ORGANIZER = caller("organizer")

    private fun entry(caller: Caller, role: Role) =
      AclEntry(
        email = caller.email,
        role = role,
        invitation_status = AclEntry.InvitationStatus.ACCEPTED,
      )

    private fun entity(surveyId: String, name: String, areaHa: Double, deleted: Boolean = false) =
      EntityRecord(
        survey_id = surveyId,
        entity_id = testId("entity-$name"),
        area_hectares = areaHa,
        is_deleted = deleted,
      )

    private fun surveySeed(
      surveyId: String,
      organizationId: String,
      entities: List<EntityRecord>,
      acl: List<AclEntry> = emptyList(),
      state: SurveyState = SurveyState.PUBLISHED,
      trashed: Boolean = false,
    ) =
      SurveySeed(
        survey =
          SurveyDef(
            survey_id = surveyId,
            organization_id = organizationId,
            state = state,
            trash_time = if (trashed) ofEpochSecond(1_767_225_600L, 0L) else null,
          ),
        acl = SurveyAcl(survey_id = surveyId, entries = acl),
        entities = entities,
      )

    val seed =
      UserDataSeed(
        surveys =
          listOf(
            surveySeed(
              SURVEY,
              ORGANIZATION,
              listOf(
                entity(SURVEY, "a", 1.5),
                entity(SURVEY, "b", 2.0),
                entity(SURVEY, "gone", 10.0, deleted = true),
              ),
              acl =
                listOf(
                  entry(VIEWER, Role.VIEWER),
                  entry(COLLECTOR, Role.DATA_COLLECTOR),
                  entry(ORGANIZER, Role.SURVEY_ORGANIZER),
                ),
            ),
            surveySeed(OTHER_SURVEY, OTHER_ORGANIZATION, listOf(entity(OTHER_SURVEY, "c", 3.0))),
            surveySeed(
              TRASHED_SURVEY,
              ORGANIZATION,
              listOf(entity(TRASHED_SURVEY, "d", 100.0)),
              trashed = true,
            ),
            surveySeed(
              DELETED_SURVEY,
              ORGANIZATION,
              listOf(entity(DELETED_SURVEY, "e", 100.0)),
              state = SurveyState.DELETED,
            ),
          ),
        places =
          listOf(
            PlaceRecord(id = "nairobi", name = "Nairobi", region_subtitle = "Kenya"),
            PlaceRecord(id = "nyeri", name = "Nyeri", region_subtitle = "Central, Kenya"),
            PlaceRecord(id = "hanoi", name = "Hà Nội", region_subtitle = "Viet Nam (Hanoi)"),
          ),
        mutation_logs =
          listOf(
            MutationLogRecord(id = "log-survey", survey_id = SURVEY),
            MutationLogRecord(id = "log-other", survey_id = OTHER_SURVEY),
            MutationLogRecord(id = "log-unscoped"),
          ),
      )
  }
}
