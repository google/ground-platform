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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/** Category of a feature rendered on the survey map that participates in zoomed-out clustering. */
enum class MapFeatureKind {
  ENTITY,
  SUBMISSION_GEOMETRY,
}

/**
 * Unified map feature representation (either a [GeospatialEntityItem] or a
 * [SubmissionGeometryPolygon]) used for spatial clustering when the map is zoomed out.
 */
data class MapClusterFeatureItem(
  val id: String,
  val kind: MapFeatureKind,
  val label: String,
  val markerSymbol: String,
  val colorHex: Long,
  val colorCss: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val entityId: String = "",
  val submissionId: String? = null,
) {
  /** True when this map feature has a non-blank `marker-symbol` (`"○"`, `"◐"`, `"✓"`, etc.). */
  val hasMarkerSymbol: Boolean
    get() = markerSymbol.isNotBlank()
}

/**
 * Represents a group of clustered map features sharing the same `marker-symbol` (including the `"no
 * marker symbol"` group where [markerSymbol] is `""`) inside a [MapFeatureCluster] balloon.
 */
data class ClusterMarkerSymbolGroup(
  val markerSymbol: String,
  val count: Int,
  val colorHex: Long,
  val colorCss: String,
  val statusLabel: String,
  val features: List<MapClusterFeatureItem>,
) {
  /** True when this group represents map features that have no marker symbol (`""`). */
  val isNoSymbolGroup: Boolean
    get() = markerSymbol.isBlank()

  /**
   * Formatted segment displayed in the cluster balloon (e.g. `"✓ 2"`, `"◐ 2"`, `"○ 1"`, or `"No
   * symbol 5"`).
   */
  val balloonSegmentLabel: String
    get() = if (isNoSymbolGroup) "No symbol $count" else "$markerSymbol $count"
}

/**
 * Groups [features] inside a cluster by `marker-symbol` (including `""` for features with no marker
 * symbol as one group), returning ordered [ClusterMarkerSymbolGroup] entries with their counts.
 */
fun groupClusterFeaturesByMarkerSymbol(
  features: List<MapClusterFeatureItem>
): List<ClusterMarkerSymbolGroup> {
  if (features.isEmpty()) return emptyList()
  val grouped = features.groupBy { it.markerSymbol.trim() }
  val canonicalOrder = listOf("✓", "◐", "○")
  val customSymbols = grouped.keys.filter { it.isNotEmpty() && it !in canonicalOrder }.sorted()
  val orderedKeys = buildList {
    canonicalOrder.forEach { sym -> if (sym in grouped) add(sym) }
    addAll(customSymbols)
    if ("" in grouped) add("")
  }

  return orderedKeys.map { symbol ->
    val members = grouped[symbol].orEmpty()
    val (defaultColorHex, statusLabel) =
      when (symbol) {
        "✓" -> 0xFF1E8E3EL to "Completed"
        "◐" -> 0xFFF9AB00L to "In progress"
        "○" -> 0xFFE65100L to "Pending"
        "" -> (members.firstOrNull()?.colorHex ?: 0xFF66BB6AL) to "No marker symbol"
        else -> (members.firstOrNull()?.colorHex ?: 0xFF2E7D32L) to "Symbol $symbol"
      }
    ClusterMarkerSymbolGroup(
      markerSymbol = symbol,
      count = members.size,
      colorHex = defaultColorHex,
      colorCss = formatHexColorCss(defaultColorHex),
      statusLabel = statusLabel,
      features = members,
    )
  }
}

/**
 * Spatial cluster of map features formed when the map is zoomed out, grouping member features by
 * `marker-symbol` (including no marker symbol as one group) and exposing the count of each for the
 * cluster balloon.
 */
data class MapFeatureCluster(
  val id: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val features: List<MapClusterFeatureItem>,
  val symbolGroups: List<ClusterMarkerSymbolGroup>,
) {
  /** Total number of map features in this cluster across all marker symbol groups. */
  val totalCount: Int
    get() = features.size

  /** Map feature (`MapFeatureKind.ENTITY`) features in this cluster. */
  val entityFeatures: List<MapClusterFeatureItem>
    get() = features.filter { it.kind == MapFeatureKind.ENTITY }

  /** Form submission geometry (`MapFeatureKind.SUBMISSION_GEOMETRY`) features in this cluster. */
  val submissionGeometryFeatures: List<MapClusterFeatureItem>
    get() = features.filter { it.kind == MapFeatureKind.SUBMISSION_GEOMETRY }

  /** Count of map features (`MapFeatureKind.ENTITY`) in this cluster. */
  val siteCount: Int
    get() = entityFeatures.size

  /** Count of form submission geometries (`MapFeatureKind.SUBMISSION_GEOMETRY`) in this cluster. */
  val submissionGeometryCount: Int
    get() = submissionGeometryFeatures.size

  /** Marker-symbol groups for **map features** (`MapFeatureKind.ENTITY`) only. */
  val siteSymbolGroups: List<ClusterMarkerSymbolGroup>
    get() = groupClusterFeaturesByMarkerSymbol(entityFeatures)

  /** Summary of map feature workflow states in this cluster (e.g. `"✓ 2 • ◐ 2 • ○ 1"`). */
  val siteStatesSummaryLabel: String
    get() = siteSymbolGroups.joinToString(" • ") { it.balloonSegmentLabel }

  /**
   * Map from `marker-symbol` (with `""` representing the no-marker-symbol group) to the count of
   * features in that group within this cluster.
   */
  val countsByMarkerSymbol: Map<String, Int>
    get() = symbolGroups.associate { it.markerSymbol to it.count }

  /** Count of features in this cluster that have no marker symbol (`markerSymbol == ""`). */
  val noMarkerSymbolCount: Int
    get() = countsByMarkerSymbol[""] ?: 0

  /** Returns the count of features in this cluster for [symbol] (`""` or `null` for no symbol). */
  fun countForSymbol(symbol: String?): Int = countsByMarkerSymbol[symbol?.trim().orEmpty()] ?: 0

  /**
   * Summary string of all marker symbol groups and their counts shown in the cluster balloon (e.g.
   * `"✓ 2 • ◐ 2 • ○ 1 • No symbol 5"`).
   */
  val balloonSummaryLabel: String
    get() = symbolGroups.joinToString(" • ") { it.balloonSegmentLabel }
}
