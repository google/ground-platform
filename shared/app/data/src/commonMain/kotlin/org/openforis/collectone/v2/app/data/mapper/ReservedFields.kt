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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import okio.Buffer
import okio.ByteString

/**
 * Reads fields whose numbers are reserved now from rows stored before. Proto3 decoding keeps such
 * fields in the message's unknown fields, so the legacy decoders find them there.
 */
internal object ReservedFields {
  /** The last string with field number [tag] in a message's [unknownFields], or `""`. */
  fun string(unknownFields: ByteString, tag: Int): String =
    values(unknownFields, tag, ProtoAdapter.STRING).lastOrNull().orEmpty()

  /** The last float with field number [tag] in a message's [unknownFields], or `null`. */
  fun float(unknownFields: ByteString, tag: Int): Float? =
    values(unknownFields, tag, ProtoAdapter.FLOAT).lastOrNull()

  /** The last double with field number [tag] in a message's [unknownFields], or `null`. */
  fun double(unknownFields: ByteString, tag: Int): Double? =
    values(unknownFields, tag, ProtoAdapter.DOUBLE).lastOrNull()

  /** Every value with field number [tag] in a message's [unknownFields], decoded by [adapter]. */
  fun <T> values(unknownFields: ByteString, tag: Int, adapter: ProtoAdapter<T>): List<T> {
    if (unknownFields.size == 0) return emptyList()
    val values = mutableListOf<T>()
    val reader = ProtoReader(Buffer().write(unknownFields))
    reader.forEachTag { field ->
      if (field == tag) values += adapter.decode(reader) else reader.readUnknownField(field)
    }
    return values
  }
}
