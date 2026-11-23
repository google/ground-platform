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
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.domain.error.DomainError

/** How [ClientSyncStateCodec] stores why an outbox entry's last send failed. */
class PendingMutationFailureCodecTest {
  private val rejectedEdit =
    PendingMutation(
      mutationId = "m1",
      clientSeq = 1L,
      target = MutationTarget.Survey("s1", workingCopy = true),
      recordKey = "survey/s1",
      payload = MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Edited"))),
      state = OutboxState.REJECTED,
    )

  private fun roundTrip(m: PendingMutation): PendingMutation =
    ClientSyncStateCodec.fromRecord(ClientSyncStateCodec.toRecord(m))!!

  @Test
  fun failure_keepsItsReasonAndDetail() {
    val failed =
      rejectedEdit.copy(
        lastError = DomainError.PermissionDenied,
        lastErrorDetail = "Only owners can edit this survey.",
      )

    assertEquals(failed, roundTrip(failed))
  }

  @Test
  fun unknownFailure_keepsItsDetail_butNotItsCause() {
    val failed =
      rejectedEdit.copy(
        lastError = DomainError.Unknown(IllegalStateException("boom")),
        lastErrorDetail = "boom",
      )

    val restored = roundTrip(failed)

    assertEquals(DomainError.Unknown(), restored.lastError)
    assertEquals("boom", restored.lastErrorDetail)
  }

  @Test
  fun noFailure_staysEmpty() {
    val restored = roundTrip(rejectedEdit)

    assertNull(restored.lastError)
    assertNull(restored.lastErrorDetail)
  }

  @Test
  fun entryStoredWithOnlyAMessage_readsAsAnUnknownFailure() {
    val stored = ClientSyncStateCodec.toRecord(rejectedEdit).copy(last_error = "Survey is closed")

    val restored = ClientSyncStateCodec.fromRecord(stored)!!

    assertEquals(DomainError.Unknown(), restored.lastError)
    assertEquals("Survey is closed", restored.lastErrorDetail)
  }
}
