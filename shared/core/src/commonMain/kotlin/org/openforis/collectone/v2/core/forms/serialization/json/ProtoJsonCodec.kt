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
package org.openforis.collectone.v2.core.forms.serialization.json

import com.squareup.wire.ProtoAdapter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
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

/** Writes and reads one message type in its own JSON shape rather than as an object of fields. */
internal interface ProtoJsonTypeMapping {
  /** The JSON for the encoded [message], or null to leave the field out. */
  fun write(message: ByteString, preserveProtoFieldNames: Boolean): JsonElement?

  /** The encoded message read from [element], or null to leave the field unset. */
  fun read(element: JsonElement): ByteString?
}

/** Reads one field instead of the generic mapping, seeing the fields of its message read so far. */
internal fun interface ProtoJsonFieldReader {
  /**
   * The value read from [element] (see [DynamicMessage] for its type), or null to leave it unset.
   */
  fun read(element: JsonElement, message: DynamicMessage.Builder): Any?
}

/**
 * Converts any message of a [ProtoSchema] to and from ProtoJSON, field by field in declaration
 * order, following the proto3 JSON mapping: lowerCamelCase keys (or the proto field names), only
 * set fields, enums by name, maps as objects. [typeMappings] give some message types their own JSON
 * shape, and [rules] cover the remaining exceptions.
 *
 * Reading is lenient: keys in either naming style, numbers and booleans as strings, values of the
 * wrong kind read as unset, `null` reads as absent, and unknown keys are ignored.
 */
