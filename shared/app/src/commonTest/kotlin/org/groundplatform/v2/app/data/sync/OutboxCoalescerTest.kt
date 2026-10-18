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

import com.squareup.wire.ofEpochSecond
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.survey.AclRolePatch
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.datasource.local.store.runNow

class OutboxCoalescerTest {
  private val editor = MutationTarget.Survey("s1", workingCopy = true)
  private val data = MutationTarget.Data("s1")
  private var seq = 0L

  private fun pending(
    target: MutationTarget,
    recordKey: String,
    payload: MutationPayload,
    state: OutboxState = OutboxState.QUEUED,
  ) = PendingMutation("m${++seq}", seq, target, recordKey, payload, state = state)

  private fun surveyField(
    key: String,
    value: String? = null,
    state: OutboxState = OutboxState.QUEUED,
  ) =
    pending(
      editor,
      "survey/s1",
      MutationPayload.Survey(
        SurveyMutation(
          update_field =
            if (key.startsWith("forms/") && value != null) {
              SurveyFieldPatch(key = key, form_value = FormDef(form_id = key, title = value))
            } else {
              SurveyFieldPatch(key = key, string_value = value)
            }
        )
      ),
      state,
    )

  private fun entityUpdate(entityId: String, vararg patches: Pair<String, String>) =
    pending(
      data,
      "entity/$entityId",
      MutationPayload.Data(
        DataMutation(
          entity_mutation =
            EntityMutation(
              entity_id = entityId,
              update =
                EntityPatch(
                  patches =
                    patches.map { (k, v) ->
                      EntityFieldPatch(key = k, property_value = TypedValue(string_value = v))
                    }
                ),
            )
        )
      ),
    )

  private fun entityUpsert(entityId: String, state: OutboxState = OutboxState.QUEUED) =
    pending(
      data,
      "entity/$entityId",
      MutationPayload.Data(
        DataMutation(
          entity_mutation =
            EntityMutation(entity_id = entityId, upsert = EntityRecord(entity_id = entityId))
        )
      ),
      state,
    )

  private fun entityDelete(entityId: String) =
    pending(
      data,
      "entity/$entityId",
      MutationPayload.Data(
        DataMutation(entity_mutation = EntityMutation(entity_id = entityId, delete = true))
      ),
    )

  private fun List<PendingMutation>.enqueue(vararg new: PendingMutation) =
    new.fold(this) { outbox, m -> OutboxCoalescer.coalesce(outbox, m) }

  @Test
  fun repeatedPatchesToOneKey_leaveOnlyTheLatest() {
    val keystrokes = (1..20).map { surveyField("title", "T$it") }

    val outbox = emptyList<PendingMutation>().enqueue(*keystrokes.toTypedArray())

    assertEquals(listOf(keystrokes.last()), outbox)
  }

  @Test
  fun patchesToDifferentKeys_areKeptInOrder() {
    val title = surveyField("title", "T")
    val description = surveyField("description", "D")

    assertEquals(
      listOf(title, description),
      emptyList<PendingMutation>().enqueue(title, description),
    )
  }

  @Test
  fun removingACollectionItem_dropsQueuedPatchesBeneathIt() {
    val control = surveyField("forms/f1/controls/c1", "C")
    val other = surveyField("forms/f2", "F2")
    val removeForm = surveyField("forms/f1")

    val outbox = emptyList<PendingMutation>().enqueue(control, other, removeForm)

    assertEquals(listOf(other, removeForm), outbox)
  }

  @Test
  fun inFlightMutations_areNeverCoalesced() {
    val sent = surveyField("title", "Sent", state = OutboxState.IN_FLIGHT)
    val next = surveyField("title", "Next")

    assertEquals(listOf(sent, next), listOf(sent).enqueue(next))
  }

  @Test
  fun dataPatches_dropOnlySupersededFields() {
    val first = entityUpdate("e1", "properties/crop" to "maize", "properties/owner" to "A")
    val second = entityUpdate("e1", "properties/crop" to "coffee")

    val outbox = emptyList<PendingMutation>().enqueue(first, second)

    assertEquals(2, outbox.size)
    val remaining = (outbox[0].payload as MutationPayload.Data).mutation.entity_mutation!!.update!!
    assertEquals(listOf("properties/owner"), remaining.patches.map { it.key })
    assertEquals(second, outbox[1])
  }

  @Test
  fun patchesForOtherRecords_areUntouched() {
    val e1 = entityUpdate("e1", "label" to "A")
    val e2 = entityUpdate("e2", "label" to "B")

    assertEquals(listOf(e1, e2), emptyList<PendingMutation>().enqueue(e1, e2))
  }

