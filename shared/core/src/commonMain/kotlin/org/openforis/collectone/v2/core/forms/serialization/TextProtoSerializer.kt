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
package org.openforis.collectone.v2.core.forms.serialization

import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoCodec
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoField
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoMessage
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoParser
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoWriter
import org.openforis.collectone.v2.core.proto.schema.CoreProtoSchema
import org.openforis.collectone.v2.core.proto.schema.DynamicMessage
import org.openforis.collectone.v2.core.proto.schema.ProtoCodecRules

/**
 * Serializer and deserializer between ProtoForms Protocol Buffer models ([FormDef] and
 * [RecordInstance]) and Protocol Buffer Text Format (`textproto` / `txtpb`).
 *
 * Fields are mapped by [TextProtoCodec] from the schema generated out of the `.proto` files, so new
 * fields need no code here; [FormCodecRules] lists where the format departs from the plain mapping.
 */
object TextProtoSerializer {

  /** Formats a [FormDef] protobuf message into a pretty-printed `textproto` string. */
  fun serializeFormDef(formDef: FormDef): String =
    TextProtoWriter().writeMessage(codec.write(FormDef.ADAPTER, formDef))

  /** Parses a `textproto` string into a [FormDef] protobuf message. */
  fun deserializeFormDef(textproto: String): FormDef =
    codec.read(FormDef.ADAPTER, TextProtoParser.parse(textproto))

  /** Formats a [RecordInstance] protobuf message into a pretty-printed `textproto` string. */
  fun serializeRecordInstance(record: RecordInstance): String =
    TextProtoWriter().writeMessage(codec.write(RecordInstance.ADAPTER, record))

  /** Parses a `textproto` string into a [RecordInstance] protobuf message. */
  fun deserializeRecordInstance(textproto: String): RecordInstance =
    codec.read(RecordInstance.ADAPTER, TextProtoParser.parse(textproto))

  /** A codec with the [FormCodecRules] plus [extraRules], for messages that embed forms. */
  internal fun codec(extraRules: ProtoCodecRules = ProtoCodecRules()): TextProtoCodec =
    TextProtoCodec(
      CoreProtoSchema,
      FormCodecRules + extraRules,
      messageWriters = mapOf("google.protobuf.Timestamp" to ::writeTimestamp),
    )

  private val codec = codec()

  /** Writes a timestamp, spelling out the epoch as `seconds: 0` rather than an empty message. */
  private fun writeTimestamp(timestamp: DynamicMessage): TextProtoMessage {
    val seconds = timestamp.values("seconds").singleOrNull() as Long? ?: 0L
    val nanos = timestamp.values("nanos").singleOrNull() as Int? ?: 0
    return TextProtoMessage(
      buildList {
        if (seconds != 0L || nanos == 0) {
          add(TextProtoField("seconds", TextProtoValue.NumberVal(seconds.toString())))
        }
        if (nanos != 0) add(TextProtoField("nanos", TextProtoValue.NumberVal(nanos.toString())))
      }
    )
  }
}
