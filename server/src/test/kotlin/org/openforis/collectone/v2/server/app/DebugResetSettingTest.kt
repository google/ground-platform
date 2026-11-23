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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the server serves the unauthenticated `POST /debug/reset` ([debugResetEnabled]). */
class DebugResetSettingTest {
  private fun env(vararg values: Pair<String, String>): (String) -> String? = values.toMap()::get

  @Test
  fun localDevRun_servesReset() {
    assertTrue(debugResetEnabled(env(), authMode = "dev"))
  }

  @Test
  fun devModeOnCloudRun_doesNotServeReset() {
    assertFalse(debugResetEnabled(env("K_SERVICE" to "c1-server"), authMode = "dev"))
  }

  @Test
  fun firebaseMode_doesNotServeReset() {
    assertFalse(debugResetEnabled(env(), authMode = "firebase"))
  }

  @Test
  fun explicitTrue_servesResetOnCloudRun() {
    val env = env("K_SERVICE" to "c1-server", "DEBUG_RESET_ENABLED" to "true")

    assertTrue(debugResetEnabled(env, authMode = "dev"))
  }

  @Test
  fun explicitFalse_turnsResetOffForALocalDevRun() {
    assertFalse(debugResetEnabled(env("DEBUG_RESET_ENABLED" to "FALSE"), authMode = "dev"))
  }

  @Test
  fun unrecognizedValue_fallsBackToTheDefault() {
    val env = env("K_SERVICE" to "c1-server", "DEBUG_RESET_ENABLED" to "yes")

    assertFalse(debugResetEnabled(env, authMode = "dev"))
  }
}
