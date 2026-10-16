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
package org.groundplatform.v2.server.userdata

import com.squareup.wire.Instant
import groundplatform.v2.data.AcquirePlotLockRequest
import groundplatform.v2.data.AcquirePlotLockResponse
import groundplatform.v2.data.AppliedMutationWatermark
import groundplatform.v2.data.AuditRecord
import groundplatform.v2.data.ConflictResolutionStatus
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.DataMutationResult
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.data.EntityCluster
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
import groundplatform.v2.data.ImpactAggregate
import groundplatform.v2.data.ImpactEvent as ImpactEventProto
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
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.PlaceRecord
import groundplatform.v2.data.PlotLock
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
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.ExportProfileDef
import groundplatform.v2.library.FormTemplateDef
import groundplatform.v2.library.GetLibraryRequest
import groundplatform.v2.library.GetLibraryResponse
import groundplatform.v2.library.GetResolvedLibraryRequest
import groundplatform.v2.library.GetResolvedLibraryResponse
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.MutateLibraryResponse
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.library.PurposePackDef
import groundplatform.v2.library.SearchConceptsRequest
import groundplatform.v2.library.SearchConceptsResponse
import groundplatform.v2.library.StreamLibrariesRequest
import groundplatform.v2.survey.AcceptSurveyInviteRequest
import groundplatform.v2.survey.AcceptSurveyInviteResponse
import groundplatform.v2.survey.AclEntry
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
import groundplatform.v2.survey.MembershipStatus
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateOrganizationResponse
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.MutateSurveyResponse
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.OrganizationRole
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.PeerDataVisibility
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.StreamOrganizationsRequest
import groundplatform.v2.survey.StreamSurveyOverviewsRequest
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyServiceServer
import groundplatform.v2.survey.SurveyState
import groundplatform.v2.userdata.UserDataSeed
import kotlinx.coroutines.channels.SendChannel
import org.groundplatform.v2.core.mutation.DataPatches
import org.groundplatform.v2.core.mutation.LibraryPatches
import org.groundplatform.v2.core.mutation.OrganizationPatches
import org.groundplatform.v2.core.mutation.SurveyPatches

/**
 * In-memory implementation of the user data services ([SurveyServiceServer],
 * [OrganizationServiceServer], [LibraryServiceServer], and [DataServiceServer]), initialized from a
 * [UserDataSeed]. State lives in this instance only: it isn't shared between server instances and
 * is lost on restart, so deploy a single instance until a database backs the service.
 *
 * Implements the server-side contract defined in `docs/technical/sync-engine.md`:
 * - Monotonically increasing authoritative `commit_timestamp` assigned to every initial record and
 *   every subsequent mutation.
 * - Bounded delta streams (`Stream*`) filtered by `commit_timestamp > since_timestamp` (and `<=
 *   until_timestamp` when set), ordered strictly by `commit_timestamp ASC`, and closed once caught
 *   up. Clients poll them while in the foreground.
 * - Query RPCs (`ListEntities`, `ListSubmissions`, `ListOrganizations`) with filtering, sorting,
 *   and offset-based paging.
 * - Soft-delete tombstones (`is_deleted = true` or `state = DELETED`) emitted when `include_deleted
 *   = true`.
 * - Idempotent mutations: each `mutation_id` is applied at most once (replays are skipped, so a
 *   stale retry can't overwrite newer writes), and the highest applied `client_seq` per
 *   `(client_id, scope)` is reported by `GetAppliedMutations`.
 * - Last-writer-wins field patches. `base_revision` / `base_version` only guard whole-record
 *   replacements (`upsert_*`), which fail with `FAILED_PRECONDITION` when stale; stale field
 *   patches on data records report `AUTO_MERGED`.
 * - Atomic `Mutate*` requests: a request that fails leaves no partial changes.
 * - 5-minute cooperative plot locks.
 */
