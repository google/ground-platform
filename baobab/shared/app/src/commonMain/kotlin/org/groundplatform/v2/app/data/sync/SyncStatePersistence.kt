/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.app.data.sync

import groundplatform.v2.userdata.ClientSyncState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import org.groundplatform.v2.app.client.storage.SyncStateFile
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.seed.SampleDataSeeder

/**
 * Keeps the sync engine's state (outbox, server bases, client identity, sync tokens) on the device,
 * so edits made offline survive restarts and the app opens offline with the last synced data
 * (`docs/technical/client/local-data-store.md`).
 *
 * [restore] loads the saved state on launch and rebuilds the local copy the UI shows by applying
 * pending mutations on top of the bases. [start] saves the state, debounced, whenever the outbox
 * changes or a sync completes ([saveSoon]).
 */
class SyncStatePersistence(
  private val store: LocalStore,
  private val file: SyncStateFile,
  private val scope: CoroutineScope,
  private val debounceMillis: Long = 500L,
) {
  private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private var job: Job? = null

  /**
   * Loads the saved state into the store and rebuilds the local copy from it. Returns false when
   * nothing usable was saved, so the caller downloads everything instead.
   */
  suspend fun restore(): Boolean {
    val bytes = file.read() ?: return false
    val saved =
      try {
        ClientSyncState.ADAPTER.decode(bytes)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        println("Ignoring unreadable sync state: $e")
        return false
      }
    if (saved.survey_bases.isEmpty() && saved.outbox.isEmpty()) return false
    store.transaction {
      ClientSyncStateCodec.restore(this, saved)
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

  /** Starts saving the state whenever the outbox changes or [saveSoon] is called. */
  @OptIn(FlowPreview::class)
  fun start() {
    if (job != null) return
    job =
      merge(store.observeOutbox().drop(1).map {}, saveRequests)
        .debounce(debounceMillis)
        .onEach { saveNow() }
        .launchIn(scope)
  }

  /** Asks for a save soon, e.g. after a sync stored new bases or sync tokens. */
  fun saveSoon() {
    saveRequests.tryEmit(Unit)
  }

  /** Saves the current state now. */
  suspend fun saveNow() {
    val state = store.transaction { ClientSyncStateCodec.capture(this) }
    try {
      file.write(ClientSyncState.ADAPTER.encode(state))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      println("Couldn't save sync state: $e")
    }
  }
}
