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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShapeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.roundTo

/** The runtime collections of a survey after publishing a Survey editor draft. */
data class ProjectedSurveyData(
  val forms: List<FormPreviewItem>,
  val mapLayers: List<MapLayerItem>,
  val entities: List<GeospatialEntityItem>,
)

/**
 * Publishes a [SurveyEditorDraft] to the survey's runtime data (the inverse of
 * [SurveyEditorDerivation]): Forms become [FormPreviewItem]s, Map layers become [MapLayerItem]s,
 * and their rows become [GeospatialEntityItem]s. Data tables have no runtime counterpart in the
 * prototype and stay in the draft.
 *
 * Collected data is preserved: a map feature whose row is still in the draft keeps its submissions,
 * sync status, GeoID, and measurements, and only its label, properties, and (when moved) center
 * change. Features are only removed when the draft [previous]ly listed them and no longer does, so
 * features added by submissions since the draft was last saved are never wiped.
 */
object SurveyEditorProjection {
  fun project(
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft,
    surveyId: String,
    forms: List<FormPreviewItem>,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): ProjectedSurveyData {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val layersByKey = projectLayers(draft, mapLayers)
    return ProjectedSurveyData(
      forms = projectForms(draft, forms),
      mapLayers = layersByKey.values.toList(),
      entities = projectEntities(draft, previous, layersByKey, mapLayers, entities, anchor),
    )
  }

  private fun projectForms(
    draft: SurveyEditorDraft,
    forms: List<FormPreviewItem>,
  ): List<FormPreviewItem> {
    val existingById = forms.associateBy { it.id }
    return draft.forms.map { entry ->
      val form = entry.form
      val target =
        SaveToRules.saveTarget(form, draft.datasets.map { it.toEditorDataset(entry.key) })
      val existing = existingById[form.formId]
      val updates = form.saveTo.mode == SaveToMode.UPDATE && target != null
      FormPreviewItem(
        id = form.formId,
        title = form.title,
        description = existing?.description.orEmpty(),
        version = existing?.version ?: NEW_FORM_VERSION,
        targetDatasetId = target?.id.orEmpty(),
        targetDatasetName = target?.displayName.orEmpty(),
        questionCount = form.questions.size,
        ctaLabel = existing?.ctaLabel ?: form.title,
        requiresEntity = updates,
        availability = form.availability,
      )
    }
  }

  /** Runtime layer per Map layer dataset key, keeping the order of the draft. */
  private fun projectLayers(
    draft: SurveyEditorDraft,
    mapLayers: List<MapLayerItem>,
  ): Map<String, MapLayerItem> {
    val takenIds = mapLayers.map { it.id }.toMutableSet()
    return draft.datasets
      .filter { it.kind == DatasetKind.MAP_LAYER }
      .associate { dataset ->
        val existing = SurveyEditorDerivation.runtimeLayerFor(dataset, mapLayers)
        val geometryLabel = geometryLabel(dataset.geometryKind)
        val color = parseHexColor(dataset.style.colorHex)
        val layer =
          existing?.copy(
            label = dataset.displayName,
            colorHex = color ?: existing.colorHex,
            iconName = dataset.style.iconName,
            datasetId = dataset.id,
            geometryTypeLabel =
              if (shapeKind(dataset.geometryKind) == EntityShapeKind.of(existing.geometryTypeLabel))
                existing.geometryTypeLabel
              else geometryLabel,
          )
            ?: MapLayerItem(
              id = newLayerId(dataset, takenIds),
              label = dataset.displayName,
              sourceDescription = "Dataset: ${dataset.id} ($geometryLabel)",
              colorHex = color ?: DEFAULT_LAYER_COLOR,
              geometryTypeLabel = geometryLabel,
              isVisible = dataset.style.visibleByDefault,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel =
                dataset.displayName.lowercase().removeSuffix("s").ifBlank { "feature" },
              pluralItemLabel = dataset.displayName.lowercase().ifBlank { "features" },
              datasetId = dataset.id,
              iconName = dataset.style.iconName,
            )
        dataset.key to layer
      }
  }

