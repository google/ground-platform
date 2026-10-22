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
package org.openforis.collectone.v2.app.client.grpc

import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.AcquirePlotLockResponse
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.GetAppliedMutationsResponse
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.GetEntityResponse
import openforis.collectone.v1.data.GetImpactAggregatesRequest
import openforis.collectone.v1.data.GetImpactAggregatesResponse
import openforis.collectone.v1.data.GetMediaDownloadUrlRequest
import openforis.collectone.v1.data.GetMediaDownloadUrlResponse
import openforis.collectone.v1.data.GetSubmissionRequest
import openforis.collectone.v1.data.GetSubmissionResponse
import openforis.collectone.v1.data.ListAuditRecordsRequest
import openforis.collectone.v1.data.ListAuditRecordsResponse
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.data.ListEntityClustersRequest
import openforis.collectone.v1.data.ListEntityClustersResponse
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListImpactEventsResponse
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListMutationLogsResponse
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.ListPlacesResponse
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.ListSubmissionsResponse
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.data.PrepareMediaUploadRequest
import openforis.collectone.v1.data.PrepareMediaUploadResponse
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.RecordImpactEventsResponse
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.ReleasePlotLockResponse
import openforis.collectone.v1.library.GetLibraryRequest
import openforis.collectone.v1.library.GetLibraryResponse
import openforis.collectone.v1.library.GetResolvedLibraryRequest
import openforis.collectone.v1.library.GetResolvedLibraryResponse
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.ListLibrariesResponse
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.MutateLibraryResponse
import openforis.collectone.v1.library.SearchConceptsRequest
import openforis.collectone.v1.library.SearchConceptsResponse
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AcceptSurveyInviteResponse
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsResponse
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetOrganizationResponse
import openforis.collectone.v1.survey.GetSurveyAclRequest
import openforis.collectone.v1.survey.GetSurveyAclResponse
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.JoinOrganizationRequest
import openforis.collectone.v1.survey.JoinOrganizationResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsResponse
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateOrganizationResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SearchOrganizationsRequest
import openforis.collectone.v1.survey.SearchOrganizationsResponse
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SearchPublicSurveysResponse
import openforis.collectone.v1.survey.SurveyServiceServer

// Remote implementations of the Wire-generated user data service interfaces. Each method forwards
// to the method with the same name on the user data service (`server/userDataService`).
// `RemoteUserDataServicesTest` checks every path against the `@WireRpc` annotations.

/**
 * [SurveyServiceServer] that calls `openforis.collectone.v1.survey.SurveyService` over
 * [GrpcWebTransport].
 */
class RemoteSurveyService(private val transport: GrpcWebTransport) : SurveyServiceServer {
  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews",
      request,
      ListSurveyOverviewsRequest.ADAPTER,
      ListSurveyOverviewsResponse.ADAPTER,
    )

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/GetSurvey",
      request,
      GetSurveyRequest.ADAPTER,
      GetSurveyResponse.ADAPTER,
    )

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/GetSurveyAcl",
      request,
      GetSurveyAclRequest.ADAPTER,
      GetSurveyAclResponse.ADAPTER,
    )

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/MutateSurvey",
      request,
      MutateSurveyRequest.ADAPTER,
      MutateSurveyResponse.ADAPTER,
    )

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/AcceptSurveyInvite",
      request,
      AcceptSurveyInviteRequest.ADAPTER,
      AcceptSurveyInviteResponse.ADAPTER,
    )

  override suspend fun BatchGetSurveyOverviews(
    request: BatchGetSurveyOverviewsRequest
  ): BatchGetSurveyOverviewsResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/BatchGetSurveyOverviews",
      request,
      BatchGetSurveyOverviewsRequest.ADAPTER,
      BatchGetSurveyOverviewsResponse.ADAPTER,
    )

  override suspend fun SearchPublicSurveys(
    request: SearchPublicSurveysRequest
  ): SearchPublicSurveysResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.SurveyService/SearchPublicSurveys",
      request,
      SearchPublicSurveysRequest.ADAPTER,
      SearchPublicSurveysResponse.ADAPTER,
    )
}

/**
 * [OrganizationServiceServer] that calls `openforis.collectone.v1.survey.OrganizationService` over
 * [GrpcWebTransport].
 */
class RemoteOrganizationService(private val transport: GrpcWebTransport) :
  OrganizationServiceServer {
  override suspend fun GetOrganization(request: GetOrganizationRequest): GetOrganizationResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.OrganizationService/GetOrganization",
      request,
      GetOrganizationRequest.ADAPTER,
      GetOrganizationResponse.ADAPTER,
    )

  override suspend fun SearchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.OrganizationService/SearchOrganizations",
      request,
      SearchOrganizationsRequest.ADAPTER,
      SearchOrganizationsResponse.ADAPTER,
    )

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.OrganizationService/ListOrganizations",
      request,
      ListOrganizationsRequest.ADAPTER,
      ListOrganizationsResponse.ADAPTER,
    )

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.OrganizationService/MutateOrganization",
      request,
      MutateOrganizationRequest.ADAPTER,
      MutateOrganizationResponse.ADAPTER,
    )

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse =
    transport.unary(
      "/openforis.collectone.v1.survey.OrganizationService/JoinOrganization",
      request,
      JoinOrganizationRequest.ADAPTER,
      JoinOrganizationResponse.ADAPTER,
    )
}

