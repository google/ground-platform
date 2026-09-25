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

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.AuditConfig
import groundplatform.v2.forms.AuditEvent
import groundplatform.v2.forms.AuditLog
import groundplatform.v2.forms.ChoiceItem
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.EntityPropertyMapping
import groundplatform.v2.forms.EntitySyncMetadata
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoConfig
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.GroupDef
import groundplatform.v2.forms.IntentConfig
import groundplatform.v2.forms.ItemsetDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.LanguageTranslation
import groundplatform.v2.forms.LocalizedString
import groundplatform.v2.forms.MediaRef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.OutputFragment
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RangeConfig
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordNodeList
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.SubmissionConfig
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
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
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.xpath.model.TemporalUtils

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
  ): String {
    val obj = encodeFormDef(formDef, preserveProtoFieldNames)
    return if (prettyPrint) prettyJson.encodeToString(JsonObject.serializer(), obj)
    else compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** Parses a JSON string into a [FormDef] protobuf message. */
  fun deserializeFormDef(json: String): FormDef {
    val element =
      try {
        compactJson.parseToJsonElement(json)
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
      }
    val obj =
      element as? JsonObject
        ?: throw IllegalArgumentException("Expected JSON object at root for FormDef")
    return decodeFormDef(obj)
  }

  /** Formats a [RecordInstance] protobuf message into a JSON string. */
  fun serializeRecordInstance(
    record: RecordInstance,
    prettyPrint: Boolean = true,
    preserveProtoFieldNames: Boolean = false,
  ): String {
    val obj = encodeRecordInstance(record, preserveProtoFieldNames)
    return if (prettyPrint) prettyJson.encodeToString(JsonObject.serializer(), obj)
    else compactJson.encodeToString(JsonObject.serializer(), obj)
  }

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
    val element =
      try {
        compactJson.parseToJsonElement(json)
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
      }
    val obj =
      element as? JsonObject
        ?: throw IllegalArgumentException("Expected JSON object at root for RecordInstance")
    return decodeRecordInstance(obj, formDef, schema)
  }

  // ===========================================================================
  // FormDef Encoding
  // ===========================================================================

  private fun encodeFormDef(msg: FormDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("form_id", msg.form_id)
      addString("title", msg.title)
      addString("version", msg.version)
      addString("default_language", msg.default_language)
      msg.model?.let { addObject("model", encodeModelDef(it, snake)) }
      msg.view?.let { addObject("view", encodeViewDef(it, snake)) }
    }

  private fun encodeModelDef(msg: ModelDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      msg.primary_instance?.let { addObject("primary_instance", encodePrimaryInstance(it, snake)) }
      addObjectList("secondary_instances", msg.secondary_instances) {
        encodeSecondaryInstance(it, snake)
      }
      addObjectList("bindings", msg.bindings) { encodeFieldBinding(it, snake) }
      msg.translations?.let { addObject("translations", encodeTranslationCatalog(it, snake)) }
      addObjectList("actions", msg.actions) { encodeActionDef(it, snake) }
      addObjectList("entities", msg.entities) { encodeEntityDeclaration(it, snake) }
      msg.metadata?.let { addObject("metadata", encodeRecordMetadata(it, snake)) }
      msg.submission?.let { addObject("submission", encodeSubmissionConfig(it, snake)) }
    }

  private fun encodePrimaryInstance(msg: PrimaryInstance, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      msg.record_schema?.let { addObject("record_schema", encodeRecordSchema(it, snake)) }
      msg.default_values?.let {
        if (it.fields.isNotEmpty()) {
          addObject("default_values", encodeRecordNode(it, snake))
        }
      }
      addString("message_type_name", msg.message_type_name)
    }

  private fun encodeRecordSchema(msg: RecordSchema, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("name", msg.name)
      addString("title", msg.title)
      addObjectList("fields", msg.fields) { encodeFieldDefinition(it, snake) }
      addString("sms_prefix", msg.sms_prefix)
      addString("sms_delimiter", msg.sms_delimiter)
    }

  private fun encodeFieldDefinition(msg: FieldDefinition, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("name", msg.name)
      if (msg.type != DataType.DATA_TYPE_UNSPECIFIED) {
        addEnum("type", msg.type.name)
      }
      addBoolean("is_repeated", msg.is_repeated)
      addObjectList("fields", msg.fields) { encodeFieldDefinition(it, snake) }
      addString("type_name", msg.type_name)
    }

  private fun encodeSecondaryInstance(msg: SecondaryInstance, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("id", msg.id)
      addString("uri", msg.uri)
      addString("inline_data", msg.inline_data)
    }

  private fun encodeFieldBinding(msg: FieldBinding, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("field_path", msg.field_path)
      if (msg.type != DataType.DATA_TYPE_UNSPECIFIED) {
        addEnum("type", msg.type.name)
      }
      addBoolean("read_only", msg.read_only)
      addString("relevant_expression", msg.relevant_expression)
      addString("calculate_expression", msg.calculate_expression)
      addString("constraint_expression", msg.constraint_expression)
      addString("required_expression", msg.required_expression)
      addString("constraint_message", msg.constraint_message)
      addString("required_message", msg.required_message)
      addString("sms_tag", msg.sms_tag)
      addString("entity_saveto", msg.entity_saveto)
      addInt("max_pixels", msg.max_pixels)
      if (msg.preload != PreloadType.PRELOAD_UNSPECIFIED) {
        addEnum("preload", msg.preload.name)
      }
      addString("preload_param", msg.preload_param)
    }

  private fun encodeTranslationCatalog(msg: TranslationCatalog, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("languages", msg.languages) { encodeLanguageTranslation(it, snake) }
    }

  private fun encodeLanguageTranslation(msg: LanguageTranslation, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("language", msg.language)
      addBoolean("is_default", msg.is_default)
      if (msg.strings.isNotEmpty()) {
        val mapEntries = linkedMapOf<String, JsonElement>()
        msg.strings.forEach { (k, v) -> mapEntries[k] = encodeLocalizedString(v, snake) }
        addObject("strings", JsonObject(mapEntries))
      }
    }

  private fun encodeLocalizedString(msg: LocalizedString, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("value", msg.value_)
      addString("short_value", msg.short_value)
      addString("guidance_value", msg.guidance_value)
      msg.media?.let { addObject("media", encodeMediaRef(it, snake)) }
    }

  private fun encodeMediaRef(msg: MediaRef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("image_uri", msg.image_uri)
      addString("big_image_uri", msg.big_image_uri)
      addString("audio_uri", msg.audio_uri)
      addString("video_uri", msg.video_uri)
    }

  private fun encodeActionDef(msg: ActionDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      val activeEvents =
        msg.events.filter { it != EventType.EVENT_TYPE_UNSPECIFIED }.map { JsonPrimitive(it.name) }
      if (activeEvents.isNotEmpty()) {
        addElement("events", JsonArray(activeEvents))
      }
      if (msg.type != ActionType.ACTION_TYPE_UNSPECIFIED) {
        addEnum("type", msg.type.name)
      }
      addString("target_field", msg.target_field)
      addString("value_expression", msg.value_expression)
      msg.literal_value?.let {
        encodeTypedValueElement(it, snake)?.let { el -> addElement("literal_value", el) }
      }
    }

  private fun encodeEntityDeclaration(msg: EntityDeclaration, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("dataset", msg.dataset)
      addString("entity_id_expression", msg.entity_id_expression)
      addString("label_expression", msg.label_expression)
      addString("create_condition", msg.create_condition)
      addString("update_condition", msg.update_condition)
      msg.sync_metadata?.let { addObject("sync_metadata", encodeEntitySyncMetadata(it, snake)) }
      addObjectList("property_mappings", msg.property_mappings) {
        encodeEntityPropertyMapping(it, snake)
      }
    }

  private fun encodeEntitySyncMetadata(msg: EntitySyncMetadata, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("base_version_expression", msg.base_version_expression)
      addString("trunk_version_expression", msg.trunk_version_expression)
      addString("branch_id_expression", msg.branch_id_expression)
    }

  private fun encodeEntityPropertyMapping(msg: EntityPropertyMapping, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("entity_property", msg.entity_property)
      addString("source_field_path", msg.source_field_path)
    }

  private fun encodeSubmissionConfig(msg: SubmissionConfig, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("action_url", msg.action_url)
      addString("method", msg.method)
      addString("base64_rsa_public_key", msg.base64_rsa_public_key)
      addBoolean("auto_send", msg.auto_send)
      addBoolean("auto_delete", msg.auto_delete)
      addBoolean("client_editable", msg.client_editable)
    }

  private fun encodeViewDef(msg: ViewDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("components", msg.components) { encodeViewComponent(it, snake) }
    }

  private fun encodeViewComponent(msg: ViewComponent, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      msg.control?.let { addObject("control", encodeControlDef(it, snake)) }
      msg.group?.let { addObject("group", encodeGroupDef(it, snake)) }
      msg.repeat?.let { addObject("repeat", encodeRepeatDef(it, snake)) }
    }

  private fun encodeControlDef(msg: ControlDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("field_ref", msg.field_ref)
      if (msg.type != ControlType.CONTROL_TYPE_UNSPECIFIED) {
        addEnum("type", msg.type.name)
      }
      msg.label?.let { addObject("label", encodeLabelDef(it, snake)) }
      msg.hint?.let { addObject("hint", encodeLabelDef(it, snake)) }
      addString("appearance", msg.appearance)
      addObjectList("choices", msg.choices) { encodeChoiceItem(it, snake) }
      msg.itemset?.let { addObject("itemset", encodeItemsetDef(it, snake)) }
      msg.range_config?.let { addObject("range_config", encodeRangeConfig(it, snake)) }
      addString("media_type", msg.media_type)
      msg.intent?.let { addObject("intent", encodeIntentConfig(it, snake)) }
      addObjectList("actions", msg.actions) { encodeActionDef(it, snake) }
      msg.geo_config?.let { addObject("geo_config", encodeGeoConfig(it, snake)) }
    }

  private fun encodeLabelDef(msg: LabelDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("text", msg.text)
      addString("text_id", msg.text_id)
      addObjectList("outputs", msg.outputs) { encodeOutputFragment(it, snake) }
    }

  private fun encodeOutputFragment(msg: OutputFragment, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("placeholder_id", msg.placeholder_id)
      addString("value_expression", msg.value_expression)
    }

  private fun encodeChoiceItem(msg: ChoiceItem, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("value", msg.value_)
      msg.label?.let { addObject("label", encodeLabelDef(it, snake)) }
      addStringMap("properties", msg.properties)
    }

  private fun encodeItemsetDef(msg: ItemsetDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("instance_id", msg.instance_id)
      addString("nodeset_filter", msg.nodeset_filter)
      addString("value_ref", msg.value_ref)
      addString("label_ref", msg.label_ref)
      addBoolean("randomize", msg.randomize)
      addString("random_seed_expression", msg.random_seed_expression)
    }

  private fun encodeRangeConfig(msg: RangeConfig, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addDouble("start", msg.start)
      addDouble("end", msg.end)
      addDouble("step", msg.step)
      addDouble("tick_interval", msg.tick_interval)
    }

  private fun encodeGeoConfig(msg: GeoConfig, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addDouble("accuracy_threshold_meters", msg.accuracy_threshold_meters)
      addDouble("warning_threshold_meters", msg.warning_threshold_meters)
      addBoolean("allow_mock_accuracy", msg.allow_mock_accuracy)
    }

  private fun encodeIntentConfig(msg: IntentConfig, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("intent_uri", msg.intent_uri)
      addStringMap("parameters", msg.parameters)
      addStringMap("response_mappings", msg.response_mappings)
    }

  private fun encodeGroupDef(msg: GroupDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("field_ref", msg.field_ref)
      msg.label?.let { addObject("label", encodeLabelDef(it, snake)) }
      addString("appearance", msg.appearance)
      msg.intent?.let { addObject("intent", encodeIntentConfig(it, snake)) }
      addObjectList("components", msg.components) { encodeViewComponent(it, snake) }
    }

  private fun encodeRepeatDef(msg: RepeatDef, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("field_ref", msg.field_ref)
      msg.label?.let { addObject("label", encodeLabelDef(it, snake)) }
      addString("appearance", msg.appearance)
      addString("count_expression", msg.count_expression)
      addBoolean("no_add_remove", msg.no_add_remove)
      addObjectList("components", msg.components) { encodeViewComponent(it, snake) }
    }

  // ===========================================================================
  // RecordInstance Encoding
  // ===========================================================================

  private fun encodeRecordInstance(msg: RecordInstance, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("form_id", msg.form_id)
      addString("form_version", msg.form_version)
      msg.metadata?.let {
        val metaObj = encodeRecordMetadata(it, snake)
        if (metaObj.isNotEmpty()) {
          addObject("metadata", metaObj)
        }
      }
      msg.data_?.let {
        val dataObj = encodeRecordNode(it, snake)
        if (dataObj.isNotEmpty()) {
          addObject("data", dataObj)
        }
      }
      msg.audit_log?.let {
        if (it.events.isNotEmpty()) {
          addObject("audit_log", encodeAuditLog(it, snake))
        }
      }
    }

  private fun encodeRecordMetadata(msg: RecordMetadata, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("instance_id", msg.instance_id)
      msg.start_time?.let { addStringAlways("start_time", formatRfc3339Timestamp(it)) }
      msg.end_time?.let { addStringAlways("end_time", formatRfc3339Timestamp(it)) }
      msg.today?.let { addStringAlways("today", formatRfc3339Timestamp(it)) }
      addString("device_id", msg.device_id)
      addString("subscriber_id", msg.subscriber_id)
      addString("sim_serial", msg.sim_serial)
      addString("phone_number", msg.phone_number)
      addString("audit_file_uri", msg.audit_file_uri)
      msg.audit_config?.let { addObject("audit_config", encodeAuditConfig(it, snake)) }
    }

  private fun encodeAuditConfig(msg: AuditConfig, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addBoolean("enabled", msg.enabled)
      addInt("location_min_interval_seconds", msg.location_min_interval_seconds)
      addInt("location_max_age_seconds", msg.location_max_age_seconds)
      addString("location_priority", msg.location_priority)
      addBoolean("track_changes", msg.track_changes)
      addString("track_changes_reasons", msg.track_changes_reasons)
    }

  private fun encodeAuditLog(msg: AuditLog, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("events", msg.events) { encodeAuditEvent(it, snake) }
    }

  private fun encodeAuditEvent(msg: AuditEvent, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addString("event", msg.event)
      addString("field_path", msg.field_path)
      msg.start_time?.let { addStringAlways("start_time", formatRfc3339Timestamp(it)) }
      msg.end_time?.let { addStringAlways("end_time", formatRfc3339Timestamp(it)) }
      msg.location?.let { addElement("location", encodeGeoPointArray(it)) }
      addString("old_value", msg.old_value)
      addString("new_value", msg.new_value)
    }

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
    if (msg.altitude_meters != 0.0 || msg.accuracy_meters != 0.0) {
      elements.add(formatDoublePrimitive(msg.altitude_meters))
      if (msg.accuracy_meters != 0.0) {
        elements.add(formatDoublePrimitive(msg.accuracy_meters))
      }
    }
    return JsonArray(elements)
  }

  // ===========================================================================
  // FormDef Decoding
  // ===========================================================================

  private fun decodeFormDef(obj: JsonObject): FormDef =
    FormDef(
      form_id = obj.getProtoString("form_id"),
      title = obj.getProtoString("title"),
      version = obj.getProtoString("version"),
      default_language = obj.getProtoString("default_language"),
      model = obj.getProtoObject("model")?.let { decodeModelDef(it) },
      view = obj.getProtoObject("view")?.let { decodeViewDef(it) },
    )

  private fun decodeModelDef(obj: JsonObject): ModelDef =
    ModelDef(
      primary_instance = obj.getProtoObject("primary_instance")?.let { decodePrimaryInstance(it) },
      secondary_instances =
        obj.getProtoObjects("secondary_instances").map { decodeSecondaryInstance(it) },
      bindings = obj.getProtoObjects("bindings").map { decodeFieldBinding(it) },
      translations = obj.getProtoObject("translations")?.let { decodeTranslationCatalog(it) },
      actions = obj.getProtoObjects("actions").map { decodeActionDef(it) },
      entities = obj.getProtoObjects("entities").map { decodeEntityDeclaration(it) },
      metadata = obj.getProtoObject("metadata")?.let { decodeRecordMetadata(it) },
      submission = obj.getProtoObject("submission")?.let { decodeSubmissionConfig(it) },
    )

  private fun decodePrimaryInstance(obj: JsonObject): PrimaryInstance {
    val schema = obj.getProtoObject("record_schema")?.let { decodeRecordSchema(it) }
    return PrimaryInstance(
      record_schema = schema,
      default_values =
        obj.getProtoObject("default_values")?.let {
          decodeRecordNode(
            it,
            schemaFields = schema?.fields?.associateBy { f -> f.name } ?: emptyMap(),
          )
        },
      message_type_name = obj.getProtoString("message_type_name"),
    )
  }

  private fun decodeRecordSchema(obj: JsonObject): RecordSchema =
    RecordSchema(
      name = obj.getProtoString("name"),
      title = obj.getProtoString("title"),
      fields = obj.getProtoObjects("fields").map { decodeFieldDefinition(it) },
      sms_prefix = obj.getProtoString("sms_prefix"),
      sms_delimiter = obj.getProtoString("sms_delimiter"),
    )

  private fun decodeFieldDefinition(obj: JsonObject): FieldDefinition =
    FieldDefinition(
      name = obj.getProtoString("name"),
      type = parseEnum(obj.getProtoStringOrNull("type"), DataType.DATA_TYPE_UNSPECIFIED),
      is_repeated = obj.getProtoBoolean("is_repeated"),
      fields = obj.getProtoObjects("fields").map { decodeFieldDefinition(it) },
      type_name = obj.getProtoString("type_name"),
    )

  private fun decodeSecondaryInstance(obj: JsonObject): SecondaryInstance =
    SecondaryInstance(
      id = obj.getProtoString("id"),
      uri = obj.getProtoString("uri"),
      inline_data = obj.getProtoString("inline_data"),
    )

  private fun decodeFieldBinding(obj: JsonObject): FieldBinding =
    FieldBinding(
      field_path = obj.getProtoString("field_path"),
      type = parseEnum(obj.getProtoStringOrNull("type"), DataType.DATA_TYPE_UNSPECIFIED),
      read_only = obj.getProtoBoolean("read_only"),
      relevant_expression = obj.getProtoString("relevant_expression"),
      calculate_expression = obj.getProtoString("calculate_expression"),
      constraint_expression = obj.getProtoString("constraint_expression"),
      required_expression = obj.getProtoString("required_expression"),
      constraint_message = obj.getProtoString("constraint_message"),
      required_message = obj.getProtoString("required_message"),
      sms_tag = obj.getProtoString("sms_tag"),
      entity_saveto = obj.getProtoString("entity_saveto"),
      max_pixels = obj.getProtoInt("max_pixels"),
      preload = parseEnum(obj.getProtoStringOrNull("preload"), PreloadType.PRELOAD_UNSPECIFIED),
      preload_param = obj.getProtoString("preload_param"),
    )

  private fun decodeTranslationCatalog(obj: JsonObject): TranslationCatalog =
    TranslationCatalog(
      languages = obj.getProtoObjects("languages").map { decodeLanguageTranslation(it) }
    )

  private fun decodeLanguageTranslation(obj: JsonObject): LanguageTranslation {
    val stringsMap = linkedMapOf<String, LocalizedString>()
    val rawStrings = obj.getProtoElement("strings")
    when (rawStrings) {
      is JsonObject -> {
        rawStrings.forEach { (k, v) ->
          (v as? JsonObject)?.let { stringsMap[k] = decodeLocalizedString(it) }
        }
      }
      is JsonArray -> {
        rawStrings.forEach { entryEl ->
          val entry = entryEl as? JsonObject ?: return@forEach
          val k = entry.getProtoString("key")
          val v = entry.getProtoObject("value")
          if (v != null) {
            stringsMap[k] = decodeLocalizedString(v)
          }
        }
      }
      else -> {}
    }
    return LanguageTranslation(
      language = obj.getProtoString("language"),
      is_default = obj.getProtoBoolean("is_default"),
      strings = stringsMap,
    )
  }

  private fun decodeLocalizedString(obj: JsonObject): LocalizedString =
    LocalizedString(
      value_ = obj.getProtoString("value"),
      short_value = obj.getProtoString("short_value"),
      guidance_value = obj.getProtoString("guidance_value"),
      media = obj.getProtoObject("media")?.let { decodeMediaRef(it) },
    )

  private fun decodeMediaRef(obj: JsonObject): MediaRef =
    MediaRef(
      image_uri = obj.getProtoString("image_uri"),
      big_image_uri = obj.getProtoString("big_image_uri"),
      audio_uri = obj.getProtoString("audio_uri"),
      video_uri = obj.getProtoString("video_uri"),
    )

  private fun decodeActionDef(obj: JsonObject): ActionDef =
    ActionDef(
      events =
        obj.getProtoStrings("events").map { parseEnum(it, EventType.EVENT_TYPE_UNSPECIFIED) },
      type = parseEnum(obj.getProtoStringOrNull("type"), ActionType.ACTION_TYPE_UNSPECIFIED),
      target_field = obj.getProtoString("target_field"),
      value_expression = obj.getProtoString("value_expression"),
      literal_value = obj.getProtoElement("literal_value")?.let { decodeTypedValueElement(it) },
    )

  private fun decodeEntityDeclaration(obj: JsonObject): EntityDeclaration =
    EntityDeclaration(
      dataset = obj.getProtoString("dataset"),
      entity_id_expression = obj.getProtoString("entity_id_expression"),
      label_expression = obj.getProtoString("label_expression"),
      create_condition = obj.getProtoString("create_condition"),
      update_condition = obj.getProtoString("update_condition"),
      sync_metadata = obj.getProtoObject("sync_metadata")?.let { decodeEntitySyncMetadata(it) },
      property_mappings =
        obj.getProtoObjects("property_mappings").map { decodeEntityPropertyMapping(it) },
    )

  private fun decodeEntitySyncMetadata(obj: JsonObject): EntitySyncMetadata =
    EntitySyncMetadata(
      base_version_expression = obj.getProtoString("base_version_expression"),
      trunk_version_expression = obj.getProtoString("trunk_version_expression"),
      branch_id_expression = obj.getProtoString("branch_id_expression"),
    )

  private fun decodeEntityPropertyMapping(obj: JsonObject): EntityPropertyMapping =
    EntityPropertyMapping(
      entity_property = obj.getProtoString("entity_property"),
      source_field_path = obj.getProtoString("source_field_path"),
    )

  private fun decodeSubmissionConfig(obj: JsonObject): SubmissionConfig =
    SubmissionConfig(
      action_url = obj.getProtoString("action_url"),
      method = obj.getProtoString("method"),
      base64_rsa_public_key = obj.getProtoString("base64_rsa_public_key"),
      auto_send = obj.getProtoBoolean("auto_send"),
      auto_delete = obj.getProtoBoolean("auto_delete"),
      client_editable = obj.getProtoBoolean("client_editable"),
    )

  private fun decodeViewDef(obj: JsonObject): ViewDef =
    ViewDef(components = obj.getProtoObjects("components").map { decodeViewComponent(it) })

  private fun decodeViewComponent(obj: JsonObject): ViewComponent =
    ViewComponent(
      control = obj.getProtoObject("control")?.let { decodeControlDef(it) },
      group = obj.getProtoObject("group")?.let { decodeGroupDef(it) },
      repeat = obj.getProtoObject("repeat")?.let { decodeRepeatDef(it) },
    )

  private fun decodeControlDef(obj: JsonObject): ControlDef =
    ControlDef(
      field_ref = obj.getProtoString("field_ref"),
      type = parseEnum(obj.getProtoStringOrNull("type"), ControlType.CONTROL_TYPE_UNSPECIFIED),
      label = obj.getProtoObject("label")?.let { decodeLabelDef(it) },
      hint = obj.getProtoObject("hint")?.let { decodeLabelDef(it) },
      appearance = obj.getProtoString("appearance"),
      choices = obj.getProtoObjects("choices").map { decodeChoiceItem(it) },
      itemset = obj.getProtoObject("itemset")?.let { decodeItemsetDef(it) },
      range_config = obj.getProtoObject("range_config")?.let { decodeRangeConfig(it) },
      media_type = obj.getProtoString("media_type"),
      intent = obj.getProtoObject("intent")?.let { decodeIntentConfig(it) },
      actions = obj.getProtoObjects("actions").map { decodeActionDef(it) },
      geo_config = obj.getProtoObject("geo_config")?.let { decodeGeoConfig(it) },
    )

  private fun decodeLabelDef(obj: JsonObject): LabelDef =
    LabelDef(
      text = obj.getProtoString("text"),
      text_id = obj.getProtoString("text_id"),
      outputs = obj.getProtoObjects("outputs").map { decodeOutputFragment(it) },
    )

  private fun decodeOutputFragment(obj: JsonObject): OutputFragment =
    OutputFragment(
      placeholder_id = obj.getProtoString("placeholder_id"),
      value_expression = obj.getProtoString("value_expression"),
    )

  private fun decodeChoiceItem(obj: JsonObject): ChoiceItem =
    ChoiceItem(
      value_ = obj.getProtoString("value"),
      label = obj.getProtoObject("label")?.let { decodeLabelDef(it) },
      properties = obj.getProtoStringMap("properties"),
    )

  private fun decodeItemsetDef(obj: JsonObject): ItemsetDef =
    ItemsetDef(
      instance_id = obj.getProtoString("instance_id"),
      nodeset_filter = obj.getProtoString("nodeset_filter"),
      value_ref = obj.getProtoString("value_ref"),
      label_ref = obj.getProtoString("label_ref"),
      randomize = obj.getProtoBoolean("randomize"),
      random_seed_expression = obj.getProtoString("random_seed_expression"),
    )

  private fun decodeRangeConfig(obj: JsonObject): RangeConfig =
    RangeConfig(
      start = obj.getProtoDouble("start"),
      end = obj.getProtoDouble("end"),
      step = obj.getProtoDouble("step"),
      tick_interval = obj.getProtoDouble("tick_interval"),
    )

  private fun decodeGeoConfig(obj: JsonObject): GeoConfig =
    GeoConfig(
      accuracy_threshold_meters = obj.getProtoDouble("accuracy_threshold_meters"),
      warning_threshold_meters = obj.getProtoDouble("warning_threshold_meters"),
      allow_mock_accuracy = obj.getProtoBoolean("allow_mock_accuracy"),
    )

  private fun decodeIntentConfig(obj: JsonObject): IntentConfig =
    IntentConfig(
      intent_uri = obj.getProtoString("intent_uri"),
      parameters = obj.getProtoStringMap("parameters"),
      response_mappings = obj.getProtoStringMap("response_mappings"),
    )

  private fun decodeGroupDef(obj: JsonObject): GroupDef =
    GroupDef(
      field_ref = obj.getProtoString("field_ref"),
      label = obj.getProtoObject("label")?.let { decodeLabelDef(it) },
      appearance = obj.getProtoString("appearance"),
      intent = obj.getProtoObject("intent")?.let { decodeIntentConfig(it) },
      components = obj.getProtoObjects("components").map { decodeViewComponent(it) },
    )

  private fun decodeRepeatDef(obj: JsonObject): RepeatDef =
    RepeatDef(
      field_ref = obj.getProtoString("field_ref"),
      label = obj.getProtoObject("label")?.let { decodeLabelDef(it) },
      appearance = obj.getProtoString("appearance"),
      count_expression = obj.getProtoString("count_expression"),
      no_add_remove = obj.getProtoBoolean("no_add_remove"),
      components = obj.getProtoObjects("components").map { decodeViewComponent(it) },
    )

  // ===========================================================================
  // RecordInstance Decoding
  // ===========================================================================

  private fun decodeRecordInstance(
    obj: JsonObject,
    formDef: FormDef? = null,
    schema: RecordSchema? = formDef?.model?.primary_instance?.record_schema,
  ): RecordInstance {
    val schemaFields = buildSchemaFieldMap(formDef, schema)
    return RecordInstance(
      form_id = obj.getProtoString("form_id"),
      form_version = obj.getProtoString("form_version"),
      metadata = obj.getProtoObject("metadata")?.let { decodeRecordMetadata(it) },
      data_ = obj.getProtoObject("data")?.let { decodeRecordNode(it, schemaFields) },
      audit_log = obj.getProtoObject("audit_log")?.let { decodeAuditLog(it) },
    )
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

  private fun decodeRecordMetadata(obj: JsonObject): RecordMetadata =
    RecordMetadata(
      instance_id = obj.getProtoString("instance_id"),
      start_time = obj.getProtoTimestamp("start_time"),
      end_time = obj.getProtoTimestamp("end_time"),
      today = obj.getProtoTimestamp("today"),
      device_id = obj.getProtoString("device_id"),
      subscriber_id = obj.getProtoString("subscriber_id"),
      sim_serial = obj.getProtoString("sim_serial"),
      phone_number = obj.getProtoString("phone_number"),
      audit_file_uri = obj.getProtoString("audit_file_uri"),
      audit_config = obj.getProtoObject("audit_config")?.let { decodeAuditConfig(it) },
    )

  private fun decodeAuditConfig(obj: JsonObject): AuditConfig =
    AuditConfig(
      enabled = obj.getProtoBoolean("enabled"),
      location_min_interval_seconds = obj.getProtoInt("location_min_interval_seconds"),
      location_max_age_seconds = obj.getProtoInt("location_max_age_seconds"),
      location_priority = obj.getProtoString("location_priority"),
      track_changes = obj.getProtoBoolean("track_changes"),
      track_changes_reasons = obj.getProtoString("track_changes_reasons"),
    )

  private fun decodeAuditLog(obj: JsonObject): AuditLog =
    AuditLog(events = obj.getProtoObjects("events").map { decodeAuditEvent(it) })

  private fun decodeAuditEvent(obj: JsonObject): AuditEvent =
    AuditEvent(
      event = obj.getProtoString("event"),
      field_path = obj.getProtoString("field_path"),
      start_time = obj.getProtoTimestamp("start_time"),
      end_time = obj.getProtoTimestamp("end_time"),
      location = obj.getProtoElement("location")?.let { decodeGeoPointElement(it) },
      old_value = obj.getProtoString("old_value"),
      new_value = obj.getProtoString("new_value"),
    )

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
    fun JsonElement?.asDouble(): Double =
      (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() } ?: 0.0
    return GeoPoint(
      latitude = arr.getOrNull(0).asDouble(),
      longitude = arr.getOrNull(1).asDouble(),
      altitude_meters = arr.getOrNull(2).asDouble(),
      accuracy_meters = arr.getOrNull(3).asDouble(),
    )
  }

  private fun decodeGeoPoint(obj: JsonObject): GeoPoint =
    GeoPoint(
      latitude = obj.getProtoDouble("latitude"),
      longitude = obj.getProtoDouble("longitude"),
      altitude_meters = obj.getProtoDouble("altitude_meters"),
      accuracy_meters = obj.getProtoDouble("accuracy_meters"),
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

  private inline fun <reified E : Enum<E>> parseEnum(name: String?, default: E): E {
    if (name == null) return default
    return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
  }

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
  // JSON Object Builder & Lookup Helpers
  // ===========================================================================

  private fun snakeToCamel(snake: String): String {
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

  private class ProtoJsonBuilder(private val preserveProtoFieldNames: Boolean) {
    val entries = linkedMapOf<String, JsonElement>()

    private fun key(snakeName: String): String =
      if (preserveProtoFieldNames) snakeName else snakeToCamel(snakeName)

    fun addString(snakeName: String, value: String) {
      if (value.isNotEmpty()) {
        entries[key(snakeName)] = JsonPrimitive(value)
      }
    }

    fun addStringAlways(snakeName: String, value: String) {
      entries[key(snakeName)] = JsonPrimitive(value)
    }

    fun addInt(snakeName: String, value: Int) {
      if (value != 0) {
        entries[key(snakeName)] = JsonPrimitive(value)
      }
    }

    fun addIntAlways(snakeName: String, value: Int) {
      entries[key(snakeName)] = JsonPrimitive(value)
    }

    fun addDouble(snakeName: String, value: Double) {
      if (value != 0.0) {
        entries[key(snakeName)] = JsonPrimitive(value)
      }
    }

    fun addDoubleAlways(snakeName: String, value: Double) {
      entries[key(snakeName)] = JsonPrimitive(value)
    }

    fun addBoolean(snakeName: String, value: Boolean) {
      if (value) {
        entries[key(snakeName)] = JsonPrimitive(true)
      }
    }

    fun addBooleanAlways(snakeName: String, value: Boolean) {
      entries[key(snakeName)] = JsonPrimitive(value)
    }

    fun addEnum(snakeName: String, enumName: String) {
      entries[key(snakeName)] = JsonPrimitive(enumName)
    }

    fun addObject(snakeName: String, obj: JsonObject) {
      entries[key(snakeName)] = obj
    }

    fun addElement(snakeName: String, element: JsonElement) {
      entries[key(snakeName)] = element
    }

    fun <T> addObjectList(snakeName: String, items: List<T>, mapper: (T) -> JsonObject) {
      if (items.isNotEmpty()) {
        entries[key(snakeName)] = JsonArray(items.map(mapper))
      }
    }

    fun addStringMap(snakeName: String, map: Map<String, String>) {
      if (map.isNotEmpty()) {
        val objMap = linkedMapOf<String, JsonElement>()
        map.forEach { (k, v) -> objMap[k] = JsonPrimitive(v) }
        entries[key(snakeName)] = JsonObject(objMap)
      }
    }
  }

  private inline fun buildProtoJsonObject(
    preserveProtoFieldNames: Boolean,
    block: ProtoJsonBuilder.() -> Unit,
  ): JsonObject = JsonObject(ProtoJsonBuilder(preserveProtoFieldNames).apply(block).entries)

  private fun JsonObject.getProtoElement(snakeName: String): JsonElement? {
    val camelName = snakeToCamel(snakeName)
    return this[camelName]?.takeIf { it !is JsonNull }
      ?: this[snakeName]?.takeIf { it !is JsonNull }
  }

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

  private fun JsonObject.getProtoBoolean(snakeName: String): Boolean =
    getProtoBooleanOrNull(snakeName) ?: false

  private fun JsonObject.getProtoObject(snakeName: String): JsonObject? =
    getProtoElement(snakeName) as? JsonObject

  private fun JsonObject.getProtoObjects(snakeName: String): List<JsonObject> =
    (getProtoElement(snakeName) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

  private fun JsonObject.getProtoStrings(snakeName: String): List<String> =
    (getProtoElement(snakeName) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
      ?: emptyList()

  private fun JsonObject.getProtoStringMap(snakeName: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    when (val el = getProtoElement(snakeName)) {
      is JsonObject -> {
        el.forEach { (k, v) -> (v as? JsonPrimitive)?.content?.let { result[k] = it } }
      }
      is JsonArray -> {
        el.forEach { item ->
          val entry = item as? JsonObject ?: return@forEach
          result[entry.getProtoString("key")] = entry.getProtoString("value")
        }
      }
      else -> {}
    }
    return result
  }

  private fun JsonObject.getProtoTimestamp(snakeName: String): Instant? {
    return when (val el = getProtoElement(snakeName)) {
      is JsonPrimitive -> parseRfc3339Timestamp(el.content)
      is JsonObject ->
        ofEpochSecond(el.getProtoLongOrNull("seconds") ?: 0L, (el.getProtoInt("nanos")).toLong())
      else -> null
    }
  }

  private fun JsonObject.getProtoDate(snakeName: String): Date? {
    return when (val el = getProtoElement(snakeName)) {
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
