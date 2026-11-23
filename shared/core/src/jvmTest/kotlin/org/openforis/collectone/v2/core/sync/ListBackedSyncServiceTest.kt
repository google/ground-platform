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
package org.openforis.collectone.v2.core.sync

import java.lang.reflect.Proxy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.data.ListImpactEventsResponse
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.ListSubmissionsResponse
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.ListLibrariesResponse
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.ListSurveyOverviewsResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.sync.ScopeCursor
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncScope

class ListBackedSyncServiceTest {

  /** Thrown by fakes for failures the service should report on the cursor, as `NOT_FOUND` (5). */
  private class ScopeFailure(message: String) : RuntimeException(message)

  /**
   * A fake of a generated service interface that answers the RPCs in [answers] (by method name) and
   * fails every other call. The generated `suspend` methods take a trailing continuation; the fakes
   * answer synchronously, so returning the response is a valid result of the suspending call.
   */
  private inline fun <reified T : Any> fake(answers: Map<String, (Any) -> Any>): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
      val answer = answers[method.name] ?: error("Unexpected call to ${method.name}")
      answer(args!!.first())
    } as T

  private val requests = mutableListOf<Any>()

  private fun service(
    survey: Map<String, (Any) -> Any> = emptyMap(),
    organization: Map<String, (Any) -> Any> = emptyMap(),
    library: Map<String, (Any) -> Any> = emptyMap(),
    data: Map<String, (Any) -> Any> = emptyMap(),
  ) =
    ListBackedSyncService(
      surveyService = fake<SurveyServiceServer>(survey.recording()),
      organizationService = fake<OrganizationServiceServer>(organization.recording()),
      libraryService = fake<LibraryServiceServer>(library.recording()),
      dataService = fake<DataServiceServer>(data.recording()),
      scopeErrorCode = { if (it is ScopeFailure) NOT_FOUND else null },
    )

  private fun Map<String, (Any) -> Any>.recording(): Map<String, (Any) -> Any> =
    mapValues { (_, answer) ->
      { request: Any ->
        requests += request
        answer(request)
      }
    }

  private fun cursor(scope: SyncScope, syncToken: String = "", pageToken: String = "") =
    ScopeCursor(scope = scope, sync_token = syncToken, page_token = pageToken)

  @Test
  fun forwardsTokensAndPageSizeToTheMatchingListCall() = runBlocking {
    val service =
      service(
        organization =
          mapOf(
            "ListOrganizations" to
              { _ ->
                ListOrganizationsResponse(next_page_token = "p2", next_sync_token = "s2")
              }
          )
      )

    val response =
      service.SyncChanges(
        SyncChangesRequest(
          cursors =
            listOf(
              cursor(SyncScope(organizations = SyncScope.All()), syncToken = "s1", pageToken = "p1")
            ),
          page_size = 50,
        )
      )

    assertEquals(
      ListOrganizationsRequest(page_size = 50, page_token = "p1", sync_token = "s1"),
      requests.single(),
    )
    val scope = response.scopes.single()
    assertEquals("p2", scope.next_page_token)
    assertEquals("s2", scope.next_sync_token)
    assertEquals(SyncScope(organizations = SyncScope.All()), scope.scope)
  }

  @Test
  fun negativePageSize_isSentAsZero() = runBlocking {
    val service = service(library = mapOf("ListLibraries" to { _ -> ListLibrariesResponse() }))

    service.SyncChanges(
      SyncChangesRequest(
        cursors = listOf(cursor(SyncScope(libraries = SyncScope.All()))),
        page_size = -5,
      )
    )

    assertEquals(
      0,
      (requests.single() as openforis.collectone.v1.library.ListLibrariesRequest).page_size,
    )
  }

  @Test
  fun surveyScopedCursors_passTheirSurveyId() = runBlocking {
    val entity = EntityRecord(entity_id = "e1")
    val service =
      service(
        data =
          mapOf(
            "ListEntities" to { _ -> ListEntitiesResponse(entities = listOf(entity)) },
            "ListSubmissions" to { _ -> ListSubmissionsResponse() },
          )
      )

    val response =
      service.SyncChanges(
        SyncChangesRequest(
          cursors =
            listOf(
              cursor(SyncScope(survey_entities = "s-1")),
              cursor(SyncScope(survey_submissions = "s-2")),
            )
        )
      )

    assertEquals("s-1", (requests[0] as ListEntitiesRequest).survey_id)
    assertEquals("s-2", (requests[1] as ListSubmissionsRequest).survey_id)
    // Cursors are answered in request order.
    assertEquals(listOf(entity), response.scopes[0].records?.entities)
    assertEquals(SyncScope(survey_submissions = "s-2"), response.scopes[1].scope)
  }

  @Test
  fun impactEvents_areListed() = runBlocking {
    val service =
      service(
        data = mapOf("ListImpactEvents" to { _ -> ListImpactEventsResponse(next_sync_token = "t") })
      )

    val response =
      service.SyncChanges(
        SyncChangesRequest(cursors = listOf(cursor(SyncScope(impact_events = SyncScope.All()))))
      )

    assertEquals("t", response.scopes.single().next_sync_token)
  }

  @Test
  fun surveyDefinitions_areInlinedOnlyWhenRequestedAndSkipDeletedAndFailingSurveys() = runBlocking {
    val overviews =
      listOf(
        SurveyOverview(survey_id = "live"),
        SurveyOverview(survey_id = "gone", state = SurveyState.DELETED),
        SurveyOverview(survey_id = "unshared"),
      )
    val service =
      service(
        survey =
          mapOf(
            "ListSurveyOverviews" to { _ -> ListSurveyOverviewsResponse(overviews = overviews) },
            "GetSurvey" to
              { request ->
                val id = (request as GetSurveyRequest).survey_id
                if (id == "unshared") throw ScopeFailure("unshared")
                GetSurveyResponse(survey = SurveyDef(survey_id = id))
              },
          )
      )
    val scope = SyncScope(survey_overviews = SyncScope.All())

    val without = service.SyncChanges(SyncChangesRequest(cursors = listOf(cursor(scope))))
    val with =
      service.SyncChanges(
        SyncChangesRequest(cursors = listOf(cursor(scope)), include_survey_definitions = true)
      )

    assertEquals(emptyMap(), without.scopes.single().records?.survey_definitions)
    val definitions = with.scopes.single().records!!.survey_definitions
    assertEquals(setOf("live"), definitions.keys)
    assertEquals(overviews, with.scopes.single().records!!.survey_overviews)
  }

  @Test
  fun cursorWithoutScope_isRejectedOnItsOwn() = runBlocking {
    val response = service().SyncChanges(SyncChangesRequest(cursors = listOf(ScopeCursor())))

    val scope = response.scopes.single()
    assertEquals(ListBackedSyncService.INVALID_ARGUMENT, scope.error_code)
    assertEquals(SyncScope(), scope.scope)
  }

  @Test
  fun scopeErrors_areReportedOnTheirCursorWithoutFailingOthers() = runBlocking {
    val service =
      service(
        data =
          mapOf(
            "ListEntities" to { _ -> throw ScopeFailure("no such survey") },
            "ListSubmissions" to { _ -> ListSubmissionsResponse(next_sync_token = "ok") },
          )
      )

    val response =
      service.SyncChanges(
        SyncChangesRequest(
          cursors =
            listOf(
              cursor(SyncScope(survey_entities = "missing")),
              cursor(SyncScope(survey_submissions = "s")),
            )
        )
      )

    assertEquals(NOT_FOUND, response.scopes[0].error_code)
    assertEquals("no such survey", response.scopes[0].error_message)
    assertEquals("ok", response.scopes[1].next_sync_token)
  }

  @Test
  fun unmappedErrors_failTheWholeCall() {
    val service =
      service(data = mapOf("ListEntities" to { _ -> throw IllegalStateException("boom") }))

    val error =
      assertFailsWith<IllegalStateException> {
        runBlocking {
          service.SyncChanges(
            SyncChangesRequest(cursors = listOf(cursor(SyncScope(survey_entities = "s"))))
          )
        }
      }
    assertEquals("boom", error.message)
  }

  @Test
  fun cancellation_isNeverReportedAsAScopeError() {
    val service =
      ListBackedSyncService(
        surveyService = fake(emptyMap()),
        organizationService = fake(emptyMap()),
        libraryService = fake(emptyMap()),
        dataService =
          fake<DataServiceServer>(
            mapOf("ListEntities" to { _ -> throw CancellationException("cancelled") })
          ),
        // Even a mapper that maps everything must not swallow cancellation.
        scopeErrorCode = { NOT_FOUND },
      )

    val error =
      assertFailsWith<CancellationException> {
        runBlocking {
          service.SyncChanges(
            SyncChangesRequest(cursors = listOf(cursor(SyncScope(survey_entities = "s"))))
          )
        }
      }
    assertTrue(error.message == "cancelled")
  }

  private companion object {
    const val NOT_FOUND = 5
  }
}
