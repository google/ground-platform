/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.domain.model

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

/**
 * An amount of data or storage, in bytes: a download's estimated size, the space a saved map area
 * takes, or the free space on the device. The UI writes it in the user's language with a decimal
 * unit (`48 MB`, `12.4 GB`); the domain never formats it.
 */
@JvmInline
value class DataSize(val bytes: Long) : Comparable<DataSize> {
  operator fun plus(other: DataSize): DataSize = DataSize(bytes + other.bytes)

  operator fun minus(other: DataSize): DataSize = DataSize(bytes - other.bytes)

  /** This size scaled by [factor], rounded to the nearest byte. */
  operator fun times(factor: Double): DataSize = DataSize((bytes * factor).roundToLong())

  override fun compareTo(other: DataSize): Int = bytes.compareTo(other.bytes)

  companion object {
    /** No data. */
    val ZERO: DataSize = DataSize(0)

    /** Bytes in a decimal megabyte (SI: 1 MB = 1,000,000 bytes). */
    const val BYTES_PER_MEGABYTE: Long = 1_000_000

    /** [megabytes] decimal megabytes. */
    fun ofMegabytes(megabytes: Long): DataSize = DataSize(megabytes * BYTES_PER_MEGABYTE)
  }
}

/** The sum of these sizes; [DataSize.ZERO] when there are none. */
fun Iterable<DataSize>.sum(): DataSize = fold(DataSize.ZERO, DataSize::plus)
