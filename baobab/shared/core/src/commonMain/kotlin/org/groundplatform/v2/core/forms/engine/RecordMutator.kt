/*
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
package org.groundplatform.v2.core.forms.engine

import com.squareup.wire.Instant
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordNodeList
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.xpath.model.TemporalUtils
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Immutable tree manipulation and path resolution utilities for [RecordNode], [RecordMetadata], and
 * [FieldDefinition] hierarchies.
 */
internal object RecordMutator {

  /** A single step in a canonical or relative instance path (e.g., `person[2]` or `age`). */
  data class PathSegment(
    val name: String,
    /** 1-based repeat index if explicitly specified (`[1]`), or `null` otherwise. */
    val repeatIndex: Int? = null,
  ) {
    fun format(includeDefaultRepeatIndex: Boolean = false): String =
      when {
        repeatIndex != null && (repeatIndex > 1 || includeDefaultRepeatIndex) ->
          "$name[$repeatIndex]"
        else -> name
      }
  }

  /**
   * Determines the canonical root element name for a [FormDef] (e.g., `"household"` or `"data"`).
   */
  fun resolveRootName(formDef: FormDef): String =
    formDef.model?.primary_instance?.record_schema?.name?.takeIf { it.isNotEmpty() }
      ?: formDef.form_id.takeIf { it.isNotEmpty() }
      ?: "data"

  /** Returns all accepted root element aliases for a [FormDef]. */
  fun resolveRootAliases(formDef: FormDef): Set<String> {
    val rootName = resolveRootName(formDef)
    return setOfNotNull(
      rootName,
      "data",
      formDef.form_id.takeIf { it.isNotEmpty() },
      formDef.model?.primary_instance?.record_schema?.name?.takeIf { it.isNotEmpty() },
    )
  }

