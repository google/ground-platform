/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.data.sync

import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget

/**
 * Autosaves the survey editor's in-progress drafts to the server's working copy
 * (`docs/technical/sync-engine.md`, "Survey editor autosave"): each call diffs the draft against
 * the last autosaved one and queues one working-copy patch per changed key, debounced so they're
 * sent once the organizer pauses. Queued patches to the same key coalesce, so typing produces one
 * patch per field.
 */
class WorkingCopyRecorder(
  /**
   * The underlying store (not a [RecordingLocalStore]), so resetting the editor draft isn't
   * recorded as an edit. Calls made inside an open transaction on it join that transaction.
   */
  private val store: LocalStore,
  private val recorder: OutboxRecorder = OutboxRecorder(),
  private val debounceMillis: Long = DEFAULT_DEBOUNCE_MS,
) {
  /** The last autosaved working copy per survey, as mapped locally. */
  private val lastAutosaved = mutableMapOf<String, SurveyDef>()

  /**
   * Queues working-copy patches turning the last autosaved working copy of [surveyId] (or, before
   * the first autosave, [baseline]: what the editor opened) into [workingCopy].
   */
  suspend fun autosave(surveyId: String, workingCopy: SurveyDef, baseline: SurveyDef?) =
    store.transaction {
      autosave(this, surveyId, workingCopy, baseline)
    }

  /** See [discard]. */
  suspend fun discard(surveyId: String) = store.transaction { discard(this, surveyId) }

  private fun autosave(
    tx: LocalStoreTransaction,
    surveyId: String,
    after: SurveyDef,
    baseline: SurveyDef?,
  ) {
    val before = lastAutosaved[surveyId] ?: baseline
    if (before != null) {
      MutationDiffs.survey(before, after).forEach { patch ->
        recorder.record(
          tx,
          MutationTarget.Survey(surveyId, workingCopy = true),
          Rebaser.surveyKey(surveyId),
          MutationPayload.Survey(SurveyMutation(update_field = patch)),
          debounceMillis = debounceMillis,
        )
      }
    }
    lastAutosaved[surveyId] = after
  }

  /**
   * Queues `discard_working_copy` when [surveyId] has an autosaved working copy (here or on the
   * server), dropping queued autosave patches first. Call it when edits are discarded, and after
   * publishing, so the server's working copy doesn't linger.
   */
  private fun discard(tx: LocalStoreTransaction, surveyId: String) {
    val autosavedHere = lastAutosaved.remove(surveyId) != null
    val target = MutationTarget.Survey(surveyId, workingCopy = true)
    val pending = tx.outbox().filter { it.target == target }
    val onServer = tx.surveyBase(surveyId)?.workingCopy != null
    if (!autosavedHere && pending.isEmpty() && !onServer) return
    recorder.record(
      tx,
      target,
      Rebaser.surveyKey(surveyId),
      MutationPayload.Survey(SurveyMutation(discard_working_copy = true)),
    )
    // The editor's stored draft follows the server's working copy (see `projectSurvey`); once
    // that's discarded, reset the draft to the saved revision with its pending changes.
    if (onServer) {
      val pendingForSurvey =
        tx.outbox().filter {
          (it.target as? MutationTarget.Survey)?.surveyId == surveyId
        }
      val effective = RecordFolds.survey(tx.surveyBase(surveyId), surveyId, pendingForSurvey).value
      val saved = effective?.saved
      if (saved != null) tx.projectSurvey(surveyId, effective.copy(workingCopy = saved), null)
    }
  }

  companion object {
    /** Quiet period before autosaved edits are sent. */
    const val DEFAULT_DEBOUNCE_MS = 1_500L
  }
}
