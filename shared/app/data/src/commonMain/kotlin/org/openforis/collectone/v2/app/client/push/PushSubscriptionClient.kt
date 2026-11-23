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
package org.openforis.collectone.v2.app.client.push

/**
 * Platform client that subscribes and unsubscribes the device to/from Firebase Cloud Messaging
 * (FCM) topics for downloaded surveys (`docs/technical/sync-engine.md`, "FCM Topic Tickles").
 *
 * Two topics are maintained per downloaded survey:
 * - [surveyDefTopic] (`survey_<survey_id>_def`) for survey definition, form, trash/restore, and ACL
 *   updates.
 * - [surveyEntitiesTopic] (`survey_<survey_id>_entities`) for entity and plot-lock updates.
 */
interface PushSubscriptionClient {
  /**
   * Whether push topic subscriptions are available on this platform and runtime (for example, true
   * on Android when Firebase is configured, false on Web or desktop/JVM). When false, the sync
   * engine retains its 60-second idle foreground polling timer as a fallback.
   */
  val isAvailable: Boolean

  /** Subscribes this device to [topic]. */
  suspend fun subscribe(topic: String)

  /** Unsubscribes this device from [topic]. */
  suspend fun unsubscribe(topic: String)
}

/** No-op [PushSubscriptionClient] used on platforms or builds without FCM support. */
object NoopPushSubscriptionClient : PushSubscriptionClient {
  override val isAvailable: Boolean = false

  override suspend fun subscribe(topic: String) {}

  override suspend fun unsubscribe(topic: String) {}
}

/** FCM topic name for definition, form, lifecycle, and ACL changes to [surveyId]. */
fun surveyDefTopic(surveyId: String): String = "survey_${surveyId}_def"

/** FCM topic name for entity and plot-lock changes to [surveyId]. */
fun surveyEntitiesTopic(surveyId: String): String = "survey_${surveyId}_entities"

/** Returns the platform's default [PushSubscriptionClient]. */
expect fun defaultPushSubscriptionClient(): PushSubscriptionClient
