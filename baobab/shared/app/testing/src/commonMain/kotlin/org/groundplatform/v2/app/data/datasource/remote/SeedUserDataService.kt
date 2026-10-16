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
package org.groundplatform.v2.app.data.datasource.remote

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
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
import groundplatform.v2.survey.SurveyState
import groundplatform.v2.userdata.SurveySeed
import groundplatform.v2.userdata.UserDataSeed
import kotlinx.coroutines.channels.SendChannel
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.data.seed.UserDataSeedGenerator

/**
 * Read-only, in-memory stand-in for the user data service, serving a [UserDataSeed] through the
 * sync RPCs the client uses (`Stream*`, `GetSurvey`, `GetSurveyAcl`, `ListPlaces`,
 * `ListMutationLogs`). Other RPCs throw; their behavior is tested in `server/user-data-service`.
 *
 * Calls never suspend, so tests can run sync code inside `runDirect`. Tests simulate changes made
 * by other clients with [putEntity] and [deleteEntity].
 */
class SeedUserDataService(seed: UserDataSeed = UserDataSeedGenerator.generate()) :
  SurveyServiceServer, OrganizationServiceServer, LibraryServiceServer, DataServiceServer {

  private val organizations = seed.organizations.toMutableList()
  private val libraries = seed.libraries.toMutableList()
  private val surveys = seed.surveys.associateByTo(LinkedHashMap()) { it.survey!!.survey_id }
  private val places = seed.places
  private val mutationLogs = seed.mutation_logs
  private var latestEpochSecond =
    (seed.organizations.map { it.commit_timestamp } +
        seed.surveys.flatMap { s ->
          listOf(s.survey?.commit_timestamp) +
            s.entities.map { it.commit_timestamp } +
            s.submissions.map { it.commit_timestamp }
        } +
        seed.mutation_logs.map { it.commit_timestamp })
      .maxOfOrNull { it?.getEpochSecond() ?: 0L } ?: 0L

  /** A client of this service. */
  fun client(): CollectOneGrpcClient =
    CollectOneGrpcClient(
      surveyService = this,
      organizationService = this,
      libraryService = this,
      dataService = this,
    )

  /** Adds or replaces an entity as if committed by another client, and returns its timestamp. */
  fun putEntity(surveyId: String, entity: EntityRecord): Instant {
    val ts = nextTimestamp()
    updateSurvey(surveyId) { s ->
      s.copy(
        entities =
          s.entities.filterNot { it.entity_id == entity.entity_id } +
            entity.copy(survey_id = surveyId, commit_timestamp = ts)
      )
    }
    return ts
  }

  /** Soft-deletes an entity as if deleted by another client, and returns its timestamp. */
  fun deleteEntity(surveyId: String, entityId: String): Instant {
    val ts = nextTimestamp()
    updateSurvey(surveyId) { s ->
      s.copy(
        entities =
          s.entities.map {
            if (it.entity_id == entityId) it.copy(is_deleted = true, commit_timestamp = ts) else it
          }
      )
    }
    return ts
  }

  private fun nextTimestamp(): Instant = ofEpochSecond(++latestEpochSecond, 0L)

  private fun updateSurvey(surveyId: String, update: (SurveySeed) -> SurveySeed) {
    surveys[surveyId] = update(checkNotNull(surveys[surveyId]) { "Unknown survey $surveyId" })
  }

  private suspend fun <T> send(
    response: SendChannel<T>,
    records: List<T>,
    since: Instant?,
    until: Instant?,
    timestamp: (T) -> Instant?,
  ) {
    records
      .filter { r ->
        val ts = timestamp(r)?.getEpochSecond() ?: 0L
        (since == null || ts > since.getEpochSecond()) &&
          (until == null || ts <= until.getEpochSecond())
      }
      .sortedBy { timestamp(it)?.getEpochSecond() ?: 0L }
      .forEach { response.send(it) }
  }

  // SurveyService

  override suspend fun StreamSurveyOverviews(
    request: StreamSurveyOverviewsRequest,
    response: SendChannel<SurveyOverview>,
  ) =
    send(
      response,
      surveys.values
        .filter { request.include_deleted || it.survey!!.state != SurveyState.DELETED }
        .map(::overviewOf),
      request.since_timestamp,
      request.until_timestamp,
      SurveyOverview::commit_timestamp,
    )

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse {
    val seed = surveys[request.survey_id] ?: return GetSurveyResponse()
    val survey = if (request.working_copy == true) seed.working_copy else seed.survey
    return GetSurveyResponse(survey = survey, acl = seed.acl)
  }

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest) =
    GetSurveyAclResponse(acl = surveys[request.survey_id]?.acl)

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse =
    unsupported()

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse = unsupported()

  // OrganizationService

  override suspend fun GetOrganization(request: GetOrganizationRequest) =
    GetOrganizationResponse(
      organization = organizations.firstOrNull { it.organization_id == request.organization_id }
    )

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse = unsupported()

  override suspend fun StreamOrganizations(
    request: StreamOrganizationsRequest,
    response: SendChannel<Organization>,
  ) =
    send(
      response,
      organizations.filter { request.include_deleted || !it.is_deleted },
      request.since_timestamp,
      request.until_timestamp,
      Organization::commit_timestamp,
    )

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse = unsupported()

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse = unsupported()

  // LibraryService

  override suspend fun GetLibrary(request: GetLibraryRequest) =
    GetLibraryResponse(
      library = libraries.firstOrNull { it.organization_id == request.organization_id }
    )

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse = unsupported()

  override suspend fun StreamLibraries(
    request: StreamLibrariesRequest,
    response: SendChannel<OrganizationLibraryRecord>,
  ) =
    send(
      response,
      libraries.filter { lib ->
        (request.include_deleted || !lib.is_deleted) &&
          (request.organization_id.isBlank() || lib.organization_id == request.organization_id)
      },
      request.since_timestamp,
      request.until_timestamp,
      OrganizationLibraryRecord::commit_timestamp,
    )

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse =
    unsupported()

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse =
    unsupported()

  // DataService

  override suspend fun GetEntity(request: GetEntityRequest) =
    GetEntityResponse(
      entity =
        surveys[request.survey_id]?.entities?.firstOrNull { it.entity_id == request.entity_id }
    )

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse =
    unsupported()

  override suspend fun StreamEntities(
    request: StreamEntitiesRequest,
    response: SendChannel<EntityRecord>,
  ) =
    send(
      response,
      surveys.values
        .filter { request.survey_id.isBlank() || it.survey!!.survey_id == request.survey_id }
        .flatMap { it.entities }
        .filter { e ->
          (request.include_deleted || !e.is_deleted) &&
            (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id)
        },
      request.since_timestamp,
      request.until_timestamp,
      EntityRecord::commit_timestamp,
    )

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse = unsupported()

  override suspend fun GetSubmission(request: GetSubmissionRequest) =
    GetSubmissionResponse(
      submission =
        surveys[request.survey_id]?.submissions?.firstOrNull {
          it.submission_id == request.submission_id
        }
    )

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse =
    unsupported()

  override suspend fun StreamSubmissions(
    request: StreamSubmissionsRequest,
    response: SendChannel<SubmissionRecord>,
  ) =
    send(
      response,
      surveys.values
        .filter { request.survey_id.isBlank() || it.survey!!.survey_id == request.survey_id }
        .flatMap { it.submissions }
        .filter { s ->
          (request.include_deleted || !s.is_deleted) &&
            (request.form_id.isBlank() || s.form_id == request.form_id) &&
            (request.entity_id.isBlank() || s.entity_id == request.entity_id)
        },
      request.since_timestamp,
      request.until_timestamp,
      SubmissionRecord::commit_timestamp,
    )

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse = unsupported()

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse =
    unsupported()

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse =
    unsupported()

  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse = unsupported()

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse = unsupported()

  override suspend fun RecordImpactEvents(
    request: RecordImpactEventsRequest
  ): RecordImpactEventsResponse = unsupported()

  override suspend fun StreamImpactEvents(
    request: StreamImpactEventsRequest,
    response: SendChannel<ImpactEvent>,
  ) = Unit

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse = unsupported()

  override suspend fun StreamAuditRecords(
    request: StreamAuditRecordsRequest,
    response: SendChannel<AuditRecord>,
  ) = Unit

  override suspend fun ListPlaces(request: ListPlacesRequest) = ListPlacesResponse(places = places)

  override suspend fun ListMutationLogs(request: ListMutationLogsRequest) =
    ListMutationLogsResponse(mutation_logs = mutationLogs)

  private fun unsupported(): Nothing =
    throw UnsupportedOperationException(
      "Not served by SeedUserDataService; test it in server/user-data-service"
    )

  private fun overviewOf(seed: SurveySeed): SurveyOverview =
    GrpcProtoMapper.surveyOverviewFromDef(
      surveyDef = seed.survey!!,
      acl = seed.acl,
      hasWorkingCopy = seed.working_copy != null,
    )
}
