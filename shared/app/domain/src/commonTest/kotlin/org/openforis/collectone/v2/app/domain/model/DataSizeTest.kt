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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSizeTest {
  @Test
  fun arithmetic_worksInBytes() {
    assertEquals(DataSize(30), DataSize(10) + DataSize(20))
    assertEquals(DataSize(5), DataSize(20) - DataSize(15))
    assertEquals(DataSize(120), DataSize(100) * 1.2)
    assertEquals(DataSize(2), DataSize(3) * 0.5) // Rounds half up to the nearest byte.
  }

  @Test
  fun sizes_compareByBytes() {
    assertTrue(DataSize(1) > DataSize.ZERO)
    assertEquals(listOf(DataSize(1), DataSize(2)), listOf(DataSize(2), DataSize(1)).sorted())
  }

  @Test
  fun megabytes_areDecimal() {
    assertEquals(DataSize(500_000_000), DataSize.ofMegabytes(500))
  }

  @Test
  fun sum_addsSizes_andIsZeroForNone() {
    assertEquals(DataSize(6), listOf(DataSize(1), DataSize(2), DataSize(3)).sum())
    assertEquals(DataSize.ZERO, emptyList<DataSize>().sum())
  }
}
