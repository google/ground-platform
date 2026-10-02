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
package org.groundplatform.v2.core.geo.geoid

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

  private fun sha256(input: String): String = Sha256().update(input.encodeToByteArray()).hexDigest()

  @Test
  fun digest_matchesFips180TestVectors() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      sha256(""),
    )
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      sha256("abc"),
    )
    assertEquals(
      "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
      sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
    )
  }

  @Test
  fun digest_ofOneMillionAs_matchesFips180TestVector() {
    val sha = Sha256()
    val chunk = ByteArray(1000) { 'a'.code.toByte() }
    repeat(1000) { sha.update(chunk) }

    assertEquals(
      "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
      sha.hexDigest(),
    )
  }

  @Test
  fun digest_isIndependentOfUpdateChunking() {
    val message = "The quick brown fox jumps over the lazy dog".repeat(5)
    val chunked = Sha256()
    message.chunked(7).forEach { chunked.update(it.encodeToByteArray()) }

    assertEquals(sha256(message), chunked.hexDigest())
  }

  @Test
  fun digest_handlesPaddingBoundaries() {
    // 55, 56 and 64 bytes straddle the single/double padding block boundary.
    assertEquals(
      "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
      sha256("a".repeat(55)),
    )
    assertEquals(
      "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
      sha256("a".repeat(56)),
    )
    assertEquals(
      "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
      sha256("a".repeat(64)),
    )
  }
}
