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
package org.openforis.collectone.v2.web.ui.common

import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * Outcome of picking an image file (e.g. an organization logo) with [openPlatformImageFilePicker].
 */
sealed interface ImageFilePickResult {
  /** The chosen image, resized and encoded as a `data:image/...;base64,...` URI. */
  data class Picked(val fileName: String, val dataUrl: String) : ImageFilePickResult

  data object Cancelled : ImageFilePickResult

  /** The file couldn't be picked or decoded; [message] says why, in the user's language. */
  data class Failed(val message: UiText) : ImageFilePickResult
}

/**
 * Picks an image file and scales it down so neither dimension exceeds [maxDimensionPx], returning a
 * compact `data:image/...;base64,...` URI. Callers should accept this type as a parameter
 * (defaulting to [openPlatformImageFilePicker]) so tests can inject image data directly.
 */
typealias ImageFilePicker = (maxDimensionPx: Int, onResult: (ImageFilePickResult) -> Unit) -> Unit

/** Default maximum width/height (in pixels) for uploaded organization logos. */
const val DEFAULT_LOGO_MAX_DIMENSION_PX = 256

/** Opens the platform image picker and returns a resized base64 data URI. */
expect fun openPlatformImageFilePicker(
  maxDimensionPx: Int = DEFAULT_LOGO_MAX_DIMENSION_PX,
  onResult: (ImageFilePickResult) -> Unit,
)
