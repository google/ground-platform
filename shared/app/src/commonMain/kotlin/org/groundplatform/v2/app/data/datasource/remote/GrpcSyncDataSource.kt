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

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ImpactEvent as ImpactEventProto
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.data.ListImpactEventsRequest
import groundplatform.v2.data.ListMutationLogsRequest
import groundplatform.v2.data.ListPlacesRequest
import groundplatform.v2.data.ListSubmissionsRequest
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.ListLibrariesRequest
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.BatchGetSurveyOverviewsRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.GetSurveyResponse
import groundplatform.v2.survey.ListOrganizationsRequest
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyState
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.client.grpc.GrpcWebException
import org.groundplatform.v2.app.client.grpc.GrpcWebTransport
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.StoredPreferences
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase
import org.groundplatform.v2.app.data.datasource.local.store.SyncTokens
import org.groundplatform.v2.app.data.sync.Rebaser
import org.groundplatform.v2.app.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.UserSettings

/** The saved revision of a survey (with its ACL) and the organizer's working copy, if any. */
private data class SurveyRevisions(val saved: GetSurveyResponse, val workingCopy: SurveyDef?)

/** One page of a `List*` response. */
private class ListPage<T>(
  val records: List<T>,
  val nextPageToken: String,
  val nextSyncToken: String,
)

/** Every record of a query-mode `List*` call and the last page's `next_sync_token`. */
private class FullList<T>(val records: List<T>, val syncToken: String)

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
 * ([org.groundplatform.v2.app.data.datasource.local.store.SyncState.downloadedSurveyIds]) are kept
 * offline (`docs/technical/sync-engine.md`, "Survey Downloads & Working Copies"):
 * - `ListSurveyOverviews` only fills the survey list. Its token lives in memory, so each launch
 *   starts with a full query, and other surveys are listed from their overviews without fetching
 *   their definitions or data.
 * - Downloaded surveys are refreshed with `BatchGetSurveyOverviews`, so link-shared and public
 *   surveys the list doesn't include stay current too. A newer `record_version` fetches the survey
 *   again; a trashed survey leaves the downloaded set; one the caller can no longer read (or that
 *   was purged) keeps its local copy and pending uploads but stops syncing.
 * - Entities and submissions are synced for downloaded surveys only.
 * - A survey that leaves the downloaded set keeps its local copy until its pending uploads have
 *   drained; then its definition, data, and tokens are dropped, and it's removed from the list
 *   unless `ListSurveyOverviews` still lists it.
 */
