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

import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibrarySettings
import org.openforis.collectone.v2.core.proto.FullyPopulatedProtos

/**
 * Checks the generated [CoreProtoSchema] against the `@WireField` metadata of Wire's generated
 * classes, so the two code generators can't drift apart.
 */
class CoreProtoSchemaTest {

  @Test
  fun everyGeneratedMessage_hasADescriptorWithTheSameFields() {
    val mismatches = mutableListOf<String>()
    for (root in ROOTS) {
      FullyPopulatedProtos.walkMessageTypes(root) { type ->
        val descriptor = CoreProtoSchema.messageFor(adapterOf(type))
        val expected = FullyPopulatedProtos.fieldsOf(type).map(::describe)
        val actual = descriptor.fields.map(::describe)
        if (expected != actual) mismatches += "${type.simpleName}: $expected != $actual"
      }
    }
    assertEquals(emptyList(), mismatches)
  }

  @Test
  fun simpleNames_areUnique() {
    val names = mutableMapOf<String, String>()
    for (root in ROOTS) {
      FullyPopulatedProtos.walkMessageTypes(root) { type ->
        val descriptor = CoreProtoSchema.messageFor(adapterOf(type))
        val previous = names.put(descriptor.simpleName, descriptor.fullName)
        assertEquals(previous ?: descriptor.fullName, descriptor.fullName)
      }
    }
  }

  private fun adapterOf(type: Class<*>) =
    type.getField("ADAPTER").get(null) as com.squareup.wire.ProtoAdapter<*>

  private fun describe(field: FullyPopulatedProtos.ProtoField): String {
    val label =
      when {
        field.isRepeated || field.isMap -> "repeated"
        // Wire omits unset messages too; only scalars and enums have implicit presence.
        field.annotation.label == WireField.Label.OMIT_IDENTITY && isScalarOrEnum(field) ->
          "implicit"
        else -> "explicit"
      }
    return "${field.name}=${field.annotation.tag} $label ${field.oneofName}"
  }

  private fun isScalarOrEnum(field: FullyPopulatedProtos.ProtoField): Boolean {
    val element = field.elementClass
    return if (element != null) WireEnum::class.java.isAssignableFrom(element)
    else field.annotation.adapter.substringAfter('#') in SCALAR_ADAPTERS
  }

  private fun describe(field: ProtoFieldDescriptor): String {
    val label =
      when (field.label) {
        ProtoFieldLabel.REPEATED -> "repeated"
        ProtoFieldLabel.IMPLICIT -> "implicit"
        ProtoFieldLabel.EXPLICIT -> "explicit"
      }
    return "${field.name}=${field.number} $label ${field.oneof.orEmpty()}"
  }

  private companion object {
    val SCALAR_ADAPTERS =
      setOf(
        "BOOL",
        "BYTES",
        "DOUBLE",
        "FIXED32",
        "FIXED64",
        "FLOAT",
        "INT32",
        "INT64",
        "SFIXED32",
        "SFIXED64",
        "SINT32",
        "SINT64",
        "STRING",
        "UINT32",
        "UINT64",
      )

    val ROOTS =
      listOf(
        FormDef::class.java,
        RecordInstance::class.java,
        LibraryBundle::class.java,
        OrganizationLibrarySettings::class.java,
      )
  }
}
