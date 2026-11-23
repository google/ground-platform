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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.math.abs

// A minimal QR Code encoder for the GeoID QR code dialog, so the dialog draws its code offline on
// every target without a library. Section numbers refer to ISO/IEC 18004:2015.

/**
 * Encodes [payload] (UTF-8) into a QR Code matrix (`true` = dark module), row by row, without the
 * quiet zone.
 *
 * It supports only what a GeoID link needs: byte mode, error correction level L, mask pattern 0,
 * and versions 1–4, whose level L data fits in a single Reed–Solomon block (section 7.5.2, table
 * 9), so no codeword interleaving is needed. The smallest version that holds the payload is used,
 * and a payload longer than version 4 holds (78 bytes) is cut there.
 */
internal fun encodeQrMatrix(payload: String): Array<BooleanArray> {
  val bytes =
    payload.encodeToByteArray().let {
      if (it.size > QrVersion.V4.byteCapacity) it.copyOf(QrVersion.V4.byteCapacity) else it
    }
  val version = QrVersion.forByteCount(bytes.size)
  val data = dataCodewords(bytes, version.dataCodewords)
  val codewords = data + reedSolomonRemainder(data, version.ecCodewords)
  return QrGrid(version)
    .apply {
      drawFunctionPatterns()
      placeCodewords(codewords)
      drawFormatInformation()
    }
    .modules
}

/**
 * The versions [encodeQrMatrix] supports, with their level L capacities (section 7.4.10, table 7):
 * [dataCodewords] and [ecCodewords] per symbol, and the bytes of byte-mode data they hold.
 */
private enum class QrVersion(
  val number: Int,
  val dataCodewords: Int,
  val ecCodewords: Int,
  val byteCapacity: Int,
) {
  V1(1, dataCodewords = 19, ecCodewords = 7, byteCapacity = 17),
  V2(2, dataCodewords = 34, ecCodewords = 10, byteCapacity = 32),
  V3(3, dataCodewords = 55, ecCodewords = 15, byteCapacity = 53),
  V4(4, dataCodewords = 80, ecCodewords = 20, byteCapacity = 78);

  /** Modules per side (section 5.3.1). */
  val size: Int
    get() = 17 + number * 4

  companion object {
    fun forByteCount(byteCount: Int): QrVersion =
      entries.firstOrNull { byteCount <= it.byteCapacity } ?: V4
  }
}

/**
 * The data codewords for [bytes] in byte mode (section 7.4): the mode indicator `0100`, an 8-bit
 * character count (versions 1–9), the bytes, a terminator of up to four zero bits, zero bits to the
 * next byte, and the pad codewords `0xEC` / `0x11` alternating up to [count] codewords.
 */
private fun dataCodewords(bytes: ByteArray, count: Int): IntArray {
  val bits = ArrayList<Boolean>(count * 8)
  fun appendBits(value: Int, length: Int) {
    for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
  }
  appendBits(0b0100, 4)
  appendBits(bytes.size, 8)
  for (b in bytes) appendBits(b.toInt() and 0xFF, 8)
  val totalDataBits = count * 8
  repeat(minOf(4, totalDataBits - bits.size)) { bits.add(false) }
  while (bits.size % 8 != 0) bits.add(false)
  val padBytes = intArrayOf(0xEC, 0x11)
  var padIdx = 0
  while (bits.size < totalDataBits) {
    appendBits(padBytes[padIdx % 2], 8)
    padIdx++
  }
  return IntArray(count) { idx ->
    var v = 0
    for (b in 0 until 8) v = (v shl 1) or if (bits[idx * 8 + b]) 1 else 0
    v
  }
}

/**
 * The module grid of one QR Code symbol being drawn. Function patterns are drawn first and marked
 * in [isFunction], so [placeCodewords] skips them; the format information cells are reserved before
 * the data is placed and written last.
 */
private class QrGrid(private val version: QrVersion) {
  private val size = version.size
  val modules = Array(size) { BooleanArray(size) }
  private val isFunction = Array(size) { BooleanArray(size) }

  private fun setFunction(r: Int, c: Int, dark: Boolean) {
    if (r in 0 until size && c in 0 until size) {
      modules[r][c] = dark
      isFunction[r][c] = true
    }
  }

