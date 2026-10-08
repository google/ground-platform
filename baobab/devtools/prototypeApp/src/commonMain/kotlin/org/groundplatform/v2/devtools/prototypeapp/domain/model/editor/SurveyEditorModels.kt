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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind

/**
 * Defines the geographic survey area for the survey: one or more polygon parts (e.g. a mainland and
 * its islands), mirroring `SurveyArea.parts` (`repeated GeoShape`). Each part is an open ring (the
 * first vertex isn't repeated at the end). Holes aren't supported.
 *
 * [center] and [zoom] default to the bounding box of all parts.
 */
data class SurveyArea(
  val name: String,
  val parts: List<List<LatLng>>,
  val center: LatLng = SurveyAreaGeometry.boundsCenter(parts) ?: LatLng(0.0, 0.0),
  val zoom: Double = SurveyAreaGeometry.zoomToFit(parts),
  val sourceLabel: String = "Selected boundary",
) {
  /** Every vertex of every part. */
  val allVertices: List<LatLng>
    get() = parts.flatten()

  val vertexCount: Int
    get() = parts.sumOf { it.size }
}

/** Survey-level metadata (mirrors the descriptive fields of `SurveyDef`). */
data class SurveyDetails(
  val surveyId: String,
  val title: String,
  val description: String,
  val defaultLanguage: String = "en",
  val supportedLanguages: List<String> = listOf("en"),
  val surveyArea: SurveyArea? = null,
  /**
   * Organization this survey belongs to (`SurveyDef.organization_id`), or `null` for a personal
   * survey. Managers of the organization inherit survey organizer access.
   */
  val organizationId: String? = null,
)

/** Collaborator roles (mirrors `groundplatform.v2.survey.Role`). */
enum class CollaboratorRole(val label: String, val description: String) {
  VIEWER("Viewer", "Can view the map, Map layers, and submissions."),
  DATA_COLLECTOR("Data collector", "Can collect submissions and add map features."),
  SURVEY_ORGANIZER("Survey organizer", "Can edit Forms, manage sharing, and export data."),
}

/** A person on a survey's access control list (mirrors `AclEntry`). */
data class Collaborator(
  val email: String,
  val role: CollaboratorRole,
  val status: InvitationStatus = InvitationStatus.PENDING,
  /** Secret part of the invite link (`…/join/<token>`); cleared once accepted. */
  val inviteToken: String? = null,
  /** `AclEntry.user_id`, known only after the invitee signs in and accepts. */
  val userId: String? = null,
  val profile: CachedProfile? = null,
) {
  /** Full name when known, otherwise the email address. */
  val displayName: String
    get() = profile?.displayName?.takeIf { it.isNotBlank() } ?: email
}

/** Mirrors `groundplatform.v2.survey.SharingPolicy`. */
enum class SharingPolicy(val label: String, val description: String) {
  RESTRICTED("Restricted", "Only people added below can open the survey."),
  /** Only valid when the survey belongs to an organization ([SurveyDetails.organizationId]). */
  ORGANIZATION(
    "Anyone in the organization",
    "Members of the survey's organization can collect data. Managers can also edit the survey.",
  ),
  ANYONE_WITH_LINK("Anyone with the link", "Anyone with the link or QR code can collect data."),
  PUBLIC("Public", "Listed in the public directory. Anyone can collect data."),
}

/** Mirrors `groundplatform.v2.survey.PeerDataVisibility`. */
enum class PeerDataVisibility(val label: String, val description: String) {
  ALL("All data", "Data collectors see everyone's submissions and map features."),
  OWN_ONLY("Own data only", "Data collectors only see what they collected."),
}

data class SharingSettings(
  val ownerEmail: String,
  val policy: SharingPolicy = SharingPolicy.RESTRICTED,
  val peerDataVisibility: PeerDataVisibility = PeerDataVisibility.ALL,
  val collaborators: List<Collaborator> = emptyList(),
  val ownerProfile: CachedProfile? = null,
)

