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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.ui.text.buildAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus

class GeoIdTextTest {

  private val entity = PrototypeAppState().entities.first()

  @Test
  fun syncedEntity_isNotPendingAndShowsGeoIdOnly() {
    val synced = entity.copy(syncStatus = SyncStatus.SYNCED)

    assertFalse(synced.isGeoIdPendingSync)
    assertEquals(synced.geoId, buildAnnotatedString { appendGeoId(synced) }.text)
  }

  @Test
  fun unsyncedEntities_arePendingAndAppendIcon() {
    for (status in listOf(SyncStatus.UPLOADING, SyncStatus.FAILED)) {
      val pending = entity.copy(syncStatus = status)
      val text = buildAnnotatedString { appendGeoId(pending) }

      assertTrue(pending.isGeoIdPendingSync, "$status should be pending sync")
      assertEquals("${pending.geoId}\u00A0($GeoIdPendingSyncDescription)", text.text)
      assertEquals(
        GeoIdPendingSyncIconId,
        text.getStringAnnotations(0, text.length).single().item,
      )
    }
  }
}
