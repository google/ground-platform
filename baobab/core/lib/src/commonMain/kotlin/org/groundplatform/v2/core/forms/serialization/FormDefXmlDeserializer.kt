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
import org.groundplatform.v2.core.forms.serialization.xml.XmlElement
import org.groundplatform.v2.core.forms.serialization.xml.XmlParser
import org.groundplatform.v2.core.forms.serialization.xml.XmlText

/** Deserializes ODK XForms XML form definitions into [FormDef] protocol buffer models. */
internal object FormDefXmlDeserializer {

  private val WHITESPACE_REGEX = Regex("\\s+")

  private data class ParsedInstanceTree(
    val schemaFields: List<FieldDefinition>,
    val defaultNode: RecordNode,
  )

  /** Deserializes an ODK XForms XML document string into a [FormDef] message. */
  fun deserialize(xml: String): FormDef {
    val root = XmlParser.parse(xml)
    val head = root.firstChildNamed("head")
    val body = root.firstChildNamed("body")

    val title = head?.firstChildNamed("title")?.textContent?.trim() ?: ""
    val modelEl = head?.firstChildNamed("model")

    // Identify primary instance and secondary instances
    val instances = modelEl?.childrenNamed("instance") ?: emptyList()
    val primaryInstanceEl =
      instances.firstOrNull { it.attr("src") == null && it.childElements.isNotEmpty() }
        ?: instances.firstOrNull()
    val primaryRootEl = primaryInstanceEl?.childElements?.firstOrNull()

    val rootName = primaryRootEl?.localName ?: "data"
    val formId = primaryRootEl?.attr("id") ?: rootName
    val version = primaryRootEl?.attr("version") ?: primaryRootEl?.attr("orx:version") ?: ""
    val smsPrefix = primaryRootEl?.attr("prefix") ?: primaryRootEl?.attr("jr:prefix") ?: ""
    val smsDelimiter = primaryRootEl?.attr("delimiter") ?: primaryRootEl?.attr("jr:delimiter") ?: ""

    // Secondary instances
    val secondaryInstances =
      instances.filter { it !== primaryInstanceEl }.map { parseSecondaryInstance(it) }

    // Translations (<itext>)
    val itextEl = modelEl?.firstChildNamed("itext")
    val translationCatalog = itextEl?.let { parseTranslations(it) }
    val defaultLanguage =
      translationCatalog?.languages?.firstOrNull { it.is_default }?.language
        ?: translationCatalog?.languages?.firstOrNull()?.language
        ?: ""

    // Submission config (<submission>)
    val submissionEl = modelEl?.firstChildNamed("submission")
    val submissionConfig = submissionEl?.let { parseSubmissionConfig(it) }

    // Bindings (<bind>)
    val bindElements = modelEl?.childrenNamed("bind") ?: emptyList()
    val rawBindings = bindElements.map { parseFieldBinding(it, rootName) }

    // Model-level actions (<setvalue>, <odk:setgeopoint>)
    val modelActions =
      modelEl
        ?.childElements
        ?.filter { it.localName == "setvalue" || it.localName == "setgeopoint" }
        ?.map { parseActionDef(it, rootName) } ?: emptyList()

    // Parse view hierarchy (<h:body>) first so we can infer Repeat/Select types and control
    // bindings
    val repeatPaths = mutableSetOf<String>()
    val selectOnePaths = mutableSetOf<String>()
    val selectMultiplePaths = mutableSetOf<String>()

    val viewDef =
      body?.let {
        parseViewDef(
          bodyEl = it,
          rootName = rootName,
          repeatPaths = repeatPaths,
          selectOnePaths = selectOnePaths,
          selectMultiplePaths = selectMultiplePaths,
        )
      } ?: ViewDef()

    // Upgrade binding types based on UI controls (e.g. select1 -> TYPE_SELECT_ONE, select ->
    // TYPE_SELECT_MULTIPLE)
    val bindings = rawBindings.map { binding ->
      val updatedType =
        when {
          binding.field_path in selectOnePaths &&
            (binding.type == DataType.DATA_TYPE_UNSPECIFIED ||
              binding.type == DataType.TYPE_STRING) -> DataType.TYPE_SELECT_ONE
          binding.field_path in selectMultiplePaths &&
            (binding.type == DataType.DATA_TYPE_UNSPECIFIED ||
              binding.type == DataType.TYPE_STRING) -> DataType.TYPE_SELECT_MULTIPLE
          else -> binding.type
        }
      binding.copy(type = updatedType)
    }

    val bindingTypeMap = bindings.associate { it.field_path to it.type }

    // Extract entities from <meta><entity .../></meta> and binding saveto attributes
    val metaEl = primaryRootEl?.firstChildNamed("meta")
    val entities = parseEntityDeclarations(metaEl, bindings)

    // Build RecordSchema and default values from primary instance tree + bindings
    val parsedTree =
      if (primaryRootEl != null) {
        parsePrimaryInstanceTree(
          parentEl = primaryRootEl,
          parentPath = "",
          bindingTypeMap = bindingTypeMap,
          repeatPaths = repeatPaths,
        )
      } else {
        ParsedInstanceTree(schemaFields = emptyList(), defaultNode = RecordNode())
      }

    val recordSchema =
      RecordSchema(
        name = rootName,
        title = title,
        fields = parsedTree.schemaFields,
        sms_prefix = smsPrefix,
        sms_delimiter = smsDelimiter,
      )

    val primaryInstance =
      PrimaryInstance(
        record_schema = recordSchema,
        default_values =
          if (parsedTree.defaultNode.fields.isNotEmpty()) parsedTree.defaultNode else null,
      )

    val modelDef =
      ModelDef(
        primary_instance = primaryInstance,
        secondary_instances = secondaryInstances,
        bindings = bindings,
        translations = translationCatalog,
        actions = modelActions,
        entities = entities,
        submission = submissionConfig,
      )

    return FormDef(
      form_id = formId,
      title = title,
      version = version,
      default_language = defaultLanguage,
      model = modelDef,
      view = viewDef,
    )
  }

