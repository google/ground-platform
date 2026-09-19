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
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoField
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoMessage
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoParser
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoValue
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoWriter

/**
 * Serializer and deserializer between ProtoForms Protocol Buffer models ([FormDef] and
 * [RecordInstance]) and Protocol Buffer Text Format (`textproto` / `txtpb`).
 */
object TextProtoSerializer {

  /** Formats a [FormDef] protobuf message into a pretty-printed `textproto` string. */
  fun serializeFormDef(formDef: FormDef): String =
    TextProtoWriter().writeMessage(encodeFormDef(formDef))

  /** Parses a `textproto` string into a [FormDef] protobuf message. */
  fun deserializeFormDef(textproto: String): FormDef =
    decodeFormDef(TextProtoParser.parse(textproto))

  /** Formats a [RecordInstance] protobuf message into a pretty-printed `textproto` string. */
  fun serializeRecordInstance(record: RecordInstance): String =
    TextProtoWriter().writeMessage(encodeRecordInstance(record))

  /** Parses a `textproto` string into a [RecordInstance] protobuf message. */
  fun deserializeRecordInstance(textproto: String): RecordInstance =
    decodeRecordInstance(TextProtoParser.parse(textproto))

  // ===========================================================================
  // FormDef Encoding
  // ===========================================================================

  private fun encodeFormDef(msg: FormDef): TextProtoMessage = buildMessage {
    addString("form_id", msg.form_id)
    addString("title", msg.title)
    addString("version", msg.version)
    addString("default_language", msg.default_language)
    msg.model?.let { addMessage("model", encodeModelDef(it)) }
    msg.view?.let { addMessage("view", encodeViewDef(it)) }
  }

  private fun encodeModelDef(msg: ModelDef): TextProtoMessage = buildMessage {
    msg.primary_instance?.let { addMessage("primary_instance", encodePrimaryInstance(it)) }
    msg.secondary_instances.forEach {
      addMessage("secondary_instances", encodeSecondaryInstance(it))
    }
    msg.bindings.forEach { addMessage("bindings", encodeFieldBinding(it)) }
    msg.translations?.let { addMessage("translations", encodeTranslationCatalog(it)) }
    msg.actions.forEach { addMessage("actions", encodeActionDef(it)) }
    msg.entities.forEach { addMessage("entities", encodeEntityDeclaration(it)) }
    msg.metadata?.let { addMessage("metadata", encodeRecordMetadata(it)) }
    msg.submission?.let { addMessage("submission", encodeSubmissionConfig(it)) }
  }

  private fun encodePrimaryInstance(msg: PrimaryInstance): TextProtoMessage = buildMessage {
    msg.record_schema?.let { addMessage("record_schema", encodeRecordSchema(it)) }
    msg.default_values?.let {
      if (it.fields.isNotEmpty()) {
        addMessage("default_values", encodeRecordNode(it))
      }
    }
    addString("message_type_name", msg.message_type_name)
  }

  private fun encodeRecordSchema(msg: RecordSchema): TextProtoMessage = buildMessage {
    addString("name", msg.name)
    addString("title", msg.title)
    msg.fields.forEach { addMessage("fields", encodeFieldDefinition(it)) }
    addString("sms_prefix", msg.sms_prefix)
    addString("sms_delimiter", msg.sms_delimiter)
  }

  private fun encodeFieldDefinition(msg: FieldDefinition): TextProtoMessage = buildMessage {
    addString("name", msg.name)
    if (msg.type != DataType.DATA_TYPE_UNSPECIFIED) {
      addEnum("type", msg.type.name)
    }
    addBoolean("is_repeated", msg.is_repeated)
    msg.fields.forEach { addMessage("fields", encodeFieldDefinition(it)) }
    addString("type_name", msg.type_name)
  }

  private fun encodeSecondaryInstance(msg: SecondaryInstance): TextProtoMessage = buildMessage {
    addString("id", msg.id)
    addString("uri", msg.uri)
    addString("inline_data", msg.inline_data)
  }

