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

/**
 * Streaming SHA-256 (FIPS 180-4) in pure Kotlin, so GeoIDs can be computed identically on every
 * Kotlin Multiplatform target without platform crypto APIs.
 */
internal class Sha256 {
  private val state = INITIAL_STATE.copyOf()
  private val block = ByteArray(BLOCK_SIZE)
  private val schedule = IntArray(64)
  private var blockLength = 0
  private var totalBytes = 0L
  private var finished = false

  fun update(bytes: ByteArray): Sha256 {
    check(!finished) { "digest() already called" }
    for (b in bytes) {
      block[blockLength++] = b
      if (blockLength == BLOCK_SIZE) {
        processBlock()
        blockLength = 0
      }
    }
    totalBytes += bytes.size
    return this
  }

  fun digest(): ByteArray {
    check(!finished) { "digest() already called" }
    finished = true
    val bitLength = totalBytes * 8
    block[blockLength++] = 0x80.toByte()
    if (blockLength > BLOCK_SIZE - 8) {
      block.fill(0, blockLength, BLOCK_SIZE)
      processBlock()
      blockLength = 0
    }
    block.fill(0, blockLength, BLOCK_SIZE - 8)
    for (k in 0 until 8) {
      block[BLOCK_SIZE - 1 - k] = (bitLength ushr (8 * k)).toByte()
    }
    processBlock()
    val out = ByteArray(32)
    for (k in 0 until 8) {
      val word = state[k]
      out[4 * k] = (word ushr 24).toByte()
      out[4 * k + 1] = (word ushr 16).toByte()
      out[4 * k + 2] = (word ushr 8).toByte()
      out[4 * k + 3] = word.toByte()
    }
    return out
  }

  /** Lowercase hexadecimal digest, matching Python's `hashlib.sha256().hexdigest()`. */
  fun hexDigest(): String {
    val digest = digest()
    val chars = CharArray(digest.size * 2)
    for ((index, b) in digest.withIndex()) {
      val v = b.toInt() and 0xff
      chars[2 * index] = HEX[v ushr 4]
      chars[2 * index + 1] = HEX[v and 0x0f]
    }
    return chars.concatToString()
  }

  private fun processBlock() {
    val w = schedule
    for (t in 0 until 16) {
      w[t] =
        ((block[4 * t].toInt() and 0xff) shl 24) or
          ((block[4 * t + 1].toInt() and 0xff) shl 16) or
          ((block[4 * t + 2].toInt() and 0xff) shl 8) or
          (block[4 * t + 3].toInt() and 0xff)
    }
    for (t in 16 until 64) {
      val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
      val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
      w[t] = w[t - 16] + s0 + w[t - 7] + s1
    }
    var a = state[0]
    var b = state[1]
    var c = state[2]
    var d = state[3]
    var e = state[4]
    var f = state[5]
    var g = state[6]
    var h = state[7]
    for (t in 0 until 64) {
      val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
      val ch = (e and f) xor (e.inv() and g)
      val temp1 = h + s1 + ch + K[t] + w[t]
      val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
      val maj = (a and b) xor (a and c) xor (b and c)
      val temp2 = s0 + maj
      h = g
      g = f
      f = e
      e = d + temp1
      d = c
      c = b
      b = a
      a = temp1 + temp2
    }
    state[0] += a
    state[1] += b
    state[2] += c
    state[3] += d
    state[4] += e
    state[5] += f
    state[6] += g
    state[7] += h
  }

  private companion object {
    const val BLOCK_SIZE = 64
    val HEX = "0123456789abcdef".toCharArray()

    val INITIAL_STATE =
      intArrayOf(
        0x6a09e667,
        -0x4498517b, // 0xbb67ae85
        0x3c6ef372,
        -0x5ab00ac6, // 0xa54ff53a
        0x510e527f,
        -0x64fa9774, // 0x9b05688c
        0x1f83d9ab,
        0x5be0cd19,
      )

    val K =
      longArrayOf(
          0x428a2f98,
          0x71374491,
          0xb5c0fbcf,
          0xe9b5dba5,
          0x3956c25b,
          0x59f111f1,
          0x923f82a4,
          0xab1c5ed5,
          0xd807aa98,
          0x12835b01,
          0x243185be,
          0x550c7dc3,
          0x72be5d74,
          0x80deb1fe,
          0x9bdc06a7,
          0xc19bf174,
          0xe49b69c1,
          0xefbe4786,
          0x0fc19dc6,
          0x240ca1cc,
          0x2de92c6f,
          0x4a7484aa,
          0x5cb0a9dc,
          0x76f988da,
          0x983e5152,
          0xa831c66d,
          0xb00327c8,
          0xbf597fc7,
          0xc6e00bf3,
          0xd5a79147,
          0x06ca6351,
          0x14292967,
          0x27b70a85,
          0x2e1b2138,
          0x4d2c6dfc,
          0x53380d13,
          0x650a7354,
          0x766a0abb,
          0x81c2c92e,
          0x92722c85,
          0xa2bfe8a1,
          0xa81a664b,
          0xc24b8b70,
          0xc76c51a3,
          0xd192e819,
          0xd6990624,
          0xf40e3585,
          0x106aa070,
          0x19a4c116,
          0x1e376c08,
          0x2748774c,
          0x34b0bcb5,
          0x391c0cb3,
          0x4ed8aa4a,
          0x5b9cca4f,
          0x682e6ff3,
          0x748f82ee,
          0x78a5636f,
          0x84c87814,
          0x8cc70208,
          0x90befffa,
          0xa4506ceb,
          0xbef9a3f7,
          0xc67178f2,
        )
        .let { longs -> IntArray(longs.size) { longs[it].toInt() } }
  }
}
