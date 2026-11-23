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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorViewModel

/**
 * The Survey editor's open Form editors, one per Form key, created on first use by [create].
 *
 * Each editor runs in its own child scope of [parentScope] (with a [SupervisorJob], so one editor's
 * failure doesn't cancel the others or the Survey editor), and stops with the Survey editor.
 * Closing an editor cancels its scope; the Survey editor closes them when a Form is deleted and
 * clears them all when its editing session restarts, so a new session never reuses an editor bound
 * to the previous draft.
 *
 * Not thread-safe: use it from the Survey editor's main dispatcher only.
 */
internal class SurveyFormEditors(
  private val parentScope: CoroutineScope,
  private val create: (formKey: String, formScope: CoroutineScope) -> FormEditorViewModel,
) {
  private val editors = mutableMapOf<String, FormEditorViewModel>()

  /** The editor of Form [formKey], created if it isn't open yet. */
  operator fun get(formKey: String): FormEditorViewModel =
    editors.getOrPut(formKey) {
      val formScope =
        CoroutineScope(
          parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job)
        )
      create(formKey, formScope)
    }

  /** Stops and forgets the editor of Form [formKey], if it has one. */
  fun close(formKey: String) {
    editors.remove(formKey)?.viewModelScope?.cancel()
  }

  /** Stops and forgets every editor. */
  fun clear() {
    editors.values.forEach { it.viewModelScope.cancel() }
    editors.clear()
  }

  /**
   * Empties every open editor's undo and redo stacks, e.g. after the draft was saved or dropped.
   */
  fun clearUndoRedo() {
    editors.values.forEach { it.clearUndoRedo() }
  }
}
