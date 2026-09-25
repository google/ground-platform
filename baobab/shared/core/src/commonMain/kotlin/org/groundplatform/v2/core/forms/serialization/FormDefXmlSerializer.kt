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

import com.squareup.wire.Instant
import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GroupDef
import groundplatform.v2.forms.IntentConfig
import groundplatform.v2.forms.ItemsetDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.SubmissionConfig
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.ViewComponent
import org.groundplatform.v2.core.forms.serialization.xml.XmlElement
import org.groundplatform.v2.core.forms.serialization.xml.XmlNode
import org.groundplatform.v2.core.forms.serialization.xml.XmlParser
import org.groundplatform.v2.core.forms.serialization.xml.XmlText
import org.groundplatform.v2.core.forms.serialization.xml.XmlWriter

/** Serializes [FormDef] protocol buffer models into XForms XML documents. */
internal object FormDefXmlSerializer {

  private data class CivilDate(val year: Int, val month: Int, val day: Int)

  /** Serializes [formDef] into a complete XForms XML document string. */
  fun serialize(formDef: FormDef, prettyPrint: Boolean = true): String {
    val rootElement = serializeToElement(formDef)
    return XmlWriter(prettyPrint = prettyPrint, includeDeclaration = true).writeElement(rootElement)
  }

  internal fun serializeToElement(formDef: FormDef): XmlElement {
    val model = formDef.model
    val schema = model?.primary_instance?.record_schema
    val rootName = schema?.name?.takeIf { it.isNotEmpty() } ?: "data"
    val hasEntities =
      (model?.entities?.isNotEmpty() == true) ||
        (model?.bindings?.any { it.entity_saveto.isNotEmpty() } == true)

    val rootAttrs = buildMap {
      put("xmlns", "http://www.w3.org/2002/xforms")
      put("xmlns:h", "http://www.w3.org/1999/xhtml")
      put("xmlns:jr", "http://openrosa.org/javarosa")
      put("xmlns:orx", "http://openrosa.org/xforms")
      put("xmlns:odk", "http://www.opendatakit.org/xforms")
      put("xmlns:ev", "http://www.w3.org/2001/xml-events")
      if (hasEntities) {
        put("xmlns:entities", "http://www.opendatakit.org/xforms/entities")
      }
    }

    val headEl = buildHeadElement(formDef, rootName)
    val bodyEl = buildBodyElement(formDef, rootName)

    return XmlElement(name = "h:html", attributes = rootAttrs, children = listOf(headEl, bodyEl))
  }

  private fun buildHeadElement(formDef: FormDef, rootName: String): XmlElement {
    val titleEl =
      XmlElement(
        name = "h:title",
        children = if (formDef.title.isNotEmpty()) listOf(XmlText(formDef.title)) else emptyList(),
      )
    val modelEl = buildModelElement(formDef, rootName)
    return XmlElement(name = "h:head", children = listOf(titleEl, modelEl))
  }

  private fun buildModelElement(formDef: FormDef, rootName: String): XmlElement {
    val model = formDef.model
    val modelChildren = buildList {
      // 1. Primary instance
      add(buildPrimaryInstanceElement(formDef, rootName))

      // 2. Secondary instances
      for (sec in model?.secondary_instances ?: emptyList()) {
        add(buildSecondaryInstanceElement(sec))
      }

      // 3. Translations (<itext>)
      model?.translations?.let { catalog ->
        if (catalog.languages.isNotEmpty()) {
          add(buildItextElement(catalog, formDef.default_language))
        }
      }

      // 4. Submission configuration (<submission>)
      model?.submission?.let { sub -> add(buildSubmissionElement(sub)) }

      // 5. Bindings (<bind>)
      for (binding in model?.bindings ?: emptyList()) {
        add(buildBindElement(binding, rootName))
      }

      // 6. Model actions (<setvalue>, <odk:setgeopoint>)
      for (action in model?.actions ?: emptyList()) {
        add(buildActionElement(action, rootName))
      }
    }

    return XmlElement(
      name = "model",
      attributes = mapOf("odk:xforms-version" to "1.0.0"),
      children = modelChildren,
    )
  }

