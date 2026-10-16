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

// Remote implementations of the Wire-generated user data service interfaces. Each method forwards
// to the method with the same name on the user data service (`server/user-data-service`).
// `RemoteUserDataServicesTest` checks every path against the `@WireRpc` annotations.

/**
 * [SurveyServiceServer] that calls `groundplatform.v2.survey.SurveyService` over
 * [GrpcWebTransport].
 */
class RemoteSurveyService(private val transport: GrpcWebTransport) : SurveyServiceServer {
  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse =
    transport.unary(
      "/groundplatform.v2.survey.SurveyService/ListSurveyOverviews",
      request,
      ListSurveyOverviewsRequest.ADAPTER,
      ListSurveyOverviewsResponse.ADAPTER,
    )

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse =
    transport.unary(
      "/groundplatform.v2.survey.SurveyService/GetSurvey",
      request,
      GetSurveyRequest.ADAPTER,
      GetSurveyResponse.ADAPTER,
    )

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse =
    transport.unary(
      "/groundplatform.v2.survey.SurveyService/GetSurveyAcl",
      request,
      GetSurveyAclRequest.ADAPTER,
      GetSurveyAclResponse.ADAPTER,
    )

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse =
    transport.unary(
      "/groundplatform.v2.survey.SurveyService/MutateSurvey",
      request,
      MutateSurveyRequest.ADAPTER,
      MutateSurveyResponse.ADAPTER,
    )

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse =
    transport.unary(
      "/groundplatform.v2.survey.SurveyService/AcceptSurveyInvite",
      request,
      AcceptSurveyInviteRequest.ADAPTER,
      AcceptSurveyInviteResponse.ADAPTER,
    )
}

/**
 * [OrganizationServiceServer] that calls `groundplatform.v2.survey.OrganizationService` over
 * [GrpcWebTransport].
 */
class RemoteOrganizationService(private val transport: GrpcWebTransport) :
  OrganizationServiceServer {
  override suspend fun GetOrganization(request: GetOrganizationRequest): GetOrganizationResponse =
    transport.unary(
      "/groundplatform.v2.survey.OrganizationService/GetOrganization",
      request,
      GetOrganizationRequest.ADAPTER,
      GetOrganizationResponse.ADAPTER,
    )

  override suspend fun SearchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse =
    transport.unary(
      "/groundplatform.v2.survey.OrganizationService/SearchOrganizations",
      request,
      SearchOrganizationsRequest.ADAPTER,
      SearchOrganizationsResponse.ADAPTER,
    )

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse =
    transport.unary(
      "/groundplatform.v2.survey.OrganizationService/ListOrganizations",
      request,
      ListOrganizationsRequest.ADAPTER,
      ListOrganizationsResponse.ADAPTER,
    )

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse =
    transport.unary(
      "/groundplatform.v2.survey.OrganizationService/MutateOrganization",
      request,
      MutateOrganizationRequest.ADAPTER,
      MutateOrganizationResponse.ADAPTER,
    )

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse =
    transport.unary(
      "/groundplatform.v2.survey.OrganizationService/JoinOrganization",
      request,
      JoinOrganizationRequest.ADAPTER,
      JoinOrganizationResponse.ADAPTER,
    )
}

/**
 * [LibraryServiceServer] that calls `groundplatform.v2.library.LibraryService` over
 * [GrpcWebTransport].
 */
