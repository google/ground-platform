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
package org.openforis.collectone.v2.buildlogic

import com.squareup.wire.schema.EnumType
import com.squareup.wire.schema.Extend
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.ProtoType
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaHandler
import com.squareup.wire.schema.Service
import com.squareup.wire.schema.Type
import okio.Path
import okio.buffer

/**
 * Wire schema handler that writes a Kotlin table of the messages and enums reachable from some root
 * messages: each message's fields (name, number, type, presence, `oneof`) in declaration order, and
 * each enum's constants.
 *
 * Wire's generated Kotlin classes carry no field metadata outside the JVM, so codecs in common code
 * (the ProtoJSON and textproto serializers of `shared/core`) read this table instead of naming
 * every field by hand. Configure it in a `wire { custom { … } }` block with these options:
 * - `roots`: comma-separated fully qualified message names to start from.
 * - `package`: package of the generated file.
 * - `name`: name of the generated `ProtoSchema` value (and of the file).
 *
 * Map fields are described like `protoc` does: as repeated messages of a synthetic
 * `<FieldName>Entry` type with a `key` (1) and a `value` (2) field.
 */
class ProtoSchemaDescriptorsFactory : SchemaHandler.Factory {
  override fun create(
    includes: List<String>,
    excludes: List<String>,
    exclusive: Boolean,
    outDirectory: String,
    options: Map<String, String>,
  ): SchemaHandler =
    ProtoSchemaDescriptorsHandler(
      roots = options.require("roots").split(',').map(String::trim).filter(String::isNotEmpty),
      packageName = options.require("package"),
      valueName = options.require("name"),
    )

  private fun Map<String, String>.require(key: String): String =
    get(key) ?: throw IllegalArgumentException("Missing option '$key'")

  private companion object {
    private const val serialVersionUID = 1L
  }
}

private class ProtoSchemaDescriptorsHandler(
  private val roots: List<String>,
  private val packageName: String,
  private val valueName: String,
) : SchemaHandler() {

  override fun handle(schema: Schema, context: Context) {
    val source = ProtoSchemaDescriptorsSource(schema, roots, packageName, valueName).render()
    val directory = context.outDirectory / packageName.replace('.', '/')
    context.fileSystem.createDirectories(directory)
    context.fileSystem.sink(directory / "$valueName.kt").buffer().use { it.writeUtf8(source) }
  }

  // Everything is written by handle(Schema, Context) above, as one file.
  override fun handle(type: Type, context: Context): Path? = null

  override fun handle(service: Service, context: Context): List<Path> = emptyList()

  override fun handle(extend: Extend, field: Field, context: Context): Path? = null
}

