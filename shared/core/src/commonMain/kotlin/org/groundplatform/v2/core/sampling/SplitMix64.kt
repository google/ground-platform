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
package org.groundplatform.v2.core.sampling

/**
 * SplitMix64 pseudo-random generator (Steele, Lea & Flood, "Fast splittable pseudorandom number
 * generators", OOPSLA 2014; reference constants from Vigna's public-domain `splitmix64.c`).
 *
 * The sampling engine pins this algorithm instead of `kotlin.random` so that a seed produces the
 * same stream on every platform and Kotlin version. It only uses 64-bit integer arithmetic, which
 * is exact on JVM, JS (emulated `Long`), WasmJS and native. Changing anything here changes
 * generated designs and requires bumping [SAMPLING_ENGINE_VERSION].
 */
class SplitMix64(seed: Long) {
  private var state: Long = seed

  /** Returns the next 64 random bits. */
  fun nextLong(): Long {
    state += GOLDEN_GAMMA
    return mix64(state)
  }

  /** Returns a uniformly distributed double in [0, 1), using the top 53 bits. */
  fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * DOUBLE_UNIT

  /** Returns a uniformly distributed int in [0, bound), without modulo bias. */
  fun nextInt(bound: Int): Int {
    require(bound > 0) { "bound must be positive" }
    val b = bound.toLong()
    while (true) {
      val r = nextLong() ushr 1
      val m = r % b
      // Reject the incomplete final block of the 63-bit range (r - m + b - 1 overflows).
      if (r - m + (b - 1) >= 0) return m.toInt()
    }
  }

  companion object {
    /** 2^64 / φ, the SplitMix64 increment (0x9E3779B97F4A7C15). */
    private const val GOLDEN_GAMMA: Long = -0x61c8864680b583ebL
    private const val MIX_1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
    private const val MIX_2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB
    private const val DOUBLE_UNIT: Double = 1.0 / (1L shl 53)

    /** SplitMix64 finalizer: a bijective 64-bit avalanche mix. */
    fun mix64(value: Long): Long {
      var z = value
      z = (z xor (z ushr 30)) * MIX_1
      z = (z xor (z ushr 27)) * MIX_2
      return z xor (z ushr 31)
    }

    /** Derives an independent stream seed from a design seed and a stream label. */
    fun derive(seed: Long, stream: Long): Long = mix64(mix64(seed) xor mix64(stream + GOLDEN_GAMMA))

    /** Derives a stream seed from a design seed and a string key (e.g. a plot ID). */
    fun derive(seed: Long, key: String): Long = derive(seed, fnv1a64(key))

    /** 64-bit FNV-1a over the UTF-16 code units of [s]; stable on every platform. */
    fun fnv1a64(s: String): Long {
      var h = -0x340d631b7bdddcdbL // 0xCBF29CE484222325
      for (c in s) {
        h = h xor (c.code.toLong() and 0xFF)
        h *= FNV_PRIME
        h = h xor ((c.code.toLong() ushr 8) and 0xFF)
        h *= FNV_PRIME
      }
      return h
    }

    private const val FNV_PRIME: Long = 0x100000001b3L
  }
}
