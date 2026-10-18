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
package org.groundplatform.v2.core.geo.s2

import kotlin.math.max
import kotlin.math.min

/**
 * A 64-bit S2 cell identifier (`S2CellId`).
 *
 * Layout, from the most significant bit: 3 bits for the cube face, 2 bits per level for the
 * position along the face's Hilbert curve, then a single `1` bit marking the end of the position,
 * followed by zeros. Ordering is unsigned, which matches Hilbert curve order across faces.
 */
internal data class S2CellId(val id: ULong) : Comparable<S2CellId> {

  override fun compareTo(other: S2CellId): Int = id.compareTo(other.id)

  /** Lowest set bit; encodes the level. */
  fun lsb(): ULong = id and (0uL - id)

  fun face(): Int = (id shr S2.POS_BITS).toInt()

  fun isLeaf(): Boolean = (id and 1uL) != 0uL

  fun level(): Int =
    if (isLeaf()) S2.MAX_LEVEL else S2.MAX_LEVEL - (id.countTrailingZeroBits() shr 1)

  fun parent(level: Int): S2CellId {
    val newLsb = lsbForLevel(level)
    return S2CellId((id and (0uL - newLsb)) or newLsb)
  }

  private fun childBegin(): S2CellId {
    val oldLsb = lsb()
    return S2CellId(id - oldLsb + (oldLsb shr 2))
  }

  private fun childEnd(): S2CellId {
    val oldLsb = lsb()
    return S2CellId(id + oldLsb + (oldLsb shr 2))
  }

  private fun next(): S2CellId = S2CellId(id + (lsb() shl 1))

  /** The four children of this (non-leaf) cell, in Hilbert curve order. */
  fun children(): List<S2CellId> {
    val result = ArrayList<S2CellId>(4)
    var child = childBegin()
    val end = childEnd()
    while (child != end) {
      result.add(child)
      child = child.next()
    }
    return result
  }

  fun toFaceIjOrientation(): FaceIjOrientation {
    var i = 0
    var j = 0
    val face = face()
    var bits = face and S2.SWAP_MASK
    for (k in 7 downTo 0) {
      val nbits = if (k == 7) S2.MAX_LEVEL - 7 * 4 else 4
      bits += ((id shr (k * 2 * 4 + 1)) and ((1uL shl (2 * nbits)) - 1uL)).toInt() shl 2
      bits = S2.LOOKUP_IJ[bits]
      i += (bits shr (4 + 2)) shl (k * 4)
      j += ((bits shr 2) and ((1 shl 4) - 1)) shl (k * 4)
      bits = bits and (S2.SWAP_MASK or S2.INVERT_MASK)
    }
    if ((lsb() and 0x1111111111111110uL) != 0uL) bits = bits xor S2.SWAP_MASK
    return FaceIjOrientation(face, i, j, bits)
  }

  /** Center of the cell in (u, v) coordinates (`S2CellId::GetCenterUV`). */
  fun centerUv(): Pair<Double, Double> {
    val (_, i, j, _) = toFaceIjOrientation()
    val delta =
      when {
        isLeaf() -> 1L
        ((i.toULong() xor (id shr 2)) and 1uL) != 0uL -> 2L
        else -> 0L
      }
    val si = 2L * i + delta
    val ti = 2L * j + delta
    return Pair(
      S2.stToUv((0.5 / S2.MAX_SIZE) * si.toDouble()),
      S2.stToUv((0.5 / S2.MAX_SIZE) * ti.toDouble()),
    )
  }

