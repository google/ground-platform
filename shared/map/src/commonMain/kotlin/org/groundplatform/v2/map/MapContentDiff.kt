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

/** A single change a platform renderer applies to its native map. */
sealed interface MapOp {
  /**
   * Loads [basemap]. Native SDKs drop all sources, layers, and images when the style changes, so
   * [diffMapContent] always follows this with a full re-add.
   */
  data class SetBasemap(val basemap: Basemap) : MapOp

  data class AddIcon(val icon: MapIcon) : MapOp

  data class RemoveIcon(val iconId: String) : MapOp

  data class AddSource(val source: GeoJsonSource) : MapOp

  data class SetSourceData(val source: GeoJsonSource) : MapOp

  data class RemoveSource(val sourceId: String) : MapOp

  /** Adds [layer] directly below [beforeLayerId], or on top when it is `null`. */
  data class AddLayer(val layer: MapLayer, val beforeLayerId: String?) : MapOp

  /** Replaces the layer with the same id in place, keeping its position. */
  data class UpdateLayer(val layer: MapLayer) : MapOp

  data class RemoveLayer(val layerId: String) : MapOp

  data class SetUserLocation(val location: LatLng?) : MapOp
}

/**
 * Computes the ops that turn a native map showing [old] into one showing [new]; `null` means an
 * empty, freshly created map.
 *
 * Ops are ordered so each is valid when applied in sequence: layers are removed before their
 * sources, and sources and icons exist before layers that use them. [MapContent.markers] are not
 * included because [GroundMap] renders them in Compose.
 */
fun diffMapContent(old: MapContent?, new: MapContent): List<MapOp> {
  if (old == null || old.basemap != new.basemap) return fullBuild(new)
  if (old == new) return emptyList()

  val ops = mutableListOf<MapOp>()
  val oldLayers = old.layers.associateBy { it.id }
  val newLayers = new.layers.associateBy { it.id }

  // Layers that moved to another source are treated as removed and re-added.
  val kept =
    new.layers
      .map { it.id }
      .filter { id -> oldLayers[id]?.sourceId == newLayers.getValue(id).sourceId }
  val keptSet = kept.toSet()
  val keptInOldOrder = old.layers.map { it.id }.filter { it in keptSet }
  val reorder = keptInOldOrder != kept

  // Remove layers first, top to bottom.
  val removedLayers = if (reorder) old.layers else old.layers.filter { it.id !in keptSet }
  removedLayers.asReversed().forEach { ops += MapOp.RemoveLayer(it.id) }

  // Sources.
  val oldSources = old.sources.associateBy { it.id }
  val newSources = new.sources.associateBy { it.id }
  old.sources.filter { it.id !in newSources }.forEach { ops += MapOp.RemoveSource(it.id) }

  // Icons (an icon whose image changed is replaced).
  val oldIcons = old.icons.associateBy { it.id }
  val newIcons = new.icons.associateBy { it.id }
  old.icons.filter { newIcons[it.id] != it }.forEach { ops += MapOp.RemoveIcon(it.id) }
  new.icons.filter { oldIcons[it.id] != it }.forEach { ops += MapOp.AddIcon(it) }

  new.sources.forEach { source ->
    val previous = oldSources[source.id]
    when {
      previous == null -> ops += MapOp.AddSource(source)
      previous != source -> ops += MapOp.SetSourceData(source)
    }
  }

  if (reorder) {
    new.layers.forEach { ops += MapOp.AddLayer(it, beforeLayerId = null) }
  } else {
    kept
      .filter { oldLayers[it] != newLayers[it] }
      .forEach { ops += MapOp.UpdateLayer(newLayers.getValue(it)) }
    // Insert new layers top-down so each one's upper neighbour already exists.
    val ids = new.layers.map { it.id }
    for (i in ids.indices.reversed()) {
      val id = ids[i]
      if (id !in keptSet) {
        ops += MapOp.AddLayer(newLayers.getValue(id), beforeLayerId = ids.getOrNull(i + 1))
      }
    }
  }

  if (old.userLocation != new.userLocation) ops += MapOp.SetUserLocation(new.userLocation)
  return ops
}

private fun fullBuild(content: MapContent): List<MapOp> = buildList {
  add(MapOp.SetBasemap(content.basemap))
  content.icons.forEach { add(MapOp.AddIcon(it)) }
  content.sources.forEach { add(MapOp.AddSource(it)) }
  content.layers.forEach { add(MapOp.AddLayer(it, beforeLayerId = null)) }
  content.userLocation?.let { add(MapOp.SetUserLocation(it)) }
}