class GrpcSyncDataSource(
  val grpcClient: CollectOneGrpcClient,
  /** Whether to sync only the surveys the user downloaded (mobile) instead of every survey. */
  val downloadedSurveysOnly: Boolean = false,
) {
  private var syncTokens = SyncTokens()

  /** In-memory `ListSurveyOverviews` token, with [downloadedSurveysOnly]. */
  private var catalogToken = ""

  /**
   * IDs of the surveys `ListSurveyOverviews` lists, with [downloadedSurveysOnly]; null until the
   * first full query.
   */
  private var catalogIds: Set<String>? = null

  /** Sync tokens after the last applied page. */
  fun currentSyncTokens(): SyncTokens = syncTokens

  /** Forgets all sync tokens, so the next pull is a full sync. */
  fun resetSyncTokens() {
    syncTokens = SyncTokens()
    catalogToken = ""
    catalogIds = null
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
    val organizations = fullList { organizationsPage(syncToken = "", pageToken = it) }
    val libraries = fullList { librariesPage(syncToken = "", pageToken = it) }
    val overviews = fullList { overviewsPage(syncToken = "", pageToken = it) }
    val live = overviews.records.filter { it.state != SurveyState.DELETED }
    val downloaded = live.filter { it.is_downloaded }.map { it.survey_id }.toSet()
    val synced = if (downloadedSurveysOnly) live.filter { it.survey_id in downloaded } else live
    val catalogOnly =
      if (downloadedSurveysOnly) live.filter { it.survey_id !in downloaded } else emptyList()
    val fullSurveys = synced.map { overview -> fetchSurveyRevisions(overview) }
    val surveyIds = synced.map { it.survey_id }
    val entities = surveyIds.associateWith { sid -> fullList { entitiesPage(sid, "", it) } }
    val submissions = surveyIds.associateWith { sid -> fullList { submissionsPage(sid, "", it) } }
    val placesResponse = grpcClient.listPlaces(ListPlacesRequest())
    val mutationLogs = fullList { mutationLogsPage(syncToken = "", pageToken = it) }

    val seeded =
      SyncTokens(
        organizations = organizations.syncToken,
        libraries = libraries.syncToken,
        surveys = if (downloadedSurveysOnly) "" else overviews.syncToken,
        entitiesBySurvey = entities.mapValues { it.value.syncToken },
        submissionsBySurvey = submissions.mapValues { it.value.syncToken },
      )
    store.transaction {
      updateSyncState { it.copy(syncTokens = seeded, downloadedSurveyIds = downloaded) }
      applySnapshotToTransaction(
        orgDeltas = organizations.records,
        libDeltas = libraries.records,
        fullSurveys = fullSurveys,
        entityDeltas = entities.values.flatMap { it.records },
        submissionDeltas = submissions.values.flatMap { it.records },
        places = placesResponse.places.map(GrpcProtoMapper::placeFromProto),
        mutationLogs = mutationLogs.records.map(GrpcProtoMapper::mutationLogFromProto),
        defaultActiveSurveyId = defaultActiveSurveyId,
        seedVersion = seedVersion,
      )
      catalogOnly.forEach { upsertSurvey(catalogPreview(it)) }
    }
    syncTokens = seeded
    if (downloadedSurveysOnly) {
      catalogToken = overviews.syncToken
      catalogIds = live.map { it.survey_id }.toSet()
    }
  }

  /**
   * Catches up every scope from its stored sync token (or with a full sync when it has none):
   * organizations, libraries, survey overviews (fetching changed surveys with `GetSurvey`), the
   * entities and submissions of every local survey (only [surveyId]'s when set), and impact events.
   * Accepted records become the new server bases and pending mutations are rebased on top of them.
   * With [downloadedSurveysOnly], surveys and their data are synced as described on the class.
   */
  suspend fun pullIncrementalCatchUp(store: LocalStore, surveyId: String? = null): SyncTokens {
    syncTokens = store.transaction { syncState().syncTokens }
    syncScope(
      store = store,
      token = { it.organizations },
      withToken = { tokens, token -> tokens.copy(organizations = token) },
      fetch = ::organizationsPage,
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
    syncScope(
      store = store,
      token = { it.libraries },
      withToken = { tokens, token -> tokens.copy(libraries = token) },
      fetch = ::librariesPage,
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
    val targetSurveyIds =
      if (downloadedSurveysOnly) {
        syncCatalog(store)
        val available = refreshDownloadedSurveys(store)
        if (surveyId != null) listOf(surveyId).filter { it in available } else available.toList()
      } else {
        syncAllSurveys(store)
        surveyId?.let(::listOf) ?: store.transaction { surveys().map { it.id } }
      }
    for (sid in targetSurveyIds) {
      try {
        syncSurveyData(store, sid)
      } catch (e: GrpcWebException) {
        // Lost access or purged since `BatchGetSurveyOverviews`: keep the local copy and retry
        // later.
        if (!downloadedSurveysOnly || e.status !in UNAVAILABLE_SURVEY_STATUSES) throw e
      }
    }
    if (downloadedSurveysOnly) removeUndownloadedSurveys(store)

    // Impact events are append-only, and events recorded on this device are kept until uploaded,
    // so both modes merge by ID.
    val mergeImpactEvents: PageApplier<ImpactEventProto> = { events ->
      {
        if (events.isNotEmpty()) {
          val existingById = impactEvents().associateBy { it.id }.toMutableMap()
          for (evProto in events) {
            val domainEv = GrpcProtoMapper.impactEventFromProto(evProto)
            existingById[domainEv.id] = domainEv
          }
          putImpactEvents(existingById.values.toList())
        }
      }
    }
    syncScope(
      store = store,
      token = { it.impactEvents },
      withToken = { tokens, token -> tokens.copy(impactEvents = token) },
      fetch = ::impactEventsPage,
      applyDelta = mergeImpactEvents,
      applyFull = mergeImpactEvents,
    )
    return syncTokens
  }

  /** Syncs every survey the caller can list, with its stored `ListSurveyOverviews` token. */
  private suspend fun syncAllSurveys(store: LocalStore) {
    syncScope(
      store = store,
      token = { it.surveys },
      withToken = { tokens, token -> tokens.copy(surveys = token) },
      fetch = ::overviewsPage,
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
  }

  /** Syncs the entities and submissions of survey [sid]. */
  private suspend fun syncSurveyData(store: LocalStore, sid: String) {
    syncScope(
      store = store,
      token = { it.entitiesBySurvey[sid].orEmpty() },
      withToken = { tokens, token ->
        tokens.copy(entitiesBySurvey = tokens.entitiesBySurvey.withToken(sid, token))
      },
      fetch = { syncToken, pageToken -> entitiesPage(sid, syncToken, pageToken) },
      applyDelta = { records -> { Rebaser(this).acceptData(sid, records, emptyList()) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          rebaser.acceptData(sid, records, emptyList(), replace = true)
          val listed = records.map { it.entity_id }.toSet()
          rebaser.forgetData(sid, entityBases(sid).keys.filter { it !in listed }, emptyList())
        }
      },
    )
    syncScope(
      store = store,
      token = { it.submissionsBySurvey[sid].orEmpty() },
      withToken = { tokens, token ->
        tokens.copy(submissionsBySurvey = tokens.submissionsBySurvey.withToken(sid, token))
      },
      fetch = { syncToken, pageToken -> submissionsPage(sid, syncToken, pageToken) },
      applyDelta = { records -> { Rebaser(this).acceptData(sid, emptyList(), records) } },
      applyFull = { records ->
        {
          val rebaser = Rebaser(this)
          rebaser.acceptData(sid, emptyList(), records, replace = true)
          val listed = records.map { it.submission_id }.toSet()
          rebaser.forgetData(sid, emptyList(), submissionBases(sid).keys.filter { it !in listed })
        }
      },
    )
  }

  /**
   * Syncs the survey list with `ListSurveyOverviews` in delta mode from the in-memory token, or
   * with a full query when there is none or the server rejects it. Surveys that aren't downloaded
   * are listed from their overviews; downloaded ones are left to [refreshDownloadedSurveys].
   */
  private suspend fun syncCatalog(store: LocalStore) {
    val stored = catalogToken
    if (stored.isNotEmpty() && catalogIds != null) {
      try {
        var pageToken = ""
        do {
          val page = overviewsPage(stored, pageToken)
          val listed = catalogIds.orEmpty() - page.records.map { it.survey_id }.toSet()
          catalogIds = listed + store.transaction { applyCatalog(page.records) }
          catalogToken = page.nextPageToken.ifEmpty { page.nextSyncToken }
          pageToken = page.nextPageToken
        } while (pageToken.isNotEmpty())
        return
      } catch (e: GrpcWebException) {
        if (e.status != GrpcWebTransport.FAILED_PRECONDITION) throw e
      }
    }
    val full = fullList { overviewsPage("", it) }
    catalogIds = store.transaction { applyCatalog(full.records) }
    catalogToken = full.syncToken
  }

  /**
   * Lists the surveys of [overviews] that aren't downloaded, aren't pending removal, and have no
   * pending edits, and returns the IDs of the listed (not deleted) surveys.
   */
  private fun LocalStoreTransaction.applyCatalog(overviews: List<SurveyOverview>): Set<String> {
    val live = overviews.filter { it.state != SurveyState.DELETED }
    val downloaded = syncState().downloadedSurveyIds
    val pending = surveysWithPendingMutations()
    for (overview in live) {
      val sid = overview.survey_id
      if (sid in downloaded || sid in pending || surveyBase(sid) != null) continue
      upsertSurvey(catalogPreview(overview))
    }
    return live.map { it.survey_id }.toSet()
  }

  /**
   * Refreshes the downloaded surveys with `BatchGetSurveyOverviews`, fetching new or changed ones
   * with `GetSurvey`, and returns the IDs of those whose data can be synced.
   */
  private suspend fun refreshDownloadedSurveys(store: LocalStore): Set<String> {
    val downloaded = store.transaction { syncState().downloadedSurveyIds }
    val available = mutableSetOf<String>()
    for (chunk in downloaded.chunked(MAX_BATCH_SURVEY_IDS)) {
      val results =
        grpcClient
          .batchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = chunk))
          .results
      val storedVersions = store.transaction {
        chunk.associateWith { sid -> surveyBase(sid)?.let(Rebaser::recordVersionOf) }
      }
      val revisionsById =
        results
          .mapNotNull { it.overview }
          .filter { !it.isTrashedOrDeleted() }
          .filter { overview ->
            val stored = storedVersions[overview.survey_id]
            stored == null || overview.record_version > stored
          }
          .associate { overview -> overview.survey_id to fetchSurveyRevisions(overview) }
      store.transaction {
        val rebaser = Rebaser(this)
        for (result in results) {
          val sid = result.survey_id
          // `NOT_FOUND` or `PERMISSION_DENIED`: keep the local copy and pending uploads.
          val overview = result.overview ?: continue
          if (overview.isTrashedOrDeleted()) {
            // Pending uploads keep draining (the server accepts them, so field data survives a
            // restore); the local copy is removed afterwards.
            updateSyncState { it.copy(downloadedSurveyIds = it.downloadedSurveyIds - sid) }
            updateSurvey(sid) { it.copy(isDownloaded = false) }
            continue
          }
          available += sid
          val revisions = revisionsById[sid]
          val saved = revisions?.saved?.survey
          if (revisions != null && saved != null) {
            rebaser.acceptSurvey(
              sid,
              SurveyBase(
                saved = saved,
                workingCopy = revisions.workingCopy,
                acl = revisions.saved.acl,
              ),
              overview,
              replace = true,
            )
          } else if (surveyBase(sid) != null) {
            rebaser.rebaseSurvey(sid, overview)
          }
        }
      }
    }
    return available
  }

  /**
   * Drops the local copy of every survey that isn't downloaded and has no pending mutations: its
   * definition, data, and sync tokens. Surveys the survey list no longer includes are removed.
   */
  private suspend fun removeUndownloadedSurveys(store: LocalStore) {
    val listed = catalogIds ?: return
    store.transaction {
      val downloaded = syncState().downloadedSurveyIds
      val pending = surveysWithPendingMutations()
      for (sid in surveys().map { it.id }) {
        if (sid in downloaded || sid in pending) continue
        if (sid !in listed) {
          deleteSurvey(sid)
          forgetDataTokens(sid)
          continue
        }
        val tokens = syncState().syncTokens
        val hasLocalCopy =
          surveyBase(sid) != null ||
            entityBases(sid).isNotEmpty() ||
            submissionBases(sid).isNotEmpty() ||
            sid in tokens.entitiesBySurvey ||
            sid in tokens.submissionsBySurvey
        if (!hasLocalCopy) continue
        putSurveyBase(sid, null)
        entityBases(sid).keys.forEach { putEntityBase(sid, it, null) }
        submissionBases(sid).keys.forEach { putSubmissionBase(sid, it, null) }
        putEntities(sid, emptyList())
        putStandaloneSubmissions(sid, emptyList())
        putSubmissionGeometries(sid, emptyList())
        forgetDataTokens(sid)
        updateSurvey(sid) { it.copy(isDownloaded = false) }
      }
    }
  }

  /** IDs of the surveys whose definition or data has mutations in the outbox (in any state). */
  private fun LocalStoreTransaction.surveysWithPendingMutations(): Set<String> =
    outbox()
      .mapNotNull {
        when (val target = it.target) {
          is MutationTarget.Survey -> target.surveyId
          is MutationTarget.Data -> target.surveyId
          else -> null
        }
      }
      .toSet()

  /** The survey list entry of a survey that isn't downloaded, from its overview alone. */
  private fun LocalStoreTransaction.catalogPreview(overview: SurveyOverview): SurveyPreviewItem =
    GrpcProtoMapper.surveyPreviewFromOverviewProto(
        overview = overview,
        mapLayers = mapLayers(overview.survey_id),
      )
      .copy(isDownloaded = false)

  private fun SurveyOverview.isTrashedOrDeleted(): Boolean =
    trash_time != null || state == SurveyState.DELETED

  /**
   * Syncs one scope: in delta mode from its stored token, one transaction per page; or, when it has
   * no token or the server rejects it with `FAILED_PRECONDITION`, with a full sync.
   */
  private suspend fun <T> syncScope(
    store: LocalStore,
    token: (SyncTokens) -> String,
    withToken: (SyncTokens, String) -> SyncTokens,
    fetch: suspend (syncToken: String, pageToken: String) -> ListPage<T>,
    applyDelta: PageApplier<T>,
    applyFull: PageApplier<T>,
  ) {
    val stored = token(syncTokens)
    if (stored.isNotEmpty()) {
      try {
        var pageToken = ""
        do {
          val page = fetch(stored, pageToken)
          // In delta mode, `next_page_token` is itself a sync token for the scope, so storing it
          // lets an interrupted sync resume after the last applied page.
          commit(store, withToken, page.nextPageToken.ifEmpty { page.nextSyncToken }) {
            applyDelta(page.records)
          }
          pageToken = page.nextPageToken
        } while (pageToken.isNotEmpty())
        return
      } catch (e: GrpcWebException) {
        if (e.status != GrpcWebTransport.FAILED_PRECONDITION) throw e
        // The server can't serve changes from the token any more: discard it and resync.
        commit(store, withToken, token = "") { {} }
      }
    }
    val full = fullList { fetch("", it) }
    commit(store, withToken, full.syncToken) { applyFull(full.records) }
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
        .associate { overview -> overview.survey_id to fetchSurveyRevisions(overview) }
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

  /** Drops the entity and submission sync tokens of a survey that no longer exists locally. */
  private fun LocalStoreTransaction.forgetDataTokens(surveyId: String) {
    updateSyncState {
      it.copy(
        syncTokens =
          it.syncTokens.copy(
            entitiesBySurvey = it.syncTokens.entitiesBySurvey - surveyId,
            submissionsBySurvey = it.syncTokens.submissionsBySurvey - surveyId,
          )
      )
    }
  }

  private fun Map<String, String>.withToken(key: String, token: String): Map<String, String> =
    if (token.isEmpty()) this - key else this + (key to token)

  /** Reads every page of a query-mode call. */
  private suspend fun <T> fullList(fetch: suspend (pageToken: String) -> ListPage<T>): FullList<T> {
    val records = mutableListOf<T>()
    var pageToken = ""
    while (true) {
      val page = fetch(pageToken)
      records += page.records
      if (page.nextPageToken.isEmpty()) return FullList(records, page.nextSyncToken)
      pageToken = page.nextPageToken
    }
  }

  // Page fetchers: one `List*` call each, in query mode when `syncToken` is empty.

  private suspend fun organizationsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listOrganizations(ListOrganizationsRequest(sync_token = syncToken, page_token = pageToken))
      .let { ListPage<Organization>(it.organizations, it.next_page_token, it.next_sync_token) }

  private suspend fun librariesPage(syncToken: String, pageToken: String) =
    grpcClient
      .listLibraries(ListLibrariesRequest(sync_token = syncToken, page_token = pageToken))
      .let {
        ListPage<OrganizationLibraryRecord>(it.libraries, it.next_page_token, it.next_sync_token)
      }

  private suspend fun overviewsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listSurveyOverviews(
        ListSurveyOverviewsRequest(sync_token = syncToken, page_token = pageToken)
      )
      .let { ListPage<SurveyOverview>(it.overviews, it.next_page_token, it.next_sync_token) }

  private suspend fun entitiesPage(surveyId: String, syncToken: String, pageToken: String) =
    grpcClient
      .listEntities(
        ListEntitiesRequest(survey_id = surveyId, sync_token = syncToken, page_token = pageToken)
      )
      .let { ListPage<EntityRecord>(it.entities, it.next_page_token, it.next_sync_token) }

  private suspend fun submissionsPage(surveyId: String, syncToken: String, pageToken: String) =
    grpcClient
      .listSubmissions(
        ListSubmissionsRequest(survey_id = surveyId, sync_token = syncToken, page_token = pageToken)
      )
      .let { ListPage<SubmissionRecord>(it.submissions, it.next_page_token, it.next_sync_token) }

  private suspend fun impactEventsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listImpactEvents(ListImpactEventsRequest(sync_token = syncToken, page_token = pageToken))
      .let { ListPage<ImpactEventProto>(it.events, it.next_page_token, it.next_sync_token) }

  private suspend fun mutationLogsPage(syncToken: String, pageToken: String) =
    grpcClient
      .listMutationLogs(ListMutationLogsRequest(sync_token = syncToken, page_token = pageToken))
      .let { ListPage<MutationLogRecord>(it.mutation_logs, it.next_page_token, it.next_sync_token) }

  /**
   * Fetches the latest saved revision of a survey and, only when [overview] reports unsaved edits
   * (`has_working_copy`), the organizer's working copy (when the caller is allowed to read it).
   */
  private suspend fun fetchSurveyRevisions(overview: SurveyOverview): SurveyRevisions {
    val surveyId = overview.survey_id
    return SurveyRevisions(
      saved = grpcClient.getSurvey(GetSurveyRequest(survey_id = surveyId)),
      workingCopy =
        if (overview.has_working_copy) {
          try {
            grpcClient.getSurvey(GetSurveyRequest(survey_id = surveyId, working_copy = true)).survey
          } catch (e: GrpcWebException) {
            if (e.status != GrpcWebTransport.PERMISSION_DENIED) throw e
            null
          }
        } else {
          null
        },
    )
  }

  /** Uploads offline impact events via `RecordImpactEvents`. */
  suspend fun pushImpactEvents(events: List<org.groundplatform.v2.app.domain.model.ImpactEvent>) {
    if (events.isEmpty()) return
    grpcClient.recordImpactEvents(
      RecordImpactEventsRequest(events = events.map { GrpcProtoMapper.impactEventToProto(it) })
    )
  }

  private fun LocalStoreTransaction.applySnapshotToTransaction(
    orgDeltas: List<Organization>,
    libDeltas: List<OrganizationLibraryRecord>,
    fullSurveys: List<SurveyRevisions>,
    entityDeltas: List<EntityRecord>,
    submissionDeltas: List<SubmissionRecord>,
    places: List<org.groundplatform.v2.app.domain.model.SurveyPlaceItem>,
    mutationLogs: List<MutationLogItem>,
    defaultActiveSurveyId: String,
    seedVersion: Int,
  ) {
    orgDeltas.forEach {
      putOrganizationBase(it.organization_id, it)
      upsertOrganization(GrpcProtoMapper.organizationFromProto(it))
    }
    libDeltas.forEach {
      putLibraryBase(it.organization_id, it)
      putLibrary(GrpcProtoMapper.libraryFromProto(it))
    }
    entityDeltas.forEach { putEntityBase(it.survey_id, it.entity_id, it) }
    submissionDeltas.forEach { putSubmissionBase(it.survey_id, it.submission_id, it) }

    val entitiesBySurvey = entityDeltas.groupBy { it.survey_id }
    val submissionsBySurvey = submissionDeltas.groupBy { it.survey_id }
    var tilePackages = emptyList<org.groundplatform.v2.app.domain.model.OfflineTilePackageItem>()

    for (fullSurvey in fullSurveys) {
      val surveyDef = fullSurvey.saved.survey ?: continue
      val surveyId = surveyDef.survey_id
      putSurveyBase(
        surveyId,
        SurveyBase(
          saved = surveyDef,
          workingCopy = fullSurvey.workingCopy,
          acl = fullSurvey.saved.acl,
        ),
      )
      val forms = GrpcProtoMapper.formsFromProto(surveyDef)
      val mapLayers = GrpcProtoMapper.mapLayersFromProto(surveyDef)
      val config = GrpcProtoMapper.surveyConfigFromProto(surveyDef)
      val surveySubs = submissionsBySurvey[surveyId].orEmpty()
      val domainSubs = surveySubs.map(GrpcProtoMapper::submissionFromProto)
      val linkedByEntityId = domainSubs.filter { it.entityId.isNotBlank() }.groupBy { it.entityId }
      val standaloneSubs = domainSubs.filter { it.entityId.isBlank() }
      val geometries = surveySubs.flatMap { sub ->
        sub.geometry_polygons.map(GrpcProtoMapper::geometryPolygonFromProto)
      }
      val entities =
        entitiesBySurvey[surveyId].orEmpty().map { entProto ->
          GrpcProtoMapper.entityFromProto(
            proto = entProto,
            submissions = linkedByEntityId[entProto.entity_id].orEmpty(),
          )
        }

      val preview =
        GrpcProtoMapper.surveyPreviewFromProto(
          surveyDef = surveyDef,
          acl = fullSurvey.saved.acl,
          mapLayers = mapLayers,
          entities = entities,
        )
      upsertSurvey(preview)
      putForms(surveyId, forms)
      putMapLayers(surveyId, mapLayers)
      putEntities(surveyId, entities)
      putStandaloneSubmissions(surveyId, standaloneSubs)
      putSubmissionGeometries(surveyId, geometries)
      putSurveyConfig(surveyId, config)

      val pkgs = GrpcProtoMapper.offlineTilePackagesFromProto(surveyDef)
      if (pkgs.isNotEmpty()) {
        tilePackages = pkgs
      }

      if (fullSurvey.workingCopy != null) {
        putSurveyEditorDraft(
          surveyId,
          GrpcProtoMapper.surveyEditorDraftFromProto(
            surveyDef = fullSurvey.workingCopy,
            acl = fullSurvey.saved.acl,
            surveyPreview = preview,
            forms = forms,
            config = config,
            mapLayers = mapLayers,
            entities = entities,
          ),
        )
      }
    }

    putMutations(mutationLogs)
    putPlaces(places)
    putOfflineTilePackages(tilePackages)
    putPreferences(
      StoredPreferences(
        activeSurveyId = defaultActiveSurveyId,
        userSettings =
          UserSettings(
            language = "en",
            measurementUnits = MeasurementUnitSystem.METRIC,
            shouldUploadPhotosOnWifiOnly = false,
          ),
        uploadedMediaCacheSizeLabel = "38.4 MB",
        uploadedMediaFileCount = 27,
        seedVersion = seedVersion,
      )
    )
  }

  private companion object {
    /** Largest number of IDs the service accepts per `BatchGetSurveyOverviews` request. */
    const val MAX_BATCH_SURVEY_IDS = 100

    /** Statuses of a survey the caller can no longer read (or that was purged). */
    val UNAVAILABLE_SURVEY_STATUSES =
      setOf(GrpcWebTransport.PERMISSION_DENIED, GrpcWebTransport.NOT_FOUND)
  }
}
