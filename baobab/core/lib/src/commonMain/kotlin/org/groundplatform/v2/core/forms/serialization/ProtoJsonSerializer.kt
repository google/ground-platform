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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.xpath.model.TemporalUtils

/**
 * Serializer and deserializer between ProtoForms Protocol Buffer models ([FormDef] and
 * [RecordInstance]) and Canonical Proto3 JSON (`ProtoJSON`).
 *
 * Adheres to the [Proto3 JSON Specification](https://protobuf.dev/programming-guides/proto3/#json):
 * - Emits `lowerCamelCase` field names by default (or `snake_case` when `preserveProtoFieldNames =
 *   true`).
 * - Accepts both `lowerCamelCase` and `snake_case` field names during deserialization.
 * - Serializes `map<string, V>` fields (`RecordNode.fields`, `LanguageTranslation.strings`, etc.)
 *   as native JSON objects.
 * - Serializes `int64` values as JSON strings and accepts both JSON strings and numbers on input.
 * - Serializes `google.protobuf.Timestamp` as RFC 3339 strings (`YYYY-MM-DDTHH:MM:SSZ`) and accepts
 *   both RFC 3339 strings and `{"seconds": ..., "nanos": ...}` objects on input.
 * - Serializes `bytes` as Base64 strings.
 */
object ProtoJsonSerializer {

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

  /** Parses a JSON string into a [RecordInstance] protobuf message. */
  fun deserializeRecordInstance(json: String): RecordInstance {
    val element =
      try {
        compactJson.parseToJsonElement(json)
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
      }
    val obj =
      element as? JsonObject
        ?: throw IllegalArgumentException("Expected JSON object at root for RecordInstance")
    return decodeRecordInstance(obj)
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
      msg.literal_value?.let { addObject("literal_value", encodeTypedValue(it, snake)) }
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
      msg.location?.let { addObject("location", encodeGeoPoint(it, snake)) }
      addString("old_value", msg.old_value)
      addString("new_value", msg.new_value)
    }

  private fun encodeRecordNode(msg: RecordNode, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      if (msg.fields.isNotEmpty()) {
        val mapObj = linkedMapOf<String, JsonElement>()
        msg.fields.forEach { (key, value) -> mapObj[key] = encodeFieldValue(value, snake) }
        addObject("fields", JsonObject(mapObj))
      }
    }

  private fun encodeFieldValue(msg: FieldValue, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      msg.scalar_value?.let { addObject("scalar_value", encodeTypedValue(it, snake)) }
      msg.list_value?.let { addObject("list_value", encodeTypedValueList(it, snake)) }
      msg.node_value?.let { addObject("node_value", encodeRecordNode(it, snake)) }
      msg.repeat_value?.let { addObject("repeat_value", encodeRecordNodeList(it, snake)) }
    }

  private fun encodeTypedValueList(msg: TypedValueList, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("values", msg.values) { encodeTypedValue(it, snake) }
    }

  private fun encodeRecordNodeList(msg: RecordNodeList, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("nodes", msg.nodes) { encodeRecordNode(it, snake) }
    }

  private fun encodeTypedValue(msg: TypedValue, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      msg.string_value?.let { addStringAlways("string_value", it) }
      msg.int32_value?.let { addIntAlways("int32_value", it) }
      // Proto3 JSON quotes 64-bit integers as strings to avoid IEEE-754 precision loss
      msg.int64_value?.let { addStringAlways("int64_value", it.toString()) }
      msg.double_value?.let { addDoubleAlways("double_value", it) }
      msg.bool_value?.let { addBooleanAlways("bool_value", it) }
      msg.date_value?.let { addObject("date_value", encodeDate(it, snake)) }
      msg.time_value?.let { addObject("time_value", encodeTimeOfDay(it, snake)) }
      msg.timestamp_value?.let { addStringAlways("timestamp_value", formatRfc3339Timestamp(it)) }
      msg.geopoint_value?.let { addObject("geopoint_value", encodeGeoPoint(it, snake)) }
      msg.binary_value?.let { addStringAlways("binary_value", it.base64()) }
      msg.geotrace_value?.let { addObject("geotrace_value", encodeGeoTrace(it, snake)) }
      msg.geoshape_value?.let { addObject("geoshape_value", encodeGeoShape(it, snake)) }
    }

  private fun encodeDate(msg: Date, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addInt("year", msg.year)
      addInt("month", msg.month)
      addInt("day", msg.day)
    }

  private fun encodeTimeOfDay(msg: TimeOfDay, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addInt("hours", msg.hours)
      addInt("minutes", msg.minutes)
      addInt("seconds", msg.seconds)
      addInt("nanos", msg.nanos)
    }

  private fun encodeGeoPoint(msg: GeoPoint, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addDoubleAlways("latitude", msg.latitude)
      addDoubleAlways("longitude", msg.longitude)
      addDouble("altitude_meters", msg.altitude_meters)
      addDouble("accuracy_meters", msg.accuracy_meters)
    }

  private fun encodeGeoTrace(msg: GeoTrace, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("points", msg.points) { encodeGeoPoint(it, snake) }
    }

