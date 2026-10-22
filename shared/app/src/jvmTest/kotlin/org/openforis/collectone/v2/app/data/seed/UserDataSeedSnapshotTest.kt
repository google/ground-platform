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
package org.openforis.collectone.v2.app.data.seed

import com.squareup.moshi.Moshi
import com.squareup.wire.WireJsonAdapterFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.userdata.UserDataSeed

/**
 * Keeps the user data service's sample seed
 * (`server/userDataService/src/main/resources/seed/user_data_seed.json`, proto JSON) identical to
 * the seed built from the Kotlin fixtures by [UserDataSeedGenerator].
 *
 * Run `./gradlew jvmTest -PregenerateUserDataSeed` to rewrite the snapshot after changing the
 * fixtures (and bump `SampleDataSeeder.SEED_VERSION` when the change should reach existing stores).
 */
class UserDataSeedSnapshotTest {
  private val snapshot =
    File("../../server/userDataService/src/main/resources/seed/user_data_seed.json")
  private val regenerate = System.getProperty("regenerateUserDataSeed") != null
  private val json =
    Moshi.Builder().add(WireJsonAdapterFactory()).build().adapter(UserDataSeed::class.java)

  @Test
  fun snapshot_matchesTheFixtures() {
    val expected = UserDataSeedGenerator.generate()
    if (regenerate) {
      snapshot.parentFile.mkdirs()
      snapshot.writeText(json.indent("  ").toJson(expected) + "\n")
    }
    assertTrue(
      snapshot.isFile,
      "Missing ${snapshot.path}; run `./gradlew jvmTest -PregenerateUserDataSeed`",
    )
    assertEquals(
      expected,
      json.fromJson(snapshot.readText()),
      "The user data seed is out of date; run `./gradlew jvmTest -PregenerateUserDataSeed`",
    )
  }
}