/** Whether an entity dataset is a spatial Map layer or a non-spatial Data table. */
enum class DatasetKind(val singular: String, val plural: String) {
  MAP_LAYER("Map layer", "Map layers"),
  DATA_TABLE("Data table", "Data tables"),
}

/** Geometry types allowed for Map layer features (`EntityDatasetDef.geometry_type`). */
enum class GeometryKind(val label: String, val dataType: String, val minVertices: Int) {
  POINT("Points", "TYPE_GEOPOINT", 1),
  LINE("Lines", "TYPE_GEOTRACE", 2),
  POLYGON("Polygons", "TYPE_GEOSHAPE", 3),
}

/**
 * Property (column) types supported in the editor (subset of `groundplatform.v2.forms.DataType`).
 */
enum class PropertyType(val label: String, val dataType: String) {
  TEXT("Text", "TYPE_STRING"),
  INTEGER("Integer", "TYPE_INT"),
  DECIMAL("Decimal", "TYPE_DECIMAL"),
  BOOLEAN("Yes / No", "TYPE_BOOLEAN"),
  DATE("Date", "TYPE_DATE");

  /** Returns true if [raw] is an acceptable cell value for this type (blank is always allowed). */
  fun accepts(raw: String): Boolean {
    val v = raw.trim()
    if (v.isEmpty()) return true
    return when (this) {
      TEXT -> true
      INTEGER -> v.toLongOrNull() != null
      DECIMAL -> v.toDoubleOrNull() != null
      BOOLEAN -> v.lowercase() in setOf("true", "false", "yes", "no")
      DATE -> Regex("""^\d{4}-\d{2}-\d{2}$""").matches(v)
    }
  }
}

/** Schema definition for one property (mirrors `EntityPropertyDefinition`). */
data class EntityProperty(
  val name: String,
  val label: String,
  val type: PropertyType = PropertyType.TEXT,
  val required: Boolean = false,
)

data class LatLng(val lat: Double, val lng: Double)

/** A single entity (row / map feature), mirroring `EntityRecord`. */
data class EntityRow(
  /** Stable editor-only identity. */
  val key: String,
  val values: Map<String, String> = emptyMap(),
  /** Vertices for Map layer features; empty for Data table rows. */
  val geometry: List<LatLng> = emptyList(),
)

/** Default map styling for a Map layer (mirrors `LayerDef` + `GeometryStyle`). */
data class LayerStyle(
  val colorHex: String = "#2E7D32",
  val strokeWidth: Double = 2.0,
  val fillOpacity: Double = 0.3,
  val visibleByDefault: Boolean = true,
  /**
   * Icon drawn inside the layer's point pins (a Material Symbols name such as `"park"`; see
   * `map/LayerIcons.kt`), or `null` for a plain pin. Only used by Point layers.
   */
  val iconName: String? = null,
)

/**
 * An entity dataset (`EntityDatasetDef`) plus its entities, editable as a Map layer or Data table.
 */
data class EntityDataset(
  val key: String,
  val kind: DatasetKind,
  val id: String,
  val displayName: String,
  val description: String = "",
  val geometryKind: GeometryKind = GeometryKind.POINT,
  val keyProperty: String,
  val labelProperty: String,
  val fieldCreationEnabled: Boolean = false,
  val linkedFormKey: String? = null,
  val properties: List<EntityProperty>,
  val rows: List<EntityRow> = emptyList(),
  val style: LayerStyle = LayerStyle(),
  /**
   * How this Map layer's features were generated (`EntityDatasetDef.generator`), or `null` for
   * hand-made and imported layers. Generated layers have their geometry locked.
   */
  val generator: SampleDesignConfig? = null,
) {
  val isLinkedToForm: Boolean
    get() = linkedFormKey != null

  /** Whether features are generated sample plots, whose geometry can't be edited by hand. */
  val isGenerated: Boolean
    get() = generator != null

  fun property(name: String): EntityProperty? = properties.firstOrNull { it.name == name }

  fun labelOf(row: EntityRow): String =
    row.values[labelProperty]?.takeIf { it.isNotBlank() }
      ?: row.values[keyProperty]?.takeIf { it.isNotBlank() }
      ?: "(unnamed)"
}