  /**
   * Draws the finder patterns with their separators, the timing patterns, the alignment pattern
   * (version 2 and up have one, at the bottom right), and the dark module, and reserves the format
   * information cells (sections 6.3.2–6.3.6, 7.9.1).
   */
  fun drawFunctionPatterns() {
    drawFinder(0, 0)
    drawFinder(0, size - 7)
    drawFinder(size - 7, 0)

    for (i in 8 until size - 8) {
      setFunction(6, i, i % 2 == 0)
      setFunction(i, 6, i % 2 == 0)
    }

    if (version.number >= 2) {
      val center = size - 7
      for (dr in -2..2) {
        for (dc in -2..2) setFunction(center + dr, center + dc, maxOf(abs(dr), abs(dc)) != 1)
      }
    }

    for (i in 0..8) {
      if (!isFunction[8][i]) setFunction(8, i, false)
      if (!isFunction[i][8]) setFunction(i, 8, false)
    }
    for (i in 0..7) {
      if (!isFunction[8][size - 1 - i]) setFunction(8, size - 1 - i, false)
      if (!isFunction[size - 1 - i][8]) setFunction(size - 1 - i, 8, false)
    }
    setFunction(size - 8, 8, true)
  }

  /** A 7×7 finder pattern with its top-left corner at ([top], [left]), and its light separator. */
  private fun drawFinder(top: Int, left: Int) {
    for (dr in -1..7) {
      for (dc in -1..7) {
        val inOuter = dr in 0..6 && dc in 0..6
        val onBorder = dr == 0 || dr == 6 || dc == 0 || dc == 6
        val inCenter = dr in 2..4 && dc in 2..4
        setFunction(top + dr, left + dc, inOuter && (onBorder || inCenter))
      }
    }
  }

  /**
   * Places [codewords] most significant bit first in two-module columns from the bottom right,
   * alternately upwards and downwards, skipping function modules and the vertical timing column
   * (section 7.7.3), and applies mask pattern 0, `(row + column) mod 2 == 0` (section 7.8.2).
   * Modules left over after the last codeword are remainder bits, zero before masking.
   */
  fun placeCodewords(codewords: IntArray) {
    var bitIdx = 0
    val totalBits = codewords.size * 8
    var right = size - 1
    var upward = true
    while (right >= 1) {
      if (right == 6) right -= 1
      for (step in 0 until size) {
        val r = if (upward) size - 1 - step else step
        for (c in right downTo right - 1) {
          if (isFunction[r][c]) continue
          val dark =
            bitIdx < totalBits && ((codewords[bitIdx / 8] ushr (7 - (bitIdx % 8))) and 1) != 0
          modules[r][c] = dark xor ((r + c) % 2 == 0)
          bitIdx++
        }
      }
      upward = !upward
      right -= 2
    }
  }

  /**
   * Writes both copies of the format information for level L (`01`) and mask 0 (`000`), BCH-coded
   * and XOR-masked as `0x77C4` (section 7.9.1, table C.1).
   */
  fun drawFormatInformation() {
    val formatBits = 0x77C4
    fun formatBit(i: Int): Boolean = ((formatBits ushr i) and 1) != 0
    for (i in 0..5) setFunction(8, i, formatBit(i))
    setFunction(8, 7, formatBit(6))
    setFunction(8, 8, formatBit(7))
    setFunction(7, 8, formatBit(8))
    for (i in 9..14) setFunction(14 - i, 8, formatBit(i))
    for (i in 0..7) setFunction(size - 1 - i, 8, formatBit(i))
    for (i in 8..14) setFunction(8, size - 15 + i, formatBit(i))
  }
}

/**
 * The [degree] Reed–Solomon error correction codewords for [data] (section 7.5.2): the remainder of
 * the data polynomial divided by the generator polynomial with roots `2^0 … 2^(degree-1)`, over
 * GF(2^8) with the QR Code field polynomial `x^8 + x^4 + x^3 + x^2 + 1` (`0x11D`).
 */
private fun reedSolomonRemainder(data: IntArray, degree: Int): IntArray {
  fun gfMul(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
      z = (z shl 1) xor ((z ushr 7) * 0x11D)
      z = z xor (((y ushr i) and 1) * x)
    }
    return z
  }
  var gen = intArrayOf(1)
  var root = 1
  repeat(degree) {
    val next = IntArray(gen.size + 1)
    for (j in gen.indices) {
      next[j] = next[j] xor gen[j]
      next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
    }
    gen = next
    root = gfMul(root, 0x02)
  }
  val result = IntArray(degree)
  for (b in data) {
    val factor = b xor result[0]
    for (i in 0 until degree - 1) result[i] = result[i + 1]
    result[degree - 1] = 0
    for (i in 0 until degree) result[i] = result[i] xor gfMul(gen[i + 1], factor)
  }
  return result
}
