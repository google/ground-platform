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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlAppearanceTest {

  @Test
  fun appearanceTokens_splitsOnAnyWhitespaceAndLowercases() {
    assertEquals(setOf("likert", "quick"), appearanceTokens(" Likert \t quick\n"))
  }

  @Test
  fun appearanceTokens_returnsEmptySetForBlankAppearance() {
    assertEquals(emptySet(), appearanceTokens("   "))
  }

  @Test
  fun isMapPanAllowed_acceptsEveryMapPlacementToken() {
    assertTrue(isMapPanAllowed("placement-map"))
    assertTrue(isMapPanAllowed("map"))
    assertTrue(isMapPanAllowed("maps"))
    assertTrue(isMapPanAllowed("walk-or-draw"))
  }

  @Test
  fun isMapPanAllowed_ignoresCaseAndOtherTokens() {
    assertTrue(isMapPanAllowed("minimal Placement-Map"))
  }

  @Test
  fun isMapPanAllowed_isFalseWithoutAMapPlacementToken() {
    assertFalse(isMapPanAllowed(""))
    assertFalse(isMapPanAllowed("minimal"))
    assertFalse(isMapPanAllowed("walk"))
  }
}