  private fun parseSecondaryInstance(el: XmlElement): SecondaryInstance {
    val id = el.attr("id") ?: ""
    val src = el.attr("src") ?: ""
    val inlineData =
      if (src.isEmpty() && el.childElements.isNotEmpty()) {
        el.childElements.first().toXmlString(prettyPrint = false)
      } else {
        ""
      }
    return SecondaryInstance(id = id, uri = src, inline_data = inlineData)
  }

  private fun parseTranslations(itextEl: XmlElement): TranslationCatalog {
    val languages =
      itextEl.childrenNamed("translation").map { transEl ->
        val lang = transEl.attr("lang") ?: ""
        val isDefault =
          transEl.attr("default")?.let { it == "true()" || it == "true" || it == "" } ?: false
        val strings = buildMap {
          for (textEl in transEl.childrenNamed("text")) {
            val textId = textEl.attr("id") ?: continue
            val valuesByForm =
              textEl.childrenNamed("value").associate { valEl ->
                (valEl.attr("form") ?: "") to valEl.textContent.trim()
              }
            val defaultVal = valuesByForm[""] ?: ""
            val shortVal = valuesByForm["short"] ?: ""
            val guidanceVal = valuesByForm["guidance"] ?: ""
            val imageUri = valuesByForm["image"] ?: ""
            val bigImageUri = valuesByForm["big-image"] ?: ""
            val audioUri = valuesByForm["audio"] ?: ""
            val videoUri = valuesByForm["video"] ?: ""

            val hasMedia =
              imageUri.isNotEmpty() ||
                bigImageUri.isNotEmpty() ||
                audioUri.isNotEmpty() ||
                videoUri.isNotEmpty()
            val media =
              if (hasMedia) {
                MediaRef(
                  image_uri = imageUri,
                  big_image_uri = bigImageUri,
                  audio_uri = audioUri,
                  video_uri = videoUri,
                )
              } else {
                null
              }

            put(
              textId,
              LocalizedString(
                value_ = defaultVal,
                short_value = shortVal,
                guidance_value = guidanceVal,
                media = media,
              ),
            )
          }
        }

        LanguageTranslation(language = lang, is_default = isDefault, strings = strings)
      }
    return TranslationCatalog(languages = languages)
  }

  private fun parseSubmissionConfig(subEl: XmlElement): SubmissionConfig {
    val actionUrl = subEl.attr("action") ?: ""
    val method = subEl.attr("method") ?: ""
    val base64Key = subEl.attr("base64RsaPublicKey") ?: ""
    val autoSend = parseBoolAttr(subEl.attr("auto-send") ?: subEl.attr("orx:auto-send"))
    val autoDelete = parseBoolAttr(subEl.attr("auto-delete") ?: subEl.attr("orx:auto-delete"))
    val clientEditable =
      parseBoolAttr(subEl.attr("client-editable") ?: subEl.attr("odk:client-editable"))

    return SubmissionConfig(
      action_url = actionUrl,
      method = method,
      base64_rsa_public_key = base64Key,
      auto_send = autoSend,
      auto_delete = autoDelete,
      client_editable = clientEditable,
    )
  }

