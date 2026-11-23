/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource

/** The English labels of Form editor settings whose domain enums carry no display text. */
class EditorLabelsTest {

  private fun english(resources: List<StringResource>) = runBlocking {
    resources.map { getString(it) }
  }

  @Test
  fun formPreviewTargetLabels() {
    assertEquals(listOf("Mobile", "Web"), english(FormPreviewTarget.entries.map { it.labelRes }))
  }

  @Test
  fun mediaSourceLabels() {
    assertEquals(
      listOf("Capture or upload", "Capture only"),
      english(MediaSource.entries.map { it.labelRes }),
    )
  }

  @Test
  fun mediaSourceDescriptions() {
    assertEquals(
      listOf(
        "Collectors can use the camera or microphone, or upload an existing file from their device.",
        "Collectors must take a new photo, video, or recording. Uploading existing files is not allowed.",
      ),
      english(MediaSource.entries.map { it.descriptionRes }),
    )
  }
}
