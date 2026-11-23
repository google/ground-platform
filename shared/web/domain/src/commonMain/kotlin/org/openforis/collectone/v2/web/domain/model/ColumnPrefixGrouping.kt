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
package org.openforis.collectone.v2.web.domain.model

private val PREFIX_DELIMITERS = charArrayOf('_', '-', '.', ':', '/')

/** Default minimum number of columns sharing a prefix before they collapse into a group. */
const val DEFAULT_MIN_PREFIX_GROUP_SIZE = 6

/**
 * A collapsible group of columns sharing a common delimiter-terminated prefix (for example
 * `whisp_*` for WHISP enrichment fields).
 */
data class ColumnPrefixGroup<T>(
  /** Stable lowercase toggle key (e.g. `whisp_`). */
  val id: String,
  /** Display prefix with trailing delimiter in first-seen casing (e.g. `whisp_`). */
  val prefix: String,
  /** Grouped columns in their original table order. */
  val items: List<T>,
) {
  /** Compact wildcard header/chip label (e.g. `whisp_*`). */
  val wildcardLabel: String
    get() = "$prefix*"

  /** Number of columns contained in this group. */
  val size: Int
    get() = items.size
}

/** One rendered slot in a table that collapses wide common-prefix column groups. */
sealed interface GroupedTableColumn<out T> {
  /** Stable key suitable for retaining per-column UI state (such as resized widths). */
  val slotKey: String

  /** An ordinary ungrouped column or an expanded member of a [ColumnPrefixGroup]. */
  data class Single<T>(
    val item: T,
    val key: String,
    /** Non-null when this column is part of an expanded [ColumnPrefixGroup]. */
    val groupId: String? = null,
    /** True on the first column of an expanded group so its header can render a collapse button. */
    val isGroupStart: Boolean = false,
    /** Total column count in the expanded group (set when [groupId] != null). */
    val groupSize: Int = 0,
    /** Wildcard label of the expanded group (e.g. `whisp_*`). */
    val groupLabel: String = "",
  ) : GroupedTableColumn<T> {
    override val slotKey: String
      get() = "col:$key"
  }

  /** A single placeholder slot representing a collapsed [ColumnPrefixGroup]. */
  data class CollapsedGroup<T>(val group: ColumnPrefixGroup<T>) : GroupedTableColumn<T> {
    override val slotKey: String
      get() = "group:${group.id}"
  }
}

/** Result of grouping table columns by common delimiter-terminated prefix. */
data class GroupedColumnsLayout<T>(
  /** All detected collapsible groups in order of first appearance. */
  val groups: List<ColumnPrefixGroup<T>>,
  /** Ordered visible slots after expanding any group whose ID is in `expandedGroupIds`. */
  val displaySlots: List<GroupedTableColumn<T>>,
) {
  /** Returns the index in [displaySlots] of the first expanded column of [groupId], or `-1`. */
  fun firstExpandedSlotIndex(groupId: String): Int = displaySlots.indexOfFirst {
    it is GroupedTableColumn.Single && it.groupId == groupId
  }

  /** Returns the index in [displaySlots] of the collapsed placeholder for [groupId], or `-1`. */
  fun collapsedSlotIndex(groupId: String): Int = displaySlots.indexOfFirst {
    it is GroupedTableColumn.CollapsedGroup && it.group.id == groupId
  }
}

/**
 * Extracts the leading delimiter-terminated prefix token from [key] (e.g. `"whisp_Area"` →
 * `"whisp_"`, `"s2.b04"` → `"s2."`), or `null` if [key] has no qualifying prefix.
 */
fun leadingPrefixToken(key: String): String? {
  val trimmed = key.trim()
  if (trimmed.isEmpty() || !trimmed[0].isLetter()) return null
  var i = 1
  while (i < trimmed.length && trimmed[i].isLetterOrDigit()) i++
  val hasTrailingName = i < trimmed.length - 1 && trimmed[i] in PREFIX_DELIMITERS
  return if (hasTrailingName) trimmed.substring(0, i + 1) else null
}

/**
 * Extends [basePrefix] to the longest delimiter-terminated prefix shared across every column name
 * in [keys] (case-insensitive), keeping first-seen casing (e.g. `ee_ndvi_2020..2026` → `ee_ndvi_`).
 */
