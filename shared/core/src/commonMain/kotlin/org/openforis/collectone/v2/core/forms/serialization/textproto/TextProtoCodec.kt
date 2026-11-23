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
package org.openforis.collectone.v2.core.forms.serialization.textproto

import com.squareup.wire.ProtoAdapter
import okio.ByteString
import org.openforis.collectone.v2.core.proto.schema.DynamicMessage
import org.openforis.collectone.v2.core.proto.schema.ProtoCodecRules
import org.openforis.collectone.v2.core.proto.schema.ProtoFieldDescriptor
import org.openforis.collectone.v2.core.proto.schema.ProtoFieldLabel
import org.openforis.collectone.v2.core.proto.schema.ProtoFieldType
import org.openforis.collectone.v2.core.proto.schema.ProtoMessageDescriptor
import org.openforis.collectone.v2.core.proto.schema.ProtoSchema
import org.openforis.collectone.v2.core.proto.schema.defaultValueOf
import org.openforis.collectone.v2.core.proto.schema.isDefaultValue
import org.openforis.collectone.v2.core.proto.schema.messageFor
import org.openforis.collectone.v2.core.proto.schema.qualifiedName
import org.openforis.collectone.v2.core.proto.schema.read

/**
 * Converts any message of a [ProtoSchema] to and from the textproto AST, field by field in
 * declaration order: proto3 presence decides what is written (implicit scalars only when not at
 * their default), map fields are repeated `key`/`value` entries, and [rules] cover the exceptions.
 *
 * Reading is lenient like the rest of the textproto support: the first occurrence of a singular
 * field wins, values of the wrong kind read as unset, and unknown fields are ignored.
 */
