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
@file:Suppress("MatchingDeclarationName")

package org.openforis.collectone.v2.app.client.push

import kotlin.concurrent.Volatile

/**
 * Android [PushSubscriptionClient] backed by a platform [delegate] (typically
 * `FirebaseMessaging.getInstance()`) attached by the Android host application for its Koin graph.
 * When no delegate is attached or Firebase is not configured, [isAvailable] is `false` and the sync
 * engine falls back to foreground polling.
 */
class AndroidPushSubscriptionRegistry(
  @Volatile var delegate: PushSubscriptionClient = NoopPushSubscriptionClient
) : PushSubscriptionClient {
  override val isAvailable: Boolean
    get() = delegate.isAvailable

  override suspend fun subscribe(topic: String) {
    delegate.subscribe(topic)
  }

  override suspend fun unsubscribe(topic: String) {
    delegate.unsubscribe(topic)
  }
}

actual fun defaultPushSubscriptionClient(): PushSubscriptionClient =
  AndroidPushSubscriptionRegistry()