private fun refineSharedPrefix(basePrefix: String, keys: List<String>): String {
  if (keys.size <= 1) return basePrefix
  val first = keys[0]
  val maxCommon = keys.minOf { it.length } - 1
  var lastDelimiterEnd = basePrefix.length
  var i = basePrefix.length
  while (i < maxCommon) {
    val ch = first[i]
    val matchesAll = keys.all { it[i].equals(ch, ignoreCase = true) }
    if (!matchesAll) break
    if (ch in PREFIX_DELIMITERS) lastDelimiterEnd = i + 1
    i++
  }
  return first.substring(0, lastDelimiterEnd)
}

/**
 * Groups [columns] when at least [minGroupSize] groupable columns share a leading
 * delimiter-terminated prefix (such as `whisp_*`).
 *
 * Groups appear at the position of their first member. Any group whose [ColumnPrefixGroup.id] is
 * not in [expandedGroupIds] collapses into a single [GroupedTableColumn.CollapsedGroup] slot;
 * groups in [expandedGroupIds] expand in place as consecutive [GroupedTableColumn.Single] slots.
 */
fun <T> groupColumnsByCommonPrefix(
  columns: List<T>,
  keyOf: (T) -> String,
  expandedGroupIds: Set<String> = emptySet(),
  minGroupSize: Int = DEFAULT_MIN_PREFIX_GROUP_SIZE,
  isGroupable: (T) -> Boolean = { true },
): GroupedColumnsLayout<T> {
  if (columns.isEmpty()) return GroupedColumnsLayout(emptyList(), emptyList())
  val buckets = collectPrefixBuckets(columns, keyOf, isGroupable)
  val groupsById = buildQualifyingGroups(buckets, keyOf, minGroupSize)
  if (groupsById.isEmpty()) {
    val singles = columns.map { GroupedTableColumn.Single(item = it, key = keyOf(it)) }
    return GroupedColumnsLayout(emptyList(), singles)
  }
  val displaySlots = emitDisplaySlots(columns, keyOf, isGroupable, groupsById, expandedGroupIds)
  return GroupedColumnsLayout(groups = groupsById.values.toList(), displaySlots = displaySlots)
}

private fun <T> collectPrefixBuckets(
  columns: List<T>,
  keyOf: (T) -> String,
  isGroupable: (T) -> Boolean,
): LinkedHashMap<String, MutableList<T>> {
  val buckets = LinkedHashMap<String, MutableList<T>>()
  for (col in columns) {
    val prefix = if (isGroupable(col)) leadingPrefixToken(keyOf(col)) else null
    if (prefix != null) {
      buckets.getOrPut(prefix.lowercase()) { mutableListOf() }.add(col)
    }
  }
  return buckets
}

private fun <T> buildQualifyingGroups(
  buckets: Map<String, List<T>>,
  keyOf: (T) -> String,
  minGroupSize: Int,
): LinkedHashMap<String, ColumnPrefixGroup<T>> {
  val groups = LinkedHashMap<String, ColumnPrefixGroup<T>>()
  for ((id, items) in buckets) {
    if (items.size >= minGroupSize) {
      val rawKeys = items.map { keyOf(it).trim() }
      val basePrefix = leadingPrefixToken(rawKeys.first())
      if (basePrefix != null) {
        val sharedPrefix = refineSharedPrefix(basePrefix, rawKeys)
        groups[id] = ColumnPrefixGroup(id = id, prefix = sharedPrefix, items = items)
      }
    }
  }
  return groups
}

private fun <T> emitDisplaySlots(
  columns: List<T>,
  keyOf: (T) -> String,
  isGroupable: (T) -> Boolean,
  groupsById: Map<String, ColumnPrefixGroup<T>>,
  expandedGroupIds: Set<String>,
): List<GroupedTableColumn<T>> = buildList {
  val emittedGroups = HashSet<String>(groupsById.size)
  for (col in columns) {
    val key = keyOf(col)
    val groupId = if (isGroupable(col)) leadingPrefixToken(key)?.lowercase() else null
    val group = groupId?.let(groupsById::get)
    when {
      group == null -> add(GroupedTableColumn.Single(item = col, key = key))
      !emittedGroups.add(group.id) -> Unit
      group.id in expandedGroupIds ->
        group.items.forEachIndexed { idx, item ->
          add(
            GroupedTableColumn.Single(
              item = item,
              key = keyOf(item),
              groupId = group.id,
              isGroupStart = idx == 0,
              groupSize = group.size,
              groupLabel = group.wildcardLabel,
            )
          )
        }
      else -> add(GroupedTableColumn.CollapsedGroup(group))
    }
  }
}
