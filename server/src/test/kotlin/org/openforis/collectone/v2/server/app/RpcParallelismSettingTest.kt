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
package org.openforis.collectone.v2.server.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.openforis.collectone.v2.server.service.sampleBackend

/** How many RPC handlers run at once in one instance ([rpcParallelism]). */
class RpcParallelismSettingTest {
  private fun env(vararg values: Pair<String, String>): (String) -> String? = values.toMap()::get

  @Test
  fun unset_usesTheDefault() {
    assertEquals(DEFAULT_RPC_PARALLELISM, rpcParallelism(env()))
  }

  @Test
  fun wholeNumberInRange_isUsed() {
    assertEquals(3, rpcParallelism(env("RPC_PARALLELISM" to " 3 ")))
  }

  @Test
  fun bounds_areAccepted() {
    assertEquals(1, rpcParallelism(env("RPC_PARALLELISM" to "1")))
    assertEquals(MAX_RPC_PARALLELISM, rpcParallelism(env("RPC_PARALLELISM" to "64")))
  }

  @Test
  fun zeroNegativeTooLargeOrNotANumber_fallBackToTheDefault() {
    for (value in listOf("0", "-2", "65", "eight", "")) {
      assertEquals(DEFAULT_RPC_PARALLELISM, rpcParallelism(env("RPC_PARALLELISM" to value)), value)
    }
  }

  @Test
  fun server_rejectsAParallelismOutOfRange() {
    assertFailsWith<IllegalArgumentException> {
      UserDataServer.create(sampleBackend(), port = 0, rpcParallelism = 0)
    }
  }
}
