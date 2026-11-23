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

import kotlin.coroutines.cancellation.CancellationException
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.sync.ScopeChanges
import openforis.collectone.v1.sync.ScopeCursor
import openforis.collectone.v1.sync.ScopeRecords
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncChangesResponse
import openforis.collectone.v1.sync.SyncScope
import openforis.collectone.v1.sync.SyncServiceServer

/**
 * [SyncServiceServer] that answers each cursor with the matching `List*` call (and `GetSurvey` for
 * inlined survey definitions), so `SyncChanges` behaves exactly like those RPCs and shares their
 * sync tokens (`docs/technical/sync-engine.md`, "Batched Catch-Up").
 *
 * The user data service serves `SyncChanges` with it over its own handlers. Clients use it over the
 * remote `List*` RPCs when the service doesn't implement `SyncChanges` yet, and tests use it over
 * in-memory fakes.
 *
 * A failure that [scopeErrorCode] maps to a `google.rpc.Code` is reported on that cursor's
 * [ScopeChanges]; any other failure fails the whole call.
 */
class ListBackedSyncService(
  private val surveyService: SurveyServiceServer,
  private val organizationService: OrganizationServiceServer,
  private val libraryService: LibraryServiceServer,
  private val dataService: DataServiceServer,
  private val scopeErrorCode: (Throwable) -> Int?,
) : SyncServiceServer {

  override suspend fun SyncChanges(request: SyncChangesRequest): SyncChangesResponse =
    SyncChangesResponse(scopes = request.cursors.map { changes(it, request) })

  private suspend fun changes(cursor: ScopeCursor, request: SyncChangesRequest): ScopeChanges {
    val scope = cursor.scope ?: SyncScope()
    return try {
      page(scope, cursor, request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      val code = scopeErrorCode(e) ?: throw e
      ScopeChanges(scope = scope, error_code = code, error_message = e.message.orEmpty())
    }
  }

  private suspend fun page(
    scope: SyncScope,
    cursor: ScopeCursor,
    request: SyncChangesRequest,
  ): ScopeChanges {
    val pageSize = request.page_size.coerceAtLeast(0)
    val pageToken = cursor.page_token
    val syncToken = cursor.sync_token
    fun changes(records: ScopeRecords, nextPageToken: String, nextSyncToken: String) =
      ScopeChanges(
        scope = scope,
        records = records,
        next_page_token = nextPageToken,
        next_sync_token = nextSyncToken,
      )
    val surveyEntities = scope.survey_entities
    val surveySubmissions = scope.survey_submissions
    return when {
      scope.organizations != null ->
        organizationService
          .ListOrganizations(
            ListOrganizationsRequest(
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
            )
          )
          .let {
            changes(
              ScopeRecords(organizations = it.organizations),
              it.next_page_token,
              it.next_sync_token,
            )
          }
      scope.libraries != null ->
        libraryService
          .ListLibraries(
            ListLibrariesRequest(
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
            )
          )
          .let {
            changes(ScopeRecords(libraries = it.libraries), it.next_page_token, it.next_sync_token)
          }
      scope.survey_overviews != null ->
        surveyService
          .ListSurveyOverviews(
            ListSurveyOverviewsRequest(
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
            )
          )
          .let {
            val definitions =
              if (request.include_survey_definitions) definitionsOf(it.overviews) else emptyMap()
            changes(
              ScopeRecords(survey_overviews = it.overviews, survey_definitions = definitions),
              it.next_page_token,
              it.next_sync_token,
            )
          }
      surveyEntities != null ->
        dataService
          .ListEntities(
            ListEntitiesRequest(
              survey_id = surveyEntities,
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
              field_mask = cursor.field_mask,
            )
          )
          .let {
            changes(ScopeRecords(entities = it.entities), it.next_page_token, it.next_sync_token)
          }
      surveySubmissions != null ->
        dataService
          .ListSubmissions(
            ListSubmissionsRequest(
              survey_id = surveySubmissions,
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
            )
          )
          .let {
            changes(
              ScopeRecords(submissions = it.submissions),
              it.next_page_token,
              it.next_sync_token,
            )
          }
      scope.impact_events != null ->
        dataService
          .ListImpactEvents(
            ListImpactEventsRequest(
              page_size = pageSize,
              page_token = pageToken,
              sync_token = syncToken,
            )
          )
          .let {
            changes(ScopeRecords(impact_events = it.events), it.next_page_token, it.next_sync_token)
          }
      else ->
        ScopeChanges(
          scope = scope,
          error_code = INVALID_ARGUMENT,
          error_message = "The cursor has no scope",
        )
    }
  }

  /**
   * The saved revisions of the [overviews] that aren't deleted, leaving out those that fail with a
   * scope error (for example a survey trashed or unshared since it was listed).
   */
  private suspend fun definitionsOf(
    overviews: List<SurveyOverview>
  ): Map<String, GetSurveyResponse> = buildMap {
    for (overview in overviews) {
      if (overview.state == SurveyState.DELETED) continue
      try {
        put(
          overview.survey_id,
          surveyService.GetSurvey(GetSurveyRequest(survey_id = overview.survey_id)),
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        scopeErrorCode(e) ?: throw e
      }
    }
  }

  companion object {
    /** `google.rpc.Code.INVALID_ARGUMENT`. */
    const val INVALID_ARGUMENT = 3
  }
}
