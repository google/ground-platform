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
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.data.EntityRecord
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
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.PrepareMediaUploadRequest
import groundplatform.v2.data.PrepareMediaUploadResponse
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.RecordImpactEventsResponse
import groundplatform.v2.data.ReleasePlotLockRequest
import groundplatform.v2.data.ReleasePlotLockResponse
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.GetLibraryRequest
import groundplatform.v2.library.GetLibraryResponse
import groundplatform.v2.library.GetResolvedLibraryRequest
import groundplatform.v2.library.GetResolvedLibraryResponse
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.library.ListLibrariesRequest
import groundplatform.v2.library.ListLibrariesResponse
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.MutateLibraryResponse
import groundplatform.v2.library.OrganizationLibraryRecord
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
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyServiceServer
import groundplatform.v2.survey.SurveyState
import groundplatform.v2.userdata.SurveySeed
import groundplatform.v2.userdata.UserDataSeed
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.client.grpc.GrpcWebException
import org.groundplatform.v2.app.client.grpc.GrpcWebTransport
import org.groundplatform.v2.app.data.seed.UserDataSeedGenerator

/**
 * Read-only, in-memory stand-in for the user data service, serving a [UserDataSeed] through the
 * sync RPCs the client uses (`List*` in query and delta modes, `GetSurvey`, `GetSurveyAcl`,
 * `ListPlaces`). Other RPCs throw; their behavior is tested in `server/user-data-service`.
 *
 * Calls never suspend, so tests can run sync code inside `runDirect`. Tests simulate changes made
 * by other clients with [putEntity], [deleteEntity], [removeEntity], [deleteLibrary], and
 * [removeLibrary], limit page sizes with [maxPageSize], expire every sync token with
 * [invalidateSyncTokens], and inspect the `List*` requests the client sent in [listRequests].
 *
 * Its sync tokens use a format of its own: like the real service's, they are opaque to clients.
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
        seed.libraries.map { it.commit_timestamp } +
        seed.surveys.flatMap { s ->
          listOf(s.survey?.commit_timestamp, s.acl?.commit_timestamp) +
            s.entities.map { it.commit_timestamp } +
            s.submissions.map { it.commit_timestamp }
        } +
        seed.mutation_logs.map { it.commit_timestamp })
      .maxOfOrNull { it?.getEpochSecond() ?: 0L } ?: 0L

  /** Largest page any `List*` call returns, so tests can exercise paging. */
  var maxPageSize: Int = Int.MAX_VALUE

  /** Every `List*` request received, oldest first. */
  val listRequests: MutableList<Any> = mutableListOf()

  /** Data epoch embedded in sync tokens; bumping it makes the service reject older tokens. */
  private var epoch = 1

  /** Makes every sync token issued so far fail with `FAILED_PRECONDITION`. */
  fun invalidateSyncTokens() {
    epoch++
  }

  /** A client of this service. */
  fun client(): CollectOneGrpcClient =
    CollectOneGrpcClient(
      surveyService = this,
      organizationService = this,
      libraryService = this,
      dataService = this,
    )

  /**
   * Adds or replaces an entity as if committed by another client, and returns its timestamp. Like
   * the real service, the write bumps the entity's `revision` past the stored one.
   */
  fun putEntity(surveyId: String, entity: EntityRecord): Instant {
    val ts = nextTimestamp()
    updateSurvey(surveyId) { s ->
      val previous = s.entities.firstOrNull { it.entity_id == entity.entity_id }
      s.copy(
        entities =
          s.entities.filterNot { it.entity_id == entity.entity_id } +
            entity.copy(
              survey_id = surveyId,
              revision = (previous?.revision ?: 0L) + 1L,
              commit_timestamp = ts,
            )
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
            if (it.entity_id == entityId) {
              it.copy(is_deleted = true, revision = it.revision + 1L, commit_timestamp = ts)
            } else {
              it
            }
          }
      )
    }
    return ts
  }

  /**
   * Removes an entity without leaving a tombstone, as a server data reset would. Clients only
   * notice through a full sync, so call [invalidateSyncTokens] too.
   */
  fun removeEntity(surveyId: String, entityId: String) {
    updateSurvey(surveyId) { s ->
      s.copy(entities = s.entities.filterNot { it.entity_id == entityId })
    }
  }

  /** Soft-deletes an organization's library as if deleted by another client. */
  fun deleteLibrary(organizationId: String): Instant {
    val ts = nextTimestamp()
    val index = libraries.indexOfFirst { it.organization_id == organizationId }
    check(index >= 0) { "Unknown library $organizationId" }
    libraries[index] =
      libraries[index].let {
        it.copy(is_deleted = true, version = it.version + 1L, commit_timestamp = ts)
      }
    return ts
  }

  /**
   * Removes an organization's library without leaving a tombstone, as a server data reset would.
   * Clients only notice through a full sync, so call [invalidateSyncTokens] too.
   */
  fun removeLibrary(organizationId: String) {
    libraries.removeAll { it.organization_id == organizationId }
  }

  private fun nextTimestamp(): Instant = ofEpochSecond(++latestEpochSecond, 0L)

  private fun updateSurvey(surveyId: String, update: (SurveySeed) -> SurveySeed) {
    surveys[surveyId] = update(checkNotNull(surveys[surveyId]) { "Unknown survey $surveyId" })
  }

  /** One page of a `List*` response. */
  private class Page<T>(val records: List<T>, val nextPageToken: String, val nextSyncToken: String)

  /**
   * Serves one `List*` page over [records] (every record in scope, including tombstones). Query
   * mode returns the records matching [inQuery] in commit order with offset paging; delta mode
   * returns the records committed after the token, including tombstones.
   */
  private fun <T> list(
    scope: String,
    records: List<T>,
    timestamp: (T) -> Instant?,
    id: (T) -> String,
    pageSize: Int,
    pageToken: String,
    syncToken: String,
    inQuery: (T) -> Boolean = { true },
  ): Page<T> {
    val size = if (pageSize <= 0) maxPageSize else minOf(pageSize, maxPageSize)
    val ordered = records.sortedWith(compareBy({ secondsOf(timestamp(it)) }, { id(it) }))
    if (syncToken.isNotEmpty()) {
      val (second, afterId) = decode(pageToken.ifEmpty { syncToken }, scope, DELTA)
      val changed = ordered.filter {
        val ts = secondsOf(timestamp(it))
        ts > second.toLong() || (ts == second.toLong() && afterId != ALL && id(it) > afterId)
      }
      val page = changed.take(size)
      val last = page.lastOrNull()
      val token =
        if (last == null) encode(scope, DELTA, second, afterId)
        else encode(scope, DELTA, secondsOf(timestamp(last)).toString(), id(last))
      return if (changed.size > size) Page(page, token, "") else Page(page, "", token)
    }
    val (snapshot, offsetText) =
      if (pageToken.isEmpty()) latestEpochSecond.toString() to "0"
      else decode(pageToken, scope, QUERY)
    val matching = ordered.filter(inQuery)
    val offset = offsetText.toInt()
    val page = matching.drop(offset).take(size)
    return if (offset + page.size < matching.size) {
      Page(page, encode(scope, QUERY, snapshot, (offset + page.size).toString()), "")
    } else {
      Page(page, "", encode(scope, DELTA, snapshot, ALL))
    }
  }

  private fun secondsOf(timestamp: Instant?): Long = timestamp?.getEpochSecond() ?: 0L

  private fun encode(scope: String, kind: String, first: String, second: String) =
    listOf("seed", epoch.toString(), kind, scope, first, second).joinToString("|")

  /** The two kind-specific fields of [token], after validating it like the real service does. */
  private fun decode(token: String, scope: String, kind: String): Pair<String, String> {
    val fields = token.split("|")
    if (fields.size != 6 || fields[0] != "seed" || fields[2] != kind) {
      throw GrpcWebException(GrpcWebTransport.INVALID_ARGUMENT, scope, "Malformed token")
    }
    if (fields[1] != epoch.toString()) {
      throw GrpcWebException(GrpcWebTransport.FAILED_PRECONDITION, scope, "Token expired")
    }
    if (fields[3] != scope) {
      throw GrpcWebException(GrpcWebTransport.INVALID_ARGUMENT, scope, "Token scope mismatch")
    }
    return fields[4] to fields[5]
  }

  // SurveyService

  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse {
    listRequests += request
    val page =
      list(
        scope = "surveys/${request.organization_id}",
        records =
          surveys.values
            .filter {
              request.organization_id.isBlank() ||
                it.survey!!.organization_id == request.organization_id
            }
            .map(::overviewOf),
        timestamp = SurveyOverview::commit_timestamp,
        id = SurveyOverview::survey_id,
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
        inQuery = { it.state != SurveyState.DELETED },
      )
    return ListSurveyOverviewsResponse(page.records, page.nextPageToken, page.nextSyncToken)
  }

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

  override suspend fun SearchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse = unsupported()

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse {
    listRequests += request
    val page =
      list(
        scope = "organizations",
        records = organizations,
        timestamp = { it.commit_timestamp },
        id = { it.organization_id },
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
        inQuery = { !it.is_deleted },
      )
    return ListOrganizationsResponse(
      organizations = page.records,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

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

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse {
    listRequests += request
    val page =
      list(
        scope = "libraries/${request.organization_id}",
        records =
          libraries.filter {
            request.organization_id.isBlank() || it.organization_id == request.organization_id
          },
        timestamp = OrganizationLibraryRecord::commit_timestamp,
        id = OrganizationLibraryRecord::organization_id,
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
        inQuery = { !it.is_deleted },
      )
    return ListLibrariesResponse(page.records, page.nextPageToken, page.nextSyncToken)
  }

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

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
    listRequests += request
    val page =
      list(
        scope = "entities/${request.survey_id}/${request.dataset_id}",
        records =
          surveys[request.survey_id]?.entities.orEmpty().filter {
            request.dataset_id.isBlank() || it.dataset_id == request.dataset_id
          },
        timestamp = EntityRecord::commit_timestamp,
        id = EntityRecord::entity_id,
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
        inQuery = { request.include_deleted || !it.is_deleted },
      )
    return ListEntitiesResponse(
      entities = page.records,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

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

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse {
    listRequests += request
    val page =
      list(
        scope = "submissions/${request.survey_id}/${request.form_id}/${request.entity_id}",
        records =
          surveys[request.survey_id]?.submissions.orEmpty().filter {
            (request.form_id.isBlank() || it.form_id == request.form_id) &&
              (request.entity_id.isBlank() || it.entity_id == request.entity_id)
          },
        timestamp = SubmissionRecord::commit_timestamp,
        id = SubmissionRecord::submission_id,
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
        inQuery = { request.include_deleted || !it.is_deleted },
      )
    return ListSubmissionsResponse(
      submissions = page.records,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

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

  override suspend fun ListImpactEvents(
    request: ListImpactEventsRequest
  ): ListImpactEventsResponse {
    listRequests += request
    val page =
      list(
        scope = "impact_events/${request.survey_id}/${request.organization_id}",
        records = emptyList<groundplatform.v2.data.ImpactEvent>(),
        timestamp = { it.commit_timestamp },
        id = { it.event_id },
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
      )
    return ListImpactEventsResponse(page.records, page.nextPageToken, page.nextSyncToken)
  }

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse = unsupported()

  override suspend fun ListAuditRecords(
    request: ListAuditRecordsRequest
  ): ListAuditRecordsResponse = unsupported()

  override suspend fun ListPlaces(request: ListPlacesRequest) = ListPlacesResponse(places = places)

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse {
    listRequests += request
    val page =
      list(
        scope = "mutation_logs/${request.survey_id}",
        records =
          mutationLogs.filter { request.survey_id.isBlank() || it.survey_id == request.survey_id },
        timestamp = MutationLogRecord::commit_timestamp,
        id = MutationLogRecord::id,
        pageSize = request.page_size,
        pageToken = request.page_token,
        syncToken = request.sync_token,
      )
    return ListMutationLogsResponse(
      mutation_logs = page.records,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse = GetAppliedMutationsResponse()

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

  private companion object {
    const val DELTA = "D"
    const val QUERY = "Q"
    const val ALL = "*"
  }
}
