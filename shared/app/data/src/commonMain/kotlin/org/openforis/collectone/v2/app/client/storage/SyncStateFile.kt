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

/**
 * Where mobile clients kept their sync state (`ClientSyncState`) between launches before the local
 * database. Read once to move the state into the database, then deleted; nothing writes it anymore.
 */
interface SyncStateFile {
  /** The saved bytes, or `null` if there is no file. */
  fun read(): ByteArray?

  /** Deletes the file, if there is one. */
  fun delete()
}

/**
 * System property holding the path of the sync state file. The Android app sets it to a file in its
 * private files directory; desktop builds may set it too. Unset: nothing is kept.
 */
const val SYNC_STATE_FILE_PROPERTY = "collectone.syncStateFile"

/**
 * The platform's sync state file from before the local database, or `null` where there never was
 * one (the web, and builds that don't set [SYNC_STATE_FILE_PROPERTY]).
 */
expect fun platformSyncStateFile(): SyncStateFile?
