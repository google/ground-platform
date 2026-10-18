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
import groundplatform.v2.data.PlotLock
import groundplatform.v2.data.PrepareMediaUploadRequest
import groundplatform.v2.data.PrepareMediaUploadResponse
import groundplatform.v2.data.QueueFilterMode
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.RecordImpactEventsResponse
import groundplatform.v2.data.ReleasePlotLockRequest
import groundplatform.v2.data.ReleasePlotLockResponse
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
import groundplatform.v2.library.ListLibrariesRequest
import groundplatform.v2.library.ListLibrariesResponse
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.MutateLibraryResponse
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.library.PurposePackDef
import groundplatform.v2.library.SearchConceptsRequest
import groundplatform.v2.library.SearchConceptsResponse
import groundplatform.v2.survey.AcceptSurveyInviteRequest
import groundplatform.v2.survey.AcceptSurveyInviteResponse
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.BatchGetSurveyOverviewsRequest
import groundplatform.v2.survey.BatchGetSurveyOverviewsResponse
import groundplatform.v2.survey.CostUnitWeights
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
import groundplatform.v2.survey.QuotaLimits
import groundplatform.v2.survey.QuotaTier
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SearchOrganizationsRequest
import groundplatform.v2.survey.SearchOrganizationsResponse
import groundplatform.v2.survey.SearchPublicSurveysRequest
import groundplatform.v2.survey.SearchPublicSurveysResponse
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyOverviewResult
import groundplatform.v2.survey.SurveyServiceServer
import groundplatform.v2.survey.SurveyState
import groundplatform.v2.userdata.UserDataSeed
import io.grpc.Status
import io.grpc.StatusException
import org.groundplatform.v2.core.mutation.DataPatches
import org.groundplatform.v2.core.mutation.LibraryPatches
import org.groundplatform.v2.core.mutation.OrganizationPatches
import org.groundplatform.v2.core.mutation.SurveyPatches

/**
 * The user data services ([SurveyServiceServer], [OrganizationServiceServer],
 * [LibraryServiceServer], and [DataServiceServer]) over a [UserDataStore]. The backend owns the
 * business rules; the store reads and writes records atomically. [InMemoryUserDataStore], built
 * from a [UserDataSeed], keeps state in this process only, so deploy a single instance with it.
 *
 * Implements the server-side contract defined in `docs/technical/sync-engine.md`:
 * - Monotonically increasing authoritative `commit_timestamp` assigned to every initial record and
 *   every subsequent mutation.
 * - Record versions that strictly increase on every write to a record, which clients compare to
 *   tell which copy is newer: `revision` for entities and submissions (including deletes and plot
 *   lock changes), `version` for organizations and libraries (one per request), and the survey's
 *   shared `record_version` on its saved revision, working copy, ACL, and overview.
 * - Unary `List*` sync RPCs with query and delta modes selected by an opaque `sync_token`
 *   ([SyncLists]). Delta mode returns every record in scope committed after the token, ordered by
 *   `(commit_timestamp, id)` and including tombstones; query mode filters, sorts, and pages a
 *   snapshot and ends with a `next_sync_token` for the scope. Tokens are bound to the store's data
 *   epoch ([UserDataStore.dataEpoch]), so tokens from before a reset (or from another in-memory
 *   process) fail with `FAILED_PRECONDITION` and clients run a full sync.
 * - `SearchOrganizations` directory queries with offset-based paging.
 * - Soft-delete tombstones (`is_deleted = true` or `state = DELETED`), returned in delta mode and
 *   in query mode only when `include_deleted = true`.
 * - Idempotent mutations: the highest applied `client_seq` per `(client_id, scope)` is stored with
 *   the changes and reported by `GetAppliedMutations`. A mutation at or below it is a replay and is
 *   skipped, so a stale retry can't overwrite newer writes. Clients send a scope's mutations in
 *   `client_seq` order; mutations without a `client_id` aren't deduplicated.
 * - Last-writer-wins field patches. `base_revision` / `base_version` only guard whole-record
 *   replacements (`upsert_*`), which fail with `FAILED_PRECONDITION` when stale; stale field
 *   patches on data records report `AUTO_MERGED`.
 * - Atomic `Mutate*` requests: a request that fails leaves no partial changes.
 * - 5-minute cooperative plot locks.
 */
