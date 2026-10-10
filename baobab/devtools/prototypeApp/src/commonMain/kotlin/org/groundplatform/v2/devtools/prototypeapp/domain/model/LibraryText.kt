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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Language-independent text normalization for library search and ID generation: lowercasing, accent
 * folding, tokenization, and edit distance.
 *
 * Kotlin common code has no Unicode normalizer, so [fold] uses an explicit table covering Latin-1,
 * Latin Extended-A, and the Vietnamese letters of Latin Extended Additional (U+1EA0–U+1EF9), plus
 * combining diacritical marks (U+0300–U+036F) for already-decomposed input.
 */
object LibraryText {
  private val FOLDS: Map<Char, String> = buildMap {
    fun map(base: String, variants: String) = variants.forEach { put(it, base) }
    map("a", "àáâãäåāăąǎ")
    map("ae", "æ")
    map("c", "çćĉċč")
    map("d", "ďđð")
    map("e", "èéêëēĕėęě")
    map("g", "ĝğġģ")
    map("h", "ĥħ")
    map("i", "ìíîïĩīĭįı")
    map("j", "ĵ")
    map("k", "ķ")
    map("l", "ĺļľŀł")
    map("n", "ñńņňŉ")
    map("o", "òóôõöøōŏőơ")
    map("oe", "œ")
    map("r", "ŕŗř")
    map("s", "śŝşš")
    map("ss", "ß")
    map("t", "ţťŧ")
    map("th", "þ")
    map("u", "ùúûüũūŭůűųư")
    map("w", "ŵ")
    map("y", "ýÿŷ")
    map("z", "źżž")
    // Vietnamese (Latin Extended Additional): runs of lowercase letters at odd code points.
    vietnameseRun(0x1EA0, 0x1EB7, "a")
    vietnameseRun(0x1EB8, 0x1EC7, "e")
    vietnameseRun(0x1EC8, 0x1ECB, "i")
    vietnameseRun(0x1ECC, 0x1EE3, "o")
    vietnameseRun(0x1EE4, 0x1EF1, "u")
    vietnameseRun(0x1EF2, 0x1EF9, "y")
  }

  private fun MutableMap<Char, String>.vietnameseRun(first: Int, last: Int, base: String) {
    for (code in first..last) put(code.toChar(), base)
  }

  /** Lowercases [text] and removes diacritics (`"Cây trồng"` → `"cay trong"`). */
  fun fold(text: String): String =
    buildString(text.length) {
      for (c in text.lowercase()) {
        when {
          c.code in 0x0300..0x036F -> Unit // Combining diacritical mark.
          else -> append(FOLDS[c] ?: c)
        }
      }
    }

  /** [fold]ed words of [text], split on anything that isn't a letter or digit. */
  fun tokens(text: String): List<String> {
    val folded = fold(text)
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    for (c in folded) {
      if (c.isLetterOrDigit()) {
        current.append(c)
      } else if (current.isNotEmpty()) {
        tokens += current.toString()
        current.clear()
      }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
  }

  /**
   * Optimal string alignment distance (Levenshtein plus adjacent transpositions) between [a] and
   * [b], or `max + 1` once it's known to exceed [max].
   */
  fun editDistance(a: String, b: String, max: Int = Int.MAX_VALUE - 1): Int {
    if (a == b) return 0
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prevPrev = IntArray(b.length + 1)
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
      curr[0] = i
      var rowMin = curr[0]
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        var value = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
          value = minOf(value, prevPrev[j - 2] + 1)
        }
        curr[j] = value
        if (value < rowMin) rowMin = value
      }
      if (rowMin > max) return max + 1
      val recycled = prevPrev
      prevPrev = prev
      prev = curr
      curr = recycled
    }
    return prev[b.length].coerceAtMost(max + 1)
  }
}
