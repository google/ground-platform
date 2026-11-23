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

import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

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
  /** How the area was defined (`SurveyArea.area_source`). */
  val source: SurveyAreaSource = SurveyAreaSource.SelectedBoundary,
) {
  /** Every vertex of every part. */
  val allVertices: List<LatLng>
    get() = parts.flatten()

  val vertexCount: Int
    get() = parts.sumOf { it.size }

  /**
   * Geographic center of the area ([SurveyAreaGeometry.centroid]), from which the survey's
   * coordinates are shown; `null` when the area has no vertices.
   */
  val centroid: LatLng?
    get() = SurveyAreaGeometry.centroid(parts)
}

/** How a [SurveyArea] was defined (`SurveyAreaSource`); the UI names it in the user's language. */
sealed interface SurveyAreaSource {
  /** A boundary chosen for the survey, such as its organization's country. */
  data object SelectedBoundary : SurveyAreaSource

  /** Drawn on the map. */
  data object Drawn : SurveyAreaSource

  /** Uploaded as the file named [fileName]. */
  data class Uploaded(val fileName: String) : SurveyAreaSource

  /** A place found with the place search. */
  data object PlaceSearch : SurveyAreaSource

  /** Coordinates typed into the place search. */
  data object Coordinates : SurveyAreaSource

  /** The plot grid of a Collect Earth project with [plotCount] plots. */
  data class CollectEarthGrid(val plotCount: Int) : SurveyAreaSource
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
  /**
   * Purpose Packs selected for the survey (`SurveyDef.purpose_ids`): global pack IDs or
   * `org.<organizationId>.<name>` IDs of the organization's packs.
   */
  val purposeIds: List<String> = emptyList(),
  /**
   * Free-text description when [SurveyWizardRules.OTHER_PURPOSE_ID] is selected in [purposeIds].
   */
  val otherPurpose: String = "",
  /**
   * Programs the survey reports to (`SurveyDef.program_ids`), e.g. `"eudr"`: preset by the starter
   * template and editable; used for tracking and impact reporting.
   */
  val programIds: List<String> = emptyList(),
  val quotaLimits: QuotaLimitsConfig = QuotaLimitsConfig(),
)

/** Collaborator roles (mirrors `openforis.collectone.v1.survey.Role`). */
enum class CollaboratorRole {
  VIEWER,
  DATA_COLLECTOR,
  SURVEY_ORGANIZER,
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

/** Mirrors `openforis.collectone.v1.survey.SharingPolicy`. */
enum class SharingPolicy {
  RESTRICTED,
  /** Only valid when the survey belongs to an organization ([SurveyDetails.organizationId]). */
  ORGANIZATION,
  ANYONE_WITH_LINK,
  PUBLIC;

  val isOpenToAnyone: Boolean
    get() = this == ANYONE_WITH_LINK || this == PUBLIC

