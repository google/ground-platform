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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.math.abs
import kotlin.math.roundToLong
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityPresentationPropertyKeys
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShape
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ExportProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.map.LatLng

/** A downloadable export. */
data class ExportFile(val fileName: String, val mimeType: String, val content: String)

/**
 * Where one output field of an export profile gets its values: the property of the exported dataset
 * that carries [conceptId], or the feature geometry for geometry concepts.
 */
data class ProfileFieldMapping(
  val outputField: String,
  val conceptId: String,
  /** The concept's label, for warnings. */
  val label: String,
  /** Dataset property holding the values, or `null` when nothing in the survey is linked. */
  val propertyName: String? = null,
  /** Whether the field is the feature geometry. */
  val isGeometry: Boolean = false,
  /** Choice value → code-list code, from the linked question's `ground_code`s. */
  val choiceCodes: Map<String, String> = emptyMap(),
) {
  val isMapped: Boolean
    get() = isGeometry || propertyName != null
}

/** An export of one dataset through [profile]: how each output field maps, and any warnings. */
data class ProfileExportPlan(
  val profile: ExportProfile,
  val datasetId: String,
  val fields: List<ProfileFieldMapping>,
  val warnings: List<String>,
) {
  val unmappedFields: List<ProfileFieldMapping>
    get() = fields.filterNot { it.isMapped }
}

/**
 * Exports a survey's map layers and data tables as GeoJSON or through an export profile (see
 * `docs/technical/model/data/04-impact-events.md`, "Export Profiles"), and records each export as
 * an [ImpactEventType.EXPORT] event.
 *
 * Profile fields are located through concept links: a profile field's concept is linked to a
 * question (survey-level links), and the question saves to a dataset property, which inherits the
 * concept. Geometry concepts map to the feature geometry.
 */
class ExportSurveyDataUseCase(private val recordImpactEvent: RecordImpactEventUseCase? = null) {
  /**
   * Export profiles enabled by the survey's purposes [purposeIds] (their Purpose Packs'
   * `export_profile_ids`), from the survey's resolved [library].
   */
  fun enabledProfiles(library: ResolvedLibrary, purposeIds: List<String>): List<ExportProfile> =
    purposeIds
      .mapNotNull(library::purposePack)
      .flatMap { it.exportProfileIds }
      .distinct()
      .mapNotNull { id -> library.exportProfiles.firstOrNull { it.id == id } }

  /** How [profile]'s fields map onto [dataset] (with its [forms]), and what to warn about. */
  fun plan(
    profile: ExportProfile,
    dataset: EntityDataset,
    forms: List<SurveyEditorForm>,
    library: ResolvedLibrary,
    entities: List<GeospatialEntityItem>,
    language: String = "en",
  ): ProfileExportPlan {
    val fields =
      profile.fieldConcepts.map { (outputField, conceptId) ->
        val concept = library.concept(conceptId)
        val label = concept?.label?.get(language)?.ifBlank { null } ?: outputField
        if (concept?.dataType?.isGeometry == true) {
          ProfileFieldMapping(outputField, conceptId, label, isGeometry = dataset.hasGeometry)
        } else {
          val (property, question) = linkedProperty(conceptId, dataset, forms)
          ProfileFieldMapping(
            outputField = outputField,
            conceptId = conceptId,
            label = label,
            propertyName = property,
            choiceCodes =
              question
                ?.choices
                ?.mapNotNull { c -> c.code?.let { c.value to it } }
                ?.toMap()
                .orEmpty(),
          )
        }
      }
    val warnings = buildList {
      val datasetEntities = entities.filter { it.datasetId == dataset.id }
      if (datasetEntities.isEmpty())
        add("${dataset.displayName} has no map features to export yet.")
      val unmapped = fields.filterNot { it.isMapped }
      val areaFallback = unmapped.filter { it.conceptId == AREA_CONCEPT_ID }
      val empty = unmapped - areaFallback.toSet()
      if (empty.isNotEmpty()) {
        add(
          "Not linked to a standard field in this survey, so left empty: " +
            empty.joinToString { it.label } +
            "."
        )
      }
      if (areaFallback.isNotEmpty()) {
        add("${areaFallback.first().label} isn't linked, so each plot's mapped area is used.")
      }
      val largePoints = datasetEntities.count {
        it.geometryKind == EntityGeometryKind.POINT && it.areaHectares > EUDR_POINT_MAX_HA
      }
      if (largePoints > 0) {
        add(
          "$largePoints ${if (largePoints == 1) "plot is" else "plots are"} over 4 ha but mapped " +
            "as a point. EUDR expects a boundary for plots over 4 ha."
        )
      }
    }
    return ProfileExportPlan(profile, dataset.id, fields, warnings)
  }

