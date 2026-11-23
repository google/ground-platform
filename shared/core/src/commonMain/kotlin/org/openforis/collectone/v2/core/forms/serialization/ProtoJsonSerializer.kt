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
package org.openforis.collectone.v2.core.forms.serialization

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ofEpochSecond
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordNodeList
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.TypedValueList
import org.openforis.collectone.v2.core.forms.serialization.json.ProtoJsonCodec
import org.openforis.collectone.v2.core.forms.serialization.json.ProtoJsonFieldReader
import org.openforis.collectone.v2.core.forms.serialization.json.ProtoJsonTypeMapping
import org.openforis.collectone.v2.core.forms.serialization.json.getProtoElement
import org.openforis.collectone.v2.core.forms.xpath.model.TemporalUtils
import org.openforis.collectone.v2.core.proto.schema.CoreProtoSchema

/**
 * Serializer and deserializer between ProtoForms Protocol Buffer models ([FormDef] and
 * [RecordInstance]) and ProtoJSON.
 *
 * Uses a compact, human-debuggable flattened mapping (modeled after `google.protobuf.Struct` /
 * `google.protobuf.Value` in the
 * [Proto3 JSON Specification](https://protobuf.dev/programming-guides/proto3/#json)) for dynamic
 * form records ([RecordNode], [FieldValue], [TypedValue]) and geospatial coordinates:
 * - [RecordNode] maps directly to a flat JSON object (`{"species": "Adansonia digitata",
 *   "height_m": 24.8}`) without intermediate `"fields"`, `"scalarValue"`, or `"stringValue"`
 *   wrapper objects.
 * - Nested groups ([FieldValue.node_value]) serialize as nested JSON objects, repeat groups
 *   ([FieldValue.repeat_value]) as JSON arrays of objects, and multi-select lists
 *   ([FieldValue.list_value]) as JSON arrays of scalar values.
 * - [GeoPoint] values serialize as compact `[lat, lon]` or `[lat, lon, alt, acc]` coordinate
 *   arrays, and [GeoTrace] / [GeoShape] values serialize as 2D coordinate arrays `[[lat, lon],
 *   ...]`.
 * - Deserialization accepts both the flattened representation and legacy canonical ProtoJSON
 *   wrapper objects (`fields`, `scalarValue`, `stringValue`, `{"latitude": ..., "longitude":
 *   ...}`).
 * - Timestamps are RFC 3339 strings and dates `YYYY-MM-DD` strings.
 *
 * Every other message maps field by field through [ProtoJsonCodec], from the schema generated out
 * of the `.proto` files; [FormCodecRules] lists where the format departs from the plain mapping.
 */
object ProtoJsonSerializer {

  private const val MAX_SAFE_JS_INTEGER = 9007199254740991L

  @OptIn(ExperimentalSerializationApi::class)
  private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  private val compactJson = Json { prettyPrint = false }

  /** Formats a [FormDef] protobuf message into a JSON string. */
  fun serializeFormDef(
    formDef: FormDef,
    prettyPrint: Boolean = true,
    preserveProtoFieldNames: Boolean = false,
  ): String = format(codec.write(FormDef.ADAPTER, formDef, preserveProtoFieldNames), prettyPrint)

  /** Parses a JSON string into a [FormDef] protobuf message. */
  fun deserializeFormDef(json: String): FormDef =
    codec.read(FormDef.ADAPTER, parseObject(json, "FormDef"), formFieldReaders)

  /** Formats a [RecordInstance] protobuf message into a JSON string. */
  fun serializeRecordInstance(
    record: RecordInstance,
    prettyPrint: Boolean = true,
    preserveProtoFieldNames: Boolean = false,
  ): String =
    format(codec.write(RecordInstance.ADAPTER, record, preserveProtoFieldNames), prettyPrint)

  /**
   * Parses a JSON string into a [RecordInstance] protobuf message.
   *
   * Optionally accepts [formDef] or [schema] to guide exact field type disambiguation when
   * deserializing flattened JSON values.
   */
  fun deserializeRecordInstance(
    json: String,
    formDef: FormDef? = null,
    schema: RecordSchema? = formDef?.model?.primary_instance?.record_schema,
  ): RecordInstance {
    val schemaFields = buildSchemaFieldMap(formDef, schema)
    val dataReader = ProtoJsonFieldReader { element, _ ->
      (element as? JsonObject)?.let {
        RecordNode.ADAPTER.encodeByteString(decodeRecordNode(it, schemaFields))
      }
    }
    return codec.read(
      RecordInstance.ADAPTER,
      parseObject(json, "RecordInstance"),
      formFieldReaders + ("RecordInstance.data" to dataReader),
    )
  }