class UserDataBackend(private val seed: UserDataSeed = UserDataSeed()) :
  SurveyServiceServer, OrganizationServiceServer, LibraryServiceServer, DataServiceServer {

  private var nextEpochSecond: Long = INITIAL_EPOCH_SECOND

  /** Allocates the next strictly increasing authoritative server `commit_timestamp`. */
  private fun nextCommitTimestamp(): Instant {
    nextEpochSecond += 1L
    return Instant.ofEpochSecond(nextEpochSecond, 0L)
  }

  /** Current highest committed server timestamp watermark. */
  fun currentWatermark(): Instant = Instant.ofEpochSecond(nextEpochSecond, 0L)

  private val organizationsById = linkedMapOf<String, Organization>()
  private val librariesByOrgId = linkedMapOf<String, OrganizationLibraryRecord>()
  private val surveysById = linkedMapOf<String, SurveyDef>()
  private val surveyWorkingCopiesById = linkedMapOf<String, SurveyDef>()
  private val surveyAclsById = linkedMapOf<String, SurveyAcl>()
  private val entitiesBySurveyAndId = linkedMapOf<String, LinkedHashMap<String, EntityRecord>>()
  private val submissionsBySurveyAndId =
    linkedMapOf<String, LinkedHashMap<String, SubmissionRecord>>()
  private val placesList = mutableListOf<PlaceRecord>()
  private val mutationLogsList = mutableListOf<MutationLogRecord>()
  private val impactEventsById = linkedMapOf<String, ImpactEventProto>()
  private val auditRecordsById = linkedMapOf<String, AuditRecord>()
  private val plotLocksByEntityKey = linkedMapOf<String, PlotLock>()
  private val appliedMutationIds = LinkedHashSet<String>()

  /** Highest applied `client_seq` keyed by `(client_id, scope)`. */
  private val appliedClientSeqs = linkedMapOf<Pair<String, String>, AppliedMutationWatermark>()

  /** Identity of one client mutation, used for deduplication and `client_seq` tracking. */
  private data class MutationIdentity(
    val mutationId: String,
    val clientId: String,
    val clientSeq: Long,
  )

  /** True when [identity] carries a `mutation_id` that has already been applied. */
  private fun isDuplicate(identity: MutationIdentity): Boolean =
    identity.mutationId.isNotBlank() && identity.mutationId in appliedMutationIds

  /**
   * The [mutations] that haven't been applied yet, dropping replays of earlier requests and repeats
   * of a `mutation_id` within the same request.
   */
  private fun <T> newMutations(mutations: List<T>, identityOf: (T) -> MutationIdentity): List<T> {
    val seen = HashSet<String>()
    return mutations.filter { mutation ->
      val identity = identityOf(mutation)
      !isDuplicate(identity) && (identity.mutationId.isBlank() || seen.add(identity.mutationId))
    }
  }

  /**
   * Fails with `FAILED_PRECONDITION` (via [IllegalStateException]) when a whole-record replacement
   * was based on a stale version. Unset base versions (`0` / blank) and new records always pass.
   */
  private fun checkBaseVersion(baseVersion: Long, currentVersion: Long?, what: String) {
    if (baseVersion == 0L || currentVersion == null) return
    check(baseVersion == currentVersion) {
      "Stale base version for $what: based on $baseVersion, current is $currentVersion"
    }
  }

  private fun checkBaseVersion(baseVersion: String, currentVersion: String?, what: String) {
    if (baseVersion.isBlank() || currentVersion == null) return
    check(baseVersion == currentVersion) {
      "Stale base version for $what: based on $baseVersion, current is $currentVersion"
    }
  }

  /**
   * Records [identities] as applied in [scope] at [ts]. Call only after the request's changes are
   * committed, so a failed request can be retried.
   */
  private fun recordApplied(scope: String, identities: List<MutationIdentity>, ts: Instant) {
    for (identity in identities) {
      if (identity.mutationId.isNotBlank()) appliedMutationIds += identity.mutationId
      if (identity.clientId.isBlank() || identity.clientSeq <= 0L) continue
      val key = identity.clientId to scope
      val previous = appliedClientSeqs[key]
      if (previous == null || identity.clientSeq > previous.client_seq) {
        appliedClientSeqs[key] =
          AppliedMutationWatermark(
            scope = scope,
            client_seq = identity.clientSeq,
            commit_timestamp = ts,
          )
      }
    }
  }

  init {
    reset()
  }

  /** Resets all state to [seed] and resumes the server clock after its latest commit timestamp. */
  fun reset() {
    organizationsById.clear()
    librariesByOrgId.clear()
    surveysById.clear()
    surveyWorkingCopiesById.clear()
    surveyAclsById.clear()
    entitiesBySurveyAndId.clear()
    submissionsBySurveyAndId.clear()
    placesList.clear()
    mutationLogsList.clear()
    impactEventsById.clear()
    auditRecordsById.clear()
    plotLocksByEntityKey.clear()
    appliedMutationIds.clear()
    appliedClientSeqs.clear()

    val timestamps = mutableListOf<Instant?>()
    seed.organizations.forEach {
      organizationsById[it.organization_id] = it
      timestamps += it.commit_timestamp
    }
    seed.libraries.forEach {
      librariesByOrgId[it.organization_id] = it
      timestamps += it.commit_timestamp
    }
    for (surveySeed in seed.surveys) {
      val survey = surveySeed.survey ?: continue
      val surveyId = survey.survey_id
      surveysById[surveyId] = survey
      surveySeed.acl?.let { surveyAclsById[surveyId] = it }
      surveySeed.working_copy?.let { surveyWorkingCopiesById[surveyId] = it }
      entitiesBySurveyAndId[surveyId] =
        surveySeed.entities.associateByTo(LinkedHashMap()) { it.entity_id }
      submissionsBySurveyAndId[surveyId] =
        surveySeed.submissions.associateByTo(LinkedHashMap()) { it.submission_id }
      timestamps += survey.commit_timestamp
      timestamps += surveySeed.acl?.commit_timestamp
      timestamps += surveySeed.entities.map { it.commit_timestamp }
      timestamps += surveySeed.submissions.map { it.commit_timestamp }
    }
    placesList += seed.places
    mutationLogsList += seed.mutation_logs
    timestamps += seed.mutation_logs.map { it.commit_timestamp }

    nextEpochSecond =
      maxOf(INITIAL_EPOCH_SECOND, timestamps.maxOfOrNull { it?.epochSecond ?: 0L } ?: 0L)
  }

  // ---------------------------------------------------------------------------
  // Timestamp comparison helpers (sync-engine.md)
  // ---------------------------------------------------------------------------

  private fun isWithinWindow(
    commitTimestamp: Instant?,
    sinceTimestamp: Instant?,
    untilTimestamp: Instant? = null,
  ): Boolean {
    val ts = commitTimestamp ?: return true
    if (sinceTimestamp != null && !isAfter(ts, sinceTimestamp)) return false
    if (untilTimestamp != null && isAfter(ts, untilTimestamp)) return false
    return true
  }

  private fun isAfter(a: Instant, b: Instant): Boolean =
    if (a.epochSecond != b.epochSecond) a.epochSecond > b.epochSecond else a.nano > b.nano

  private val instantComparator =
    Comparator<Instant?> { a, b ->
      when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        a.epochSecond != b.epochSecond -> a.epochSecond.compareTo(b.epochSecond)
        else -> a.nano.compareTo(b.nano)
      }
    }

  /**
   * Sends the records returned by [select] whose commit timestamp is after [since] (and not after
   * [until]) in `commit_timestamp ASC` order, then returns so the stream closes.
   */
  private suspend fun <T> sendDeltas(
    response: SendChannel<T>,
    since: Instant?,
    until: Instant?,
    commitTimestamp: (T) -> Instant?,
    select: () -> Collection<T>,
  ) {
    val batch =
      select()
        .filter { isWithinWindow(commitTimestamp(it), since, until) }
        .sortedWith { a, b -> instantComparator.compare(commitTimestamp(a), commitTimestamp(b)) }
    for (item in batch) {
      response.send(item)
    }
  }

  /** Applies `page_size` / `page_token` offset paging to an already filtered and sorted list. */
  private fun <T> page(items: List<T>, pageSize: Int, pageToken: String): Pair<List<T>, String> {
    val offset = pageToken.toIntOrNull() ?: 0
    if (pageSize <= 0) return items.drop(offset) to ""
    val end = minOf(offset + pageSize, items.size)
    val next = if (end < items.size) end.toString() else ""
    return items.subList(minOf(offset, items.size), end) to next
  }

  // ---------------------------------------------------------------------------
  // OrganizationServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetOrganization(request: GetOrganizationRequest): GetOrganizationResponse {
    val org =
      organizationsById[request.organization_id]
        ?: Organization(organization_id = request.organization_id)
    return GetOrganizationResponse(organization = org)
  }

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse {
    val q = request.query.trim().lowercase()
    val matches =
      organizationsById.values
        .filter { org ->
          org.listed &&
            !org.is_deleted &&
            (q.isEmpty() ||
              org.name.lowercase().contains(q) ||
              org.description.lowercase().contains(q))
        }
        .sortedBy { it.name.lowercase() }
    val (items, next) = page(matches, request.page_size, request.page_token)
    return ListOrganizationsResponse(organizations = items, next_page_token = next)
  }

  override suspend fun StreamOrganizations(
    request: StreamOrganizationsRequest,
    response: SendChannel<Organization>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      Organization::commit_timestamp,
    ) {
      organizationsById.values.filter { request.include_deleted || !it.is_deleted }
    }

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse {
    val orgId = request.organization_id
    val existing = organizationsById[orgId]
    val toApply =
      newMutations(request.mutations) {
        MutationIdentity(it.mutation_id, it.client_id, it.client_seq)
      }
    if (toApply.isEmpty()) {
      return MutateOrganizationResponse(
        organization_id = orgId,
        organization = existing,
        commit_timestamp = existing?.commit_timestamp ?: currentWatermark(),
      )
    }
    if (toApply.any { it.upsert_organization != null }) {
      checkBaseVersion(request.base_version, existing?.version, "organization $orgId")
    }

    val ts = nextCommitTimestamp()
    var current: Organization =
      existing ?: Organization(organization_id = orgId, version = 0L, commit_timestamp = ts)

    for (mutation in toApply) {
      val up = mutation.upsert_organization
      val patch = mutation.update_field
      when {
        mutation.delete_organization == true -> {
          current =
            current.copy(
              is_deleted = true,
              version = current.version + 1L,
              commit_timestamp = ts,
            )
        }
        up != null -> {
          current =
            up.copy(
              organization_id = orgId.ifBlank { up.organization_id },
              version = current.version + 1L,
              commit_timestamp = ts,
            )
        }
        patch != null -> {
          current = OrganizationPatches.applyField(current, patch)
        }
      }
    }

    current = current.copy(version = current.version + 1L, commit_timestamp = ts)
    organizationsById[current.organization_id] = current
    recordApplied(
      scope = "organizations/${current.organization_id}",
      identities = toApply.map { MutationIdentity(it.mutation_id, it.client_id, it.client_seq) },
      ts = ts,
    )
    return MutateOrganizationResponse(
      organization_id = current.organization_id,
      organization = current,
      commit_timestamp = ts,
    )
  }

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse {
    val ts = nextCommitTimestamp()
    val current =
      organizationsById[request.organization_id]
        ?: return JoinOrganizationResponse(organization = null, commit_timestamp = ts)
    require(request.email.isNotBlank()) { "email is required" }
    val email = request.email
    val status =
      if (request.invite_token.isNotBlank()) MembershipStatus.ACTIVE else MembershipStatus.REQUESTED
    val newMember =
      OrganizationMember(
        user_id = request.user_id,
        email = email,
        role = OrganizationRole.MEMBER,
        status = status,
        invite_token = request.invite_token,
      )
    val updated =
      current.copy(
        members =
          current.members.filterNot { it.email.equals(email, ignoreCase = true) } + newMember,
        version = current.version + 1L,
        commit_timestamp = ts,
      )
    organizationsById[request.organization_id] = updated
    return JoinOrganizationResponse(
      organization = updated,
      member = newMember,
      commit_timestamp = ts,
    )
  }

  // ---------------------------------------------------------------------------
  // LibraryServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse {
    val record =
      librariesByOrgId[request.organization_id]
        ?: OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle = LibraryBundle(),
          settings = OrganizationLibrarySettings(organization_id = request.organization_id),
          version = 1L,
          commit_timestamp = currentWatermark(),
        )
    return GetLibraryResponse(library = record)
  }

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse {
    val globalRecord = librariesByOrgId[ALL_USERS_ORGANIZATION_ID]
    val orgRecord =
      if (request.organization_id.isNotBlank()) librariesByOrgId[request.organization_id] else null
    val mergedConcepts = LinkedHashMap<String, ConceptDef>()
    val mergedTemplates = LinkedHashMap<String, FormTemplateDef>()
    val mergedPacks = LinkedHashMap<String, PurposePackDef>()
    val mergedProfiles = LinkedHashMap<String, ExportProfileDef>()

    globalRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    orgRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    return GetResolvedLibraryResponse(
      resolved_library =
        OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle =
            LibraryBundle(
              concepts = mergedConcepts.values.toList(),
              form_templates = mergedTemplates.values.toList(),
              purpose_packs = mergedPacks.values.toList(),
              export_profiles = mergedProfiles.values.toList(),
            ),
          settings = orgRecord?.settings ?: globalRecord?.settings,
          version = orgRecord?.version ?: 1L,
          commit_timestamp = currentWatermark(),
        )
    )
  }

  override suspend fun StreamLibraries(
    request: StreamLibrariesRequest,
    response: SendChannel<OrganizationLibraryRecord>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      OrganizationLibraryRecord::commit_timestamp,
    ) {
      librariesByOrgId.values.filter { lib ->
        (request.include_deleted || !lib.is_deleted) &&
          (request.organization_id.isBlank() || lib.organization_id == request.organization_id)
      }
    }

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse {
    val resolved =
      GetResolvedLibrary(GetResolvedLibraryRequest(organization_id = request.organization_id))
    val q = request.query.trim().lowercase()
    val matches =
      resolved.resolved_library?.bundle?.concepts.orEmpty().filter { c ->
        (request.data_type == DataType.DATA_TYPE_UNSPECIFIED || c.data_type == request.data_type) &&
          (q.isEmpty() ||
            c.id.lowercase().contains(q) ||
            c.label?.values?.values?.any { it.lowercase().contains(q) } == true)
      }
    return SearchConceptsResponse(concepts = matches)
  }

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse {
    val existing = librariesByOrgId[request.organization_id]
    val toApply =
      newMutations(request.mutations) {
        MutationIdentity(it.mutation_id, it.client_id, it.client_seq)
      }
    if (toApply.isEmpty()) {
      return MutateLibraryResponse(
        organization_id = request.organization_id,
        library = existing,
        commit_timestamp = existing?.commit_timestamp ?: currentWatermark(),
      )
    }
    if (toApply.any { it.upsert_library != null }) {
      checkBaseVersion(
        request.base_version,
        existing?.version,
        "library ${request.organization_id}",
      )
    }

    val ts = nextCommitTimestamp()
    var current =
      existing
        ?: OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle = LibraryBundle(),
          settings = OrganizationLibrarySettings(organization_id = request.organization_id),
          version = 0L,
          commit_timestamp = ts,
        )

    for (mutation in toApply) {
      val upLib = mutation.upsert_library
      val patch = mutation.update_field
      if (upLib != null) {
        current =
          upLib.copy(
            organization_id = request.organization_id,
            version = current.version + 1L,
            commit_timestamp = ts,
          )
      } else if (patch != null) {
        current = LibraryPatches.applyField(current, patch)
      }
    }

    current = current.copy(version = current.version + 1L, commit_timestamp = ts)
    librariesByOrgId[request.organization_id] = current
    recordApplied(
      scope = "libraries/${request.organization_id}",
      identities = toApply.map { MutationIdentity(it.mutation_id, it.client_id, it.client_seq) },
      ts = ts,
    )
    return MutateLibraryResponse(
      organization_id = request.organization_id,
      library = current,
      commit_timestamp = ts,
    )
  }

  // ---------------------------------------------------------------------------
  // SurveyServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun StreamSurveyOverviews(
    request: StreamSurveyOverviewsRequest,
    response: SendChannel<SurveyOverview>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      SurveyOverview::commit_timestamp,
    ) {
      surveysById.values
        .filter { s ->
          (request.include_deleted || s.state != SurveyState.DELETED) &&
            (request.organization_id.isBlank() || s.organization_id == request.organization_id)
        }
        .map { s ->
          overviewOf(
            surveyDef = s,
            acl = surveyAclsById[s.survey_id],
            hasWorkingCopy = surveyWorkingCopiesById.containsKey(s.survey_id),
          )
        }
    }

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse {
    val saved = surveysById[request.survey_id]
    val requestedVersion = request.saved_version
    val survey =
      when {
        request.working_copy == true -> surveyWorkingCopiesById[request.survey_id]
        // The static backend only keeps the latest saved revision.
        requestedVersion != null -> saved?.takeIf { it.version == requestedVersion }
        else -> saved
      }
    return GetSurveyResponse(survey = survey, acl = surveyAclsById[request.survey_id])
  }

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse =
    GetSurveyAclResponse(
      acl = surveyAclsById[request.survey_id] ?: SurveyAcl(survey_id = request.survey_id)
    )

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
    val surveyId = request.survey_id
    val toApply =
      newMutations(request.mutations) {
        MutationIdentity(it.mutation_id, it.client_id, it.client_seq)
      }
    if (toApply.isEmpty()) {
      val existingSaved = surveysById[surveyId]
      val existingWorkingCopy = surveyWorkingCopiesById[surveyId]
      return MutateSurveyResponse(
        survey_id = surveyId,
        survey = if (request.working_copy) existingWorkingCopy ?: existingSaved else existingSaved,
        acl = surveyAclsById[surveyId],
        commit_timestamp = existingSaved?.commit_timestamp ?: currentWatermark(),
      )
    }
    if (toApply.any { it.upsert_survey != null || it.upsert_acl != null }) {
      val baseTarget =
        if (request.working_copy) {
          surveyWorkingCopiesById[surveyId] ?: surveysById[surveyId]
        } else {
          surveysById[surveyId]
        }
      checkBaseVersion(request.base_version, baseTarget?.version, "survey $surveyId")
    }

    val ts = nextCommitTimestamp()
    var saved =
      surveysById[surveyId]
        ?: SurveyDef(
          survey_id = surveyId,
          state = SurveyState.PUBLISHED,
          saved_revision_number = 1L,
          commit_timestamp = ts,
        )
    var workingCopy = surveyWorkingCopiesById[surveyId]
    var currentAcl =
      surveyAclsById[surveyId] ?: SurveyAcl(survey_id = surveyId, commit_timestamp = ts)

    // Field and snapshot mutations apply to the revision selected by `request.working_copy`.
    fun target(): SurveyDef = if (request.working_copy) workingCopy ?: saved else saved
    fun setTarget(def: SurveyDef) {
      if (request.working_copy) workingCopy = def else saved = def
    }

    for (mutation in toApply) {
      val upSurvey = mutation.upsert_survey
      val upAcl = mutation.upsert_acl
      val patchField = mutation.update_field
      val patchRole = mutation.update_acl_role
      val patchAccess = mutation.update_access_rules
      when {
        mutation.delete_survey == true -> {
          saved = saved.copy(state = SurveyState.DELETED)
          workingCopy = null
        }
        upSurvey != null -> setTarget(upSurvey.copy(survey_id = surveyId))
        upAcl != null -> currentAcl = upAcl.copy(survey_id = surveyId)
        mutation.save_working_copy == true -> {
          val toSave = workingCopy
          if (toSave != null) {
            val number = saved.saved_revision_number + 1L
            saved = toSave.copy(saved_revision_number = number, version = number.toString())
            workingCopy = null
          }
        }
        mutation.discard_working_copy == true -> workingCopy = null
        patchField != null -> setTarget(SurveyPatches.applyField(target(), patchField))
        patchRole != null -> currentAcl = SurveyPatches.applyAclRole(currentAcl, patchRole)
        patchAccess != null -> currentAcl = SurveyPatches.applyAccessRules(currentAcl, patchAccess)
      }
    }

    // Always stamp the saved revision so overview streams see working-copy and access changes.
    saved = saved.copy(commit_timestamp = ts)
    surveysById[surveyId] = saved
    surveyAclsById[surveyId] = currentAcl.copy(commit_timestamp = ts)
    val finalWorkingCopy = workingCopy?.copy(commit_timestamp = ts)
    if (finalWorkingCopy != null) {
      surveyWorkingCopiesById[surveyId] = finalWorkingCopy
    } else {
      surveyWorkingCopiesById.remove(surveyId)
    }
    recordApplied(
      scope = "surveys/$surveyId",
      identities = toApply.map { MutationIdentity(it.mutation_id, it.client_id, it.client_seq) },
      ts = ts,
    )

    return MutateSurveyResponse(
      survey_id = surveyId,
      survey = if (request.working_copy) finalWorkingCopy ?: saved else saved,
      acl = surveyAclsById[surveyId],
      commit_timestamp = ts,
    )
  }

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse {
    val ts = nextCommitTimestamp()
    val acl = surveyAclsById[request.survey_id] ?: SurveyAcl(survey_id = request.survey_id)
    val updatedEntries =
      acl.entries.map { entry ->
        if (entry.invite_token == request.invite_token) {
          entry.copy(
            invitation_status = AclEntry.InvitationStatus.ACCEPTED,
            user_id = request.user_id.ifBlank { entry.user_id },
            profile = request.profile ?: entry.profile,
          )
        } else {
          entry
        }
      }
    val updatedAcl = acl.copy(entries = updatedEntries, commit_timestamp = ts)
    surveyAclsById[request.survey_id] = updatedAcl
    surveysById[request.survey_id]?.let { s ->
      surveysById[request.survey_id] = s.copy(commit_timestamp = ts)
    }
    return AcceptSurveyInviteResponse(
      survey = surveysById[request.survey_id],
      acl = updatedAcl,
      commit_timestamp = ts,
    )
  }

  // ---------------------------------------------------------------------------
  // DataServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse {
    val entity =
      entitiesBySurveyAndId[request.survey_id]?.get(request.entity_id)
        ?: EntityRecord(survey_id = request.survey_id, entity_id = request.entity_id)
    return GetEntityResponse(entity = entity)
  }

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
    val watermark = currentWatermark()
    val matches =
      entitiesBySurveyAndId[request.survey_id]
        ?.values
        .orEmpty()
        .filter { e ->
          (request.include_deleted || !e.is_deleted) &&
            (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id) &&
            request.filter.all { (key, value) -> e.properties[key]?.string_value == value }
        }
        .let { list ->
          when (request.order_by.trim().lowercase()) {
            "label" -> list.sortedBy { it.label }
            "label desc" -> list.sortedByDescending { it.label }
            "commit_timestamp desc" ->
              list.sortedWith { a, b ->
                instantComparator.compare(b.commit_timestamp, a.commit_timestamp)
              }
            else ->
              list.sortedWith { a, b ->
                instantComparator.compare(a.commit_timestamp, b.commit_timestamp)
              }
          }
        }
    val (items, next) = page(matches, request.page_size, request.page_token)
    return ListEntitiesResponse(
      entities = items,
      next_page_token = next,
      server_watermark = watermark,
    )
  }

  override suspend fun StreamEntities(
    request: StreamEntitiesRequest,
    response: SendChannel<EntityRecord>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      EntityRecord::commit_timestamp,
    ) {
      val source =
        if (request.survey_id.isNotBlank()) {
          entitiesBySurveyAndId[request.survey_id]?.values.orEmpty()
        } else {
          entitiesBySurveyAndId.values.flatMap { it.values }
        }
      source.filter { e ->
        (request.include_deleted || !e.is_deleted) &&
          (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id)
      }
    }

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse {
    val entities =
      entitiesBySurveyAndId[request.survey_id]
        ?.values
        ?.filter {
          !it.is_deleted && (request.dataset_id.isBlank() || it.dataset_id == request.dataset_id)
        }
        .orEmpty()
    if (entities.isEmpty()) return ListEntityClustersResponse()
    val cluster =
      EntityCluster(
        s2_cell_token = "10d96b",
        centroid = GeoPoint(latitude = -0.4192, longitude = 36.9498),
        total_count = entities.size,
      )
    return ListEntityClustersResponse(clusters = listOf(cluster))
  }

  override suspend fun GetSubmission(request: GetSubmissionRequest): GetSubmissionResponse {
    val sub =
      submissionsBySurveyAndId[request.survey_id]?.get(request.submission_id)
        ?: SubmissionRecord(survey_id = request.survey_id, submission_id = request.submission_id)
    return GetSubmissionResponse(submission = sub)
  }

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse {
    val watermark = currentWatermark()
    val matches =
      submissionsBySurveyAndId[request.survey_id]
        ?.values
        .orEmpty()
        .filter { sub ->
          (request.include_deleted || !sub.is_deleted) &&
            (request.form_id.isBlank() || sub.form_id == request.form_id) &&
            (request.entity_id.isBlank() || sub.entity_id == request.entity_id) &&
            request.filter.all { (key, value) ->
              when (key) {
                "collector_email" -> sub.collector_email == value
                "collector_name" -> sub.collector_name == value
                else -> true
              }
            }
        }
        .let { list ->
          when (request.order_by.trim().lowercase()) {
            "collector_name" -> list.sortedBy { it.collector_name }
            "collector_name desc" -> list.sortedByDescending { it.collector_name }
            "commit_timestamp desc" ->
              list.sortedWith { a, b ->
                instantComparator.compare(b.commit_timestamp, a.commit_timestamp)
              }
            else ->
              list.sortedWith { a, b ->
                instantComparator.compare(a.commit_timestamp, b.commit_timestamp)
              }
          }
        }
    val (items, next) = page(matches, request.page_size, request.page_token)
    return ListSubmissionsResponse(
      submissions = items,
      next_page_token = next,
      server_watermark = watermark,
    )
  }

  override suspend fun StreamSubmissions(
    request: StreamSubmissionsRequest,
    response: SendChannel<SubmissionRecord>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      SubmissionRecord::commit_timestamp,
    ) {
      val source =
        if (request.survey_id.isNotBlank()) {
          submissionsBySurveyAndId[request.survey_id]?.values.orEmpty()
        } else {
          submissionsBySurveyAndId.values.flatMap { it.values }
        }
      source.filter { sub ->
        (request.include_deleted || !sub.is_deleted) &&
          (request.form_id.isBlank() || sub.form_id == request.form_id) &&
          (request.entity_id.isBlank() || sub.entity_id == request.entity_id)
      }
    }

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
    val results = mutableListOf<DataMutationResult>()
    var batchCommitTimestamp = currentWatermark()
    val surveyId = request.survey_id
    // Apply to copies and commit only once every mutation succeeded, so a failed request leaves no
    // partial changes and can be retried as a whole.
    val entityMap = LinkedHashMap(entitiesBySurveyAndId[surveyId].orEmpty())
    val subMap = LinkedHashMap(submissionsBySurveyAndId[surveyId].orEmpty())
    val applied = mutableListOf<MutationIdentity>()
    val seenInRequest = HashSet<String>()

    for (mutation in request.mutations) {
      val identity = MutationIdentity(mutation.mutation_id, mutation.client_id, mutation.client_seq)
      val repeated = identity.mutationId.isNotBlank() && !seenInRequest.add(identity.mutationId)
      if (isDuplicate(identity) || repeated) {
        results += duplicateDataResult(mutation, entityMap, subMap)
        continue
      }
      val ts = nextCommitTimestamp()
      batchCommitTimestamp = ts
      results += applyDataMutation(surveyId, mutation, entityMap, subMap, ts)
      applied += identity
    }

    if (applied.isNotEmpty()) {
      entitiesBySurveyAndId[surveyId] = entityMap
      submissionsBySurveyAndId[surveyId] = subMap
      recordApplied(
        scope = "surveys/$surveyId/data",
        identities = applied,
        ts = batchCommitTimestamp,
      )
    }
    return MutateDataResponse(results = results, commit_timestamp = batchCommitTimestamp)
  }

  /** Result for a replayed `mutation_id`: the target record's current state, unchanged. */
  private fun duplicateDataResult(
    mutation: DataMutation,
    entityMap: Map<String, EntityRecord>,
    subMap: Map<String, SubmissionRecord>,
  ): DataMutationResult {
    val entity = mutation.entity_mutation?.let { entityMap[it.entity_id] }
    val submission = mutation.submission_mutation?.let { subMap[it.submission_id] }
    return DataMutationResult(
      entity = entity,
      submission = if (entity == null) submission else null,
      conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN,
      commit_timestamp = entity?.commit_timestamp ?: submission?.commit_timestamp,
      duplicate = true,
    )
  }

  /**
   * Conflict status for a field patch based on [baseRevision] against the stored [currentRevision]:
   * patches always apply (last writer wins), but report `AUTO_MERGED` when based on an older
   * revision.
   */
  private fun patchConflictStatus(baseRevision: Long, currentRevision: Long) =
    if (baseRevision in 1L until currentRevision) {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_AUTO_MERGED
    } else {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
    }

  private fun applyDataMutation(
    surveyId: String,
    mutation: DataMutation,
    entityMap: LinkedHashMap<String, EntityRecord>,
    subMap: LinkedHashMap<String, SubmissionRecord>,
    ts: Instant,
  ): DataMutationResult {
    val em = mutation.entity_mutation
    val sm = mutation.submission_mutation
    return when {
      em != null -> {
        val existing =
          entityMap[em.entity_id]
            ?: EntityRecord(
              entity_id = em.entity_id,
              dataset_id = em.dataset_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val emUpsert = em.upsert
        val emUpdate = em.update
        if (emUpsert != null) {
          checkBaseVersion(
            em.base_revision,
            entityMap[em.entity_id]?.revision,
            "entity ${em.entity_id}",
          )
        }
        val updated: EntityRecord =
          when {
            em.delete == true ->
              existing.copy(
                revision = existing.revision + 1L,
                is_deleted = true,
                commit_timestamp = ts,
              )
            emUpsert != null ->
              emUpsert.copy(
                entity_id = em.entity_id.ifBlank { emUpsert.entity_id },
                dataset_id = em.dataset_id.ifBlank { emUpsert.dataset_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                commit_timestamp = ts,
                sync_status_key = "SYNCED",
              )
            emUpdate != null ->
              DataPatches.applyEntity(existing, emUpdate)
                .copy(
                  revision = existing.revision + 1L,
                  commit_timestamp = ts,
                  sync_status_key = "SYNCED",
                )
            else ->
              existing.copy(
                revision = existing.revision + 1L,
                commit_timestamp = ts,
                sync_status_key = "SYNCED",
              )
          }
        entityMap[updated.entity_id] = updated
        DataMutationResult(
          entity = updated,
          conflict_status = patchConflictStatus(em.base_revision, existing.revision),
          commit_timestamp = ts,
        )
      }
      sm != null -> {
        val existing =
          subMap[sm.submission_id]
            ?: SubmissionRecord(
              submission_id = sm.submission_id,
              form_id = sm.form_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val smUpsert = sm.upsert
        val smUpdate = sm.update
        if (smUpsert != null) {
          checkBaseVersion(
            sm.base_revision,
            subMap[sm.submission_id]?.revision,
            "submission ${sm.submission_id}",
          )
        }
        val updated: SubmissionRecord =
          when {
            sm.delete == true ->
              existing.copy(
                revision = existing.revision + 1L,
                is_deleted = true,
                commit_timestamp = ts,
              )
            smUpsert != null ->
              smUpsert.copy(
                submission_id = sm.submission_id.ifBlank { smUpsert.submission_id },
                form_id = sm.form_id.ifBlank { smUpsert.form_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                commit_timestamp = ts,
                sync_status_key = "SYNCED",
              )
            smUpdate != null ->
              DataPatches.applySubmission(existing, smUpdate)
                .copy(
                  revision = existing.revision + 1L,
                  commit_timestamp = ts,
                  sync_status_key = "SYNCED",
                )
            else ->
              existing.copy(
                revision = existing.revision + 1L,
                commit_timestamp = ts,
                sync_status_key = "SYNCED",
              )
          }
        subMap[updated.submission_id] = updated
        DataMutationResult(
          submission = updated,
          conflict_status = patchConflictStatus(sm.base_revision, existing.revision),
          commit_timestamp = ts,
        )
      }
      else ->
        DataMutationResult(
          conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN,
          commit_timestamp = ts,
        )
    }
  }

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse {
    val ts = nextCommitTimestamp()
    val key = "${request.survey_id}:${request.entity_id}"
    val existing = plotLocksByEntityKey[key]
    val nowSec = ts.epochSecond
    if (
      existing != null &&
        existing.locked_by_user_id != request.user_id &&
        (existing.expires_at?.epochSecond ?: 0L) > nowSec
    ) {
      val ent = entitiesBySurveyAndId[request.survey_id]?.get(request.entity_id)
      return AcquirePlotLockResponse(entity = ent, lock = existing)
    }
    val lock =
      PlotLock(
        entity_id = request.entity_id,
        locked_by_user_id = request.user_id,
        locked_at = ts,
        expires_at = Instant.ofEpochSecond(nowSec + 300L, 0L),
      )
    plotLocksByEntityKey[key] = lock
    val updatedEntity =
      entitiesBySurveyAndId[request.survey_id]?.get(request.entity_id)?.let { ent ->
        val withLock = ent.copy(active_lock = lock, commit_timestamp = ts)
        entitiesBySurveyAndId[request.survey_id]!![request.entity_id] = withLock
        withLock
      }
    return AcquirePlotLockResponse(entity = updatedEntity, lock = lock)
  }

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse {
    val ts = nextCommitTimestamp()
    val key = "${request.survey_id}:${request.entity_id}"
    plotLocksByEntityKey.remove(key)
    entitiesBySurveyAndId[request.survey_id]?.get(request.entity_id)?.let { ent ->
      entitiesBySurveyAndId[request.survey_id]!![request.entity_id] =
        ent.copy(active_lock = null, commit_timestamp = ts)
    }
    return ReleasePlotLockResponse(released = true)
  }

  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse =
    PrepareMediaUploadResponse(
      upload_url =
        "https://storage.collectone.groundplatform.org/upload/${request.survey_id}/${request.sha256_hash}",
      media_uri = "gs://collect-one-media/${request.survey_id}/${request.sha256_hash}",
      already_exists = false,
    )

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse =
    GetMediaDownloadUrlResponse(
      download_url =
        "https://storage.collectone.groundplatform.org/download/${request.survey_id}/${request.media_uri}"
    )

  override suspend fun RecordImpactEvents(
    request: RecordImpactEventsRequest
  ): RecordImpactEventsResponse {
    var ts = currentWatermark()
    val stampedEvents = mutableListOf<ImpactEventProto>()
    for (event in request.events) {
      ts = nextCommitTimestamp()
      val stamped = event.copy(commit_timestamp = ts)
      impactEventsById[stamped.event_id] = stamped
      stampedEvents += stamped
    }
    return RecordImpactEventsResponse(events = stampedEvents, commit_timestamp = ts)
  }

  override suspend fun StreamImpactEvents(
    request: StreamImpactEventsRequest,
    response: SendChannel<ImpactEventProto>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      ImpactEventProto::commit_timestamp,
    ) {
      impactEventsById.values.filter { ev ->
        (request.survey_id.isBlank() || ev.survey_id == request.survey_id) &&
          (request.organization_id.isBlank() || ev.organization_id == request.organization_id)
      }
    }

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse {
    val activeSurveys =
      surveysById.values.filter { s ->
        s.state != SurveyState.DELETED &&
          (request.survey_id.isBlank() || s.survey_id == request.survey_id) &&
          (request.organization_id.isBlank() || s.organization_id == request.organization_id)
      }
    var totalEntities = 0L
    var totalAreaHa = 0.0
    for (s in activeSurveys) {
      val ents = entitiesBySurveyAndId[s.survey_id]?.values?.filter { !it.is_deleted }.orEmpty()
      totalEntities += ents.size
      totalAreaHa += ents.sumOf { it.area_hectares }
    }
    return GetImpactAggregatesResponse(
      aggregates =
        listOf(
          ImpactAggregate(
            run_id = "run-static-1",
            pipeline_version = 1,
            computed_at = currentWatermark(),
            scope_type =
              if (request.survey_id.isNotBlank()) ImpactAggregate.ScopeType.SURVEY
              else if (request.organization_id.isNotBlank()) ImpactAggregate.ScopeType.ORGANIZATION
              else ImpactAggregate.ScopeType.GLOBAL,
            scope_id = request.survey_id.ifBlank { request.organization_id },
            metric = ImpactAggregate.Metric.FEATURES,
            feature_count = totalEntities,
            area_ha = totalAreaHa,
          )
        )
    )
  }

  override suspend fun StreamAuditRecords(
    request: StreamAuditRecordsRequest,
    response: SendChannel<AuditRecord>,
  ) =
    sendDeltas(
      response,
      request.since_timestamp,
      request.until_timestamp,
      AuditRecord::commit_timestamp,
    ) {
      auditRecordsById.values.filter { a ->
        (request.survey_id.isBlank() || a.survey_id == request.survey_id) &&
          (request.target_id.isBlank() || a.target_id == request.target_id)
      }
    }

  override suspend fun ListPlaces(request: ListPlacesRequest): ListPlacesResponse {
    val q = request.query.trim().lowercase()
    val filtered =
      if (q.isEmpty()) {
        placesList.toList()
      } else {
        placesList.filter {
          it.name.lowercase().contains(q) || it.region_subtitle.lowercase().contains(q)
        }
      }
    return ListPlacesResponse(places = filtered)
  }

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse {
    val filtered = mutationLogsList.filter {
      (request.survey_id.isBlank() || it.survey_id == request.survey_id) &&
        isWithinWindow(it.commit_timestamp, request.since_timestamp)
    }
    return ListMutationLogsResponse(
      mutation_logs = filtered,
      server_watermark = currentWatermark(),
    )
  }

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse {
    require(request.client_id.isNotBlank()) { "client_id is required" }
    val scopes = request.scopes.toSet()
    return GetAppliedMutationsResponse(
      watermarks =
        appliedClientSeqs
          .filterKeys { (clientId, scope) ->
            clientId == request.client_id && (scopes.isEmpty() || scope in scopes)
          }
          .values
          .toList()
    )
  }

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `StreamSurveyOverviews` clients.
   */
  private fun overviewOf(
    surveyDef: SurveyDef,
    acl: SurveyAcl?,
    hasWorkingCopy: Boolean,
  ): SurveyOverview =
    SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      state = surveyDef.state,
      version = surveyDef.version,
      saved_revision_number = surveyDef.saved_revision_number,
      location_label = surveyDef.location_label,
      coordinates_label = surveyDef.coordinates_label,
      offline_size_label = surveyDef.offline_size_label,
      is_downloaded = surveyDef.is_downloaded,
      thumbnail_theme = surveyDef.thumbnail_theme,
      entity_count = surveyDef.entity_count,
      survey_area = surveyDef.map_config?.survey_area,
      outcome = surveyDef.outcome,
      closed_at = surveyDef.closed_at,
      closed_at_iso = surveyDef.closed_at_iso,
      commit_timestamp =
        listOfNotNull(surveyDef.commit_timestamp, acl?.commit_timestamp)
          .maxWithOrNull(instantComparator),
      has_working_copy = hasWorkingCopy,
      sharing_policy = acl?.sharing_policy ?: SharingPolicy.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
    )

  companion object {
    /** Server clock start when the seed has no timestamps. */
    const val INITIAL_EPOCH_SECOND: Long = 1_770_000_000L

    /** Organization whose library is the global library shared by all users. */
    const val ALL_USERS_ORGANIZATION_ID: String = "org-all-users"
  }
}
