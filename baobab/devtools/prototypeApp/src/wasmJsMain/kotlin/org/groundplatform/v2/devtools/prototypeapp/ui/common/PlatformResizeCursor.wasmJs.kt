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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Compose for Web only exposes the common cursors, so `col-resize`, `grab`, and `grabbing` cursors
 * come from [showPlatformHorizontalResizeCursor], [showPlatformGrabCursor], and
 * [showPlatformGrabbingCursor].
 */
internal actual val HorizontalResizePointerIcon: PointerIcon = PointerIcon.Default

internal actual val GrabPointerIcon: PointerIcon = PointerIcon.Default

internal actual val GrabbingPointerIcon: PointerIcon = PointerIcon.Default

/**
 * Toggles a cursor utility class (`ground-col-resize`, `ground-grab`, or `ground-grabbing`) across
 * the page and every open shadow root (`ComposeViewport`).
 *
 * - `.ground-col-resize` and `.ground-grabbing` force `col-resize` / `grabbing` everywhere while an
 *   active drag is in progress, so the cursor doesn't flicker when the pointer moves across child
 *   elements or runs ahead of the dragged target.
 * - `canvas.ground-grab` applies `cursor: grab !important` only when Compose has not set a more
 *   specific inline cursor (`pointer`, `text`, or `crosshair`) on the canvas and no active drag
 *   (`.ground-grabbing` / `.ground-col-resize`) is underway. This lets the pannable canvas and `⋮⋮`
 *   drag handles (which use [GrabPointerIcon] / `default`) show `grab`, while selectable question
 *   cards and buttons inside the canvas (which use [PointerIcon.Hand] / `pointer`) show `pointer`.
 */
@JsFun(
  """(className, show) => {
    const id = 'ground-cursor-style';
    const css = [
      'canvas.ground-grab:not(.ground-grabbing):not(.ground-col-resize):not([style*="pointer"]):not([style*="text"]):not([style*="crosshair"]) { cursor: grab !important; }',
      '.ground-grabbing, .ground-grabbing * { cursor: grabbing !important; }',
      '.ground-col-resize, .ground-col-resize * { cursor: col-resize !important; }'
    ].join('\n');
    const roots = [document];
    document.querySelectorAll('*').forEach((el) => { if (el.shadowRoot) roots.push(el.shadowRoot); });
    for (const root of roots) {
      const existing = root.getElementById(id);
      if (!existing) {
        const style = document.createElement('style');
        style.id = id;
        style.textContent = css;
        (root === document ? document.head : root).appendChild(style);
      } else if (existing.textContent !== css) {
        existing.textContent = css;
      }
      root.querySelectorAll('canvas').forEach((c) => c.classList.toggle(className, show));
    }
    document.documentElement.classList.toggle(className, show);
  }"""
)
private external fun jsToggleCursorClass(className: String, show: Boolean)

private var colResizeHolders = 0
private var grabHolders = 0
private var grabbingHolders = 0

internal actual fun showPlatformHorizontalResizeCursor(show: Boolean) {
  colResizeHolders = (colResizeHolders + if (show) 1 else -1).coerceAtLeast(0)
  jsToggleCursorClass("ground-col-resize", colResizeHolders > 0)
}

internal actual fun showPlatformGrabCursor(show: Boolean) {
  grabHolders = (grabHolders + if (show) 1 else -1).coerceAtLeast(0)
  jsToggleCursorClass("ground-grab", grabHolders > 0)
}

internal actual fun showPlatformGrabbingCursor(show: Boolean) {
  grabbingHolders = (grabbingHolders + if (show) 1 else -1).coerceAtLeast(0)
  jsToggleCursorClass("ground-grabbing", grabbingHolders > 0)
}