  private fun buildPrimaryInstanceElement(formDef: FormDef, rootName: String): XmlElement {
    val model = formDef.model
    val schema = model?.primary_instance?.record_schema
    val defaults = model?.primary_instance?.default_values

    val rootAttrs = buildMap {
      put("id", formDef.form_id.ifEmpty { rootName })
      if (formDef.version.isNotEmpty()) {
        put("version", formDef.version)
      }
      if (schema != null) {
        if (schema.sms_prefix.isNotEmpty()) {
          put("jr:prefix", schema.sms_prefix)
        }
        if (schema.sms_delimiter.isNotEmpty()) {
          put("jr:delimiter", schema.sms_delimiter)
        }
      }
    }

    val rootChildren = buildList {
      for (fieldDef in schema?.fields ?: emptyList()) {
        val defaultVal = defaults?.fields?.get(fieldDef.name)
        addAll(buildSchemaFieldElements(fieldDef, defaultVal))
      }

      // Build <meta> block if any meta bindings or entities exist
      val metaBindings =
        (model?.bindings ?: emptyList()).filter {
          it.field_path.startsWith("meta/") || it.field_path.startsWith("orx:meta/")
        }
      val entities = model?.entities ?: emptyList()
      if (metaBindings.isNotEmpty() || entities.isNotEmpty()) {
        add(buildMetaElement(metaBindings, entities))
      }
    }

    val dataRootEl = XmlElement(name = rootName, attributes = rootAttrs, children = rootChildren)
    return XmlElement(name = "instance", children = listOf(dataRootEl))
  }

  private fun buildSchemaFieldElements(
    fieldDef: FieldDefinition,
    defaultVal: FieldValue?,
  ): List<XmlElement> {
    if (fieldDef.is_repeated && fieldDef.type == DataType.TYPE_MESSAGE) {
      return buildList {
        // Emit jr:template="" node
        val templateChildren =
          fieldDef.fields.flatMap { childDef -> buildSchemaFieldElements(childDef, null) }
        add(
          XmlElement(
            name = fieldDef.name,
            attributes = mapOf("jr:template" to ""),
            children = templateChildren,
          )
        )
        // Emit any pre-populated default repeat nodes
        val repeatNodes = defaultVal?.repeat_value?.nodes ?: emptyList()
        for (node in repeatNodes) {
          val nodeChildren =
            fieldDef.fields.flatMap { childDef ->
              buildSchemaFieldElements(childDef, node.fields[childDef.name])
            }
          add(XmlElement(name = fieldDef.name, children = nodeChildren))
        }
      }
    } else if (fieldDef.type == DataType.TYPE_MESSAGE) {
      val nodeVal = defaultVal?.node_value
      val children =
        fieldDef.fields.flatMap { childDef ->
          buildSchemaFieldElements(childDef, nodeVal?.fields?.get(childDef.name))
        }
      return listOf(XmlElement(name = fieldDef.name, children = children))
    } else {
      val textStr = formatFieldValue(defaultVal)
      val children = if (textStr.isNotEmpty()) listOf(XmlText(textStr)) else emptyList()
      return listOf(XmlElement(name = fieldDef.name, children = children))
    }
  }

