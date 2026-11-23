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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.web.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@JsFun(
  """() => {
    const listener = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', listener);
    return listener;
  }"""
)
private external fun jsAddBeforeUnloadListener(): JsAny

@JsFun("(listener) => window.removeEventListener('beforeunload', listener)")
private external fun jsRemoveBeforeUnloadListener(listener: JsAny)

@Composable
actual fun PlatformBeforeUnloadEffect(enabled: Boolean) {
  DisposableEffect(enabled) {
    val listener = if (enabled) jsAddBeforeUnloadListener() else null
    onDispose {
      if (listener != null) {
        jsRemoveBeforeUnloadListener(listener)
      }
    }
  }
}
