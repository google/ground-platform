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
package org.openforis.collectone.v2.app.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.EntityShape
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.computeAgStackGeoId
import org.openforis.collectone.v2.app.domain.model.matchesAgStackGeoId
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

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
 * clients yet and stay in the draft.
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
      entities =
        projectEntities(draft, previous, surveyId, layersByKey, mapLayers, entities, anchor),
    )
  }

  private fun projectForms(
    draft: SurveyEditorDraft,
    forms: List<FormPreviewItem>,
  ): List<FormPreviewItem> {
    val existingById = forms.associateBy { it.id }
    return draft.forms.map { entry ->
      val form = entry.form
      val catalog = draft.datasets.map { it.toEditorDataset(entry.key) }
      val target = SaveToRules.saveTarget(form, catalog)
      val existing = existingById[form.formId]
      val updates = form.saveTo.mode == SaveToMode.UPDATE && target != null
      // Like `ProtoSurveyMapper.formsFromProto`: Forms that update a feature pick it (the compiled
      // XForms gets a `target_entity` field), so the web dashboard offers them on that feature.
      val hasEntitySelect =
        updates ||
          (form.saveTo.idSource == EntityIdSource.QUESTION &&
            !form.saveTo.idQuestionKey.isNullOrBlank()) ||
          form.questions.any { q ->
            (q.choiceSource == ChoiceSource.MAP_LAYER &&
              (q.choiceDatasetId == target?.id || target == null)) || q.name == "target_entity"
          } ||
          existing?.hasEntitySelectQuestion == true
      FormPreviewItem(
        id = form.formId,
        title = form.title,
        description = existing?.description.orEmpty(),
        version = existing?.version ?: NEW_FORM_VERSION,
        targetDatasetId = target?.id.orEmpty(),
        targetDatasetName = target?.displayName.orEmpty(),
        questionCount = form.questions.size,
        ctaLabel = form.effectiveCtaLabel,
        requiresEntity = updates,
        availability = form.availability,
        hasEntitySelectQuestion = hasEntitySelect,
        targetTypeNoun =
          existing?.targetTypeNoun?.takeIf { existing.targetDatasetId == target?.id }.orEmpty(),
        openInGoogleEarth = form.openInGoogleEarth && form.canOpenInGoogleEarth(catalog),
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
        val color = parseHexColor(dataset.style.colorHex)
        val sampleDesign = dataset.generator?.toSampleDesign()
        val layer =
          existing?.copy(
            label = dataset.displayName,
            colorHex = color ?: existing.colorHex,
            iconName = dataset.style.iconName,
            datasetId = dataset.id,
            geometryType = dataset.geometryType,
            sampleDesign = sampleDesign,
          )
            ?: MapLayerItem(
              id = newLayerId(dataset, takenIds),
              label = dataset.displayName,
              colorHex = color ?: DEFAULT_LAYER_COLOR,
              geometryType = dataset.geometryType,
              isVisible = dataset.style.visibleByDefault,
              sourceType = LayerSourceType.ENTITY_DATASET,
              datasetId = dataset.id,
              iconName = dataset.style.iconName,
              sampleDesign = sampleDesign,
            )
        dataset.key to layer
      }
  }

  private fun projectEntities(
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft,
    surveyId: String,
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
        dataset.key to
          dataset.rows.map { SurveyEditorDerivation.entityIdOf(dataset, it, surveyId) }.toSet()
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
        val rowContext =
          LayerProjectionContext(
            dataset = dataset,
            layer = layer,
            surveyId = surveyId,
            entitiesById = entitiesById,
            runtimeKeys = runtimeKeys,
            dataProperties = dataProperties,
            anchor = anchor,
          )
        val rowIds = mutableSetOf<String>()
        dataset.rows.forEach { row ->
          val projected = projectEntityRow(row, rowContext)
          rowIds += projected.id
          result[projected.id] = projected
        }
        val removable = previousIds[dataset.key].orEmpty()
        layerEntities.forEach { if (it.id !in rowIds && it.id in removable) result.remove(it.id) }
      }
    return result.values.toList()
  }

  private class LayerProjectionContext(
    val dataset: EntityDataset,
    val layer: MapLayerItem,
    val surveyId: String,
    val entitiesById: Map<String, GeospatialEntityItem>,
    val runtimeKeys: Map<String, String>,
    val dataProperties: List<EntityProperty>,
    val anchor: SurveyMapAnchor,
  )

  private fun projectEntityRow(
    row: EntityRow,
    ctx: LayerProjectionContext,
  ): GeospatialEntityItem {
    val dataset = ctx.dataset
    val rawKey = SurveyEditorDerivation.rawKeyOf(dataset, row)
    val id = SurveyEditorDerivation.entityIdOf(dataset, row, ctx.surveyId)
    val existing = ctx.entitiesById[id]
    val label =
      row.values[dataset.labelProperty]?.takeIf { it.isNotBlank() }
        ?: existing?.label
        ?: dataset.labelOf(row)
    val keyEntry =
      if (rawKey != id && dataset.keyProperty.isNotBlank()) {
        mapOf(dataset.keyProperty to rawKey)
      } else {
        emptyMap()
      }
    val properties =
      existing?.properties.orEmpty().filterKeys {
        it !in ctx.runtimeKeys.values && it != dataset.keyProperty
      } +
        keyEntry +
        ctx.dataProperties.mapNotNull { property ->
          row.values[property.name]?.let { ctx.runtimeKeys.getValue(property.name) to it }
        }
    val geometry = projectedGeometry(row, dataset, existing, ctx.anchor)
    val geoId = projectedGeoId(row, geometry, existing)
    return existing?.copy(
      label = label,
      datasetId = dataset.id,
      datasetName = dataset.displayName,
      layerId = ctx.layer.id,
      geoId = geoId,
      geometry = geometry,
      properties = properties,
      isGenerated = dataset.isGenerated,
    )
      ?: GeospatialEntityItem(
        id = id,
        label = label,
        datasetId = dataset.id,
        datasetName = dataset.displayName,
        layerId = ctx.layer.id,
        geoId = geoId,
        geometry = geometry,
        areaHectares = 0.0,
        perimeterMeters = 0,
        colorHex = ctx.layer.colorHex,
        properties = properties,
        submissions = emptyList(),
        isGenerated = dataset.isGenerated,
      )
  }

  /**
   * Resolves the AgStack GeoID for a projected [row]: keeps [existing]'s GeoID when its geometry is
   * unchanged and already matches a valid AgStack candidate (including a server-confirmed level-20
   * fallback), else uses a valid geometry-matching imported GeoID from `row.values`, else computes
   * the offline primary candidate from [geometry].
   */
  private fun projectedGeoId(
    row: EntityRow,
    geometry: Geometry,
    existing: GeospatialEntityItem?,
  ): String {
    if (
      existing != null &&
        existing.geometry == geometry &&
        geometry.matchesAgStackGeoId(existing.geoId)
    ) {
      return existing.geoId
    }
    val importedGeoId =
      listOf("c1_geo_id", "geo_id", "geoid").firstNotNullOfOrNull { key ->
        row.values[key]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
      }
    if (importedGeoId != null && geometry.matchesAgStackGeoId(importedGeoId)) {
      return importedGeoId
    }
    return geometry.computeAgStackGeoId()
  }

  /**
   * The geometry of a projected [row]: the [existing] feature's when its shape is unchanged, else
   * the row's vertices, else (a row without vertices) a placeholder shape at the existing feature's
   * position or the survey's center.
   */
  private fun projectedGeometry(
    row: EntityRow,
    dataset: EntityDataset,
    existing: GeospatialEntityItem?,
    anchor: SurveyMapAnchor,
  ): Geometry {
    val type = dataset.geometryType
    val unchangedShape =
      existing != null && row.geometry == SurveyEditorDerivation.rowGeometry(existing)
    return existing?.geometry?.takeIf { unchangedShape && it.type == type }
      ?: geometryOf(row.geometry, type)
      ?: EntityShape.placeholder(
        type = type,
        center = existing?.geometry?.representativePoint ?: anchor.center,
      )
  }

  /** A [type] geometry through an editor row's [vertices], or `null` when there are too few. */
  private fun geometryOf(vertices: List<LatLng>, type: GeometryType): Geometry? {
    val points = vertices
    return when (type) {
      GeometryType.POINT -> points.firstOrNull()?.let(Geometry::Point)
      GeometryType.LINE_STRING -> points.takeIf { it.size >= 2 }?.let(Geometry::LineString)
      GeometryType.POLYGON -> {
        val ring =
          if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points
        ring.takeIf { it.size >= 3 }?.let { Geometry.Polygon(listOf(it)) }
      }
    }
  }

  private fun newLayerId(dataset: EntityDataset, takenIds: MutableSet<String>): String {
    val base = "layer-" + dataset.id.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    var id = base
    var n = 2
    while (id in takenIds) id = "$base-${n++}"
    takenIds += id
    return id
  }

  /** Version of Forms created in the editor; existing Forms keep their version. */
  const val NEW_FORM_VERSION = "1"

  /** [LayerStyle.colorHex] default as an ARGB long, used when a style color is malformed. */
  private val DEFAULT_LAYER_COLOR: Long = parseHexColor(LayerStyle().colorHex) ?: 0xFF2E7D32
}