  private fun format(json: JsonObject, prettyPrint: Boolean): String =
    if (prettyPrint) prettyJson.encodeToString(JsonObject.serializer(), json)
    else compactJson.encodeToString(JsonObject.serializer(), json)

  private fun parseObject(json: String, messageName: String): JsonObject {
    val element =
      try {
        compactJson.parseToJsonElement(json)
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
      }
    return element as? JsonObject
      ?: throw IllegalArgumentException("Expected JSON object at root for $messageName")
  }

  // ===========================================================================
  // Schema-driven mapping
  // ===========================================================================

  /** Message types with their own JSON shape (see the class documentation). */
  private val typeMappings: Map<String, ProtoJsonTypeMapping> =
    mapOf(
      "openforis.collectone.v1.forms.RecordNode" to
        typeMapping(
          RecordNode.ADAPTER,
          toJson = ::encodeRecordNode,
          fromJson = { (it as? JsonObject)?.let { node -> decodeRecordNode(node) } },
        ),
      "openforis.collectone.v1.forms.TypedValue" to
        typeMapping(
          TypedValue.ADAPTER,
          toJson = ::encodeTypedValueElement,
          fromJson = { decodeTypedValueElement(it) },
        ),
      "openforis.collectone.v1.forms.GeoPoint" to
        typeMapping(
          GeoPoint.ADAPTER,
          toJson = { point, _ -> encodeGeoPointArray(point) },
          fromJson = ::decodeGeoPointElement,
        ),
      "google.protobuf.Timestamp" to
        typeMapping(
          ProtoAdapter.INSTANT,
          toJson = { instant, _ -> JsonPrimitive(formatRfc3339Timestamp(instant)) },
          fromJson = ::decodeTimestampElement,
        ),
      "google.type.Date" to
        typeMapping(
          Date.ADAPTER,
          toJson = { date, _ -> JsonPrimitive(TemporalUtils.formatDate(date)) },
          fromJson = ::decodeDateElement,
        ),
    )

  /** Default values are typed by the record schema read before them. */
  private val formFieldReaders =
    mapOf(
      "PrimaryInstance.default_values" to
        ProtoJsonFieldReader { element, primaryInstance ->
          val schema =
            (primaryInstance.values("record_schema").singleOrNull() as ByteString?)?.let(
              RecordSchema.ADAPTER::decode
            )
          (element as? JsonObject)?.let {
            val schemaFields = schema?.fields?.associateBy { f -> f.name } ?: emptyMap()
            RecordNode.ADAPTER.encodeByteString(decodeRecordNode(it, schemaFields))
          }
        }
    )

  private val codec = ProtoJsonCodec(CoreProtoSchema, FormCodecRules, typeMappings)

  private fun <M : Any> typeMapping(
    adapter: ProtoAdapter<M>,
    toJson: (M, Boolean) -> JsonElement?,
    fromJson: (JsonElement) -> M?,
  ): ProtoJsonTypeMapping =
    object : ProtoJsonTypeMapping {
      override fun write(message: ByteString, preserveProtoFieldNames: Boolean): JsonElement? =
        toJson(adapter.decode(message), preserveProtoFieldNames)

      override fun read(element: JsonElement): ByteString? =
        fromJson(element)?.let(adapter::encodeByteString)
    }

  // ===========================================================================
  // RecordNode, TypedValue, and geometry mapping
  // ===========================================================================

  private fun encodeRecordNode(msg: RecordNode, snake: Boolean): JsonObject {
    val mapObj = linkedMapOf<String, JsonElement>()
    msg.fields.forEach { (key, value) ->
      encodeFieldValueElement(value, snake)?.let { mapObj[key] = it }
    }
    return JsonObject(mapObj)
  }

  private fun encodeFieldValueElement(msg: FieldValue, snake: Boolean): JsonElement? =
    when {
      msg.scalar_value != null -> encodeTypedValueElement(msg.scalar_value, snake)
      msg.list_value != null ->
        JsonArray(msg.list_value.values.mapNotNull { encodeTypedValueElement(it, snake) })
      msg.node_value != null -> encodeRecordNode(msg.node_value, snake)
      msg.repeat_value != null ->
        JsonArray(msg.repeat_value.nodes.map { encodeRecordNode(it, snake) })
      else -> null
    }

