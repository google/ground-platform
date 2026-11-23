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

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer
import okio.ByteString
import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef

class DynamicMessageTest {

  private val form =
    FormDef(
      form_id = "trees",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              default_values =
                RecordNode(
                  fields = mapOf("count" to FieldValue(scalar_value = TypedValue(int32_value = 0)))
                )
            ),
          actions = listOf(ActionDef(events = listOf(EventType.EVENT_VALUE_CHANGED))),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    choices = listOf(ChoiceItem(properties = mapOf("b" to "2", "a" to "")))
                  )
              )
            )
        ),
      sort_order = -1,
    )

  @Test
  fun readThenDecode_returnsTheSameMessage() {
    val read = CoreProtoSchema.read(FormDef.ADAPTER, form)

    assertEquals(form, read.decodeAs(FormDef.ADAPTER))
  }

  @Test
  fun setFields_areTheNonDefaultFieldsInDeclarationOrder() {
    val read = CoreProtoSchema.read(FormDef.ADAPTER, form)

    assertEquals(listOf("form_id", "model", "view", "sort_order"), read.setFields.map { it.name })
    assertEquals(listOf<Any>("trees"), read.values("form_id"))
    assertEquals(listOf<Any>(-1), read.values("sort_order"))
  }

  @Test
  fun messageFields_holdTheEncodedSubMessage() {
    val read = CoreProtoSchema.read(FormDef.ADAPTER, form)

    val view = read.values("view").single() as ByteString

    assertEquals(form.view, ViewDef.ADAPTER.decode(view))
  }

  @Test
  fun packedRepeatedNumbers_readAsElements() {
    val packed = Buffer().apply { write(byteArrayOf(1, 3)) }.readByteString()
    val bytes = encode { ProtoAdapter.BYTES.encodeWithTag(it, 1, packed) }

    val read = CoreProtoSchema.read(CoreProtoSchema.messageFor(ActionDef.ADAPTER), bytes)

    assertEquals(listOf<Any>(1, 3), read.values("events"))
  }

  @Test
  fun unknownFieldsAndMismatchedEncodings_areSkipped() {
    val bytes = encode {
      ProtoAdapter.STRING.encodeWithTag(it, 99, "unknown")
      ProtoAdapter.STRING.encodeWithTag(it, 2, "type, but length-delimited")
      ProtoAdapter.STRING.encodeWithTag(it, 3, "target")
    }

    val read = CoreProtoSchema.read(CoreProtoSchema.messageFor(ActionDef.ADAPTER), bytes)

    assertEquals(listOf("target_field"), read.setFields.map { it.name })
  }

  @Test
  fun theLastOneofMemberRead_wins() {
    val bytes = encode {
      ProtoAdapter.INT32.encodeWithTag(it, 2, 5)
      ProtoAdapter.STRING.encodeWithTag(it, 1, "last")
    }

    val read = CoreProtoSchema.read(CoreProtoSchema.messageFor(TypedValue.ADAPTER), bytes)

    assertEquals(TypedValue(string_value = "last"), read.decodeAs(TypedValue.ADAPTER))
  }

  @Test
  fun isDefaultValue_treatsNegativeZeroAsZero() {
    assertTrue(isDefaultValue(ProtoFieldType.DOUBLE, -0.0))
    assertTrue(isDefaultValue(ProtoFieldType.STRING, ""))
    assertTrue(!isDefaultValue(ProtoFieldType.INT32, 1))
  }

  @Test
  fun mapFields_areRepeatedEntryMessages() {
    val choice = CoreProtoSchema.message("openforis.collectone.v1.forms.ChoiceItem")
    val properties = choice.field("properties")
    val entry = CoreProtoSchema.messageOf(properties)

    assertTrue(properties.isRepeated)
    assertTrue(entry.isMapEntry)
    assertEquals("ChoiceItem.PropertiesEntry", entry.simpleName)
    assertEquals(listOf("key", "value"), entry.fields.map { it.name })
  }

  private fun encode(write: (ProtoWriter) -> Unit): ByteString {
    val buffer = Buffer()
    write(ProtoWriter(buffer))
    return buffer.readByteString()
  }
}
