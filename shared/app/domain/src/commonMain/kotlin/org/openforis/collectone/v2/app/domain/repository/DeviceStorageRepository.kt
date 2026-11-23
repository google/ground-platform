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
package org.openforis.collectone.v2.app.domain.repository

import org.openforis.collectone.v2.app.domain.model.DataSize

/** The device's storage, measured: what's free and what there is in all. */
interface DeviceStorageRepository {
  /** The space free for the app, or null when this platform can't measure it. */
  suspend fun freeSpace(): DataSize?

  /** The device's (or the app's quota's) total space, or null when it can't be measured. */
  suspend fun totalSpace(): DataSize?
}