  @OptIn(ExperimentalSerializationApi::class)
  private fun formatDoublePrimitive(value: Double): JsonPrimitive {
    // Proto3 canonical JSON encodes non-finite doubles as quoted strings. They must be handled
    // before the "add a decimal point" normalization below, which would otherwise turn the token
    // "NaN" into the unquoted literal `NaN.0` -- output that no JSON parser can read.
    if (value.isNaN()) return JsonPrimitive("NaN")
    if (value == Double.POSITIVE_INFINITY) return JsonPrimitive("Infinity")
    if (value == Double.NEGATIVE_INFINITY) return JsonPrimitive("-Infinity")
    val s = value.toString()
    val formatted =
      if (!s.contains('.') && !s.contains('e') && !s.contains('E')) {
        "$s.0"
      } else {
        s
      }
    return JsonUnquotedLiteral(formatted)
  }

  private fun encodeTypedValueElement(msg: TypedValue, snake: Boolean): JsonElement? =
    when {
      msg.string_value != null -> {
        val str = msg.string_value
        if (looksLikeTemporalLiteral(str)) {
          JsonObject(mapOf("string" to JsonPrimitive(str)))
        } else {
          JsonPrimitive(str)
        }
      }
      msg.int32_value != null -> JsonPrimitive(msg.int32_value)
      msg.int64_value != null -> {
        val v = msg.int64_value
        if (
          v !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
            v in -MAX_SAFE_JS_INTEGER..MAX_SAFE_JS_INTEGER
        ) {
          JsonPrimitive(v)
        } else {
          val keyName = if (snake) "int64_value" else "int64"
          JsonObject(mapOf(keyName to JsonPrimitive(v.toString())))
        }
      }
      msg.double_value != null -> formatDoublePrimitive(msg.double_value)
      msg.bool_value != null -> JsonPrimitive(msg.bool_value)
      msg.date_value != null -> JsonPrimitive(TemporalUtils.formatDate(msg.date_value))
      msg.time_value != null -> JsonPrimitive(formatIsoTimeOfDay(msg.time_value))
      msg.timestamp_value != null -> JsonPrimitive(formatRfc3339Timestamp(msg.timestamp_value))
      msg.geopoint_value != null -> encodeGeoPointArray(msg.geopoint_value)
      msg.binary_value != null ->
        JsonObject(mapOf("base64" to JsonPrimitive(msg.binary_value.base64())))
      msg.geotrace_value != null -> {
        val pts = msg.geotrace_value.points
        val coordsArray = JsonArray(pts.map { encodeGeoPointArray(it) })
        if (pts.isNotEmpty() && (pts.size < 4 || pts.first() != pts.last())) {
          coordsArray
        } else {
          JsonObject(mapOf("geotrace" to coordsArray))
        }
      }
      msg.geoshape_value != null -> {
        val pts = msg.geoshape_value.points
        val coordsArray = JsonArray(pts.map { encodeGeoPointArray(it) })
        if (pts.size >= 4 && pts.first() == pts.last()) {
          coordsArray
        } else {
          JsonObject(mapOf("geoshape" to coordsArray))
        }
      }
      else -> null
    }

  private fun encodeGeoPointArray(msg: GeoPoint): JsonArray {
    val elements =
      mutableListOf<JsonElement>(
        formatDoublePrimitive(msg.latitude),
        formatDoublePrimitive(msg.longitude),
      )
    val alt = msg.altitude_meters ?: 0.0
    val acc = msg.accuracy_meters ?: 0.0
    if (alt != 0.0 || acc != 0.0) {
      elements.add(formatDoublePrimitive(alt))
      if (acc != 0.0) {
        elements.add(formatDoublePrimitive(acc))
      }
    }
    return JsonArray(elements)
  }

  private fun buildSchemaFieldMap(
    formDef: FormDef?,
    schema: RecordSchema?,
  ): Map<String, FieldDefinition> {
    val result = linkedMapOf<String, FieldDefinition>()
    schema?.fields?.forEach { result[it.name] = it }
    formDef?.model?.bindings?.forEach { binding ->
      if (binding.type != DataType.DATA_TYPE_UNSPECIFIED) {
        val leafName = binding.field_path.trim('/').substringAfterLast('/')
        if (leafName.isNotEmpty() && !result.containsKey(leafName)) {
          result[leafName] = FieldDefinition(name = leafName, type = binding.type)
        }
      }
    }
    return result
  }

  private fun isLegacyFieldValueObject(obj: JsonObject): Boolean =
    obj.containsKey("scalarValue") ||
      obj.containsKey("scalar_value") ||
      obj.containsKey("listValue") ||
      obj.containsKey("list_value") ||
      obj.containsKey("nodeValue") ||
      obj.containsKey("node_value") ||
      obj.containsKey("repeatValue") ||
      obj.containsKey("repeat_value")

