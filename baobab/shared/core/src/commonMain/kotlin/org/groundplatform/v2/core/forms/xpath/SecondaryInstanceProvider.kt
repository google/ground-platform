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
package org.groundplatform.v2.core.forms.xpath

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Service Provider Interface (SPI) for resolving secondary lookup datasets (`instance('id')` and
 * `pulldata('id', ...)`).
 *
 * Supports both:
 * 1. **Equality Predicate Pushdown (`lookupByEquality`)**: Allows hosted or database-backed
 *    runtimes (such as SQLite or IndexedDB Entity Datasets) to answer `pulldata()` and
 *    `instance('id')/root/item[key = val]` queries in $O(1)$ or $O(\log N)$ time without loading
 *    the entire dataset into memory.
 * 2. **Virtual Tree Traversal (`resolveRoot`)**: Provides a fallback virtual [XPathNode] tree for
 *    arbitrary XPath axes and complex non-equality filter expressions.
 */
interface SecondaryInstanceProvider {

  /**
   * Performs an indexed equality lookup for rows where `row[keyField] == keyValue`.
   *
   * @return List of matching rows (each a map from column name to [TypedValue]), or `null` if the
   *   provider does not support equality pushdown for this instance/column (falling back to
   *   [resolveRoot]).
   */
  fun lookupByEquality(
    instanceId: String,
    keyField: String,
    keyValue: String,
  ): List<Map<String, TypedValue>>? = null

  /**
   * Resolves the virtual root node for `instance(instanceId)`. Standard XForms secondary instances
   * expose `root/item` (or direct `item`) children under this node.
   */
  fun resolveRoot(instanceId: String): XPathNode?

  companion object {
    val EMPTY =
      object : SecondaryInstanceProvider {
        override fun resolveRoot(instanceId: String): XPathNode? = null
      }
  }
}

/**
 * In-memory implementation of [SecondaryInstanceProvider] backed by pre-indexed tables or parsed
 * inline CSV/key-value datasets from [FormDef].
 */
