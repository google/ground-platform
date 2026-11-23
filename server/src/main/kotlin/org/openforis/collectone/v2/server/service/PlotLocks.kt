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
package org.openforis.collectone.v2.server.service

import com.squareup.wire.Instant
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.AcquirePlotLockResponse
import openforis.collectone.v1.data.PlotLock
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.ReleasePlotLockResponse
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier

/**
 * The plot lock RPCs of `DataService`. A data collector locks a map feature (plot) while collecting
 * data on it, so peers see it is taken: the lock is stored in `PlotLocks` and copied into the
 * entity's `active_lock` (bumping its `revision`) so that it syncs to every client. A lock expires
 * [LOCK_DURATION_SECONDS] after it was taken; expired locks are taken over or released freely. Each
 * change tickles the survey's entities topic so clients sync it promptly.
 */
internal class PlotLocks(
  private val store: UserDataStore,
  private val topicNotifier: SurveyTopicNotifier,
) {
  suspend fun acquire(request: AcquirePlotLockRequest): AcquirePlotLockResponse {
    store.requireSurveyRole(request.survey_id, SurveyRole.DATA_COLLECTOR)
    val userId = callerUserId(request.user_id)
    val committed = store.write { tx ->
      checkNotClosed(tx.survey(request.survey_id), ClosedSurveyRefusal.PLOT_LOCKS)
      val existing = tx.plotLock(request.survey_id, request.entity_id)
      val nowSec = tx.now.epochSecond
      if (
        existing != null &&
          existing.locked_by_user_id != userId &&
          (existing.expires_at?.epochSecond ?: 0L) > nowSec
      ) {
        return@write AcquirePlotLockResponse(
          entity = tx.entity(request.survey_id, request.entity_id),
          lock = existing,
        ) to false
      }
      val lock =
        PlotLock(
          entity_id = request.entity_id,
          locked_by_user_id = userId,
          locked_at = tx.now,
          expires_at = Instant.ofEpochSecond(nowSec + LOCK_DURATION_SECONDS, 0L),
        )
      tx.putPlotLock(request.survey_id, request.entity_id, lock)
      val withLock =
        tx.entity(request.survey_id, request.entity_id)?.let { ent ->
          ent.copy(active_lock = lock, revision = ent.revision + 1L).also(tx::putEntity)
        }
      AcquirePlotLockResponse(entity = withLock, lock = lock) to true
    }
    val (response, written) = committed.value
    return if (written) {
      topicNotifier.notifySurveyEntitiesChanged(request.survey_id, committed.commitTimestamp)
      response.copy(entity = response.entity?.copy(commit_timestamp = committed.commitTimestamp))
    } else {
      response
    }
  }

  suspend fun release(request: ReleasePlotLockRequest): ReleasePlotLockResponse {
    val caller = currentCaller()
    val committed = store.write { tx ->
      val access = Access(tx, caller)
      val role = access.requireSurvey(request.survey_id, SurveyRole.DATA_COLLECTOR)
      val existingLock = tx.plotLock(request.survey_id, request.entity_id)
      if (existingLock != null) {
        val nowSec = tx.now.epochSecond
        val isExpired = (existingLock.expires_at?.epochSecond ?: 0L) <= nowSec
        val isHolder =
          existingLock.locked_by_user_id.isNotBlank() &&
            existingLock.locked_by_user_id == access.userId
        val isOrganizer = role.atLeast(SurveyRole.SURVEY_ORGANIZER)
        if (!isExpired && !isHolder && !isOrganizer) {
          throw Access.denied("plot lock on entity ${request.entity_id}")
        }
      }
      val hadLock = existingLock != null
      tx.putPlotLock(request.survey_id, request.entity_id, null)
      var entityUpdated = false
      tx.entity(request.survey_id, request.entity_id)?.let { ent ->
        tx.putEntity(ent.copy(active_lock = null, revision = ent.revision + 1L))
        entityUpdated = true
      }
      hadLock || entityUpdated
    }
    if (committed.value) {
      topicNotifier.notifySurveyEntitiesChanged(request.survey_id, committed.commitTimestamp)
    }
    return ReleasePlotLockResponse(released = true)
  }

  private companion object {
    /** How long a lock holds without being renewed. */
    const val LOCK_DURATION_SECONDS = 300L
  }
}
