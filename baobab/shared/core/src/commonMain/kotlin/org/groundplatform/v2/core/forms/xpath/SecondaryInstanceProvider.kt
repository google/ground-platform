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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
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
    private val lenientJson = Json {
      ignoreUnknownKeys = true
      isLenient = true
    }

    /** Builds an [InMemorySecondaryInstanceProvider] from string key-value row maps. */
    fun fromStringTables(
      tables: Map<String, List<Map<String, String>>>
    ): InMemorySecondaryInstanceProvider {
      val typedTables = tables.mapValues { (_, rows) ->
        rows.map { row -> row.mapValues { (_, v) -> TypedValue(string_value = v) } }
      }
      return InMemorySecondaryInstanceProvider(typedTables)
    }

    /** Builds an [InMemorySecondaryInstanceProvider] from RFC 7946 GeoJSON datasets by ID. */
    fun fromGeoJson(datasets: Map<String, String>): InMemorySecondaryInstanceProvider {
      val typedTables = datasets.mapValues { (_, jsonText) -> parseGeoJsonData(jsonText) }
      return InMemorySecondaryInstanceProvider(typedTables)
    }

    /** Parses inline CSV, XML, or GeoJSON datasets from `FormDef.model.secondary_instances`. */
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
      if (trimmed.startsWith("{")) {
        val geoRows = parseGeoJsonData(trimmed)
        if (geoRows.isNotEmpty()) return geoRows
      }
      if (trimmed.startsWith("<")) {
        try {
          val rootEl = org.groundplatform.v2.core.forms.serialization.xml.XmlParser.parse(trimmed)
          val itemElements =
            rootEl.childrenNamed("item").ifEmpty {
              if (rootEl.localName == "item") listOf(rootEl) else rootEl.childElements
            }
          val multiRowItems = itemElements.mapNotNull { itemEl ->
            if (itemEl.childElements.isEmpty()) {
              null
            } else {
              itemEl.childElements.associate { colEl ->
                colEl.localName to TypedValue(string_value = colEl.textContent.trim())
              }
            }
          }
          if (multiRowItems.isNotEmpty()) {
            return multiRowItems
          }
          // Single-record XML fallback (e.g., <data><item>val</item><count>3</count></data>)
          if (rootEl.childElements.isNotEmpty()) {
            return listOf(
              rootEl.childElements.associate { colEl ->
                colEl.localName to TypedValue(string_value = colEl.textContent.trim())
              }
            )
          }
          return emptyList()
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

    internal fun parseGeoJsonData(jsonText: String): List<Map<String, TypedValue>> {
      val rootObj =
        try {
          lenientJson.parseToJsonElement(jsonText) as? JsonObject
        } catch (_: Exception) {
          null
        } ?: return emptyList()

      val typeStr = (rootObj["type"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
      val features: List<JsonObject> =
        when (typeStr) {
          "FeatureCollection" ->
            (rootObj["features"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
          "Feature" -> listOf(rootObj)
          else -> emptyList()
        }

      return features.map { feature ->
        val row = linkedMapOf<String, TypedValue>()
        val props = feature["properties"] as? JsonObject
        if (props != null) {
          for ((k, v) in props) {
            if (v is JsonPrimitive && v !is JsonNull) {
              row[k] = TypedValue(string_value = v.content)
            }
          }
        }
        val topId = (feature["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        if (!topId.isNullOrEmpty() && "id" !in row) {
          row["id"] = TypedValue(string_value = topId)
        }
        val geomObj = feature["geometry"] as? JsonObject
        if (geomObj != null && "geometry" !in row) {
          val odkGeom = formatGeoJsonGeometryToOdk(geomObj)
          if (odkGeom.isNotEmpty()) {
            row["geometry"] = TypedValue(string_value = odkGeom)
          }
        }
        row
      }
    }

    private fun formatGeoJsonGeometryToOdk(geomObj: JsonObject): String {
      val geomType =
        (geomObj["type"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: return ""
      val coords = geomObj["coordinates"] as? JsonArray ?: return ""
      fun formatCoord(coordArr: JsonArray): String? {
        if (coordArr.size < 2) return null
        val lon = (coordArr[0] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (coordArr[1] as? JsonPrimitive)?.doubleOrNull ?: return null
        val alt =
          if (coordArr.size >= 3) (coordArr[2] as? JsonPrimitive)?.doubleOrNull ?: 0.0 else 0.0
        val latStr = if (lat == lat.toLong().toDouble()) lat.toLong().toString() else lat.toString()
        val lonStr = if (lon == lon.toLong().toDouble()) lon.toLong().toString() else lon.toString()
        val altStr = if (alt == alt.toLong().toDouble()) alt.toLong().toString() else alt.toString()
        return "$latStr $lonStr $altStr 0"
      }
      return when (geomType) {
        "Point" -> formatCoord(coords) ?: ""
        "LineString" ->
          coords.mapNotNull { (it as? JsonArray)?.let(::formatCoord) }.joinToString("; ")
        "Polygon" -> {
          val outerRing = coords.firstOrNull() as? JsonArray ?: return ""
          outerRing.mapNotNull { (it as? JsonArray)?.let(::formatCoord) }.joinToString("; ")
        }
        else -> ""
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
     * Creates a virtual `XPathNode` hierarchy for a secondary instance table. Supports
     * `instance('id')/root/item[...]`, direct `instance('id')/item[...]`, custom XML container
     * paths (e.g. `instance('choices')/counties/county[...]`), and single-record paths (e.g.
     * `instance('last-saved')/data/field`).
     */
    internal fun createSecondaryInstanceRootNode(
      instanceId: String,
      rows: List<Map<String, TypedValue>>,
    ): XPathNode {
      lateinit var instanceRootNode: XPathNode.SecondaryInstanceNode
      val allColumnNames: Set<String> by lazy { rows.flatMap { it.keys }.toSet() }

      fun buildItemNodes(parentNode: XPathNode, itemName: String = "item"): List<XPathNode> {
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
              name = itemName,
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

      fun buildContainerElement(containerName: String): XPathNode.SecondaryInstanceNode {
        lateinit var containerElement: XPathNode.SecondaryInstanceNode
        val defaultItems by lazy { buildItemNodes(containerElement, "item") }
        val customItemsCache = mutableMapOf<String, List<XPathNode>>()
        containerElement =
          XPathNode.SecondaryInstanceNode(
            name = containerName,
            parent = instanceRootNode,
            childrenProvider = { _, nameFilter ->
              when {
                nameFilter == null -> defaultItems
                nameFilter == "item" && containerName == "root" -> defaultItems
                nameFilter in allColumnNames -> {
                  rows.mapNotNull { rowMap ->
                    rowMap[nameFilter]?.let { typedVal ->
                      XPathNode.SecondaryInstanceNode(
                        name = nameFilter,
                        parent = containerElement,
                        childrenProvider = { _, _ -> emptyList() },
                        value = XPathValue.fromTypedValue(typedVal),
                      )
                    }
                  }
                }
                nameFilter == "item" -> defaultItems
                else ->
                  customItemsCache.getOrPut(nameFilter) {
                    buildItemNodes(containerElement, nameFilter)
                  }
              }
            },
          )
        return containerElement
      }

      val virtualRootElement by lazy { buildContainerElement("root") }
      val customContainersCache = mutableMapOf<String, XPathNode.SecondaryInstanceNode>()
      val itemNodesDirect by lazy { buildItemNodes(instanceRootNode, "item") }

      instanceRootNode =
        XPathNode.SecondaryInstanceNode(
          name = instanceId,
          parent = null,
          childrenProvider = { _, nameFilter ->
            when (nameFilter) {
              null,
              "root" -> listOf(virtualRootElement)
              "item" -> itemNodesDirect
              else ->
                listOf(
                  customContainersCache.getOrPut(nameFilter) { buildContainerElement(nameFilter) }
                )
            }
          },
        )
      return instanceRootNode
    }
  }
}