  private fun parseFieldBinding(bindEl: XmlElement, rootName: String): FieldBinding {
    val rawNodeset = bindEl.attr("nodeset") ?: bindEl.attr("ref") ?: ""
    val fieldPath = stripRootPrefix(rawNodeset, rootName)
    val typeStr = bindEl.attr("type") ?: ""
    val dataType = mapXFormsTypeToDataType(typeStr)
    val readOnly = parseBoolExpr(bindEl.attr("readonly"))
    val relevantExpr = bindEl.attr("relevant") ?: ""
    val calculateExpr = bindEl.attr("calculate") ?: ""
    val constraintExpr = bindEl.attr("constraint") ?: ""
    val requiredExpr = bindEl.attr("required") ?: ""
    val constraintMsg = bindEl.attr("constraintMsg") ?: bindEl.attr("jr:constraintMsg") ?: ""
    val requiredMsg = bindEl.attr("requiredMsg") ?: bindEl.attr("jr:requiredMsg") ?: ""
    val smsTag =
      bindEl.attr("smsTag")
        ?: bindEl.attr("jr:smsTag")
        ?: bindEl.attr("tag")
        ?: bindEl.attr("jr:tag")
        ?: ""
    val entitySaveTo = bindEl.attr("saveto") ?: bindEl.attr("entities:saveto") ?: ""
    val maxPixels = (bindEl.attr("max-pixels") ?: bindEl.attr("orx:max-pixels"))?.toIntOrNull() ?: 0
    val preloadStr = bindEl.attr("preload") ?: bindEl.attr("jr:preload") ?: ""
    val preloadType = mapPreloadType(preloadStr)
    val preloadParam = bindEl.attr("preloadParams") ?: bindEl.attr("jr:preloadParams") ?: ""

    return FieldBinding(
      field_path = fieldPath,
      type = dataType,
      read_only = readOnly,
      relevant_expression = relevantExpr,
      calculate_expression = calculateExpr,
      constraint_expression = constraintExpr,
      required_expression = requiredExpr,
      constraint_message = constraintMsg,
      required_message = requiredMsg,
      sms_tag = smsTag,
      entity_saveto = entitySaveTo,
      max_pixels = maxPixels,
      preload = preloadType,
      preload_param = preloadParam,
    )
  }

  private fun parseActionDef(actionEl: XmlElement, rootName: String): ActionDef {
    val eventStr = actionEl.attr("event") ?: actionEl.attr("ev:event") ?: ""
    val events =
      eventStr.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }.mapNotNull { mapEventType(it) }
    val rawRef = actionEl.attr("ref") ?: actionEl.attr("target") ?: ""
    val targetField = stripRootPrefix(rawRef, rootName)

