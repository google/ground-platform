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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent

/**
 * Domain repository contract for the append-only impact event log (see
 * `docs/technical/model/data/04-impact-events.md`). Events are never edited or removed; only their
 * upload state changes.
 */
interface ImpactEventRepository {
  /** Every recorded event, oldest first. */
  fun observeEvents(): Flow<List<ImpactEvent>>

  suspend fun getEvents(): List<ImpactEvent>

  /** Appends [event] (ignored if an event with its ID was already recorded). */
  suspend fun append(event: ImpactEvent)

  /** Marks every event not uploaded yet as uploaded and returns how many there were. */
  suspend fun markAllUploaded(): Int
}
