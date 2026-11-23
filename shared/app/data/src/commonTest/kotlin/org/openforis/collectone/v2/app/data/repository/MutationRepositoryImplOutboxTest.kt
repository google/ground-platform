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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState

/** The `Uploads` state of logged changes while the sync engine tracks the outbox. */
class MutationRepositoryImplOutboxTest {
  private val store = seededStore()
  private val repository = MutationRepositoryImpl(store, trackOutbox = true)

  /** A pending change of the seed data whose record has no outbox entry yet. */
  private val logged: MutationLogItem = runNow {
    MutationRepositoryImpl(store).getMutations().first {
      it.isOutbox && it.submissionId == null && it.state != MutationSyncState.FAILED
    }
  }

  private fun outboxEntryFor(item: MutationLogItem, state: OutboxState) =
    PendingMutation(
      mutationId = "m-${item.id}",
      clientSeq = 1L,
      target = MutationTarget.Data(item.surveyId),
      recordKey = Rebaser.entityKey(item.entityId),
      payload =
        MutationPayload.Data(
          DataMutation(
            entity_mutation = EntityMutation(entity_id = item.entityId, upsert = EntityRecord())
          )
        ),
      state = state,
      lastError = DomainError.PermissionDenied,
      lastErrorDetail = "Only owners can edit this survey.",
    )

  private fun stateOf(item: MutationLogItem): MutationLogItem = runNow {
    repository.getMutations().first { it.id == item.id }
  }

  @Test
  fun rejectedChange_failsWithItsOutboxError() {
    runNow { store.transaction { putOutbox(listOf(outboxEntryFor(logged, OutboxState.REJECTED))) } }

    val failed = stateOf(logged)

    assertEquals(MutationSyncState.FAILED, failed.state)
    assertEquals(DomainError.PermissionDenied, failed.lastError)
    // The raw server message is kept for diagnostics only.
    assertEquals("Only owners can edit this survey.", failed.stateDetail)
  }

  @Test
  fun retryingChange_carriesItsOutboxError() {
    runNow { store.transaction { putOutbox(listOf(outboxEntryFor(logged, OutboxState.RETRYING))) } }

    assertEquals(DomainError.PermissionDenied, stateOf(logged).lastError)
  }

  @Test
  fun queuedChange_hasNoError() {
    runNow {
      store.transaction {
        putOutbox(listOf(outboxEntryFor(logged, OutboxState.QUEUED).copy(lastError = null)))
      }
    }

    val queued = stateOf(logged)

    assertEquals(MutationSyncState.QUEUED, queued.state)
    assertNull(queued.lastError)
  }

  @Test
  fun unmatchedOutboxDataMutations_areSynthesizedIntoLiveUploadItems_andOrphansAreExcluded() {
    val activeSurveyId = logged.surveyId
    runNow {
      store.transaction {
        putMutations(emptyList())
        putOutbox(
          listOf(
            PendingMutation(
              mutationId = "persisted-sub-1",
              clientSeq = 1L,
              target = MutationTarget.Data(activeSurveyId),
              recordKey = Rebaser.submissionKey("sub-offline-1"),
              payload =
                MutationPayload.Data(
                  DataMutation(
                    submission_mutation =
                      SubmissionMutation(
                        submission_id = "sub-offline-1",
                        form_id = "form-canopy",
                        upsert =
                          SubmissionRecord(
                            submission_id = "sub-offline-1",
                            form_title = "Canopy Audit",
                            entity_id = logged.entityId,
                            entity_label = "Plot NYR-108",
                            collector_name = "Gino",
                          ),
                      )
                  )
                ),
              state = OutboxState.IN_FLIGHT,
            ),
            // Orphaned entry for a deleted/unknown survey should be ignored.
            PendingMutation(
              mutationId = "orphaned-survey-mut",
              clientSeq = 2L,
              target = MutationTarget.Data("deleted-survey-id"),
              recordKey = Rebaser.entityKey("ent-orphan"),
              payload =
                MutationPayload.Data(
                  DataMutation(
                    entity_mutation =
                      EntityMutation(
                        entity_id = "ent-orphan",
                        upsert = EntityRecord(label = "Orphan"),
                      )
                  )
                ),
              state = OutboxState.QUEUED,
            ),
          )
        )
      }
    }

    val live = runNow { repository.getMutations() }
    assertEquals(1, live.size)
    val synthesized = live.single()
    assertEquals("persisted-sub-1", synthesized.id)
    assertEquals(activeSurveyId, synthesized.surveyId)
    assertEquals(MutationOperationKind.CREATE_SUBMISSION, synthesized.operationKind)
    assertEquals("Canopy Audit", synthesized.title)
    assertEquals("Plot NYR-108", synthesized.targetLabel)
    assertEquals(MutationSyncState.UPLOADING, synthesized.state)

    // Once the outbox drains, the synthesized item disappears cleanly.
    runNow { store.transaction { putOutbox(emptyList()) } }
    assertTrue(runNow { repository.getMutations() }.isEmpty())
  }

  @Test
  fun entityOperationWithLinkedSubmissionId_tracksEntityOutboxKey() {
    val entityUpdate =
      MutationLogItem(
        id = "mut-ent-linked",
        surveyId = logged.surveyId,
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Marked parcel as Completed",
        targetLabel = logged.targetLabel,
        entityId = logged.entityId,
        submissionId = "sub-already-uploaded",
        actorName = "Gino",
        state = MutationSyncState.QUEUED,
        stateDetail = "Waiting to upload",
        operationTimestamp = "2026-10-01 12:00:00 UTC",
      )
    runNow {
      store.transaction {
        putMutations(listOf(entityUpdate))
        putOutbox(listOf(outboxEntryFor(entityUpdate, OutboxState.REJECTED)))
      }
    }

    val tracked = stateOf(entityUpdate)
    assertEquals(MutationSyncState.FAILED, tracked.state)
    assertEquals(DomainError.PermissionDenied, tracked.lastError)
  }
}