  /**
   * The cells at [level] that share the vertex closest to this cell: normally four, or three when
   * that vertex is a cube corner (`S2CellId::AppendVertexNeighbors`). [level] must be strictly less
   * than this cell's level.
   */
  fun vertexNeighbors(level: Int): List<S2CellId> {
    require(level < level()) { "level $level must be less than ${level()}" }
    val (face, i, j, _) = toFaceIjOrientation()
    val halfSize = sizeIj(level + 1)
    val size = halfSize shl 1
    val iOffset: Int
    val iSame: Boolean
    if ((i and halfSize) != 0) {
      iOffset = size
      iSame = (i + size) < S2.MAX_SIZE
    } else {
      iOffset = -size
      iSame = (i - size) >= 0
    }
    val jOffset: Int
    val jSame: Boolean
    if ((j and halfSize) != 0) {
      jOffset = size
      jSame = (j + size) < S2.MAX_SIZE
    } else {
      jOffset = -size
      jSame = (j - size) >= 0
    }
    val neighbors = mutableListOf(parent(level))
    neighbors.add(fromFaceIjSame(face, i + iOffset, j, iSame).parent(level))
    neighbors.add(fromFaceIjSame(face, i, j + jOffset, jSame).parent(level))
    if (iSame || jSame) {
      neighbors.add(fromFaceIjSame(face, i + iOffset, j + jOffset, iSame && jSame).parent(level))
    }
    return neighbors
  }

  /** Hex encoding of [id] with trailing zeros removed (`S2CellId::ToToken`). */
  fun toToken(): String = id.toString(16).padStart(16, '0').trimEnd('0')

  override fun toString(): String = "S2CellId(${toToken()})"

  companion object {
    fun lsbForLevel(level: Int): ULong = 1uL shl (2 * (S2.MAX_LEVEL - level))

    fun sizeIj(level: Int): Int = 1 shl (S2.MAX_LEVEL - level)

    fun fromFacePosLevel(face: Int, pos: ULong, level: Int): S2CellId =
      S2CellId((face.toULong() shl S2.POS_BITS) + (pos or 1uL)).parent(level)

    fun fromFaceIj(face: Int, i: Int, j: Int): S2CellId {
      var n = face.toULong() shl (S2.POS_BITS - 1)
      var bits = face and S2.SWAP_MASK
      val mask = (1 shl 4) - 1
      for (k in 7 downTo 0) {
        bits += ((i shr (k * 4)) and mask) shl (4 + 2)
        bits += ((j shr (k * 4)) and mask) shl 2
        bits = S2.LOOKUP_POS[bits]
        n = n or ((bits shr 2).toULong() shl (k * 2 * 4))
        bits = bits and (S2.SWAP_MASK or S2.INVERT_MASK)
      }
      return S2CellId(n * 2uL + 1uL)
    }

    /** Leaf cell for (i, j) coordinates just beyond the edge of [face], on the adjacent face. */
    private fun fromFaceIjWrap(face: Int, i: Int, j: Int): S2CellId {
      val ci = max(-1, min(S2.MAX_SIZE, i)).toLong()
      val cj = max(-1, min(S2.MAX_SIZE, j)).toLong()
      val scale = 1.0 / S2.MAX_SIZE
      val u = scale * ((ci shl 1) + 1 - S2.MAX_SIZE).toDouble()
      val v = scale * ((cj shl 1) + 1 - S2.MAX_SIZE).toDouble()
      val wrapped = S2.xyzToFaceUv(S2.faceUvToXyz(face, u, v))
      return fromFaceIj(
        wrapped.face,
        S2.stToIj(0.5 * (wrapped.u + 1)),
        S2.stToIj(0.5 * (wrapped.v + 1)),
      )
    }

    private fun fromFaceIjSame(face: Int, i: Int, j: Int, sameFace: Boolean): S2CellId =
      if (sameFace) fromFaceIj(face, i, j) else fromFaceIjWrap(face, i, j)

    /** Leaf cell containing [p] (`S2CellId::FromPoint`). */
    fun fromPoint(p: S2Point): S2CellId {
      val (face, u, v) = S2.xyzToFaceUv(p)
      return fromFaceIj(face, S2.stToIj(S2.uvToSt(u)), S2.stToIj(S2.uvToSt(v)))
    }
  }
}
