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
package org.groundplatform.v2.map

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Extra tap tolerance around points and lines, so thin features stay tappable. */
internal val HIT_SLOP = 6.dp

/**
 * Returns the topmost feature under [tap], checking layers from top to bottom, or `null` if the
 * background was hit. Geometry is projected with [project]; sizes come from each layer's style.
 * [at] is the tap's geographic position, reported in the event.
 */
internal fun hitTest(
  content: MapContent,
  tap: DpOffset,
  project: (LatLng) -> DpOffset,
  at: LatLng,
): MapEvent.FeatureTapped? {
  val sources = content.sources.associateBy { it.id }
  for (layer in content.layers.asReversed()) {
    val features = sources[layer.sourceId]?.features ?: continue
    for (feature in features.asReversed()) {
      if (layer.filter?.matches(feature) == false) continue
      if (layerHits(layer, feature, tap, project)) {
        return MapEvent.FeatureTapped(layer.id, feature.id, at)
      }
    }
  }
  return null
}

private fun layerHits(
  layer: MapLayer,
  feature: MapFeature,
  tap: DpOffset,
  project: (LatLng) -> DpOffset,
): Boolean {
  val geometry = feature.geometry
  return when (layer) {
    is MapLayer.Fill ->
      geometry is Geometry.Polygon && polygonContains(geometry.rings.first().map(project), tap)
    is MapLayer.Line -> {
      val tolerance = layer.width.evaluate(feature.properties).value / 2 + HIT_SLOP.value
      lineRings(geometry).any { ring -> nearPolyline(ring.map(project), tap, tolerance) }
    }
    is MapLayer.Circle ->
      geometry is Geometry.Point &&
        distance(project(geometry.position), tap) <=
          (layer.radius.evaluate(feature.properties) + HIT_SLOP).value
    is MapLayer.Symbol ->
      geometry is Geometry.Point &&
        distance(project(geometry.position), tap) <= SYMBOL_HIT_RADIUS.value
  }
}

internal val SYMBOL_HIT_RADIUS = 16.dp

/** Vertex lists a line layer strokes, with polygon rings closed. */
internal fun lineRings(geometry: Geometry): List<List<LatLng>> =
  when (geometry) {
    is Geometry.Point -> emptyList()
    is Geometry.LineString -> listOf(geometry.points)
    is Geometry.Polygon -> geometry.rings.map { it + it.first() }
  }

internal fun distance(a: DpOffset, b: DpOffset): Float {
  val dx = a.x.value - b.x.value
  val dy = a.y.value - b.y.value
  return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** Whether [p] is within [tolerance] dp of any segment of [points]. */
internal fun nearPolyline(points: List<DpOffset>, p: DpOffset, tolerance: Float): Boolean =
  points.zipWithNext().any { (a, b) -> distanceToSegment(p, a, b) <= tolerance }

internal fun distanceToSegment(p: DpOffset, a: DpOffset, b: DpOffset): Float {
  val abx = b.x.value - a.x.value
  val aby = b.y.value - a.y.value
  val lengthSq = abx * abx + aby * aby
  if (lengthSq == 0f) return distance(p, a)
  val t =
    (((p.x.value - a.x.value) * abx + (p.y.value - a.y.value) * aby) / lengthSq).coerceIn(0f, 1f)
  return distance(p, DpOffset((a.x.value + t * abx).dp, (a.y.value + t * aby).dp))
}

/** Even-odd ray casting; [ring] need not repeat its first vertex. */
internal fun polygonContains(ring: List<DpOffset>, p: DpOffset): Boolean {
  var inside = false
  var j = ring.lastIndex
  for (i in ring.indices) {
    val yi = ring[i].y.value
    val yj = ring[j].y.value
    val xi = ring[i].x.value
    val xj = ring[j].x.value
    if (
      (yi > p.y.value) != (yj > p.y.value) &&
        p.x.value < (xj - xi) * (p.y.value - yi) / (yj - yi) + xi
    ) {
      inside = !inside
    }
    j = i
  }
  return inside
}