  private fun isLegacyFieldsContainer(obj: JsonObject): Boolean {
    if (obj.size != 1 || !obj.containsKey("fields")) return false
    return when (val raw = obj["fields"]) {
      is JsonArray ->
        raw.all { it is JsonObject && it.containsKey("key") && it.containsKey("value") }
      is JsonObject -> raw.values.all { it is JsonObject && isLegacyFieldValueObject(it) }
      else -> false
    }
  }

  private fun decodeRecordNode(
    obj: JsonObject,
    schemaFields: Map<String, FieldDefinition> = emptyMap(),
  ): RecordNode {
    val fieldsMap = linkedMapOf<String, FieldValue>()
    if (isLegacyFieldsContainer(obj)) {
      when (val rawFields = obj["fields"]) {
        is JsonObject -> {
          rawFields.forEach { (key, valueEl) ->
            val fieldDef = schemaFields[key]
            decodeFieldValueElement(valueEl, fieldDef)?.let { fieldsMap[key] = it }
          }
        }
        is JsonArray -> {
          rawFields.forEach { entryEl ->
            val entry = entryEl as? JsonObject ?: return@forEach
            val key = entry.getProtoString("key")
            val valueEl = entry["value"] ?: return@forEach
            val fieldDef = schemaFields[key]
            decodeFieldValueElement(valueEl, fieldDef)?.let { fieldsMap[key] = it }
          }
        }
        else -> {}
      }
      return RecordNode(fields = fieldsMap)
    }

    obj.forEach { (key, valueEl) ->
      if (valueEl !is JsonNull) {
        val fieldDef = schemaFields[key]
        decodeFieldValueElement(valueEl, fieldDef)?.let { fieldsMap[key] = it }
      }
    }
    return RecordNode(fields = fieldsMap)
  }

  private fun decodeFieldValueElement(
    element: JsonElement,
    fieldDef: FieldDefinition? = null,
  ): FieldValue? {
    val expectedType =
      fieldDef?.type?.takeIf { it != DataType.DATA_TYPE_UNSPECIFIED && !fieldDef.is_repeated }
    return when (element) {
      is JsonPrimitive ->
        decodeTypedValueElement(element, expectedType)?.let { FieldValue(scalar_value = it) }
      is JsonObject -> {
        if (isLegacyFieldValueObject(element)) {
          decodeLegacyFieldValue(element, fieldDef)
        } else if (isTypedScalarObject(element)) {
          decodeTypedValueElement(element, expectedType)?.let { FieldValue(scalar_value = it) }
        } else {
          val childSchema = fieldDef?.fields?.associateBy { it.name } ?: emptyMap()
          FieldValue(node_value = decodeRecordNode(element, childSchema))
        }
      }
      is JsonArray -> {
        if (element.isEmpty()) {
          if (fieldDef?.is_repeated == true && fieldDef.type == DataType.TYPE_MESSAGE) {
            FieldValue(repeat_value = RecordNodeList(nodes = emptyList()))
          } else {
            FieldValue(list_value = TypedValueList(values = emptyList()))
          }
        } else if (
          expectedType == DataType.TYPE_GEOPOINT ||
            (expectedType != DataType.TYPE_SELECT_MULTIPLE && isGeoPointCoordinateArray(element))
        ) {
          FieldValue(scalar_value = TypedValue(geopoint_value = decodeGeoPointArray(element)))
        } else if (is2DCoordinateArray(element)) {
          val pts = element.mapNotNull { (it as? JsonArray)?.let(::decodeGeoPointArray) }
          when {
            expectedType == DataType.TYPE_GEOTRACE ->
              FieldValue(scalar_value = TypedValue(geotrace_value = GeoTrace(points = pts)))
            expectedType == DataType.TYPE_GEOSHAPE ->
              FieldValue(scalar_value = TypedValue(geoshape_value = GeoShape(points = pts)))
            pts.size >= 4 && pts.first() == pts.last() ->
              FieldValue(scalar_value = TypedValue(geoshape_value = GeoShape(points = pts)))
            else -> FieldValue(scalar_value = TypedValue(geotrace_value = GeoTrace(points = pts)))
          }
        } else if (element.all { it is JsonObject && !isTypedScalarObject(it) }) {
          val childSchema = fieldDef?.fields?.associateBy { it.name } ?: emptyMap()
          FieldValue(
            repeat_value =
              RecordNodeList(
                nodes =
                  element.mapNotNull {
                    (it as? JsonObject)?.let { o -> decodeRecordNode(o, childSchema) }
                  }
              )
          )
        } else {
          FieldValue(
            list_value = TypedValueList(values = element.mapNotNull { decodeTypedValueElement(it) })
          )
        }
      }
    }
  }

