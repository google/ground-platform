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
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption
import org.openforis.collectone.v2.core.forms.model.ResolvedLabel

class ChoiceOptionColorStyleTest {

  private val paletteHexes =
    listOf(
      "#D13135",
      "#F37C22",
      "#F9A825",
      "#FBC02D",
      "#2E7D32",
      "#1B5E20",
      "#00897B",
      "#1976D2",
      "#5C6BC0",
      "#8E24AA",
      "#6D4C41",
      "#546E7A",
    )

  @Test
  fun parseChoiceColor_supports6Digit8DigitAnd3DigitHex() {
    assertEquals(Color(0xFF2E7D32), ChoiceImageRegistry.parseChoiceColor("#2E7D32"))
    assertEquals(Color(0xFF2E7D32), ChoiceImageRegistry.parseChoiceColor("2e7d32"))
    assertEquals(Color(0xCC1976D2), ChoiceImageRegistry.parseChoiceColor("#CC1976D2"))
    assertEquals(Color(0xFFFF8800), ChoiceImageRegistry.parseChoiceColor("#F80"))
    assertNull(ChoiceImageRegistry.parseChoiceColor(null))
    assertNull(ChoiceImageRegistry.parseChoiceColor(""))
    assertNull(ChoiceImageRegistry.parseChoiceColor("not-a-color"))
  }

  @Test
  fun choiceOptionColorStyle_meetsWcagAaContrastInLightAndDarkThemes() {
    val lightSurface = AppLightColorScheme.surface
    val lightOnSurface = AppLightColorScheme.onSurface
    val darkSurface = AppDarkColorScheme.surface
    val darkOnSurface = AppDarkColorScheme.onSurface

    for (hex in paletteHexes) {
      val choiceColor = assertNotNull(ChoiceImageRegistry.parseChoiceColor(hex), hex)
      for ((themeName, surfacePair) in
        listOf(
          "light" to (lightSurface to lightOnSurface),
          "dark" to (darkSurface to darkOnSurface),
        )) {
        val (surface, onSurface) = surfacePair
        val unselected =
          ChoiceImageRegistry.choiceOptionColorStyle(
            choiceColor = choiceColor,
            isSelected = false,
            surfaceColor = surface,
            onSurfaceColor = onSurface,
          )
        val selected =
          ChoiceImageRegistry.choiceOptionColorStyle(
            choiceColor = choiceColor,
            isSelected = true,
            surfaceColor = surface,
            onSurfaceColor = onSurface,
          )

        assertNotEquals(surface, unselected.containerColor, "$hex ($themeName) unselected tint")
        assertNotEquals(
          unselected.containerColor,
          selected.containerColor,
          "$hex ($themeName) selected vs unselected tint",
        )

        val unselectedContrast =
          ChoiceImageRegistry.contrastRatio(unselected.contentColor, unselected.containerColor)
        assertTrue(
          unselectedContrast >= 4.5f,
          "Expected >= 4.5 contrast for $hex unselected in $themeName, got $unselectedContrast",
        )

        val selectedContrast =
          ChoiceImageRegistry.contrastRatio(selected.contentColor, selected.containerColor)
        assertTrue(
          selectedContrast >= 4.5f,
          "Expected >= 4.5 contrast for $hex selected in $themeName, got $selectedContrast",
        )

        val swatchContrast =
          ChoiceImageRegistry.contrastRatio(selected.onSwatchColor, selected.swatchColor)
        assertTrue(
          swatchContrast >= 4.5f,
          "Expected >= 4.5 contrast on swatch for $hex in $themeName, got $swatchContrast",
        )
      }
    }
  }

