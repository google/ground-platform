/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SubmissionTimeFormatTest {
  private val savedOnDevice = Instant.parse("2026-09-24T08:15:42Z")
  private val acceptedByServer = Instant.parse("2026-09-24T08:17:05Z")

  private fun submission(clientUpdatedAt: Instant?, serverUpdatedAt: Instant?) =
    SubmissionPreviewItem(
      id = "submission-1",
      formId = "form-1",
      formTitle = "Plot visit",
      formVersion = "1",
      collectorName = "Maya Lin",
      collectorEmail = "maya.lin@example.org",
      clientUpdatedAt = clientUpdatedAt,
      serverUpdatedAt = serverUpdatedAt,
      fields = emptyList(),
    )

  @Test
  fun formatSubmissionTime_showsTheUtcMinute() {
    assertEquals("2026-09-24 08:15 UTC", formatSubmissionTime(savedOnDevice))
  }

  @Test
  fun formatMutationTime_showsTheUtcSecond() {
    assertEquals("2026-09-24 08:15:42 UTC", formatMutationTime(savedOnDevice))
  }

  @Test
  fun updatedAtLabel_prefersTheCollectorsSaveTime() {
    val synced = submission(clientUpdatedAt = savedOnDevice, serverUpdatedAt = acceptedByServer)

    assertEquals("2026-09-24 08:15 UTC", synced.updatedAtLabel)
  }

  @Test
  fun updatedAtLabel_withoutAClientTime_showsTheServerTime() {
    val fromAnOlderClient = submission(clientUpdatedAt = null, serverUpdatedAt = acceptedByServer)

    assertEquals("2026-09-24 08:17 UTC", fromAnOlderClient.updatedAtLabel)
  }

  @Test
  fun updatedAtLabel_withoutAnyTime_isEmpty() {
    assertEquals("", submission(clientUpdatedAt = null, serverUpdatedAt = null).updatedAtLabel)
  }
}