  private fun encodeFieldBinding(msg: FieldBinding): TextProtoMessage = buildMessage {
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

  private fun encodeTranslationCatalog(msg: TranslationCatalog): TextProtoMessage = buildMessage {
    msg.languages.forEach { addMessage("languages", encodeLanguageTranslation(it)) }
  }

  private fun encodeLanguageTranslation(msg: LanguageTranslation): TextProtoMessage = buildMessage {
    addString("language", msg.language)
    addBoolean("is_default", msg.is_default)
    msg.strings.forEach { (key, value) ->
      addMessage(
        "strings",
        buildMessage {
          addStringAlways("key", key)
          addMessage("value", encodeLocalizedString(value))
        },
      )
    }
  }

  private fun encodeLocalizedString(msg: LocalizedString): TextProtoMessage = buildMessage {
    addString("value", msg.value_)
    addString("short_value", msg.short_value)
    addString("guidance_value", msg.guidance_value)
    msg.media?.let { addMessage("media", encodeMediaRef(it)) }
  }

  private fun encodeMediaRef(msg: MediaRef): TextProtoMessage = buildMessage {
    addString("image_uri", msg.image_uri)
    addString("big_image_uri", msg.big_image_uri)
    addString("audio_uri", msg.audio_uri)
    addString("video_uri", msg.video_uri)
  }

  private fun encodeActionDef(msg: ActionDef): TextProtoMessage = buildMessage {
    msg.events.forEach {
      if (it != EventType.EVENT_TYPE_UNSPECIFIED) {
        addEnum("events", it.name)
      }
    }
    if (msg.type != ActionType.ACTION_TYPE_UNSPECIFIED) {
      addEnum("type", msg.type.name)
    }
    addString("target_field", msg.target_field)
    addString("value_expression", msg.value_expression)
    msg.literal_value?.let { addMessage("literal_value", encodeTypedValue(it)) }
  }

  private fun encodeEntityDeclaration(msg: EntityDeclaration): TextProtoMessage = buildMessage {
    addString("dataset", msg.dataset)
    addString("entity_id_expression", msg.entity_id_expression)
    addString("label_expression", msg.label_expression)
    addString("create_condition", msg.create_condition)
    addString("update_condition", msg.update_condition)
    msg.sync_metadata?.let { addMessage("sync_metadata", encodeEntitySyncMetadata(it)) }
    msg.property_mappings.forEach {
      addMessage("property_mappings", encodeEntityPropertyMapping(it))
    }
  }

  private fun encodeEntitySyncMetadata(msg: EntitySyncMetadata): TextProtoMessage = buildMessage {
    addString("base_version_expression", msg.base_version_expression)
    addString("trunk_version_expression", msg.trunk_version_expression)
    addString("branch_id_expression", msg.branch_id_expression)
  }

  private fun encodeEntityPropertyMapping(msg: EntityPropertyMapping): TextProtoMessage =
    buildMessage {
      addString("entity_property", msg.entity_property)
      addString("source_field_path", msg.source_field_path)
    }

  private fun encodeSubmissionConfig(msg: SubmissionConfig): TextProtoMessage = buildMessage {
    addString("action_url", msg.action_url)
    addString("method", msg.method)
    addString("base64_rsa_public_key", msg.base64_rsa_public_key)
    addBoolean("auto_send", msg.auto_send)
    addBoolean("auto_delete", msg.auto_delete)
    addBoolean("client_editable", msg.client_editable)
  }

  private fun encodeViewDef(msg: ViewDef): TextProtoMessage = buildMessage {
    msg.components.forEach { addMessage("components", encodeViewComponent(it)) }
  }

  private fun encodeViewComponent(msg: ViewComponent): TextProtoMessage = buildMessage {
    msg.control?.let { addMessage("control", encodeControlDef(it)) }
    msg.group?.let { addMessage("group", encodeGroupDef(it)) }
    msg.repeat?.let { addMessage("repeat", encodeRepeatDef(it)) }
  }

  private fun encodeControlDef(msg: ControlDef): TextProtoMessage = buildMessage {
    addString("field_ref", msg.field_ref)
    if (msg.type != ControlType.CONTROL_TYPE_UNSPECIFIED) {
      addEnum("type", msg.type.name)
    }
    msg.label?.let { addMessage("label", encodeLabelDef(it)) }
    msg.hint?.let { addMessage("hint", encodeLabelDef(it)) }
    addString("appearance", msg.appearance)
    msg.choices.forEach { addMessage("choices", encodeChoiceItem(it)) }
    msg.itemset?.let { addMessage("itemset", encodeItemsetDef(it)) }
    msg.range_config?.let { addMessage("range_config", encodeRangeConfig(it)) }
    addString("media_type", msg.media_type)
    msg.intent?.let { addMessage("intent", encodeIntentConfig(it)) }
    msg.actions.forEach { addMessage("actions", encodeActionDef(it)) }
    msg.geo_config?.let { addMessage("geo_config", encodeGeoConfig(it)) }
  }

  private fun encodeLabelDef(msg: LabelDef): TextProtoMessage = buildMessage {
    addString("text", msg.text)
    addString("text_id", msg.text_id)
    msg.outputs.forEach { addMessage("outputs", encodeOutputFragment(it)) }
  }

  private fun encodeOutputFragment(msg: OutputFragment): TextProtoMessage = buildMessage {
    addString("placeholder_id", msg.placeholder_id)
    addString("value_expression", msg.value_expression)
  }

  private fun encodeChoiceItem(msg: ChoiceItem): TextProtoMessage = buildMessage {
    addString("value", msg.value_)
    msg.label?.let { addMessage("label", encodeLabelDef(it)) }
    msg.properties.forEach { (key, value) ->
      addMessage(
        "properties",
        buildMessage {
          addStringAlways("key", key)
          addStringAlways("value", value)
        },
      )
    }
  }

  private fun encodeItemsetDef(msg: ItemsetDef): TextProtoMessage = buildMessage {
    addString("instance_id", msg.instance_id)
    addString("nodeset_filter", msg.nodeset_filter)
    addString("value_ref", msg.value_ref)
    addString("label_ref", msg.label_ref)
    addBoolean("randomize", msg.randomize)
    addString("random_seed_expression", msg.random_seed_expression)
  }

  private fun encodeRangeConfig(msg: RangeConfig): TextProtoMessage = buildMessage {
    addDouble("start", msg.start)
    addDouble("end", msg.end)
    addDouble("step", msg.step)
    addDouble("tick_interval", msg.tick_interval)
  }

  private fun encodeGeoConfig(msg: GeoConfig): TextProtoMessage = buildMessage {
    addDouble("accuracy_threshold_meters", msg.accuracy_threshold_meters)
    addDouble("warning_threshold_meters", msg.warning_threshold_meters)
    addBoolean("allow_mock_accuracy", msg.allow_mock_accuracy)
  }

  private fun encodeIntentConfig(msg: IntentConfig): TextProtoMessage = buildMessage {
    addString("intent_uri", msg.intent_uri)
    msg.parameters.forEach { (key, value) ->
      addMessage(
        "parameters",
        buildMessage {
          addStringAlways("key", key)
          addStringAlways("value", value)
        },
      )
    }
    msg.response_mappings.forEach { (key, value) ->
      addMessage(
        "response_mappings",
        buildMessage {
          addStringAlways("key", key)
          addStringAlways("value", value)
        },
      )
    }
  }

  private fun encodeGroupDef(msg: GroupDef): TextProtoMessage = buildMessage {
    addString("field_ref", msg.field_ref)
    msg.label?.let { addMessage("label", encodeLabelDef(it)) }
    addString("appearance", msg.appearance)
    msg.intent?.let { addMessage("intent", encodeIntentConfig(it)) }
    msg.components.forEach { addMessage("components", encodeViewComponent(it)) }
  }

  private fun encodeRepeatDef(msg: RepeatDef): TextProtoMessage = buildMessage {
    addString("field_ref", msg.field_ref)
    msg.label?.let { addMessage("label", encodeLabelDef(it)) }
    addString("appearance", msg.appearance)
    addString("count_expression", msg.count_expression)
    addBoolean("no_add_remove", msg.no_add_remove)
    msg.components.forEach { addMessage("components", encodeViewComponent(it)) }
  }

  // ===========================================================================
  // RecordInstance Encoding
  // ===========================================================================

  private fun encodeRecordInstance(msg: RecordInstance): TextProtoMessage = buildMessage {
    addString("form_id", msg.form_id)
    addString("form_version", msg.form_version)
    msg.metadata?.let {
      val metaMsg = encodeRecordMetadata(it)
      if (metaMsg.fields.isNotEmpty()) {
        addMessage("metadata", metaMsg)
      }
    }
    msg.data_?.let {
      val dataMsg = encodeRecordNode(it)
      if (dataMsg.fields.isNotEmpty()) {
        addMessage("data", dataMsg)
      }
    }
    msg.audit_log?.let {
      if (it.events.isNotEmpty()) {
        addMessage("audit_log", encodeAuditLog(it))
      }
    }
  }

  private fun encodeRecordMetadata(msg: RecordMetadata): TextProtoMessage = buildMessage {
    addString("instance_id", msg.instance_id)
    msg.start_time?.let { addMessage("start_time", encodeInstant(it)) }
    msg.end_time?.let { addMessage("end_time", encodeInstant(it)) }
    msg.today?.let { addMessage("today", encodeInstant(it)) }
    addString("device_id", msg.device_id)
    addString("subscriber_id", msg.subscriber_id)
    addString("sim_serial", msg.sim_serial)
    addString("phone_number", msg.phone_number)
    addString("audit_file_uri", msg.audit_file_uri)
    msg.audit_config?.let { addMessage("audit_config", encodeAuditConfig(it)) }
  }

  private fun encodeAuditConfig(msg: AuditConfig): TextProtoMessage = buildMessage {
    addBoolean("enabled", msg.enabled)
    addInt("location_min_interval_seconds", msg.location_min_interval_seconds)
    addInt("location_max_age_seconds", msg.location_max_age_seconds)
    addString("location_priority", msg.location_priority)
    addBoolean("track_changes", msg.track_changes)
    addString("track_changes_reasons", msg.track_changes_reasons)
  }

  private fun encodeAuditLog(msg: AuditLog): TextProtoMessage = buildMessage {
    msg.events.forEach { addMessage("events", encodeAuditEvent(it)) }
  }

  private fun encodeAuditEvent(msg: AuditEvent): TextProtoMessage = buildMessage {
    addString("event", msg.event)
    addString("field_path", msg.field_path)
    msg.start_time?.let { addMessage("start_time", encodeInstant(it)) }
    msg.end_time?.let { addMessage("end_time", encodeInstant(it)) }
    msg.location?.let { addMessage("location", encodeGeoPoint(it)) }
    addString("old_value", msg.old_value)
    addString("new_value", msg.new_value)
  }

  private fun encodeRecordNode(msg: RecordNode): TextProtoMessage = buildMessage {
    msg.fields.forEach { (key, value) ->
      addMessage(
        "fields",
        buildMessage {
          addStringAlways("key", key)
          addMessage("value", encodeFieldValue(value))
        },
      )
    }
  }

  private fun encodeFieldValue(msg: FieldValue): TextProtoMessage = buildMessage {
    msg.scalar_value?.let { addMessage("scalar_value", encodeTypedValue(it)) }
    msg.list_value?.let { addMessage("list_value", encodeTypedValueList(it)) }
    msg.node_value?.let { addMessage("node_value", encodeRecordNode(it)) }
    msg.repeat_value?.let { addMessage("repeat_value", encodeRecordNodeList(it)) }
  }

  private fun encodeTypedValueList(msg: TypedValueList): TextProtoMessage = buildMessage {
    msg.values.forEach { addMessage("values", encodeTypedValue(it)) }
  }

  private fun encodeRecordNodeList(msg: RecordNodeList): TextProtoMessage = buildMessage {
    msg.nodes.forEach { addMessage("nodes", encodeRecordNode(it)) }
  }

  private fun encodeTypedValue(msg: TypedValue): TextProtoMessage = buildMessage {
    msg.string_value?.let { addStringAlways("string_value", it) }
    msg.int32_value?.let { addIntAlways("int32_value", it) }
    msg.int64_value?.let { addLongAlways("int64_value", it) }
    msg.double_value?.let { addDoubleAlways("double_value", it) }
    msg.bool_value?.let { addBooleanAlways("bool_value", it) }
    msg.date_value?.let { addMessage("date_value", encodeDate(it)) }
    msg.time_value?.let { addMessage("time_value", encodeTimeOfDay(it)) }
    msg.timestamp_value?.let { addMessage("timestamp_value", encodeInstant(it)) }
    msg.geopoint_value?.let { addMessage("geopoint_value", encodeGeoPoint(it)) }
    msg.binary_value?.let { addStringAlways("binary_value", it.utf8()) }
    msg.geotrace_value?.let { addMessage("geotrace_value", encodeGeoTrace(it)) }
    msg.geoshape_value?.let { addMessage("geoshape_value", encodeGeoShape(it)) }
  }

  private fun encodeDate(msg: Date): TextProtoMessage = buildMessage {
    addInt("year", msg.year)
    addInt("month", msg.month)
    addInt("day", msg.day)
  }

  private fun encodeTimeOfDay(msg: TimeOfDay): TextProtoMessage = buildMessage {
    addInt("hours", msg.hours)
    addInt("minutes", msg.minutes)
    addInt("seconds", msg.seconds)
    addInt("nanos", msg.nanos)
  }

  private fun encodeInstant(msg: Instant): TextProtoMessage = buildMessage {
    val sec = msg.getEpochSecond()
    val nano = msg.getNano()
    if (sec != 0L || nano == 0) {
      addLongAlways("seconds", sec)
    }
    if (nano != 0) {
      addIntAlways("nanos", nano)
    }
  }

  private fun encodeGeoPoint(msg: GeoPoint): TextProtoMessage = buildMessage {
    addDoubleAlways("latitude", msg.latitude)
    addDoubleAlways("longitude", msg.longitude)
    addDouble("altitude_meters", msg.altitude_meters)
    addDouble("accuracy_meters", msg.accuracy_meters)
  }

  private fun encodeGeoTrace(msg: GeoTrace): TextProtoMessage = buildMessage {
    msg.points.forEach { addMessage("points", encodeGeoPoint(it)) }
  }

  private fun encodeGeoShape(msg: GeoShape): TextProtoMessage = buildMessage {
    msg.points.forEach { addMessage("points", encodeGeoPoint(it)) }
  }

  // ===========================================================================
  // FormDef Decoding
  // ===========================================================================

  private fun decodeFormDef(node: TextProtoMessage): FormDef =
    FormDef(
      form_id = node.getString("form_id"),
      title = node.getString("title"),
      version = node.getString("version"),
      default_language = node.getString("default_language"),
      model = node.getMessageOrNull("model")?.let { decodeModelDef(it) },
      view = node.getMessageOrNull("view")?.let { decodeViewDef(it) },
    )

  private fun decodeModelDef(node: TextProtoMessage): ModelDef =
    ModelDef(
      primary_instance =
        node.getMessageOrNull("primary_instance")?.let { decodePrimaryInstance(it) },
      secondary_instances =
        node.getMessages("secondary_instances").map { decodeSecondaryInstance(it) },
      bindings = node.getMessages("bindings").map { decodeFieldBinding(it) },
      translations = node.getMessageOrNull("translations")?.let { decodeTranslationCatalog(it) },
      actions = node.getMessages("actions").map { decodeActionDef(it) },
      entities = node.getMessages("entities").map { decodeEntityDeclaration(it) },
      metadata = node.getMessageOrNull("metadata")?.let { decodeRecordMetadata(it) },
      submission = node.getMessageOrNull("submission")?.let { decodeSubmissionConfig(it) },
    )

  private fun decodePrimaryInstance(node: TextProtoMessage): PrimaryInstance =
    PrimaryInstance(
      record_schema = node.getMessageOrNull("record_schema")?.let { decodeRecordSchema(it) },
      default_values = node.getMessageOrNull("default_values")?.let { decodeRecordNode(it) },
      message_type_name = node.getString("message_type_name"),
    )

  private fun decodeRecordSchema(node: TextProtoMessage): RecordSchema =
    RecordSchema(
      name = node.getString("name"),
      title = node.getString("title"),
      fields = node.getMessages("fields").map { decodeFieldDefinition(it) },
      sms_prefix = node.getString("sms_prefix"),
      sms_delimiter = node.getString("sms_delimiter"),
    )

  private fun decodeFieldDefinition(node: TextProtoMessage): FieldDefinition =
    FieldDefinition(
      name = node.getString("name"),
      type = parseEnum(node.getIdentifierOrNull("type"), DataType.DATA_TYPE_UNSPECIFIED),
      is_repeated = node.getBoolean("is_repeated"),
      fields = node.getMessages("fields").map { decodeFieldDefinition(it) },
      type_name = node.getString("type_name"),
    )

  private fun decodeSecondaryInstance(node: TextProtoMessage): SecondaryInstance =
    SecondaryInstance(
      id = node.getString("id"),
      uri = node.getString("uri"),
      inline_data = node.getString("inline_data"),
    )

  private fun decodeFieldBinding(node: TextProtoMessage): FieldBinding =
    FieldBinding(
      field_path = node.getString("field_path"),
      type = parseEnum(node.getIdentifierOrNull("type"), DataType.DATA_TYPE_UNSPECIFIED),
      read_only = node.getBoolean("read_only"),
      relevant_expression = node.getString("relevant_expression"),
      calculate_expression = node.getString("calculate_expression"),
      constraint_expression = node.getString("constraint_expression"),
      required_expression = node.getString("required_expression"),
      constraint_message = node.getString("constraint_message"),
      required_message = node.getString("required_message"),
      sms_tag = node.getString("sms_tag"),
      entity_saveto = node.getString("entity_saveto"),
      max_pixels = node.getInt("max_pixels"),
      preload = parseEnum(node.getIdentifierOrNull("preload"), PreloadType.PRELOAD_UNSPECIFIED),
      preload_param = node.getString("preload_param"),
    )

  private fun decodeTranslationCatalog(node: TextProtoMessage): TranslationCatalog =
    TranslationCatalog(
      languages = node.getMessages("languages").map { decodeLanguageTranslation(it) }
    )

  private fun decodeLanguageTranslation(node: TextProtoMessage): LanguageTranslation {
    val stringsMap = linkedMapOf<String, LocalizedString>()
    for (entry in node.getMessages("strings")) {
      val key = entry.getString("key")
      val valueMsg = entry.getMessageOrNull("value")
      if (valueMsg != null) {
        stringsMap[key] = decodeLocalizedString(valueMsg)
      }
    }
    return LanguageTranslation(
      language = node.getString("language"),
      is_default = node.getBoolean("is_default"),
      strings = stringsMap,
    )
  }

  private fun decodeLocalizedString(node: TextProtoMessage): LocalizedString =
    LocalizedString(
      value_ = node.getString("value"),
      short_value = node.getString("short_value"),
      guidance_value = node.getString("guidance_value"),
      media = node.getMessageOrNull("media")?.let { decodeMediaRef(it) },
    )

  private fun decodeMediaRef(node: TextProtoMessage): MediaRef =
    MediaRef(
      image_uri = node.getString("image_uri"),
      big_image_uri = node.getString("big_image_uri"),
      audio_uri = node.getString("audio_uri"),
      video_uri = node.getString("video_uri"),
    )

  private fun decodeActionDef(node: TextProtoMessage): ActionDef =
    ActionDef(
      events =
        node.getIdentifiers("events").map { parseEnum(it, EventType.EVENT_TYPE_UNSPECIFIED) },
      type = parseEnum(node.getIdentifierOrNull("type"), ActionType.ACTION_TYPE_UNSPECIFIED),
      target_field = node.getString("target_field"),
      value_expression = node.getString("value_expression"),
      literal_value = node.getMessageOrNull("literal_value")?.let { decodeTypedValue(it) },
    )

  private fun decodeEntityDeclaration(node: TextProtoMessage): EntityDeclaration =
    EntityDeclaration(
      dataset = node.getString("dataset"),
      entity_id_expression = node.getString("entity_id_expression"),
      label_expression = node.getString("label_expression"),
      create_condition = node.getString("create_condition"),
      update_condition = node.getString("update_condition"),
      sync_metadata = node.getMessageOrNull("sync_metadata")?.let { decodeEntitySyncMetadata(it) },
      property_mappings =
        node.getMessages("property_mappings").map { decodeEntityPropertyMapping(it) },
    )

  private fun decodeEntitySyncMetadata(node: TextProtoMessage): EntitySyncMetadata =
    EntitySyncMetadata(
      base_version_expression = node.getString("base_version_expression"),
      trunk_version_expression = node.getString("trunk_version_expression"),
      branch_id_expression = node.getString("branch_id_expression"),
    )

  private fun decodeEntityPropertyMapping(node: TextProtoMessage): EntityPropertyMapping =
    EntityPropertyMapping(
      entity_property = node.getString("entity_property"),
      source_field_path = node.getString("source_field_path"),
    )

  private fun decodeSubmissionConfig(node: TextProtoMessage): SubmissionConfig =
    SubmissionConfig(
      action_url = node.getString("action_url"),
      method = node.getString("method"),
      base64_rsa_public_key = node.getString("base64_rsa_public_key"),
      auto_send = node.getBoolean("auto_send"),
      auto_delete = node.getBoolean("auto_delete"),
      client_editable = node.getBoolean("client_editable"),
    )

  private fun decodeViewDef(node: TextProtoMessage): ViewDef =
    ViewDef(components = node.getMessages("components").map { decodeViewComponent(it) })

  private fun decodeViewComponent(node: TextProtoMessage): ViewComponent =
    ViewComponent(
      control = node.getMessageOrNull("control")?.let { decodeControlDef(it) },
      group = node.getMessageOrNull("group")?.let { decodeGroupDef(it) },
      repeat = node.getMessageOrNull("repeat")?.let { decodeRepeatDef(it) },
    )

  private fun decodeControlDef(node: TextProtoMessage): ControlDef =
    ControlDef(
      field_ref = node.getString("field_ref"),
      type = parseEnum(node.getIdentifierOrNull("type"), ControlType.CONTROL_TYPE_UNSPECIFIED),
      label = node.getMessageOrNull("label")?.let { decodeLabelDef(it) },
      hint = node.getMessageOrNull("hint")?.let { decodeLabelDef(it) },
      appearance = node.getString("appearance"),
      choices = node.getMessages("choices").map { decodeChoiceItem(it) },
      itemset = node.getMessageOrNull("itemset")?.let { decodeItemsetDef(it) },
      range_config = node.getMessageOrNull("range_config")?.let { decodeRangeConfig(it) },
      media_type = node.getString("media_type"),
      intent = node.getMessageOrNull("intent")?.let { decodeIntentConfig(it) },
      actions = node.getMessages("actions").map { decodeActionDef(it) },
      geo_config = node.getMessageOrNull("geo_config")?.let { decodeGeoConfig(it) },
    )

  private fun decodeLabelDef(node: TextProtoMessage): LabelDef =
    LabelDef(
      text = node.getString("text"),
      text_id = node.getString("text_id"),
      outputs = node.getMessages("outputs").map { decodeOutputFragment(it) },
    )

  private fun decodeOutputFragment(node: TextProtoMessage): OutputFragment =
    OutputFragment(
      placeholder_id = node.getString("placeholder_id"),
      value_expression = node.getString("value_expression"),
    )

  private fun decodeChoiceItem(node: TextProtoMessage): ChoiceItem {
    val props = linkedMapOf<String, String>()
    for (entry in node.getMessages("properties")) {
      props[entry.getString("key")] = entry.getString("value")
    }
    return ChoiceItem(
      value_ = node.getString("value"),
      label = node.getMessageOrNull("label")?.let { decodeLabelDef(it) },
      properties = props,
    )
  }

  private fun decodeItemsetDef(node: TextProtoMessage): ItemsetDef =
    ItemsetDef(
      instance_id = node.getString("instance_id"),
      nodeset_filter = node.getString("nodeset_filter"),
      value_ref = node.getString("value_ref"),
      label_ref = node.getString("label_ref"),
      randomize = node.getBoolean("randomize"),
      random_seed_expression = node.getString("random_seed_expression"),
    )

  private fun decodeRangeConfig(node: TextProtoMessage): RangeConfig =
    RangeConfig(
      start = node.getDouble("start"),
      end = node.getDouble("end"),
      step = node.getDouble("step"),
      tick_interval = node.getDouble("tick_interval"),
    )

  private fun decodeGeoConfig(node: TextProtoMessage): GeoConfig =
    GeoConfig(
      accuracy_threshold_meters = node.getDouble("accuracy_threshold_meters"),
      warning_threshold_meters = node.getDouble("warning_threshold_meters"),
      allow_mock_accuracy = node.getBoolean("allow_mock_accuracy"),
    )

  private fun decodeIntentConfig(node: TextProtoMessage): IntentConfig {
    val params = linkedMapOf<String, String>()
    for (entry in node.getMessages("parameters")) {
      params[entry.getString("key")] = entry.getString("value")
    }
    val resp = linkedMapOf<String, String>()
    for (entry in node.getMessages("response_mappings")) {
      resp[entry.getString("key")] = entry.getString("value")
    }
    return IntentConfig(
      intent_uri = node.getString("intent_uri"),
      parameters = params,
      response_mappings = resp,
    )
  }

  private fun decodeGroupDef(node: TextProtoMessage): GroupDef =
    GroupDef(
      field_ref = node.getString("field_ref"),
      label = node.getMessageOrNull("label")?.let { decodeLabelDef(it) },
      appearance = node.getString("appearance"),
      intent = node.getMessageOrNull("intent")?.let { decodeIntentConfig(it) },
      components = node.getMessages("components").map { decodeViewComponent(it) },
    )

  private fun decodeRepeatDef(node: TextProtoMessage): RepeatDef =
    RepeatDef(
      field_ref = node.getString("field_ref"),
      label = node.getMessageOrNull("label")?.let { decodeLabelDef(it) },
      appearance = node.getString("appearance"),
      count_expression = node.getString("count_expression"),
      no_add_remove = node.getBoolean("no_add_remove"),
      components = node.getMessages("components").map { decodeViewComponent(it) },
    )

  // ===========================================================================
  // RecordInstance Decoding
  // ===========================================================================

  private fun decodeRecordInstance(node: TextProtoMessage): RecordInstance =
    RecordInstance(
      form_id = node.getString("form_id"),
      form_version = node.getString("form_version"),
      metadata = node.getMessageOrNull("metadata")?.let { decodeRecordMetadata(it) },
      data_ = node.getMessageOrNull("data")?.let { decodeRecordNode(it) },
      audit_log = node.getMessageOrNull("audit_log")?.let { decodeAuditLog(it) },
    )

  private fun decodeRecordMetadata(node: TextProtoMessage): RecordMetadata =
    RecordMetadata(
      instance_id = node.getString("instance_id"),
      start_time = node.getMessageOrNull("start_time")?.let { decodeInstant(it) },
      end_time = node.getMessageOrNull("end_time")?.let { decodeInstant(it) },
      today = node.getMessageOrNull("today")?.let { decodeInstant(it) },
      device_id = node.getString("device_id"),
      subscriber_id = node.getString("subscriber_id"),
      sim_serial = node.getString("sim_serial"),
      phone_number = node.getString("phone_number"),
      audit_file_uri = node.getString("audit_file_uri"),
      audit_config = node.getMessageOrNull("audit_config")?.let { decodeAuditConfig(it) },
    )

  private fun decodeAuditConfig(node: TextProtoMessage): AuditConfig =
    AuditConfig(
      enabled = node.getBoolean("enabled"),
      location_min_interval_seconds = node.getInt("location_min_interval_seconds"),
      location_max_age_seconds = node.getInt("location_max_age_seconds"),
      location_priority = node.getString("location_priority"),
      track_changes = node.getBoolean("track_changes"),
      track_changes_reasons = node.getString("track_changes_reasons"),
    )

  private fun decodeAuditLog(node: TextProtoMessage): AuditLog =
    AuditLog(events = node.getMessages("events").map { decodeAuditEvent(it) })

  private fun decodeAuditEvent(node: TextProtoMessage): AuditEvent =
    AuditEvent(
      event = node.getString("event"),
      field_path = node.getString("field_path"),
      start_time = node.getMessageOrNull("start_time")?.let { decodeInstant(it) },
      end_time = node.getMessageOrNull("end_time")?.let { decodeInstant(it) },
      location = node.getMessageOrNull("location")?.let { decodeGeoPoint(it) },
      old_value = node.getString("old_value"),
      new_value = node.getString("new_value"),
    )

  private fun decodeRecordNode(node: TextProtoMessage): RecordNode {
    val fieldsMap = linkedMapOf<String, FieldValue>()
    for (entry in node.getMessages("fields")) {
      val key = entry.getString("key")
      val valueMsg = entry.getMessageOrNull("value")
      if (valueMsg != null) {
        fieldsMap[key] = decodeFieldValue(valueMsg)
      }
    }
    return RecordNode(fields = fieldsMap)
  }

  private fun decodeFieldValue(node: TextProtoMessage): FieldValue =
    FieldValue(
      scalar_value = node.getMessageOrNull("scalar_value")?.let { decodeTypedValue(it) },
      list_value = node.getMessageOrNull("list_value")?.let { decodeTypedValueList(it) },
      node_value = node.getMessageOrNull("node_value")?.let { decodeRecordNode(it) },
      repeat_value = node.getMessageOrNull("repeat_value")?.let { decodeRecordNodeList(it) },
    )

  private fun decodeTypedValueList(node: TextProtoMessage): TypedValueList =
    TypedValueList(values = node.getMessages("values").map { decodeTypedValue(it) })

  private fun decodeRecordNodeList(node: TextProtoMessage): RecordNodeList =
    RecordNodeList(nodes = node.getMessages("nodes").map { decodeRecordNode(it) })

  private fun decodeTypedValue(node: TextProtoMessage): TypedValue =
    TypedValue(
      string_value = node.getStringOrNull("string_value"),
      int32_value = node.getIntOrNull("int32_value"),
      int64_value = node.getLongOrNull("int64_value"),
      double_value = node.getDoubleOrNull("double_value"),
      bool_value = node.getBooleanOrNull("bool_value"),
      date_value = node.getMessageOrNull("date_value")?.let { decodeDate(it) },
      time_value = node.getMessageOrNull("time_value")?.let { decodeTimeOfDay(it) },
      timestamp_value = node.getMessageOrNull("timestamp_value")?.let { decodeInstant(it) },
      geopoint_value = node.getMessageOrNull("geopoint_value")?.let { decodeGeoPoint(it) },
      binary_value = node.getStringOrNull("binary_value")?.encodeUtf8(),
      geotrace_value = node.getMessageOrNull("geotrace_value")?.let { decodeGeoTrace(it) },
      geoshape_value = node.getMessageOrNull("geoshape_value")?.let { decodeGeoShape(it) },
    )

  private fun decodeDate(node: TextProtoMessage): Date =
    Date(year = node.getInt("year"), month = node.getInt("month"), day = node.getInt("day"))

  private fun decodeTimeOfDay(node: TextProtoMessage): TimeOfDay =
    TimeOfDay(
      hours = node.getInt("hours"),
      minutes = node.getInt("minutes"),
      seconds = node.getInt("seconds"),
      nanos = node.getInt("nanos"),
    )

  private fun decodeInstant(node: TextProtoMessage): Instant =
    ofEpochSecond(node.getLong("seconds"), node.getLong("nanos"))

  private fun decodeGeoPoint(node: TextProtoMessage): GeoPoint =
    GeoPoint(
      latitude = node.getDouble("latitude"),
      longitude = node.getDouble("longitude"),
      altitude_meters = node.getDouble("altitude_meters"),
      accuracy_meters = node.getDouble("accuracy_meters"),
    )

  private fun decodeGeoTrace(node: TextProtoMessage): GeoTrace =
    GeoTrace(points = node.getMessages("points").map { decodeGeoPoint(it) })

  private fun decodeGeoShape(node: TextProtoMessage): GeoShape =
    GeoShape(points = node.getMessages("points").map { decodeGeoPoint(it) })

  private inline fun <reified E : Enum<E>> parseEnum(name: String?, default: E): E {
    if (name == null) return default
    return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
  }

  // ===========================================================================
  // Message Builder DSL
  // ===========================================================================

  private class MessageBuilder {
    val fields = mutableListOf<TextProtoField>()

    fun addString(name: String, value: String) {
      if (value.isNotEmpty()) {
        fields.add(TextProtoField(name, TextProtoValue.StringVal(value)))
      }
    }

    fun addStringAlways(name: String, value: String) {
      fields.add(TextProtoField(name, TextProtoValue.StringVal(value)))
    }

    fun addInt(name: String, value: Int) {
      if (value != 0) {
        fields.add(TextProtoField(name, TextProtoValue.NumberVal(value.toString())))
      }
    }

    fun addIntAlways(name: String, value: Int) {
      fields.add(TextProtoField(name, TextProtoValue.NumberVal(value.toString())))
    }

    fun addLong(name: String, value: Long) {
      if (value != 0L) {
        fields.add(TextProtoField(name, TextProtoValue.NumberVal(value.toString())))
      }
    }

    fun addLongAlways(name: String, value: Long) {
      fields.add(TextProtoField(name, TextProtoValue.NumberVal(value.toString())))
    }

    fun addDouble(name: String, value: Double) {
      if (value != 0.0) {
        fields.add(
          TextProtoField(name, TextProtoValue.NumberVal(TextProtoWriter.formatDouble(value)))
        )
      }
    }

    fun addDoubleAlways(name: String, value: Double) {
      fields.add(
        TextProtoField(name, TextProtoValue.NumberVal(TextProtoWriter.formatDouble(value)))
      )
    }

    fun addBoolean(name: String, value: Boolean) {
      if (value) {
        fields.add(TextProtoField(name, TextProtoValue.IdentifierVal("true")))
      }
    }

    fun addBooleanAlways(name: String, value: Boolean) {
      fields.add(TextProtoField(name, TextProtoValue.IdentifierVal(if (value) "true" else "false")))
    }

    fun addEnum(name: String, enumName: String) {
      fields.add(TextProtoField(name, TextProtoValue.IdentifierVal(enumName)))
    }

    fun addMessage(name: String, msg: TextProtoMessage) {
      fields.add(TextProtoField(name, TextProtoValue.MessageVal(msg)))
    }
  }

  private inline fun buildMessage(block: MessageBuilder.() -> Unit): TextProtoMessage =
    TextProtoMessage(MessageBuilder().apply(block).fields)
}
