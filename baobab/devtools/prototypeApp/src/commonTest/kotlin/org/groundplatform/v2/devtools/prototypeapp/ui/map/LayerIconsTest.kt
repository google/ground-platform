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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import androidx.compose.ui.graphics.vector.PathNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeMapLayersData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples

class LayerIconsTest {
  @Test
  fun library_hasUniqueNamesAndConvertsEveryIconToSvg() {
    val names = LayerIcons.all.map { it.name }
    assertEquals(names.toSet().size, names.size)
    assertTrue(names.size in 24..40)
    LayerIcons.all.forEach { icon ->
      val svg = LayerIcons.svgPathElements(icon, "#FFFFFF")
      assertTrue(svg.startsWith("<path d=\"M") || svg.startsWith("<path d=\"m"), icon.name)
      assertFalse(svg.contains("E-"), "${icon.name} has an exponent")
    }
  }

  @Test
  fun forName_returnsNullForBlankOrUnknownNames() {
    assertEquals("park", LayerIcons.forName("park")?.name)
    assertNull(LayerIcons.forName(null))
    assertNull(LayerIcons.forName("not-an-icon"))
  }

  @Test
  fun svgPathData_formatsEveryCommand() {
    val data =
      LayerIcons.svgPathData(
        listOf(
          PathNode.MoveTo(1f, 2f),
          PathNode.RelativeLineTo(-0.5f, 0.25f),
          PathNode.HorizontalTo(3f),
          PathNode.RelativeVerticalTo(1.0004f),
          PathNode.RelativeArcTo(2f, 2f, 0f, false, true, 4f, 0f),
          PathNode.Close,
        )
      )
    assertEquals("M1 2 l-0.5 0.25 H3 v1 a2 2 0 0 1 4 0 Z", data)
  }

  @Test
  fun seedLayers_useDistinctColorsAndKnownIcons() {
    val layers = PrototypeFakeMapLayersData.defaultMapLayers()
    assertEquals(layers.size, layers.map { it.colorHex }.toSet().size)
    layers.forEach { assertTrue(LayerIcons.forName(it.iconName) != null, it.id) }
    SurveyEditorSamples.draft()
      .datasets
      .mapNotNull { it.style.iconName }
      .forEach {
        assertTrue(LayerIcons.forName(it) != null, it)
      }
  }

  @Test
  fun pinIconName_isOnlyShownForPointLayers() {
    val layers = PrototypeFakeMapLayersData.defaultMapLayers()
    layers.forEach { layer ->
      if (layer.geometryTypeLabel == "Point") {
        assertEquals(layer.iconName, layer.pinIconName, layer.id)
      } else {
        assertNull(layer.pinIconName, layer.id)
      }
    }
    assertNull(layers.first().copy(geometryTypeLabel = "Polygon", iconName = "park").pinIconName)
  }

  @Test
  fun contentColor_isDarkOnlyOnLightBackgrounds() {
    assertEquals(0xFFFFFFFFL, contentColorOnArgb(0xFF1565C0))
    assertEquals(0xFF1F1F1FL, contentColorOnArgb(0xFFFFEB3B))
  }
}