class InMemorySecondaryInstanceProvider(
  private val tables: Map<String, List<Map<String, TypedValue>>> = emptyMap()
) : SecondaryInstanceProvider {

  // Secondary index: instanceId -> keyField -> keyValue -> List<Row>
  private val equalityIndex:
    Map<String, Map<String, Map<String, List<Map<String, TypedValue>>>>> by lazy {
    tables.mapValues { (_, rows) ->
      val columns = rows.flatMap { it.keys }.toSet()
      columns.associateWith { col ->
        rows.groupBy { row -> XPathValue.fromTypedValue(row[col]).toXPathString() }
      }
    }
  }

  override fun lookupByEquality(
    instanceId: String,
    keyField: String,
    keyValue: String,
  ): List<Map<String, TypedValue>>? {
    val instanceIdx = equalityIndex[instanceId] ?: return null
    val colIdx = instanceIdx[keyField] ?: return emptyList()
    return colIdx[keyValue] ?: emptyList()
  }

  override fun resolveRoot(instanceId: String): XPathNode? {
    val rows = tables[instanceId] ?: return null
    return createSecondaryInstanceRootNode(instanceId, rows)
  }

  companion object {
    /** Builds an [InMemorySecondaryInstanceProvider] from string key-value row maps. */
    fun fromStringTables(
      tables: Map<String, List<Map<String, String>>>
    ): InMemorySecondaryInstanceProvider {
      val typedTables = tables.mapValues { (_, rows) ->
        rows.map { row -> row.mapValues { (_, v) -> TypedValue(string_value = v) } }
      }
      return InMemorySecondaryInstanceProvider(typedTables)
    }

    /** Parses inline CSV datasets from `FormDef.model.secondary_instances`. */
    fun fromFormDef(formDef: FormDef): InMemorySecondaryInstanceProvider {
      val secondaryInstances = formDef.model?.secondary_instances ?: emptyList()
      val parsed = mutableMapOf<String, List<Map<String, TypedValue>>>()
      for (sec in secondaryInstances) {
        if (sec.id.isNotEmpty() && sec.inline_data.isNotEmpty()) {
          parsed[sec.id] = parseInlineData(sec)
        }
      }
      return InMemorySecondaryInstanceProvider(parsed)
    }

    private fun parseInlineData(sec: SecondaryInstance): List<Map<String, TypedValue>> {
      val trimmed = sec.inline_data.trim()
      if (trimmed.isEmpty()) return emptyList()
      if (trimmed.startsWith("<")) {
        try {
          val rootEl =
            org.groundplatform.v2.core.forms.serialization.xml.XmlParser.parse(trimmed)
          val itemElements =
            rootEl.childrenNamed("item").ifEmpty {
              if (rootEl.localName == "item") listOf(rootEl) else rootEl.childElements
            }
          return itemElements.mapNotNull { itemEl ->
            if (itemEl.childElements.isEmpty()) {
              null
            } else {
              itemEl.childElements.associate { colEl ->
                colEl.localName to TypedValue(string_value = colEl.textContent.trim())
              }
            }
          }
        } catch (_: Exception) {
          // Fall back to CSV parser if XML parsing fails
        }
      }
      val lines =
        sec.inline_data.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
      if (lines.isEmpty()) return emptyList()
      val delimiter =
        when {
          lines.first().contains('\t') -> '\t'
          lines.first().contains(';') -> ';'
          else -> ','
        }
      val headers = splitCsvLine(lines.first(), delimiter)
      return lines.drop(1).map { line ->
        val values = splitCsvLine(line, delimiter)
        headers
          .mapIndexed { idx, col -> col to TypedValue(string_value = values.getOrElse(idx) { "" }) }
          .toMap()
      }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
      val result = mutableListOf<String>()
      val sb = StringBuilder()
      var inQuotes = false
      var i = 0
      while (i < line.length) {
        val c = line[i]
        when {
          c == '"' -> {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
              sb.append('"')
              i++
            } else {
              inQuotes = !inQuotes
            }
          }
          c == delimiter && !inQuotes -> {
            result.add(sb.toString().trim())
            sb.clear()
          }
          else -> sb.append(c)
        }
        i++
      }
      result.add(sb.toString().trim())
      return result
    }

    /**
     * Creates a virtual `XPathNode` hierarchy for a secondary instance table. Supports both
     * `instance('id')/root/item[...]` and direct `instance('id')/item[...]` paths.
     */
    internal fun createSecondaryInstanceRootNode(
      instanceId: String,
      rows: List<Map<String, TypedValue>>,
    ): XPathNode {
      lateinit var instanceRootNode: XPathNode.SecondaryInstanceNode
      lateinit var virtualRootElement: XPathNode.SecondaryInstanceNode

      fun buildItemNodes(parentNode: XPathNode): List<XPathNode> {
        val total = rows.size
        return rows.mapIndexed { idx, rowMap ->
          lateinit var itemNode: XPathNode.SecondaryInstanceNode
          val colNodes by lazy {
            rowMap.map { (colName, typedVal) ->
              XPathNode.SecondaryInstanceNode(
                name = colName,
                parent = itemNode,
                childrenProvider = { _, _ -> emptyList() },
                value = XPathValue.fromTypedValue(typedVal),
              )
            }
          }
          itemNode =
            XPathNode.SecondaryInstanceNode(
              name = "item",
              parent = parentNode,
              childrenProvider = { _, nameFilter ->
                if (nameFilter == null) colNodes else colNodes.filter { it.name == nameFilter }
              },
              value =
                XPathValue.Str(
                  rowMap.values.joinToString(" ") { XPathValue.fromTypedValue(it).toXPathString() }
                ),
              repeatIndex = idx + 1,
              siblingRepeatCount = total,
              rowAttributes = rowMap,
            )
          itemNode
        }
      }

      val itemNodesUnderRootElement by lazy { buildItemNodes(virtualRootElement) }

      virtualRootElement =
        XPathNode.SecondaryInstanceNode(
          name = "root",
          parent = null,
          childrenProvider = { _, nameFilter ->
            if (nameFilter == null || nameFilter == "item") itemNodesUnderRootElement
            else emptyList()
          },
        )

      val itemNodesDirect by lazy { buildItemNodes(instanceRootNode) }

      instanceRootNode =
        XPathNode.SecondaryInstanceNode(
          name = instanceId,
          parent = null,
          childrenProvider = { _, nameFilter ->
            when (nameFilter) {
              null -> listOf(virtualRootElement)
              "root" -> listOf(virtualRootElement)
              "item" -> itemNodesDirect
              else -> emptyList()
            }
          },
        )
      return instanceRootNode
    }
  }
}
