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
package org.openforis.collectone.v2.app.ui.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * The session's notice: one short message at a time (such as "Centered map on …" or a failed
 * sign-in), shown by whichever screen is visible until the next outcome replaces or clears it.
 *
 * A [UiText] notice is resolved in the user's language on [scope], which may finish after the call
 * returns; a later [show] or [clear] wins over a notice still being resolved.
 */
class NoticeController(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
) {
  private val _notice = MutableStateFlow<String?>(null)
  private var resolving: Job? = null

  /** The message to show, or `null` when there is none. */
  val notice: StateFlow<String?> = _notice.asStateFlow()

  /** Shows [message], replacing the current notice (or clears it when `null`). */
  fun show(message: String?) {
    resolving?.cancel()
    _notice.value = message
  }

  /** Shows [message] in the user's language, replacing the current notice. */
  fun show(message: UiText) {
    resolving?.cancel()
    resolving = scope.launch { _notice.value = message.resolveString() }
  }

  /** Clears the current notice. */
  fun clear() {
    resolving?.cancel()
    _notice.value = null
  }
}
