/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Compose for Web only exposes the common cursors, so the resize cursor comes from
 * [showPlatformHorizontalResizeCursor].
 */
internal actual val HorizontalResizePointerIcon: PointerIcon = PointerIcon.Default

/**
 * Shows or hides the `col-resize` cursor across the page. A stylesheet rule with `!important` wins
 * over the inline `cursor` Compose writes on its canvas. Compose for Web renders into a canvas
 * inside an open shadow root (`ComposeViewport`), which document styles don't reach, so the rule is
 * also added to every open shadow root and the class is toggled on the canvases there. `<html>`
 * gets the class too, for the map and any other light-DOM element under the pointer while dragging.
 */
@JsFun(
  """(show) => {
    const id = 'ground-col-resize-style';
    const css = '.ground-col-resize, .ground-col-resize * { cursor: col-resize !important; }';
    const roots = [document];
    document.querySelectorAll('*').forEach((el) => { if (el.shadowRoot) roots.push(el.shadowRoot); });
    for (const root of roots) {
      if (!root.getElementById(id)) {
        const style = document.createElement('style');
        style.id = id;
        style.textContent = css;
        (root === document ? document.head : root).appendChild(style);
      }
      root.querySelectorAll('canvas').forEach((c) => c.classList.toggle('ground-col-resize', show));
    }
    document.documentElement.classList.toggle('ground-col-resize', show);
  }"""
)
private external fun jsShowColResizeCursor(show: Boolean)

internal actual fun showPlatformHorizontalResizeCursor(show: Boolean) {
  jsShowColResizeCursor(show)
}
