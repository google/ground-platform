/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.client.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Follows [ProcessLifecycleOwner]: in the foreground between `ON_START` and `ON_STOP`. */
actual fun observeAppForeground(onChange: (Boolean) -> Unit): () -> Unit {
  val observer =
    object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) = onChange(true)

      override fun onStop(owner: LifecycleOwner) = onChange(false)
    }
  val lifecycle = ProcessLifecycleOwner.get().lifecycle
  // Lifecycle observers must be added and removed on the main thread; adding one replays the
  // current state, so the first callback reports whether the app is in the foreground now.
  val main = Handler(Looper.getMainLooper())
  main.post { lifecycle.addObserver(observer) }
  return { main.post { lifecycle.removeObserver(observer) } }
}

/** Leaving an Android app doesn't discard the process's outbox. */
actual fun guardUnsentChangesOnUnload(hasUnsentChanges: () -> Boolean): () -> Unit = {}
