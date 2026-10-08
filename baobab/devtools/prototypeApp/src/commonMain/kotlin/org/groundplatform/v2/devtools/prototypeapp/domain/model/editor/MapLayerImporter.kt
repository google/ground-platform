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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import org.groundplatform.v2.core.geo.io.GeoFeature
import org.groundplatform.v2.core.geo.io.GeoGeometry
import org.groundplatform.v2.core.geo.io.GeoReadResult
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind

/**
 * A Map layer ready to be created from an uploaded GeoJSON or KML file: one geometry type, a
 * property per file attribute, and one row (without editor keys yet) per feature.
 */
data class MapLayerImportPlan(
  val name: String,
  val geometryKind: GeometryKind,
  val properties: List<EntityProperty>,
  val keyProperty: String,
  val labelProperty: String,
  val rows: List<Pair<Map<String, String>, List<LatLng>>>,
  /** Features skipped because their geometry type differs from [geometryKind]. */
  val skippedFeatures: Int,
)

/** Turns geometry read from a file into a [MapLayerImportPlan]. */
object MapLayerImporter {

  /**
   * Plans a Map layer for [result]. The layer's geometry type is the most specific one present
   * (polygons, then lines, then points); features of other types are skipped. Returns `null` if the
   * file has no usable features.
   */
  fun plan(fileName: String, result: GeoReadResult): MapLayerImportPlan? {
    val features = result.features
    if (features.isEmpty()) return null
    val kind =
      when {
        features.any { it.geometry is GeoGeometry.Polygon } -> GeometryKind.POLYGON
        features.any { it.geometry is GeoGeometry.LineString } -> GeometryKind.LINE
        else -> GeometryKind.POINT
      }
    val kept = features.filter { it.kind() == kind }

    // One property per attribute, with names made valid and unique.
    val names = LinkedHashMap<String, String>() // attribute → property name
    kept.forEach { f ->
      f.properties.keys.forEach { attr ->
        if (attr !in names) names[attr] = uniqueName(propertyName(attr), names.values)
      }
    }
    val idAttr = names.entries.firstOrNull { it.value == "id" }?.key
    val idValues = kept.map { it.properties[idAttr].orEmpty().trim() }
    val idIsUsableKey =
      idAttr != null && idValues.none { it.isEmpty() } && idValues.toSet().size == idValues.size
    val keyProperty = if (idIsUsableKey) "id" else uniqueName("feature_id", names.values)
    val properties = buildList {
      if (!idIsUsableKey) add(EntityProperty(keyProperty, "Feature ID", required = true))
      names.forEach { (attr, name) ->
        add(EntityProperty(name, attr.ifBlank { name }, required = name == keyProperty))
      }
    }
    val labelProperty = names.values.firstOrNull { it == "name" } ?: keyProperty
    val rows = kept.mapIndexed { i, f ->
      val values = buildMap {
        if (!idIsUsableKey) put(keyProperty, "F-${(i + 1).toString().padStart(4, '0')}")
        f.properties.forEach { (attr, v) -> put(names.getValue(attr), v) }
      }
      values to f.vertices()
    }
    return MapLayerImportPlan(
      name = fileName.substringBeforeLast('.').ifBlank { "Imported map layer" },
      geometryKind = kind,
      properties = properties,
      keyProperty = keyProperty,
      labelProperty = labelProperty,
      rows = rows,
      skippedFeatures = features.size - kept.size,
    )
  }

  private fun GeoFeature.kind(): GeometryKind =
    when (geometry) {
      is GeoGeometry.Point -> GeometryKind.POINT
      is GeoGeometry.LineString -> GeometryKind.LINE
      is GeoGeometry.Polygon -> GeometryKind.POLYGON
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

  private fun uniqueName(base: String, taken: Collection<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
  }
}