  @Test
  fun resolveChoiceAnswerColors_resolvesCustomFormDefAndBuiltInChoices() {
    val customForm =
      FormDef(
        form_id = "custom",
        title = "Custom Land Use",
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    fields =
                      listOf(
                        FieldDefinition(
                          name = "canopy_status",
                          type = DataType.TYPE_STRING,
                        )
                      ),
                  )
              ),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "/data/canopy_status",
                  type = DataType.TYPE_STRING,
                  entity_saveto = "canopy_status_prop",
                )
              ),
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/canopy_status",
                      type = ControlType.CONTROL_SELECT_ONE,
                      label = LabelDef(text = "Canopy status"),
                      choices =
                        listOf(
                          ChoiceItem(
                            value_ = "dense",
                            label = LabelDef(text = "Dense canopy"),
                            properties = mapOf("color" to "#1B5E20"),
                          ),
                          ChoiceItem(
                            value_ = "sparse",
                            label = LabelDef(text = "Sparse canopy"),
                            properties = mapOf("color" to "#F9A825"),
                          ),
                        ),
                    )
                )
              )
          ),
      )

    // Lookup by field name
    val byField =
      ChoiceImageRegistry.resolveChoiceAnswerColors(
        questionName = "canopy_status",
        answerValue = "dense",
        formDefs = listOf(customForm),
      )
    assertEquals(1, byField.size)
    assertEquals("dense", byField.single().label)
    assertEquals(Color(0xFF1B5E20), byField.single().color)

    // Lookup by entity_saveto property name with "code (Label)" formatting
    val bySaveTo =
      ChoiceImageRegistry.resolveChoiceAnswerColors(
        questionName = "canopy_status_prop",
        answerValue = "sparse (Sparse canopy)",
        formDefs = listOf(customForm),
      )
    assertEquals(1, bySaveTo.size)
    assertEquals("sparse (Sparse canopy)", bySaveTo.single().label)
    assertEquals(Color(0xFFF9A825), bySaveTo.single().color)

    // Lookup via built-in IPCC land use fallback when no FormDef is provided
    val builtIn =
      ChoiceImageRegistry.resolveChoiceAnswerColors(
        questionName = "ipcc_land_use",
        answerValue = "forest_land (Forest land)",
      )
    assertEquals(1, builtIn.size)
    assertEquals("forest_land (Forest land)", builtIn.single().label)
    assertEquals(Color(0xFF3C8D40), builtIn.single().color)
  }

  @Test
  fun selectOneAndCompactRunner_renderProminentColorStripeSwatchAndCollapsedPill() =
    runComposeUiTest {
      val formDef =
        FormDef(
          form_id = "color_form",
          title = "Color Form",
          model =
            ModelDef(
              primary_instance =
                PrimaryInstance(
                  record_schema =
                    RecordSchema(
                      name = "data",
                      fields =
                        listOf(
                          FieldDefinition(name = "land_use", type = DataType.TYPE_STRING),
                          FieldDefinition(name = "notes", type = DataType.TYPE_STRING),
                        ),
                    )
                ),
              bindings =
                listOf(
                  FieldBinding(field_path = "/data/land_use", type = DataType.TYPE_STRING),
                  FieldBinding(field_path = "/data/notes", type = DataType.TYPE_STRING),
                ),
            ),
          view =
            ViewDef(
              components =
                listOf(
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/land_use",
                        type = ControlType.CONTROL_SELECT_ONE,
                        label = LabelDef(text = "Land use class"),
                        choices =
                          listOf(
                            ChoiceItem(
                              value_ = "forest",
                              label = LabelDef(text = "Forest land"),
                              properties = mapOf("color" to "#1B5E20"),
                            ),
                            ChoiceItem(
                              value_ = "cropland",
                              label = LabelDef(text = "Annual cropland"),
                              properties = mapOf("color" to "#FBC02D"),
                            ),
                            ChoiceItem(
                              value_ = "uncolored",
                              label = LabelDef(text = "Uncolored option"),
                            ),
                          ),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/notes",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Field notes"),
                      )
                  ),
                )
            ),
        )
      val controller = FormWizardController(formDef)

      setContent {
        MaterialTheme {
          CompactFormRunner(
            controller = controller,
            modifier = Modifier.verticalScroll(rememberScrollState()),
          )
        }
      }

      // Colored options render their prominent left accent stripe and rounded swatch badge
      onNodeWithTag("choice-color-stripe-forest", useUnmergedTree = true).assertExists()
      onNodeWithTag("choice-color-swatch-forest", useUnmergedTree = true).assertExists()
      onNodeWithTag("choice-color-stripe-cropland", useUnmergedTree = true).assertExists()
      onNodeWithTag("choice-color-swatch-cropland", useUnmergedTree = true).assertExists()
      // Uncolored option does not render color stripe or swatch
      onNodeWithTag("choice-color-stripe-uncolored", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithTag("choice-color-swatch-uncolored", useUnmergedTree = true).assertDoesNotExist()

      // Select "Forest land" and collapse the question by focusing the next question
      onNodeWithText("Forest land").performClick()
      waitForIdle()
      val selectedColors =
        ChoiceImageRegistry.resolveSelectedOptionColors(
          options =
            listOf(
              ResolvedChoiceOption(
                value = "forest",
                label = ResolvedLabel("Forest land"),
                properties = mapOf("color" to "#1B5E20"),
              )
            ),
          selectedValues = setOf("forest"),
        )
      assertEquals(1, selectedColors.size)
    }
}