  private fun decodeLegacyFieldValue(
    obj: JsonObject,
    fieldDef: FieldDefinition? = null,
  ): FieldValue {
    val childSchema = fieldDef?.fields?.associateBy { it.name } ?: emptyMap()
    return FieldValue(
      scalar_value =
        obj.getProtoElement("scalar_value")?.let { decodeTypedValueElement(it, fieldDef?.type) },
      list_value = obj.getProtoObject("list_value")?.let { decodeTypedValueList(it) },
      node_value = obj.getProtoObject("node_value")?.let { decodeRecordNode(it, childSchema) },
      repeat_value =
        obj.getProtoObject("repeat_value")?.let {
          RecordNodeList(
            nodes = it.getProtoObjects("nodes").map { n -> decodeRecordNode(n, childSchema) }
          )
        },
    )
  }

  private fun decodeTypedValueList(obj: JsonObject): TypedValueList =
    TypedValueList(
      values =
        (obj.getProtoElement("values") as? JsonArray)?.mapNotNull { decodeTypedValueElement(it) }
          ?: emptyList()
    )

  private val TYPED_SCALAR_OBJECT_KEYS =
    setOf(
      "string",
      "stringValue",
      "string_value",
      "int32",
      "int32Value",
      "int32_value",
      "int64",
      "int64Value",
      "int64_value",
      "double",
      "doubleValue",
      "double_value",
      "bool",
      "boolValue",
      "bool_value",
      "date",
      "dateValue",
      "date_value",
      "time",
      "timeValue",
      "time_value",
      "timestamp",
      "timestampValue",
      "timestamp_value",
      "geopoint",
      "geopointValue",
      "geopoint_value",
      "binary",
      "base64",
      "binaryValue",
      "binary_value",
      "geotrace",
      "geotraceValue",
      "geotrace_value",
      "geoshape",
      "geoshapeValue",
      "geoshape_value",
    )

  private fun isTypedScalarObject(obj: JsonObject): Boolean {
    if (obj.size == 1 && obj.keys.first() in TYPED_SCALAR_OBJECT_KEYS) return true
    if (obj.containsKey("latitude") && obj.containsKey("longitude") && obj.size in 2..4) return true
    return false
  }

  private fun isGeoPointCoordinateArray(arr: JsonArray): Boolean =
    arr.size in 2..4 &&
      arr.all { el -> el is JsonPrimitive && !el.isString && el.doubleOrNull != null }

  private fun is2DCoordinateArray(arr: JsonArray): Boolean =
    arr.isNotEmpty() && arr.all { el -> el is JsonArray && isGeoPointCoordinateArray(el) }

  private fun decodeTypedValueElement(
    element: JsonElement,
    expectedType: DataType? = null,
  ): TypedValue? =
    when (element) {
      is JsonPrimitive -> decodePrimitiveTypedValue(element, expectedType)
      is JsonArray -> {
        if (isGeoPointCoordinateArray(element)) {
          TypedValue(geopoint_value = decodeGeoPointArray(element))
        } else if (is2DCoordinateArray(element)) {
          val pts = element.mapNotNull { (it as? JsonArray)?.let(::decodeGeoPointArray) }
          if (
            expectedType == DataType.TYPE_GEOSHAPE ||
              (expectedType != DataType.TYPE_GEOTRACE && pts.size >= 4 && pts.first() == pts.last())
          ) {
            TypedValue(geoshape_value = GeoShape(points = pts))
          } else {
            TypedValue(geotrace_value = GeoTrace(points = pts))
          }
        } else {
          null
        }
      }
      is JsonObject -> decodeObjectTypedValue(element)
    }