  val isListedInDirectory: Boolean
    get() = this == PUBLIC
}

/** Mirrors `openforis.collectone.v1.survey.PeerDataVisibility`. */
enum class PeerDataVisibility {
  ALL,
  OWN_ONLY,
}

data class SharingSettings(
  val ownerEmail: String,
  val policy: SharingPolicy = SharingPolicy.RESTRICTED,
  val viewPolicy: SharingPolicy? = null,
  val peerDataVisibility: PeerDataVisibility = PeerDataVisibility.ALL,
  val collaborators: List<Collaborator> = emptyList(),
  val ownerProfile: CachedProfile? = null,
  val collectorConsentRequired: Boolean = false,
  val collectorConsentText: String = "",
  val defaultRole: CollaboratorRole = CollaboratorRole.DATA_COLLECTOR,
) {
  val effectiveViewPolicy: SharingPolicy
    get() = viewPolicy ?: policy

  val isOpenToAnyone: Boolean
    get() = policy.isOpenToAnyone

  val isListedInDirectory: Boolean
    get() = policy.isListedInDirectory

  val isPublicData: Boolean
    get() = effectiveViewPolicy.isOpenToAnyone && peerDataVisibility == PeerDataVisibility.ALL

  val isPrivateSubmissions: Boolean
    get() = policy.isOpenToAnyone && peerDataVisibility == PeerDataVisibility.OWN_ONLY

  val requiresCollectorConsent: Boolean
    get() = isPublicData || collectorConsentRequired
}

/** Whether an entity dataset is a spatial Map layer or a non-spatial Data table. */
enum class DatasetKind {
  MAP_LAYER,
  DATA_TABLE,
}

/**
 * Property (column) types supported in the editor (subset of
 * `openforis.collectone.v1.forms.DataType`).
 */
enum class PropertyType(val dataType: String) {
  TEXT("TYPE_STRING"),
  INTEGER("TYPE_INT"),
  DECIMAL("TYPE_DECIMAL"),
  BOOLEAN("TYPE_BOOLEAN"),
  DATE("TYPE_DATE");

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
  /**
   * Dictionary concept of this property (`EntityPropertyDefinition.concept_ref`), copied from the
   * linked question that saves to it (see [SaveToRules.inheritConcepts]).
   */
  val conceptLink: ConceptLink? = null,
)

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
  val geometryType: GeometryType = GeometryType.POINT,
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
data class DatasetIssue(
  /** What is wrong, localized by the presentation layer (`ui/text/DomainMessageText.kt`). */
  val message: DomainMessage,
  val rowKey: String? = null,
)

object EntityDatasetValidator {
  fun validate(
    dataset: EntityDataset,
    allDatasetIds: List<String> = listOf(dataset.id),
  ): List<DatasetIssue> {
    val issues = mutableListOf<DatasetIssue>()
    if (!FormEditorValidator.isValidName(dataset.id)) {
      issues += DatasetIssue(message = EditorIssueMessage.DATASET_ID_INVALID.message())
    } else if (allDatasetIds.count { it == dataset.id } > 1) {
      issues += DatasetIssue(message = EditorIssueMessage.DATASET_ID_DUPLICATE.message(dataset.id))
    }
    if (dataset.displayName.isBlank()) {
      issues += DatasetIssue(message = EditorIssueMessage.NAME_REQUIRED.message())
    }
    val names = dataset.properties.map { it.name }
    dataset.properties.forEach { p ->
      if (!FormEditorValidator.isValidName(p.name) || p.name == "geometry") {
        issues +=
          DatasetIssue(message = EditorIssueMessage.DATASET_PROPERTY_NAME_INVALID.message(p.name))
      }
    }
    if (names.toSet().size != names.size) {
      issues += DatasetIssue(message = EditorIssueMessage.DATASET_PROPERTY_NAME_DUPLICATE.message())
    }
    if (dataset.property(dataset.keyProperty) == null) {
      issues += DatasetIssue(message = EditorIssueMessage.DATASET_KEY_PROPERTY_MISSING.message())
    }
    if (dataset.property(dataset.labelProperty) == null) {
      issues += DatasetIssue(message = EditorIssueMessage.DATASET_LABEL_PROPERTY_MISSING.message())
    }
    val keys = mutableSetOf<String>()
    dataset.rows.forEach { row ->
      val keyValue = row.values[dataset.keyProperty].orEmpty().trim()
      val rowName = dataset.labelOf(row)
      when {
        keyValue.isEmpty() ->
          issues +=
            DatasetIssue(
              EditorIssueMessage.DATASET_ROW_KEY_MISSING.message(rowName),
              row.key,
            )
        !keys.add(keyValue) ->
          issues +=
            DatasetIssue(
              EditorIssueMessage.DATASET_ROW_KEY_DUPLICATE.message(keyValue),
              row.key,
            )
      }
      dataset.properties.forEach { p ->
        val v = row.values[p.name].orEmpty()
        if (p.required && v.isBlank() && p.name != dataset.keyProperty) {
          issues +=
            DatasetIssue(
              EditorIssueMessage.DATASET_ROW_VALUE_MISSING.message(rowName, p.label),
              row.key,
            )
        } else if (!p.type.accepts(v)) {
          issues +=
            DatasetIssue(
              EditorIssueMessage.DATASET_ROW_VALUE_WRONG_TYPE.message(
                rowName,
                p.label,
                DomainMessage.Lowercase(DomainMessage.Label(p.type)),
              ),
              row.key,
            )
        }
      }
      if (
        dataset.kind == DatasetKind.MAP_LAYER &&
          row.geometry.size < dataset.geometryType.minVertices
      ) {
        issues +=
          DatasetIssue(
            EditorIssueCountedMessage.DATASET_ROW_GEOMETRY_TOO_SHORT.message(
              dataset.geometryType.minVertices,
              rowName,
              dataset.geometryType.minVertices,
            ),
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
    points.joinToString("; ") { "${round6(it.latitude)}, ${round6(it.longitude)}" }

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
