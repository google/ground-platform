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
package org.openforis.collectone.v2.core.proto

import com.squareup.wire.AnyMessage
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import java.lang.reflect.Field
import java.time.Duration
import java.time.Instant
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

/**
 * Builds "fully populated" Wire messages by reflection, so tests automatically cover fields that
 * are added to the schema later.
 *
 * Every field gets a non-default value: scalars a fixed non-zero value, enums their first non-zero
 * constant, messages a recursively populated instance, repeated fields one element and maps one
 * entry. Only one member of a `oneof` can be set, so [populate] takes a `variant`: variant `n` sets
 * member `n % size` of every `oneof`. Running variants `0 until` [variantCount] covers every member
 * at least once.
 *
 * Recursive types (a group inside a group, a record node inside a record node) are expanded
 * [MAX_RECURSION] levels deep and left empty below that.
 *
 * Messages are built by encoding the chosen values with each field's own [ProtoAdapter] and
 * decoding the bytes with the message adapter, which avoids depending on constructor shapes.
 */
object FullyPopulatedProtos {

  private const val MAX_RECURSION = 1

  /** One field of a generated message, read from its [WireField] annotation. */
  class ProtoField(val owner: Class<*>, val javaField: Field, val annotation: WireField) {
    val name: String
      get() = annotation.declaredName.ifEmpty { javaField.name }

    val oneofName: String
      get() = annotation.oneofName

    val isMap: Boolean
      get() = annotation.keyAdapter.isNotEmpty()

    val isRepeated: Boolean
      get() =
        annotation.label == WireField.Label.REPEATED || annotation.label == WireField.Label.PACKED

    val adapter: ProtoAdapter<Any>
      @Suppress("UNCHECKED_CAST") get() = ProtoAdapter.get(annotation.adapter) as ProtoAdapter<Any>

    /** The generated class of a message or enum field, or null for scalars and well-known types. */
    val elementClass: Class<*>?
      get() =
        annotation.adapter
          .substringBefore('#')
          .takeUnless { it.startsWith("com.squareup.wire.") }
          ?.let { Class.forName(it) }

    override fun toString(): String = "${owner.simpleName}.$name"
  }

  /** The [WireField]-annotated fields of [messageClass], in schema order. */
  fun fieldsOf(messageClass: Class<*>): List<ProtoField> =
    messageClass.declaredFields
      .mapNotNull { field ->
        field.getAnnotation(WireField::class.java)?.let { ProtoField(messageClass, field, it) }
      }
      .sortedBy { it.annotation.schemaIndex }

  /** How many variants are needed to set every `oneof` member reachable from [messageClass]. */
  fun variantCount(messageClass: Class<*>): Int {
    var max = 1
    walkMessageTypes(messageClass) { type ->
      fieldsOf(type)
        .filter { it.oneofName.isNotEmpty() }
        .groupBy { it.oneofName }
        .values
        .forEach { members -> max = maxOf(max, members.size) }
    }
    return max
  }

  /** A fully populated instance of [messageClass] for the given oneof [variant]. */
  fun <M : Message<M, *>> populate(messageClass: Class<M>, variant: Int = 0): M =
    populateMessage(messageClass, variant, stack = emptyList())

  @Suppress("UNCHECKED_CAST")
  private fun <M : Any> populateMessage(type: Class<M>, variant: Int, stack: List<Class<*>>): M {
    val nextStack = stack + type
    val buffer = Buffer()
    val writer = ProtoWriter(buffer)
    for (field in fieldsChosenFor(type, variant, nextStack)) {
      val value = valueFor(field, variant, nextStack) ?: continue
      encodedAdapter(field).encodeWithTag(writer, field.annotation.tag, value)
    }
    val adapter = type.getField("ADAPTER").get(null) as ProtoAdapter<M>
    return adapter.decode(buffer.readByteString())
  }

  /**
   * Every non-oneof field, plus member `variant % size` of each oneof. When that member is a
   * message whose recursion budget is spent, the next member is used instead, so a oneof is never
   * left unset (an unset oneof would make the fixture ambiguous rather than fully populated).
   */
  private fun fieldsChosenFor(
    type: Class<*>,
    variant: Int,
    stack: List<Class<*>>,
  ): List<ProtoField> {
    val fields = fieldsOf(type)
    val oneofChoices =
      fields
        .filter { it.oneofName.isNotEmpty() }
        .groupBy { it.oneofName }
        .values
        .map { members ->
          val rotated = members.indices.map { members[(variant + it) % members.size] }
          rotated.firstOrNull { !isExhausted(it, stack) } ?: rotated.first()
        }
        .toSet()
    return fields.filter { it.oneofName.isEmpty() || it in oneofChoices }
  }