  private fun projectEntities(
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft,
    layersByKey: Map<String, MapLayerItem>,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
    anchor: SurveyMapAnchor,
  ): List<GeospatialEntityItem> {
    val keptLayerIds = layersByKey.values.map { it.id }.toSet()
    val result = LinkedHashMap<String, GeospatialEntityItem>()
    // Features of deleted Map layers go with their layer; everything else starts out kept.
    entities.filter { it.layerId in keptLayerIds }.forEach { result[it.id] = it }
    val entitiesById = entities.associateBy { it.id }
    val previousIds =
      previous.datasets.associate { dataset ->
        dataset.key to dataset.rows.map { SurveyEditorDerivation.entityIdOf(dataset, it) }.toSet()
      }
    draft.datasets
      .filter { it.kind == DatasetKind.MAP_LAYER }
      .forEach { dataset ->
        val layer = layersByKey.getValue(dataset.key)
        val previousLayer = SurveyEditorDerivation.runtimeLayerFor(dataset, mapLayers)
        val layerEntities = entities.filter { it.layerId == (previousLayer?.id ?: layer.id) }
        val runtimeKeys = SurveyEditorDerivation.runtimeKeysFor(dataset, layerEntities)
        val dataProperties =
          dataset.properties.filterNot {
            it.name == dataset.keyProperty || it.name == dataset.labelProperty
          }
        val rowIds = mutableSetOf<String>()
        dataset.rows.forEach { row ->
          val id = SurveyEditorDerivation.entityIdOf(dataset, row)
          rowIds += id
          val existing = entitiesById[id]
          val label =
            row.values[dataset.labelProperty]?.takeIf { it.isNotBlank() }
              ?: existing?.label
              ?: dataset.labelOf(row)
          val properties =
            existing?.properties.orEmpty().filterKeys { it !in runtimeKeys.values } +
              dataProperties.mapNotNull { property ->
                row.values[property.name]?.let { runtimeKeys.getValue(property.name) to it }
              }
          val unchangedShape =
            existing != null &&
              row.geometry ==
                SurveyEditorDerivation.rowGeometry(existing, anchor, dataset.geometryKind)
          val center = if (unchangedShape) null else center(row.geometry, anchor)
          val geometryLabel =
            existing?.geometryTypeLabel?.takeIf {
              EntityShapeKind.of(it) == shapeKind(dataset.geometryKind)
            } ?: geometryLabel(dataset.geometryKind)
          result[id] =
            existing?.copy(
              label = label,
              datasetId = dataset.id,
              datasetName = dataset.displayName,
              layerId = layer.id,
              geometryTypeLabel = geometryLabel,
              normalizedX = center?.first ?: existing.normalizedX,
              normalizedY = center?.second ?: existing.normalizedY,
              properties = properties,
            )
              ?: GeospatialEntityItem(
                id = id,
                label = label,
                datasetId = dataset.id,
                datasetName = dataset.displayName,
                layerId = layer.id,
                geoId = "",
                geometryTypeLabel = geometryLabel,
                areaHectares = 0.0,
                perimeterMeters = 0,
                coordinatesLabel = coordinatesLabel(row.geometry),
                normalizedX = center?.first ?: 0.5f,
                normalizedY = center?.second ?: 0.5f,
                colorHex = layer.colorHex,
                properties = properties,
                submissions = emptyList(),
              )
        }
        val removable = previousIds[dataset.key].orEmpty()
        layerEntities.forEach { if (it.id !in rowIds && it.id in removable) result.remove(it.id) }
      }
    return result.values.toList()
  }

  private fun center(geometry: List<LatLng>, anchor: SurveyMapAnchor): Pair<Float, Float>? {
    if (geometry.isEmpty()) return null
    val lat = geometry.sumOf { it.lat } / geometry.size
    val lng = geometry.sumOf { it.lng } / geometry.size
    val (nx, ny) = anchor.toNormalized(org.groundplatform.v2.map.LatLng(lat, lng))
    return nx.roundTo(6).toFloat() to ny.roundTo(6).toFloat()
  }

  private fun coordinatesLabel(geometry: List<LatLng>): String {
    if (geometry.isEmpty()) return ""
    val lat = geometry.sumOf { it.lat } / geometry.size
    val lng = geometry.sumOf { it.lng } / geometry.size
    fun fmt(v: Double) = ((v * 10000).toLong() / 10000.0).toString()
    val ns = if (lat < 0) "S" else "N"
    val ew = if (lng < 0) "W" else "E"
    return "${fmt(kotlin.math.abs(lat))}°$ns, ${fmt(kotlin.math.abs(lng))}°$ew"
  }

  private fun newLayerId(dataset: EntityDataset, takenIds: MutableSet<String>): String {
    val base = "layer-" + dataset.id.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    var id = base
    var n = 2
    while (id in takenIds) id = "$base-${n++}"
    takenIds += id
    return id
  }

  private fun shapeKind(kind: GeometryKind): EntityShapeKind =
    when (kind) {
      GeometryKind.POINT -> EntityShapeKind.POINT
      GeometryKind.LINE -> EntityShapeKind.LINE
      GeometryKind.POLYGON -> EntityShapeKind.POLYGON
    }

  private fun geometryLabel(kind: GeometryKind): String =
    when (kind) {
      GeometryKind.POINT -> "Point"
      GeometryKind.LINE -> "LineString"
      GeometryKind.POLYGON -> "Polygon"
    }

  /** Version of Forms created in the editor; existing Forms keep their version. */
  const val NEW_FORM_VERSION = "1"

  /** [LayerStyle.colorHex] default as an ARGB long, used when a style color is malformed. */
  private val DEFAULT_LAYER_COLOR: Long = parseHexColor(LayerStyle().colorHex) ?: 0xFF2E7D32
}