  @Test
  fun deletingALocallyCreatedRecord_cancelsBoth() {
    val create = entityUpsert("e1")
    val edit = entityUpdate("e1", "label" to "A")
    val other = entityUpdate("e2", "label" to "B")

    val outbox = emptyList<PendingMutation>().enqueue(create, edit, other, entityDelete("e1"))

    assertEquals(listOf(other), outbox)
  }

  @Test
  fun deletingARecordWhoseCreateWasSent_queuesTheDelete() {
    val create = entityUpsert("e1", state = OutboxState.IN_FLIGHT)
    val edit = entityUpdate("e1", "label" to "A")
    val delete = entityDelete("e1")

    assertEquals(listOf(create, delete), listOf(create).enqueue(edit, delete))
  }

  @Test
  fun aclRolePatches_coalescePerUser() {
    fun role(user: String, role: Role) =
      pending(
        editor,
        "survey/s1",
        MutationPayload.Survey(
          SurveyMutation(update_acl_role = AclRolePatch(user_id = user, role = role))
        ),
      )
    val a1 = role("a@example.org", Role.VIEWER)
    val b = role("b@example.org", Role.VIEWER)
    val a2 = role("A@example.org", Role.DATA_COLLECTOR)

    assertEquals(listOf(b, a2), emptyList<PendingMutation>().enqueue(a1, b, a2))
  }

  @Test
  fun organizationDelete_replacesQueuedFieldPatches() {
    val target = MutationTarget.Organization("o1")
    val rename =
      pending(
        target,
        "organization/o1",
        MutationPayload.Organization(
          OrganizationMutation(
            update_field = OrganizationFieldPatch(key = "name", string_value = "N")
          )
        ),
      )
    val delete =
      pending(
        target,
        "organization/o1",
        MutationPayload.Organization(OrganizationMutation(delete_organization = true)),
      )

    assertEquals(listOf(delete), emptyList<PendingMutation>().enqueue(rename, delete))
  }
}

class OutboxRecorderTest {
  private val now = ofEpochSecond(1_800_000_000L, 0L)
  private var ids = 0
  private val recorder = OutboxRecorder(clock = { now }, newId = { "id${++ids}" })

  private fun title(value: String) =
    MutationPayload.Survey(
      SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = value))
    )

  @Test
  fun record_assignsClientIdOnce_andIncreasingClientSeq() = runNow {
    val store = InMemoryLocalStore()
    val target = MutationTarget.Survey("s1", workingCopy = true)
    store.transaction {
      recorder.record(this, target, "survey/s1", title("A"))
      recorder.record(
        this,
        target,
        "survey/s1",
        MutationPayload.Survey(
          SurveyMutation(update_field = SurveyFieldPatch(key = "description", string_value = "D"))
        ),
      )
    }

    store.transaction {
      assertEquals("id1", syncState().clientId)
      assertEquals(3L, syncState().nextClientSeq)
      assertEquals(listOf(1L, 2L), outbox().map { it.clientSeq })
      assertEquals(listOf("id2", "id3"), outbox().map { it.mutationId })
      assertEquals(now, outbox().first().clientTimestamp)
    }
  }

  @Test
  fun record_withDebounce_setsNotBefore() = runNow {
    val store = InMemoryLocalStore()
    val recorded =
      store.transaction {
        recorder.record(
          this,
          MutationTarget.Survey("s1", workingCopy = true),
          "survey/s1",
          title("A"),
          debounceMillis = 1_500L,
        )
      }!!

    assertEquals(ofEpochSecond(1_800_000_001L, 500_000_000L), recorded.notBefore)
  }

  @Test
  fun record_returnsNull_whenTheMutationCancelsOut() = runNow {
    val store = InMemoryLocalStore()
    val target = MutationTarget.Data("s1")
    val created = store.transaction {
      recorder.record(
        this,
        target,
        "entity/e1",
        MutationPayload.Data(
          DataMutation(
            entity_mutation =
              EntityMutation(entity_id = "e1", upsert = EntityRecord(entity_id = "e1"))
          )
        ),
      )
    }
    val deleted = store.transaction {
      recorder.record(
        this,
        target,
        "entity/e1",
        MutationPayload.Data(
          DataMutation(entity_mutation = EntityMutation(entity_id = "e1", delete = true))
        ),
      )
    }

    assertEquals(
      "e1",
      (created!!.payload as MutationPayload.Data).mutation.entity_mutation!!.entity_id,
    )
    assertNull(deleted)
    assertEquals(emptyList(), store.transaction { outbox() })
  }
}