  private fun buildMetaElement(
    metaBindings: List<FieldBinding>,
    entities: List<EntityDeclaration>,
  ): XmlElement {
    val metaChildren = mutableListOf<XmlNode>()
    val addedTags = mutableSetOf<String>()

    for (binding in metaBindings) {
      val subName = binding.field_path.substringAfter("meta/")
      if (subName.isNotEmpty() && subName !in addedTags && !subName.startsWith("entity")) {
        addedTags.add(subName)
        metaChildren.add(XmlElement(name = subName))
      }
    }
    if ("instanceID" !in addedTags) {
      metaChildren.add(0, XmlElement(name = "instanceID"))
    }

    for (entity in entities) {
      val entityAttrs = buildMap {
        if (entity.dataset.isNotEmpty()) put("dataset", entity.dataset)
        if (entity.entity_id_expression.isNotEmpty()) put("id", entity.entity_id_expression)
        if (entity.create_condition.isNotEmpty()) put("create", entity.create_condition)
        if (entity.update_condition.isNotEmpty()) put("update", entity.update_condition)
        entity.sync_metadata?.let { sync ->
          if (sync.base_version_expression.isNotEmpty()) {
            put("baseVersion", sync.base_version_expression)
          }
          if (sync.trunk_version_expression.isNotEmpty()) {
            put("trunkVersion", sync.trunk_version_expression)
          }
          if (sync.branch_id_expression.isNotEmpty()) {
            put("branchId", sync.branch_id_expression)
          }
        }
      }
      val entityChildren =
        if (entity.label_expression.isNotEmpty()) {
          listOf(XmlElement(name = "label", children = listOf(XmlText(entity.label_expression))))
        } else {
          emptyList()
        }
      metaChildren.add(
        XmlElement(name = "entity", attributes = entityAttrs, children = entityChildren)
      )
    }

    return XmlElement(name = "meta", children = metaChildren)
  }

  private fun buildSecondaryInstanceElement(sec: SecondaryInstance): XmlElement {
    val attrs = buildMap {
      if (sec.id.isNotEmpty()) put("id", sec.id)
      if (sec.uri.isNotEmpty()) put("src", sec.uri)
    }
    val children =
      if (sec.inline_data.isNotEmpty()) {
        try {
          listOf(XmlParser.parse(sec.inline_data))
        } catch (_: Exception) {
          emptyList()
        }
      } else {
        emptyList()
      }
    return XmlElement(name = "instance", attributes = attrs, children = children)
  }

  private fun buildItextElement(catalog: TranslationCatalog, defaultLang: String): XmlElement {
    val transElements =
      catalog.languages.map { lang ->
        val attrs = buildMap {
          put("lang", lang.language)
          if (lang.is_default || (defaultLang.isNotEmpty() && lang.language == defaultLang)) {
            put("default", "true()")
          }
        }

        val textElements =
          lang.strings.map { (textId, locStr) ->
            val valElements = buildList {
              val hasNoOtherForms =
                locStr.short_value.isEmpty() &&
                  locStr.guidance_value.isEmpty() &&
                  locStr.media == null
              if (locStr.value_.isNotEmpty() || hasNoOtherForms) {
                add(valueElement(locStr.value_))
              }
              if (locStr.short_value.isNotEmpty()) {
                add(valueElement(locStr.short_value, form = "short"))
              }
              if (locStr.guidance_value.isNotEmpty()) {
                add(valueElement(locStr.guidance_value, form = "guidance"))
              }
              locStr.media?.let { media ->
                if (media.image_uri.isNotEmpty()) add(valueElement(media.image_uri, form = "image"))
                if (media.big_image_uri.isNotEmpty()) {
                  add(valueElement(media.big_image_uri, form = "big-image"))
                }
                if (media.audio_uri.isNotEmpty()) add(valueElement(media.audio_uri, form = "audio"))
                if (media.video_uri.isNotEmpty()) add(valueElement(media.video_uri, form = "video"))
              }
            }
            XmlElement(name = "text", attributes = mapOf("id" to textId), children = valElements)
          }

        XmlElement(name = "translation", attributes = attrs, children = textElements)
      }
    return XmlElement(name = "itext", children = transElements)
  }

  private fun valueElement(text: String, form: String? = null): XmlElement =
    XmlElement(
      name = "value",
      attributes = if (form != null) mapOf("form" to form) else emptyMap(),
      children = listOf(XmlText(text)),
    )

  private fun buildSubmissionElement(sub: SubmissionConfig): XmlElement {
    val attrs = buildMap {
      if (sub.action_url.isNotEmpty()) put("action", sub.action_url)
      if (sub.method.isNotEmpty()) put("method", sub.method)
      if (sub.base64_rsa_public_key.isNotEmpty()) {
        put("base64RsaPublicKey", sub.base64_rsa_public_key)
      }
      if (sub.auto_send) put("orx:auto-send", "true")
      if (sub.auto_delete) put("orx:auto-delete", "true")
      if (sub.client_editable) put("odk:client-editable", "true")
    }
    return XmlElement(name = "submission", attributes = attrs)
  }

