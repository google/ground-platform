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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.repository.MediaCacheInfo

/** [SettingsRepositoryImpl] keeps the user's settings in the store's preferences. */
class SettingsRepositoryImplTest {
  private val store = InMemoryLocalStore()
  private val repository = SettingsRepositoryImpl(store)

  @Test
  fun updates_returnAndStoreTheNewSettings() {
    val updated = runNow {
      repository.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)
      repository.updateUploadPhotosOnWifiOnly(true)
    }

    assertEquals(MeasurementUnitSystem.IMPERIAL, updated.measurementUnits)
    assertEquals(true, updated.shouldUploadPhotosOnWifiOnly)
    assertEquals(updated, runNow { repository.getUserSettings() })
    assertEquals(updated, runNow { repository.observeUserSettings().first() })
  }

  @Test
  fun updateLanguage_acceptsOnlyOfferedLanguages() {
    val original = runNow { repository.getUserSettings() }

    assertEquals("fr", runNow { repository.updateLanguage("fr") }.language)
    assertEquals("fr", runNow { repository.updateLanguage("xx-unknown") }.language)
    assertEquals(original.copy(language = "fr"), runNow { repository.getUserSettings() })
  }

  @Test
  fun evictingTheMediaCache_returnsWhatWasCachedAndEmptiesIt() {
    runNow {
      store.transaction {
        updatePreferences {
          it.copy(uploadedMediaCacheSizeLabel = "12 MB", uploadedMediaFileCount = 3)
        }
      }
    }

    val evicted = runNow { repository.evictUploadedMediaCache() }

    assertEquals(MediaCacheInfo("12 MB", 3), evicted)
    assertEquals(MediaCacheInfo("0 MB", 0), runNow { repository.observeMediaCache().first() })
  }
}