/** Renders the generated Kotlin source. */
private class ProtoSchemaDescriptorsSource(
  private val schema: Schema,
  private val roots: List<String>,
  private val packageName: String,
  private val valueName: String,
) {
  private val messages = linkedMapOf<String, List<FieldLine>>()
  private val mapEntries = mutableSetOf<String>()
  private val enums = linkedMapOf<String, EnumType>()

  fun render(): String {
    roots.forEach { visit(ProtoType.get(it)) }
    return buildString {
      appendLine(
        "// Generated from the .proto files by ProtoSchemaDescriptorsFactory (build-logic)."
      )
      appendLine("// Do not edit.")
      appendLine("package $packageName")
      appendLine()
      for (type in IMPORTED_TYPES) {
        appendLine("import org.openforis.collectone.v2.core.proto.schema.$type")
      }
      appendLine()
      appendLine("/** The messages and enums reachable from ${roots.joinToString()}. */")
      appendLine("internal val $valueName: ProtoSchema by lazy {")
      appendLine("  ProtoSchema(")
      appendLine("    messages =")
      appendLine("      listOf(")
      messages.keys.indices.forEach { appendLine("        message$it(),") }
      appendLine("      ),")
      appendLine("    enums =")
      appendLine("      listOf(")
      enums.keys.indices.forEach { appendLine("        enum$it(),") }
      appendLine("      ),")
      appendLine("  )")
      appendLine("}")
      messages.entries.forEachIndexed { i, (name, fields) ->
        appendLine()
        appendLine("private fun message$i() =")
        appendLine("  ProtoMessageDescriptor(")
        appendLine("    \"$name\",")
        appendLine("    listOf(")
        fields.forEach { appendLine("      ${it.render()},") }
        appendLine("    ),")
        if (name in mapEntries) appendLine("    isMapEntry = true,")
        appendLine("  )")
      }
      enums.entries.forEachIndexed { i, (name, type) ->
        appendLine()
        appendLine("private fun enum$i() =")
        appendLine("  ProtoEnumDescriptor(")
        appendLine("    \"$name\",")
        appendLine("    listOf(")
        type.constants.forEach { appendLine("      \"${it.name}\" to ${it.tag},") }
        appendLine("    ),")
        appendLine("  )")
      }
    }
  }

  private fun visit(type: ProtoType) {
    val name = type.toString()
    if (type.isScalar || name in messages || name in enums) return
    when (val resolved = schema.getType(type)) {
      is EnumType -> enums[name] = resolved
      is MessageType -> {
        messages[name] = emptyList() // Marks the type visited before recursing.
        messages[name] = WELL_KNOWN_TYPES[name] ?: fieldsOf(resolved)
        require(!name.startsWith("google.protobuf.") || name in WELL_KNOWN_TYPES) {
          "Add the layout of $name to WELL_KNOWN_TYPES"
        }
      }
      else -> throw IllegalArgumentException("Unknown or unsupported proto type $name")
    }
  }

  /** The fields of [message] in declaration order, `oneof` members included. */
  private fun fieldsOf(message: MessageType): List<FieldLine> =
    message.fieldsAndOneOfFields
      .sortedWith(compareBy({ it.location.line }, { it.location.column }))
      .map { field ->
        val type = field.type!!
        if (type.isMap) {
          val entryName = "${message.type}.${entryTypeName(field.name)}"
          visitMapEntry(entryName, type)
          FieldLine(field.name, field.tag, "MESSAGE", "REPEATED", typeName = entryName)
        } else {
          visit(type)
          FieldLine(
            name = field.name,
            number = field.tag,
            type = fieldType(type),
            label = label(field, type),
            oneof = message.oneOfs.firstOrNull { field in it.fields }?.name,
            typeName = typeNameOf(type),
          )
        }
      }

  private fun visitMapEntry(entryName: String, mapType: ProtoType) {
    if (entryName in messages) return
    mapEntries += entryName
    val key = mapType.keyType!!
    val value = mapType.valueType!!
    visit(value)
    // Entry fields are always written, like protoc's map entries.
    messages[entryName] =
      listOf(
        FieldLine("key", 1, fieldType(key), "EXPLICIT"),
        FieldLine("value", 2, fieldType(value), "EXPLICIT", typeName = typeNameOf(value)),
      )
  }

  private fun label(field: Field, type: ProtoType): String =
    when {
      field.isRepeated -> "REPEATED"
      field.isOneOf || field.label == Field.Label.OPTIONAL || fieldType(type) == "MESSAGE" ->
        "EXPLICIT"
      else -> "IMPLICIT"
    }

  private fun fieldType(type: ProtoType): String =
    when {
      type.isScalar -> type.simpleName.uppercase()
      schema.getType(type) is EnumType -> "ENUM"
      else -> "MESSAGE"
    }

  private fun typeNameOf(type: ProtoType): String? = if (type.isScalar) null else type.toString()

  private fun entryTypeName(fieldName: String): String =
    fieldName.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } + "Entry"

  private class FieldLine(
    val name: String,
    val number: Int,
    val type: String,
    val label: String,
    val oneof: String? = null,
    val typeName: String? = null,
  ) {
    fun render(): String = buildString {
      append(
        "ProtoFieldDescriptor(\"$name\", $number, ProtoFieldType.$type, ProtoFieldLabel.$label"
      )
      oneof?.let { append(", oneof = \"$it\"") }
      typeName?.let { append(", typeName = \"$it\"") }
      append(")")
    }
  }

  private companion object {
    val IMPORTED_TYPES =
      listOf(
        "ProtoEnumDescriptor",
        "ProtoFieldDescriptor",
        "ProtoFieldLabel",
        "ProtoFieldType",
        "ProtoMessageDescriptor",
        "ProtoSchema",
      )

    /**
     * Wire links only the referenced members of the types it loads from the proto path, like the
     * well-known `google.protobuf` types it bundles, so their fields aren't in the schema. Their
     * layout is fixed by the protobuf specification.
     */
    val WELL_KNOWN_TYPES =
      mapOf(
        "google.protobuf.Any" to
          listOf(
            FieldLine("type_url", 1, "STRING", "IMPLICIT"),
            FieldLine("value", 2, "BYTES", "IMPLICIT"),
          ),
        "google.protobuf.Duration" to
          listOf(
            FieldLine("seconds", 1, "INT64", "IMPLICIT"),
            FieldLine("nanos", 2, "INT32", "IMPLICIT"),
          ),
        "google.protobuf.Timestamp" to
          listOf(
            FieldLine("seconds", 1, "INT64", "IMPLICIT"),
            FieldLine("nanos", 2, "INT32", "IMPLICIT"),
          ),
      )
  }
}
