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
package org.openforis.collectone.v2.app.data.sync

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.client.push.PushSubscriptionClient
import org.openforis.collectone.v2.app.client.push.surveyDefTopic
import org.openforis.collectone.v2.app.client.push.surveyEntitiesTopic
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore

/**
 * Observes the locally downloaded surveys in [store] and keeps the device subscribed to each active
 * downloaded survey's two FCM topics (`survey_<id>_def` and `survey_<id>_entities`).
 *
 * Also informs [coordinator] whether push topic subscriptions are active on this platform so idle
 * 60-second foreground polling can be suppressed when push wakeups are available.
 */
class SurveyTopicSubscriptionManager(
  private val store: LocalStore,
  private val pushClient: PushSubscriptionClient,
  private val coordinator: SyncCoordinator,
  private val scope: CoroutineScope,
) {
  private val mutex = Mutex()
  private var subscribedSurveyIds: Set<String> = emptySet()
  private var job: Job? = null

  /** Currently subscribed survey IDs (each subscribed to both `_def` and `_entities` topics). */
  fun currentSubscribedSurveyIds(): Set<String> = subscribedSurveyIds

  /** Starts observing downloaded surveys and updating FCM topic subscriptions. */
  fun start() {
    coordinator.setPushSubscriptionsActive(pushClient.isAvailable)
    if (!pushClient.isAvailable || job != null) return
    job =
      store
        .observeSurveys()
        .map { surveys ->
          surveys.filter { it.isDownloaded && !it.isUnavailable }.map { it.id }.toSet()
        }
        .distinctUntilChanged()
        .onEach { desiredIds -> reconcileSubscriptions(desiredIds) }
        .launchIn(scope)
  }

  /** Stops observing downloaded surveys. */
  fun stop() {
    job?.cancel()
    job = null
  }

  private suspend fun reconcileSubscriptions(desiredIds: Set<String>) {
    mutex.withLock {
      val toSubscribe = desiredIds - subscribedSurveyIds
      val toUnsubscribe = subscribedSurveyIds - desiredIds
      for (surveyId in toSubscribe) {
        try {
          pushClient.subscribe(surveyDefTopic(surveyId))
          pushClient.subscribe(surveyEntitiesTopic(surveyId))
          subscribedSurveyIds = subscribedSurveyIds + surveyId
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // Best effort: will retry on the next state emission or launch.
        }
      }
      for (surveyId in toUnsubscribe) {
        try {
          pushClient.unsubscribe(surveyDefTopic(surveyId))
          pushClient.unsubscribe(surveyEntitiesTopic(surveyId))
          subscribedSurveyIds = subscribedSurveyIds - surveyId
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // Best effort: ignore transient unsubscribe errors.
        }
      }
    }
  }
}
