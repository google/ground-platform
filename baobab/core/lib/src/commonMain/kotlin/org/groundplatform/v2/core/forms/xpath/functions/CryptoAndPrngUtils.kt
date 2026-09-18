/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.xpath.functions

import kotlin.math.abs
import kotlin.random.Random
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * Pure Kotlin Multiplatform cryptographic digest (`digest()`), Base64 (`base64-decode()`), UUID
 * (`uuid()`), and Park-Miller seeded PRNG (`randomize()`) utilities.
 */
internal object CryptoAndPrngUtils {

  private const val ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

  /**
   * Computes a cryptographic digest for [payload] using [algorithm] (`MD5`, `SHA-1`, `SHA-256`,
   * `SHA-384`, `SHA-512`) and formats as `hex` (default) or `base64`.
   */
  fun digest(payload: String, algorithm: String, encoding: String = "hex"): String {
    val bytes = payload.encodeUtf8()
    val digestBytes: ByteString =
      when (algorithm.uppercase().replace("-", "")) {
        "MD5" -> bytes.md5()
        "SHA1" -> bytes.sha1()
        "SHA256" -> bytes.sha256()
        "SHA384" -> sha384(bytes.toByteArray()).toByteString()
        "SHA512" -> bytes.sha512()
        else -> throw IllegalArgumentException("Unsupported digest algorithm: $algorithm")
      }
    return when (encoding.lowercase()) {
      "base64" -> digestBytes.base64()
      "hex",
      "" -> digestBytes.hex()
      else -> throw IllegalArgumentException("Unsupported digest encoding: $encoding")
    }
  }

  /** Decodes a Base64 string into UTF-8 text. */
  fun base64Decode(encoded: String): String {
    val decoded = encoded.trim().decodeBase64() ?: return ""
    return decoded.utf8()
  }

