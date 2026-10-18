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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormDensityTest {

  @Test
  fun numericSteppers_shownOnlyInComfortableDensity() {
    // The mobile runner keeps the -1/+1 and -0.5/+0.5 buttons; the compact web layout drops them
    // and gives the text field the full width.
    assertTrue(showNumericSteppers(FormDensity.COMFORTABLE))
    assertFalse(showNumericSteppers(FormDensity.COMPACT))
  }

  @Test
  fun select_picksTheValueForTheDensity() {
    assertEquals(6.dp, FormDensity.COMPACT.select(compact = 6.dp, comfortable = 8.dp))
    assertEquals(8.dp, FormDensity.COMFORTABLE.select(compact = 6.dp, comfortable = 8.dp))
    assertEquals("dense", FormDensity.COMPACT.select(compact = "dense", comfortable = "roomy"))
  }

  @Test
  fun compactTextField_isOnlySlightlyShorterThanMaterialDefault() {
    // Material's OutlinedTextField minimum is 56.dp; the compact field trims it a touch, no more.
    assertEquals(48.dp, COMPACT_TEXT_FIELD_MIN_HEIGHT)
    assertTrue(COMPACT_TEXT_FIELD_MIN_HEIGHT < 56.dp)
    assertTrue(COMPACT_TEXT_FIELD_MIN_HEIGHT >= 40.dp)
  }
}