    return when (actionEl.localName) {
      "setgeopoint" ->
        ActionDef(
          events = events,
          type = ActionType.ACTION_SET_GEOPOINT,
          target_field = targetField,
        )
      else -> {
        val valueExpr = actionEl.attr("value") ?: ""
        val textVal = actionEl.textContent
        val literal =
          if (valueExpr.isEmpty() && textVal.isNotEmpty()) {
            TypedValue(string_value = textVal)
          } else {
            null
          }
        ActionDef(
          events = events,
          type = ActionType.ACTION_SET_VALUE,
          target_field = targetField,
          value_expression = valueExpr,
          literal_value = literal,
        )
      }
    }
  }

  private fun parseEntityDeclarations(
    metaEl: XmlElement?,
    bindings: List<FieldBinding>,
  ): List<EntityDeclaration> {
    val entityEl = metaEl?.firstChildNamed("entity") ?: return emptyList()
    val dataset = entityEl.attr("dataset") ?: ""
    val idExpr = entityEl.attr("id") ?: ""
    val createCond = entityEl.attr("create") ?: ""
    val updateCond = entityEl.attr("update") ?: ""
    val baseVer = entityEl.attr("baseVersion") ?: ""
    val trunkVer = entityEl.attr("trunkVersion") ?: ""
    val branchId = entityEl.attr("branchId") ?: ""
    val labelExpr = entityEl.firstChildNamed("label")?.textContent?.trim() ?: ""

    val syncMeta =
      if (baseVer.isNotEmpty() || trunkVer.isNotEmpty() || branchId.isNotEmpty()) {
        EntitySyncMetadata(
          base_version_expression = baseVer,
          trunk_version_expression = trunkVer,
          branch_id_expression = branchId,
        )
      } else {
        null
      }

    val mappings =
      bindings
        .filter { it.entity_saveto.isNotEmpty() }
        .map {
          EntityPropertyMapping(
            entity_property = it.entity_saveto,
            source_field_path = it.field_path,
          )
        }

    return listOf(
      EntityDeclaration(
        dataset = dataset,
        entity_id_expression = idExpr,
        label_expression = labelExpr,
        create_condition = createCond,
        update_condition = updateCond,
        sync_metadata = syncMeta,
        property_mappings = mappings,
      )
    )
  }

  private fun parsePrimaryInstanceTree(
    parentEl: XmlElement,
    parentPath: String,
    bindingTypeMap: Map<String, DataType>,
    repeatPaths: Set<String>,
  ): ParsedInstanceTree {
    val fields = mutableListOf<FieldDefinition>()
    val defaultMap = linkedMapOf<String, FieldValue>()

    // Group child elements by localName to handle repeated elements and template nodes
    val groupedChildren =
      parentEl.childElements.filter { it.localName != "meta" }.groupBy { it.localName }

    for ((fieldName, elements) in groupedChildren) {
      val currentPath = if (parentPath.isEmpty()) fieldName else "$parentPath/$fieldName"
      val hasTemplate = elements.any {
        it.attr("template") != null || it.attr("jr:template") != null
      }
      val isRepeat = hasTemplate || elements.size > 1 || currentPath in repeatPaths
      val hasNestedElements = elements.any { it.childElements.isNotEmpty() }
      val boundType = bindingTypeMap[currentPath] ?: DataType.DATA_TYPE_UNSPECIFIED

      if (hasNestedElements || (isRepeat && boundType == DataType.DATA_TYPE_UNSPECIFIED)) {
        // Merge schema across all occurrences of this group/repeat
        val nestedFieldsMap = linkedMapOf<String, FieldDefinition>()
        val repeatNodes = mutableListOf<RecordNode>()

        for (el in elements) {
          val isTemplateNode = el.attr("template") != null || el.attr("jr:template") != null
          val childParsed =
            parsePrimaryInstanceTree(
              parentEl = el,
              parentPath = currentPath,
              bindingTypeMap = bindingTypeMap,
              repeatPaths = repeatPaths,
            )
          for (cd in childParsed.schemaFields) {
            if (cd.name !in nestedFieldsMap) {
              nestedFieldsMap[cd.name] = cd
            }
          }
          if (!isTemplateNode && childParsed.defaultNode.fields.isNotEmpty()) {
            repeatNodes.add(childParsed.defaultNode)
          }
        }

        fields.add(
          FieldDefinition(
            name = fieldName,
            type = DataType.TYPE_MESSAGE,
            is_repeated = isRepeat,
            fields = nestedFieldsMap.values.toList(),
          )
        )

        if (isRepeat && repeatNodes.isNotEmpty()) {
          defaultMap[fieldName] = FieldValue(repeat_value = RecordNodeList(nodes = repeatNodes))
        } else if (!isRepeat && repeatNodes.isNotEmpty()) {
          defaultMap[fieldName] = FieldValue(node_value = repeatNodes.first())
        }
      } else {
        val effectiveType =
          if (boundType != DataType.DATA_TYPE_UNSPECIFIED) {
            boundType
          } else {
            DataType.TYPE_STRING
          }

        fields.add(FieldDefinition(name = fieldName, type = effectiveType, is_repeated = isRepeat))

        val nonTemplateEls = elements.filter {
          it.attr("template") == null && it.attr("jr:template") == null
        }
        val firstText = nonTemplateEls.firstOrNull()?.textContent?.trim() ?: ""
        if (firstText.isNotEmpty()) {
          if (effectiveType == DataType.TYPE_SELECT_MULTIPLE) {
            val listValues =
              firstText
                .split(WHITESPACE_REGEX)
                .filter { it.isNotEmpty() }
                .map { TypedValue(string_value = it) }
            defaultMap[fieldName] = FieldValue(list_value = TypedValueList(values = listValues))
          } else {
            val typedVal = parseTypedLiteral(firstText, effectiveType)
            defaultMap[fieldName] = FieldValue(scalar_value = typedVal)
          }
        }
      }
    }

    return ParsedInstanceTree(schemaFields = fields, defaultNode = RecordNode(fields = defaultMap))
  }

  private fun parseViewDef(
    bodyEl: XmlElement,
    rootName: String,
    repeatPaths: MutableSet<String>,
    selectOnePaths: MutableSet<String>,
    selectMultiplePaths: MutableSet<String>,
  ): ViewDef {
    val components =
      parseViewComponents(
        containerEl = bodyEl,
        rootName = rootName,
        enclosingPath = "",
        repeatPaths = repeatPaths,
        selectOnePaths = selectOnePaths,
        selectMultiplePaths = selectMultiplePaths,
      )
    return ViewDef(components = components)
  }

  private fun parseViewComponents(
    containerEl: XmlElement,
    rootName: String,
    enclosingPath: String,
    repeatPaths: MutableSet<String>,
    selectOnePaths: MutableSet<String>,
    selectMultiplePaths: MutableSet<String>,
  ): List<ViewComponent> = buildList {
    for (child in containerEl.childElements) {
      when (child.localName) {
        "group" -> {
          // Check if this is a wrapper group around a single <repeat> (standard ODK XForms repeat
          // idiom)
          val directRepeat = child.firstChildNamed("repeat")
          val nonLabelChildren =
            child.childElements.filter { it.localName != "label" && it.localName != "hint" }
          if (
            directRepeat != null &&
              nonLabelChildren.size == 1 &&
              nonLabelChildren.first() === directRepeat
          ) {
            val groupLabel = child.firstChildNamed("label")?.let { parseLabelDef(it) }
            val repeatDef =
              parseRepeatDef(
                repeatEl = directRepeat,
                wrapperLabel = groupLabel,
                wrapperAppearance = child.attr("appearance") ?: "",
                rootName = rootName,
                enclosingPath = enclosingPath,
                repeatPaths = repeatPaths,
                selectOnePaths = selectOnePaths,
                selectMultiplePaths = selectMultiplePaths,
              )
            add(ViewComponent(repeat = repeatDef))
          } else {
            val groupDef =
              parseGroupDef(
                groupEl = child,
                rootName = rootName,
                enclosingPath = enclosingPath,
                repeatPaths = repeatPaths,
                selectOnePaths = selectOnePaths,
                selectMultiplePaths = selectMultiplePaths,
              )
            add(ViewComponent(group = groupDef))
          }
        }
        "repeat" -> {
          val repeatDef =
            parseRepeatDef(
              repeatEl = child,
              wrapperLabel = null,
              wrapperAppearance = "",
              rootName = rootName,
              enclosingPath = enclosingPath,
              repeatPaths = repeatPaths,
              selectOnePaths = selectOnePaths,
              selectMultiplePaths = selectMultiplePaths,
            )
          add(ViewComponent(repeat = repeatDef))
        }
        "input",
        "select1",
        "select",
        "rank",
        "range",
        "upload",
        "trigger" -> {
          val controlDef =
            parseControlDef(
              controlEl = child,
              rootName = rootName,
              enclosingPath = enclosingPath,
              selectOnePaths = selectOnePaths,
              selectMultiplePaths = selectMultiplePaths,
            )
          add(ViewComponent(control = controlDef))
        }
      }
    }
  }

  private fun parseGroupDef(
    groupEl: XmlElement,
    rootName: String,
    enclosingPath: String,
    repeatPaths: MutableSet<String>,
    selectOnePaths: MutableSet<String>,
    selectMultiplePaths: MutableSet<String>,
  ): GroupDef {
    val rawRef = groupEl.attr("ref") ?: groupEl.attr("nodeset") ?: ""
    val fullPath = stripRootPrefix(rawRef, rootName)
    val relativeRef = relativizePath(fullPath, enclosingPath)
    val nextEnclosingPath = if (fullPath.isNotEmpty()) fullPath else enclosingPath

    val label = groupEl.firstChildNamed("label")?.let { parseLabelDef(it) }
    val appearance = groupEl.attr("appearance") ?: ""
    val intentStr = groupEl.attr("intent") ?: ""
    val intentConfig = if (intentStr.isNotEmpty()) parseIntentString(intentStr) else null

    val children =
      parseViewComponents(
        containerEl = groupEl,
        rootName = rootName,
        enclosingPath = nextEnclosingPath,
        repeatPaths = repeatPaths,
        selectOnePaths = selectOnePaths,
        selectMultiplePaths = selectMultiplePaths,
      )

    return GroupDef(
      field_ref = relativeRef,
      label = label,
      appearance = appearance,
      intent = intentConfig,
      components = children,
    )
  }

  private fun parseRepeatDef(
    repeatEl: XmlElement,
    wrapperLabel: LabelDef?,
    wrapperAppearance: String,
    rootName: String,
    enclosingPath: String,
    repeatPaths: MutableSet<String>,
    selectOnePaths: MutableSet<String>,
    selectMultiplePaths: MutableSet<String>,
  ): RepeatDef {
    val rawNodeset = repeatEl.attr("nodeset") ?: repeatEl.attr("ref") ?: ""
    val fullPath = stripRootPrefix(rawNodeset, rootName)
    if (fullPath.isNotEmpty()) {
      repeatPaths.add(fullPath)
    }
    val relativeRef = relativizePath(fullPath, enclosingPath)
    val nextEnclosingPath = if (fullPath.isNotEmpty()) fullPath else enclosingPath

    val label = repeatEl.firstChildNamed("label")?.let { parseLabelDef(it) } ?: wrapperLabel
    val appearance = repeatEl.attr("appearance") ?: wrapperAppearance
    val countExpr = repeatEl.attr("count") ?: repeatEl.attr("jr:count") ?: ""
    val noAddRemove = parseBoolExpr(repeatEl.attr("noAddRemove") ?: repeatEl.attr("jr:noAddRemove"))

    val children =
      parseViewComponents(
        containerEl = repeatEl,
        rootName = rootName,
        enclosingPath = nextEnclosingPath,
        repeatPaths = repeatPaths,
        selectOnePaths = selectOnePaths,
        selectMultiplePaths = selectMultiplePaths,
      )

    return RepeatDef(
      field_ref = relativeRef,
      label = label,
      appearance = appearance,
      count_expression = countExpr,
      no_add_remove = noAddRemove,
      components = children,
    )
  }

  private fun parseControlDef(
    controlEl: XmlElement,
    rootName: String,
    enclosingPath: String,
    selectOnePaths: MutableSet<String>,
    selectMultiplePaths: MutableSet<String>,
  ): ControlDef {
    val rawRef = controlEl.attr("ref") ?: controlEl.attr("nodeset") ?: ""
    val fullPath = stripRootPrefix(rawRef, rootName)
    val relativeRef = relativizePath(fullPath, enclosingPath)

    val controlType =
      when (controlEl.localName) {
        "input" -> ControlType.CONTROL_INPUT
        "select1" -> {
          if (fullPath.isNotEmpty()) selectOnePaths.add(fullPath)
          ControlType.CONTROL_SELECT_ONE
        }
        "select" -> {
          if (fullPath.isNotEmpty()) selectMultiplePaths.add(fullPath)
          ControlType.CONTROL_SELECT_MULTIPLE
        }
        "rank" -> ControlType.CONTROL_RANK
        "range" -> ControlType.CONTROL_RANGE
        "upload" -> ControlType.CONTROL_UPLOAD
        "trigger" -> ControlType.CONTROL_TRIGGER
        else -> ControlType.CONTROL_TYPE_UNSPECIFIED
      }

    val label = controlEl.firstChildNamed("label")?.let { parseLabelDef(it) }
    val hint = controlEl.firstChildNamed("hint")?.let { parseLabelDef(it) }
    val appearance = controlEl.attr("appearance") ?: ""
    val mediaType = controlEl.attr("mediatype") ?: ""

    // Choices (<item>)
    val choices =
      controlEl.childrenNamed("item").map { itemEl ->
        val itemLabel = itemEl.firstChildNamed("label")?.let { parseLabelDef(it) }
        val itemVal = itemEl.firstChildNamed("value")?.textContent?.trim() ?: ""
        ChoiceItem(value_ = itemVal, label = itemLabel)
      }

    // Dynamic Itemset (<itemset>)
    val itemsetEl = controlEl.firstChildNamed("itemset")
    val itemset = itemsetEl?.let { parseItemsetDef(it) }

    // Range configuration
    val rangeConfig =
      if (controlType == ControlType.CONTROL_RANGE) {
        RangeConfig(
          start = controlEl.attr("start")?.toDoubleOrNull() ?: 0.0,
          end = controlEl.attr("end")?.toDoubleOrNull() ?: 0.0,
          step = controlEl.attr("step")?.toDoubleOrNull() ?: 0.0,
          tick_interval =
            (controlEl.attr("tick-interval") ?: controlEl.attr("odk:tick-interval"))
              ?.toDoubleOrNull() ?: 0.0,
        )
      } else {
        null
      }

    // Geo configuration
    val accThresh = controlEl.attr("accuracyThreshold")?.toDoubleOrNull()
    val warnThresh = controlEl.attr("unacceptableAccuracyThreshold")?.toDoubleOrNull()
    val allowMock = controlEl.attr("allowMockAccuracy")
    val geoConfig =
      if (accThresh != null || warnThresh != null || allowMock != null) {
        GeoConfig(
          accuracy_threshold_meters = accThresh ?: 0.0,
          warning_threshold_meters = warnThresh ?: 0.0,
          allow_mock_accuracy = parseBoolAttr(allowMock),
        )
      } else {
        null
      }

    // Intent configuration
    val intentStr = controlEl.attr("intent") ?: ""
    val intentConfig = if (intentStr.isNotEmpty()) parseIntentString(intentStr) else null

    // Control-level nested actions
    val actions =
      controlEl.childElements
        .filter { it.localName == "setvalue" || it.localName == "setgeopoint" }
        .map { parseActionDef(it, rootName) }

    return ControlDef(
      field_ref = relativeRef,
      type = controlType,
      label = label,
      hint = hint,
      appearance = appearance,
      choices = choices,
      itemset = itemset,
      range_config = rangeConfig,
      media_type = mediaType,
      intent = intentConfig,
      actions = actions,
      geo_config = geoConfig,
    )
  }

  private fun parseLabelDef(labelEl: XmlElement): LabelDef {
    val refAttr = labelEl.attr("ref") ?: ""
    val textId =
      if (refAttr.startsWith("jr:itext(")) {
        refAttr.substringAfter("jr:itext(").substringBeforeLast(")").trim('\'', '"', ' ')
      } else {
        ""
      }

    val outputs = mutableListOf<OutputFragment>()
    val textBuilder = StringBuilder()

    for (child in labelEl.children) {
      when (child) {
        is XmlText -> textBuilder.append(child.text)
        is XmlElement -> {
          if (child.localName == "output") {
            val rawExpr = child.attr("value") ?: ""
            val placeholder = "{${outputs.size}}"
            textBuilder.append(placeholder)
            outputs.add(OutputFragment(placeholder_id = placeholder, value_expression = rawExpr))
          } else {
            textBuilder.append(child.textContent)
          }
        }
      }
    }

    return LabelDef(text = textBuilder.toString().trim(), text_id = textId, outputs = outputs)
  }

  private fun parseItemsetDef(itemsetEl: XmlElement): ItemsetDef {
    val rawNodeset = itemsetEl.attr("nodeset") ?: ""
    val isRandomize = rawNodeset.startsWith("randomize(") && rawNodeset.endsWith(")")
    val unwrapped =
      if (isRandomize) {
        rawNodeset.substring(10, rawNodeset.length - 1).trim()
      } else {
        rawNodeset
      }
    val commaIdx = if (isRandomize) findTopLevelComma(unwrapped) else -1
    val nodeset = if (commaIdx != -1) unwrapped.substring(0, commaIdx).trim() else unwrapped
    val seedExpr = if (commaIdx != -1) unwrapped.substring(commaIdx + 1).trim() else ""

    val instanceId = extractInstanceId(nodeset)
    val bracketStart = nodeset.indexOf('[')
    val bracketEnd = nodeset.lastIndexOf(']')
    val filter =
      if (bracketStart != -1 && bracketEnd > bracketStart) {
        nodeset.substring(bracketStart + 1, bracketEnd).trim()
      } else {
        ""
      }

    val valueRef = itemsetEl.firstChildNamed("value")?.attr("ref") ?: ""
    val labelRef = itemsetEl.firstChildNamed("label")?.attr("ref") ?: ""

    return ItemsetDef(
      instance_id = instanceId,
      nodeset_filter = filter,
      value_ref = valueRef,
      label_ref = labelRef,
      randomize = isRandomize,
      random_seed_expression = seedExpr,
    )
  }

  private fun extractInstanceId(nodeset: String): String {
    val instStart = nodeset.indexOf("instance(")
    if (instStart == -1) return ""
    val quoteStart = nodeset.indexOfAny(charArrayOf('\'', '"'), instStart + 9)
    if (quoteStart == -1) return ""
    val quoteChar = nodeset[quoteStart]
    val quoteEnd = nodeset.indexOf(quoteChar, quoteStart + 1)
    if (quoteEnd == -1) return ""
    return nodeset.substring(quoteStart + 1, quoteEnd)
  }

  private fun findTopLevelComma(input: String): Int {
    var bracketDepth = 0
    var parenDepth = 0
    var inQuote: Char? = null
    for (i in input.indices) {
      val c = input[i]
      if (inQuote != null) {
        if (c == inQuote) inQuote = null
      } else {
        when (c) {
          '\'',
          '"' -> inQuote = c
          '[' -> bracketDepth++
          ']' -> bracketDepth--
          '(' -> parenDepth++
          ')' -> parenDepth--
          ',' -> if (bracketDepth == 0 && parenDepth == 0) return i
        }
      }
    }
    return -1
  }

  private fun parseIntentString(intentStr: String): IntentConfig {
    val parenStart = intentStr.indexOf('(')
    val parenEnd = intentStr.lastIndexOf(')')
    if (parenStart == -1 || parenEnd <= parenStart) {
      return IntentConfig(intent_uri = intentStr.trim())
    }
    val uri = intentStr.substring(0, parenStart).trim()
    val paramsPart = intentStr.substring(parenStart + 1, parenEnd)
    val params = buildMap {
      for (pair in paramsPart.split(',')) {
        val eqIdx = pair.indexOf('=')
        if (eqIdx != -1) {
          put(pair.substring(0, eqIdx).trim(), pair.substring(eqIdx + 1).trim())
        }
      }
    }
    return IntentConfig(intent_uri = uri, parameters = params)
  }

  internal fun stripRootPrefix(path: String, rootName: String): String {
    val trimmed = path.trim()
    val prefix = "/$rootName/"
    return when {
      trimmed.startsWith(prefix) -> trimmed.removePrefix(prefix)
      trimmed == "/$rootName" -> ""
      else -> trimmed.removePrefix("/")
    }
  }

  private fun relativizePath(fullPath: String, enclosingPath: String): String =
    if (enclosingPath.isNotEmpty()) fullPath.removePrefix("$enclosingPath/") else fullPath

  internal fun mapXFormsTypeToDataType(typeStr: String): DataType {
    val clean = typeStr.substringAfter(':').lowercase()
    return when (clean) {
      "string",
      "text",
      "barcode" -> DataType.TYPE_STRING
      "int",
      "integer" -> DataType.TYPE_INT32
      "long" -> DataType.TYPE_INT64
      "decimal",
      "double",
      "float" -> DataType.TYPE_DOUBLE
      "boolean",
      "bool" -> DataType.TYPE_BOOLEAN
      "date" -> DataType.TYPE_DATE
      "time" -> DataType.TYPE_TIME
      "datetime",
      "timestamp" -> DataType.TYPE_DATETIME
      "geopoint" -> DataType.TYPE_GEOPOINT
      "geotrace" -> DataType.TYPE_GEOTRACE
      "geoshape" -> DataType.TYPE_GEOSHAPE
      "binary" -> DataType.TYPE_BINARY
      "select1" -> DataType.TYPE_SELECT_ONE
      "select" -> DataType.TYPE_SELECT_MULTIPLE
      else -> DataType.DATA_TYPE_UNSPECIFIED
    }
  }

  private fun mapPreloadType(preloadStr: String): PreloadType =
    when (preloadStr.lowercase()) {
      "timestamp" -> PreloadType.PRELOAD_TIMESTAMP
      "date" -> PreloadType.PRELOAD_DATE
      "time" -> PreloadType.PRELOAD_TIME
      "uid" -> PreloadType.PRELOAD_UID
      "property" -> PreloadType.PRELOAD_PROPERTY
      "context" -> PreloadType.PRELOAD_CONTEXT
      else -> PreloadType.PRELOAD_UNSPECIFIED
    }

  private fun mapEventType(eventStr: String): EventType? =
    when (eventStr) {
      "odk-instance-first-load" -> EventType.EVENT_INSTANCE_FIRST_LOAD
      "odk-instance-load" -> EventType.EVENT_INSTANCE_LOAD
      "xforms-value-changed" -> EventType.EVENT_VALUE_CHANGED
      "odk-new-repeat" -> EventType.EVENT_REPEAT_INSERT
      else -> null
    }

  private fun parseBoolAttr(value: String?): Boolean =
    when (value?.trim()?.lowercase()) {
      "true",
      "true()",
      "1",
      "yes" -> true
      else -> false
    }

  private fun parseBoolExpr(expr: String?): Boolean =
    when (expr?.trim()?.lowercase()) {
      "true()",
      "true",
      "1" -> true
      else -> false
    }

  internal fun parseTypedLiteral(text: String, dataType: DataType): TypedValue =
    when (dataType) {
      DataType.TYPE_INT32 -> TypedValue(int32_value = text.toIntOrNull() ?: 0)
      DataType.TYPE_INT64 -> TypedValue(int64_value = text.toLongOrNull() ?: 0L)
      DataType.TYPE_DOUBLE -> TypedValue(double_value = text.toDoubleOrNull() ?: 0.0)
      DataType.TYPE_BOOLEAN -> TypedValue(bool_value = text.lowercase() == "true" || text == "1")
      DataType.TYPE_DATE ->
        parseDateLiteral(text)?.let { TypedValue(date_value = it) }
          ?: TypedValue(string_value = text)
      DataType.TYPE_TIME ->
        parseTimeLiteral(text)?.let { TypedValue(time_value = it) }
          ?: TypedValue(string_value = text)
      DataType.TYPE_DATETIME ->
        parseTimestampLiteral(text)?.let { TypedValue(timestamp_value = it) }
          ?: TypedValue(string_value = text)
      DataType.TYPE_GEOPOINT ->
        parseGeoPointString(text)?.let { TypedValue(geopoint_value = it) }
          ?: TypedValue(string_value = text)
      DataType.TYPE_GEOTRACE ->
        parseGeoTraceString(text)?.let { TypedValue(geotrace_value = it) }
          ?: TypedValue(string_value = text)
      DataType.TYPE_GEOSHAPE ->
        parseGeoShapeString(text)?.let { TypedValue(geoshape_value = it) }
          ?: TypedValue(string_value = text)
      else -> TypedValue(string_value = text)
    }

  internal fun parseDateLiteral(text: String): Date? {
    val parts = text.trim().split('-')
    if (parts.size < 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].take(2).toIntOrNull() ?: return null
    return Date(year = y, month = m, day = d)
  }

  internal fun parseTimeLiteral(text: String): TimeOfDay? {
    val clean = text.trim().substringBefore('Z').substringBefore('+').substringBefore('-')
    val parts = clean.split(':')
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val secParts = if (parts.size >= 3) parts[2].split('.') else listOf("0")
    val s = secParts[0].toIntOrNull() ?: 0
    val nanos =
      if (secParts.size > 1) {
        secParts[1].padEnd(9, '0').take(9).toIntOrNull() ?: 0
      } else {
        0
      }
    return TimeOfDay(hours = h, minutes = m, seconds = s, nanos = nanos)
  }

  internal fun parseTimestampLiteral(text: String): Instant? {
    // Parse standard ISO-8601 string YYYY-MM-DDTHH:MM:SS(.sss)Z
    return try {
      // Simple ISO-8601 UTC / offset parser for KMP commonMain
      val datePart = text.substringBefore('T')
      val timePart = text.substringAfter('T', "")
      if (timePart.isEmpty()) return null
      val date = parseDateLiteral(datePart) ?: return null
      val time = parseTimeLiteral(timePart) ?: return null
      val epochDays = daysFromCivil(date.year, date.month, date.day)
      val epochSeconds = epochDays * 86400L + time.hours * 3600L + time.minutes * 60L + time.seconds
      ofEpochSecond(epochSeconds, time.nanos.toLong())
    } catch (_: Exception) {
      null
    }
  }

  private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val year = if (m <= 2) y - 1L else y.toLong()
    val era = if (year >= 0) year / 400 else (year - 399) / 400
    val yoe = year - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  internal fun parseGeoPointString(text: String): GeoPoint? {
    val parts = text.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
    val acc = if (parts.size >= 4) parts[3].toDoubleOrNull() ?: 0.0 else 0.0
    return GeoPoint(latitude = lat, longitude = lon, altitude_meters = alt, accuracy_meters = acc)
  }

  internal fun parseGeoTraceString(text: String): GeoTrace? {
    val points =
      text
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { parseGeoPointString(it) }
    if (points.isEmpty()) return null
    return GeoTrace(points = points)
  }

  internal fun parseGeoShapeString(text: String): GeoShape? {
    val points =
      text
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { parseGeoPointString(it) }
    if (points.isEmpty()) return null
    return GeoShape(points = points)
  }
}
