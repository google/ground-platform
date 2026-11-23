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
package org.openforis.collectone.v2.core.sync

import openforis.collectone.v1.data.EntityRecord

/**
 * Applies `ListEntitiesRequest.field_mask` (and `ScopeCursor.field_mask`) to [EntityRecord]s.
 *
 * An empty mask returns the full record unchanged. A non-empty mask keeps identity and sync
 * bookkeeping (`entity_id`, `dataset_id`, `survey_id`, `revision`, `commit_timestamp`,
 * `is_deleted`) plus only the named fields. Property entries are selected either as `"properties"`
 * (every property) or individually as `"properties.<key>"`.
 */
object EntityFieldMask {
  const val PROPERTIES_FIELD = "properties"
  const val PROPERTIES_PREFIX = "properties."

  /**
   * Core map, workflow, and sample-plot property keys kept on lightweight entity syncs so the web
   * console can render the map, status chips, and plot identity without loading hundreds of tabular
   * or enrichment columns for every row into memory.
   */
  val CORE_PROPERTY_KEYS: List<String> =
    listOf(
      "status",
      "marker-color",
      "marker-symbol",
      "stroke",
      "fill",
      "plot_id",
      "stratum",
      "sample_order",
      "inclusion_weight",
      "samples",
      "sample_ids",
    )

  /**
   * Top-level metadata and geometry fields kept by lightweight web syncs (everything except
   * unrequested entries of `properties`).
   */
  val METADATA_FIELDS: List<String> =
    listOf(
      "entity_id",
      "dataset_id",
      "survey_id",
      "revision",
      "label",
      "geometry",
      "point",
      "trace",
      "shape",
      "audit_info",
      "is_field_created",
      "is_deleted",
      "s2_cell_token",
      "commit_timestamp",
      "active_lock",
      "quality_rollup",
      "unresolved_conflicts",
      "dataset_name",
      "layer_id",
      "geo_id",
      "area_hectares",
      "perimeter_meters",
      "color_hex",
      "singular_type_label",
      "sync_status_key",
    )

  /**
   * Lightweight mask for web console syncs, with optional extra [propertyKeys] (e.g. linked
   * concepts).
   */
  fun forLightweightSync(extraPropertyKeys: Collection<String> = emptyList()): List<String> =
    buildList {
      addAll(METADATA_FIELDS)
      val seen = HashSet<String>()
      for (key in CORE_PROPERTY_KEYS) {
        if (seen.add(key)) add("$PROPERTIES_PREFIX$key")
      }
      for (key in extraPropertyKeys) {
        if (key.isNotBlank() && seen.add(key)) add("$PROPERTIES_PREFIX$key")
      }
    }

  /**
   * Compact mask for fetching one lazy table tile: identity, label, GeoID, optional geometry, and
   * only the requested [propertyKeys] (or all properties when [propertyKeys] is empty).
   */
  fun forTableTile(
    propertyKeys: Collection<String>,
    includeGeometry: Boolean = false,
  ): List<String> = buildList {
    add("entity_id")
    add("dataset_id")
    add("revision")
    add("label")
    add("geo_id")
    if (includeGeometry) {
      add("geometry")
      add("point")
      add("trace")
      add("shape")
    }
    if (propertyKeys.isEmpty()) {
      add(PROPERTIES_FIELD)
    } else {
      propertyKeys.forEach { key -> if (key.isNotBlank()) add("$PROPERTIES_PREFIX$key") }
    }
  }

  /** Minimal mask used when only counting entities or discovering dataset property keys. */
  val SCHEMA_ONLY_MASK: List<String> = listOf("entity_id")

  private data class ParsedMask(
    val fields: Set<String>,
    val includeAllProperties: Boolean,
    val propertyKeys: Set<String>?,
  ) {
    fun has(field: String): Boolean = field in fields

    fun filterProperties(properties: Map<String, openforis.collectone.v1.forms.TypedValue>) =
      when {
        includeAllProperties -> properties
        propertyKeys != null -> properties.filterKeys { it in propertyKeys }
        else -> emptyMap()
      }
  }

  private fun parseMask(fieldMask: List<String>): ParsedMask {
    val fields = HashSet<String>(fieldMask.size)
    var includeAllProperties = false
    var propertyKeys: HashSet<String>? = null
    for (raw in fieldMask) {
      val path = raw.trim()
      when {
        path.isEmpty() -> Unit
        path == PROPERTIES_FIELD -> includeAllProperties = true
        path.startsWith(PROPERTIES_PREFIX) -> {
          val key = path.removePrefix(PROPERTIES_PREFIX)
          if (key.isNotEmpty()) {
            val keys = propertyKeys ?: HashSet<String>().also { propertyKeys = it }
            keys.add(key)
          }
        }
        else -> fields.add(path)
      }
    }
    return ParsedMask(fields, includeAllProperties, propertyKeys)
  }

  private fun <T> ParsedMask.keepOr(field: String, value: T, default: T): T =
    if (has(field)) value else default

  /** Returns [record] trimmed to the fields named in [fieldMask], or unchanged when empty. */
  fun apply(record: EntityRecord, fieldMask: List<String>): EntityRecord {
    if (fieldMask.isEmpty()) return record
    val mask = parseMask(fieldMask)
    val keepGeometry = mask.has("geometry")
    return EntityRecord(
      entity_id = record.entity_id,
      dataset_id = record.dataset_id,
      revision = record.revision,
      label = mask.keepOr("label", record.label, ""),
      point = if (keepGeometry || mask.has("point")) record.point else null,
      trace = if (keepGeometry || mask.has("trace")) record.trace else null,
      shape = if (keepGeometry || mask.has("shape")) record.shape else null,
      properties = mask.filterProperties(record.properties),
      audit_info = mask.keepOr("audit_info", record.audit_info, null),
      is_field_created = mask.keepOr("is_field_created", record.is_field_created, false),
      is_deleted = record.is_deleted,
      s2_cell_token = mask.keepOr("s2_cell_token", record.s2_cell_token, ""),
      survey_id = record.survey_id,
      commit_timestamp = record.commit_timestamp,
      active_lock = mask.keepOr("active_lock", record.active_lock, null),
      quality_rollup = mask.keepOr("quality_rollup", record.quality_rollup, null),
      unresolved_conflicts =
        mask.keepOr("unresolved_conflicts", record.unresolved_conflicts, emptyList()),
      dataset_name = mask.keepOr("dataset_name", record.dataset_name, ""),
      layer_id = mask.keepOr("layer_id", record.layer_id, ""),
      geo_id = mask.keepOr("geo_id", record.geo_id, ""),
      area_hectares = mask.keepOr("area_hectares", record.area_hectares, 0.0),
      perimeter_meters = mask.keepOr("perimeter_meters", record.perimeter_meters, 0),
      color_hex = mask.keepOr("color_hex", record.color_hex, 0L),
      singular_type_label = mask.keepOr("singular_type_label", record.singular_type_label, ""),
      sync_status_key = mask.keepOr("sync_status_key", record.sync_status_key, ""),
      geometry = if (keepGeometry) record.geometry else null,
    )
  }
}