  /**
   * Generates an RFC 4122 v4 UUID when [length] is null, or a random alphanumeric string of
   * [length] characters.
   */
  fun generateUuidOrToken(length: Int? = null, seed: Long? = null): String {
    val random = if (seed != null) Random(seed) else Random.Default
    if (length != null) {
      if (length <= 0) return ""
      val sb = StringBuilder(length)
      for (i in 0 until length) {
        sb.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)])
      }
      return sb.toString()
    }
    val randomBytes = ByteArray(16)
    random.nextBytes(randomBytes)
    // RFC 4122 version 4 & variant 1
    randomBytes[6] = ((randomBytes[6].toInt() and 0x0F) or 0x40).toByte()
    randomBytes[8] = ((randomBytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = randomBytes.toByteString().hex()
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
  }

  /**
   * Permutes [items] using the ODK/JavaRosa inside-out Fisher-Yates shuffle. When [seedStr] is
   * provided, uses a deterministic Park-Miller PRNG. Non-numeric seeds are hashed via SHA-256 to a
   * 64-bit big-endian integer first.
   */
  fun <T> randomizeList(items: List<T>, seedStr: String?, fallbackSeed: Long? = null): List<T> {
    if (items.size <= 1) return items
    val seedLong: Long =
      if (seedStr != null) {
        val numeric = seedStr.trim().toDoubleOrNull()
        if (numeric != null && !numeric.isNaN()) {
          numeric.toLong()
        } else {
          // Hash non-numeric seed via SHA-256 to 64-bit signed big-endian integer
          val hash = seedStr.encodeUtf8().sha256().toByteArray()
          var acc = 0L
          for (i in 0 until 8) {
            acc = (acc shl 8) or (hash[i].toLong() and 0xFFL)
          }
          acc
        }
      } else {
        fallbackSeed ?: Random.Default.nextLong()
      }

    // Park-Miller Minimal Standard PRNG: X_{n+1} = (16807 * X_n) mod 2147483647
    var state = abs(seedLong) % 2147483647L
    if (state == 0L) state = 1L

    fun nextParkMillerDouble(): Double {
      state = (state * 16807L) % 2147483647L
      return (state - 1L).toDouble() / 2147483646.0
    }

    // Inside-out Fisher-Yates shuffle
    val result = MutableList(items.size) { items[0] }
    for (i in items.indices) {
      val j =
        if (i == 0) {
          0
        } else {
          (nextParkMillerDouble() * (i + 1)).toInt().coerceIn(0, i)
        }
      if (j != i) {
        result[i] = result[j]
      }
      result[j] = items[i]
    }
    return result
  }

  // Pure Kotlin SHA-384 implementation (FIPS 180-4)
  private val SHA384_IV =
    longArrayOf(
      0xcbbb9d5dc1059ed8uL.toLong(),
      0x629a292a367cd507uL.toLong(),
      0x9159015a3070dd17uL.toLong(),
      0x152fecd8f70e5939uL.toLong(),
      0x67332667ffc00b31uL.toLong(),
      0x8eb44a8768581511uL.toLong(),
      0xdb0c2e0d64f98fa7uL.toLong(),
      0x47b5481dbefa4fa4uL.toLong(),
    )

  private val SHA512_K =
    longArrayOf(
      0x428a2f98d728ae22uL.toLong(),
      0x7137449123ef65cduL.toLong(),
      0xb5c0fbcfec4d3b2fuL.toLong(),
      0xe9b5dba58189dbbcuL.toLong(),
      0x3956c25bf348b538uL.toLong(),
      0x59f111f1b605d019uL.toLong(),
      0x923f82a4af194f9buL.toLong(),
      0xab1c5ed5da6d8118uL.toLong(),
      0xd807aa98a3030242uL.toLong(),
      0x12835b0145706fbeuL.toLong(),
      0x243185be4ee4b28cuL.toLong(),
      0x550c7dc3d5ffb4e2uL.toLong(),
      0x72be5d74f27b896fuL.toLong(),
      0x80deb1fe3b1696b1uL.toLong(),
      0x9bdc06a725c71235uL.toLong(),
      0xc19bf174cf692694uL.toLong(),
      0xe49b69c19ef14ad2uL.toLong(),
      0xefbe4786384f25e3uL.toLong(),
      0x0fc19dc68b8cd5b5uL.toLong(),
      0x240ca1cc77ac9c65uL.toLong(),
      0x2de92c6f592b0275uL.toLong(),
      0x4a7484aa6ea6e483uL.toLong(),
      0x5cb0a9dcbd41fbd4uL.toLong(),
      0x76f988da831153b5uL.toLong(),
      0x983e5152ee66dfabuL.toLong(),
      0xa831c66d2db43210uL.toLong(),
      0xb00327c898fb213fuL.toLong(),
      0xbf597fc7beef0ee4uL.toLong(),
      0xc6e00bf33da88fc2uL.toLong(),
      0xd5a79147930aa725uL.toLong(),
      0x06ca6351e003826fuL.toLong(),
      0x142929670a0e6e70uL.toLong(),
      0x27b70a8546d22ffcuL.toLong(),
      0x2e1b21385c26c926uL.toLong(),
      0x4d2c6dfc5ac42aeduL.toLong(),
      0x53380d139d95b3dfuL.toLong(),
      0x650a73548baf63deuL.toLong(),
      0x766a0abb3c77b2a8uL.toLong(),
      0x81c2c92e47edaee6uL.toLong(),
      0x92722c851482353buL.toLong(),
      0xa2bfe8a14cf10364uL.toLong(),
      0xa81a664bbc423001uL.toLong(),
      0xc24b8b70d0f89791uL.toLong(),
      0xc76c51a30654be30uL.toLong(),
      0xd192e819d6ef5218uL.toLong(),
      0xd69906245565a910uL.toLong(),
      0xf40e35855771202auL.toLong(),
      0x106aa07032bbd1b8uL.toLong(),
      0x19a4c116b8d2d0c8uL.toLong(),
      0x1e376c085141ab53uL.toLong(),
      0x2748774cdf8eeb99uL.toLong(),
      0x34b0bcb5e19b48a8uL.toLong(),
      0x391c0cb3c5c95a63uL.toLong(),
      0x4ed8aa4ae3418acbuL.toLong(),
      0x5b9cca4f7763e373uL.toLong(),
      0x682e6ff3d6b2b8a3uL.toLong(),
      0x748f82ee5defb2fcuL.toLong(),
      0x78a5636f43172f60uL.toLong(),
      0x84c87814a1f0ab72uL.toLong(),
      0x8cc702081a6439ecuL.toLong(),
      0x90befffa23631e28uL.toLong(),
      0xa4506cebde82bde9uL.toLong(),
      0xbef9a3f7b2c67915uL.toLong(),
      0xc67178f2e372532buL.toLong(),
      0xca273eceea26619cuL.toLong(),
      0xd186b8c721c0c207uL.toLong(),
      0xeada7dd6cde0eb1euL.toLong(),
      0xf57d4f7fee6ed178uL.toLong(),
      0x06f067aa72176fbauL.toLong(),
      0x0a637dc5a2c898a6uL.toLong(),
      0x113f9804bef90daeuL.toLong(),
      0x1b710b35131c471buL.toLong(),
      0x28db77f523047d84uL.toLong(),
      0x32caab7b40c72493uL.toLong(),
      0x3c9ebe0a15c9bebcuL.toLong(),
      0x431d67c49c100d4cuL.toLong(),
      0x4cc5d4becb3e42b6uL.toLong(),
      0x597f299cfc657e2auL.toLong(),
      0x5fcb6fab3ad6faecuL.toLong(),
      0x6c44198c4a475817uL.toLong(),
    )

  private fun sha384(input: ByteArray): ByteArray {
    val bitLen = input.size.toLong() * 8L
    val padLen = (112 - ((input.size + 1) % 128) + 128) % 128
    val totalLen = input.size + 1 + padLen + 16
    val padded = ByteArray(totalLen)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
      padded[totalLen - 1 - i] = ((bitLen ushr (i * 8)) and 0xFFL).toByte()
    }

    val h = SHA384_IV.copyOf()
    val w = LongArray(80)

    var offset = 0
    while (offset < totalLen) {
      for (i in 0 until 16) {
        var v = 0L
        for (b in 0 until 8) {
          v = (v shl 8) or (padded[offset + i * 8 + b].toLong() and 0xFFL)
        }
        w[i] = v
      }
      for (i in 16 until 80) {
        val s0 = w[i - 15].rotateRight(1) xor w[i - 15].rotateRight(8) xor (w[i - 15] ushr 7)
        val s1 = w[i - 2].rotateRight(19) xor w[i - 2].rotateRight(61) xor (w[i - 2] ushr 6)
        w[i] = w[i - 16] + s0 + w[i - 7] + s1
      }

      var a = h[0]
      var b = h[1]
      var c = h[2]
      var d = h[3]
      var e = h[4]
      var f = h[5]
      var g = h[6]
      var hh = h[7]

      for (i in 0 until 80) {
        val sigma1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
        val ch = (e and f) xor (e.inv() and g)
        val temp1 = hh + sigma1 + ch + SHA512_K[i] + w[i]
        val sigma0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
        val maj = (a and b) xor (a and c) xor (b and c)
        val temp2 = sigma0 + maj

        hh = g
        g = f
        f = e
        e = d + temp1
        d = c
        c = b
        b = a
        a = temp1 + temp2
      }

      h[0] += a
      h[1] += b
      h[2] += c
      h[3] += d
      h[4] += e
      h[5] += f
      h[6] += g
      h[7] += hh
      offset += 128
    }

    val out = ByteArray(48)
    for (i in 0 until 6) {
      val v = h[i]
      for (b in 0 until 8) {
        out[i * 8 + b] = ((v ushr ((7 - b) * 8)) and 0xFFL).toByte()
      }
    }
    return out
  }
}