  private fun isExhausted(field: ProtoField, stack: List<Class<*>>): Boolean {
    val element = field.elementClass ?: return false
    return Message::class.java.isAssignableFrom(element) &&
      (stack.count { it == element } > MAX_RECURSION || containsExhaustedOneofOnly(element, stack))
  }

  /** A wrapper message (e.g. a node list) whose only content would itself be exhausted. */
  private fun containsExhaustedOneofOnly(type: Class<*>, stack: List<Class<*>>): Boolean {
    val nested = fieldsOf(type)
    return nested.isNotEmpty() &&
      nested.all { field ->
        val element = field.elementClass
        element != null &&
          Message::class.java.isAssignableFrom(element) &&
          (stack + type).count { it == element } > MAX_RECURSION
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun encodedAdapter(field: ProtoField): ProtoAdapter<Any> =
    when {
      field.isMap ->
        ProtoAdapter.newMapAdapter(ProtoAdapter.get(field.annotation.keyAdapter), field.adapter)
          as ProtoAdapter<Any>
      field.annotation.label == WireField.Label.PACKED ->
        field.adapter.asPacked() as ProtoAdapter<Any>
      field.isRepeated -> field.adapter.asRepeated() as ProtoAdapter<Any>
      else -> field.adapter
    }

  private fun valueFor(field: ProtoField, variant: Int, stack: List<Class<*>>): Any? {
    val element = elementValue(field, field.annotation.adapter, variant, stack) ?: return null
    return when {
      field.isMap ->
        mapOf(elementValue(field, field.annotation.keyAdapter, variant, stack) to element)
      field.isRepeated -> listOf(element)
      else -> element
    }
  }

  private fun elementValue(
    field: ProtoField,
    adapterName: String,
    variant: Int,
    stack: List<Class<*>>,
  ): Any? {
    scalarValue(field, adapterName)?.let {
      return it
    }
    val type = Class.forName(adapterName.substringBefore('#'))
    return when {
      WireEnum::class.java.isAssignableFrom(type) ->
        type.enumConstants.first { (it as WireEnum).value != 0 }
      stack.count { it == type } > MAX_RECURSION -> null
      else -> populateMessage(type, variant, stack)
    }
  }

  /** A non-default value for Wire's built-in adapters, or null for generated types. */
  private fun scalarValue(field: ProtoField, adapterName: String): Any? =
    when (
      adapterName.substringAfter("com.squareup.wire.ProtoAdapter#", missingDelimiterValue = "")
    ) {
      "STRING",
      "STRING_VALUE" -> "${field.name}-value"
      "BOOL",
      "BOOL_VALUE" -> true
      "INT32",
      "UINT32",
      "SINT32",
      "FIXED32",
      "SFIXED32",
      "INT32_VALUE",
      "UINT32_VALUE" -> 7
      "INT64",
      "UINT64",
      "SINT64",
      "FIXED64",
      "SFIXED64",
      "INT64_VALUE",
      "UINT64_VALUE" -> 11L
      "FLOAT",
      "FLOAT_VALUE" -> 1.5f
      "DOUBLE",
      "DOUBLE_VALUE" -> 2.25
      "BYTES",
      "BYTES_VALUE" -> "${field.name}-bytes".encodeUtf8()
      // Millisecond precision: the JSON and XML codecs write ISO-8601 instants with millis.
      "INSTANT" -> Instant.ofEpochSecond(1_767_225_600L, 125_000_000L)
      "DURATION" -> Duration.ofSeconds(90)
      "STRUCT_MAP" -> mapOf("key" to "value")
      "STRUCT_LIST" -> listOf("value")
      "STRUCT_VALUE" -> "value"
      "STRUCT_NULL" -> null
      "EMPTY" -> Unit
      "" ->
        if (adapterName == "com.squareup.wire.AnyMessage#ADAPTER") {
          AnyMessage(typeUrl = "type.googleapis.com/example.Extension", value = "x".encodeUtf8())
        } else {
          null
        }
      else -> error("No fixture value for $adapterName on $field")
    }

  /** Visits [root] and every message type reachable from it, once each. */
  fun walkMessageTypes(root: Class<*>, visit: (Class<*>) -> Unit) {
    val seen = mutableSetOf<Class<*>>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
      val type = queue.removeFirst()
      if (!seen.add(type)) continue
      visit(type)
      for (field in fieldsOf(type)) {
        val element = field.elementClass ?: continue
        if (Message::class.java.isAssignableFrom(element)) queue.add(element)
      }
    }
  }
}