/**
 * [LibraryServiceServer] that calls `openforis.collectone.v1.library.LibraryService` over
 * [GrpcWebTransport].
 */
class RemoteLibraryService(private val transport: GrpcWebTransport) : LibraryServiceServer {
  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse =
    transport.unary(
      "/openforis.collectone.v1.library.LibraryService/GetLibrary",
      request,
      GetLibraryRequest.ADAPTER,
      GetLibraryResponse.ADAPTER,
    )

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse =
    transport.unary(
      "/openforis.collectone.v1.library.LibraryService/GetResolvedLibrary",
      request,
      GetResolvedLibraryRequest.ADAPTER,
      GetResolvedLibraryResponse.ADAPTER,
    )

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse =
    transport.unary(
      "/openforis.collectone.v1.library.LibraryService/ListLibraries",
      request,
      ListLibrariesRequest.ADAPTER,
      ListLibrariesResponse.ADAPTER,
    )

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse =
    transport.unary(
      "/openforis.collectone.v1.library.LibraryService/SearchConcepts",
      request,
      SearchConceptsRequest.ADAPTER,
      SearchConceptsResponse.ADAPTER,
    )

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse =
    transport.unary(
      "/openforis.collectone.v1.library.LibraryService/MutateLibrary",
      request,
      MutateLibraryRequest.ADAPTER,
      MutateLibraryResponse.ADAPTER,
    )
}

/**
 * [DataServiceServer] that calls `openforis.collectone.v1.data.DataService` over
 * [GrpcWebTransport].
 */
class RemoteDataService(private val transport: GrpcWebTransport) : DataServiceServer {
  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/GetEntity",
      request,
      GetEntityRequest.ADAPTER,
      GetEntityResponse.ADAPTER,
    )

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListEntities",
      request,
      ListEntitiesRequest.ADAPTER,
      ListEntitiesResponse.ADAPTER,
    )

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListEntityClusters",
      request,
      ListEntityClustersRequest.ADAPTER,
      ListEntityClustersResponse.ADAPTER,
    )

  override suspend fun GetSubmission(request: GetSubmissionRequest): GetSubmissionResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/GetSubmission",
      request,
      GetSubmissionRequest.ADAPTER,
      GetSubmissionResponse.ADAPTER,
    )

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListSubmissions",
      request,
      ListSubmissionsRequest.ADAPTER,
      ListSubmissionsResponse.ADAPTER,
    )

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/MutateData",
      request,
      MutateDataRequest.ADAPTER,
      MutateDataResponse.ADAPTER,
    )

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/AcquirePlotLock",
      request,
      AcquirePlotLockRequest.ADAPTER,
      AcquirePlotLockResponse.ADAPTER,
    )

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ReleasePlotLock",
      request,
      ReleasePlotLockRequest.ADAPTER,
      ReleasePlotLockResponse.ADAPTER,
    )

  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/PrepareMediaUpload",
      request,
      PrepareMediaUploadRequest.ADAPTER,
      PrepareMediaUploadResponse.ADAPTER,
    )

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/GetMediaDownloadUrl",
      request,
      GetMediaDownloadUrlRequest.ADAPTER,
      GetMediaDownloadUrlResponse.ADAPTER,
    )

  override suspend fun RecordImpactEvents(
    request: RecordImpactEventsRequest
  ): RecordImpactEventsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/RecordImpactEvents",
      request,
      RecordImpactEventsRequest.ADAPTER,
      RecordImpactEventsResponse.ADAPTER,
    )

  override suspend fun ListImpactEvents(
    request: ListImpactEventsRequest
  ): ListImpactEventsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListImpactEvents",
      request,
      ListImpactEventsRequest.ADAPTER,
      ListImpactEventsResponse.ADAPTER,
    )

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/GetImpactAggregates",
      request,
      GetImpactAggregatesRequest.ADAPTER,
      GetImpactAggregatesResponse.ADAPTER,
    )

  override suspend fun ListAuditRecords(
    request: ListAuditRecordsRequest
  ): ListAuditRecordsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListAuditRecords",
      request,
      ListAuditRecordsRequest.ADAPTER,
      ListAuditRecordsResponse.ADAPTER,
    )

  override suspend fun ListPlaces(request: ListPlacesRequest): ListPlacesResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListPlaces",
      request,
      ListPlacesRequest.ADAPTER,
      ListPlacesResponse.ADAPTER,
    )

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/ListMutationLogs",
      request,
      ListMutationLogsRequest.ADAPTER,
      ListMutationLogsResponse.ADAPTER,
    )

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse =
    transport.unary(
      "/openforis.collectone.v1.data.DataService/GetAppliedMutations",
      request,
      GetAppliedMutationsRequest.ADAPTER,
      GetAppliedMutationsResponse.ADAPTER,
    )
}

/** Clients for every user data service, sharing one [GrpcWebTransport]. */
fun remoteUserDataGrpcClient(transport: GrpcWebTransport): CollectOneGrpcClient =
  CollectOneGrpcClient(
    surveyService = RemoteSurveyService(transport),
    organizationService = RemoteOrganizationService(transport),
    libraryService = RemoteLibraryService(transport),
    dataService = RemoteDataService(transport),
  )
