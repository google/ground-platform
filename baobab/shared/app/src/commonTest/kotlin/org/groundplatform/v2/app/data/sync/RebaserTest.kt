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
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.survey.Organization as OrganizationProto
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.domain.model.SyncStatus

/**
 * Rebase on remote changes (`docs/technical/sync-engine.md`): remote copies replace the base, and
 * pending mutations are re-applied on top of them.
 */
class RebaserTest {
  private val store = InMemoryLocalStore()
  private val recorder = OutboxRecorder(clock = { ofEpochSecond(1_800_000_000L, 0L) })
  private val data = MutationTarget.Data(SURVEY)

  private fun entity(label: String, seconds: Long, vararg props: Pair<String, String>) =
    EntityRecord(
      entity_id = ENTITY,
      survey_id = SURVEY,
      label = label,
      revision = seconds,
      properties = props.associate { (k, v) -> k to TypedValue(string_value = v) },
      commit_timestamp = ofEpochSecond(seconds, 0L),
    )

  private fun recordLabel(label: String) = runNow {
    store.transaction {
      recorder.record(
        this,
        data,
        Rebaser.entityKey(ENTITY),
        MutationPayload.Data(
          DataMutation(
            entity_mutation =
              EntityMutation(
                entity_id = ENTITY,
                update =
                  EntityPatch(
                    patches = listOf(EntityFieldPatch(key = "label", label_value = label))
                  ),
              )
          )
        ),
      )
    }
  }

  private fun accept(record: EntityRecord) = runNow {
    store.transaction { Rebaser(this).acceptData(SURVEY, listOf(record), emptyList()) }
  }

  private fun view() = runNow { store.transaction { entity(SURVEY, ENTITY) } }

  @Test
  fun remoteChangeBeforeSend_updatesBase_thenReappliesPendingMutation() {
    accept(entity("Server", 1_000))
    recordLabel("Mine")

    accept(entity("Theirs", 1_001, "crop" to "coffee"))

    val local = view()!!
    assertEquals("Mine", local.label)
    assertEquals(mapOf("crop" to "coffee"), local.properties)
    assertEquals(SyncStatus.UPLOADING, local.syncStatus)
    assertEquals("Theirs", runNow { store.transaction { entityBase(SURVEY, ENTITY) } }!!.label)
  }

  @Test
  fun pendingPatchThatMatchesTheNewBase_isDropped() {
    accept(entity("Server", 1_000))
    recordLabel("Agreed")

    accept(entity("Agreed", 1_001))

    assertEquals(emptyList(), runNow { store.transaction { outbox() } })
    assertEquals(SyncStatus.SYNCED, view()!!.syncStatus)
  }

  @Test
  fun olderRemoteCopy_doesNotReplaceANewerBase() {
    accept(entity("New", 2_000))

    accept(entity("Old", 1_000))

    assertEquals("New", view()!!.label)
  }

  @Test
  fun remoteDelete_rejectsPendingMutations_andRemovesTheRecord() {
    accept(entity("Server", 1_000))
    recordLabel("Mine")

    accept(entity("Server", 1_001).copy(is_deleted = true))

    assertNull(view())
    val pending = runNow { store.transaction { outbox() } }.single()
    assertEquals(OutboxState.REJECTED, pending.state)
    assertEquals(Rebaser.DELETED_ELSEWHERE, pending.lastError)
  }

  @Test
  fun organization_rebasesPendingRename_onRemoteChange() {
    val base =
      OrganizationProto(
        organization_id = "o1",
        name = "Old",
        description = "D1",
        commit_timestamp = ofEpochSecond(1_000L, 0L),
      )
    runNow { store.transaction { Rebaser(this).acceptOrganization(base) } }
    runNow {
      store.transaction {
        recorder.record(
          this,
          MutationTarget.Organization("o1"),
          Rebaser.organizationKey("o1"),
          MutationPayload.Organization(
            OrganizationMutation(
              update_field = OrganizationFieldPatch(key = "name", string_value = "Renamed")
            )
          ),
        )
        Rebaser(this).rebaseOrganization("o1")
      }
    }

    runNow {
      store.transaction {
        Rebaser(this)
          .acceptOrganization(
            base.copy(description = "D2", commit_timestamp = ofEpochSecond(1_001L, 0L))
          )
      }
    }

    val org = runNow { store.transaction { organization("o1") } }!!
    assertEquals("Renamed", org.name)
    assertEquals("D2", org.description)
  }

  @Test
  fun surveyFold_appliesWorkingCopyPatches_andSavePromotesThem() {
    val saved = SurveyDef(survey_id = SURVEY, title = "Saved", saved_revision_number = 1)
    var seq = 0L
    fun pending(workingCopy: Boolean, mutation: SurveyMutation) =
      org.groundplatform.v2.app.data.datasource.local.store.PendingMutation(
        mutationId = "m${++seq}",
        clientSeq = seq,
        target = MutationTarget.Survey(SURVEY, workingCopy),
        recordKey = Rebaser.surveyKey(SURVEY),
        payload = MutationPayload.Survey(mutation),
      )
    val edit =
      pending(
        true,
        SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = "Draft")),
      )

    val editing = RecordFolds.survey(SurveyBase(saved = saved), SURVEY, listOf(edit)).value!!
    assertEquals("Saved", editing.saved?.title)
    assertEquals("Draft", editing.workingCopy?.title)

    val published =
      RecordFolds.survey(
          SurveyBase(saved = saved),
          SURVEY,
          listOf(edit, pending(false, SurveyMutation(save_working_copy = true))),
        )
        .value!!
    assertEquals("Draft", published.saved?.title)
    assertEquals(2L, published.saved?.saved_revision_number)
    assertNull(published.workingCopy)
  }

  private companion object {
    const val SURVEY = "s1"
    const val ENTITY = "e1"
  }
}
