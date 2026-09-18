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
package org.groundplatform.v2.core.forms.xpath.model

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.TypedValue

/**
 * Virtual cursor / zipper node wrapping immutable Protocol Buffer messages ([RecordInstance],
 * [RecordNode], [FieldValue], [RecordMetadata]) and secondary lookup rows.
 *
 * Provides upward parent links (`..`), 1-based repeat position indices (`position()`), sibling
 * traversal, and schema-aware empty node resolution without requiring reflection or modifying
 * Wire-generated classes.
 */
sealed interface XPathNode {
  /** Field or element name (e.g., `"household"`, `"member"`, `"age"`). */
  val name: String

  /** Enclosing parent node, or `null` if this is the virtual document root. */
  val parent: XPathNode?

  /** 1-based repeat index among identically named siblings (`1` for non-repeat fields). */
  val repeatIndex: Int

  /**
   * Total number of identically named siblings in the enclosing repeat/group (`1` for non-repeats).
   */
  val siblingRepeatCount: Int

  /** Declared schema data type for this field, if known. */
  val schemaType: DataType?

  /** Returns direct child nodes, optionally filtered by [nameFilter]. */
  fun children(nameFilter: String? = null): List<XPathNode>

  /** Extracts the typed value stored at this node. */
  fun extractValue(): XPathValue

  /**
   * Returns the canonical absolute path from root to this node (e.g. `/household/member[2]/age`).
   */
  fun canonicalPath(): String {
    val segments = mutableListOf<String>()
    var curr: XPathNode? = this
    while (curr != null && curr !is DocumentRootNode) {
      val suffix = if (curr.siblingRepeatCount > 1) "[${curr.repeatIndex}]" else ""
      segments.add("${curr.name}$suffix")
      curr = curr.parent
    }
    return "/" + segments.reversed().joinToString("/")
  }

  /**
   * Virtual document root `/` above the top-level instance element (e.g. `/data` or `/household`).
   */
  class DocumentRootNode(val recordInstance: RecordInstance, val schema: RecordSchema? = null) :
    XPathNode {
    override val name: String = ""
    override val parent: XPathNode? = null
    override val repeatIndex: Int = 1
    override val siblingRepeatCount: Int = 1
    override val schemaType: DataType? = null

    val instanceElementNode: InstanceRootElementNode by lazy {
      val rootName =
        schema?.name?.takeIf { it.isNotEmpty() }
          ?: recordInstance.form_id.takeIf { it.isNotEmpty() }
          ?: "data"
      InstanceRootElementNode(
        name = rootName,
        parent = this,
        recordNode = recordInstance.data_ ?: RecordNode(),
        metadata = recordInstance.metadata,
        schemaFields = schema?.fields ?: emptyList(),
        aliases = setOf(rootName, "data", recordInstance.form_id).filter { it.isNotEmpty() }.toSet(),
      )
    }

    override fun children(nameFilter: String?): List<XPathNode> {
      if (nameFilter == null || instanceElementNode.matchesName(nameFilter)) {
        return listOf(instanceElementNode)
      }
      return emptyList()
    }

    override fun extractValue(): XPathValue = instanceElementNode.extractValue()
  }

