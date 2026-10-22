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
package org.openforis.collectone.v2.server.userdata

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.wire.WireJsonAdapterFactory
import openforis.collectone.v1.userdata.UserDataSeed

internal val protoJsonMoshi: Moshi =
  Moshi.Builder()
    .add(
      Date::class.java,
      IntFieldsAdapter(listOf("year", "month", "day"), { Date(it[0], it[1], it[2]) }) {
        listOf(it.year, it.month, it.day)
      },
    )
    .add(
      TimeOfDay::class.java,
      IntFieldsAdapter(
        listOf("hours", "minutes", "seconds", "nanos"),
        { TimeOfDay(it[0], it[1], it[2], it[3]) },
      ) {
        listOf(it.hours, it.minutes, it.seconds, it.nanos)
      },
    )
    .add(WireJsonAdapterFactory())
    .build()

/**
 * Reads and writes [UserDataSeed] as proto JSON, the format of the bundled sample seed.
 *
 * `google.type.Date` and `google.type.TimeOfDay` get explicit adapters: grpc-protobuf (needed by
 * Armeria) puts protobuf-java classes with the same names on the classpath, which confuses the
 * reflection behind [WireJsonAdapterFactory].
 */
val userDataSeedJsonAdapter: JsonAdapter<UserDataSeed> =
  protoJsonMoshi.adapter(UserDataSeed::class.java)

/**
 * Proto JSON adapter for a message made only of `int32` fields named [names]. Like Wire, it omits
 * zero values when writing.
 */
private class IntFieldsAdapter<T : Any>(
  private val names: List<String>,
  private val create: (List<Int>) -> T,
  private val values: (T) -> List<Int>,
) : JsonAdapter<T>() {
  override fun fromJson(reader: JsonReader): T? {
    if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
    val fields = IntArray(names.size)
    reader.beginObject()
    while (reader.hasNext()) {
      val index = names.indexOf(reader.nextName())
      if (index >= 0) fields[index] = reader.nextInt() else reader.skipValue()
    }
    reader.endObject()
    return create(fields.toList())
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    names
      .zip(values(value))
      .filter { it.second != 0 }
      .forEach { writer.name(it.first).value(it.second) }
    writer.endObject()
  }
}
