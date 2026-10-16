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

import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.SubmissionPatch
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation

/**
 * Coalesces a newly recorded mutation with the queued (not yet sent) mutations for the same record,
 * so the outbox holds at most one pending value per key path (see `docs/technical/sync-engine.md`,
 * "The Offline Flow"):
 * - A field patch replaces queued patches to the same key path and to key paths beneath it (for
 *   example `forms/f1` replaces queued `forms/f1/controls/c1`).
 * - A whole-record operation (an upsert or delete of an entity, submission, organization, or
 *   library, or a full survey snapshot) replaces every queued field patch for the record.
 * - Deleting an entity or submission that was created locally and never sent cancels both.
 *
 * Mutations that are in flight, retrying, or rejected are never changed: the server may already
 * have applied them. Patches to different key paths commute, so appending the new mutation after
 * the remaining queued ones preserves the result.
 */
object OutboxCoalescer {
  /** Returns [outbox] with [new] recorded: coalesced, appended, or cancelled. */
  fun coalesce(outbox: List<PendingMutation>, new: PendingMutation): List<PendingMutation> {
    val sameRecord = { m: PendingMutation ->
      m.target == new.target && m.recordKey == new.recordKey
    }
    val queued = { m: PendingMutation -> m.state == OutboxState.QUEUED && sameRecord(m) }

    if (isDataDelete(new.payload)) {
      val forRecord = outbox.filter(sameRecord)
      val createdLocally =
        forRecord.isNotEmpty() &&
          forRecord.all { it.state == OutboxState.QUEUED } &&
          isDataUpsert(forRecord.first().payload)
      if (createdLocally) return outbox.filterNot(sameRecord)
    }

    if (isWholeRecord(new.payload)) {
      return outbox.filterNot { queued(it) && isFieldPatch(it.payload) } + new
    }

    val newKeys = fieldKeys(new.payload)
    if (newKeys.isEmpty()) return outbox + new
    val coalesced = outbox.mapNotNull { existing ->
      if (!queued(existing)) existing else withoutKeys(existing, newKeys)
    }
    return coalesced + new
  }

  /** True when [old] is [new] or a key path beneath it. */
  internal fun supersedes(new: String, old: String): Boolean = old == new || old.startsWith("$new/")

  /**
   * [mutation] without the field patches that any of [newKeys] supersedes, or `null` when nothing
   * is left of it.
   */
  private fun withoutKeys(mutation: PendingMutation, newKeys: Set<String>): PendingMutation? {
    fun superseded(key: String) = newKeys.any { supersedes(it, key) }
    val payload = mutation.payload
    return when (payload) {
      is MutationPayload.Survey -> {
        val m = payload.mutation
        val field = m.update_field?.key
        val role = m.update_acl_role?.user_id?.let(::aclKey)
        if ((field != null && superseded(field)) || (role != null && superseded(role))) null
        else mutation
      }
      is MutationPayload.Organization ->
        mutation.takeUnless { payload.mutation.update_field?.key?.let(::superseded) == true }
      is MutationPayload.Library ->
        mutation.takeUnless { payload.mutation.update_field?.key?.let(::superseded) == true }
      is MutationPayload.Data -> {
        val m = payload.mutation
        val entityUpdate = m.entity_mutation?.update
        val submissionUpdate = m.submission_mutation?.update
        when {
          entityUpdate != null -> {
            val remaining = entityUpdate.patches.filterNot { superseded(it.key) }
            if (remaining.isEmpty()) null
            else
              mutation.copy(
                payload =
                  MutationPayload.Data(
                    m.copy(
                      entity_mutation =
                        m.entity_mutation!!.copy(update = EntityPatch(patches = remaining))
                    )
                  )
              )
          }
          submissionUpdate != null -> {
            val remaining = submissionUpdate.patches.filterNot { superseded(it.key) }
            if (remaining.isEmpty()) null
            else
              mutation.copy(
                payload =
                  MutationPayload.Data(
                    m.copy(
                      submission_mutation =
                        m.submission_mutation!!.copy(update = SubmissionPatch(patches = remaining))
                    )
                  )
              )
          }
          else -> mutation
        }
      }
    }
  }

  /** Key paths a field-patch mutation sets; empty for mutations that never coalesce. */
  internal fun fieldKeys(payload: MutationPayload): Set<String> =
    when (payload) {
      is MutationPayload.Survey ->
        setOfNotNull(
          payload.mutation.update_field?.key,
          payload.mutation.update_acl_role?.user_id?.let(::aclKey),
        )
      is MutationPayload.Organization -> setOfNotNull(payload.mutation.update_field?.key)
      is MutationPayload.Library -> setOfNotNull(payload.mutation.update_field?.key)
      is MutationPayload.Data ->
        payload.mutation.entity_mutation?.update?.patches?.mapTo(mutableSetOf()) { it.key }
          ?: payload.mutation.submission_mutation?.update?.patches?.mapTo(mutableSetOf()) {
            it.key
          }
          ?: emptySet()
    }

  private fun isFieldPatch(payload: MutationPayload): Boolean = fieldKeys(payload).isNotEmpty()

  private fun isWholeRecord(payload: MutationPayload): Boolean =
    when (payload) {
      is MutationPayload.Survey -> payload.mutation.upsert_survey != null
      is MutationPayload.Organization ->
        payload.mutation.upsert_organization != null || payload.mutation.delete_organization == true
      is MutationPayload.Library -> payload.mutation.upsert_library != null
      is MutationPayload.Data -> isDataUpsert(payload) || isDataDelete(payload)
    }

  private fun isDataUpsert(payload: MutationPayload): Boolean =
    payload is MutationPayload.Data &&
      (payload.mutation.entity_mutation?.upsert != null ||
        payload.mutation.submission_mutation?.upsert != null)

  private fun isDataDelete(payload: MutationPayload): Boolean =
    payload is MutationPayload.Data && payload.mutation.isDelete()

  private fun DataMutation.isDelete(): Boolean =
    entity_mutation?.delete == true || submission_mutation?.delete == true

  /** Pseudo key path for ACL role patches, so later patches for the same user coalesce. */
  private fun aclKey(userId: String): String = "acl/${userId.lowercase()}"
}
