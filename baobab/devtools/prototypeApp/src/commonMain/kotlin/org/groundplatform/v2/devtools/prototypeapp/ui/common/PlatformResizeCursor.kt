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
 * Forces (when [show] is `true`) or releases the platform's left-right resize cursor over the whole
 * app, for platforms where [HorizontalResizePointerIcon] can't express it. Kept on while a border
 * is dragged so the cursor doesn't flicker when the pointer runs ahead of the border. No-op where
 * [HorizontalResizePointerIcon] already is a resize cursor.
 */
internal expect fun showPlatformHorizontalResizeCursor(show: Boolean)