  /**
   * [plan]'s GeoJSON `FeatureCollection` of [entities] in [dataset]: WGS84 coordinates rounded to 6
   * decimal places, boundaries as polygons and point plots as points, with one property per mapped
   * profile field (select answers written as their standard codes).
   */
  fun profileGeoJson(
    plan: ProfileExportPlan,
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
    anchor: SurveyMapAnchor,
  ): ExportFile {
    val features =
      entities
        .filter { it.datasetId == dataset.id }
        .map { entity ->
          val properties =
            plan.fields
              .filterNot { it.isGeometry }
              .map { field ->
                val value =
                  when {
                    field.propertyName != null -> {
                      val raw = propertyValue(entity, dataset, field.propertyName)
                      field.choiceCodes[raw] ?: raw
                    }
                    field.conceptId == AREA_CONCEPT_ID && entity.areaHectares > 0 ->
                      formatDecimal(entity.areaHectares, 4)
                    else -> ""
                  }
                field.outputField to value
              }
          feature(entity, anchor, properties)
        }
    return ExportFile(
      fileName = fileName("${dataset.displayName} ${plan.profile.title.text}", "geojson"),
      mimeType = GEOJSON_MIME,
      content = featureCollection(features),
    )
  }

  /** A plain GeoJSON `FeatureCollection` of [entities] in [dataset] with all their properties. */
  fun geoJson(
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
    anchor: SurveyMapAnchor,
  ): ExportFile {
    val features =
      entities
        .filter { it.datasetId == dataset.id }
        .map { entity ->
          val properties =
            listOf("id" to entity.id) +
              entity.properties.filterKeys { it !in EntityPresentationPropertyKeys }.toList()
          feature(entity, anchor, properties)
        }
    return ExportFile(
      fileName = fileName(dataset.displayName, "geojson"),
      mimeType = GEOJSON_MIME,
      content = featureCollection(features),
    )
  }

  /**
   * Records an export of [entities] from [surveyId] (through [exportProfileId], if any) as an
   * [ImpactEventType.EXPORT] event covering them, counting plots that share a GeoID once.
   */
  suspend fun recordExport(
    surveyId: String,
    entities: List<GeospatialEntityItem>,
    exportProfileId: String? = null,
  ) {
    recordImpactEvent?.invoke(
      type = ImpactEventType.EXPORT,
      surveyId = surveyId,
      coverage = ImpactCoverage.of(entities),
      exportProfileId = exportProfileId,
    )
  }

  // --- Mapping ---

  /**
   * The property of [dataset] that holds [conceptId], and the question linked to it: a property
   * that inherited the concept, else the property a linked question of [forms] saves to.
   */
  private fun linkedProperty(
    conceptId: String,
    dataset: EntityDataset,
    forms: List<SurveyEditorForm>,
  ): Pair<String?, EditorQuestion?> {
    val linkedQuestions = forms.flatMap { entry ->
      entry.form.questions.filter { it.conceptLink?.conceptId == conceptId }.map { entry to it }
    }
    dataset.properties
      .firstOrNull { it.conceptLink?.conceptId == conceptId }
      ?.let { property ->
        val question = linkedQuestions.firstOrNull { it.second.name == property.name }?.second
        return property.name to (question ?: linkedQuestions.firstOrNull()?.second)
      }
    for ((entry, question) in linkedQuestions) {
      val form = entry.form
      val property =
        when {
          dataset.linkedFormKey == entry.key && form.saveTo.mode == SaveToMode.CREATE ->
            question.name.takeIf { name -> dataset.properties.any { it.name == name } }
          form.saveTo.mode == SaveToMode.UPDATE && form.saveTo.targetDatasetId == dataset.id ->
            form.saveTo.mappings.firstOrNull { it.questionKey == question.key }?.property
          else -> null
        }
      if (property != null) return property to question
    }
    return null to null
  }