internal class ProtoJsonCodec(
  private val schema: ProtoSchema,
  private val rules: ProtoCodecRules,
  /** Custom JSON shapes, by full message name. */
  private val typeMappings: Map<String, ProtoJsonTypeMapping> = emptyMap(),
) {

  fun <M : Any> write(
    adapter: ProtoAdapter<M>,
    message: M,
    preserveProtoFieldNames: Boolean,
  ): JsonObject = write(schema.read(adapter, message), preserveProtoFieldNames)

  /** Reads [json] as an [adapter] message; [fieldReaders] (by `Message.field`) replace mappings. */
  fun <M : Any> read(
    adapter: ProtoAdapter<M>,
    json: JsonObject,
    fieldReaders: Map<String, ProtoJsonFieldReader> = emptyMap(),
  ): M = read(schema.messageFor(adapter), json, fieldReaders).decodeAs(adapter)

  // ===========================================================================
  // Writing
  // ===========================================================================

  private fun write(message: DynamicMessage, snake: Boolean): JsonObject {
    val descriptor = message.descriptor
    val entries = linkedMapOf<String, JsonElement>()
    for (field in rules.writeOrder(descriptor)) {
      val name = descriptor.qualifiedName(field)
      if (name in rules.ignoredFields) continue
      val values = message.values(field)
      val element =
        when {
          values.isEmpty() ->
            if (name in rules.alwaysWritten) format(field, defaultValueOf(field.type), snake)
            else null
          isMap(field) -> writeMap(field, values, snake)
          field.isRepeated -> writeRepeated(name, field, values, snake)
          else -> writeSingular(descriptor, field, values.single(), snake)
        }
      if (element != null) entries[jsonName(field.name, snake)] = element
    }
    return JsonObject(entries)
  }

  private fun writeSingular(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    value: Any,
    snake: Boolean,
  ): JsonElement? {
    val name = descriptor.qualifiedName(field)
    val skipDefault = field.label == ProtoFieldLabel.IMPLICIT || name in rules.omittedWhenDefault
    return when {
      field.type == ProtoFieldType.MESSAGE -> writeMessageField(name, field, value, snake)
      skipDefault && isDefaultValue(field.type, value) -> null
      else -> format(field, value, snake)
    }
  }

  /** Writes the message field [name], or null where the rules leave it out. */
  private fun writeMessageField(
    name: String,
    field: ProtoFieldDescriptor,
    value: Any,
    snake: Boolean,
  ): JsonElement? {
    val required = rules.omittedWithout[name]
    if (
      required != null &&
        schema.read(schema.messageOf(field), value as ByteString).values(required).isEmpty()
    ) {
      return null
    }
    return format(field, value, snake)?.takeUnless {
      name in rules.omittedWhenEmpty && it is JsonObject && it.isEmpty()
    }
  }

  private fun writeRepeated(
    name: String,
    field: ProtoFieldDescriptor,
    values: List<Any>,
    snake: Boolean,
  ): JsonElement? {
    val elements =
      values
        .filterNot { name in rules.zeroEnumsDropped && it == 0 }
        .mapNotNull { format(field, it, snake) }
    return if (elements.isEmpty()) null else JsonArray(elements)
  }

  private fun writeMap(
    field: ProtoFieldDescriptor,
    entries: List<Any>,
    snake: Boolean,
  ): JsonObject {
    val entryType = schema.messageOf(field)
    val keyField = entryType.field("key")
    val valueField = entryType.field("value")
    val map = linkedMapOf<String, JsonElement>()
    for (bytes in entries) {
      val entry = schema.read(entryType, bytes as ByteString)
      val key = entry.values(keyField).singleOrNull() ?: defaultValueOf(keyField.type)
      val value = entry.values(valueField).singleOrNull() ?: defaultValueOf(valueField.type)
      format(valueField, value, snake)?.let { map[key.toString()] = it }
    }
    return JsonObject(map)
  }

  private fun format(field: ProtoFieldDescriptor, value: Any, snake: Boolean): JsonElement? =
    when (field.type) {
      ProtoFieldType.MESSAGE -> {
        val bytes = value as ByteString
        typeMappings[field.typeName]?.write(bytes, snake)
          ?: if (field.typeName in typeMappings) null
          else write(schema.read(schema.messageOf(field), bytes), snake)
      }
      ProtoFieldType.ENUM ->
        schema.enumOf(field).nameOf(value as Int)?.let(::JsonPrimitive) ?: JsonPrimitive(value)
      ProtoFieldType.STRING -> JsonPrimitive(value as String)
      ProtoFieldType.BOOL -> JsonPrimitive(value as Boolean)
      ProtoFieldType.BYTES -> JsonPrimitive((value as ByteString).base64())
      ProtoFieldType.DOUBLE -> JsonPrimitive(value as Double)
      ProtoFieldType.FLOAT -> JsonPrimitive(value as Float)
      ProtoFieldType.UINT32,
      ProtoFieldType.FIXED32 -> JsonPrimitive((value as Int).toUInt().toLong())
      ProtoFieldType.INT32,
      ProtoFieldType.SINT32,
      ProtoFieldType.SFIXED32 -> JsonPrimitive(value as Int)
      // 64-bit integers are strings in proto3 JSON, as JavaScript numbers can't hold them.
      ProtoFieldType.UINT64,
      ProtoFieldType.FIXED64 -> JsonPrimitive((value as Long).toULong().toString())
      else -> JsonPrimitive(value.toString())
    }

  // ===========================================================================
  // Reading
  // ===========================================================================

  private fun read(
    descriptor: ProtoMessageDescriptor,
    json: JsonObject,
    fieldReaders: Map<String, ProtoJsonFieldReader>,
  ): DynamicMessage {
    val builder = DynamicMessage.Builder(descriptor)
    for (field in descriptor.fields) {
      val name = descriptor.qualifiedName(field)
      val element = json.getProtoElement(field.name)?.takeUnless { name in rules.ignoredFields }
      val reader = fieldReaders[name]
      when {
        element == null -> Unit
        reader != null -> reader.read(element, builder)?.let { builder.add(field, it) }
        isMap(field) -> readMap(field, element).forEach { builder.add(field, it) }
        field.isRepeated ->
          readRepeated(descriptor, field, element, fieldReaders).forEach { builder.add(field, it) }
        else -> readValue(descriptor, field, element, fieldReaders)?.let { builder.add(field, it) }
      }
    }
    rules.resolveOneofs(descriptor, builder)
    return builder.build()
  }

  private fun readRepeated(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    element: JsonElement,
    fieldReaders: Map<String, ProtoJsonFieldReader>,
  ): List<Any> {
    val array = element as? JsonArray ?: return emptyList()
    return when (field.type) {
      ProtoFieldType.MESSAGE ->
        array.filterIsInstance<JsonObject>().mapNotNull {
          readValue(descriptor, field, it, fieldReaders)
        }
      else -> array.mapNotNull { readValue(descriptor, field, it, fieldReaders) }
    }
  }

  /**
   * Reads a map from an object, or from an array of `{"key": …, "value": …}` entries. An entry
   * without its message value is dropped.
   */
  private fun readMap(field: ProtoFieldDescriptor, element: JsonElement): List<ByteString> {
    val entryType = schema.messageOf(field)
    val keyField = entryType.field("key")
    val valueField = entryType.field("value")
    val entries: List<Pair<JsonElement, JsonElement?>> =
      when (element) {
        is JsonObject -> element.map { (key, value) -> JsonPrimitive(key) to value }
        is JsonArray ->
          element.filterIsInstance<JsonObject>().map { entry ->
            (entry.getProtoElement("key") ?: JsonPrimitive("")) to entry.getProtoElement("value")
          }
        else -> emptyList()
      }
    return entries.mapNotNull { (keyElement, valueElement) ->
      val key =
        readValue(entryType, keyField, keyElement, emptyMap()) ?: defaultValueOf(keyField.type)
      val value =
        valueElement?.let { readValue(entryType, valueField, it, emptyMap()) }
          ?: if (valueField.type == ProtoFieldType.MESSAGE) return@mapNotNull null
          else defaultValueOf(valueField.type)
      DynamicMessage.Builder(entryType).add(keyField, key).add(valueField, value).build().encode()
    }
  }

  private fun readValue(
    descriptor: ProtoMessageDescriptor,
    field: ProtoFieldDescriptor,
    element: JsonElement,
    fieldReaders: Map<String, ProtoJsonFieldReader>,
  ): Any? {
    if (field.type == ProtoFieldType.MESSAGE) return readMessage(field, element, fieldReaders)
    val primitive = element as? JsonPrimitive ?: return null
    return when (field.type) {
      ProtoFieldType.ENUM -> rules.parseEnum(descriptor, schema.enumOf(field), primitive.content)
      ProtoFieldType.STRING -> primitive.content
      ProtoFieldType.BOOL -> primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
      ProtoFieldType.BYTES -> primitive.content.decodeBase64()
      ProtoFieldType.DOUBLE -> primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
      ProtoFieldType.FLOAT ->
        (primitive.doubleOrNull ?: primitive.content.toDoubleOrNull())?.toFloat()
      ProtoFieldType.INT32,
      ProtoFieldType.SINT32,
      ProtoFieldType.SFIXED32 -> primitive.intOrNull ?: primitive.content.toIntOrNull()
      ProtoFieldType.UINT32,
      ProtoFieldType.FIXED32 -> (primitive.longOrNull ?: primitive.content.toLongOrNull())?.toInt()
      else -> primitive.longOrNull ?: primitive.content.toLongOrNull()
    }
  }

  private fun readMessage(
    field: ProtoFieldDescriptor,
    element: JsonElement,
    fieldReaders: Map<String, ProtoJsonFieldReader>,
  ): ByteString? {
    val mapping = typeMappings[field.typeName]
    return when {
      mapping != null -> mapping.read(element)
      element is JsonObject -> read(schema.messageOf(field), element, fieldReaders).encode()
      else -> null
    }
  }

  private fun isMap(field: ProtoFieldDescriptor): Boolean =
    field.isRepeated && field.type == ProtoFieldType.MESSAGE && schema.messageOf(field).isMapEntry

  private fun jsonName(protoName: String, snake: Boolean): String =
    if (snake) protoName else snakeToCamel(protoName)
}

/** The lowerCamelCase JSON name of the proto field [snake], e.g. `form_id` to `formId`. */
internal fun snakeToCamel(snake: String): String {
  if (!snake.contains('_')) return snake
  val sb = StringBuilder(snake.length)
  var capitalizeNext = false
  for (c in snake) {
    if (c == '_') {
      capitalizeNext = true
    } else if (capitalizeNext) {
      sb.append(c.uppercaseChar())
      capitalizeNext = false
    } else {
      sb.append(c)
    }
  }
  return sb.toString()
}

/**
 * The value of the proto field [snakeName], under its lowerCamelCase or its proto name; `null`
 * values count as absent.
 */
internal fun JsonObject.getProtoElement(snakeName: String): JsonElement? =
  this[snakeToCamel(snakeName)]?.takeIf { it !is JsonNull }
    ?: this[snakeName]?.takeIf { it !is JsonNull }