  private fun buildBindElement(binding: FieldBinding, rootName: String): XmlElement {
    val cleanPath = binding.field_path.removePrefix("/")
    val attrs = buildMap {
      put("nodeset", "/$rootName/$cleanPath")

      val typeStr = mapDataTypeToXFormsType(binding.type)
      if (typeStr.isNotEmpty()) put("type", typeStr)

      if (binding.read_only) put("readonly", "true()")
      if (binding.required_expression.isNotEmpty()) put("required", binding.required_expression)
      if (binding.relevant_expression.isNotEmpty()) put("relevant", binding.relevant_expression)
      if (binding.constraint_expression.isNotEmpty()) {
        put("constraint", binding.constraint_expression)
      }
      if (binding.constraint_message.isNotEmpty()) {
        put("jr:constraintMsg", binding.constraint_message)
      }
      if (binding.required_message.isNotEmpty()) put("jr:requiredMsg", binding.required_message)
      if (binding.calculate_expression.isNotEmpty()) put("calculate", binding.calculate_expression)
      if (binding.preload != PreloadType.PRELOAD_UNSPECIFIED) {
        put("jr:preload", mapPreloadTypeToString(binding.preload))
      }
      if (binding.preload_param.isNotEmpty()) put("jr:preloadParams", binding.preload_param)
      if (binding.max_pixels > 0) put("orx:max-pixels", binding.max_pixels.toString())
      if (binding.entity_saveto.isNotEmpty()) put("entities:saveto", binding.entity_saveto)
      if (binding.sms_tag.isNotEmpty()) put("jr:smsTag", binding.sms_tag)
    }

    return XmlElement(name = "bind", attributes = attrs)
  }

  private fun buildActionElement(action: ActionDef, rootName: String): XmlElement {
    val eventStr = action.events.mapNotNull { mapEventTypeToString(it) }.joinToString(" ")
    val cleanTarget = action.target_field.removePrefix("/")
    val attrs = buildMap {
      if (eventStr.isNotEmpty()) put("event", eventStr)
      put("ref", "/$rootName/$cleanTarget")
      if (action.type != ActionType.ACTION_SET_GEOPOINT && action.value_expression.isNotEmpty()) {
        put("value", action.value_expression)
      }
    }

    return when (action.type) {
      ActionType.ACTION_SET_GEOPOINT -> XmlElement(name = "odk:setgeopoint", attributes = attrs)
      else -> {
        val children =
          if (action.value_expression.isEmpty() && action.literal_value != null) {
            listOf(XmlText(formatTypedValue(action.literal_value)))
          } else {
            emptyList()
          }
        XmlElement(name = "setvalue", attributes = attrs, children = children)
      }
    }
  }

  private fun buildBodyElement(formDef: FormDef, rootName: String): XmlElement {
    val components = formDef.view?.components ?: emptyList()
    val bodyChildren = components.mapNotNull { comp ->
      buildComponentElement(comp, rootName, enclosingPath = "")
    }
    return XmlElement(name = "h:body", children = bodyChildren)
  }

  private fun buildComponentElement(
    component: ViewComponent,
    rootName: String,
    enclosingPath: String,
  ): XmlElement? =
    when {
      component.control != null -> buildControlElement(component.control, rootName, enclosingPath)
      component.group != null -> buildGroupElement(component.group, rootName, enclosingPath)
      component.repeat != null -> buildRepeatElement(component.repeat, rootName, enclosingPath)
      else -> null
    }

  private fun buildGroupElement(
    group: GroupDef,
    rootName: String,
    enclosingPath: String,
  ): XmlElement {
    val fullPath = resolveFullPath(group.field_ref, enclosingPath)
    val attrs = buildMap {
      if (fullPath.isNotEmpty()) put("ref", "/$rootName/$fullPath")
      if (group.appearance.isNotEmpty()) put("appearance", group.appearance)
      group.intent?.let { intent -> put("intent", formatIntentString(intent)) }
    }

    val nextEnclosing = if (fullPath.isNotEmpty()) fullPath else enclosingPath
    val children = buildList {
      group.label?.let { add(buildLabelElement("label", it)) }
      for (childComp in group.components) {
        buildComponentElement(childComp, rootName, nextEnclosing)?.let { add(it) }
      }
    }

    return XmlElement(name = "group", attributes = attrs, children = children)
  }

