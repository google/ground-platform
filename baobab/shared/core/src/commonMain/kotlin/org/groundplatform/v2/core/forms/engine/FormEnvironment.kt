/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.engine

import kotlin.random.Random
import org.groundplatform.v2.core.forms.xpath.EvaluationContext
import org.groundplatform.v2.core.forms.xpath.SecondaryInstanceProvider
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Runtime environment hooks supplied to [FormEngine] for preload evaluation, secondary dataset
 * resolution, deterministic clocks, and UUID/PRNG generation.
 */
data class FormEnvironment(
  /** Supplier for current UTC epoch milliseconds (`today()`, `now()`, `PRELOAD_TIMESTAMP`). */
  val clockEpochMillis: () -> Long = { EvaluationContext.DEFAULT_EPOCH_MILLIS },
  /** Optional deterministic seed for `random()`, `uuid()`, and `ItemsetDef.randomize`. */
  val randomSeed: Long? = null,
  /**
   * Generator for raw RFC 4122 v4 UUID strings (e.g., `"550e8400-e29b-41d4-a716-446655440000"`).
   * `PRELOAD_UID` automatically prefixes `"uuid:"` if not already present.
   */
  val uuidGenerator: () -> String = { generateRfc4122Uuid(randomSeed) },
  /**
   * Client device and user properties for `PRELOAD_PROPERTY` bindings (e.g., `"deviceid"`,
   * `"email"`, `"username"`, `"phonenumber"`, `"simserial"`, `"subscriberid"`).
   */
  val deviceProperties: Map<String, String> = emptyMap(),
  /** Session context parameters for `PRELOAD_CONTEXT` bindings. */
  val contextParams: Map<String, String> = emptyMap(),
  /**
   * Optional custom secondary dataset provider for `instance('id')`, `pulldata()`, and dynamic
   * `ItemsetDef` queries. If `null`, inline datasets from `FormDef.model.secondary_instances` are
   * used automatically.
   */
  val secondaryInstanceProvider: SecondaryInstanceProvider? = null,
  /** Additional named variables accessible in XPath expressions via `$var`. */
  val variables: Map<String, XPathValue> = emptyMap(),
) {
  /** Resolves a device property case-insensitively (supporting aliases like `device_id`). */
  fun getDeviceProperty(param: String): String? {
    val normalized = param.trim().lowercase().replace("_", "")
    for ((k, v) in deviceProperties) {
      if (k.trim().lowercase().replace("_", "") == normalized) {
        return v
      }
    }
    return null
  }

  companion object {
    /** Default pure-KMP deterministic environment. */
    val DEFAULT = FormEnvironment()

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    internal fun generateRfc4122Uuid(seed: Long? = null): String {
      val rng = if (seed != null) Random(seed) else Random.Default
      val bytes = ByteArray(16)
      rng.nextBytes(bytes)
      // Set version to 4 (0100xxxx)
      bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
      // Set variant to RFC 4122 (10xxxxxx)
      bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()

      val sb = StringBuilder(36)
      for (i in 0 until 16) {
        if (i == 4 || i == 6 || i == 8 || i == 10) {
          sb.append('-')
        }
        val b = bytes[i].toInt() and 0xff
        sb.append(HEX_CHARS[b ushr 4])
        sb.append(HEX_CHARS[b and 0x0f])
      }
      return sb.toString()
    }
  }
}
