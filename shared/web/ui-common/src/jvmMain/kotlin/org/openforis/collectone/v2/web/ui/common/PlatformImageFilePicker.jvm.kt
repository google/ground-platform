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

import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_file_upload_unavailable

/** Test hook for JVM Compose UI tests to inject image pick outcomes. */
var jvmImageFilePickerOverride: ImageFilePicker? = null

actual fun openPlatformImageFilePicker(
  maxDimensionPx: Int,
  onResult: (ImageFilePickResult) -> Unit,
) {
  val override = jvmImageFilePickerOverride
  if (override != null) {
    override(maxDimensionPx, onResult)
  } else {
    onResult(ImageFilePickResult.Failed(Res.string.web_file_upload_unavailable.toUiText()))
  }
}
