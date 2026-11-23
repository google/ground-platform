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

import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import openforis.collectone.v1.data.ImpactEvent as ImpactEventProto
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.sync.ScopeChanges
import openforis.collectone.v1.sync.ScopeCursor
import openforis.collectone.v1.sync.ScopeRecords
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncScope
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncTokens
import org.openforis.collectone.v2.app.data.mapper.ProtoOrganizationMapper
import org.openforis.collectone.v2.app.data.mapper.ProtoRecordMapper
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.core.sync.EntityFieldMask

/** Builds, outside the transaction, the change that applies one page to the store. */
private typealias PageApplier<T> = suspend (List<T>) -> (LocalStoreTransaction.() -> Unit)

/**
 * Remote synchronization data source for clients that keep an offline copy (mobile). Syncs each
 * scope with its `List*` RPC (`docs/technical/sync-engine.md`, "List RPCs: Query and Delta Modes")
 * against [CollectOneGrpcClient] and applies the results to [LocalStore]:
 * - A scope without a sync token gets a full sync: a query with no filter, whose results replace
 *   the scope's bases (records missing from the result are forgotten) in one transaction.
 * - A scope with a token is caught up in delta mode. Each page is applied in its own transaction,
 *   together with the token to resume from, so an interrupted sync never skips changes.
 * - When the server can't serve changes from a token (`FAILED_PRECONDITION`), the token is
 *   discarded and the scope gets a full sync. Pending outbox mutations are kept and rebased.
 *
 * Sync tokens are opaque: they are stored ([SyncTokens]) and sent back unchanged. Foreground
 * freshness comes from repeating [pullIncrementalCatchUp] on a timer (Foreground Polling).
 *
 * With [downloadedSurveysOnly] (mobile), only the surveys in the device's downloaded set
 * ([org.openforis.collectone.v2.app.data.datasource.local.store.SyncState.downloadedSurveyIds]) are
 * kept offline (`docs/technical/sync-engine.md`, "Survey Downloads & Working Copies"):
 * - `ListSurveyOverviews` only fills the survey list, and is caught up only while the list is open
 *   ([setSurveyListOpen]). Its token lives in memory, so each open starts with a full query. Other
 *   surveys are listed from their overviews without fetching their definitions or data.
 * - Organization libraries aren't synced: only the web console's survey editor uses them.
 * - Downloaded surveys are refreshed with `BatchGetSurveyOverviews`, so link-shared and public
 *   surveys the list doesn't include stay current too. A newer `record_version` fetches the survey
 *   again; a trashed survey leaves the downloaded set; one the caller can no longer read (or that
 *   was purged) is marked unavailable and keeps its local copy and pending uploads, but stops
 *   syncing until it's available again.
 * - Entities and submissions are synced for downloaded surveys only.
 * - A survey that leaves the downloaded set keeps its local copy until its pending uploads have
 *   drained; then its definition, data, and tokens are dropped, and it's removed from the list
 *   unless `ListSurveyOverviews` still lists it.
 *
 * This class orchestrates the scopes and owns the sync tokens. The `List*` and `GetSurvey` paging
 * lives in [SyncPageFetcher], the survey list and downloaded set in [DownloadedSurveyCatalog], and
 * the seed's store write in [applySeedSnapshot]. Calls must not overlap: the in-memory tokens and
 * caches assume one sync at a time (`SyncCoordinator` serializes catch-ups behind a lock).
 */
