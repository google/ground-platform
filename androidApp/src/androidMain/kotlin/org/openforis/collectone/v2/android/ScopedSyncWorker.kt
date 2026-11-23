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
package org.openforis.collectone.v2.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.coroutines.cancellation.CancellationException
import org.openforis.collectone.v2.app.data.sync.SyncTickle
import org.openforis.collectone.v2.app.data.sync.parseSyncTickle
import org.openforis.collectone.v2.app.data.sync.syncTickleNow

/**
 * Background [CoroutineWorker] enqueued when a `survey_<survey_id>_def` or
 * `survey_<survey_id>_entities` FCM topic tickle arrives while the app is in the background.
 *
 * Waits for network connectivity (`NetworkType.CONNECTED`) and runs the tickle's targeted delta
 * pull via [syncTickleNow], retrying later when it fails.
 */
class ScopedSyncWorker(appContext: Context, params: WorkerParameters) :
  CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    // The input data holds the push payload itself (see [enqueue]).
    val tickle =
      parseSyncTickle(inputData.keyValueMap.mapValues { it.value as? String })
        ?: return Result.success()
    val holder =
      MobileSyncRuntime.getOrCreateDataHolder(applicationContext) ?: return Result.success()
    val coordinator = holder.syncCoordinator ?: return Result.success()
    return try {
      if (coordinator.syncTickleNow(tickle)) Result.success() else Result.retry()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      Result.retry()
    }
  }

  companion object {
    /** Enqueues the sync [tickle] asks for, replacing a pending one for the same survey scope. */
    fun enqueue(context: Context, tickle: SyncTickle) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val payload =
        listOfNotNull(
          "survey_id" to tickle.surveyId,
          "scope" to tickle.scope.wireValue,
          tickle.minRecordVersion?.let { "record_version" to it.toString() },
        )
      val request =
        OneTimeWorkRequestBuilder<ScopedSyncWorker>()
          .setConstraints(constraints)
          .setInputData(workDataOf(*payload.toTypedArray()))
          .build()
      val uniqueWorkName = "collect_one_sync_${tickle.surveyId}_${tickle.scope.wireValue}"
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
    }
  }
}
