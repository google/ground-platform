/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType

class ImpactEventRepositoryImplTest {
  private fun event(id: String, isUploaded: Boolean = false) =
    ImpactEvent(
      id = id,
      type = ImpactEventType.RECEIPT_GENERATED,
      surveyId = "survey-kenya-coffee",
      organizationId = null,
      occurredAt = "2026-10-01T09:00:00Z",
      actorUserId = "collector@example.org",
      isUploaded = isUploaded,
    )

  @Test
  fun append_keepsEventsInOrder_andIgnoresARepeatedId() {
    val repository = ImpactEventRepositoryImpl(InMemoryLocalStore())
    runNow {
      repository.append(event("a"))
      repository.append(event("b"))
      repository.append(event("a").copy(type = ImpactEventType.RECEIPT_SHARED))
    }

    val events = runNow { repository.getEvents() }
    assertEquals(listOf("a", "b"), events.map { it.id })
    assertEquals(ImpactEventType.RECEIPT_GENERATED, events.first().type)
    assertEquals(events, runNow { repository.observeEvents().first() })
  }

  @Test
  fun markAllUploaded_returnsHowManyWerePending_andIsIdempotent() {
    val repository = ImpactEventRepositoryImpl(InMemoryLocalStore())
    runNow {
      repository.append(event("a", isUploaded = true))
      repository.append(event("b"))
      repository.append(event("c"))
    }

    assertEquals(2, runNow { repository.markAllUploaded() })
    assertTrue(runNow { repository.getEvents() }.all { it.isUploaded })
    assertEquals(0, runNow { repository.markAllUploaded() })
    assertEquals(listOf("a", "b", "c"), runNow { repository.getEvents() }.map { it.id })
  }
}