class UserDataBackend(private val store: UserDataStore) :
  SurveyServiceServer, OrganizationServiceServer, LibraryServiceServer, DataServiceServer {

  /** A backend over an [InMemoryUserDataStore] initialized from [seed]. */
  constructor(seed: UserDataSeed = UserDataSeed()) : this(InMemoryUserDataStore(seed))

  private val syncLists = SyncLists { store.dataEpoch }

  /** Current highest committed server timestamp watermark. */
  fun currentWatermark(): Instant = store.watermark()

  /** Identity of one client mutation, used for replay detection and `client_seq` tracking. */
  private data class MutationIdentity(val clientId: String, val clientSeq: Long)

  /**
   * Recognizes replays in [scope]: a mutation is one when its `client_seq` is at or below its
   * client's watermark, or below a mutation earlier in the same request.
   */
  private class ReplayFilter(private val reader: UserDataReader, private val scope: String) {
    private val highest = HashMap<String, Long>()

    /** Whether [identity] is a replay. If it isn't, it raises its client's watermark here. */
    fun isReplay(identity: MutationIdentity): Boolean {
      if (identity.clientId.isBlank() || identity.clientSeq <= 0L) return false
      val watermark =
        highest.getOrPut(identity.clientId) {
          reader.appliedWatermarks(identity.clientId).firstOrNull { it.scope == scope }?.client_seq
            ?: 0L
        }
      if (identity.clientSeq <= watermark) return true
      highest[identity.clientId] = identity.clientSeq
      return false
    }
  }

  /** The [mutations] that aren't replays in [scope]. */
  private fun <T> UserDataReader.newMutations(
    scope: String,
    mutations: List<T>,
    identityOf: (T) -> MutationIdentity,
  ): List<T> {
    val replays = ReplayFilter(this, scope)
    return mutations.filterNot { replays.isReplay(identityOf(it)) }
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
   * The next shared record version of survey [surveyId]: one more than the highest version on its
   * saved revision, working copy, and ACL (`SurveyDef.record_version`).
   */
  private fun UserDataReader.nextSurveyRecordVersion(surveyId: String): Long =
    maxOf(
      survey(surveyId)?.record_version ?: 0L,
      workingCopy(surveyId)?.record_version ?: 0L,
      acl(surveyId)?.record_version ?: 0L,
    ) + 1L

  /**
   * Records [identities] as applied in [scope], in the same transaction as the changes they made,
   * so a failed request can be retried.
   */
  private fun UserDataWriter.recordApplied(scope: String, identities: List<MutationIdentity>) {
    identities
      .filter { it.clientId.isNotBlank() && it.clientSeq > 0L }
      .groupBy { it.clientId }
      .forEach { (clientId, ids) -> recordApplied(scope, clientId, ids.maxOf { it.clientSeq }) }
  }

  /** Resets all state to the store's initial data and starts a new data epoch. */
  fun reset() = store.reset()

  // ---------------------------------------------------------------------------
  // Timestamp comparison and paging helpers (sync-engine.md)
  // ---------------------------------------------------------------------------

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

  /** Applies `page_size` / `page_token` offset paging to an already filtered and sorted list. */
  private fun <T> page(items: List<T>, pageSize: Int, pageToken: String): Pair<List<T>, String> {
    val offset = pageToken.toIntOrNull() ?: 0
    if (pageSize <= 0) return items.drop(offset) to ""
    val end = minOf(offset + pageSize, items.size)
    val next = if (end < items.size) end.toString() else ""
    return items.subList(minOf(offset, items.size), end) to next
  }

  /** Query that matches [matches] in commit order (the default for full syncs). */
  private fun <T> commitOrderQuery(
    commitTimestamp: (T) -> Instant?,
    matches: (T) -> Boolean = { true },
  ) =
    QuerySpec(
      matches = matches,
      sortKey = { record: T -> SyncLists.timestampSortKey(commitTimestamp(record)) },
    )

  /**
   * Query sorted by `order_by`: [stringKeys] maps sortable field names to string sort keys;
   * `commit_timestamp` (the default) is always supported. Unknown fields fall back to commit order.
   */
  private fun <T> orderedQuery(
    orderBy: String,
    commitTimestamp: (T) -> Instant?,
    stringKeys: Map<String, (T) -> String>,
    fingerprint: String,
    matches: (T) -> Boolean,
  ): QuerySpec<T> {
    val parts = orderBy.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val field = parts.firstOrNull().orEmpty()
    val descending = parts.getOrNull(1) == "desc"
    val key: (T) -> String =
      stringKeys[field] ?: { record -> SyncLists.timestampSortKey(commitTimestamp(record)) }
    return QuerySpec(matches, key, descending, fingerprint)
  }

  // ---------------------------------------------------------------------------
  // OrganizationServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetOrganization(request: GetOrganizationRequest): GetOrganizationResponse {
    val org =
      store.read { it.organization(request.organization_id) }
        ?: Organization(organization_id = request.organization_id)
    return GetOrganizationResponse(organization = org)
  }

  override suspend fun SearchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse {
    val q = request.query.trim().lowercase()
    val matches =
      store
        .read { it.organizations() }
        .filter { org ->
          org.listed &&
            !org.is_deleted &&
            (q.isEmpty() ||
              org.name.lowercase().contains(q) ||
              org.description.lowercase().contains(q))
        }
        .sortedBy { it.name.lowercase() }
    val (items, next) = page(matches, request.page_size, request.page_token)
    return SearchOrganizationsResponse(organizations = items, next_page_token = next)
  }

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse {
    val page = store.read { r ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "organizations",
        records = r.organizations(),
        commitTimestamp = Organization::commit_timestamp,
        id = Organization::organization_id,
        query = commitOrderQuery(Organization::commit_timestamp) { !it.is_deleted },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
      )
    }
    return ListOrganizationsResponse(
      organizations = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse {
    val orgId = request.organization_id
    val (existing, toApply) =
      store.read { r ->
        r.organization(orgId) to
          r.newMutations("organizations/$orgId", request.mutations) {
            MutationIdentity(it.client_id, it.client_seq)
          }
      }
    if (toApply.isEmpty()) {
      return MutateOrganizationResponse(
        organization_id = orgId,
        organization = existing,
        commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
      )
    }
    if (toApply.any { it.upsert_organization != null }) {
      checkBaseVersion(request.base_version, existing?.version, "organization $orgId")
    }

    val committed = store.write { tx ->
      val stored = tx.organization(orgId)
      var current: Organization = stored ?: Organization(organization_id = orgId, version = 0L)
      for (mutation in toApply) {
        val up = mutation.upsert_organization
        val patch = mutation.update_field
        when {
          mutation.delete_organization == true -> current = current.copy(is_deleted = true)
          // The server owns the version; it's bumped once for the whole request below.
          up != null ->
            current =
              up.copy(
                organization_id = orgId.ifBlank { up.organization_id },
                version = current.version,
              )
          patch != null -> current = OrganizationPatches.applyField(current, patch)
        }
      }
      // Every write bumps the version by exactly one, however many mutations the request has.
      current = current.copy(version = (stored?.version ?: 0L) + 1L)
      tx.putOrganization(current)
      tx.recordApplied(
        scope = "organizations/${current.organization_id}",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      current
    }
    val organization = committed.value.copy(commit_timestamp = committed.commitTimestamp)
    return MutateOrganizationResponse(
      organization_id = organization.organization_id,
      organization = organization,
      commit_timestamp = committed.commitTimestamp,
    )
  }

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse {
    if (store.read { it.organization(request.organization_id) } == null) {
      return JoinOrganizationResponse(organization = null, commit_timestamp = store.watermark())
    }
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
    val committed = store.write { tx ->
      val current = checkNotNull(tx.organization(request.organization_id))
      val updated =
        current.copy(
          members =
            current.members.filterNot { it.email.equals(email, ignoreCase = true) } + newMember,
          version = current.version + 1L,
        )
      tx.putOrganization(updated)
      updated
    }
    return JoinOrganizationResponse(
      organization = committed.value.copy(commit_timestamp = committed.commitTimestamp),
      member = newMember,
      commit_timestamp = committed.commitTimestamp,
    )
  }

  // ---------------------------------------------------------------------------
  // LibraryServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse {
    val record =
      store.read { it.library(request.organization_id) }
        ?: OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle = LibraryBundle(),
          settings = OrganizationLibrarySettings(organization_id = request.organization_id),
          version = 1L,
          commit_timestamp = store.watermark(),
        )
    return GetLibraryResponse(library = record)
  }

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse {
    val (globalRecord, orgRecord) =
      store.read { r ->
        r.library(ALL_USERS_ORGANIZATION_ID) to
          if (request.organization_id.isNotBlank()) r.library(request.organization_id) else null
      }
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
          commit_timestamp = store.watermark(),
        )
    )
  }

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse {
    val orgId = request.organization_id
    val page = store.read { r ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "libraries|organization_id=$orgId",
        records = r.libraries().filter { orgId.isBlank() || it.organization_id == orgId },
        commitTimestamp = OrganizationLibraryRecord::commit_timestamp,
        id = OrganizationLibraryRecord::organization_id,
        query = commitOrderQuery(OrganizationLibraryRecord::commit_timestamp) { !it.is_deleted },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
      )
    }
    return ListLibrariesResponse(
      libraries = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
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
    val orgId = request.organization_id
    val (existing, toApply) =
      store.read { r ->
        r.library(orgId) to
          r.newMutations("libraries/$orgId", request.mutations) {
            MutationIdentity(it.client_id, it.client_seq)
          }
      }
    if (toApply.isEmpty()) {
      return MutateLibraryResponse(
        organization_id = orgId,
        library = existing,
        commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
      )
    }
    if (toApply.any { it.upsert_library != null }) {
      checkBaseVersion(request.base_version, existing?.version, "library $orgId")
    }

    val committed = store.write { tx ->
      val stored = tx.library(orgId)
      var current =
        stored
          ?: OrganizationLibraryRecord(
            organization_id = orgId,
            bundle = LibraryBundle(),
            settings = OrganizationLibrarySettings(organization_id = orgId),
            version = 0L,
          )
      for (mutation in toApply) {
        val upLib = mutation.upsert_library
        val patch = mutation.update_field
        if (upLib != null) {
          // The server owns the version; it's bumped once for the whole request below.
          current = upLib.copy(organization_id = orgId, version = current.version)
        } else if (patch != null) {
          current = LibraryPatches.applyField(current, patch)
        }
      }
      // Every write bumps the version by exactly one, however many mutations the request has.
      current = current.copy(version = (stored?.version ?: 0L) + 1L)
      tx.putLibrary(current)
      tx.recordApplied(
        scope = "libraries/$orgId",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      current
    }
    return MutateLibraryResponse(
      organization_id = orgId,
      library = committed.value.copy(commit_timestamp = committed.commitTimestamp),
      commit_timestamp = committed.commitTimestamp,
    )
  }

  // ---------------------------------------------------------------------------
  // SurveyServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse {
    val orgId = request.organization_id
    val page = store.read { r ->
      val overviews =
        r.surveys()
          .filter { s -> orgId.isBlank() || s.organization_id == orgId }
          .map { s -> r.overviewOf(s) }
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "surveys|organization_id=$orgId",
        records = overviews,
        commitTimestamp = SurveyOverview::commit_timestamp,
        id = SurveyOverview::survey_id,
        query =
          commitOrderQuery(SurveyOverview::commit_timestamp) { it.state != SurveyState.DELETED },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
      )
    }
    return ListSurveyOverviewsResponse(
      overviews = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse = store.read { r ->
    r.checkNotTrashed(request.survey_id)
    val saved = r.survey(request.survey_id)
    val requestedVersion = request.saved_version
    val survey =
      when {
        request.working_copy == true -> r.workingCopy(request.survey_id)
        // The static backend only keeps the latest saved revision.
        requestedVersion != null -> saved?.takeIf { it.version == requestedVersion }
        else -> saved
      }
    GetSurveyResponse(survey = survey, acl = r.acl(request.survey_id))
  }

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse =
    GetSurveyAclResponse(
      acl = store.read { it.acl(request.survey_id) } ?: SurveyAcl(survey_id = request.survey_id)
    )

  private fun UserDataReader.resolveEffectiveQuota(
    survey: SurveyDef?,
    organizationId: String?,
  ): QuotaLimits {
    val global = organization(ALL_USERS_ORGANIZATION_ID)?.quota_limits
    val org = organizationId?.takeIf { it.isNotBlank() }?.let { organization(it)?.quota_limits }
    val s = survey?.quota_limits

    val effectiveTier =
      when {
        s?.tier != null && s.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> s.tier
        org?.tier != null && org.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> org.tier
        organizationId?.isNotBlank() == true -> QuotaTier.SPONSORED
        global?.tier != null && global.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> global.tier
        else -> QuotaTier.BASIC
      }

    val isSponsored = effectiveTier == QuotaTier.SPONSORED
    val defaultMaxSurveysPerOwner = if (isSponsored) 100 else 5
    val defaultMaxFormsPerSurvey = if (isSponsored) 10 else 5
    val defaultMaxQuestionsPerForm = if (isSponsored) 50 else 10
    val defaultMaxPhotoQuestionsPerForm = if (isSponsored) 10 else 1
    val defaultMaxPredefinedEntities = if (isSponsored) 10_000 else 1_000
    val defaultMaxAclUsers = if (isSponsored) 50 else 10
    val defaultMaxSurveysPerUser = if (isSponsored) 100 else 5
    val defaultMaxAdhocEntitiesPerUser = if (isSponsored) 200 else 100
    val defaultMaxSubmissionsPerEntityPerUser = if (isSponsored) 50 else 10
    val defaultMaxGeneratedEntities = if (isSponsored) 100_000 else 10_000
    val defaultMaxAreaVertices = if (isSponsored) 50_000 else 5_000

    fun resolveInt(sVal: Int?, orgVal: Int?, gVal: Int?, default: Int): Int =
      when {
        sVal != null && sVal > 0 -> sVal
        orgVal != null && orgVal > 0 -> orgVal
        gVal != null && gVal > 0 -> gVal
        else -> default
      }

    fun resolveDouble(sVal: Double?, orgVal: Double?, gVal: Double?, default: Double): Double =
      when {
        sVal != null && sVal > 0.0 -> sVal
        orgVal != null && orgVal > 0.0 -> orgVal
        gVal != null && gVal > 0.0 -> gVal
        else -> default
      }

    val gWeights = global?.cost_unit_weights
    val orgWeights = org?.cost_unit_weights
    val sWeights = s?.cost_unit_weights

    val effectiveWeights =
      CostUnitWeights(
        usd_per_survey_month =
          resolveDouble(
            sWeights?.usd_per_survey_month,
            orgWeights?.usd_per_survey_month,
            gWeights?.usd_per_survey_month,
            0.50,
          ),
        usd_per_thousand_entities =
          resolveDouble(
            sWeights?.usd_per_thousand_entities,
            orgWeights?.usd_per_thousand_entities,
            gWeights?.usd_per_thousand_entities,
            0.10,
          ),
        usd_per_thousand_submissions =
          resolveDouble(
            sWeights?.usd_per_thousand_submissions,
            orgWeights?.usd_per_thousand_submissions,
            gWeights?.usd_per_thousand_submissions,
            0.25,
          ),
        usd_per_photo =
          resolveDouble(
            sWeights?.usd_per_photo,
            orgWeights?.usd_per_photo,
            gWeights?.usd_per_photo,
            0.01,
          ),
        usd_per_thousand_generated_plots =
          resolveDouble(
            sWeights?.usd_per_thousand_generated_plots,
            orgWeights?.usd_per_thousand_generated_plots,
            gWeights?.usd_per_thousand_generated_plots,
            0.05,
          ),
      )

    return QuotaLimits(
      tier = effectiveTier,
      max_surveys_per_owner =
        resolveInt(
          s?.max_surveys_per_owner,
          org?.max_surveys_per_owner,
          global?.max_surveys_per_owner,
          defaultMaxSurveysPerOwner,
        ),
      max_forms_per_survey =
        resolveInt(
          s?.max_forms_per_survey,
          org?.max_forms_per_survey,
          global?.max_forms_per_survey,
          defaultMaxFormsPerSurvey,
        ),
      max_questions_per_form =
        resolveInt(
          s?.max_questions_per_form,
          org?.max_questions_per_form,
          global?.max_questions_per_form,
          defaultMaxQuestionsPerForm,
        ),
      max_photo_questions_per_form =
        resolveInt(
          s?.max_photo_questions_per_form,
          org?.max_photo_questions_per_form,
          global?.max_photo_questions_per_form,
          defaultMaxPhotoQuestionsPerForm,
        ),
      max_predefined_entities =
        resolveInt(
          s?.max_predefined_entities,
          org?.max_predefined_entities,
          global?.max_predefined_entities,
          defaultMaxPredefinedEntities,
        ),
      max_acl_users =
        resolveInt(s?.max_acl_users, org?.max_acl_users, global?.max_acl_users, defaultMaxAclUsers),
      max_surveys_per_user =
        resolveInt(
          s?.max_surveys_per_user,
          org?.max_surveys_per_user,
          global?.max_surveys_per_user,
          defaultMaxSurveysPerUser,
        ),
      max_adhoc_entities_per_user =
        resolveInt(
          s?.max_adhoc_entities_per_user,
          org?.max_adhoc_entities_per_user,
          global?.max_adhoc_entities_per_user,
          defaultMaxAdhocEntitiesPerUser,
        ),
      max_submissions_per_entity_per_user =
        resolveInt(
          s?.max_submissions_per_entity_per_user,
          org?.max_submissions_per_entity_per_user,
          global?.max_submissions_per_entity_per_user,
          defaultMaxSubmissionsPerEntityPerUser,
        ),
      max_generated_entities =
        resolveInt(
          s?.max_generated_entities,
          org?.max_generated_entities,
          global?.max_generated_entities,
          defaultMaxGeneratedEntities,
        ),
      max_area_vertices =
        resolveInt(
          s?.max_area_vertices,
          org?.max_area_vertices,
          global?.max_area_vertices,
          defaultMaxAreaVertices,
        ),
      monthly_budget_usd =
        resolveDouble(
          s?.monthly_budget_usd,
          org?.monthly_budget_usd,
          global?.monthly_budget_usd,
          0.0,
        ),
      cost_unit_weights = effectiveWeights,
    )
  }

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
    val surveyId = request.survey_id
    val toApply = store.read { r ->
      r.newMutations("surveys/$surveyId", request.mutations) {
        MutationIdentity(it.client_id, it.client_seq)
      }
    }
    if (toApply.isEmpty()) {
      return store.read { r ->
        val existingSaved = r.survey(surveyId)
        MutateSurveyResponse(
          survey_id = surveyId,
          survey =
            if (request.working_copy) r.workingCopy(surveyId) ?: existingSaved else existingSaved,
          acl = r.acl(surveyId),
          commit_timestamp = existingSaved?.commit_timestamp ?: store.watermark(),
        )
      }
    }
    require(
      toApply.none {
        it.update_access_rules?.sharing_role == Role.SURVEY_ORGANIZER ||
          it.upsert_acl?.sharing_role == Role.SURVEY_ORGANIZER
      }
    ) {
      "sharing_role can't be SURVEY_ORGANIZER; organizers must be named on the ACL"
    }

    val committed = store.write { tx ->
      if (toApply.any { it.upsert_survey != null || it.upsert_acl != null }) {
        val baseTarget =
          if (request.working_copy) tx.workingCopy(surveyId) ?: tx.survey(surveyId)
          else tx.survey(surveyId)
        checkBaseVersion(request.base_version, baseTarget?.version, "survey $surveyId")
      }
      val isNewSurvey = tx.survey(surveyId) == null
      var saved =
        tx.survey(surveyId)
          ?: SurveyDef(
            survey_id = surveyId,
            state = SurveyState.PUBLISHED,
            saved_revision_number = 1L,
          )
      var workingCopy = tx.workingCopy(surveyId)
      var currentAcl = tx.acl(surveyId) ?: SurveyAcl(survey_id = surveyId)

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
          mutation.trash_survey == true ->
            if (saved.trash_time == null) saved = saved.copy(trash_time = tx.now)
          mutation.restore_survey == true -> saved = saved.copy(trash_time = null)
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
          patchAccess != null ->
            currentAcl = SurveyPatches.applyAccessRules(currentAcl, patchAccess)
        }
      }

      if (saved.state != SurveyState.DELETED) {
        val limits = tx.resolveEffectiveQuota(target(), target().organization_id)

        if (isNewSurvey) {
          val owner = target().owner_id.ifBlank { currentAcl.owner_email }
          if (owner.isNotBlank() && limits.max_surveys_per_owner > 0) {
            val ownerSurveys =
              tx.surveys().count {
                it.survey_id != surveyId &&
                  it.state != SurveyState.DELETED &&
                  (it.owner_id == owner)
              }
            if (ownerSurveys >= limits.max_surveys_per_owner) {
              throw StatusException(
                Status.RESOURCE_EXHAUSTED.withDescription(
                  "Survey quota exceeded: maximum surveys per owner reached (${limits.max_surveys_per_owner})"
                )
              )
            }
          }
        }

        if (limits.max_forms_per_survey > 0 && target().forms.size > limits.max_forms_per_survey) {
          throw StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              "Form quota exceeded: survey has ${target().forms.size} forms, exceeding maximum of ${limits.max_forms_per_survey}"
            )
          )
        }

        for (form in target().forms) {
          val questionsCount = form.model?.bindings?.size ?: form.view?.components?.size ?: 0
          if (limits.max_questions_per_form > 0 && questionsCount > limits.max_questions_per_form) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Question quota exceeded: form \"${form.form_id}\" has $questionsCount questions, exceeding maximum of ${limits.max_questions_per_form}"
              )
            )
          }
          val photoQuestions =
            form.model?.bindings?.count {
              it.type == groundplatform.v2.forms.DataType.TYPE_BINARY ||
                it.field_path.lowercase().contains("photo") ||
                it.field_path.lowercase().contains("picture") ||
                it.field_path.lowercase().contains("image")
            } ?: 0
          if (
            limits.max_photo_questions_per_form > 0 &&
              photoQuestions > limits.max_photo_questions_per_form
          ) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Photo question quota exceeded: form \"${form.form_id}\" has $photoQuestions photo questions, exceeding maximum of ${limits.max_photo_questions_per_form}"
              )
            )
          }
        }

        if (limits.max_acl_users > 0 && currentAcl.entries.size > limits.max_acl_users) {
          throw StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              "ACL quota exceeded: survey has ${currentAcl.entries.size} collaborators, exceeding maximum of ${limits.max_acl_users}"
            )
          )
        }

        val surveyArea = target().map_config?.survey_area
        if (surveyArea != null && limits.max_area_vertices > 0) {
          val totalVertices = surveyArea.parts.sumOf { it.points.size }
          if (totalVertices > limits.max_area_vertices) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Survey area vertex quota exceeded: survey area has $totalVertices vertices, exceeding maximum of ${limits.max_area_vertices}"
              )
            )
          }
        }

        if (limits.monthly_budget_usd > 0.0) {
          val entities = tx.entities(surveyId).filter { !it.is_deleted }
          val submissions = tx.submissions(surveyId).filter { !it.is_deleted }
          val weights = limits.cost_unit_weights ?: CostUnitWeights()
          val estimatedCost =
            weights.usd_per_survey_month +
              (entities.size / 1000.0) * weights.usd_per_thousand_entities +
              (submissions.size / 1000.0) * weights.usd_per_thousand_submissions
          if (estimatedCost > limits.monthly_budget_usd) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Monthly budget cap exceeded: estimated monthly cost exceeds budget limit of $${limits.monthly_budget_usd}"
              )
            )
          }
        }
      }

      // Always write the saved revision so overview delta polls see working-copy and access
      // changes, and bump the survey's shared record version on all of its parts.
      val recordVersion = tx.nextSurveyRecordVersion(surveyId)
      saved = saved.copy(record_version = recordVersion)
      val acl = currentAcl.copy(record_version = recordVersion)
      val finalWorkingCopy = workingCopy?.copy(record_version = recordVersion)
      tx.putSurvey(saved)
      tx.putAcl(acl)
      tx.putWorkingCopy(surveyId, finalWorkingCopy)
      tx.recordApplied(
        scope = "surveys/$surveyId",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      Triple(saved, finalWorkingCopy, acl)
    }
    val ts = committed.commitTimestamp
    val (saved, workingCopy, acl) = committed.value
    return MutateSurveyResponse(
      survey_id = surveyId,
      survey =
        (if (request.working_copy) workingCopy ?: saved else saved).copy(commit_timestamp = ts),
      acl = acl.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse {
    val committed = store.write { tx ->
      val acl = tx.acl(request.survey_id) ?: SurveyAcl(survey_id = request.survey_id)
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
      val recordVersion = tx.nextSurveyRecordVersion(request.survey_id)
      val updatedAcl = acl.copy(entries = updatedEntries, record_version = recordVersion)
      tx.putAcl(updatedAcl)
      val saved = tx.survey(request.survey_id)?.copy(record_version = recordVersion)
      saved?.let { tx.putSurvey(it) }
      tx.workingCopy(request.survey_id)?.let { wc ->
        tx.putWorkingCopy(request.survey_id, wc.copy(record_version = recordVersion))
      }
      saved to updatedAcl
    }
    val ts = committed.commitTimestamp
    return AcceptSurveyInviteResponse(
      survey = committed.value.first?.copy(commit_timestamp = ts),
      acl = committed.value.second.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  override suspend fun BatchGetSurveyOverviews(
    request: BatchGetSurveyOverviewsRequest
  ): BatchGetSurveyOverviewsResponse {
    require(request.survey_ids.size <= MAX_BATCH_SURVEY_IDS) {
      "At most $MAX_BATCH_SURVEY_IDS survey_ids per request"
    }
    return store.read { r ->
      BatchGetSurveyOverviewsResponse(
        results =
          request.survey_ids.map { id ->
            val saved = r.survey(id)
            if (saved == null) {
              SurveyOverviewResult(survey_id = id, error_code = Status.Code.NOT_FOUND.value())
            } else {
              // The backend doesn't authenticate callers yet, so it never reports
              // PERMISSION_DENIED.
              SurveyOverviewResult(survey_id = id, overview = r.overviewOf(saved))
            }
          }
      )
    }
  }

  override suspend fun SearchPublicSurveys(
    request: SearchPublicSurveysRequest
  ): SearchPublicSurveysResponse {
    val q = request.query.trim().lowercase()
    val matching = store.read { r ->
      r.surveys()
        .filter { s ->
          r.acl(s.survey_id)?.sharing_policy == SharingPolicy.PUBLIC &&
            s.trash_time == null &&
            s.state != SurveyState.DELETED &&
            (q.isEmpty() ||
              s.title.lowercase().contains(q) ||
              s.description.lowercase().contains(q))
        }
        .sortedBy { it.survey_id }
        .map { s -> r.overviewOf(s) }
    }
    val (items, nextPageToken) = page(matching, request.page_size, request.page_token)
    return SearchPublicSurveysResponse(overviews = items, next_page_token = nextPageToken)
  }

  /** Fails with `FAILED_PRECONDITION` when survey [surveyId] is in Trash. */
  private fun UserDataReader.checkNotTrashed(surveyId: String) {
    check(survey(surveyId)?.trash_time == null) { "Survey $surveyId is in Trash" }
  }

  // ---------------------------------------------------------------------------
  // DataServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse {
    val entity =
      store.read { it.entity(request.survey_id, request.entity_id) }
        ?: EntityRecord(survey_id = request.survey_id, entity_id = request.entity_id)
    return GetEntityResponse(entity = entity)
  }

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
    val queryFieldsSet = buildList {
      if (request.dataset_id.isNotBlank()) add("dataset_id")
      if (request.s2_cell_tokens.isNotEmpty()) add("s2_cell_tokens")
      if (request.include_deleted) add("include_deleted")
      if (request.bbox != null) add("bbox")
      if (request.filter.isNotEmpty()) add("filter")
      if (request.queue_filter_mode != QueueFilterMode.QUEUE_FILTER_MODE_UNSPECIFIED) {
        add("queue_filter_mode")
      }
      if (request.order_by.isNotBlank()) add("order_by")
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = EntityRecord::commit_timestamp,
        stringKeys = mapOf("label" to EntityRecord::label),
        fingerprint =
          "dataset_id=${request.dataset_id}|include_deleted=${request.include_deleted}|" +
            "filter=${request.filter.toSortedMap()}|order_by=${request.order_by.trim().lowercase()}",
      ) { e ->
        (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id) &&
          (request.include_deleted || !e.is_deleted) &&
          request.filter.all { (key, value) -> e.properties[key]?.string_value == value }
      }
    // `s2_cell_tokens` is a query-only filter, but the backend doesn't index entities by S2 cell
    // yet, so it doesn't narrow the results.
    val page = store.read { r ->
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "entities|survey_id=${request.survey_id}",
        changedAfter = { after, limit -> r.entitiesChangedAfter(request.survey_id, after, limit) },
        queryRecords = {
          r.entities(request.survey_id, request.dataset_id, request.include_deleted)
        },
        commitTimestamp = EntityRecord::commit_timestamp,
        id = EntityRecord::entity_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
      )
    }
    return ListEntitiesResponse(
      entities = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse {
    val entities = store.read {
      it.entities(request.survey_id, request.dataset_id, includeDeleted = false)
    }
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
      store.read { it.submission(request.survey_id, request.submission_id) }
        ?: SubmissionRecord(survey_id = request.survey_id, submission_id = request.submission_id)
    return GetSubmissionResponse(submission = sub)
  }

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse {
    val queryFieldsSet = buildList {
      if (request.form_id.isNotBlank()) add("form_id")
      if (request.entity_id.isNotBlank()) add("entity_id")
      if (request.include_deleted) add("include_deleted")
      if (request.filter.isNotEmpty()) add("filter")
      if (request.order_by.isNotBlank()) add("order_by")
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = SubmissionRecord::commit_timestamp,
        stringKeys = mapOf("collector_name" to SubmissionRecord::collector_name),
        fingerprint =
          "form_id=${request.form_id}|entity_id=${request.entity_id}|" +
            "include_deleted=${request.include_deleted}|filter=${request.filter.toSortedMap()}|" +
            "order_by=${request.order_by.trim().lowercase()}",
      ) { sub ->
        (request.form_id.isBlank() || sub.form_id == request.form_id) &&
          (request.entity_id.isBlank() || sub.entity_id == request.entity_id) &&
          (request.include_deleted || !sub.is_deleted) &&
          request.filter.all { (key, value) ->
            when (key) {
              "collector_email" -> sub.collector_email == value
              "collector_name" -> sub.collector_name == value
              else -> true
            }
          }
      }
    val page = store.read { r ->
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "submissions|survey_id=${request.survey_id}",
        changedAfter = { after, limit ->
          r.submissionsChangedAfter(request.survey_id, after, limit)
        },
        queryRecords = {
          r.submissions(request.survey_id, request.form_id, request.include_deleted)
        },
        commitTimestamp = SubmissionRecord::commit_timestamp,
        id = SubmissionRecord::submission_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
      )
    }
    return ListSubmissionsResponse(
      submissions = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
    val surveyId = request.survey_id
    val scope = "surveys/$surveyId/data"
    val hasNew = store.read { r ->
      val replays = ReplayFilter(r, scope)
      request.mutations.any { !replays.isReplay(MutationIdentity(it.client_id, it.client_seq)) }
    }
    if (!hasNew) {
      // Every mutation is a replay: report the records' current state without writing.
      return store.read { r ->
        MutateDataResponse(
          results =
            request.mutations.map { r.duplicateDataResult(surveyId, it, emptyMap(), emptyMap()) },
          commit_timestamp = store.watermark(),
        )
      }
    }
    val committed = store.write { tx ->
      // The transaction reads data as of its start, so changes made by earlier mutations of this
      // request are tracked here.
      val entities = LinkedHashMap<String, EntityRecord>()
      val submissions = LinkedHashMap<String, SubmissionRecord>()
      val applied = mutableListOf<MutationIdentity>()
      val replays = ReplayFilter(tx, scope)
      val results = mutableListOf<Pair<DataMutationResult, Boolean>>()
      for (mutation in request.mutations) {
        val identity = MutationIdentity(mutation.client_id, mutation.client_seq)
        if (replays.isReplay(identity)) {
          results += tx.duplicateDataResult(surveyId, mutation, entities, submissions) to false
          continue
        }
        results += tx.applyDataMutation(surveyId, mutation, entities, submissions) to true
        applied += identity
      }
      entities.values.forEach(tx::putEntity)
      submissions.values.forEach(tx::putSubmission)
      tx.recordApplied(scope, identities = applied)
      results
    }
    val ts = committed.commitTimestamp
    return MutateDataResponse(
      results =
        committed.value.map { (result, written) ->
          if (!written) result
          else
            result.copy(
              entity = result.entity?.copy(commit_timestamp = ts),
              submission = result.submission?.copy(commit_timestamp = ts),
              commit_timestamp = ts,
            )
        },
      commit_timestamp = ts,
    )
  }

  /** Result for a replayed `mutation_id`: the target record's current state, unchanged. */
  private fun UserDataReader.duplicateDataResult(
    surveyId: String,
    mutation: DataMutation,
    entities: Map<String, EntityRecord>,
    submissions: Map<String, SubmissionRecord>,
  ): DataMutationResult {
    val entity =
      mutation.entity_mutation?.let {
        entities[it.entity_id] ?: entity(surveyId, it.entity_id)
      }
    val submission =
      mutation.submission_mutation?.let {
        submissions[it.submission_id] ?: submission(surveyId, it.submission_id)
      }
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

  /**
   * Applies [mutation] on top of the stored records and this request's earlier changes ([entities]
   * / [submissions], which it updates). The store stamps the commit timestamp.
   */
  private fun UserDataReader.applyDataMutation(
    surveyId: String,
    mutation: DataMutation,
    entities: LinkedHashMap<String, EntityRecord>,
    submissions: LinkedHashMap<String, SubmissionRecord>,
  ): DataMutationResult {
    val em = mutation.entity_mutation
    val sm = mutation.submission_mutation
    return when {
      em != null -> {
        val stored = entities[em.entity_id] ?: entity(surveyId, em.entity_id)
        val existing =
          stored
            ?: EntityRecord(
              entity_id = em.entity_id,
              dataset_id = em.dataset_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val emUpsert = em.upsert
        val emUpdate = em.update
        if (emUpsert != null) {
          checkBaseVersion(em.base_revision, stored?.revision, "entity ${em.entity_id}")
          if (existing.revision == 0L) {
            val s = survey(surveyId)
            val limits = resolveEffectiveQuota(s, s?.organization_id)
            if (emUpsert.is_field_created) {
              val userId = emUpsert.audit_info?.created_by.orEmpty()
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val userAdhoc =
                allEntities.values.count {
                  !it.is_deleted &&
                    it.is_field_created &&
                    (userId.isBlank() || it.audit_info?.created_by == userId)
                }
              if (
                limits.max_adhoc_entities_per_user > 0 &&
                  userAdhoc >= limits.max_adhoc_entities_per_user
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Ad-hoc entity quota exceeded: maximum ad-hoc entities per user reached (${limits.max_adhoc_entities_per_user})"
                  )
                )
              }
            } else {
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val predefined = allEntities.values.count { !it.is_deleted && !it.is_field_created }
              if (
                limits.max_predefined_entities > 0 && predefined >= limits.max_predefined_entities
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Predefined entity quota exceeded: maximum predefined entities reached (${limits.max_predefined_entities})"
                  )
                )
              }
            }

            if (limits.monthly_budget_usd > 0.0) {
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val allSubmissions =
                submissions(surveyId).associateBy { it.submission_id } + submissions
              val entitiesCount = allEntities.values.count { !it.is_deleted } + 1
              val subCount = allSubmissions.values.count { !it.is_deleted }
              val weights = limits.cost_unit_weights ?: CostUnitWeights()
              val estimated =
                weights.usd_per_survey_month +
                  (entitiesCount / 1000.0) * weights.usd_per_thousand_entities +
                  (subCount / 1000.0) * weights.usd_per_thousand_submissions
              if (estimated > limits.monthly_budget_usd) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Monthly budget cap exceeded: operation would exceed budget limit of $${limits.monthly_budget_usd}"
                  )
                )
              }
            }
          }
        }
        val updated: EntityRecord =
          when {
            em.delete == true -> existing.copy(revision = existing.revision + 1L, is_deleted = true)
            emUpsert != null ->
              emUpsert.copy(
                entity_id = em.entity_id.ifBlank { emUpsert.entity_id },
                dataset_id = em.dataset_id.ifBlank { emUpsert.dataset_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                sync_status_key = "SYNCED",
              )
            emUpdate != null ->
              DataPatches.applyEntity(existing, emUpdate)
                .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
            else -> existing.copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
          }
        entities[updated.entity_id] = updated
        DataMutationResult(
          entity = updated,
          conflict_status = patchConflictStatus(em.base_revision, existing.revision),
        )
      }
      sm != null -> {
        val stored = submissions[sm.submission_id] ?: submission(surveyId, sm.submission_id)
        val existing =
          stored
            ?: SubmissionRecord(
              submission_id = sm.submission_id,
              form_id = sm.form_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val smUpsert = sm.upsert
        val smUpdate = sm.update
        if (smUpsert != null) {
          checkBaseVersion(sm.base_revision, stored?.revision, "submission ${sm.submission_id}")
          if (existing.revision == 0L) {
            val s = survey(surveyId)
            val limits = resolveEffectiveQuota(s, s?.organization_id)
            if (smUpsert.entity_id.isNotBlank()) {
              val userId = smUpsert.audit_info?.created_by.orEmpty()
              val allSubmissions =
                submissions(surveyId).associateBy { it.submission_id } + submissions
              val countForEntityAndUser =
                allSubmissions.values.count {
                  !it.is_deleted &&
                    it.entity_id == smUpsert.entity_id &&
                    (userId.isBlank() || it.audit_info?.created_by == userId)
                }
              if (
                limits.max_submissions_per_entity_per_user > 0 &&
                  countForEntityAndUser >= limits.max_submissions_per_entity_per_user
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Submission quota exceeded: maximum submissions per entity per user reached (${limits.max_submissions_per_entity_per_user})"
                  )
                )
              }
            }

            if (limits.monthly_budget_usd > 0.0) {
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val allSubmissions =
                submissions(surveyId).associateBy { it.submission_id } + submissions
              val entitiesCount = allEntities.values.count { !it.is_deleted }
              val subCount = allSubmissions.values.count { !it.is_deleted } + 1
              val weights = limits.cost_unit_weights ?: CostUnitWeights()
              val estimated =
                weights.usd_per_survey_month +
                  (entitiesCount / 1000.0) * weights.usd_per_thousand_entities +
                  (subCount / 1000.0) * weights.usd_per_thousand_submissions
              if (estimated > limits.monthly_budget_usd) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Monthly budget cap exceeded: operation would exceed budget limit of $${limits.monthly_budget_usd}"
                  )
                )
              }
            }
          }
        }
        val updated: SubmissionRecord =
          when {
            sm.delete == true -> existing.copy(revision = existing.revision + 1L, is_deleted = true)
            smUpsert != null ->
              smUpsert.copy(
                submission_id = sm.submission_id.ifBlank { smUpsert.submission_id },
                form_id = sm.form_id.ifBlank { smUpsert.form_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                sync_status_key = "SYNCED",
              )
            smUpdate != null ->
              DataPatches.applySubmission(existing, smUpdate)
                .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
            else -> existing.copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
          }
        submissions[updated.submission_id] = updated
        DataMutationResult(
          submission = updated,
          conflict_status = patchConflictStatus(sm.base_revision, existing.revision),
        )
      }
      else ->
        DataMutationResult(
          conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
        )
    }
  }

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse {
    val committed = store.write { tx ->
      val existing = tx.plotLock(request.survey_id, request.entity_id)
      val nowSec = tx.now.epochSecond
      if (
        existing != null &&
          existing.locked_by_user_id != request.user_id &&
          (existing.expires_at?.epochSecond ?: 0L) > nowSec
      ) {
        return@write AcquirePlotLockResponse(
          entity = tx.entity(request.survey_id, request.entity_id),
          lock = existing,
        ) to false
      }
      val lock =
        PlotLock(
          entity_id = request.entity_id,
          locked_by_user_id = request.user_id,
          locked_at = tx.now,
          expires_at = Instant.ofEpochSecond(nowSec + 300L, 0L),
        )
      tx.putPlotLock(request.survey_id, request.entity_id, lock)
      val withLock =
        tx.entity(request.survey_id, request.entity_id)?.let { ent ->
          ent.copy(active_lock = lock, revision = ent.revision + 1L).also(tx::putEntity)
        }
      AcquirePlotLockResponse(entity = withLock, lock = lock) to true
    }
    val (response, written) = committed.value
    return if (written) {
      response.copy(entity = response.entity?.copy(commit_timestamp = committed.commitTimestamp))
    } else {
      response
    }
  }

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse {
    store.write { tx ->
      tx.putPlotLock(request.survey_id, request.entity_id, null)
      tx.entity(request.survey_id, request.entity_id)?.let { ent ->
        tx.putEntity(ent.copy(active_lock = null, revision = ent.revision + 1L))
      }
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
    if (request.events.isEmpty()) {
      return RecordImpactEventsResponse(commit_timestamp = store.watermark())
    }
    val committed = store.write { tx -> request.events.forEach(tx::putImpactEvent) }
    val ts = committed.commitTimestamp
    return RecordImpactEventsResponse(
      events = request.events.map { it.copy(commit_timestamp = ts) },
      commit_timestamp = ts,
    )
  }

  override suspend fun ListImpactEvents(
    request: ListImpactEventsRequest
  ): ListImpactEventsResponse {
    val page = store.read { snapshot ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey =
          "impact_events|survey_id=${request.survey_id}|organization_id=${request.organization_id}",
        records =
          snapshot.impactEvents().filter { ev ->
            (request.survey_id.isBlank() || ev.survey_id == request.survey_id) &&
              (request.organization_id.isBlank() || ev.organization_id == request.organization_id)
          },
        commitTimestamp = ImpactEventProto::commit_timestamp,
        id = ImpactEventProto::event_id,
        query = commitOrderQuery(ImpactEventProto::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
      )
    }
    return ListImpactEventsResponse(
      events = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse {
    val activeSurveys =
      store
        .read { it.surveys() }
        .filter { s ->
          s.state != SurveyState.DELETED &&
            s.trash_time == null &&
            (request.survey_id.isBlank() || s.survey_id == request.survey_id) &&
            (request.organization_id.isBlank() || s.organization_id == request.organization_id)
        }
    var totalEntities = 0L
    var totalAreaHa = 0.0
    for (s in activeSurveys) {
      val ents = store.read { it.entities(s.survey_id, includeDeleted = false) }
      totalEntities += ents.size
      totalAreaHa += ents.sumOf { it.area_hectares }
    }
    return GetImpactAggregatesResponse(
      aggregates =
        listOf(
          ImpactAggregate(
            run_id = "run-static-1",
            pipeline_version = 1,
            computed_at = store.watermark(),
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

  override suspend fun ListAuditRecords(
    request: ListAuditRecordsRequest
  ): ListAuditRecordsResponse {
    val page = store.read { snapshot ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "audit_records|survey_id=${request.survey_id}|target_id=${request.target_id}",
        records =
          snapshot.auditRecords().filter { a ->
            (request.survey_id.isBlank() || a.survey_id == request.survey_id) &&
              (request.target_id.isBlank() || a.target_id == request.target_id)
          },
        commitTimestamp = AuditRecord::commit_timestamp,
        id = AuditRecord::audit_id,
        query = commitOrderQuery(AuditRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
      )
    }
    return ListAuditRecordsResponse(
      audit_records = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun ListPlaces(request: ListPlacesRequest): ListPlacesResponse {
    val q = request.query.trim().lowercase()
    val filtered =
      if (q.isEmpty()) {
        store.read { it.places() }
      } else {
        store
          .read { it.places() }
          .filter {
            it.name.lowercase().contains(q) || it.region_subtitle.lowercase().contains(q)
          }
      }
    return ListPlacesResponse(places = filtered)
  }

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse {
    val page = store.read { snapshot ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "mutation_logs|survey_id=${request.survey_id}",
        records =
          snapshot.mutationLogs().filter {
            request.survey_id.isBlank() || it.survey_id == request.survey_id
          },
        commitTimestamp = MutationLogRecord::commit_timestamp,
        id = MutationLogRecord::id,
        query = commitOrderQuery(MutationLogRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
      )
    }
    return ListMutationLogsResponse(
      mutation_logs = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse {
    require(request.client_id.isNotBlank()) { "client_id is required" }
    val scopes = request.scopes.toSet()
    return GetAppliedMutationsResponse(
      watermarks =
        store
          .read { it.appliedWatermarks(request.client_id) }
          .filter { scopes.isEmpty() || it.scope in scopes }
    )
  }

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `ListSurveyOverviews` delta polls. A trashed survey's
   * overview is a tombstone (`state == DELETED`) carrying `trash_time`, so lists drop it.
   */
  private fun UserDataReader.overviewOf(surveyDef: SurveyDef): SurveyOverview {
    val acl = acl(surveyDef.survey_id)
    return SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      state = if (surveyDef.trash_time != null) SurveyState.DELETED else surveyDef.state,
      trash_time = surveyDef.trash_time,
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
      record_version = maxOf(surveyDef.record_version, acl?.record_version ?: 0L),
      has_working_copy = workingCopy(surveyDef.survey_id) != null,
      sharing_policy = acl?.sharing_policy ?: SharingPolicy.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
      sharing_role =
        acl?.sharing_role?.takeIf { it in SurveyPatches.SHARING_ROLES } ?: Role.DATA_COLLECTOR,
    )
  }

  companion object {
    /** Largest number of IDs served by one `BatchGetSurveyOverviews` request. */
    const val MAX_BATCH_SURVEY_IDS: Int = 100

    /** Organization whose library is the global library shared by all users. */
    const val ALL_USERS_ORGANIZATION_ID: String =
      org.groundplatform.v2.core.impact.ALL_USERS_ORGANIZATION_ID
  }
}
