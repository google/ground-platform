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
package org.groundplatform.v2.app.client.grpc

import groundplatform.v2.data.AcquirePlotLockRequest
import groundplatform.v2.data.AcquirePlotLockResponse
import groundplatform.v2.data.AuditRecord
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.GetEntityRequest
import groundplatform.v2.data.GetEntityResponse
import groundplatform.v2.data.GetImpactAggregatesRequest
import groundplatform.v2.data.GetImpactAggregatesResponse
import groundplatform.v2.data.GetMediaDownloadUrlRequest
import groundplatform.v2.data.GetMediaDownloadUrlResponse
import groundplatform.v2.data.GetSubmissionRequest
import groundplatform.v2.data.GetSubmissionResponse
import groundplatform.v2.data.ImpactEvent
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.data.ListEntitiesResponse
import groundplatform.v2.data.ListEntityClustersRequest
import groundplatform.v2.data.ListEntityClustersResponse
import groundplatform.v2.data.ListMutationLogsRequest
import groundplatform.v2.data.ListMutationLogsResponse
import groundplatform.v2.data.ListPlacesRequest
import groundplatform.v2.data.ListPlacesResponse
import groundplatform.v2.data.ListSubmissionsRequest
import groundplatform.v2.data.ListSubmissionsResponse
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.data.MutateDataResponse
import groundplatform.v2.data.PrepareMediaUploadRequest
import groundplatform.v2.data.PrepareMediaUploadResponse
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.RecordImpactEventsResponse
import groundplatform.v2.data.ReleasePlotLockRequest
import groundplatform.v2.data.ReleasePlotLockResponse
import groundplatform.v2.data.StreamAuditRecordsRequest
import groundplatform.v2.data.StreamEntitiesRequest
import groundplatform.v2.data.StreamImpactEventsRequest
import groundplatform.v2.data.StreamSubmissionsRequest
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.GetLibraryRequest
import groundplatform.v2.library.GetLibraryResponse
import groundplatform.v2.library.GetResolvedLibraryRequest
import groundplatform.v2.library.GetResolvedLibraryResponse
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.MutateLibraryResponse
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.SearchConceptsRequest
import groundplatform.v2.library.SearchConceptsResponse
import groundplatform.v2.library.StreamLibrariesRequest
import groundplatform.v2.survey.AcceptSurveyInviteRequest
import groundplatform.v2.survey.AcceptSurveyInviteResponse
import groundplatform.v2.survey.GetOrganizationRequest
import groundplatform.v2.survey.GetOrganizationResponse
import groundplatform.v2.survey.GetSurveyAclRequest
import groundplatform.v2.survey.GetSurveyAclResponse
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.GetSurveyResponse
import groundplatform.v2.survey.JoinOrganizationRequest
import groundplatform.v2.survey.JoinOrganizationResponse
import groundplatform.v2.survey.ListOrganizationsRequest
import groundplatform.v2.survey.ListOrganizationsResponse
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateOrganizationResponse
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.MutateSurveyResponse
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.StreamOrganizationsRequest
import groundplatform.v2.survey.StreamSurveyOverviewsRequest
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyServiceServer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * System & Framework gRPC client (`client/grpc/ *Client`) bridging the Wire-generated Kotlin gRPC
 * services ([SurveyServiceServer], [OrganizationServiceServer], [LibraryServiceServer], and
 * [DataServiceServer]) into suspending unary RPCs and cold reactive [Flow] server streams.
 *
 * Delta streams (`Stream*`) are bounded, as described in `docs/technical/sync-engine.md`: each
 * returned [Flow] completes once the client is caught up. Foreground freshness comes from polling.
 */
