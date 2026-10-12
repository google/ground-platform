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
package org.groundplatform.v2.core.forms.ui

import groundplatform.v2.forms.ControlType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.ComponentState

class ExampleFormsCatalogTest {

  private fun collectSelectControls(
    components: List<ComponentState>
  ): List<ComponentState.ControlState> {
    val result = mutableListOf<ComponentState.ControlState>()
    for (component in components) {
      when (component) {
        is ComponentState.ControlState ->
          if (
            component.controlDef.type == ControlType.CONTROL_SELECT_ONE ||
              component.controlDef.type == ControlType.CONTROL_SELECT_MULTIPLE
          ) {
            result += component
          }
        is ComponentState.GroupState -> result += collectSelectControls(component.children)
        is ComponentState.RepeatGroupState ->
          for (instance in component.instances) {
            result += collectSelectControls(instance.children)
          }
      }
    }
    return result
  }

  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun allExampleForms_compileAndIncludeColorAndImageChoices() {
    for (example in WorkbenchExampleForm.entries) {
      val session =
        FormSession(
          formDef = example.formDefinition.proto,
          existingRecord = example.sampleRecordInstance.proto,
        )
      val selectControls = collectSelectControls(session.state.rootComponents)
      assertTrue(
        selectControls.isNotEmpty(),
        "Expected ${example.name} to have at least one select control",
      )

      var hasColorControl = false
      var hasImageControl = false
      for (control in selectControls) {
        val questionHasColor = control.options.any { it.properties["color"] != null }
        val questionHasImage =
          control.options.any { it.label.media?.image_uri?.isNotBlank() == true }
        assertTrue(
          !(questionHasColor && questionHasImage),
          "Question '${control.canonicalPath}' in ${example.name} must not mix colors and images",
        )
        for (option in control.options) {
          val colorHex = option.properties["color"]
          val imageUri = option.label.media?.image_uri?.takeIf { it.isNotBlank() }
          assertTrue(
            colorHex == null || imageUri == null,
            "Option '${option.value}' in ${example.name} (${control.canonicalPath}) must not have both color ($colorHex) and image ($imageUri)",
          )
          if (colorHex != null) {
            hasColorControl = true
            assertNotNull(
              ChoiceImageRegistry.parseChoiceColor(colorHex),
              "Expected valid color hex '$colorHex' in ${example.name} (${control.canonicalPath}/${option.value})",
            )
          }
          if (imageUri != null) {
            hasImageControl = true
            val media = ChoiceImageRegistry.lookup(imageUri)
            assertNotNull(
              media,
              "Expected registered image for '$imageUri' in ${example.name} (${control.canonicalPath}/${option.value})",
            )
            assertEquals("image/png", media.mimeType)
            val bytes = Base64.decode(media.base64)
            assertTrue(bytes.size > 8, "Expected non-empty PNG payload for '$imageUri'")
            // Check PNG magic header: 0x89 'P' 'N' 'G'
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
            assertEquals('N'.code.toByte(), bytes[2])
            assertEquals('G'.code.toByte(), bytes[3])
          }
        }
      }
      assertTrue(
        hasColorControl,
        "Expected ${example.name} to include at least one multiple-choice option with a color swatch",
      )
      if (example != WorkbenchExampleForm.SINGLE_POINT_LAND_USE) {
        assertTrue(
          hasImageControl,
          "Expected ${example.name} to include at least one multiple-choice option with an image",
        )
      }
    }
  }
}