  /** [entity]'s value of [propertyName] (map features may key properties by their labels). */
  private fun propertyValue(
    entity: GeospatialEntityItem,
    dataset: EntityDataset,
    propertyName: String,
  ): String =
    entity.properties[propertyName]
      ?: dataset.properties
        .firstOrNull { it.name == propertyName }
        ?.let { entity.properties[it.label] }
      ?: ""

  // --- GeoJSON ---

  private fun feature(
    entity: GeospatialEntityItem,
    anchor: SurveyMapAnchor,
    properties: List<Pair<String, String>>,
  ): String {
    val geometry = geometry(entity, anchor)
    val props = properties.joinToString(",") { (k, v) -> "${jsonString(k)}:${jsonString(v)}" }
    return "{\"type\":\"Feature\",\"id\":${jsonString(entity.id)},\"geometry\":$geometry," +
      "\"properties\":{$props}}"
  }

  private fun geometry(entity: GeospatialEntityItem, anchor: SurveyMapAnchor): String {
    val kind = entity.geometryKind
    if (kind == EntityGeometryKind.NONE) return "null"
    val vertices = EntityShape.vertices(entity, anchor)
    return when (kind) {
      EntityGeometryKind.POINT ->
        "{\"type\":\"Point\",\"coordinates\":${position(vertices.first())}}"
      EntityGeometryKind.LINE ->
        "{\"type\":\"LineString\",\"coordinates\":[${vertices.joinToString(",", transform = ::position)}]}"
      EntityGeometryKind.POLYGON -> {
        val ring =
          if (vertices.first() == vertices.last()) vertices else vertices + vertices.first()
        "{\"type\":\"Polygon\",\"coordinates\":[[${ring.joinToString(",", transform = ::position)}]]}"
      }
      EntityGeometryKind.NONE -> "null"
    }
  }

  /** A GeoJSON position, `[longitude, latitude]` in WGS84 with 6 decimal places (about 0.1 m). */
  private fun position(point: LatLng): String =
    "[${formatDecimal(point.longitude, 6)},${formatDecimal(point.latitude, 6)}]"

  private fun featureCollection(features: List<String>): String =
    "{\"type\":\"FeatureCollection\",\"features\":[${features.joinToString(",")}]}"

  companion object {
    /** Plots up to this size may be given as a point under EUDR; larger ones need a boundary. */
    const val EUDR_POINT_MAX_HA = 4.0

    private const val AREA_CONCEPT_ID = "core.area_ha"
    private const val GEOJSON_MIME = "application/geo+json"

    private val ConceptDataType.isGeometry: Boolean
      get() =
        this == ConceptDataType.POINT ||
          this == ConceptDataType.LINE ||
          this == ConceptDataType.POLYGON

    private val EntityDataset.hasGeometry: Boolean
      get() = kind == DatasetKind.MAP_LAYER

    /** [value] with exactly [decimals] decimal places, without locale-dependent formatting. */
    fun formatDecimal(value: Double, decimals: Int): String {
      var scale = 1L
      repeat(decimals) { scale *= 10 }
      val scaled = (abs(value) * scale).roundToLong()
      val sign = if (value < 0 && scaled != 0L) "-" else ""
      val whole = scaled / scale
      val fraction = (scaled % scale).toString().padStart(decimals, '0')
      return if (decimals == 0) "$sign$whole" else "$sign$whole.$fraction"
    }

    /** [text] as a JSON string literal. */
    fun jsonString(text: String): String {
      val builder = StringBuilder("\"")
      text.forEach { c ->
        when {
          c == '"' -> builder.append("\\\"")
          c == '\\' -> builder.append("\\\\")
          c == '\n' -> builder.append("\\n")
          c == '\r' -> builder.append("\\r")
          c == '\t' -> builder.append("\\t")
          c < ' ' -> builder.append("\\u").append(c.code.toString(16).padStart(4, '0'))
          else -> builder.append(c)
        }
      }
      return builder.append('"').toString()
    }

    /** A file name from [title] (characters invalid in file names replaced) and [extension]. */
    fun fileName(title: String, extension: String): String {
      val base =
        title.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trim('.').ifEmpty { "export" }
      return "$base.$extension"
    }
  }
}