  private fun encodeGeoShape(msg: GeoShape, snake: Boolean): JsonObject =
    buildProtoJsonObject(snake) {
      addObjectList("points", msg.points) { encodeGeoPoint(it, snake) }
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

  private fun decodePrimaryInstance(obj: JsonObject): PrimaryInstance =
    PrimaryInstance(
      record_schema = obj.getProtoObject("record_schema")?.let { decodeRecordSchema(it) },
      default_values = obj.getProtoObject("default_values")?.let { decodeRecordNode(it) },
      message_type_name = obj.getProtoString("message_type_name"),
    )

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
      literal_value = obj.getProtoObject("literal_value")?.let { decodeTypedValue(it) },
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

  private fun decodeRecordInstance(obj: JsonObject): RecordInstance =
    RecordInstance(
      form_id = obj.getProtoString("form_id"),
      form_version = obj.getProtoString("form_version"),
      metadata = obj.getProtoObject("metadata")?.let { decodeRecordMetadata(it) },
      data_ = obj.getProtoObject("data")?.let { decodeRecordNode(it) },
      audit_log = obj.getProtoObject("audit_log")?.let { decodeAuditLog(it) },
    )

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
      location = obj.getProtoObject("location")?.let { decodeGeoPoint(it) },
      old_value = obj.getProtoString("old_value"),
      new_value = obj.getProtoString("new_value"),
    )

  private fun decodeRecordNode(obj: JsonObject): RecordNode {
    val fieldsMap = linkedMapOf<String, FieldValue>()
    val rawFields = obj.getProtoElement("fields")
    when (rawFields) {
      is JsonObject -> {
        rawFields.forEach { (key, valueEl) ->
          (valueEl as? JsonObject)?.let { fieldsMap[key] = decodeFieldValue(it) }
        }
      }
      is JsonArray -> {
        rawFields.forEach { entryEl ->
          val entry = entryEl as? JsonObject ?: return@forEach
          val key = entry.getProtoString("key")
          val valueObj = entry.getProtoObject("value")
          if (valueObj != null) {
            fieldsMap[key] = decodeFieldValue(valueObj)
          }
        }
      }
      else -> {}
    }
    return RecordNode(fields = fieldsMap)
  }

  private fun decodeFieldValue(obj: JsonObject): FieldValue =
    FieldValue(
      scalar_value = obj.getProtoObject("scalar_value")?.let { decodeTypedValue(it) },
      list_value = obj.getProtoObject("list_value")?.let { decodeTypedValueList(it) },
      node_value = obj.getProtoObject("node_value")?.let { decodeRecordNode(it) },
      repeat_value = obj.getProtoObject("repeat_value")?.let { decodeRecordNodeList(it) },
    )

  private fun decodeTypedValueList(obj: JsonObject): TypedValueList =
    TypedValueList(values = obj.getProtoObjects("values").map { decodeTypedValue(it) })

  private fun decodeRecordNodeList(obj: JsonObject): RecordNodeList =
    RecordNodeList(nodes = obj.getProtoObjects("nodes").map { decodeRecordNode(it) })

  private fun decodeTypedValue(obj: JsonObject): TypedValue =
    TypedValue(
      string_value = obj.getProtoStringOrNull("string_value"),
      int32_value = obj.getProtoIntOrNull("int32_value"),
      int64_value = obj.getProtoLongOrNull("int64_value"),
      double_value = obj.getProtoDoubleOrNull("double_value"),
      bool_value = obj.getProtoBooleanOrNull("bool_value"),
      date_value = obj.getProtoDate("date_value"),
      time_value = obj.getProtoTimeOfDay("time_value"),
      timestamp_value = obj.getProtoTimestamp("timestamp_value"),
      geopoint_value = obj.getProtoObject("geopoint_value")?.let { decodeGeoPoint(it) },
      binary_value =
        obj.getProtoStringOrNull("binary_value")?.let { str ->
          str.decodeBase64() ?: str.encodeUtf8()
        },
      geotrace_value = obj.getProtoObject("geotrace_value")?.let { decodeGeoTrace(it) },
      geoshape_value = obj.getProtoObject("geoshape_value")?.let { decodeGeoShape(it) },
    )

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

  private fun decodeGeoPoint(obj: JsonObject): GeoPoint =
    GeoPoint(
      latitude = obj.getProtoDouble("latitude"),
      longitude = obj.getProtoDouble("longitude"),
      altitude_meters = obj.getProtoDouble("altitude_meters"),
      accuracy_meters = obj.getProtoDouble("accuracy_meters"),
    )

  private fun decodeGeoTrace(obj: JsonObject): GeoTrace =
    GeoTrace(points = obj.getProtoObjects("points").map { decodeGeoPoint(it) })

  private fun decodeGeoShape(obj: JsonObject): GeoShape =
    GeoShape(points = obj.getProtoObjects("points").map { decodeGeoPoint(it) })

  private inline fun <reified E : Enum<E>> parseEnum(name: String?, default: E): E {
    if (name == null) return default
    return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
  }

  // ===========================================================================
  // RFC 3339 Timestamp & Temporal Helpers
  // ===========================================================================

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
