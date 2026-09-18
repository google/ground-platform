/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.serialization.textproto

/** Represents a parsed Protocol Buffer Text Format (`textproto`) message AST. */
internal data class TextProtoMessage(val fields: List<TextProtoField> = emptyList()) {

  /** Returns all field values matching [name]. */
  fun getFields(name: String): List<TextProtoValue> =
    fields.filter { it.name == name }.map { it.value }

  /** Returns the first field value matching [name], or null. */
  fun getField(name: String): TextProtoValue? = fields.firstOrNull { it.name == name }?.value

  /** Returns a string value for [name], or [default]. */
  fun getString(name: String, default: String = ""): String =
    getField(name)?.asStringOrNull() ?: default

  /** Returns a nullable string value for [name]. */
  fun getStringOrNull(name: String): String? = getField(name)?.asStringOrNull()

  /** Returns an Int value for [name], or [default]. */
  fun getInt(name: String, default: Int = 0): Int = getField(name)?.asIntOrNull() ?: default

  /** Returns a nullable Int value for [name]. */
  fun getIntOrNull(name: String): Int? = getField(name)?.asIntOrNull()

  /** Returns a Long value for [name], or [default]. */
  fun getLong(name: String, default: Long = 0L): Long = getField(name)?.asLongOrNull() ?: default

  /** Returns a nullable Long value for [name]. */
  fun getLongOrNull(name: String): Long? = getField(name)?.asLongOrNull()

  /** Returns a Double value for [name], or [default]. */
  fun getDouble(name: String, default: Double = 0.0): Double =
    getField(name)?.asDoubleOrNull() ?: default

  /** Returns a nullable Double value for [name]. */
  fun getDoubleOrNull(name: String): Double? = getField(name)?.asDoubleOrNull()

  /** Returns a Boolean value for [name], or [default]. */
  fun getBoolean(name: String, default: Boolean = false): Boolean =
    getField(name)?.asBooleanOrNull() ?: default

  /** Returns a nullable Boolean value for [name]. */
  fun getBooleanOrNull(name: String): Boolean? = getField(name)?.asBooleanOrNull()

  /** Returns an enum/identifier name for [name], or null. */
  fun getIdentifierOrNull(name: String): String? = getField(name)?.asIdentifierOrNull()

  /** Returns a nested [TextProtoMessage] for [name], or null. */
  fun getMessageOrNull(name: String): TextProtoMessage? = getField(name)?.asMessageOrNull()

  /**
   * Returns all nested [TextProtoMessage]s for repeated field [name] (handling list syntax too).
   */
  fun getMessages(name: String): List<TextProtoMessage> =
    getFields(name).flatMap { value ->
      when (value) {
        is TextProtoValue.MessageVal -> listOf(value.message)
        is TextProtoValue.ListVal -> value.elements.mapNotNull { it.asMessageOrNull() }
        else -> emptyList()
      }
    }

  /** Returns all string values for repeated field [name]. */
  fun getStrings(name: String): List<String> =
    getFields(name).flatMap { value ->
      when (value) {
        is TextProtoValue.ListVal -> value.elements.mapNotNull { it.asStringOrNull() }
        else -> listOfNotNull(value.asStringOrNull())
      }
    }

  /** Returns all identifier values for repeated field [name]. */
  fun getIdentifiers(name: String): List<String> =
    getFields(name).flatMap { value ->
      when (value) {
        is TextProtoValue.ListVal -> value.elements.mapNotNull { it.asIdentifierOrNull() }
        else -> listOfNotNull(value.asIdentifierOrNull())
      }
    }
}

/** Single `name: value` or `name { ... }` entry in a textproto message. */
internal data class TextProtoField(val name: String, val value: TextProtoValue)

/** Value node within a textproto AST. */
internal sealed class TextProtoValue {
  data class StringVal(val value: String) : TextProtoValue()

  data class NumberVal(val raw: String) : TextProtoValue()

  data class IdentifierVal(val name: String) : TextProtoValue()

  data class MessageVal(val message: TextProtoMessage) : TextProtoValue()

  data class ListVal(val elements: List<TextProtoValue>) : TextProtoValue()

  fun asStringOrNull(): String? =
    when (this) {
      is StringVal -> value
      is IdentifierVal -> name
      is NumberVal -> raw
      else -> null
    }

  fun asIntOrNull(): Int? =
    when (this) {
      is NumberVal -> raw.toIntOrNull()
      is StringVal -> value.toIntOrNull()
      else -> null
    }

  fun asLongOrNull(): Long? =
    when (this) {
      is NumberVal -> raw.toLongOrNull()
      is StringVal -> value.toLongOrNull()
      else -> null
    }

  fun asDoubleOrNull(): Double? =
    when (this) {
      is NumberVal -> raw.toDoubleOrNull()
      is IdentifierVal ->
        when (name.lowercase()) {
          "nan" -> Double.NaN
          "inf",
          "infinity" -> Double.POSITIVE_INFINITY
          "-inf",
          "-infinity" -> Double.NEGATIVE_INFINITY
          else -> name.toDoubleOrNull()
        }
      is StringVal -> value.toDoubleOrNull()
      else -> null
    }

  fun asBooleanOrNull(): Boolean? =
    when (this) {
      is IdentifierVal ->
        when (name.lowercase()) {
          "true",
          "t",
          "1" -> true
          "false",
          "f",
          "0" -> false
          else -> null
        }
      is NumberVal ->
        when (raw) {
          "1" -> true
          "0" -> false
          else -> null
        }
      is StringVal -> value.toBooleanStrictOrNull()
      else -> null
    }

  fun asIdentifierOrNull(): String? =
    when (this) {
      is IdentifierVal -> name
      is StringVal -> value
      else -> null
    }

  fun asMessageOrNull(): TextProtoMessage? = (this as? MessageVal)?.message
}
