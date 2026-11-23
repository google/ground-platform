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

/** Follows the tab's `visibilitychange` events. */
actual fun observeAppForeground(onChange: (Boolean) -> Unit): () -> Unit {
  onChange(isDocumentVisible())
  val handler = addVisibilityListener(onChange)
  return { removeVisibilityListener(handler) }
}

/** Shows the browser's leave-page confirmation from `beforeunload` while changes are unsent. */
actual fun guardUnsentChangesOnUnload(hasUnsentChanges: () -> Boolean): () -> Unit {
  val handler = addBeforeUnloadListener(hasUnsentChanges)
  return { removeBeforeUnloadListener(handler) }
}

@JsFun("() => document.visibilityState === 'visible'")
private external fun isDocumentVisible(): Boolean

@JsFun(
  """(callback) => {
    const handler = () => callback(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', handler);
    return handler;
  }"""
)
private external fun addVisibilityListener(callback: (Boolean) -> Unit): JsAny

@JsFun("(handler) => document.removeEventListener('visibilitychange', handler)")
private external fun removeVisibilityListener(handler: JsAny)

@JsFun(
  """(hasUnsentChanges) => {
    const handler = (event) => {
      if (hasUnsentChanges()) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handler);
    return handler;
  }"""
)
private external fun addBeforeUnloadListener(hasUnsentChanges: () -> Boolean): JsAny

@JsFun("(handler) => window.removeEventListener('beforeunload', handler)")
private external fun removeBeforeUnloadListener(handler: JsAny)
