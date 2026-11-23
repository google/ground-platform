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
package org.openforis.collectone.v2.server.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedAreaBackfillSettingTest {
  private fun envOf(value: String?): (String) -> String? = { name ->
    if (name == "SEED_AREA_BACKFILL_ENABLED") value else null
  }

  @Test
  fun unset_runsTheBackfill() {
    assertTrue(isSeedAreaBackfillEnabled(envOf(null)))
  }

  @Test
  fun false_skipsTheBackfill() {
    assertFalse(isSeedAreaBackfillEnabled(envOf("false")))
  }

  @Test
  fun true_runsTheBackfill() {
    assertTrue(isSeedAreaBackfillEnabled(envOf(" TRUE ")))
  }

  @Test
  fun anInvalidValue_runsTheBackfill() {
    assertTrue(isSeedAreaBackfillEnabled(envOf("maybe")))
  }
}
