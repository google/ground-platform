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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.FormEditorValidator
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.core.geo.io.GeoFeature
import org.openforis.collectone.v2.core.geo.io.GeoGeometry
import org.openforis.collectone.v2.core.geo.io.GeoReadResult
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * A Map layer ready to be created from an uploaded GeoJSON or KML file: one geometry type, a
 * property per file attribute, and one row (without editor keys yet) per feature.
 */
data class MapLayerImportPlan(
  val name: String,
  val geometryType: GeometryType,
  val properties: List<EntityProperty>,
  val keyProperty: String,
  val labelProperty: String,
  val rows: List<Pair<Map<String, String>, List<LatLng>>>,
  /** Features skipped because their geometry type differs from [geometryType]. */
  val skippedFeatures: Int,
)

/** Turns geometry read from a file into a [MapLayerImportPlan]. */
object MapLayerImporter {
  /** Internal `<Data>` attributes written by Collect One's KML exporter (`KmlBalloonWriter`). */
  private val KML_SYSTEM_ATTRIBUTES =
    setOf(
      "c1_entity_id",
      "c1_dataset_id",
      "c1_geo_id",
      "c1_revision",
      "c1_updated_at",
      "c1_props_json",
    )

  /**
   * Plans a Map layer for [result]. The layer's geometry type is the most specific one present
   * (polygons, then lines, then points); features of other types are skipped. Returns `null` if the
   * file has no usable features.
   */
  fun plan(fileName: String, result: GeoReadResult): MapLayerImportPlan? {
    val features = result.features
    if (features.isEmpty()) return null
    val kind = selectGeometryKind(features)
    val kept = features.filter { it.kind() == kind }
    val names = buildAttributeNameMap(kept)
    val idAttr = names.entries.firstOrNull { it.value == "id" }?.key
    val idIsUsableKey = isUsableIdAttribute(kept, idAttr)
    val keyProperty = if (idIsUsableKey) "id" else uniqueName("feature_id", names.values)
    val properties = buildList {
      if (!idIsUsableKey) add(EntityProperty(keyProperty, "Feature ID", required = true))
      names.forEach { (attr, name) ->
        add(EntityProperty(name, attr.ifBlank { name }, required = name == keyProperty))
      }
    }
    val labelProperty = names.values.firstOrNull { it == "name" } ?: keyProperty
    val rows = kept.mapIndexed { i, f -> buildImportedRow(i, f, names, keyProperty, idIsUsableKey) }
    return MapLayerImportPlan(
      name = fileName.substringBeforeLast('.').ifBlank { "Imported map layer" },
      geometryType = kind,
      properties = properties,
      keyProperty = keyProperty,
      labelProperty = labelProperty,
      rows = rows,
      skippedFeatures = features.size - kept.size,
    )
  }

  private fun selectGeometryKind(features: List<GeoFeature>): GeometryType =
    when {
      features.any { it.geometry is GeoGeometry.Polygon } -> GeometryType.POLYGON
      features.any { it.geometry is GeoGeometry.LineString } -> GeometryType.LINE_STRING
      else -> GeometryType.POINT
    }

  private fun buildAttributeNameMap(kept: List<GeoFeature>): LinkedHashMap<String, String> {
    // One property per attribute, with names made valid and unique.
    val names = LinkedHashMap<String, String>() // attribute → property name
    kept.forEach { f ->
      f.properties.keys
        .filterNot { it in KML_SYSTEM_ATTRIBUTES }
        .forEach { attr ->
          if (attr !in names) names[attr] = uniqueName(propertyName(attr), names.values)
        }
    }
    return names
  }

  private fun isUsableIdAttribute(kept: List<GeoFeature>, idAttr: String?): Boolean {
    if (idAttr == null) return false
    val idValues = kept.map { it.properties[idAttr].orEmpty().trim() }
    return idValues.none { it.isEmpty() } && idValues.toSet().size == idValues.size
  }

  private fun buildImportedRow(
    index: Int,
    feature: GeoFeature,
    names: Map<String, String>,
    keyProperty: String,
    idIsUsableKey: Boolean,
  ): Pair<Map<String, String>, List<LatLng>> {
    val values = buildMap {
      if (!idIsUsableKey) put(keyProperty, "F-${(index + 1).toString().padStart(4, '0')}")
      feature.properties.forEach { (attr, v) ->
        if (attr in names) {
          put(names.getValue(attr), v)
        } else if (attr == "c1_geo_id" && v.isNotBlank()) {
          put("c1_geo_id", v)
        }
      }
    }
    return values to feature.vertices()
  }

  private fun GeoFeature.kind(): GeometryType =
    when (geometry) {
      is GeoGeometry.Point -> GeometryType.POINT
      is GeoGeometry.LineString -> GeometryType.LINE_STRING
      is GeoGeometry.Polygon -> GeometryType.POLYGON
    }

  private fun GeoFeature.vertices(): List<LatLng> =
    when (val g = geometry) {
      is GeoGeometry.Point -> listOf(g.coord.toLatLng())
      is GeoGeometry.LineString -> g.coords.map { it.toLatLng() }
      is GeoGeometry.Polygon -> g.ring.map { it.toLatLng() }
    }

  /** A valid property name for file attribute [attr] (letters, digits, `_ . -`). */
  internal fun propertyName(attr: String): String {
    val cleaned =
      attr
        .trim()
        .lowercase()
        .map { if (it.isLetterOrDigit() || it in "_.-") it else '_' }
        .joinToString("")
    val name = if (cleaned.firstOrNull()?.isLetter() == true) cleaned else "p_$cleaned"
    return if (name == "geometry" || !FormEditorValidator.isValidName(name)) "${name}_" else name
  }

  internal fun uniqueName(base: String, taken: Collection<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
  }
}
