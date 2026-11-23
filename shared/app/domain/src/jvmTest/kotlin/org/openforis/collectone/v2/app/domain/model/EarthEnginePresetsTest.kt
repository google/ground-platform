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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EarthEnginePresetsTest {
  @Test
  fun presets_containsAllSixCeoPresets() {
    val presets = EarthEnginePresets.presets
    assertEquals(6, presets.size)

    val expectedIds =
      setOf(
        "imagery-all-users-ee-s2-true-color",
        "imagery-all-users-ee-s2-false-color-ir",
        "imagery-all-users-ee-s2-agriculture",
        "imagery-all-users-ee-s2-ndvi",
        "imagery-all-users-ee-s1-sar",
        "imagery-all-users-ee-dynamic-world",
      )
    assertEquals(expectedIds, presets.map { it.id }.toSet())
  }

  @Test
  fun presets_allHaveEarthEngineTypeAndValidExpression() {
    for (preset in EarthEnginePresets.presets) {
      assertEquals(ImagerySourceType.EARTH_ENGINE, preset.type)
      assertNotNull(preset.expression, "Preset ${preset.id} missing expression")
      val expr = preset.expression!!
      assertTrue(expr.values.isNotEmpty(), "Preset ${preset.id} has empty values")
      assertTrue(expr.result.isNotBlank(), "Preset ${preset.id} has blank result")
      assertTrue(
        expr.values.containsKey(expr.result),
        "Preset ${preset.id} result '${expr.result}' not found in values",
      )
    }
  }

  @Test
  fun presets_allConstantNumbersAreDouble() {
    for (preset in EarthEnginePresets.presets) {
      val expr = preset.expression!!
      for ((key, node) in expr.values) {
        checkConstantValueNumbers(node.constant_value, "${preset.id}.$key")
      }
    }
  }

  private fun checkConstantValueNumbers(value: Any?, path: String) {
    when (value) {
      is Number -> {
        assertTrue(
          value is Double,
          "Expected Double number at $path, but found ${value::class.simpleName}: $value",
        )
      }
      is List<*> -> {
        value.forEachIndexed { i, elem -> checkConstantValueNumbers(elem, "$path[$i]") }
      }
      is Map<*, *> -> {
        value.forEach { (k, v) -> checkConstantValueNumbers(v, "$path.$k") }
      }
    }
  }
}
