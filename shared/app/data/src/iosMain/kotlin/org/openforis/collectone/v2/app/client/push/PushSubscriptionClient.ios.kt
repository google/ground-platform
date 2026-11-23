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

import kotlin.concurrent.Volatile

/**
 * Swift-compatible bridge for Firebase Cloud Messaging topic subscriptions on iOS.
 *
 * Because the Firebase iOS SDK (`FirebaseMessaging`) is linked by the Swift host (`iosApp`), the
 * host implements [IosPushTopicBridge] in Swift and assigns it to
 * [IosPushSubscriptionRegistry.bridge] at launch. When no bridge is registered (or Firebase is not
 * configured), [isAvailable] is `false` and the sync engine falls back to foreground polling.
 */
interface IosPushTopicBridge {
  val isAvailable: Boolean

  fun subscribe(topic: String)

  fun unsubscribe(topic: String)
}

/** Registry where `iosApp` plugs in its [IosPushTopicBridge] at launch. */
object IosPushSubscriptionRegistry {
  @Volatile var bridge: IosPushTopicBridge? = null
}

actual fun defaultPushSubscriptionClient(): PushSubscriptionClient =
  object : PushSubscriptionClient {
    override val isAvailable: Boolean
      get() = IosPushSubscriptionRegistry.bridge?.isAvailable == true

    override suspend fun subscribe(topic: String) {
      IosPushSubscriptionRegistry.bridge?.subscribe(topic)
    }

    override suspend fun unsubscribe(topic: String) {
      IosPushSubscriptionRegistry.bridge?.unsubscribe(topic)
    }
  }
