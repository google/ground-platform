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
package org.openforis.collectone.v2.app.client.lifecycle

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * Observes `UIApplicationDidBecomeActiveNotification` and
 * `UIApplicationWillResignActiveNotification` on the main queue, immediately reporting the current
 * application state.
 */
actual fun observeAppForeground(onChange: (Boolean) -> Unit): () -> Unit {
  val center = NSNotificationCenter.defaultCenter
  val mainQueue = NSOperationQueue.mainQueue
  val isActiveNow =
    UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
  onChange(isActiveNow)

  val activeObserver =
    center.addObserverForName(
      name = UIApplicationDidBecomeActiveNotification,
      `object` = null,
      queue = mainQueue,
    ) { _ ->
      onChange(true)
    }
  val resignObserver =
    center.addObserverForName(
      name = UIApplicationWillResignActiveNotification,
      `object` = null,
      queue = mainQueue,
    ) { _ ->
      onChange(false)
    }
  return {
    center.removeObserver(activeObserver)
    center.removeObserver(resignObserver)
  }
}

/** Leaving an iOS app doesn't discard the persisted outbox. */
actual fun guardUnsentChangesOnUnload(hasUnsentChanges: () -> Boolean): () -> Unit = {}
