/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.client.storage

/** Where a mobile client keeps its sync state (`ClientSyncState`) between launches. */
interface SyncStateFile {
  /** The saved bytes, or `null` if nothing was saved yet. */
  fun read(): ByteArray?

  /** Replaces the saved bytes atomically. */
  fun write(bytes: ByteArray)
}

/**
 * System property holding the path of the sync state file. The Android app sets it to a file in its
 * private files directory; desktop builds may set it too. Unset: nothing is kept.
 */
const val SYNC_STATE_FILE_PROPERTY = "groundplatform.syncStateFile"

/**
 * The platform's sync state file, or `null` where sync state isn't kept between launches (the web,
 * whose outbox is in memory by design, and builds that don't set [SYNC_STATE_FILE_PROPERTY]).
 */
expect fun platformSyncStateFile(): SyncStateFile?
