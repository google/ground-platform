/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.serialization

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordSchema

/**
 * Primary Kotlin Multiplatform serializer and deserializer for XForms XML documents to and from
 * ProtoForms protocol buffer models ([FormDef] and [RecordInstance]).
 */
object XFormsXmlSerializer {

  /**
   * Deserializes an XForms XML form definition (`<h:html>...</h:html>`) into a [FormDef] protobuf
   * model.
   */
  fun deserializeFormDef(xml: String): FormDef = FormDefXmlDeserializer.deserialize(xml)

  /**
   * Serializes a [FormDef] protobuf model into an XForms XML form definition string
   * (`<h:html>...</h:html>`).
   */
  fun serialize(formDef: FormDef, prettyPrint: Boolean = true): String =
    FormDefXmlSerializer.serialize(formDef, prettyPrint = prettyPrint)

  /** Explicit alias for [serialize] on a [FormDef] protobuf model. */
  fun serializeFormDef(formDef: FormDef, prettyPrint: Boolean = true): String =
    FormDefXmlSerializer.serialize(formDef, prettyPrint = prettyPrint)

  /**
   * Deserializes an XForms submission instance XML (`<data id="...">...</data>`) into a
   * [RecordInstance] protobuf model.
   *
   * @param xml The XML string representing the submission instance.
   * @param schema Optional [RecordSchema] to guide field type resolution.
   * @param formDef Optional [FormDef] from which schema and bindings can be resolved.
   */
  fun deserializeRecordInstance(
    xml: String,
    schema: RecordSchema? = null,
    formDef: FormDef? = null,
  ): RecordInstance =
    RecordInstanceXmlSerializer.deserialize(xml = xml, schema = schema, formDef = formDef)

  /**
   * Serializes a [RecordInstance] protobuf model into an XForms submission instance XML string
   * (`<data id="...">...</data>`).
   */
  fun serialize(
    record: RecordInstance,
    rootElementName: String = "data",
    prettyPrint: Boolean = true,
  ): String =
    RecordInstanceXmlSerializer.serialize(
      record = record,
      rootElementName = rootElementName,
      prettyPrint = prettyPrint,
    )

  /** Explicit alias for [serialize] on a [RecordInstance] protobuf model. */
  fun serializeRecordInstance(
    record: RecordInstance,
    rootElementName: String = "data",
    prettyPrint: Boolean = true,
  ): String =
    RecordInstanceXmlSerializer.serialize(
      record = record,
      rootElementName = rootElementName,
      prettyPrint = prettyPrint,
    )

  /** Formats a [FormDef] protobuf model into a `textproto` string. */
  fun serializeFormDefToTextProto(formDef: FormDef): String =
    TextProtoSerializer.serializeFormDef(formDef)

  /** Parses a `textproto` string into a [FormDef] protobuf model. */
  fun deserializeFormDefFromTextProto(textproto: String): FormDef =
    TextProtoSerializer.deserializeFormDef(textproto)

  /** Formats a [RecordInstance] protobuf model into a `textproto` string. */
  fun serializeRecordInstanceToTextProto(record: RecordInstance): String =
    TextProtoSerializer.serializeRecordInstance(record)

  /** Parses a `textproto` string into a [RecordInstance] protobuf model. */
  fun deserializeRecordInstanceFromTextProto(textproto: String): RecordInstance =
    TextProtoSerializer.deserializeRecordInstance(textproto)
}
