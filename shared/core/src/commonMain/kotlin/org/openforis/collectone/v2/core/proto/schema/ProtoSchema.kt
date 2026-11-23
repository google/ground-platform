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

/**
 * The shape of a set of proto messages and enums, generated at build time from the `.proto` files
 * (see `ProtoSchemaDescriptorsFactory` in `build-logic`).
 *
 * Wire's generated classes don't expose their fields outside the JVM, so the codecs in common code
 * walk messages through this table and Wire's binary encoding (see [DynamicMessage]).
 */
internal class ProtoSchema(
  messages: List<ProtoMessageDescriptor>,
  enums: List<ProtoEnumDescriptor>,
) {
  private val messagesByName = messages.associateBy { it.fullName }
  private val enumsByName = enums.associateBy { it.fullName }

  /** The message named [fullName], e.g. `openforis.collectone.v1.forms.FormDef`. */
  fun message(fullName: String): ProtoMessageDescriptor =
    messagesByName[fullName] ?: throw IllegalArgumentException("Unknown proto message $fullName")

  /** The enum named [fullName]. */
  fun enum(fullName: String): ProtoEnumDescriptor =
    enumsByName[fullName] ?: throw IllegalArgumentException("Unknown proto enum $fullName")

  /** The message type of [field], which must be a message field. */
  fun messageOf(field: ProtoFieldDescriptor): ProtoMessageDescriptor =
    message(checkNotNull(field.typeName) { "${field.name} is not a message field" })

  /** The enum type of [field], which must be an enum field. */
  fun enumOf(field: ProtoFieldDescriptor): ProtoEnumDescriptor =
    enum(checkNotNull(field.typeName) { "${field.name} is not an enum field" })
}

/** A message type and its fields in declaration order. */
internal class ProtoMessageDescriptor(
  val fullName: String,
  val fields: List<ProtoFieldDescriptor>,
  /** Whether this is the synthetic entry type of a `map<…>` field (`key` = 1, `value` = 2). */
  val isMapEntry: Boolean = false,
) {
  private val fieldsByNumber = fields.associateBy { it.number }
  private val fieldsByName = fields.associateBy { it.name }

  /** The simple name, e.g. `FormDef`, or `LanguageTranslation.StringsEntry` for a map entry. */
  val simpleName: String
    get() =
      if (isMapEntry) fullName.split('.').takeLast(2).joinToString(".")
      else fullName.substringAfterLast('.')

  fun field(number: Int): ProtoFieldDescriptor? = fieldsByNumber[number]

  fun field(name: String): ProtoFieldDescriptor =
    fieldsByName[name] ?: throw IllegalArgumentException("$fullName has no field $name")
}

/** One field of a message. */
internal class ProtoFieldDescriptor(
  val name: String,
  val number: Int,
  val type: ProtoFieldType,
  val label: ProtoFieldLabel,
  /** The `oneof` this field belongs to, if any. */
  val oneof: String? = null,
  /** The fully qualified message or enum type, for [ProtoFieldType.MESSAGE] and `ENUM` fields. */
  val typeName: String? = null,
) {
  val isRepeated: Boolean
    get() = label == ProtoFieldLabel.REPEATED

  override fun toString(): String = name
}

/** How a field is present in a message. */
internal enum class ProtoFieldLabel {
  /** A proto3 scalar without `optional`: unset when it holds the default value. */
  IMPLICIT,
  /** A message, `oneof` member, `optional` scalar, or map entry field: set or unset. */
  EXPLICIT,
  REPEATED,
}

/** The proto type of a field. */
internal enum class ProtoFieldType {
  DOUBLE,
  FLOAT,
  INT32,
  INT64,
  UINT32,
  UINT64,
  SINT32,
  SINT64,
  FIXED32,
  FIXED64,
  SFIXED32,
  SFIXED64,
  BOOL,
  STRING,
  BYTES,
  ENUM,
  MESSAGE,
}

/** An enum type and its constants. */
internal class ProtoEnumDescriptor(val fullName: String, constants: List<Pair<String, Int>>) {
  private val namesByNumber = constants.associate { (name, number) -> number to name }
  private val numbersByName = constants.toMap()

  /** The constant names, in declaration order. */
  val names: List<String> = constants.map { it.first }

  fun nameOf(number: Int): String? = namesByNumber[number]

  fun numberOf(name: String): Int? = numbersByName[name]
}