class GrpcSyncDataSource(
  val grpcClient: CollectOneGrpcClient,
  /** Whether to sync only the surveys the user downloaded (mobile) instead of every survey. */
  val downloadedSurveysOnly: Boolean = false,
  /**
   * Whether the sync engine's outbox owns local upload statuses: when true, records fetched during
   * initial seed start as `SYNCED` (rebased onto any pending outbox mutations) rather than keeping
   * static sample sync statuses.
   */
  private val trackOutbox: Boolean = false,
  /** Measures the time since the last full pull ([timeSinceLastFullPull]); replaceable in tests. */
  private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
  /**
   * Whether entity syncs request only lightweight metadata + core/concept-linked properties,
   * leaving bulk tabular/enrichment columns to be lazy-loaded on scroll by the web console tables.
   */
  private val lazyLoadEntityProperties: Boolean = false,
) {
  /** The tokens after the last applied page, mirrored from (and written through to) the store. */
  private var syncTokens = SyncTokens()
  private val pages = SyncPageFetcher(grpcClient)
  private val catalog = DownloadedSurveyCatalog(grpcClient, pages)
  private val entityFieldMasksBySurvey = mutableMapOf<String, List<String>>()

  /** When the last seed or unscoped [pullIncrementalCatchUp] finished; null before the first. */
  private var lastFullPull: ComparableTimeMark? = null

  /**
   * Tells a data source with [downloadedSurveysOnly] whether the survey list is on screen. Syncs
   * catch up the list only while it's open; closing it discards the list's token, so the next open
   * starts with a fresh query.
   */
  fun setSurveyListOpen(open: Boolean) {
    catalog.setListOpen(open)
  }

  /** Sync tokens after the last applied page. */
  fun currentSyncTokens(): SyncTokens = syncTokens

  /**
   * Time since the last seed ([seedStoreFromGrpc]) or unscoped [pullIncrementalCatchUp] finished,
   * or null if neither has finished since the tokens were last reset.
   */
  fun timeSinceLastFullPull(): Duration? = lastFullPull?.let { timeSource.markNow() - it }

  /** Forgets all sync tokens, so the next pull is a full sync. */
  fun resetSyncTokens() {
    lastFullPull = null
    syncTokens = SyncTokens()
    entityFieldMasksBySurvey.clear()
    catalog.reset()
  }

  /**
   * Computes the lightweight entity sync field mask for [surveyDef]: metadata, geometry, core
   * workflow/sample-plot keys, plus key/label and concept-linked properties needed by Impact.
   */
  private fun entityFieldMaskFor(surveyDef: SurveyDef): List<String> {
    val extras = buildList {
      surveyDef.entity_datasets.forEach { ds ->
        if (ds.key_property.isNotBlank()) add(ds.key_property)
        if (ds.label_property.isNotBlank()) add(ds.label_property)
        ds.properties.forEach { prop ->
          if (prop.concept_ref != null && prop.name.isNotBlank()) {
            add(prop.name)
            if (prop.label.isNotBlank()) add(prop.label)
          }
        }
      }
    }
    return EntityFieldMask.forLightweightSync(extras)
  }

  /**
   * Performs an initial full sync: `ListSurveyOverviews` for the survey catalog, `GetSurvey` for
   * full survey definitions, and query-mode `ListOrganizations`, `ListLibraries`, `ListEntities`,
   * and `ListSubmissions` (per survey) to populate [store] inside a single atomic transaction,
   * storing each scope's `next_sync_token`.
   *
   * The downloaded set starts with the surveys marked as downloaded by default
   * (`SurveyOverview.is_downloaded`). With [downloadedSurveysOnly], only those surveys' definitions
   * and data are fetched; the others are listed from their overviews.
   */
  suspend fun seedStoreFromGrpc(
    store: LocalStore,
    defaultActiveSurveyId: String,
    seedVersion: Int,
  ) {
    resetSyncTokens()
    val seed = fetchSeed(store)
    val live = seed.overviews.records.filter { it.state != SurveyState.DELETED }
    val downloaded = live.filter { it.is_downloaded }.map { it.survey_id }.toSet()
    val catalogOnly =
      if (downloadedSurveysOnly) live.filter { it.survey_id !in downloaded } else emptyList()

    val seeded =
      SyncTokens(
        organizations = seed.organizations.syncToken,
        libraries = seed.libraries.syncToken,
        surveys = if (downloadedSurveysOnly) "" else seed.overviews.syncToken,
        entitiesBySurvey = seed.entities.mapValues { it.value.syncToken },
        submissionsBySurvey = seed.submissions.mapValues { it.value.syncToken },
      )
    store.transaction {
      updateSyncState { it.copy(syncTokens = seeded, downloadedSurveyIds = downloaded) }
      applySeedSnapshot(seed, defaultActiveSurveyId, seedVersion, trackOutbox = trackOutbox)
      catalogOnly.forEach { upsertSurvey(catalogPreview(it)) }
    }
    syncTokens = seeded
    if (downloadedSurveysOnly) {
      catalog.onSeeded(seed.overviews.syncToken, live.map { it.survey_id }.toSet())
    }
    lastFullPull = timeSource.markNow()
  }

  /**
   * Fetches everything a seed stores, concurrently: every scope in query mode, the full revisions
   * of the synced surveys, places, and the mutation log. Only organizations and libraries are
   * written as they arrive (see below); the rest waits for [applySeedSnapshot]. Runs on the
   * caller's dispatcher (unconfined when it has none).
   */
  private suspend fun fetchSeed(store: LocalStore): SeedSnapshot {
    val dispatcher = currentCoroutineContext()[ContinuationInterceptor] ?: Dispatchers.Unconfined
    return withContext(dispatcher) {
      // Organizations and libraries are small and independent of survey data, so they're stored
      // as soon as they arrive: the survey wizard's purposes don't wait for every survey's
      // entities and submissions. The final transaction below stores them again with the rest.
      val organizationsDeferred = async {
        pages
          .fullList { pages.organizationsPage(syncToken = "", pageToken = it) }
          .also { orgs ->
            store.transaction {
              orgs.records.forEach {
                putOrganizationBase(it.organization_id, it)
                upsertOrganization(ProtoOrganizationMapper.organizationFromProto(it))
              }
            }
          }
      }
      val librariesDeferred = async {
        if (downloadedSurveysOnly) FullList(emptyList(), syncToken = "")
        else
          pages
            .fullList { pages.librariesPage(syncToken = "", pageToken = it) }
            .also { libs ->
              store.transaction {
                libs.records.forEach {
                  putLibraryBase(it.organization_id, it)
                  putLibrary(ProtoOrganizationMapper.libraryFromProto(it))
                }
              }
            }
      }
      val overviewsDeferred = async {
        pages.fullList { pages.overviewsPage(syncToken = "", pageToken = it) }
      }
      val placesDeferred = async { grpcClient.listPlaces(ListPlacesRequest()) }
      val mutationLogsDeferred = async {
        pages.fullList { pages.mutationLogsPage(syncToken = "", pageToken = it) }
      }

      val overviewsResult = overviewsDeferred.await()
      val live = overviewsResult.records.filter { it.state != SurveyState.DELETED }
      val downloaded = live.filter { it.is_downloaded }.map { it.survey_id }.toSet()
      val synced = if (downloadedSurveysOnly) live.filter { it.survey_id in downloaded } else live
      val surveyIds = synced.map { it.survey_id }

      val fullSurveysDeferred = synced.map { overview ->
        async { pages.fetchSurveyRevisions(overview) }
      }
      val submissionsDeferred = surveyIds.map { sid ->
        sid to async { pages.fullList { pages.submissionsPage(sid, "", it) } }
      }
      val fullSurveys =
        if (lazyLoadEntityProperties) {
          fullSurveysDeferred.awaitAll().also(::cacheEntityFieldMasks)
        } else {
          null
        }
      val entitiesDeferred = surveyIds.map { sid ->
        val mask = entityFieldMaskForSurvey(sid)
        sid to async { pages.fullList { pages.entitiesPage(sid, "", it, fieldMask = mask) } }
      }

      SeedSnapshot(
        organizations = organizationsDeferred.await(),
        libraries = librariesDeferred.await(),
        overviews = overviewsResult,
        fullSurveys = fullSurveys ?: fullSurveysDeferred.awaitAll(),
        entities = entitiesDeferred.associate { (sid, deferred) -> sid to deferred.await() },
        submissions = submissionsDeferred.associate { (sid, deferred) -> sid to deferred.await() },
        placesResponse = placesDeferred.await(),
        mutationLogs = mutationLogsDeferred.await(),
      )
    }
  }

  private fun cacheEntityFieldMasks(revisions: List<SurveyRevisions>) {
    revisions.forEach { rev ->
      rev.saved.survey?.let { surveyDef ->
        entityFieldMasksBySurvey[surveyDef.survey_id] = entityFieldMaskFor(surveyDef)
      }
    }
  }

  private fun entityFieldMaskForSurvey(surveyId: String): List<String> =
    if (lazyLoadEntityProperties) {
      entityFieldMasksBySurvey[surveyId] ?: EntityFieldMask.forLightweightSync()
    } else {
      emptyList()
    }

  /**
   * Catches up every scope from its stored sync token (or with a full sync when it has none):
   * organizations, libraries, survey overviews (with the saved revisions of changed surveys
   * inlined), the entities and submissions of every local survey (only [surveyId]'s when set), and
   * impact events. Accepted records become the new server bases and pending mutations are rebased
   * on top of them. With [downloadedSurveysOnly], surveys and their data are synced as described on
   * the class.
   *
   * All scopes are caught up together with `SyncChanges` (one call, plus one per extra page), and
   * surveys that the overviews just added get their data in one more call.
   */
  suspend fun pullIncrementalCatchUp(store: LocalStore, surveyId: String? = null): SyncTokens {
    syncTokens = store.transaction { syncState().syncTokens }
    val scopes = mutableListOf<ScopeSync<*>>(organizationsScope())
    // Mobile doesn't keep libraries: only the web console's survey editor uses them.
    if (!downloadedSurveysOnly) scopes += librariesScope()
    val dataSurveyIds =
      if (downloadedSurveysOnly) {
        if (catalog.isListOpen) catalog.syncCatalog(store)
        val available = catalog.refreshDownloadedSurveys(store)
        if (surveyId != null) listOf(surveyId).filter { it in available } else available.toList()
      } else {
        scopes += surveyOverviewsScope()
        surveyId?.let(::listOf) ?: store.transaction { surveys().map { it.id } }
      }
    for (sid in dataSurveyIds) scopes += dataScopes(sid)
    scopes += impactEventsScope()
    syncScopes(store, scopes)
    if (!downloadedSurveysOnly && surveyId == null) {
      // Surveys the overviews just added catch up their data too.
      val added = store.transaction { surveys().map { it.id } } - dataSurveyIds.toSet()
      if (added.isNotEmpty()) syncScopes(store, added.flatMap(::dataScopes))
    }
    if (downloadedSurveysOnly) catalog.removeUndownloadedSurveys(store)
    if (surveyId == null) lastFullPull = timeSource.markNow()
    return syncTokens
  }

  private fun organizationsScope() =
    ScopeSync(
      scope = SyncScope(organizations = SyncScope.All()),
      token = { it.organizations },
      withToken = { tokens, token -> tokens.copy(organizations = token) },
      records = ScopeRecords::organizations,
      applyDelta = { records -> { records.forEach(Rebaser(this)::acceptOrganization) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          records.forEach { rebaser.acceptOrganization(it, replace = true) }
          val listed = records.map { it.organization_id }.toSet()
          organizations()
            .map { it.id }
            .filter { it !in listed && organizationBase(it) != null }
            .forEach(rebaser::forgetOrganization)
        }
      },
    )

  private fun librariesScope() =
    ScopeSync(
      scope = SyncScope(libraries = SyncScope.All()),
      token = { it.libraries },
      withToken = { tokens, token -> tokens.copy(libraries = token) },
      records = ScopeRecords::libraries,
      applyDelta = { records -> { records.forEach(Rebaser(this)::acceptLibrary) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          records.forEach { rebaser.acceptLibrary(it, replace = true) }
          val listed = records.map { it.organization_id }.toSet()
          libraries()
            .keys
            .filter { it !in listed && libraryBase(it) != null }
            .forEach(rebaser::forgetLibrary)
        }
      },
    )

  /** Every survey the caller can list, with its stored `ListSurveyOverviews` token. */
  private fun surveyOverviewsScope() =
    ScopeSync(
      scope = SyncScope(survey_overviews = SyncScope.All()),
      token = { it.surveys },
      withToken = { tokens, token -> tokens.copy(surveys = token) },
      records = { records ->
        pages.inlineSurveyDefinitions(records.survey_definitions)
        records.survey_overviews
      },
      applyDelta = { overviews -> surveyApplier(overviews, replace = false) },
      applyFull = { overviews ->
        val apply = surveyApplier(overviews, replace = true)
        val listed = overviews.map { it.survey_id }.toSet()
        val block: LocalStoreTransaction.() -> Unit = {
          apply()
          val rebaser = Rebaser(this)
          for (sid in surveys().map { it.id }) {
            if (sid in listed || surveyBase(sid) == null) continue
            rebaser.forgetSurvey(sid)
            if (survey(sid) == null) forgetDataTokens(sid)
          }
        }
        block
      },
    )

  /** The entity and submission scopes of survey [sid]. */
  private fun dataScopes(sid: String): List<ScopeSync<*>> =
    listOf(entitiesScope(sid), submissionsScope(sid))

  private fun entitiesScope(sid: String) =
    ScopeSync(
      scope = SyncScope(survey_entities = sid),
      token = { it.entitiesBySurvey[sid].orEmpty() },
      withToken = { tokens, token ->
        tokens.copy(entitiesBySurvey = tokens.entitiesBySurvey.withToken(sid, token))
      },
      records = ScopeRecords::entities,
      applyDelta = { records -> { Rebaser(this).acceptData(sid, records, emptyList()) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          rebaser.acceptData(sid, records, emptyList(), replace = true)
          val listed = records.map { it.entity_id }.toSet()
          rebaser.forgetData(sid, entityBases(sid).keys.filter { it !in listed }, emptyList())
        }
      },
      surveyId = sid,
      fieldMask = {
        if (lazyLoadEntityProperties) {
          entityFieldMasksBySurvey[sid] ?: EntityFieldMask.forLightweightSync()
        } else {
          emptyList()
        }
      },
    )

  private fun submissionsScope(sid: String) =
    ScopeSync(
      scope = SyncScope(survey_submissions = sid),
      token = { it.submissionsBySurvey[sid].orEmpty() },
      withToken = { tokens, token ->
        tokens.copy(submissionsBySurvey = tokens.submissionsBySurvey.withToken(sid, token))
      },
      records = ScopeRecords::submissions,
      applyDelta = { records -> { Rebaser(this).acceptData(sid, emptyList(), records) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          rebaser.acceptData(sid, emptyList(), records, replace = true)
          val listed = records.map { it.submission_id }.toSet()
          rebaser.forgetData(sid, emptyList(), submissionBases(sid).keys.filter { it !in listed })
        }
      },
      surveyId = sid,
    )

  private fun impactEventsScope(): ScopeSync<ImpactEventProto> {
    // Impact events are append-only, and events recorded on this device are kept until uploaded,
    // so both modes merge by ID.
    val mergeImpactEvents: PageApplier<ImpactEventProto> = { events ->
      {
        if (events.isNotEmpty()) {
          val existingById = impactEvents().associateBy { it.id }.toMutableMap()
          for (evProto in events) {
            val domainEv = ProtoRecordMapper.impactEventFromProto(evProto)
            existingById[domainEv.id] = domainEv
          }
          putImpactEvents(existingById.values.toList())
        }
      }
    }
    return ScopeSync(
      scope = SyncScope(impact_events = SyncScope.All()),
      token = { it.impactEvents },
      withToken = { tokens, token -> tokens.copy(impactEvents = token) },
      records = ScopeRecords::impact_events,
      applyDelta = mergeImpactEvents,
      applyFull = mergeImpactEvents,
    )
  }

  /**
   * Targeted catch-up for a single survey's definition, forms, lifecycle/trash state, and ACLs,
   * triggered by a `survey_<survey_id>_def` FCM topic tickle.
   *
   * When [minRecordVersion] is provided and the local [SurveyBase] already has `record_version >=
   * minRecordVersion`, the call returns immediately without issuing an RPC. Does not call
   * `ListEntities` or `ListSubmissions`.
   */
  suspend fun pullSurveyDefinitionCatchUp(
    store: LocalStore,
    surveyId: String,
    minRecordVersion: Long? = null,
  ): SyncTokens {
    val (storedTokens, downloaded, storedVersion) =
      store.transaction {
        val state = syncState()
        Triple(
          state.syncTokens,
          state.downloadedSurveyIds,
          surveyBase(surveyId)?.let(Rebaser::recordVersionOf),
        )
      }
    syncTokens = storedTokens
    if (downloadedSurveysOnly && surveyId !in downloaded) {
      return syncTokens
    }
    if (minRecordVersion != null && storedVersion != null && storedVersion >= minRecordVersion) {
      return syncTokens
    }
    catalog.refreshDownloadedSurveys(store, targetSurveyIds = setOf(surveyId))
    if (downloadedSurveysOnly) catalog.removeUndownloadedSurveys(store)
    return syncTokens
  }

  /**
   * Targeted catch-up for a single survey's entities (including plot locks), triggered by a
   * `survey_<survey_id>_entities` FCM topic tickle.
   *
   * Syncs only the survey's entity scope (one `SyncChanges` cursor) in delta mode, or full mode if
   * no token is stored yet, and does not call `BatchGetSurveyOverviews`, `GetSurvey`, or sync
   * submissions.
   */
  suspend fun pullSurveyEntitiesCatchUp(store: LocalStore, surveyId: String): SyncTokens {
    val (storedTokens, downloaded) =
      store.transaction {
        val state = syncState()
        state.syncTokens to state.downloadedSurveyIds
      }
    syncTokens = storedTokens
    if (downloadedSurveysOnly && surveyId !in downloaded) {
      return syncTokens
    }
    syncScopes(store, listOf(entitiesScope(surveyId)))
    return syncTokens
  }

  /**
   * One scope of a batched catch-up: how to read and store its sync token, and how to apply its
   * pages (`docs/technical/sync-engine.md`, "List RPCs: Query and Delta Modes").
   *
   * In delta mode each page is applied in its own transaction with the token to resume from, so an
   * interrupted sync never skips changes. Without a token, or when the server rejects it with
   * `FAILED_PRECONDITION`, the scope runs a full sync whose pages are applied together.
   *
   * [surveyId] is set for a survey's data scopes. Their pages are dropped when the survey is no
   * longer stored locally (for example when the overviews in the same batch deleted it), and a
   * survey the caller can no longer read is marked unavailable with [downloadedSurveysOnly].
   */
  private inner class ScopeSync<T>(
    val scope: SyncScope,
    private val token: (SyncTokens) -> String,
    private val withToken: (SyncTokens, String) -> SyncTokens,
    private val records: (ScopeRecords) -> List<T>,
    private val applyDelta: PageApplier<T>,
    private val applyFull: PageApplier<T>,
    private val surveyId: String? = null,
    private val fieldMask: () -> List<String> = { emptyList() },
  ) {
    private var syncToken = ""
    private var pageToken = ""
    private var isFull = false
    private val fullRecords = mutableListOf<T>()

    /** Whether the scope is caught up (or failed). */
    var isDone = false
      private set

    fun start() {
      syncToken = token(syncTokens)
      pageToken = ""
      isFull = syncToken.isEmpty()
      fullRecords.clear()
      isDone = false
    }

    fun cursor() =
      ScopeCursor(
        scope = scope,
        sync_token = if (isFull) "" else syncToken,
        page_token = pageToken,
        field_mask = fieldMask(),
      )

    suspend fun accept(store: LocalStore, changes: ScopeChanges) {
      if (surveyId != null && store.transaction { survey(surveyId) == null }) {
        isDone = true
        return
      }
      if (changes.error_code != 0) {
        if (!isFull && changes.error_code == GrpcWebTransport.FAILED_PRECONDITION) {
          // The server can't serve changes from the token any more: discard it and resync.
          commit(store, withToken, token = "") { {} }
          isFull = true
          pageToken = ""
          return
        }
        isDone = true
        fail(store, changes)
        return
      }
      val page = records(changes.records ?: ScopeRecords())
      if (isFull) {
        fullRecords += page
        if (changes.next_page_token.isEmpty()) {
          val all = fullRecords.toList()
          commit(store, withToken, changes.next_sync_token) { applyFull(all) }
          isDone = true
        }
      } else {
        // In delta mode, `next_page_token` is itself a sync token for the scope, so storing it lets
        // an interrupted sync resume after the last applied page.
        commit(store, withToken, changes.next_page_token.ifEmpty { changes.next_sync_token }) {
          applyDelta(page)
        }
        isDone = changes.next_page_token.isEmpty()
      }
      pageToken = changes.next_page_token
    }

    private suspend fun fail(store: LocalStore, changes: ScopeChanges) {
      val status = changes.error_code
      if (surveyId != null && downloadedSurveysOnly && status in UNAVAILABLE_SURVEY_STATUSES) {
        // Lost access or purged since `BatchGetSurveyOverviews`: keep the local copy and retry
        // later.
        store.transaction { updateSurvey(surveyId) { it.copy(isUnavailable = true) } }
        return
      }
      throw GrpcWebException(status, SYNC_CHANGES_PATH, changes.error_message.ifEmpty { null })
    }
  }

  /**
   * Catches up [scopes] with `SyncChanges`: one call for the first page of every scope, then one
   * more per round of extra pages, sending only the scopes that have more.
   */
  private suspend fun syncScopes(store: LocalStore, scopes: List<ScopeSync<*>>) {
    pages.clearInlinedSurveyDefinitions()
    scopes.forEach { it.start() }
    var pending = scopes
    while (pending.isNotEmpty()) {
      val response =
        grpcClient.syncChanges(
          SyncChangesRequest(
            cursors = pending.map { it.cursor() },
            include_survey_definitions = pending.any { it.scope.survey_overviews != null },
          )
        )
      check(response.scopes.size == pending.size) {
        "SyncChanges returned ${response.scopes.size} scopes for ${pending.size} cursors"
      }
      pending.zip(response.scopes).forEach { (scope, changes) -> scope.accept(store, changes) }
      pending = pending.filter { !it.isDone }
    }
    pages.clearInlinedSurveyDefinitions()
  }

  /** Applies [change] and stores [token] for the scope in one transaction. */
  private suspend fun commit(
    store: LocalStore,
    withToken: (SyncTokens, String) -> SyncTokens,
    token: String,
    change: suspend () -> (LocalStoreTransaction.() -> Unit),
  ) {
    val apply = change()
    syncTokens = store.transaction {
      apply()
      updateSyncState { it.copy(syncTokens = withToken(it.syncTokens, token)) }.syncTokens
    }
  }

  /** Fetches the revisions of changed surveys, then accepts them (and deletions) as new bases. */
  private suspend fun surveyApplier(
    overviews: List<SurveyOverview>,
    replace: Boolean,
  ): LocalStoreTransaction.() -> Unit {
    val revisionsById =
      overviews
        .filter { it.state != SurveyState.DELETED }
        .associate { overview -> overview.survey_id to pages.fetchSurveyRevisions(overview) }
    if (lazyLoadEntityProperties) {
      revisionsById.forEach { (sid, rev) ->
        rev.saved.survey?.let { surveyDef ->
          entityFieldMasksBySurvey[sid] = entityFieldMaskFor(surveyDef)
        }
      }
    }
    return {
      val rebaser = Rebaser(this)
      for (overview in overviews) {
        val sid = overview.survey_id
        if (overview.state == SurveyState.DELETED) {
          rebaser.acceptSurveyDeleted(sid)
          forgetDataTokens(sid)
          continue
        }
        val revisions = revisionsById[sid] ?: continue
        val saved = revisions.saved.survey ?: continue
        rebaser.acceptSurvey(
          sid,
          SurveyBase(saved = saved, workingCopy = revisions.workingCopy, acl = revisions.saved.acl),
          overview,
          replace = replace,
        )
      }
    }
  }

  /** Uploads offline impact events via `RecordImpactEvents`. */
  suspend fun pushImpactEvents(
    events: List<org.openforis.collectone.v2.app.domain.model.ImpactEvent>
  ) {
    if (events.isEmpty()) return
    grpcClient.recordImpactEvents(
      RecordImpactEventsRequest(events = events.map { ProtoRecordMapper.impactEventToProto(it) })
    )
  }

  private companion object {
    /** Method path reported on `SyncChanges` scope failures. */
    const val SYNC_CHANGES_PATH = "/openforis.collectone.v1.sync.SyncService/SyncChanges"

    /** Statuses of a survey the caller can no longer read (or that was purged). */
    val UNAVAILABLE_SURVEY_STATUSES =
      setOf(GrpcWebTransport.PERMISSION_DENIED, GrpcWebTransport.NOT_FOUND)
  }
}
