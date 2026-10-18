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
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.data.GetAppliedMutationsRequest
import groundplatform.v2.data.GetAppliedMutationsResponse
import groundplatform.v2.data.GetEntityRequest
import groundplatform.v2.data.GetEntityResponse
import groundplatform.v2.data.GetImpactAggregatesRequest
import groundplatform.v2.data.GetImpactAggregatesResponse
import groundplatform.v2.data.GetMediaDownloadUrlRequest
import groundplatform.v2.data.GetMediaDownloadUrlResponse
import groundplatform.v2.data.GetSubmissionRequest
import groundplatform.v2.data.GetSubmissionResponse
import groundplatform.v2.data.ListAuditRecordsRequest
import groundplatform.v2.data.ListAuditRecordsResponse
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.data.ListEntitiesResponse
import groundplatform.v2.data.ListEntityClustersRequest
import groundplatform.v2.data.ListEntityClustersResponse
import groundplatform.v2.data.ListImpactEventsRequest
import groundplatform.v2.data.ListImpactEventsResponse
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
import groundplatform.v2.library.GetLibraryRequest
import groundplatform.v2.library.GetLibraryResponse
import groundplatform.v2.library.GetResolvedLibraryRequest
import groundplatform.v2.library.GetResolvedLibraryResponse
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.library.ListLibrariesRequest
import groundplatform.v2.library.ListLibrariesResponse
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.MutateLibraryResponse
import groundplatform.v2.library.SearchConceptsRequest
import groundplatform.v2.library.SearchConceptsResponse
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
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.ListSurveyOverviewsResponse
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateOrganizationResponse
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.MutateSurveyResponse
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.SearchOrganizationsRequest
import groundplatform.v2.survey.SearchOrganizationsResponse
import groundplatform.v2.survey.SurveyServiceServer

/**
 * System & Framework gRPC client (`client/grpc/ *Client`) bridging the Wire-generated Kotlin gRPC
 * services ([SurveyServiceServer], [OrganizationServiceServer], [LibraryServiceServer], and
 * [DataServiceServer]) into suspending unary RPCs.
 *
 * Synced resources use the `List*` RPCs in query mode (full syncs) and delta mode (`sync_token`),
 * as described in `docs/technical/sync-engine.md`. Foreground freshness comes from polling.
 */
class CollectOneGrpcClient(
  val surveyService: SurveyServiceServer,
  val organizationService: OrganizationServiceServer,
  val libraryService: LibraryServiceServer,
  val dataService: DataServiceServer,
) {
  // ---------------------------------------------------------------------------
  // OrganizationService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getOrganization(request: GetOrganizationRequest): GetOrganizationResponse =
    organizationService.GetOrganization(request)

  suspend fun searchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse = organizationService.SearchOrganizations(request)

  suspend fun listOrganizations(request: ListOrganizationsRequest): ListOrganizationsResponse =
    organizationService.ListOrganizations(request)

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

  suspend fun listLibraries(request: ListLibrariesRequest): ListLibrariesResponse =
    libraryService.ListLibraries(request)

  suspend fun searchConcepts(request: SearchConceptsRequest): SearchConceptsResponse =
    libraryService.SearchConcepts(request)

  suspend fun mutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse =
    libraryService.MutateLibrary(request)

  // ---------------------------------------------------------------------------
  // SurveyService RPCs
  // ---------------------------------------------------------------------------

  suspend fun listSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse = surveyService.ListSurveyOverviews(request)

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

  suspend fun listEntityClusters(request: ListEntityClustersRequest): ListEntityClustersResponse =
    dataService.ListEntityClusters(request)

  suspend fun getSubmission(request: GetSubmissionRequest): GetSubmissionResponse =
    dataService.GetSubmission(request)

  suspend fun listSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse =
    dataService.ListSubmissions(request)

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

  suspend fun listImpactEvents(request: ListImpactEventsRequest): ListImpactEventsResponse =
    dataService.ListImpactEvents(request)

  suspend fun getImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse = dataService.GetImpactAggregates(request)

  suspend fun listAuditRecords(request: ListAuditRecordsRequest): ListAuditRecordsResponse =
    dataService.ListAuditRecords(request)

  suspend fun listPlaces(request: ListPlacesRequest): ListPlacesResponse =
    dataService.ListPlaces(request)

  suspend fun listMutationLogs(request: ListMutationLogsRequest): ListMutationLogsResponse =
    dataService.ListMutationLogs(request)

  suspend fun getAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse = dataService.GetAppliedMutations(request)
}
