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

import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.sync.Rebaser

/**
 * The survey list and the downloaded set of a [GrpcSyncDataSource] with `downloadedSurveysOnly`
 * (mobile; `docs/technical/sync-engine.md`, "Survey Downloads & Working Copies").
 *
 * Owns the in-memory survey list state: the `ListSurveyOverviews` token ([catalogToken]), the IDs
 * that list last reported ([catalogIds]), and whether the list is on screen. That state is
 * deliberately not persisted: each launch, and each time the list opens, starts with a full query,
 * so a list the user isn't looking at costs no polling.
 *
 * Downloaded surveys are refreshed with `BatchGetSurveyOverviews` rather than the list, so
 * link-shared and public surveys the list never includes stay current too. Not thread-safe: the
 * owning data source runs one sync at a time.
 */
internal class DownloadedSurveyCatalog(
  private val grpcClient: CollectOneGrpcClient,
  private val pages: SyncPageFetcher,
) {
  /** In-memory `ListSurveyOverviews` token; empty means the next sync runs a full query. */
  private var catalogToken = ""

  /** IDs of the surveys `ListSurveyOverviews` lists; null until the first full query. */
  private var catalogIds: Set<String>? = null

  /** Whether the survey list is on screen; see [setListOpen]. */
  var isListOpen = false
    private set

  /**
   * Syncs catch up the list only while it's open; closing it discards the list's token, so the next
   * open starts with a fresh query.
   */
  fun setListOpen(open: Boolean) {
    isListOpen = open
    if (!open) catalogToken = ""
  }

  /** Forgets the list's token and IDs, as after sign-out or a token reset. */
  fun reset() {
    catalogToken = ""
    catalogIds = null
  }

  /** Records the list a seed's full `ListSurveyOverviews` query returned. */
  fun onSeeded(syncToken: String, listedIds: Set<String>) {
    catalogToken = syncToken
    catalogIds = listedIds
  }

  /**
   * Syncs the survey list with `ListSurveyOverviews` in delta mode from the in-memory token, or
   * with a full query when there is none or the server rejects it (`FAILED_PRECONDITION`). Surveys
   * that aren't downloaded are listed from their overviews; downloaded ones are left to
   * [refreshDownloadedSurveys].
   */
  suspend fun syncCatalog(store: LocalStore) {
    val stored = catalogToken
    if (stored.isNotEmpty() && catalogIds != null) {
      try {
        var pageToken = ""
        do {
          val page = pages.overviewsPage(stored, pageToken)
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
    val full = pages.fullList { pages.overviewsPage("", it) }
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
   * Refreshes the downloaded surveys (or [targetSurveyIds] when specified) with
   * `BatchGetSurveyOverviews`, fetching new or changed ones (a higher `record_version`) with
   * `GetSurvey`, and returns the IDs of those whose data can be synced.
   *
   * A trashed survey leaves the downloaded set; one the caller can no longer read (or that was
   * purged) is marked unavailable and keeps its local copy and pending uploads, but stops syncing
   * until it's available again. Revisions are fetched outside the transaction, so the store is
   * locked only while they're applied.
   */
  suspend fun refreshDownloadedSurveys(
    store: LocalStore,
    targetSurveyIds: Set<String>? = null,
  ): Set<String> {
    val downloaded = targetSurveyIds ?: store.transaction { syncState().downloadedSurveyIds }
    val available = mutableSetOf<String>()
    val trashed = mutableSetOf<String>()
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
          .associate { overview -> overview.survey_id to pages.fetchSurveyRevisions(overview) }
      store.transaction {
        val rebaser = Rebaser(this)
        for (result in results) {
          val sid = result.survey_id
          val overview = result.overview
          when {
            // `NOT_FOUND` or `PERMISSION_DENIED`: keep the local copy and pending uploads.
            overview == null -> updateSurvey(sid) { it.copy(isUnavailable = true) }
            overview.isTrashedOrDeleted() -> {
              // Pending uploads keep draining (the server accepts them, so field data survives a
              // restore); the local copy is removed afterwards.
              updateSyncState { it.copy(downloadedSurveyIds = it.downloadedSurveyIds - sid) }
              updateSurvey(sid) { it.copy(isDownloaded = false) }
              trashed += sid
            }
            else -> {
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
      }
    }
    // The survey list doesn't include trashed surveys either, whether or not it's open.
    catalogIds = catalogIds?.minus(trashed)
    return available
  }

  /**
   * Drops the local copy of every survey that isn't downloaded and has no pending mutations: its
   * definition, data, and sync tokens. Surveys the survey list's last query or delta didn't include
   * are removed.
   */
  suspend fun removeUndownloadedSurveys(store: LocalStore) {
    // Before the survey list's first query, keep the list entries and drop only local copies.
    val listed = catalogIds
    store.transaction {
      val downloaded = syncState().downloadedSurveyIds
      val pending = surveysWithPendingMutations()
      for (sid in surveys().map { it.id }) {
        if (sid in downloaded || sid in pending) continue
        if (listed != null && sid !in listed) {
          deleteSurvey(sid)
          forgetDataTokens(sid)
        } else if (hasLocalCopy(sid)) {
          dropLocalCopy(sid)
        }
      }
    }
  }

  /** Whether survey [sid] still has a base, data bases, or data sync tokens on the device. */
  private fun LocalStoreTransaction.hasLocalCopy(sid: String): Boolean {
    val tokens = syncState().syncTokens
    return surveyBase(sid) != null ||
      entityBases(sid).isNotEmpty() ||
      submissionBases(sid).isNotEmpty() ||
      sid in tokens.entitiesBySurvey ||
      sid in tokens.submissionsBySurvey
  }

  /**
   * Drops survey [sid]'s definition, data, and data tokens, keeping its survey list entry (marked
   * not downloaded) so the list can still show it.
   */
  private fun LocalStoreTransaction.dropLocalCopy(sid: String) {
    putSurveyBase(sid, null)
    entityBases(sid).keys.forEach { putEntityBase(sid, it, null) }
    submissionBases(sid).keys.forEach { putSubmissionBase(sid, it, null) }
    putEntities(sid, emptyList())
    putStandaloneSubmissions(sid, emptyList())
    putSubmissionGeometries(sid, emptyList())
    putMutations(mutations().filterNot { it.surveyId == sid })
    forgetDataTokens(sid)
    updateSurvey(sid) { it.copy(isDownloaded = false) }
  }

  private companion object {
    /** Largest number of IDs the service accepts per `BatchGetSurveyOverviews` request. */
    const val MAX_BATCH_SURVEY_IDS = 100
  }
}