/** Problem detected in a dataset, optionally tied to a row. */
data class DatasetIssue(val message: String, val rowKey: String? = null)

object EntityDatasetValidator {
  fun validate(
    dataset: EntityDataset,
    allDatasetIds: List<String> = listOf(dataset.id),
  ): List<DatasetIssue> {
    val issues = mutableListOf<DatasetIssue>()
    if (!FormEditorValidator.isValidName(dataset.id)) {
      issues += DatasetIssue("ID must start with a letter and use only letters, digits, _ . -")
    } else if (allDatasetIds.count { it == dataset.id } > 1) {
      issues += DatasetIssue("ID \"${dataset.id}\" is used by another dataset in this survey.")
    }
    if (dataset.displayName.isBlank()) issues += DatasetIssue("Name is required.")
    val names = dataset.properties.map { it.name }
    dataset.properties.forEach { p ->
      if (!FormEditorValidator.isValidName(p.name) || p.name == "geometry") {
        issues += DatasetIssue("Property \"${p.name}\" has an invalid name.")
      }
    }
    if (names.toSet().size != names.size) issues += DatasetIssue("Property names must be unique.")
    if (dataset.property(dataset.keyProperty) == null) {
      issues += DatasetIssue("Choose a key property.")
    }
    if (dataset.property(dataset.labelProperty) == null) {
      issues += DatasetIssue("Choose a label property.")
    }
    val keys = mutableSetOf<String>()
    dataset.rows.forEach { row ->
      val keyValue = row.values[dataset.keyProperty].orEmpty().trim()
      val rowName = dataset.labelOf(row)
      when {
        keyValue.isEmpty() -> issues += DatasetIssue("\"$rowName\" is missing a key.", row.key)
        !keys.add(keyValue) -> issues += DatasetIssue("Key \"$keyValue\" is duplicated.", row.key)
      }
      dataset.properties.forEach { p ->
        val v = row.values[p.name].orEmpty()
        if (p.required && v.isBlank() && p.name != dataset.keyProperty) {
          issues += DatasetIssue("\"$rowName\" is missing ${p.label}.", row.key)
        } else if (!p.type.accepts(v)) {
          issues +=
            DatasetIssue("\"$rowName\": ${p.label} must be ${p.type.label.lowercase()}.", row.key)
        }
      }
      if (
        dataset.kind == DatasetKind.MAP_LAYER &&
          row.geometry.size < dataset.geometryKind.minVertices
      ) {
        issues +=
          DatasetIssue(
            "\"$rowName\" needs at least ${dataset.geometryKind.minVertices} " +
              "${if (dataset.geometryKind.minVertices == 1) "coordinate" else "vertices"}.",
            row.key,
          )
      }
    }
    return issues
  }
}

/** Formats / parses geometry as `lat, lng; lat, lng; …` for inline editing. */
object GeometryText {
  fun format(points: List<LatLng>): String =
    points.joinToString("; ") { "${round6(it.lat)}, ${round6(it.lng)}" }

  /** Returns the parsed vertices, or `null` if any pair is malformed or out of range. */
  fun parse(text: String): List<LatLng>? {
    if (text.isBlank()) return emptyList()
    return text
      .split(';')
      .filter { it.isNotBlank() }
      .map { pair ->
        val parts = pair.split(',').map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        LatLng(lat, lng)
      }
  }

  private fun round6(v: Double): String {
    val scaled = kotlin.math.round(v * 1_000_000) / 1_000_000
    return scaled.toString()
  }
}

/** Parses `#RRGGBB` into an ARGB long, or `null` if malformed. */
fun parseHexColor(hex: String): Long? {
  val digits = hex.trim().removePrefix("#")
  if (digits.length != 6) return null
  val rgb = digits.toLongOrNull(16) ?: return null
  return 0xFF000000L or rgb
}
