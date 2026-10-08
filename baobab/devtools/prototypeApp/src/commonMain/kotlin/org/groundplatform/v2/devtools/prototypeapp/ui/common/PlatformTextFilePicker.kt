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

/** Outcome of picking a text file (e.g. GeoJSON or KML) with [openPlatformTextFilePicker]. */
sealed interface TextFilePickResult {
  data class Picked(val fileName: String, val text: String) : TextFilePickResult

  data object Cancelled : TextFilePickResult

  data class Failed(val message: String) : TextFilePickResult
}

/**
 * Picks a file and reads it as text: [accept] lists file extensions or MIME types, as in an HTML
 * `<input type="file" accept>`. Callers should take a picker function as a parameter (defaulting to
 * this one) so tests can supply file contents directly.
 */
typealias TextFilePicker = (accept: String, onResult: (TextFilePickResult) -> Unit) -> Unit

/** Whether this platform has a real file picker for [openPlatformTextFilePicker]. */
internal expect val isPlatformTextFilePickerAvailable: Boolean

/** Opens the platform file picker and reads the chosen file as UTF-8 text. */
internal expect fun openPlatformTextFilePicker(
  accept: String,
  onResult: (TextFilePickResult) -> Unit,
)
