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
package org.groundplatform.v2.core.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.impact.ALL_USERS_ORGANIZATION_ID

class RecordIdsTest {
  private val uuidV4 =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

  @Test
  fun newId_isARandomLowercaseVersion4Uuid() {
    val ids = List(100) { RecordIds.newId() }

    assertTrue(ids.all { uuidV4.matches(it) }, ids.first())
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun allUsersOrganizationId_isAReservedUuid() {
    assertTrue(uuidV4.matches(ALL_USERS_ORGANIZATION_ID))
  }
}
