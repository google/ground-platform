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
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetOrganizationResponse
import openforis.collectone.v1.survey.GetOverlayTileUrlRequest
import openforis.collectone.v1.survey.GetOverlayTileUrlResponse
import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotStatisticsResponse
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesResponse
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.GetSceneTileUrlResponse
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
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncChangesResponse
import openforis.collectone.v1.sync.SyncServiceServer
import openforis.collectone.v1.userdata.ClientVersionPolicy
import openforis.collectone.v1.userdata.ClientVersionServiceServer
import openforis.collectone.v1.userdata.GetClientVersionPolicyRequest
import openforis.collectone.v1.userdata.GetClientVersionPolicyResponse
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyRequest
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyResponse
import org.openforis.collectone.v2.core.sync.ListBackedSyncService

/**
 * System & Framework gRPC client (`client/grpc/ *Client`) bridging the Wire-generated Kotlin gRPC
 * services ([SurveyServiceServer], [OrganizationServiceServer], [LibraryServiceServer], and
 * [DataServiceServer]) into suspending unary RPCs.
 *
 * Synced resources use the `List*` RPCs in query mode (full syncs) and delta mode (`sync_token`),
 * as described in `docs/technical/sync-engine.md`. Catch-ups batch them with [syncChanges]: through
 * [syncService] when the service implements `SyncChanges`, and otherwise one `List*` call per scope
 * ([ListBackedSyncService]). Foreground freshness comes from polling.
 */
class CollectOneGrpcClient(
  val surveyService: SurveyServiceServer,
  val organizationService: OrganizationServiceServer,
  val libraryService: LibraryServiceServer,
  val dataService: DataServiceServer,
  val geoDataService: GeoDataServiceServer? = null,
  /** `SyncService`, or null to answer [syncChanges] with the `List*` RPCs. */
  val syncService: SyncServiceServer? = null,
  /** `ClientVersionService`, or null when the service doesn't expose version policies. */
  val clientVersionService: ClientVersionServiceServer? = null,
) {
  // ---------------------------------------------------------------------------
  // SyncService RPCs
  // ---------------------------------------------------------------------------

  /** Answers `SyncChanges` with one `List*` call per scope, for services without `SyncService`. */
  private val listBackedSyncService =
    ListBackedSyncService(
      surveyService = surveyService,
      organizationService = organizationService,
      libraryService = libraryService,
      dataService = dataService,
      scopeErrorCode = { (it as? GrpcWebException)?.status },
    )

  /** False once the service answered `SyncChanges` with `UNIMPLEMENTED` (an older deployment). */
  private var isSyncChangesSupported = syncService != null

  /**
   * Catches up several sync scopes in one `SyncChanges` call, or, when the service doesn't
   * implement it, with one `List*` call per scope (same tokens, same results).
   */
  suspend fun syncChanges(request: SyncChangesRequest): SyncChangesResponse {
    val service = syncService
    if (service != null && isSyncChangesSupported) {
      try {
        return service.SyncChanges(request)
      } catch (e: GrpcWebException) {
        if (e.status != GrpcWebTransport.UNIMPLEMENTED) throw e
        isSyncChangesSupported = false
      }
    }
    return listBackedSyncService.SyncChanges(request)
  }

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

  suspend fun batchGetSurveyOverviews(
    request: BatchGetSurveyOverviewsRequest
  ): BatchGetSurveyOverviewsResponse = surveyService.BatchGetSurveyOverviews(request)

  suspend fun searchPublicSurveys(
    request: SearchPublicSurveysRequest
  ): SearchPublicSurveysResponse = surveyService.SearchPublicSurveys(request)

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

  // ---------------------------------------------------------------------------
  // GeoDataService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getOverlayTileUrl(request: GetOverlayTileUrlRequest): GetOverlayTileUrlResponse =
    checkNotNull(geoDataService) { "GeoDataService not configured" }.GetOverlayTileUrl(request)

  suspend fun getPlotStatistics(request: GetPlotStatisticsRequest): GetPlotStatisticsResponse =
    checkNotNull(geoDataService) { "GeoDataService not configured" }.GetPlotStatistics(request)

  suspend fun getPlotTimeSeries(request: GetPlotTimeSeriesRequest): GetPlotTimeSeriesResponse =
    checkNotNull(geoDataService) { "GeoDataService not configured" }.GetPlotTimeSeries(request)

  suspend fun getSceneTileUrl(request: GetSceneTileUrlRequest): GetSceneTileUrlResponse =
    checkNotNull(geoDataService) { "GeoDataService not configured" }.GetSceneTileUrl(request)

  // ---------------------------------------------------------------------------
  // ClientVersionService RPCs
  // ---------------------------------------------------------------------------

  suspend fun getClientVersionPolicy(
    request: GetClientVersionPolicyRequest
  ): GetClientVersionPolicyResponse =
    clientVersionService?.GetClientVersionPolicy(request)
      ?: GetClientVersionPolicyResponse(policy = ClientVersionPolicy())

  suspend fun updateClientVersionPolicy(
    request: UpdateClientVersionPolicyRequest
  ): UpdateClientVersionPolicyResponse =
    checkNotNull(clientVersionService) { "ClientVersionService not configured" }
      .UpdateClientVersionPolicy(request)
}