  private fun decodePrimitiveTypedValue(
    prim: JsonPrimitive,
    expectedType: DataType? = null,
  ): TypedValue? {
    if (prim is JsonNull) return null
    if (prim.isString) {
      val s = prim.content
      return when (expectedType) {
        DataType.TYPE_STRING,
        DataType.TYPE_SELECT_ONE -> TypedValue(string_value = s)
        DataType.TYPE_DATE -> TypedValue(date_value = TemporalUtils.tryParseDate(s))
        DataType.TYPE_TIME -> TypedValue(time_value = TemporalUtils.tryParseTime(s))
        DataType.TYPE_DATETIME -> TypedValue(timestamp_value = parseRfc3339Timestamp(s))
        DataType.TYPE_INT32 -> TypedValue(int32_value = s.toIntOrNull())
        DataType.TYPE_INT64 -> TypedValue(int64_value = s.toLongOrNull())
        DataType.TYPE_DOUBLE -> TypedValue(double_value = s.toDoubleOrNull())
        DataType.TYPE_BOOLEAN -> TypedValue(bool_value = s.toBooleanStrictOrNull())
        DataType.TYPE_BINARY -> TypedValue(binary_value = s.decodeBase64() ?: s.encodeUtf8())
        else -> {
          when {
            isIsoDateLiteral(s) -> TypedValue(date_value = TemporalUtils.tryParseDate(s))
            isRfc3339TimestampLiteral(s) -> TypedValue(timestamp_value = parseRfc3339Timestamp(s))
            isIsoTimeLiteral(s) -> TypedValue(time_value = TemporalUtils.tryParseTime(s))
            else -> TypedValue(string_value = s)
          }
        }
      }
    }
    prim.booleanOrNull?.let {
      return TypedValue(bool_value = it)
    }
    return when (expectedType) {
      DataType.TYPE_DOUBLE -> TypedValue(double_value = prim.doubleOrNull)
      DataType.TYPE_INT64 -> TypedValue(int64_value = prim.longOrNull)
      DataType.TYPE_INT32 -> TypedValue(int32_value = prim.intOrNull)
      else -> {
        val raw = prim.content
        when {
          raw.contains('.') || raw.contains('e') || raw.contains('E') ->
            TypedValue(double_value = prim.doubleOrNull)
          prim.intOrNull != null -> TypedValue(int32_value = prim.intOrNull)
          prim.longOrNull != null -> TypedValue(int64_value = prim.longOrNull)
          else -> TypedValue(double_value = prim.doubleOrNull)
        }
      }
    }
  }

  private fun decodeObjectTypedValue(obj: JsonObject): TypedValue {
    if (obj.containsKey("latitude") && obj.containsKey("longitude")) {
      return TypedValue(geopoint_value = decodeGeoPoint(obj))
    }
    return TypedValue(
      string_value = obj.getProtoStringOrNull("string_value") ?: obj.getProtoStringOrNull("string"),
      int32_value = obj.getProtoIntOrNull("int32_value") ?: obj.getProtoIntOrNull("int32"),
      int64_value = obj.getProtoLongOrNull("int64_value") ?: obj.getProtoLongOrNull("int64"),
      double_value = obj.getProtoDoubleOrNull("double_value") ?: obj.getProtoDoubleOrNull("double"),
      bool_value = obj.getProtoBooleanOrNull("bool_value") ?: obj.getProtoBooleanOrNull("bool"),
      date_value = obj.getProtoDate("date_value") ?: obj.getProtoDate("date"),
      time_value = obj.getProtoTimeOfDay("time_value") ?: obj.getProtoTimeOfDay("time"),
      timestamp_value =
        obj.getProtoTimestamp("timestamp_value") ?: obj.getProtoTimestamp("timestamp"),
      geopoint_value =
        (obj.getProtoElement("geopoint_value") ?: obj.getProtoElement("geopoint"))?.let {
          decodeGeoPointElement(it)
        },
      binary_value =
        (obj.getProtoStringOrNull("binary_value")
            ?: obj.getProtoStringOrNull("base64")
            ?: obj.getProtoStringOrNull("binary"))
          ?.let { str -> str.decodeBase64() ?: str.encodeUtf8() },
      geotrace_value =
        (obj.getProtoElement("geotrace_value") ?: obj.getProtoElement("geotrace"))?.let {
          decodeGeoTraceElement(it)
        },
      geoshape_value =
        (obj.getProtoElement("geoshape_value") ?: obj.getProtoElement("geoshape"))?.let {
          decodeGeoShapeElement(it)
        },
    )
  }

  private fun decodeDate(obj: JsonObject): Date =
    Date(
      year = obj.getProtoInt("year"),
      month = obj.getProtoInt("month"),
      day = obj.getProtoInt("day"),
    )

  private fun decodeTimeOfDay(obj: JsonObject): TimeOfDay =
    TimeOfDay(
      hours = obj.getProtoInt("hours"),
      minutes = obj.getProtoInt("minutes"),
      seconds = obj.getProtoInt("seconds"),
      nanos = obj.getProtoInt("nanos"),
    )

  private fun decodeGeoPointElement(el: JsonElement): GeoPoint? =
    when (el) {
      is JsonArray -> decodeGeoPointArray(el)
      is JsonObject -> decodeGeoPoint(el)
      else -> null
    }

