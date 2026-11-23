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
package org.openforis.collectone.v2.app.domain.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainResultTest {
  private val success: DomainResult<Int> = DomainResult.Success(2)
  private val failure: DomainResult<Int> = DomainResult.Failure(DomainError.NotFound)

  @Test
  fun getOrElse_returnsTheValueOfASuccess() {
    assertEquals(2, success.getOrElse { 0 })
  }

  @Test
  fun getOrElse_passesTheErrorOfAFailureToTheFallback() {
    var seen: DomainError? = null

    val value = failure.getOrElse { error ->
      seen = error
      0
    }

    assertEquals(0, value)
    assertEquals(DomainError.NotFound, seen)
  }

  @Test
  fun getOrNull_returnsTheValueOrNull() {
    assertEquals(2, success.getOrNull())
    assertNull(failure.getOrNull())
  }

  @Test
  fun map_transformsSuccessesOnly() {
    assertEquals(DomainResult.Success("2"), success.map { it.toString() })
    assertEquals(DomainResult.Failure(DomainError.NotFound), failure.map { it.toString() })
  }
}