  /**
   * Top-level primary instance element node (e.g. `<data>` or `<household>`). Matches any of its
   * [aliases] so expressions using `/data/field` or `/household/field` both work.
   */
  class InstanceRootElementNode(
    override val name: String,
    override val parent: XPathNode,
    val recordNode: RecordNode,
    val metadata: RecordMetadata?,
    val schemaFields: List<FieldDefinition>,
    val aliases: Set<String>,
  ) : XPathNode {
    override val repeatIndex: Int = 1
    override val siblingRepeatCount: Int = 1
    override val schemaType: DataType = DataType.TYPE_MESSAGE

    fun matchesName(candidate: String): Boolean = candidate == name || aliases.contains(candidate)

    private val schemaMap: Map<String, FieldDefinition> by lazy {
      schemaFields.associateBy { it.name }
    }

    override fun children(nameFilter: String?): List<XPathNode> {
      val result = mutableListOf<XPathNode>()

      // 1. Expose <meta> block if requested or wildcard
      if (
        metadata != null && (nameFilter == null || nameFilter == "meta" || nameFilter == "orx:meta")
      ) {
        result.add(MetadataGroupNode(this, metadata))
      }

      // 2. Combine populated fields and unpopulated schema fields in deterministic order
      val allKeys = linkedSetOf<String>()
      schemaFields.forEach { allKeys.add(it.name) }
      recordNode.fields.keys.forEach { allKeys.add(it) }

      for (key in allKeys) {
        if (nameFilter != null && key != nameFilter) continue
        val fv = recordNode.fields[key]
        val fieldDef = schemaMap[key]
        result.addAll(createChildNodes(this, key, fv, fieldDef))
      }
      return result
    }

    override fun extractValue(): XPathValue {
      val childStrings =
        children(null).map { it.extractValue().toXPathString() }.filter { it.isNotEmpty() }
      return XPathValue.Str(childStrings.joinToString(" "))
    }
  }

  /** Structured group or repeat item container node wrapping a [RecordNode]. */
  class GroupOrRepeatNode(
    override val name: String,
    override val parent: XPathNode,
    val recordNode: RecordNode,
    val schemaFields: List<FieldDefinition> = emptyList(),
    override val repeatIndex: Int = 1,
    override val siblingRepeatCount: Int = 1,
  ) : XPathNode {
    override val schemaType: DataType = DataType.TYPE_MESSAGE

    private val schemaMap: Map<String, FieldDefinition> by lazy {
      schemaFields.associateBy { it.name }
    }

    override fun children(nameFilter: String?): List<XPathNode> {
      val result = mutableListOf<XPathNode>()
      val allKeys = linkedSetOf<String>()
      schemaFields.forEach { allKeys.add(it.name) }
      recordNode.fields.keys.forEach { allKeys.add(it) }

      for (key in allKeys) {
        if (nameFilter != null && key != nameFilter) continue
        val fv = recordNode.fields[key]
        val fieldDef = schemaMap[key]
        result.addAll(createChildNodes(this, key, fv, fieldDef))
      }
      return result
    }

    override fun extractValue(): XPathValue {
      val childStrings =
        children(null).map { it.extractValue().toXPathString() }.filter { it.isNotEmpty() }
      return XPathValue.Str(childStrings.joinToString(" "))
    }
  }

  /** Leaf field node wrapping a populated or default empty scalar/list [FieldValue]. */
  class LeafFieldNode(
    override val name: String,
    override val parent: XPathNode,
    val fieldValue: FieldValue?,
    override val schemaType: DataType? = null,
    override val repeatIndex: Int = 1,
    override val siblingRepeatCount: Int = 1,
  ) : XPathNode {
    override fun children(nameFilter: String?): List<XPathNode> = emptyList()

    override fun extractValue(): XPathValue = XPathValue.fromFieldValue(fieldValue)
  }

