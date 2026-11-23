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

import io.grpc.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.sync.ScopeCursor
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncScope

/** `SyncChanges` answers each scope exactly like its `List*` RPC, with interchangeable tokens. */
class SyncChangesTest {
  private val backend = sampleBackend()

  private fun cursor(scope: SyncScope, syncToken: String = "", pageToken: String = "") =
    ScopeCursor(scope = scope, sync_token = syncToken, page_token = pageToken)

  @Test
  fun fullSync_matchesTheListRpcs() = runTest {
    val all = SyncScope.All()
    val response =
      backend.SyncChanges(
        SyncChangesRequest(
          cursors =
            listOf(
              cursor(SyncScope(organizations = all)),
              cursor(SyncScope(libraries = all)),
              cursor(SyncScope(survey_overviews = all)),
              cursor(SyncScope(survey_entities = KENYA)),
              cursor(SyncScope(survey_submissions = KENYA)),
              cursor(SyncScope(impact_events = all)),
            )
        )
      )

    val orgs = response.scopes[0]
    val libs = response.scopes[1]
    val overviews = response.scopes[2]
    val entities = response.scopes[3]
    val submissions = response.scopes[4]
    val events = response.scopes[5]
    assertEquals(
      backend.ListOrganizations(ListOrganizationsRequest()).organizations,
      orgs.records!!.organizations,
    )
    assertEquals(backend.ListLibraries(ListLibrariesRequest()).libraries, libs.records!!.libraries)
    assertEquals(
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews,
      overviews.records!!.survey_overviews,
    )
    assertEquals(
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).entities,
      entities.records!!.entities,
    )
    assertEquals(
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)).submissions,
      submissions.records!!.submissions,
    )
    assertEquals(
      backend.ListImpactEvents(ListImpactEventsRequest()).events,
      events.records!!.impact_events,
    )
    response.scopes.forEach {
      assertEquals(0, it.error_code)
      assertTrue(it.next_sync_token.isNotEmpty())
      assertEquals("", it.next_page_token)
    }
    // Survey definitions aren't inlined unless asked for.
    assertTrue(overviews.records!!.survey_definitions.isEmpty())
  }

  @Test
  fun includeSurveyDefinitions_inlinesSavedRevisions() = runTest {
    val response =
      backend.SyncChanges(
        SyncChangesRequest(
          cursors = listOf(cursor(SyncScope(survey_overviews = SyncScope.All()))),
          include_survey_definitions = true,
        )
      )

    val records = response.scopes.single().records!!
    val live = records.survey_overviews.filter { it.state != SurveyState.DELETED }
    assertEquals(live.map { it.survey_id }.toSet(), records.survey_definitions.keys)
    assertEquals(
      backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)),
      records.survey_definitions[KENYA],
    )
  }

  @Test
  fun tokensAreInterchangeableWithTheListRpcs() = runTest {
    val listToken = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-sync-changes",
              entity_mutation =
                EntityMutation(
                  entity_id = testId("entity-sync-changes"),
                  dataset_id = "coffee_parcels",
                  upsert =
                    EntityRecord(
                      entity_id = testId("entity-sync-changes"),
                      dataset_id = "coffee_parcels",
                      label = "New plot",
                    ),
                ),
            )
          ),
      )
    )

    val changes =
      backend
        .SyncChanges(
          SyncChangesRequest(
            cursors = listOf(cursor(SyncScope(survey_entities = KENYA), syncToken = listToken))
          )
        )
        .scopes
        .single()

    assertEquals(listOf("New plot"), changes.records!!.entities.map { it.label })
    // The returned token works with ListEntities, which then has nothing new.
    val after =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, sync_token = changes.next_sync_token)
      )
    assertTrue(after.entities.isEmpty())
  }

  @Test
  fun scopeFailures_areReportedPerScope() = runTest {
    val response =
      backend.SyncChanges(
        SyncChangesRequest(
          cursors =
            listOf(
              cursor(SyncScope(survey_entities = KENYA), syncToken = "not-a-token"),
              cursor(SyncScope(organizations = SyncScope.All())),
              cursor(SyncScope()),
            )
        )
      )

    val (badToken, orgs, noScope) = response.scopes
    assertEquals(Status.Code.INVALID_ARGUMENT.value(), badToken.error_code)
    assertEquals(0, orgs.error_code)
    assertTrue(orgs.records!!.organizations.isNotEmpty())
    assertEquals(Status.Code.INVALID_ARGUMENT.value(), noScope.error_code)
  }

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
  }
}
