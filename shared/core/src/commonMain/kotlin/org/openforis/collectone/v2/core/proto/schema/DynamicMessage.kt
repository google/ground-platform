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
package org.openforis.collectone.v2.core.proto.schema

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString

/**
 * A message read field by field through its [ProtoMessageDescriptor] rather than its generated
 * class: the values of the fields that are set, in declaration order.
 *
 * Values have these Kotlin types: [Int] for 32-bit integers and enum numbers, [Long] for 64-bit
 * integers, [Float], [Double], [Boolean], [String], and [ByteString] for `bytes` and for message
 * fields, which hold the encoded sub-message (read it with [ProtoSchema.read]). A singular field
 * has one value, a repeated field one per element.
 *
 * Converting to and from the generated classes goes through Wire's binary encoding, so Wire's
 * adapters stay the single source of truth for what a message contains.
 */
internal class DynamicMessage(
  val descriptor: ProtoMessageDescriptor,
  private val values: Map<ProtoFieldDescriptor, List<Any>>,
) {
  /** The set fields, in declaration order. */
  val setFields: List<ProtoFieldDescriptor>
    get() = descriptor.fields.filter { it in values }

  /** The values of [field]: empty when unset, one element for a set singular field. */
  fun values(field: ProtoFieldDescriptor): List<Any> = values[field].orEmpty()

  /** The values of the field named [name]. */
  fun values(name: String): List<Any> = values(descriptor.field(name))

  /** Whether no field is set. */
  fun isEmpty(): Boolean = values.isEmpty()

  /** Encodes this message in the proto binary format. */
  fun encode(): ByteString {
    val buffer = Buffer()
    val writer = ProtoWriter(buffer)
    for (field in setFields) {
      val adapter = adapterOf(field.type)
      for (value in values.getValue(field)) adapter.encodeWithTag(writer, field.number, value)
    }
    return buffer.readByteString()
  }

  /** Decodes this message into its generated class with [adapter]. */
  fun <M : Any> decodeAs(adapter: ProtoAdapter<M>): M = adapter.decode(encode())

  /** Collects field values into a [DynamicMessage]. */
  class Builder(private val descriptor: ProtoMessageDescriptor) {
    private val values = linkedMapOf<ProtoFieldDescriptor, MutableList<Any>>()

    /** Appends [value] to [field]; a singular field keeps only its last value. */
    fun add(field: ProtoFieldDescriptor, value: Any): Builder = apply {
      val list = values.getOrPut(field) { mutableListOf() }
      if (!field.isRepeated) list.clear()
      list += value
    }

    /** Unsets [field]. */
    fun remove(field: ProtoFieldDescriptor): Builder = apply { values.remove(field) }

    fun has(field: ProtoFieldDescriptor): Boolean = field in values

    /** The values added to [field] so far. */
    fun values(field: ProtoFieldDescriptor): List<Any> = values[field].orEmpty()

    /** The values added to the field named [name] so far. */
    fun values(name: String): List<Any> = values(descriptor.field(name))

    fun build(): DynamicMessage = DynamicMessage(descriptor, values)
  }
}

/** Reads the proto binary encoding [bytes] of a [descriptor] message. */
internal fun ProtoSchema.read(
  descriptor: ProtoMessageDescriptor,
  bytes: ByteString,
): DynamicMessage {
  val builder = DynamicMessage.Builder(descriptor)
  val oneofMembers = mutableMapOf<String, ProtoFieldDescriptor>()
  val reader = ProtoReader(Buffer().write(bytes))
  val token = reader.beginMessage()
  var tag = reader.nextTag()
  while (tag != -1) {
    val field = descriptor.field(tag)
    if (field != null && isReadable(field, reader.peekFieldEncoding())) {
      // Like Wire: the last member read of a oneof wins.
      field.oneof?.let { oneof -> oneofMembers.put(oneof, field)?.let(builder::remove) }
      builder.add(field, adapterOf(field.type).decode(reader))
    } else {
      reader.skip()
    }
    tag = reader.nextTag()
  }
  reader.endMessageAndGetUnknownFields(token)
  return builder.build()
}

