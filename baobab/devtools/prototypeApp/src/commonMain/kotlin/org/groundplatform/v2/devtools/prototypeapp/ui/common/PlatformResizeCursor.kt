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
 * Pointer icon for a horizontally draggable border (a left-right resize cursor where the platform
 * exposes one to Compose). Compose Multiplatform's common [PointerIcon] set has no resize cursor,
 * so platforms without one return [PointerIcon.Default] and rely on
 * [showPlatformHorizontalResizeCursor] instead.
 */
internal expect val HorizontalResizePointerIcon: PointerIcon

/**
 * Pointer icon for a pannable surface or drag handle at rest (an open-hand `grab` cursor where the
 * platform exposes one). Platforms without a built-in grab [PointerIcon] return
 * [PointerIcon.Default] and rely on [showPlatformGrabCursor] instead.
 */
internal expect val GrabPointerIcon: PointerIcon

/**
 * Pointer icon while actively panning a surface or dragging an item to reorder it (a closed-hand
 * `grabbing` cursor where the platform exposes one). Platforms without a built-in grabbing
 * [PointerIcon] return [PointerIcon.Default] and rely on [showPlatformGrabbingCursor] instead.
 */
internal expect val GrabbingPointerIcon: PointerIcon

/**
 * Forces (when [show] is `true`) or releases the platform's left-right resize cursor over the whole
 * app, for platforms where [HorizontalResizePointerIcon] can't express it. Kept on while a border
 * is dragged so the cursor doesn't flicker when the pointer runs ahead of the border. No-op where
 * [HorizontalResizePointerIcon] already is a resize cursor.
 */
internal expect fun showPlatformHorizontalResizeCursor(show: Boolean)

/**
 * Enables (when [show] is `true`) or releases the platform's open-hand `grab` cursor on the canvas
 * whenever no descendant overrides the cursor with [PointerIcon.Hand], [PointerIcon.Text], or
 * [PointerIcon.Crosshair]. This lets a pannable canvas and drag handles (`⋮⋮`) show `grab` while
 * clickable cards and buttons inside the canvas still show `pointer`.
 */
internal expect fun showPlatformGrabCursor(show: Boolean)

/**
 * Forces (when [show] is `true`) or releases the platform's closed-hand `grabbing` cursor across
 * the whole app while a pan or drag-to-reorder gesture is in progress.
 */
internal expect fun showPlatformGrabbingCursor(show: Boolean)
