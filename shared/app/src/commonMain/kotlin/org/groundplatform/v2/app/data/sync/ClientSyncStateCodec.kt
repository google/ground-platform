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
import groundplatform.v2.userdata.PendingMutationRecord
import groundplatform.v2.userdata.SurveyBaseRecord
import groundplatform.v2.userdata.SyncTokensRecord
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase
import org.groundplatform.v2.app.data.datasource.local.store.SyncState
import org.groundplatform.v2.app.data.datasource.local.store.SyncTokens

/**
 * Converts the store's sync state (outbox, server bases, client identity, sync tokens) to and from
 * [ClientSyncState], the proto mobile clients keep on the device between launches.
 */
object ClientSyncStateCodec {
  /** The sync state held in [tx]. */
  fun capture(tx: LocalStoreTransaction): ClientSyncState {
    val state = tx.syncState()
    val surveyIds =
      (tx.surveys().map { it.id } +
          tx.outbox().mapNotNull {
            when (val t = it.target) {
              is MutationTarget.Survey -> t.surveyId
              is MutationTarget.Data -> t.surveyId
              else -> null
            }
          })
        .distinct()
    val organizationIds =
      (tx.organizations().map { it.id } +
          tx.libraries().keys +
          tx.outbox().mapNotNull {
            when (val t = it.target) {
              is MutationTarget.Organization -> t.organizationId
              is MutationTarget.Library -> t.organizationId
              else -> null
            }
          })
        .distinct()
    return ClientSyncState(
      outbox = tx.outbox().map(::toRecord),
      survey_bases =
        surveyIds.mapNotNull { id ->
          tx.surveyBase(id)?.let {
            SurveyBaseRecord(
              survey_id = id,
              saved = it.saved,
              working_copy = it.workingCopy,
              acl = it.acl,
            )
          }
        },
      entity_bases = surveyIds.flatMap { tx.entityBases(it).values },
      submission_bases = surveyIds.flatMap { tx.submissionBases(it).values },
      organization_bases = organizationIds.mapNotNull { tx.organizationBase(it) },
      library_bases = organizationIds.mapNotNull { tx.libraryBase(it) },
      client_id = state.clientId,
      next_client_seq = state.nextClientSeq,
      sync_tokens =
        state.syncTokens.let { t ->
          SyncTokensRecord(
            organizations = t.organizations,
            libraries = t.libraries,
            surveys = t.surveys,
            entities_by_survey = t.entitiesBySurvey,
            submissions_by_survey = t.submissionsBySurvey,
            impact_events = t.impactEvents,
          )
        },
    )
  }

  /** Writes [saved] into [tx]: the outbox, the bases, and the sync state. */
  fun restore(tx: LocalStoreTransaction, saved: ClientSyncState) {
    tx.putOutbox(saved.outbox.mapNotNull(::fromRecord))
    saved.survey_bases.forEach {
      tx.putSurveyBase(it.survey_id, SurveyBase(it.saved, it.working_copy, it.acl))
    }
    saved.entity_bases.forEach { tx.putEntityBase(it.survey_id, it.entity_id, it) }
    saved.submission_bases.forEach { tx.putSubmissionBase(it.survey_id, it.submission_id, it) }
    saved.organization_bases.forEach { tx.putOrganizationBase(it.organization_id, it) }
    saved.library_bases.forEach { tx.putLibraryBase(it.organization_id, it) }
    // State saved before sync tokens existed has none, so every scope gets a full sync.
    val t = saved.sync_tokens
    tx.putSyncState(
      SyncState(
        clientId = saved.client_id,
        nextClientSeq = saved.next_client_seq.coerceAtLeast(1L),
        syncTokens =
          SyncTokens(
            organizations = t?.organizations.orEmpty(),
            libraries = t?.libraries.orEmpty(),
            surveys = t?.surveys.orEmpty(),
            entitiesBySurvey = t?.entities_by_survey.orEmpty(),
            submissionsBySurvey = t?.submissions_by_survey.orEmpty(),
            impactEvents = t?.impact_events.orEmpty(),
          ),
      )
    )
  }

  internal fun toRecord(m: PendingMutation): PendingMutationRecord {
    val base =
      PendingMutationRecord(
        mutation_id = m.mutationId,
        client_seq = m.clientSeq,
        record_key = m.recordKey,
        base_revision = m.baseRevision,
        client_timestamp = m.clientTimestamp,
        state =
          when (m.state) {
            OutboxState.QUEUED -> PendingMutationRecord.State.QUEUED
            OutboxState.IN_FLIGHT -> PendingMutationRecord.State.IN_FLIGHT
            OutboxState.RETRYING -> PendingMutationRecord.State.RETRYING
            OutboxState.REJECTED -> PendingMutationRecord.State.REJECTED
          },
        attempts = m.attempts,
        last_error = m.lastError.orEmpty(),
        not_before = m.notBefore,
      )
    val target = m.target
    val payload = m.payload
    return when {
      target is MutationTarget.Survey && payload is MutationPayload.Survey ->
        base.copy(
          scope_id = target.surveyId,
          working_copy = target.workingCopy,
          survey_mutation = payload.mutation,
        )
      target is MutationTarget.Data && payload is MutationPayload.Data ->
        base.copy(scope_id = target.surveyId, data_mutation = payload.mutation)
      target is MutationTarget.Organization && payload is MutationPayload.Organization ->
        base.copy(scope_id = target.organizationId, organization_mutation = payload.mutation)
      target is MutationTarget.Library && payload is MutationPayload.Library ->
        base.copy(scope_id = target.organizationId, library_mutation = payload.mutation)
      else -> error("Mismatched target $target and payload $payload")
    }
  }

  /** The pending mutation [r] describes, or `null` when it has no payload. */
  internal fun fromRecord(r: PendingMutationRecord): PendingMutation? {
    val (target, payload) =
      r.survey_mutation?.let {
        MutationTarget.Survey(r.scope_id, r.working_copy) to MutationPayload.Survey(it)
      }
        ?: r.data_mutation?.let { MutationTarget.Data(r.scope_id) to MutationPayload.Data(it) }
        ?: r.organization_mutation?.let {
          MutationTarget.Organization(r.scope_id) to MutationPayload.Organization(it)
        }
        ?: r.library_mutation?.let {
          MutationTarget.Library(r.scope_id) to MutationPayload.Library(it)
        }
        ?: return null
    return PendingMutation(
      mutationId = r.mutation_id,
      clientSeq = r.client_seq,
      target = target,
      recordKey = r.record_key,
      payload = payload,
      baseRevision = r.base_revision,
      clientTimestamp = r.client_timestamp,
      state =
        when (r.state) {
          PendingMutationRecord.State.IN_FLIGHT -> OutboxState.IN_FLIGHT
          PendingMutationRecord.State.RETRYING -> OutboxState.RETRYING
          PendingMutationRecord.State.REJECTED -> OutboxState.REJECTED
          else -> OutboxState.QUEUED
        },
      attempts = r.attempts,
      lastError = r.last_error.ifBlank { null },
      notBefore = r.not_before,
    )
  }
}