  /**
   * Normalizes a raw binding or control path into a schema-relative path without leading `/` or
   * root element prefix, and with repeat index predicates (`[1]`) stripped.
   *
   * Example: `"/household/person[2]/age"` -> `"person/age"`; `"orx:meta/instanceID"` ->
   * `"meta/instanceID"`.
   */
  fun normalizeSchemaPath(rawPath: String, rootAliases: Set<String>): String {
    val trimmed = rawPath.trim()
    if (trimmed.isEmpty() || trimmed == "/" || trimmed == ".") return ""
    val rawSegments =
      trimmed
        .trimStart('/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." }
        .map { stripPredicate(it) }
    if (rawSegments.isEmpty()) return ""
    val withoutRoot =
      if (rawSegments.first() in rootAliases) {
        rawSegments.drop(1)
      } else {
        rawSegments
      }
    return withoutRoot.map { seg -> if (seg == "orx:meta") "meta" else seg }.joinToString("/")
  }

  /**
   * Resolves [rawPath] against [contextCanonicalPath] to produce a canonical path rooted at
   * `/$rootName/...`.
   *
   * Preserves repeat indices inherited from [contextCanonicalPath] when [rawPath] references a
   * descendant or sibling within the same repeat instance.
   */
  fun resolveCanonicalPath(
    rawPath: String,
    rootName: String,
    rootAliases: Set<String>,
    contextCanonicalPath: String = "/$rootName",
  ): String {
    val trimmed = rawPath.trim()
    if (trimmed.isEmpty() || trimmed == ".") return contextCanonicalPath
    if (trimmed == "/") return "/$rootName"

    val contextSegments = parseSegments(contextCanonicalPath.trimStart('/'))
    val rawParts = trimmed.split('/').filter { it.isNotEmpty() }

    val resolvedSegments: MutableList<PathSegment>
    if (trimmed.startsWith('/')) {
      val firstName = stripPredicate(rawParts.firstOrNull() ?: rootName)
      val tailParts = if (firstName in rootAliases) rawParts.drop(1) else rawParts
      resolvedSegments = mutableListOf(PathSegment(rootName))
      for (part in tailParts) {
        applyRelativePart(resolvedSegments, part, contextSegments)
      }
    } else {
      // Check if rawPath starts with rootName or is already a schema-root-relative path
      val firstName = stripPredicate(rawParts.first())
      if (firstName in rootAliases && rawParts.size > 1) {
        resolvedSegments = mutableListOf(PathSegment(rootName))
        for (part in rawParts.drop(1)) {
          applyRelativePart(resolvedSegments, part, contextSegments)
        }
      } else if (
        !rawParts.first().startsWith(".") &&
          contextSegments.size > 1 &&
          rawParts.first().let { stripPredicate(it) } == contextSegments.getOrNull(1)?.name
      ) {
        // Path starts from top-level schema element (e.g. "person/age" inside context
        // "/data/person[2]")
        resolvedSegments = mutableListOf(PathSegment(rootName))
        for (part in rawParts) {
          applyRelativePart(resolvedSegments, part, contextSegments)
        }
      } else {
        resolvedSegments = contextSegments.toMutableList()
        if (resolvedSegments.isEmpty()) {
          resolvedSegments.add(PathSegment(rootName))
        }
        for (part in rawParts) {
          applyRelativePart(resolvedSegments, part, contextSegments)
        }
      }
    }

    return "/" + resolvedSegments.joinToString("/") { it.format() }
  }

  private fun applyRelativePart(
    stack: MutableList<PathSegment>,
    part: String,
    contextSegments: List<PathSegment>,
  ) {
    when (part) {
      "." -> {}
      ".." -> {
        if (stack.size > 1) {
          stack.removeAt(stack.lastIndex)
        }
      }
      else -> {
        val parsed = parseSingleSegment(if (part == "orx:meta") "meta" else part)
        val depthIndex = stack.size
        // Inherit repeatIndex from contextSegments if matching the same ancestor step
        val inheritedIndex =
          if (
            parsed.repeatIndex == null &&
              depthIndex < contextSegments.size &&
              contextSegments[depthIndex].name == parsed.name &&
              prefixMatches(stack, contextSegments, depthIndex)
          ) {
            contextSegments[depthIndex].repeatIndex
          } else {
            parsed.repeatIndex
          }
        stack.add(PathSegment(parsed.name, inheritedIndex))
      }
    }
  }

  private fun prefixMatches(a: List<PathSegment>, b: List<PathSegment>, length: Int): Boolean {
    for (i in 1 until length) {
      if (a[i].name != b[i].name) return false
      val idxA = a[i].repeatIndex ?: 1
      val idxB = b[i].repeatIndex ?: 1
      if (idxA != idxB) return false
    }
    return true
  }

  fun parseSegments(path: String): List<PathSegment> =
    path.trim('/').split('/').filter { it.isNotEmpty() }.map { parseSingleSegment(it) }

  fun parseSingleSegment(segment: String): PathSegment {
    val bracketStart = segment.indexOf('[')
    val bracketEnd = segment.indexOf(']')
    if (bracketStart > 0 && bracketEnd > bracketStart) {
      val rawName = segment.substring(0, bracketStart)
      val name = if (rawName == "orx:meta") "meta" else rawName
      val idx = segment.substring(bracketStart + 1, bracketEnd).trim().toIntOrNull()
      return PathSegment(name, idx)
    }
    val name = if (segment == "orx:meta") "meta" else segment
    return PathSegment(name, null)
  }

  private fun stripPredicate(segment: String): String {
    val idx = segment.indexOf('[')
    return if (idx > 0) segment.substring(0, idx) else segment
  }

  /** Returns true if a [FieldValue] is null or contains an empty/unpopulated answer. */
  fun isFieldValueEmpty(fv: FieldValue?): Boolean {
    if (fv == null) return true
    return when {
      fv.scalar_value != null -> XPathValue.fromTypedValue(fv.scalar_value).isEmptyValue()
      fv.list_value != null ->
        fv.list_value.values.isEmpty() ||
          fv.list_value.values.all { XPathValue.fromTypedValue(it).isEmptyValue() }
      fv.node_value != null -> fv.node_value.fields.values.all { isFieldValueEmpty(it) }
      fv.repeat_value != null -> fv.repeat_value.nodes.isEmpty()
      else -> true
    }
  }

  /**
   * Builds the initial [RecordNode] for a new form session by merging schema structure and default
   * values from `FormDef.model.primary_instance`.
   */
  fun buildInitialRecordNode(
    schema: RecordSchema?,
    defaultValues: RecordNode?,
    existingNode: RecordNode? = null,
  ): RecordNode {
    if (existingNode != null) {
      return mergeNodes(
        base = buildTemplateNode(schema?.fields ?: emptyList(), defaultValues),
        override = existingNode,
      )
    }
    return buildTemplateNode(schema?.fields ?: emptyList(), defaultValues)
  }

  /** Builds a default [RecordNode] for a newly inserted repeat instance. */
  fun buildDefaultRepeatItemNode(
    repeatFieldDef: FieldDefinition?,
    defaultTemplateNode: RecordNode? = null,
  ): RecordNode = buildTemplateNode(repeatFieldDef?.fields ?: emptyList(), defaultTemplateNode)

  private fun buildTemplateNode(
    schemaFields: List<FieldDefinition>,
    defaults: RecordNode?,
  ): RecordNode {
    val map = linkedMapOf<String, FieldValue>()
    val schemaByName = schemaFields.associateBy { it.name }

    for (fieldDef in schemaFields) {
      val defaultVal = defaults?.fields?.get(fieldDef.name)
      if (fieldDef.is_repeated && fieldDef.type == DataType.TYPE_MESSAGE) {
        val defaultRepeatItems = defaultVal?.repeat_value?.nodes ?: emptyList()
        val mergedItems = defaultRepeatItems.map { itemNode ->
          buildTemplateNode(fieldDef.fields, itemNode)
        }
        map[fieldDef.name] = FieldValue(repeat_value = RecordNodeList(nodes = mergedItems))
      } else if (fieldDef.type == DataType.TYPE_MESSAGE) {
        val childNode = buildTemplateNode(fieldDef.fields, defaultVal?.node_value)
        map[fieldDef.name] = FieldValue(node_value = childNode)
      } else if (defaultVal != null) {
        map[fieldDef.name] = defaultVal
      }
    }

    // Also include any default fields not explicitly listed in schemaFields
    for ((k, v) in defaults?.fields ?: emptyMap()) {
      if (!schemaByName.containsKey(k)) {
        map[k] = v
      }
    }
    return RecordNode(fields = map)
  }

  private fun mergeNodes(base: RecordNode, override: RecordNode): RecordNode {
    val merged = linkedMapOf<String, FieldValue>()
    merged.putAll(base.fields)
    for ((key, overVal) in override.fields) {
      val baseVal = merged[key]
      if (baseVal?.node_value != null && overVal.node_value != null) {
        merged[key] = FieldValue(node_value = mergeNodes(baseVal.node_value, overVal.node_value))
      } else {
        merged[key] = overVal
      }
    }
    return RecordNode(fields = merged)
  }

  /**
   * Reads a [FieldValue] at [canonicalPath] from [rootNode] (or [metadata] if targeting
   * `meta/...`).
   */
  fun getFieldValue(
    rootNode: RecordNode,
    metadata: RecordMetadata?,
    rootAliases: Set<String>,
    canonicalPath: String,
  ): FieldValue? {
    val segments = parseSegments(canonicalPath)
    val steps =
      if (segments.isNotEmpty() && segments.first().name in rootAliases) {
        segments.drop(1)
      } else {
        segments
      }
    if (steps.isEmpty()) return FieldValue(node_value = rootNode)

    // Check if targeting <meta>
    if (steps.first().name == "meta" && steps.size == 2) {
      val metaField = steps[1].name
      readMetadataFieldValue(metadata, metaField)?.let {
        return it
      }
    }

    return getFromRecordNode(rootNode, steps, 0)
  }

  private fun getFromRecordNode(
    node: RecordNode,
    steps: List<PathSegment>,
    index: Int,
  ): FieldValue? {
    val seg = steps[index]
    val fv = node.fields[seg.name] ?: return null
    if (index == steps.lastIndex) {
      if (fv.repeat_value != null && seg.repeatIndex != null) {
        val targetNode = fv.repeat_value.nodes.getOrNull(seg.repeatIndex - 1) ?: return null
        return FieldValue(node_value = targetNode)
      }
      return fv
    }

    return when {
      fv.node_value != null -> getFromRecordNode(fv.node_value, steps, index + 1)
      fv.repeat_value != null -> {
        val repIdx = (seg.repeatIndex ?: 1) - 1
        val childNode = fv.repeat_value.nodes.getOrNull(repIdx) ?: return null
        getFromRecordNode(childNode, steps, index + 1)
      }
      else -> null
    }
  }

  private fun readMetadataFieldValue(metadata: RecordMetadata?, fieldName: String): FieldValue? {
    if (metadata == null) return null
    return when (fieldName) {
      "instanceID",
      "instance_id" ->
        metadata.instance_id
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      "timeStart",
      "start_time" ->
        metadata.start_time?.let { FieldValue(scalar_value = TypedValue(timestamp_value = it)) }
      "timeEnd",
      "end_time" ->
        metadata.end_time?.let { FieldValue(scalar_value = TypedValue(timestamp_value = it)) }
      "today" -> metadata.today?.let { FieldValue(scalar_value = TypedValue(timestamp_value = it)) }
      "deviceID",
      "device_id" ->
        metadata.device_id
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      "subscriberID",
      "subscriber_id" ->
        metadata.subscriber_id
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      "simSerial",
      "sim_serial" ->
        metadata.sim_serial
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      "phoneNumber",
      "phone_number" ->
        metadata.phone_number
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      "audit" ->
        metadata.audit_file_uri
          .takeIf { it.isNotEmpty() }
          ?.let { FieldValue(scalar_value = TypedValue(string_value = it)) }
      else -> null
    }
  }

  /**
   * Writes [newValue] at [canonicalPath] into [rootNode] and/or [metadata], returning the updated
   * pair `(RecordNode, RecordMetadata)`.
   */
  fun setFieldValue(
    rootNode: RecordNode,
    metadata: RecordMetadata?,
    rootAliases: Set<String>,
    canonicalPath: String,
    newValue: FieldValue?,
  ): Pair<RecordNode, RecordMetadata?> {
    val segments = parseSegments(canonicalPath)
    val steps =
      if (segments.isNotEmpty() && segments.first().name in rootAliases) {
        segments.drop(1)
      } else {
        segments
      }
    if (steps.isEmpty()) return rootNode to metadata

    var updatedMetadata = metadata
    if (steps.first().name == "meta" && steps.size == 2) {
      val metaField = steps[1].name
      val metaUpdated = updateMetadataField(metadata ?: RecordMetadata(), metaField, newValue)
      if (metaUpdated != null) {
        updatedMetadata = metaUpdated
      }
    }

    val updatedRoot = setInRecordNode(rootNode, steps, 0, newValue)
    return updatedRoot to updatedMetadata
  }

  private fun updateMetadataField(
    meta: RecordMetadata,
    fieldName: String,
    fv: FieldValue?,
  ): RecordMetadata? {
    val strVal = fv?.scalar_value?.let { XPathValue.fromTypedValue(it).toXPathString() } ?: ""
    val tsVal: Instant? =
      fv?.scalar_value?.timestamp_value
        ?: fv?.scalar_value?.date_value?.let {
          TemporalUtils.epochDaysToInstant(TemporalUtils.dateToEpochDays(it))
        }
        ?: TemporalUtils.tryParseToEpochDays(strVal)?.let { TemporalUtils.epochDaysToInstant(it) }

    return when (fieldName) {
      "instanceID",
      "instance_id" -> meta.copy(instance_id = strVal)
      "timeStart",
      "start_time" -> meta.copy(start_time = tsVal)
      "timeEnd",
      "end_time" -> meta.copy(end_time = tsVal)
      "today" -> meta.copy(today = tsVal)
      "deviceID",
      "device_id" -> meta.copy(device_id = strVal)
      "subscriberID",
      "subscriber_id" -> meta.copy(subscriber_id = strVal)
      "simSerial",
      "sim_serial" -> meta.copy(sim_serial = strVal)
      "phoneNumber",
      "phone_number" -> meta.copy(phone_number = strVal)
      "audit" -> meta.copy(audit_file_uri = strVal)
      else -> null
    }
  }

  private fun setInRecordNode(
    node: RecordNode,
    steps: List<PathSegment>,
    index: Int,
    newValue: FieldValue?,
  ): RecordNode {
    val seg = steps[index]
    val mutableFields = node.fields.toMutableMap()

    if (index == steps.lastIndex) {
      if (newValue == null) {
        mutableFields.remove(seg.name)
      } else {
        mutableFields[seg.name] = newValue
      }
      return RecordNode(fields = mutableFields)
    }

    val existing = mutableFields[seg.name]
    if (seg.repeatIndex != null || existing?.repeat_value != null) {
      val currentList = existing?.repeat_value?.nodes?.toMutableList() ?: mutableListOf()
      val targetIdx = (seg.repeatIndex ?: 1) - 1
      while (currentList.size <= targetIdx) {
        currentList.add(RecordNode())
      }
      currentList[targetIdx] = setInRecordNode(currentList[targetIdx], steps, index + 1, newValue)
      mutableFields[seg.name] = FieldValue(repeat_value = RecordNodeList(nodes = currentList))
    } else {
      val childNode = existing?.node_value ?: RecordNode()
      val updatedChild = setInRecordNode(childNode, steps, index + 1, newValue)
      mutableFields[seg.name] = FieldValue(node_value = updatedChild)
    }
    return RecordNode(fields = mutableFields)
  }

  /** Reads the list of repeat item [RecordNode]s at [repeatCanonicalPath]. */
  fun getRepeatInstances(
    rootNode: RecordNode,
    rootAliases: Set<String>,
    repeatCanonicalPath: String,
  ): List<RecordNode> {
    val segments = parseSegments(repeatCanonicalPath)
    val steps =
      if (segments.isNotEmpty() && segments.first().name in rootAliases) {
        segments.drop(1)
      } else {
        segments
      }
    if (steps.isEmpty()) return emptyList()
    val fv = getFromRecordNode(rootNode, steps, 0)
    return fv?.repeat_value?.nodes ?: emptyList()
  }

  /** Replaces the list of repeat item [RecordNode]s at [repeatCanonicalPath]. */
  fun setRepeatInstances(
    rootNode: RecordNode,
    rootAliases: Set<String>,
    repeatCanonicalPath: String,
    instances: List<RecordNode>,
  ): RecordNode {
    val segments = parseSegments(repeatCanonicalPath)
    val steps =
      if (segments.isNotEmpty() && segments.first().name in rootAliases) {
        segments.drop(1)
      } else {
        segments
      }
    if (steps.isEmpty()) return rootNode
    val repeatFv = FieldValue(repeat_value = RecordNodeList(nodes = instances))
    return setInRecordNode(rootNode, steps, 0, repeatFv)
  }

  /**
   * Produces a pruned [RecordNode] omitting any fields, groups, or repeat instances marked
   * non-relevant in [relevancyMap] (per the ODK XForms specification).
   */
  fun pruneNonRelevantNodes(
    node: RecordNode,
    currentPathPrefix: String,
    relevancyMap: Map<String, Boolean>,
  ): RecordNode {
    val prunedFields = linkedMapOf<String, FieldValue>()
    for ((fieldName, fv) in node.fields) {
      val fieldPath = "$currentPathPrefix/$fieldName"
      if (relevancyMap[fieldPath] == false) {
        continue
      }
      when {
        fv.repeat_value != null -> {
          val total = fv.repeat_value.nodes.size
          val keptNodes = mutableListOf<RecordNode>()
          for ((idx, itemNode) in fv.repeat_value.nodes.withIndex()) {
            val idx1 = idx + 1
            val indexedPath = "$fieldPath[$idx1]"
            val unindexedPath = if (total == 1) fieldPath else indexedPath
            val isInstanceRelevant =
              relevancyMap[indexedPath] ?: relevancyMap[unindexedPath] ?: true
            if (isInstanceRelevant) {
              keptNodes.add(
                pruneNonRelevantNodes(
                  node = itemNode,
                  currentPathPrefix = indexedPath,
                  relevancyMap = relevancyMap,
                )
              )
            }
          }
          prunedFields[fieldName] = FieldValue(repeat_value = RecordNodeList(nodes = keptNodes))
        }
        fv.node_value != null -> {
          prunedFields[fieldName] =
            FieldValue(
              node_value =
                pruneNonRelevantNodes(
                  node = fv.node_value,
                  currentPathPrefix = fieldPath,
                  relevancyMap = relevancyMap,
                )
            )
        }
        else -> {
          prunedFields[fieldName] = fv
        }
      }
    }
    return RecordNode(fields = prunedFields)
  }
}
