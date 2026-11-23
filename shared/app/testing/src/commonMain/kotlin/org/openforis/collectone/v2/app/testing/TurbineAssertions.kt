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
package org.openforis.collectone.v2.app.testing

import app.cash.turbine.ReceiveTurbine
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Awaits items until one matches [predicate] and returns it. Fails, like
 * [ReceiveTurbine.awaitItem], when no matching item arrives before the turbine's timeout.
 */
suspend fun <T> ReceiveTurbine<T>.awaitItemMatching(predicate: (T) -> Boolean): T {
  var item = awaitItem()
  while (!predicate(item)) item = awaitItem()
  return item
}

/**
 * Takes the items received so far that weren't consumed yet, without waiting for more. For
 * asserting that an action emitted an event among others, where the exact sequence isn't the point.
 */
fun <T : Any> ReceiveTurbine<T>.takeReceivedItems(): List<T> {
  val items = asChannel()
  return generateSequence { items.tryReceive().getOrNull() }.toList()
}

/**
 * Fails if an item arrives within [window] of real time. For work that would run on other threads
 * (such as loading a string resource), where `runTest`'s virtual time can't tell it never started.
 */
suspend fun <T> ReceiveTurbine<T>.expectNoItemsWithin(window: Duration) {
  withContext(Dispatchers.Default) { delay(window) }
  expectNoEvents()
}