class RemoteLibraryService(private val transport: GrpcWebTransport) : LibraryServiceServer {
  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse =
    transport.unary(
      "/groundplatform.v2.library.LibraryService/GetLibrary",
      request,
      GetLibraryRequest.ADAPTER,
      GetLibraryResponse.ADAPTER,
    )

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse =
    transport.unary(
      "/groundplatform.v2.library.LibraryService/GetResolvedLibrary",
      request,
      GetResolvedLibraryRequest.ADAPTER,
      GetResolvedLibraryResponse.ADAPTER,
    )

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse =
    transport.unary(
      "/groundplatform.v2.library.LibraryService/ListLibraries",
      request,
      ListLibrariesRequest.ADAPTER,
      ListLibrariesResponse.ADAPTER,
    )

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse =
    transport.unary(
      "/groundplatform.v2.library.LibraryService/SearchConcepts",
      request,
      SearchConceptsRequest.ADAPTER,
      SearchConceptsResponse.ADAPTER,
    )

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse =
    transport.unary(
      "/groundplatform.v2.library.LibraryService/MutateLibrary",
      request,
      MutateLibraryRequest.ADAPTER,
      MutateLibraryResponse.ADAPTER,
    )
}

/** [DataServiceServer] that calls `groundplatform.v2.data.DataService` over [GrpcWebTransport]. */
class RemoteDataService(private val transport: GrpcWebTransport) : DataServiceServer {
  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/GetEntity",
      request,
      GetEntityRequest.ADAPTER,
      GetEntityResponse.ADAPTER,
    )

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListEntities",
      request,
      ListEntitiesRequest.ADAPTER,
      ListEntitiesResponse.ADAPTER,
    )

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListEntityClusters",
      request,
      ListEntityClustersRequest.ADAPTER,
      ListEntityClustersResponse.ADAPTER,
    )

  override suspend fun GetSubmission(request: GetSubmissionRequest): GetSubmissionResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/GetSubmission",
      request,
      GetSubmissionRequest.ADAPTER,
      GetSubmissionResponse.ADAPTER,
    )

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListSubmissions",
      request,
      ListSubmissionsRequest.ADAPTER,
      ListSubmissionsResponse.ADAPTER,
    )

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/MutateData",
      request,
      MutateDataRequest.ADAPTER,
      MutateDataResponse.ADAPTER,
    )

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/AcquirePlotLock",
      request,
      AcquirePlotLockRequest.ADAPTER,
      AcquirePlotLockResponse.ADAPTER,
    )

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ReleasePlotLock",
      request,
      ReleasePlotLockRequest.ADAPTER,
      ReleasePlotLockResponse.ADAPTER,
    )

  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/PrepareMediaUpload",
      request,
      PrepareMediaUploadRequest.ADAPTER,
      PrepareMediaUploadResponse.ADAPTER,
    )

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/GetMediaDownloadUrl",
      request,
      GetMediaDownloadUrlRequest.ADAPTER,
      GetMediaDownloadUrlResponse.ADAPTER,
    )

  override suspend fun RecordImpactEvents(
    request: RecordImpactEventsRequest
  ): RecordImpactEventsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/RecordImpactEvents",
      request,
      RecordImpactEventsRequest.ADAPTER,
      RecordImpactEventsResponse.ADAPTER,
    )

  override suspend fun ListImpactEvents(
    request: ListImpactEventsRequest
  ): ListImpactEventsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListImpactEvents",
      request,
      ListImpactEventsRequest.ADAPTER,
      ListImpactEventsResponse.ADAPTER,
    )

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/GetImpactAggregates",
      request,
      GetImpactAggregatesRequest.ADAPTER,
      GetImpactAggregatesResponse.ADAPTER,
    )

  override suspend fun ListAuditRecords(
    request: ListAuditRecordsRequest
  ): ListAuditRecordsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListAuditRecords",
      request,
      ListAuditRecordsRequest.ADAPTER,
      ListAuditRecordsResponse.ADAPTER,
    )

  override suspend fun ListPlaces(request: ListPlacesRequest): ListPlacesResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListPlaces",
      request,
      ListPlacesRequest.ADAPTER,
      ListPlacesResponse.ADAPTER,
    )

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/ListMutationLogs",
      request,
      ListMutationLogsRequest.ADAPTER,
      ListMutationLogsResponse.ADAPTER,
    )

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse =
    transport.unary(
      "/groundplatform.v2.data.DataService/GetAppliedMutations",
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
