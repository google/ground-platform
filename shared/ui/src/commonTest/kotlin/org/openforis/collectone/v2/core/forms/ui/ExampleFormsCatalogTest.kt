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
package org.openforis.collectone.v2.core.forms.ui

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlType
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.ComponentState

class ExampleFormsCatalogTest {

  private fun collectAllControls(
    components: List<ComponentState>
  ): List<ComponentState.ControlState> {
    val result = mutableListOf<ComponentState.ControlState>()
    for (component in components) {
      when (component) {
        is ComponentState.ControlState -> result += component
        is ComponentState.GroupState -> result += collectAllControls(component.children)
        is ComponentState.RepeatGroupState ->
          for (instance in component.instances) {
            result += collectAllControls(instance.children)
          }
      }
    }
    return result
  }

  private fun collectRepeatGroups(
    components: List<ComponentState>
  ): List<ComponentState.RepeatGroupState> {
    val result = mutableListOf<ComponentState.RepeatGroupState>()
    for (component in components) {
      when (component) {
        is ComponentState.ControlState -> Unit
        is ComponentState.GroupState -> result += collectRepeatGroups(component.children)
        is ComponentState.RepeatGroupState -> {
          result += component
          for (instance in component.instances) {
            result += collectRepeatGroups(instance.children)
          }
        }
      }
    }
    return result
  }

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

  @Test
  fun allExampleForms_startEmptyExceptCalculatedAndPreloadedFields() {
    val calculatedPaths =
      setOf(
        "/data/selected_plot_stratum",
        "/data/selected_plot_elevation",
        "/data/preloaded_household_code",
        "/data/preloaded_baseline_role",
        "/data/preloaded_baseline_year",
        "/data/calculated_shade_index",
      )
    for (example in WorkbenchExampleForm.entries) {
      val session = FormSession(formDef = example.formDefinition.proto)
      val controls = collectAllControls(session.state.rootComponents)
      for (control in controls) {
        if (control.canonicalPath in calculatedPaths) {
          assertTrue(
            control.fieldState.isReadOnly,
            "Expected calculated/preloaded control ${control.canonicalPath} in ${example.name} to be read-only",
          )
          if (control.canonicalPath == "/data/calculated_shade_index") {
            assertEquals(
              "Species:  | Saplings:  | Canopy: %",
              control.fieldState.value?.scalar_value?.string_value,
            )
          } else {
            // Entity lookup fields start blank until an entity is selected
            assertEquals(
              "",
              control.fieldState.value?.scalar_value?.string_value.orEmpty(),
              "Expected lookup field ${control.canonicalPath} in ${example.name} to be blank before entity selection",
            )
          }
        } else {
          assertNull(
            control.fieldState.value,
            "Expected user-input control ${control.canonicalPath} in ${example.name} to start empty, but was ${control.fieldState.value}",
          )
        }
      }
      val repeatGroups = collectRepeatGroups(session.state.rootComponents)
      for (repeatGroup in repeatGroups) {
        assertTrue(
          repeatGroup.instances.isEmpty(),
          "Expected repeat group ${repeatGroup.canonicalPath} in ${example.name} to start with 0 instances",
        )
      }
    }

    // Verify preloaded entity lookup fields populate when the user selects an entity
    val plotSession =
      FormSession(
        formDef = WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT.formDefinition.proto
      )
    plotSession.updateString("/data/sample_plot_entity", "00000016-cc1f-5125-8841-166ab5b5f68f")
    val plotControls =
      collectAllControls(plotSession.state.rootComponents).associateBy { it.canonicalPath }
    assertEquals(
      "Montane Moist Indigenous Forest",
      plotControls["/data/selected_plot_stratum"]?.fieldState?.value?.scalar_value?.string_value,
    )
    assertEquals(
      "1840m",
      plotControls["/data/selected_plot_elevation"]?.fieldState?.value?.scalar_value?.string_value,
    )

    val householdSession =
      FormSession(
        formDef = WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS.formDefinition.proto
      )
    householdSession.updateString(
      "/data/primary_respondent_id",
      "0000000c-a9ae-59f6-ac51-7f5a8777598f",
    )
    val householdControls =
      collectAllControls(householdSession.state.rootComponents).associateBy { it.canonicalPath }
    assertEquals(
      "HH-KAK-014 (Kakamega East)",
      householdControls["/data/preloaded_household_code"]
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.string_value,
    )
    assertEquals(
      "Household Head & Lead Agroforester",
      householdControls["/data/preloaded_baseline_role"]
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.string_value,
    )
    assertEquals(
      "2023 Wave 1",
      householdControls["/data/preloaded_baseline_year"]
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.string_value,
    )
  }
}