  private fun buildRepeatElement(
    repeat: RepeatDef,
    rootName: String,
    enclosingPath: String,
  ): XmlElement {
    val fullPath = resolveFullPath(repeat.field_ref, enclosingPath)
    val nodesetPath = "/$rootName/$fullPath"

    val repeatAttrs = buildMap {
      put("nodeset", nodesetPath)
      if (repeat.count_expression.isNotEmpty()) put("jr:count", repeat.count_expression)
      if (repeat.no_add_remove) put("jr:noAddRemove", "true()")
      if (repeat.appearance.isNotEmpty() && repeat.label == null) {
        put("appearance", repeat.appearance)
      }
    }

    val nextEnclosing = if (fullPath.isNotEmpty()) fullPath else enclosingPath
    val repeatChildren = buildList {
      for (childComp in repeat.components) {
        buildComponentElement(childComp, rootName, nextEnclosing)?.let { add(it) }
      }
    }

    val repeatEl = XmlElement(name = "repeat", attributes = repeatAttrs, children = repeatChildren)

    // Wrap in <group ref="..."> if label is present (standard XForms repeat pattern)
    return if (repeat.label != null) {
      val wrapperAttrs = buildMap {
        put("ref", nodesetPath)
        if (repeat.appearance.isNotEmpty()) put("appearance", repeat.appearance)
      }
      XmlElement(
        name = "group",
        attributes = wrapperAttrs,
        children = listOf(buildLabelElement("label", repeat.label), repeatEl),
      )
    } else {
      repeatEl
    }
  }

  private fun buildControlElement(
    control: ControlDef,
    rootName: String,
    enclosingPath: String,
  ): XmlElement {
    val tagName =
      when (control.type) {
        ControlType.CONTROL_INPUT -> "input"
        ControlType.CONTROL_SELECT_ONE -> "select1"
        ControlType.CONTROL_SELECT_MULTIPLE -> "select"
        ControlType.CONTROL_RANK -> "odk:rank"
        ControlType.CONTROL_RANGE -> "range"
        ControlType.CONTROL_UPLOAD -> "upload"
        ControlType.CONTROL_TRIGGER -> "trigger"
        else -> "input"
      }

    val fullPath = resolveFullPath(control.field_ref, enclosingPath)
    val attrs = buildMap {
      if (fullPath.isNotEmpty()) put("ref", "/$rootName/$fullPath")
      if (control.appearance.isNotEmpty()) put("appearance", control.appearance)
      if (control.media_type.isNotEmpty()) put("mediatype", control.media_type)
      control.range_config?.let { rc ->
        put("start", formatDouble(rc.start))
        put("end", formatDouble(rc.end))
        put("step", formatDouble(rc.step))
        if (rc.tick_interval > 0.0) {
          put("odk:tick-interval", formatDouble(rc.tick_interval))
        }
      }
      control.geo_config?.let { gc ->
        if (gc.accuracy_threshold_meters > 0.0) {
          put("accuracyThreshold", formatDouble(gc.accuracy_threshold_meters))
        }
        if (gc.warning_threshold_meters > 0.0) {
          put("unacceptableAccuracyThreshold", formatDouble(gc.warning_threshold_meters))
        }
        if (gc.allow_mock_accuracy) {
          put("allowMockAccuracy", "true")
        }
      }
      control.intent?.let { intent -> put("intent", formatIntentString(intent)) }
    }

    val children = buildList {
      control.label?.let { add(buildLabelElement("label", it)) }
      control.hint?.let { add(buildLabelElement("hint", it)) }

      for (choice in control.choices) {
        val itemChildren = buildList {
          choice.label?.let { add(buildLabelElement("label", it)) }
          add(XmlElement(name = "value", children = listOf(XmlText(choice.value_))))
        }
        add(XmlElement(name = "item", children = itemChildren))
      }

      control.itemset?.let { itemset -> add(buildItemsetElement(itemset)) }

      for (action in control.actions) {
        add(buildActionElement(action, rootName))
      }
    }

    return XmlElement(name = tagName, attributes = attrs, children = children)
  }

