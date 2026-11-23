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
package org.openforis.collectone.v2.app.data.sync

/**
 * A push notification asking the device to sync one survey's [scope]
 * (`docs/technical/sync-engine.md`, "FCM Topic Tickles (Mobile)").
 */
data class SyncTickle(
  val surveyId: String,
  val scope: SurveySyncScope,
  /** The record version that triggered the tickle, if the server sent one. */
  val minRecordVersion: Long? = null,
)

/**
 * Reads a [SyncTickle] from the data of a push notification (`survey_id`, `scope` and optional
 * `record_version`), or returns null when the payload isn't a tickle.
 */
fun parseSyncTickle(data: Map<String, String?>): SyncTickle? {
  val surveyId = data["survey_id"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val scope = SurveySyncScope.fromWireValue(data["scope"]) ?: return null
  return SyncTickle(
    surveyId = surveyId,
    scope = scope,
    minRecordVersion = data["record_version"]?.trim()?.toLongOrNull(),
  )
}

/**
 * Runs the scoped sync [tickle] asks for now and returns whether it succeeded. A sync that ends
 * with [SyncActivity.lastError] set counts as failed, so background schedulers retry it later.
 */
suspend fun SyncCoordinator.syncTickleNow(tickle: SyncTickle): Boolean {
  syncScopedNow(
    surveyId = tickle.surveyId,
    scope = tickle.scope,
    minRecordVersion = tickle.minRecordVersion,
  )
  return activity.value.lastError == null
}
