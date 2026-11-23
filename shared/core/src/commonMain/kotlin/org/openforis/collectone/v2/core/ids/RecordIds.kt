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
package org.openforis.collectone.v2.core.ids

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.openforis.collectone.v2.core.geo.geoid.Sha256

/**
 * IDs of the records clients create, often offline: surveys, organizations, map features
 * (entities), submissions, and impact events. They're canonical RFC 4122 UUIDs, so clients never
 * wait for the server to assign one and Spanner's `UUID`-validated key columns accept every record
 * (`docs/technical/backend/spanner-schema.md`, "Client-Generated UUID Keys").
 */
@OptIn(ExperimentalUuidApi::class)
object RecordIds {
  private val CANONICAL_UUID_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  /** A new random record ID in canonical lowercase form. */
  fun newId(): String = Uuid.random().toString()

  /** Whether [value] is a canonical 36-character hyphenated UUID (`8-4-4-4-12`). */
  fun isUuid(value: String): Boolean = CANONICAL_UUID_REGEX.matches(value.trim())

  /**
   * Deterministic RFC 4122 version-5-shaped UUID derived from [namespace] and [name] via SHA-256,
   * identical across JVM, WasmJS, Android, and iOS. Used when projecting human-readable editor keys
   * (such as generated sample plot IDs like `P001` or imported row keys) into Spanner entity IDs.
   */
  fun deterministicId(namespace: String, name: String): String {
    val digest = Sha256().update("$namespace\u0000$name".encodeToByteArray()).digest()
    val bytes = digest.copyOf(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    return Uuid.fromByteArray(bytes).toString()
  }

  /**
   * Returns [candidate] trimmed when it is already a canonical UUID, or a [deterministicId] under
   * [namespace] otherwise.
   */
  fun uuidOrDeterministic(candidate: String, namespace: String): String {
    val trimmed = candidate.trim()
    return if (isUuid(trimmed)) trimmed else deterministicId(namespace, trimmed)
  }

  /** Computes the 32-byte SHA-256 digest of [bytes] across all Kotlin Multiplatform targets. */
  fun sha256(bytes: ByteArray): ByteArray = Sha256().update(bytes).digest()
}