internal class TextProtoCodec(
  private val schema: ProtoSchema,
  private val rules: ProtoCodecRules,
  /** Writers that replace the generic one for whole message types, by full name. */
  private val messageWriters: Map<String, (DynamicMessage) -> TextProtoMessage> = emptyMap(),
) {

  fun <M : Any> write(adapter: ProtoAdapter<M>, message: M): TextProtoMessage =
    write(schema.read(adapter, message))

  fun <M : Any> read(adapter: ProtoAdapter<M>, node: TextProtoMessage): M =
    read(schema.messageFor(adapter), node).decodeAs(adapter)

  // ===========================================================================
  // Writing
  // ===========================================================================

  private fun write(message: DynamicMessage): TextProtoMessage {
    val descriptor = message.descriptor
    messageWriters[descriptor.fullName]?.let {
      return it(message)
    }
    val fields = mutableListOf<TextProtoField>()
    for (field in rules.writeOrder(descriptor)) {
      val name = descriptor.qualifiedName(field)
      if (name in rules.ignoredFields) continue
      val values = message.values(field)
      when {
        values.isEmpty() ->
          if (name in rules.alwaysWritten) {
            fields += TextProtoField(field.name, format(field, defaultValueOf(field.type)))
          }
        field.isRepeated ->
          values
            .filterNot { name in rules.zeroEnumsDropped && it == 0 }
            .forEach { fields += TextProtoField(field.name, format(field, it)) }
        else -> writeSingular(descriptor, field, values.single())?.let { fields += it }
      }
    }
    return TextProtoMessage(fields)
  }

  private fun writeSingular(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    value: Any,
  ): TextProtoField? {
    val name = descriptor.qualifiedName(field)
    val skipDefault = field.label == ProtoFieldLabel.IMPLICIT || name in rules.omittedWhenDefault
    val written =
      when {
        field.type == ProtoFieldType.MESSAGE ->
          writeMessageField(name, field, value as ByteString)?.let(TextProtoValue::MessageVal)
        skipDefault && isDefaultValue(field.type, value) -> null
        else -> format(field, value)
      }
    return written?.let { TextProtoField(field.name, it) }
  }

  /** Writes the message [bytes] of the field [name], or null where the rules leave it out. */
  private fun writeMessageField(
    name: String,
    field: ProtoFieldDescriptor,
    bytes: ByteString,
  ): TextProtoMessage? {
    val message = schema.read(schema.messageOf(field), bytes)
    val required = rules.omittedWithout[name]
    if (required != null && message.values(required).isEmpty()) return null
    return write(message).takeUnless { name in rules.omittedWhenEmpty && it.fields.isEmpty() }
  }

  private fun format(field: ProtoFieldDescriptor, value: Any): TextProtoValue =
    when (field.type) {
      ProtoFieldType.DOUBLE ->
        TextProtoValue.NumberVal(TextProtoWriter.formatDouble(value as Double))
      ProtoFieldType.FLOAT ->
        TextProtoValue.NumberVal(TextProtoWriter.formatDouble((value as Float).toDouble()))
      ProtoFieldType.UINT32,
      ProtoFieldType.FIXED32 -> TextProtoValue.NumberVal((value as Int).toUInt().toString())
      ProtoFieldType.UINT64,
      ProtoFieldType.FIXED64 -> TextProtoValue.NumberVal((value as Long).toULong().toString())
      ProtoFieldType.BOOL -> TextProtoValue.IdentifierVal(value.toString())
      ProtoFieldType.STRING -> TextProtoValue.StringVal(value as String)
      ProtoFieldType.BYTES -> TextProtoValue.BytesVal(value as ByteString)
      ProtoFieldType.ENUM ->
        schema.enumOf(field).nameOf(value as Int)?.let(TextProtoValue::IdentifierVal)
          ?: TextProtoValue.NumberVal(value.toString())
      ProtoFieldType.MESSAGE ->
        TextProtoValue.MessageVal(write(schema.read(schema.messageOf(field), value as ByteString)))
      else -> TextProtoValue.NumberVal(value.toString())
    }

  // ===========================================================================
  // Reading
  // ===========================================================================

  private fun read(descriptor: ProtoMessageDescriptor, node: TextProtoMessage): DynamicMessage {
    val builder = DynamicMessage.Builder(descriptor)
    for (field in descriptor.fields) {
      if (descriptor.qualifiedName(field) in rules.ignoredFields) continue
      if (field.isRepeated) {
        readRepeated(descriptor, field, node).forEach { builder.add(field, it) }
      } else {
        node
          .getField(field.name)
          ?.let { readValue(descriptor, field, it) }
          ?.let {
            builder.add(field, it)
          }
      }
    }
    rules.resolveOneofs(descriptor, builder)
    return builder.build()
  }

  private fun readRepeated(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    node: TextProtoMessage,
  ): List<Any> =
    when (field.type) {
      ProtoFieldType.MESSAGE -> {
        val type = schema.messageOf(field)
        node.getMessages(field.name).mapNotNull { sub ->
          if (type.isMapEntry) readMapEntry(type, sub) else read(type, sub).encode()
        }
      }
      ProtoFieldType.ENUM ->
        node.getIdentifiers(field.name).map {
          rules.parseEnum(descriptor, schema.enumOf(field), it)
        }
      ProtoFieldType.STRING -> node.getStrings(field.name)
      else ->
        node
          .getFields(field.name)
          .flatMap { if (it is TextProtoValue.ListVal) it.elements else listOf(it) }
          .mapNotNull { readValue(descriptor, field, it) }
    }

  /**
   * Reads a map entry, filling in an unset key or scalar value with its default. An entry without
   * its message value is dropped.
   */
  private fun readMapEntry(entry: ProtoMessageDescriptor, node: TextProtoMessage): ByteString? {
    val read = read(entry, node)
    val builder = DynamicMessage.Builder(entry)
    for (field in entry.fields) {
      val value =
        read.values(field).singleOrNull()
          ?: if (field.type == ProtoFieldType.MESSAGE) return null else defaultValueOf(field.type)
      builder.add(field, value)
    }
    return builder.build().encode()
  }

  private fun readValue(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    value: TextProtoValue,
  ): Any? =
    when (field.type) {
      ProtoFieldType.MESSAGE ->
        value.asMessageOrNull()?.let { read(schema.messageOf(field), it).encode() }
      ProtoFieldType.ENUM ->
        value.asIdentifierOrNull()?.let { rules.parseEnum(descriptor, schema.enumOf(field), it) }
      ProtoFieldType.STRING -> value.asStringOrNull()
      ProtoFieldType.BYTES -> value.asByteStringOrNull()
      ProtoFieldType.BOOL -> value.asBooleanOrNull()
      ProtoFieldType.DOUBLE -> value.asDoubleOrNull()
      ProtoFieldType.FLOAT -> value.asDoubleOrNull()?.toFloat()
      ProtoFieldType.INT32,
      ProtoFieldType.SINT32,
      ProtoFieldType.SFIXED32 -> value.asIntOrNull()
      ProtoFieldType.UINT32,
      ProtoFieldType.FIXED32 -> value.asLongOrNull()?.toInt()
      else -> value.asLongOrNull()
    }
}