  private fun buildLabelElement(tagName: String, labelDef: LabelDef): XmlElement {
    val attrs =
      if (labelDef.text_id.isNotEmpty()) {
        mapOf("ref" to "jr:itext('${labelDef.text_id}')")
      } else {
        emptyMap()
      }

    val children = buildList {
      if (labelDef.outputs.isEmpty()) {
        if (labelDef.text.isNotEmpty()) {
          add(XmlText(labelDef.text))
        }
      } else {
        // Reconstruct mixed content by replacing placeholders with <output value="..."/>
        var remaining = labelDef.text
        for (output in labelDef.outputs) {
          val idx = remaining.indexOf(output.placeholder_id)
          if (idx != -1) {
            val before = remaining.substring(0, idx)
            if (before.isNotEmpty()) {
              add(XmlText(before))
            }
            add(XmlElement(name = "output", attributes = mapOf("value" to output.value_expression)))
            remaining = remaining.substring(idx + output.placeholder_id.length)
          }
        }
        if (remaining.isNotEmpty()) {
          add(XmlText(remaining))
        }
      }
    }

    return XmlElement(name = tagName, attributes = attrs, children = children)
  }

  private fun buildItemsetElement(itemset: ItemsetDef): XmlElement {
    val base = buildString {
      append("instance('").append(itemset.instance_id).append("')/root/item")
      if (itemset.nodeset_filter.isNotEmpty()) {
        append('[').append(itemset.nodeset_filter).append(']')
      }
    }
    val nodeset =
      if (itemset.randomize) {
        if (itemset.random_seed_expression.isNotEmpty()) {
          "randomize($base, ${itemset.random_seed_expression})"
        } else {
          "randomize($base)"
        }
      } else {
        base
      }

    val children =
      listOf(
        XmlElement(name = "value", attributes = mapOf("ref" to itemset.value_ref)),
        XmlElement(name = "label", attributes = mapOf("ref" to itemset.label_ref)),
      )
    return XmlElement(
      name = "itemset",
      attributes = mapOf("nodeset" to nodeset),
      children = children,
    )
  }

  private fun formatIntentString(intent: IntentConfig): String {
    if (intent.parameters.isEmpty()) return intent.intent_uri
    val paramsJoined = intent.parameters.entries.joinToString(", ") { (k, v) -> "$k=$v" }
    return "${intent.intent_uri}($paramsJoined)"
  }

  private fun resolveFullPath(fieldRef: String, enclosingPath: String): String {
    val clean = fieldRef.trim().removePrefix("/")
    if (clean.isEmpty()) return enclosingPath
    if (enclosingPath.isEmpty()) return clean
    if (clean.startsWith("$enclosingPath/")) return clean
    return "$enclosingPath/$clean"
  }

  internal fun mapDataTypeToXFormsType(dataType: DataType): String =
    when (dataType) {
      DataType.TYPE_STRING -> "string"
      DataType.TYPE_INT32 -> "int"
      DataType.TYPE_INT64 -> "long"
      DataType.TYPE_DOUBLE -> "decimal"
      DataType.TYPE_BOOLEAN -> "boolean"
      DataType.TYPE_DATE -> "date"
      DataType.TYPE_TIME -> "time"
      DataType.TYPE_DATETIME -> "dateTime"
      DataType.TYPE_GEOPOINT -> "geopoint"
      DataType.TYPE_GEOTRACE -> "geotrace"
      DataType.TYPE_GEOSHAPE -> "geoshape"
      DataType.TYPE_BINARY -> "binary"
      DataType.TYPE_SELECT_ONE -> "string"
      DataType.TYPE_SELECT_MULTIPLE -> "string"
      else -> ""
    }

  private fun mapPreloadTypeToString(preload: PreloadType): String =
    when (preload) {
      PreloadType.PRELOAD_TIMESTAMP -> "timestamp"
      PreloadType.PRELOAD_DATE -> "date"
      PreloadType.PRELOAD_TIME -> "time"
      PreloadType.PRELOAD_UID -> "uid"
      PreloadType.PRELOAD_PROPERTY -> "property"
      PreloadType.PRELOAD_CONTEXT -> "context"
      else -> ""
    }