class CollectOneGrpcClient(
  val surveyService: SurveyServiceServer,
  val organizationService: OrganizationServiceServer,
  val libraryService: LibraryServiceServer,
  val dataService: DataServiceServer,
) {
  private fun <T> collectServerStream(block: suspend (SendChannel<T>) -> Unit): Flow<T> = flow {
    val ch = Channel<T>(Channel.UNLIMITED)
    try {
      block(ch)
    } finally {
      ch.close()
    }
    for (item in ch) {
      emit(item)
    }
  }

  // ---------------------------------------------------------------------------
  // OrganizationService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getOrganization(request: GetOrganizationRequest): GetOrganizationResponse =
    organizationService.GetOrganization(request)

  suspend fun listOrganizations(request: ListOrganizationsRequest): ListOrganizationsResponse =
    organizationService.ListOrganizations(request)

  fun streamOrganizations(request: StreamOrganizationsRequest): Flow<Organization> =
    collectServerStream { ch ->
      organizationService.StreamOrganizations(request, ch)
    }

  suspend fun mutateOrganization(request: MutateOrganizationRequest): MutateOrganizationResponse =
    organizationService.MutateOrganization(request)

  suspend fun joinOrganization(request: JoinOrganizationRequest): JoinOrganizationResponse =
    organizationService.JoinOrganization(request)

  // ---------------------------------------------------------------------------
  // LibraryService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getLibrary(request: GetLibraryRequest): GetLibraryResponse =
    libraryService.GetLibrary(request)

  suspend fun getResolvedLibrary(request: GetResolvedLibraryRequest): GetResolvedLibraryResponse =
    libraryService.GetResolvedLibrary(request)

  fun streamLibraries(request: StreamLibrariesRequest): Flow<OrganizationLibraryRecord> =
    collectServerStream { ch ->
      libraryService.StreamLibraries(request, ch)
    }

  suspend fun searchConcepts(request: SearchConceptsRequest): SearchConceptsResponse =
    libraryService.SearchConcepts(request)

  suspend fun mutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse =
    libraryService.MutateLibrary(request)

  // ---------------------------------------------------------------------------
  // SurveyService RPCs
  // ---------------------------------------------------------------------------

  fun streamSurveyOverviews(request: StreamSurveyOverviewsRequest): Flow<SurveyOverview> =
    collectServerStream { ch ->
      surveyService.StreamSurveyOverviews(request, ch)
    }

  suspend fun getSurvey(request: GetSurveyRequest): GetSurveyResponse =
    surveyService.GetSurvey(request)

  suspend fun getSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse =
    surveyService.GetSurveyAcl(request)

  suspend fun mutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse =
    surveyService.MutateSurvey(request)

  suspend fun acceptSurveyInvite(request: AcceptSurveyInviteRequest): AcceptSurveyInviteResponse =
    surveyService.AcceptSurveyInvite(request)

  // ---------------------------------------------------------------------------
  // DataService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getEntity(request: GetEntityRequest): GetEntityResponse =
    dataService.GetEntity(request)

  suspend fun listEntities(request: ListEntitiesRequest): ListEntitiesResponse =
    dataService.ListEntities(request)

  fun streamEntities(request: StreamEntitiesRequest): Flow<EntityRecord> =
    collectServerStream { ch ->
      dataService.StreamEntities(request, ch)
    }

  suspend fun listEntityClusters(request: ListEntityClustersRequest): ListEntityClustersResponse =
    dataService.ListEntityClusters(request)

  suspend fun getSubmission(request: GetSubmissionRequest): GetSubmissionResponse =
    dataService.GetSubmission(request)

  suspend fun listSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse =
    dataService.ListSubmissions(request)

  fun streamSubmissions(request: StreamSubmissionsRequest): Flow<SubmissionRecord> =
    collectServerStream { ch ->
      dataService.StreamSubmissions(request, ch)
    }

  suspend fun mutateData(request: MutateDataRequest): MutateDataResponse =
    dataService.MutateData(request)

  suspend fun acquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse =
    dataService.AcquirePlotLock(request)

  suspend fun releasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse =
    dataService.ReleasePlotLock(request)

  suspend fun prepareMediaUpload(request: PrepareMediaUploadRequest): PrepareMediaUploadResponse =
    dataService.PrepareMediaUpload(request)

  suspend fun getMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse = dataService.GetMediaDownloadUrl(request)

  suspend fun recordImpactEvents(request: RecordImpactEventsRequest): RecordImpactEventsResponse =
    dataService.RecordImpactEvents(request)

  fun streamImpactEvents(request: StreamImpactEventsRequest): Flow<ImpactEvent> =
    collectServerStream { ch ->
      dataService.StreamImpactEvents(request, ch)
    }

  suspend fun getImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse = dataService.GetImpactAggregates(request)

  fun streamAuditRecords(request: StreamAuditRecordsRequest): Flow<AuditRecord> =
    collectServerStream { ch ->
      dataService.StreamAuditRecords(request, ch)
    }

  suspend fun listPlaces(request: ListPlacesRequest): ListPlacesResponse =
    dataService.ListPlaces(request)

  suspend fun listMutationLogs(request: ListMutationLogsRequest): ListMutationLogsResponse =
    dataService.ListMutationLogs(request)
}