/** Reads [message] through its descriptor in this schema. */
internal fun <M : Any> ProtoSchema.read(adapter: ProtoAdapter<M>, message: M): DynamicMessage =
  read(messageFor(adapter), adapter.encodeByteString(message))

/** The descriptor of the messages [adapter] encodes. */
internal fun ProtoSchema.messageFor(adapter: ProtoAdapter<*>): ProtoMessageDescriptor =
  message(checkNotNull(adapter.typeUrl) { "$adapter has no type URL" }.substringAfterLast('/'))

/**
 * Whether a value encoded as [encoding] can be read as [field]: its own encoding, or a packed run
 * (length-delimited) of a repeated number field. Anything else is skipped like an unknown field.
 */
private fun isReadable(field: ProtoFieldDescriptor, encoding: FieldEncoding?): Boolean {
  val expected = encodingOf(field.type)
  return encoding == expected ||
    (field.isRepeated && encoding == FieldEncoding.LENGTH_DELIMITED && expected != encoding)
}

private fun encodingOf(type: ProtoFieldType): FieldEncoding =
  when (type) {
    ProtoFieldType.DOUBLE,
    ProtoFieldType.FIXED64,
    ProtoFieldType.SFIXED64 -> FieldEncoding.FIXED64
    ProtoFieldType.FLOAT,
    ProtoFieldType.FIXED32,
    ProtoFieldType.SFIXED32 -> FieldEncoding.FIXED32
    ProtoFieldType.STRING,
    ProtoFieldType.BYTES,
    ProtoFieldType.MESSAGE -> FieldEncoding.LENGTH_DELIMITED
    else -> FieldEncoding.VARINT
  }

/** The Wire adapter reading and writing one value of [type]; see [DynamicMessage] for the types. */
@Suppress("UNCHECKED_CAST")
internal fun adapterOf(type: ProtoFieldType): ProtoAdapter<Any> =
  when (type) {
    ProtoFieldType.DOUBLE -> ProtoAdapter.DOUBLE
    ProtoFieldType.FLOAT -> ProtoAdapter.FLOAT
    ProtoFieldType.INT32,
    ProtoFieldType.ENUM -> ProtoAdapter.INT32
    ProtoFieldType.INT64 -> ProtoAdapter.INT64
    ProtoFieldType.UINT32 -> ProtoAdapter.UINT32
    ProtoFieldType.UINT64 -> ProtoAdapter.UINT64
    ProtoFieldType.SINT32 -> ProtoAdapter.SINT32
    ProtoFieldType.SINT64 -> ProtoAdapter.SINT64
    ProtoFieldType.FIXED32 -> ProtoAdapter.FIXED32
    ProtoFieldType.FIXED64 -> ProtoAdapter.FIXED64
    ProtoFieldType.SFIXED32 -> ProtoAdapter.SFIXED32
    ProtoFieldType.SFIXED64 -> ProtoAdapter.SFIXED64
    ProtoFieldType.BOOL -> ProtoAdapter.BOOL
    ProtoFieldType.STRING -> ProtoAdapter.STRING
    ProtoFieldType.BYTES,
    ProtoFieldType.MESSAGE -> ProtoAdapter.BYTES
  }
    as ProtoAdapter<Any>

/** The value an unset field of [type] reads as (an enum's number 0, an empty message). */
internal fun defaultValueOf(type: ProtoFieldType): Any =
  when (type) {
    ProtoFieldType.DOUBLE -> 0.0
    ProtoFieldType.FLOAT -> 0f
    ProtoFieldType.INT64,
    ProtoFieldType.UINT64,
    ProtoFieldType.SINT64,
    ProtoFieldType.FIXED64,
    ProtoFieldType.SFIXED64 -> 0L
    ProtoFieldType.BOOL -> false
    ProtoFieldType.STRING -> ""
    ProtoFieldType.BYTES,
    ProtoFieldType.MESSAGE -> ByteString.EMPTY
    else -> 0
  }

/** Whether [value] of [type] equals the default; `-0.0` counts as zero, as in the text formats. */
internal fun isDefaultValue(type: ProtoFieldType, value: Any): Boolean =
  when (value) {
    is Double -> value == 0.0
    is Float -> value == 0f
    else -> value == defaultValueOf(type)
  }
