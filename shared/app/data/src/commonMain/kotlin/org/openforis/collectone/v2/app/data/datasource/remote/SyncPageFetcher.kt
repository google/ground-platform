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
package org.openforis.collectone.v2.app.data.datasource.remote

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport

/** The saved revision of a survey (with its ACL) and the organizer's working copy, if any. */
internal data class SurveyRevisions(val saved: GetSurveyResponse, val workingCopy: SurveyDef?)

/** One page of a `List*` response. */
internal class ListPage<T>(
  val records: List<T>,
  val nextPageToken: String,
  val nextSyncToken: String,
)

/** Every record of a query-mode `List*` call and the last page's `next_sync_token`. */
internal class FullList<T>(val records: List<T>, val syncToken: String)

/**
 * The read RPCs [GrpcSyncDataSource] and [DownloadedSurveyCatalog] page through: one `List*` call
 * per page (in query mode when the sync token is empty, in delta mode otherwise) and the
 * `GetSurvey` calls that fetch a survey's revisions.
 *
 * Holds one piece of state: the saved survey revisions a `SyncChanges` response inlined
 * ([inlineSurveyDefinitions]). [fetchSurveyRevisions] consumes them instead of calling `GetSurvey`,
 * so a batch that already carries a definition doesn't fetch it twice. The cache only lives for one
 * batched catch-up ([clearInlinedSurveyDefinitions] runs before and after it), so a stale inlined
 * revision never outlives the batch that sent it. Not thread-safe: callers run one sync at a time.
 */
internal class SyncPageFetcher(private val grpcClient: CollectOneGrpcClient) {
  private val inlinedSurveyDefinitions = mutableMapOf<String, GetSurveyResponse>()

  /** Remembers saved revisions a `SyncChanges` page inlined, by survey ID. */
  fun inlineSurveyDefinitions(definitions: Map<String, GetSurveyResponse>) {
    inlinedSurveyDefinitions += definitions
  }

  /** Forgets inlined revisions that no survey of the batch consumed. */
  fun clearInlinedSurveyDefinitions() {
    inlinedSurveyDefinitions.clear()
  }

  /** Reads every page of a query-mode call. */
  suspend fun <T> fullList(fetch: suspend (pageToken: String) -> ListPage<T>): FullList<T> {
    val records = mutableListOf<T>()
    var pageToken = ""
    while (true) {
      val page = fetch(pageToken)
      records += page.records
      if (page.nextPageToken.isEmpty()) return FullList(records, page.nextSyncToken)
      pageToken = page.nextPageToken
    }
  }

  // Page fetchers: one `List*` call each, in query mode when `syncToken` is empty.

  suspend fun organizationsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listOrganizations(ListOrganizationsRequest(sync_token = syncToken, page_token = pageToken))
      .let { ListPage<Organization>(it.organizations, it.next_page_token, it.next_sync_token) }

  suspend fun librariesPage(syncToken: String, pageToken: String) =
    grpcClient
      .listLibraries(ListLibrariesRequest(sync_token = syncToken, page_token = pageToken))
      .let {
        ListPage<OrganizationLibraryRecord>(it.libraries, it.next_page_token, it.next_sync_token)
      }

  suspend fun overviewsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listSurveyOverviews(
        ListSurveyOverviewsRequest(sync_token = syncToken, page_token = pageToken)
      )
      .let { ListPage<SurveyOverview>(it.overviews, it.next_page_token, it.next_sync_token) }

  suspend fun entitiesPage(
    surveyId: String,
    syncToken: String,
    pageToken: String,
    fieldMask: List<String> = emptyList(),
  ) =
    grpcClient
      .listEntities(
        ListEntitiesRequest(
          survey_id = surveyId,
          sync_token = syncToken,
          page_token = pageToken,
          field_mask = fieldMask,
        )
      )
      .let { ListPage<EntityRecord>(it.entities, it.next_page_token, it.next_sync_token) }

  suspend fun submissionsPage(surveyId: String, syncToken: String, pageToken: String) =
    grpcClient
      .listSubmissions(
        ListSubmissionsRequest(survey_id = surveyId, sync_token = syncToken, page_token = pageToken)
      )
      .let { ListPage<SubmissionRecord>(it.submissions, it.next_page_token, it.next_sync_token) }

  suspend fun mutationLogsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listMutationLogs(ListMutationLogsRequest(sync_token = syncToken, page_token = pageToken))
      .let { ListPage<MutationLogRecord>(it.mutation_logs, it.next_page_token, it.next_sync_token) }

  /**
   * Fetches the latest saved revision of a survey (unless `SyncChanges` inlined it) and, only when
   * [overview] reports unsaved edits (`has_working_copy`), the organizer's working copy. A caller
   * who may read the survey but not its working copy (`PERMISSION_DENIED`) gets the saved revision
   * alone; any other failure propagates.
   */
  suspend fun fetchSurveyRevisions(overview: SurveyOverview): SurveyRevisions {
    val surveyId = overview.survey_id
    return SurveyRevisions(
      saved =
        inlinedSurveyDefinitions.remove(surveyId)
          ?: grpcClient.getSurvey(GetSurveyRequest(survey_id = surveyId)),
      workingCopy =
        if (overview.has_working_copy) {
          try {
            grpcClient
              .getSurvey(
                GetSurveyRequest(
                  survey_id = surveyId,
                  working_copy = GetSurveyRequest.WorkingCopy(),
                )
              )
              .survey
          } catch (e: GrpcWebException) {
            if (e.status != GrpcWebTransport.PERMISSION_DENIED) throw e
            null
          }
        } else {
          null
        },
    )
  }
}