  /**
   * Virtual `<meta>` container node exposing [RecordMetadata] fields as standard XPath children.
   */
  class MetadataGroupNode(override val parent: XPathNode, val metadata: RecordMetadata) :
    XPathNode {
    override val name: String = "meta"
    override val repeatIndex: Int = 1
    override val siblingRepeatCount: Int = 1
    override val schemaType: DataType = DataType.TYPE_MESSAGE

    override fun children(nameFilter: String?): List<XPathNode> {
      val fields = mutableListOf<Pair<String, TypedValue>>()
      if (metadata.instance_id.isNotEmpty()) {
        fields.add("instanceID" to TypedValue(string_value = metadata.instance_id))
      }
      metadata.start_time?.let { fields.add("timeStart" to TypedValue(timestamp_value = it)) }
      metadata.end_time?.let { fields.add("timeEnd" to TypedValue(timestamp_value = it)) }
      metadata.today?.let { fields.add("today" to TypedValue(timestamp_value = it)) }
      if (metadata.device_id.isNotEmpty()) {
        fields.add("deviceID" to TypedValue(string_value = metadata.device_id))
      }
      if (metadata.subscriber_id.isNotEmpty()) {
        fields.add("subscriberID" to TypedValue(string_value = metadata.subscriber_id))
      }
      if (metadata.sim_serial.isNotEmpty()) {
        fields.add("simSerial" to TypedValue(string_value = metadata.sim_serial))
      }
      if (metadata.phone_number.isNotEmpty()) {
        fields.add("phoneNumber" to TypedValue(string_value = metadata.phone_number))
      }
      return fields
        .filter { nameFilter == null || it.first.equals(nameFilter, ignoreCase = true) }
        .map { (fieldName, tv) ->
          LeafFieldNode(name = fieldName, parent = this, fieldValue = FieldValue(scalar_value = tv))
        }
    }

    override fun extractValue(): XPathValue = XPathValue.Str(metadata.instance_id)
  }

  /**
   * Virtual node representing the root or item rows of a Secondary Instance lookup dataset
   * (`instance('id')/root/item[...]`).
   */
  class SecondaryInstanceNode(
    override val name: String,
    override val parent: XPathNode?,
    val childrenProvider: (XPathNode, String?) -> List<XPathNode>,
    val value: XPathValue = XPathValue.Str(""),
    override val repeatIndex: Int = 1,
    override val siblingRepeatCount: Int = 1,
    val rowAttributes: Map<String, TypedValue> = emptyMap(),
  ) : XPathNode {
    override val schemaType: DataType? = null

    override fun children(nameFilter: String?): List<XPathNode> = childrenProvider(this, nameFilter)

    override fun extractValue(): XPathValue = value
  }

  companion object {
    /** Wraps a [RecordInstance] in a virtual document tree ready for XPath evaluation. */
    fun fromRecordInstance(
      recordInstance: RecordInstance,
      schema: RecordSchema? = null,
    ): DocumentRootNode = DocumentRootNode(recordInstance, schema)

    internal fun createChildNodes(
      parent: XPathNode,
      name: String,
      fieldValue: FieldValue?,
      fieldDef: FieldDefinition?,
    ): List<XPathNode> {
      if (fieldValue == null) {
        // Unpopulated field in schema: if it's a repeat, it has 0 items; otherwise 1 empty node
        if (fieldDef?.is_repeated == true) {
          return emptyList()
        }
        if (fieldDef?.type == DataType.TYPE_MESSAGE) {
          return listOf(
            GroupOrRepeatNode(
              name = name,
              parent = parent,
              recordNode = RecordNode(),
              schemaFields = fieldDef.fields,
            )
          )
        }
        return listOf(
          LeafFieldNode(
            name = name,
            parent = parent,
            fieldValue = null,
            schemaType = fieldDef?.type,
          )
        )
      }

      return when {
        fieldValue.repeat_value != null -> {
          val nodes = fieldValue.repeat_value.nodes
          val total = nodes.size
          nodes.mapIndexed { idx, recordNode ->
            GroupOrRepeatNode(
              name = name,
              parent = parent,
              recordNode = recordNode,
              schemaFields = fieldDef?.fields ?: emptyList(),
              repeatIndex = idx + 1,
              siblingRepeatCount = total,
            )
          }
        }
        fieldValue.node_value != null -> {
          listOf(
            GroupOrRepeatNode(
              name = name,
              parent = parent,
              recordNode = fieldValue.node_value,
              schemaFields = fieldDef?.fields ?: emptyList(),
            )
          )
        }
        else -> {
          listOf(
            LeafFieldNode(
              name = name,
              parent = parent,
              fieldValue = fieldValue,
              schemaType = fieldDef?.type,
            )
          )
        }
      }
    }
  }
}