  private fun decodeGeoPointArray(arr: JsonArray): GeoPoint {
    fun JsonElement?.asDoubleOrNull(): Double? =
      (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
    fun JsonElement?.asDouble(): Double = asDoubleOrNull() ?: 0.0
    return GeoPoint(
      latitude = arr.getOrNull(0).asDouble(),
      longitude = arr.getOrNull(1).asDouble(),
      altitude_meters = arr.getOrNull(2).asDoubleOrNull(),
      accuracy_meters = arr.getOrNull(3).asDoubleOrNull(),
    )
  }

  private fun decodeGeoPoint(obj: JsonObject): GeoPoint =
    GeoPoint(
      latitude = obj.getProtoDouble("latitude"),
      longitude = obj.getProtoDouble("longitude"),
      altitude_meters = obj.getProtoDoubleOrNull("altitude_meters"),
      accuracy_meters = obj.getProtoDoubleOrNull("accuracy_meters"),
    )

  private fun decodeGeoTraceElement(el: JsonElement): GeoTrace? =
    when (el) {
      is JsonArray -> GeoTrace(points = el.mapNotNull { decodeGeoPointElement(it) })
      is JsonObject -> decodeGeoTrace(el)
      else -> null
    }

  private fun decodeGeoTrace(obj: JsonObject): GeoTrace =
    GeoTrace(
      points =
        (obj.getProtoElement("points") as? JsonArray)?.mapNotNull { decodeGeoPointElement(it) }
          ?: emptyList()
    )

  private fun decodeGeoShapeElement(el: JsonElement): GeoShape? =
    when (el) {
      is JsonArray -> GeoShape(points = el.mapNotNull { decodeGeoPointElement(it) })
      is JsonObject -> decodeGeoShape(el)
      else -> null
    }

  private fun decodeGeoShape(obj: JsonObject): GeoShape =
    GeoShape(
      points =
        (obj.getProtoElement("points") as? JsonArray)?.mapNotNull { decodeGeoPointElement(it) }
          ?: emptyList()
    )

  // ===========================================================================
  // RFC 3339 Timestamp & Temporal Helpers
  // ===========================================================================

  private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
  private val ISO_TIME_REGEX = Regex("""^\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$""")
  private val RFC3339_TIMESTAMP_REGEX =
    Regex("""^\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:[Zz]|[+-]\d{2}:\d{2})$""")

  private fun isIsoDateLiteral(str: String): Boolean =
    ISO_DATE_REGEX.matches(str) && TemporalUtils.tryParseDate(str) != null

  private fun isIsoTimeLiteral(str: String): Boolean =
    ISO_TIME_REGEX.matches(str) && TemporalUtils.tryParseTime(str) != null

  private fun isRfc3339TimestampLiteral(str: String): Boolean =
    RFC3339_TIMESTAMP_REGEX.matches(str) && TemporalUtils.tryParseDate(str) != null

  private fun looksLikeTemporalLiteral(str: String): Boolean =
    isIsoDateLiteral(str) || isIsoTimeLiteral(str) || isRfc3339TimestampLiteral(str)

  private fun formatIsoTimeOfDay(time: TimeOfDay): String {
    val base =
      "${time.hours.toString().padStart(2, '0')}:${time.minutes.toString().padStart(2, '0')}:${time.seconds.toString().padStart(2, '0')}"
    val nano = time.nanos
    val frac =
      when {
        nano == 0 -> ""
        nano % 1_000_000 == 0 -> "." + (nano / 1_000_000).toString().padStart(3, '0')
        nano % 1_000 == 0 -> "." + (nano / 1_000).toString().padStart(6, '0')
        else -> "." + nano.toString().padStart(9, '0')
      }
    return "$base$frac"
  }

  private fun formatRfc3339Timestamp(instant: Instant): String {
    val epochSec = instant.getEpochSecond()
    val nano = instant.getNano()
    val days = if (epochSec >= 0L) epochSec / 86400L else ((epochSec + 1L) / 86400L) - 1L
    val remSec = ((epochSec % 86400L) + 86400L) % 86400L
    val date = TemporalUtils.epochDaysToDate(days)
    val hours = (remSec / 3600L).toInt()
    val minutes = ((remSec % 3600L) / 60L).toInt()
    val seconds = (remSec % 60L).toInt()
    val dateStr = TemporalUtils.formatDate(date)
    val timeStr =
      "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    val fracStr =
      when {
        nano == 0 -> ""
        nano % 1_000_000 == 0 -> "." + (nano / 1_000_000).toString().padStart(3, '0')
        nano % 1_000 == 0 -> "." + (nano / 1_000).toString().padStart(6, '0')
        else -> "." + nano.toString().padStart(9, '0')
      }
    return "${dateStr}T${timeStr}${fracStr}Z"
  }

  private fun parseRfc3339Timestamp(str: String): Instant {
    val trimmed = str.trim()
    val date =
      TemporalUtils.tryParseDate(trimmed)
        ?: throw IllegalArgumentException("Invalid RFC 3339 timestamp: $str")
    val baseDays = TemporalUtils.dateToEpochDays(date.year, date.month, date.day)
    if (!trimmed.contains('T') && !trimmed.contains('t')) {
      return ofEpochSecond(baseDays * 86400L, 0L)
    }
    val afterT = trimmed.substringAfter('T', trimmed.substringAfter('t'))
    var offsetSeconds = 0L
    val timePart: String
    if (afterT.endsWith('Z') || afterT.endsWith('z')) {
      timePart = afterT.dropLast(1)
    } else if (afterT.contains('+')) {
      timePart = afterT.substringBefore('+')
      offsetSeconds = parseTzOffset(afterT.substringAfter('+'))
    } else if (afterT.lastIndexOf('-') > 0) {
      val idx = afterT.lastIndexOf('-')
      timePart = afterT.substring(0, idx)
      offsetSeconds = -parseTzOffset(afterT.substring(idx + 1))
    } else {
      timePart = afterT
    }
    val time =
      TemporalUtils.tryParseTime(timePart)
        ?: throw IllegalArgumentException("Invalid time in RFC 3339 timestamp: $str")
    val epochSec =
      baseDays * 86400L + time.hours * 3600L + time.minutes * 60L + time.seconds.toLong() -
        offsetSeconds
    return ofEpochSecond(epochSec, time.nanos.toLong())
  }

  private fun parseTzOffset(tz: String): Long {
    val parts = tz.split(':')
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return h * 3600L + m * 60L
  }

  // ===========================================================================
  // JSON lookup helpers
  // ===========================================================================

  private fun JsonObject.getProtoStringOrNull(snakeName: String): String? =
    (getProtoElement(snakeName) as? JsonPrimitive)?.content

  private fun JsonObject.getProtoString(snakeName: String): String =
    getProtoStringOrNull(snakeName) ?: ""

  private fun JsonObject.getProtoIntOrNull(snakeName: String): Int? {
    val prim = getProtoElement(snakeName) as? JsonPrimitive ?: return null
    return prim.intOrNull ?: prim.content.toIntOrNull()
  }

  private fun JsonObject.getProtoInt(snakeName: String): Int = getProtoIntOrNull(snakeName) ?: 0

  private fun JsonObject.getProtoLongOrNull(snakeName: String): Long? {
    val prim = getProtoElement(snakeName) as? JsonPrimitive ?: return null
    return prim.longOrNull ?: prim.content.toLongOrNull()
  }

  private fun JsonObject.getProtoDoubleOrNull(snakeName: String): Double? {
    val prim = getProtoElement(snakeName) as? JsonPrimitive ?: return null
    return prim.doubleOrNull ?: prim.content.toDoubleOrNull()
  }

  private fun JsonObject.getProtoDouble(snakeName: String): Double =
    getProtoDoubleOrNull(snakeName) ?: 0.0

  private fun JsonObject.getProtoBooleanOrNull(snakeName: String): Boolean? {
    val prim = getProtoElement(snakeName) as? JsonPrimitive ?: return null
    return prim.booleanOrNull ?: prim.content.toBooleanStrictOrNull()
  }

  private fun JsonObject.getProtoObject(snakeName: String): JsonObject? =
    getProtoElement(snakeName) as? JsonObject

  private fun JsonObject.getProtoObjects(snakeName: String): List<JsonObject> =
    (getProtoElement(snakeName) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

  private fun JsonObject.getProtoTimestamp(snakeName: String): Instant? =
    getProtoElement(snakeName)?.let(::decodeTimestampElement)

  private fun decodeTimestampElement(element: JsonElement): Instant? {
    return when (val el = element) {
      is JsonPrimitive -> parseRfc3339Timestamp(el.content)
      is JsonObject ->
        ofEpochSecond(el.getProtoLongOrNull("seconds") ?: 0L, (el.getProtoInt("nanos")).toLong())
      else -> null
    }
  }

  private fun JsonObject.getProtoDate(snakeName: String): Date? =
    getProtoElement(snakeName)?.let(::decodeDateElement)

  private fun decodeDateElement(element: JsonElement): Date? {
    return when (val el = element) {
      is JsonObject -> decodeDate(el)
      is JsonPrimitive -> TemporalUtils.tryParseDate(el.content)
      else -> null
    }
  }

  private fun JsonObject.getProtoTimeOfDay(snakeName: String): TimeOfDay? {
    return when (val el = getProtoElement(snakeName)) {
      is JsonObject -> decodeTimeOfDay(el)
      is JsonPrimitive -> TemporalUtils.tryParseTime(el.content)
      else -> null
    }
  }
}