  private fun mapEventTypeToString(eventType: EventType): String? =
    when (eventType) {
      EventType.EVENT_INSTANCE_FIRST_LOAD -> "odk-instance-first-load"
      EventType.EVENT_INSTANCE_LOAD -> "odk-instance-load"
      EventType.EVENT_VALUE_CHANGED -> "xforms-value-changed"
      EventType.EVENT_REPEAT_INSERT -> "odk-new-repeat"
      else -> null
    }

  internal fun formatFieldValue(fieldValue: FieldValue?): String =
    when {
      fieldValue == null -> ""
      fieldValue.scalar_value != null -> formatTypedValue(fieldValue.scalar_value)
      fieldValue.list_value != null ->
        fieldValue.list_value.values.joinToString(" ") { formatTypedValue(it) }
      else -> ""
    }

  internal fun formatTypedValue(typedValue: TypedValue): String =
    when {
      typedValue.string_value != null -> typedValue.string_value
      typedValue.int32_value != null -> typedValue.int32_value.toString()
      typedValue.int64_value != null -> typedValue.int64_value.toString()
      typedValue.double_value != null -> formatDouble(typedValue.double_value)
      typedValue.bool_value != null -> if (typedValue.bool_value) "true" else "false"
      typedValue.date_value != null -> {
        val d = typedValue.date_value
        "${d.year.toString().padStart(4, '0')}-${d.month.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
      }
      typedValue.time_value != null -> {
        val t = typedValue.time_value
        val base =
          "${t.hours.toString().padStart(2, '0')}:${t.minutes.toString().padStart(2, '0')}:${t.seconds.toString().padStart(2, '0')}"
        if (t.nanos > 0) {
          val ms = (t.nanos / 1_000_000).toString().padStart(3, '0')
          "$base.$ms"
        } else {
          base
        }
      }
      typedValue.timestamp_value != null -> formatInstantIso8601(typedValue.timestamp_value)
      typedValue.geopoint_value != null -> formatGeoPoint(typedValue.geopoint_value)
      typedValue.geotrace_value != null ->
        typedValue.geotrace_value.points.joinToString("; ") { formatGeoPoint(it) }
      typedValue.geoshape_value != null ->
        typedValue.geoshape_value.points.joinToString("; ") { formatGeoPoint(it) }
      else -> ""
    }

  internal fun formatGeoPoint(gp: GeoPoint): String =
    "${formatDouble(gp.latitude)} ${formatDouble(gp.longitude)} ${formatDouble(gp.altitude_meters)} ${formatDouble(gp.accuracy_meters)}"

  internal fun formatDouble(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) {
      asLong.toString()
    } else {
      value.toString()
    }
  }

  internal fun formatInstantIso8601(instant: Instant): String {
    val epochSec = instant.getEpochSecond()
    val nanos = instant.getNano()
    val days = epochSec.floorDiv(86400L)
    val secsOfDay = epochSec.mod(86400L)
    val civilDate = civilFromDays(days)
    val hours = secsOfDay / 3600L
    val minutes = (secsOfDay % 3600L) / 60L
    val seconds = secsOfDay % 60L

    val dateStr =
      "${civilDate.year.toString().padStart(4, '0')}-${civilDate.month.toString().padStart(2, '0')}-${civilDate.day.toString().padStart(2, '0')}"
    val timeStr =
      "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    return if (nanos > 0) {
      val ms = (nanos / 1_000_000).toString().padStart(3, '0')
      "${dateStr}T${timeStr}.${ms}Z"
    } else {
      "${dateStr}T${timeStr}Z"
    }
  }

  private fun civilFromDays(zDays: Long): CivilDate {
    val z = zDays + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + (if (mp < 10L) 3L else -9L)
    val finalY = y + (if (m <= 2L) 1L else 0L)
    return CivilDate(year = finalY.toInt(), month = m.toInt(), day = d.toInt())
  }
}
