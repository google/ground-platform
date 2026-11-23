/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.ClientSyncState
import org.openforis.collectone.v2.app.client.storage.SyncStateFile
import org.openforis.collectone.v2.app.data.datasource.local.persistence.ClientSyncStateCodec
import org.openforis.collectone.v2.app.data.datasource.local.persistence.PersistentLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.seed.SampleDataSeeder

private val log = Logger.withTag("SyncStatePersistence")

/**
 * Keeps the sync engine's state (outbox, server bases, client identity, sync tokens) on the device,
 * so edits made offline survive restarts and the app opens offline with the last synced data
 * (`docs/technical/client/local-data-store.md`).
 *
 * [restore] loads the stored state on launch and rebuilds the local copy the UI shows by applying
 * pending mutations on top of the bases. [start] then stores every later change in the transaction
 * that makes it. State saved by older versions in a [SyncStateFile] ([legacyFile]) is moved into an
 * empty local database on the first [restore], and the file is deleted.
 */
class SyncStatePersistence(
  private val store: PersistentLocalStore,
  private val legacyFile: SyncStateFile? = null,
) {
  /**
   * Loads the state stored for [account] into the store and rebuilds the local copy from it.
   * Returns false when nothing usable was stored for [account], so the caller downloads everything
   * instead. [account] is the signed-in account's email, or blank for sample and development
   * accounts.
   */
  suspend fun restore(account: String = ""): Boolean {
    migrateLegacyFile()
    val saved = loadStateOf(account) ?: return false
    if (
      saved.survey_bases.isEmpty() &&
        saved.outbox.isEmpty() &&
        saved.downloaded_survey_ids.isEmpty()
    ) {
      return false
    }
    store.transaction {
      ClientSyncStateCodec.restore(this, saved)
      val cleanedOutbox = outbox().filterNot(::isSpuriousEntityCountSurveyMutation)
      if (cleanedOutbox.size != outbox().size) {
        putOutbox(cleanedOutbox)
      }
      rebaseRestoredState(saved)
      pruneOrphanedOutboxEntries()
      // The restored data replaces the initial download.
      updatePreferences { prefs ->
        prefs.copy(
          seedVersion = SampleDataSeeder.SEED_VERSION,
          activeSurveyId =
            prefs.activeSurveyId.takeIf { id -> surveys().any { it.id == id } }
              ?: surveys().firstOrNull()?.id.orEmpty(),
        )
      }
    }
    return true
  }

  private fun LocalStoreTransaction.rebaseRestoredState(saved: ClientSyncState) {
    val rebaser = Rebaser(this)
    val pending = outbox()
    val organizationIds =
      (saved.organization_bases.map { it.organization_id } +
          pending.mapNotNull { (it.target as? MutationTarget.Organization)?.organizationId })
        .distinct()
    organizationIds.forEach(rebaser::rebaseOrganization)
    val libraryIds =
      (saved.library_bases.map { it.organization_id } +
          pending.mapNotNull { (it.target as? MutationTarget.Library)?.organizationId })
        .distinct()
    libraryIds.forEach(rebaser::rebaseLibrary)
    val dataSurveyIds =
      (saved.entity_bases.map { it.survey_id } +
          saved.submission_bases.map { it.survey_id } +
          pending.mapNotNull { (it.target as? MutationTarget.Data)?.surveyId })
        .distinct()
    for (surveyId in dataSurveyIds) {
      val target = MutationTarget.Data(surveyId)
      val forSurvey = pending.filter { it.target == target }
      rebaser.rebaseData(
        surveyId,
        entityBases(surveyId).keys +
          forSurvey.mapNotNull { m ->
            m.recordKey.removePrefix("entity/").takeIf { m.recordKey.startsWith("entity/") }
          },
        submissionBases(surveyId).keys +
          forSurvey.mapNotNull { m ->
            m.recordKey.removePrefix("submission/").takeIf {
              m.recordKey.startsWith("submission/")
            }
          },
      )
    }
    val surveyIds =
      (saved.survey_bases.map { it.survey_id } +
          pending.mapNotNull { (it.target as? MutationTarget.Survey)?.surveyId })
        .distinct()
    surveyIds.forEach { rebaser.rebaseSurvey(it) }
  }

  private fun LocalStoreTransaction.pruneOrphanedOutboxEntries() {
    val restoredSurveyIds = (surveys().map { it.id } + syncState().downloadedSurveyIds).toSet()
    val prunedOutbox =
      outbox().filter { m ->
        when (val target = m.target) {
          is MutationTarget.Data -> {
            val data = (m.payload as? MutationPayload.Data)?.mutation
            target.surveyId in restoredSurveyIds &&
              (data?.entity_mutation != null || data?.submission_mutation != null)
          }
          is MutationTarget.Survey -> target.surveyId in restoredSurveyIds
          else -> true
        }
      }
    if (prunedOutbox.size != outbox().size) putOutbox(prunedOutbox)
  }

  /**
   * Stores the current sync state as [account]'s, and from then on every change in the transaction
   * making it.
   */
  suspend fun start(account: String = "") {
    store.startWritingThrough(account)
  }

  /**
   * The state stored for [account], or `null` when it is unreadable or another account's (which
   * [start] replaces when it stores this one's).
   */
  private suspend fun loadStateOf(account: String): ClientSyncState? =
    try {
      if (store.storedAccount() == account) store.loadSyncState() else null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.w { "Ignoring unreadable sync state: $e" }
      null
    }

  private suspend fun migrateLegacyFile() {
    val file = legacyFile ?: return
    val bytes = file.read() ?: return
    val saved =
      try {
        ClientSyncState.ADAPTER.decode(bytes)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.w { "Ignoring unreadable sync state file: $e" }
        null
      }
    // A database that already holds state is newer than the file (e.g. the file's deletion failed).
    if (saved != null && store.loadSyncState() == ClientSyncState()) store.replaceSyncState(saved)
    file.delete()
  }
}

private fun isSpuriousEntityCountSurveyMutation(mutation: PendingMutation): Boolean {
  if (mutation.state != OutboxState.REJECTED) return false
  val sm = (mutation.payload as? MutationPayload.Survey)?.mutation ?: return false
  val up = sm.upsert
  val del = sm.delete
  if (up == null && del == null) return false
  val emptyMutation = SurveyMutation(upsert = up, delete = del)
  if (sm != emptyMutation) return false
  val emptyUp = SurveyDef(entity_count = up?.entity_count ?: 0)
  val emptyDel = SurveyDef(entity_count = del?.entity_count ?: 0)
  return (up == null || up == emptyUp) && (del == null || del == emptyDel)
}
