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
 * Field-level exceptions to the generic proto3 mapping of the schema-driven codecs, for the places
 * where the established ProtoJSON or textproto shape of a message differs from it.
 *
 * Fields are named `Message.field` with the message's simple name (see
 * [ProtoMessageDescriptor.simpleName]), like `ActionDef.events`.
 */
internal class ProtoCodecRules(
  /** Fields neither written nor read. */
  val ignoredFields: Set<String> = emptySet(),
  /** Explicit-presence fields left out when set to their default value, like implicit ones. */
  val omittedWhenDefault: Set<String> = emptySet(),
  /** Implicit-presence fields written even when they hold their default value. */
  val alwaysWritten: Set<String> = emptySet(),
  /** Message fields left out when the message would be written empty. */
  val omittedWhenEmpty: Set<String> = emptySet(),
  /**
   * Message fields left out when the set message has no value for a field of its own:
   * `"PrimaryInstance.default_values" to "fields"` drops default values without any field.
   */
  val omittedWithout: Map<String, String> = emptyMap(),
  /** Repeated enum fields whose zero (`…_UNSPECIFIED`) elements are dropped when written. */
  val zeroEnumsDropped: Set<String> = emptySet(),
  /** `oneof` members that win when read alongside another member of their `oneof`. */
  val preferredOneofMembers: Set<String> = emptySet(),
  /**
   * Write order of the fields of some messages (simple name to field names), if not declaration.
   */
  val fieldOrder: Map<String, List<String>> = emptyMap(),
  /**
   * Packages whose messages read enum values strictly: the exact constant name or an
   * [IllegalArgumentException]. Elsewhere names match case-insensitively and unknown names read as
   * the zero constant.
   */
  val strictEnumPackages: Set<String> = emptySet(),
) {
  /** These rules and [other]'s together. */
  operator fun plus(other: ProtoCodecRules): ProtoCodecRules =
    ProtoCodecRules(
      ignoredFields = ignoredFields + other.ignoredFields,
      omittedWhenDefault = omittedWhenDefault + other.omittedWhenDefault,
      alwaysWritten = alwaysWritten + other.alwaysWritten,
      omittedWhenEmpty = omittedWhenEmpty + other.omittedWhenEmpty,
      omittedWithout = omittedWithout + other.omittedWithout,
      zeroEnumsDropped = zeroEnumsDropped + other.zeroEnumsDropped,
      preferredOneofMembers = preferredOneofMembers + other.preferredOneofMembers,
      fieldOrder = fieldOrder + other.fieldOrder,
      strictEnumPackages = strictEnumPackages + other.strictEnumPackages,
    )

  /** The fields of [message] in the order they are written; unlisted fields go last. */
  fun writeOrder(message: ProtoMessageDescriptor): List<ProtoFieldDescriptor> {
    val listed = fieldOrder[message.simpleName]?.map(message::field) ?: return message.fields
    return listed + (message.fields - listed.toSet())
  }

  /**
   * The number of the [enum] constant [name], read in a field of [message]: see
   * [strictEnumPackages].
   */
  fun parseEnum(
    message: ProtoMessageDescriptor,
    enum: ProtoEnumDescriptor,
    name: String,
  ): Int {
    if (message.packageName in strictEnumPackages) {
      return enum.numberOf(name)
        ?: throw IllegalArgumentException(
          "Unknown ${enum.fullName.substringAfterLast('.')} value '$name'"
        )
    }
    val match = enum.names.firstOrNull { it.equals(name, ignoreCase = true) }
    return match?.let(enum::numberOf) ?: 0
  }

  /**
   * Applies `oneof` semantics to the fields read into [builder]: [preferredOneofMembers] win, and
   * any other conflict is an [IllegalArgumentException], like the generated constructors throw.
   */
  fun resolveOneofs(message: ProtoMessageDescriptor, builder: DynamicMessage.Builder) {
    message.fields
      .filter { it.oneof != null && builder.has(it) }
      .groupBy { it.oneof }
      .values
      .filter { it.size > 1 }
      .forEach { members ->
        val preferred = members.firstOrNull {
          "${message.simpleName}.${it.name}" in preferredOneofMembers
        }
        requireNotNull(preferred) {
          "At most one of ${members.joinToString()} may be set in ${message.simpleName}"
        }
        members.filter { it != preferred }.forEach(builder::remove)
      }
  }
}

/** The package of this message, e.g. `openforis.collectone.v1.forms`. */
internal val ProtoMessageDescriptor.packageName: String
  get() = fullName.removeSuffix(".$simpleName")

/** This field named `Message.field`, as in [ProtoCodecRules]. */
internal fun ProtoMessageDescriptor.qualifiedName(field: ProtoFieldDescriptor): String =
  "$simpleName.${field.name}"
