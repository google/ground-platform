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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorValidator

/** Defines the geographic survey area and boundaries for the survey. */
data class SurveyArea(
  val name: String,
  val boundaries: List<LatLng>,
  val center: LatLng =
    if (boundaries.isNotEmpty()) {
      LatLng(
        lat = boundaries.map { it.lat }.average(),
        lng = boundaries.map { it.lng }.average(),
      )
    } else {
      LatLng(0.0, 0.0)
    },
  val zoom: Double = 12.0,
  val sourceLabel: String = "Selected boundary",
)

/** Survey-level metadata (mirrors the descriptive fields of `SurveyDef`). */
data class SurveyDetails(
  val surveyId: String,
  val title: String,
  val description: String,
  val defaultLanguage: String = "en",
  val supportedLanguages: List<String> = listOf("en"),
  val surveyArea: SurveyArea? = null,
)

/** Collaborator roles (mirrors `groundplatform.v2.survey.Role`). */
enum class CollaboratorRole(val label: String, val description: String) {
  VIEWER("Viewer", "Can view the map, Map layers, and submissions."),
  DATA_COLLECTOR("Data collector", "Can collect submissions and add map features."),
  SURVEY_ORGANIZER("Survey organizer", "Can edit Forms, manage sharing, and export data."),
}

/** Mirrors `AclEntry.InvitationStatus`. */
enum class InvitationStatus(val label: String) {
  PENDING("Invited"),
  ACCEPTED("Joined"),
}

/**
 * Name and photo copied from the invitee's account when they accept the invite link, so the Sharing
 * pane can show people by name without querying the identity provider every time.
 *
 * [photoUrl] is normally an HTTPS URL. The prototype uses `avatar:<n>` placeholders, which are
 * drawn as illustrated portraits (see [ProfileAvatar]).
 */
data class CachedProfile(
  val displayName: String,
  val photoUrl: String? = null,
  val cachedOn: String = "",
)

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

/** Invite links and the name suggested on the (simulated) acceptance screen. */
object InviteLinks {
  const val BASE_URL = "https://ground.example.org/join/"

  fun url(token: String) = BASE_URL + token

  /** Guesses a display name from an email local part, e.g. `grace.njeri@…` → "Grace Njeri". */
  fun suggestedName(email: String): String =
    email
      .substringBefore('@')
      .split('.', '_', '-', '+')
      .filter { it.isNotBlank() }
      .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

  /** Up to two initials from a full name or email. */
  fun initials(nameOrEmail: String): String {
    val base = if ('@' in nameOrEmail) suggestedName(nameOrEmail) else nameOrEmail
    val words = base.split(' ').filter { it.isNotBlank() }
    return when {
      words.isEmpty() -> "?"
      words.size == 1 -> words[0].take(1).uppercase()
      else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
  }
}

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
) {
  val isLinkedToForm: Boolean
    get() = linkedFormKey != null

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

/** Starter survey shown when the Survey editor first opens (fictional sample data). */
object SurveyEditorSamples {
  fun details() =
    SurveyDetails(
      surveyId = "kenya_coffee_shade",
      title = "Kenya Coffee Shade Monitoring",
      description =
        "Monitor shade tree cover and parcel boundaries across smallholder coffee farms.",
      supportedLanguages = listOf("en", "sw"),
      surveyArea =
        SurveyArea(
          name = "Othaya Sub-County, Nyeri",
          center = LatLng(-0.4192, 36.9498),
          zoom = 12.5,
          boundaries =
            listOf(
              LatLng(-0.3960, 36.9220),
              LatLng(-0.3960, 36.9780),
              LatLng(-0.4420, 36.9780),
              LatLng(-0.4420, 36.9220),
            ),
        ),
    )

  fun sharing() =
    SharingSettings(
      ownerEmail = "organizer@example.org",
      ownerProfile = CachedProfile("Amina Wanjiru", "avatar:3", "2026-03-02"),
      policy = SharingPolicy.RESTRICTED,
      collaborators =
        listOf(
          Collaborator(
            "field.lead@example.org",
            CollaboratorRole.SURVEY_ORGANIZER,
            InvitationStatus.ACCEPTED,
            userId = "uid-field-lead",
            profile = CachedProfile("Daniel Kiprop", "avatar:0", "2026-03-04"),
          ),
          Collaborator(
            "collector.one@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            InvitationStatus.ACCEPTED,
            userId = "uid-collector-one",
            profile = CachedProfile("Grace Njeri", "avatar:5", "2026-03-06"),
          ),
          Collaborator(
            "collector.two@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            inviteToken = "k7q2-mx4p",
          ),
          Collaborator("reviewer@example.org", CollaboratorRole.VIEWER, inviteToken = "r9w3-bt6d"),
        ),
    )

  private fun square(lat: Double, lng: Double, d: Double) =
    listOf(
      LatLng(lat, lng),
      LatLng(lat, lng + d),
      LatLng(lat - d * 0.8, lng + d * 1.1),
      LatLng(lat - d, lng - d * 0.1),
    )

  fun coffeeParcels() =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "coffee_parcels",
      displayName = "Coffee parcels",
      description = "Registered smallholder parcel boundaries.",
      geometryKind = GeometryKind.POLYGON,
      keyProperty = "parcel_id",
      labelProperty = "parcel_name",
      properties =
        listOf(
          EntityProperty("parcel_id", "Parcel ID", PropertyType.TEXT, required = true),
          EntityProperty("parcel_name", "Parcel name", PropertyType.TEXT, required = true),
          EntityProperty("area_ha", "Area (ha)", PropertyType.DECIMAL),
          EntityProperty("status", "Status", PropertyType.TEXT),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "parcel_id" to "NYR-104",
              "parcel_name" to "Gatura Ridge",
              "area_ha" to "1.8",
              "status" to "completed",
            ),
            square(-0.4180, 36.9480, 0.004),
          ),
          EntityRow(
            "r2",
            mapOf(
              "parcel_id" to "NYR-108",
              "parcel_name" to "Kiamariga",
              "area_ha" to "2.4",
              "status" to "in_progress",
            ),
            square(-0.4230, 36.9560, 0.005),
          ),
          EntityRow(
            "r3",
            mapOf(
              "parcel_id" to "NYR-112",
              "parcel_name" to "Mathira East",
              "area_ha" to "0.9",
              "status" to "pending",
            ),
            square(-0.4120, 36.9620, 0.003),
          ),
        ),
      style = LayerStyle(colorHex = "#6D4C41", strokeWidth = 2.0, fillOpacity = 0.25),
    )

  fun shadePlots() =
    EntityDataset(
      key = "d2",
      kind = DatasetKind.MAP_LAYER,
      id = "shade_monitoring_plots",
      displayName = "Shade monitoring plots",
      description = "Permanent 20 m radius plots for shade tree measurements.",
      geometryKind = GeometryKind.POINT,
      keyProperty = "plot_id",
      labelProperty = "plot_id",
      fieldCreationEnabled = true,
      properties =
        listOf(
          EntityProperty("plot_id", "Plot ID", PropertyType.TEXT, required = true),
          EntityProperty("established", "Established", PropertyType.DATE),
          EntityProperty("canopy_pct", "Canopy cover (%)", PropertyType.INTEGER),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf("plot_id" to "SHD-201", "established" to "2025-03-14", "canopy_pct" to "42"),
            listOf(LatLng(-0.4150, 36.9500)),
          ),
          EntityRow(
            "r2",
            mapOf("plot_id" to "SHD-202", "established" to "2025-03-15", "canopy_pct" to "35"),
            listOf(LatLng(-0.4205, 36.9585)),
          ),
          EntityRow(
            "r3",
            mapOf("plot_id" to "SHD-203", "established" to "2025-04-02", "canopy_pct" to "58"),
            listOf(LatLng(-0.4108, 36.9641)),
          ),
          EntityRow(
            "r4",
            mapOf("plot_id" to "SHD-204", "established" to "2025-04-03"),
            listOf(LatLng(-0.4252, 36.9470)),
          ),
        ),
      style = LayerStyle(colorHex = "#2E7D32"),
    )

  fun farmers() =
    EntityDataset(
      key = "d3",
      kind = DatasetKind.DATA_TABLE,
      id = "farmers",
      displayName = "Farmers",
      description = "Cooperative member roster (fictional sample data).",
      keyProperty = "farmer_id",
      labelProperty = "name",
      fieldCreationEnabled = true,
      properties =
        listOf(
          EntityProperty("farmer_id", "Farmer ID", PropertyType.TEXT, required = true),
          EntityProperty("name", "Name", PropertyType.TEXT, required = true),
          EntityProperty("cooperative", "Cooperative", PropertyType.TEXT),
          EntityProperty("member_since", "Member since", PropertyType.DATE),
          EntityProperty("certified", "Certified", PropertyType.BOOLEAN),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "farmer_id" to "F-001",
              "name" to "Farmer A",
              "cooperative" to "Gatura",
              "member_since" to "2019-06-01",
              "certified" to "yes",
            ),
          ),
          EntityRow(
            "r2",
            mapOf(
              "farmer_id" to "F-002",
              "name" to "Farmer B",
              "cooperative" to "Gatura",
              "member_since" to "2021-02-15",
              "certified" to "no",
            ),
          ),
          EntityRow(
            "r3",
            mapOf(
              "farmer_id" to "F-003",
              "name" to "Farmer C",
              "cooperative" to "Mathira",
              "member_since" to "2018-09-30",
              "certified" to "yes",
            ),
          ),
        ),
    )

  fun treeSpecies() =
    EntityDataset(
      key = "d4",
      kind = DatasetKind.DATA_TABLE,
      id = "tree_species",
      displayName = "Tree species",
      description = "Shade tree species lookup list.",
      keyProperty = "code",
      labelProperty = "common_name",
      properties =
        listOf(
          EntityProperty("code", "Code", PropertyType.TEXT, required = true),
          EntityProperty("scientific_name", "Scientific name", PropertyType.TEXT, required = true),
          EntityProperty("common_name", "Common name", PropertyType.TEXT),
          EntityProperty("native", "Native", PropertyType.BOOLEAN),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "code" to "GRRO",
              "scientific_name" to "Grevillea robusta",
              "common_name" to "Silky oak",
              "native" to "no",
            ),
          ),
          EntityRow(
            "r2",
            mapOf(
              "code" to "COAF",
              "scientific_name" to "Cordia africana",
              "common_name" to "Large-leaved cordia",
              "native" to "yes",
            ),
          ),
          EntityRow(
            "r3",
            mapOf(
              "code" to "CRME",
              "scientific_name" to "Croton megalocarpus",
              "common_name" to "Croton",
              "native" to "yes",
            ),
          ),
          EntityRow(
            "r4",
            mapOf(
              "code" to "MAIN",
              "scientific_name" to "Macadamia integrifolia",
              "common_name" to "Macadamia",
              "native" to "no",
            ),
          ),
        ),
    )
}
