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
package org.groundplatform.v2.app.domain.model.editor

import org.groundplatform.v2.app.domain.model.FormAvailability
import org.groundplatform.v2.app.domain.model.OrganizationType
import org.groundplatform.v2.app.domain.model.PurposePack
import org.groundplatform.v2.app.domain.usecase.ResolvedLibrary

/** The 6 guided steps of the Open Foris Collect One Survey Creation Wizard. */
enum class CreateSurveyWizardStep(
  val stepNumber: Int,
  val label: String,
  val subtitle: String,
) {
  OVERVIEW(
    stepNumber = 1,
    label = "Overview",
    subtitle = "Title, organization, languages, and start method",
  ),
  MODE_AND_PURPOSE(
    stepNumber = 2,
    label = "Purpose",
    subtitle = "Survey goal, purpose pack, and reporting programs",
  ),
  SURVEY_AREA(
    stepNumber = 3,
    label = "Survey area",
    subtitle = "Study boundary and offline/web basemap imagery",
  ),
  MAP_FEATURES(
    stepNumber = 4,
    label = "Map features",
    subtitle = "Primary map layer, sample plots, or imported features",
  ),
  FORM_QUESTIONS(
    stepNumber = 5,
    label = "Form & questions",
    subtitle = "Questionnaire, library concepts, and export mapping",
  ),
  SHARING(
    stepNumber = 6,
    label = "Sharing",
    subtitle = "Access, CARE data sovereignty, and impact readiness",
  ),
}

/** How the survey structure is initialized in Step 1 (`Overview`). */
enum class WizardStartMethod(val label: String, val description: String) {
  FROM_SCRATCH(
    label = "Build a new survey",
    description = "Configure map layers and forms step by step or from purpose packs.",
  ),
  COPY_EXISTING(
    label = "Copy an existing survey",
    description = "Clone forms, map layer schemas, survey area, and purpose packs without data.",
  ),
  IMPORT_XFORMS(
    label = "Import from file (XForms XML)",
    description = "Upload an ODK XForms XML file and link its questions to library concepts.",
  ),
}

/**
 * Primary collection mode selected in Step 2 (`Mode & purpose`), bridging Ground field data
 * collection and Collect Earth Online (CEO) desk-based visual classification workflows.
 */
enum class SurveyCollectionMode(
  val label: String,
  val summary: String,
  val tags: List<String>,
  val formAvailability: FormAvailability,
  val defaultGeometryCapture: GeometryCapture,
  val defaultFieldCreationEnabled: Boolean,
  val defaultSaveToMode: SaveToMode,
  val defaultFeatureSource: WizardMapFeatureSource,
  val defaultPreviewTarget: FormPreviewTarget,
  val surveyAreaRequired: Boolean,
) {
  FIELD_SURVEY(
    label = "Field survey",
    summary = "Mobile offline/online field data collection by data collectors on the ground.",
    tags = listOf("Ground"),
    formAvailability = FormAvailability.MOBILE,
    defaultGeometryCapture = GeometryCapture.GPS_ONLY,
    defaultFieldCreationEnabled = true,
    defaultSaveToMode = SaveToMode.CREATE,
    defaultFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
    defaultPreviewTarget = FormPreviewTarget.MOBILE,
    surveyAreaRequired = true,
  ),
  VISUAL_CLASSIFICATION(
    label = "Visual classification",
    summary =
      "Desk-based visual classification of sample plots over satellite and basemap imagery.",
    tags = listOf("Collect Earth Online"),
    formAvailability = FormAvailability.BOTH,
    defaultGeometryCapture = GeometryCapture.GPS_OR_MAP,
    defaultFieldCreationEnabled = false,
    defaultSaveToMode = SaveToMode.UPDATE,
    defaultFeatureSource = WizardMapFeatureSource.SAMPLE_PLOTS,
    defaultPreviewTarget = FormPreviewTarget.WEB,
    surveyAreaRequired = true,
  ),
  BOTH(
    label = "Both",
    summary = "Combined desk-based visual classification and mobile field data collection.",
    tags = listOf("Ground", "Collect Earth Online"),
    formAvailability = FormAvailability.BOTH,
    defaultGeometryCapture = GeometryCapture.GPS_OR_MAP,
    defaultFieldCreationEnabled = true,
    defaultSaveToMode = SaveToMode.UPDATE,
    defaultFeatureSource = WizardMapFeatureSource.SAMPLE_PLOTS,
    defaultPreviewTarget = FormPreviewTarget.MOBILE,
    surveyAreaRequired = true,
  ),
}

/** How the primary Map layer's features are populated in Step 4 (`Map features`). */
enum class WizardMapFeatureSource(
  val label: String,
  val description: String,
  val tags: List<String> = emptyList(),
) {
  FIELD_FREEFORM(
    label = "Data collectors add map features in the field",
    description = "Collectors capture new points or polygons on site during field visits.",
    tags = listOf("Ground"),
  ),
  SAMPLE_PLOTS(
    label = "Generate sample plots",
    description =
      "Create systematic or random sample plots (and sub-plot grids) inside the survey area.",
    tags = listOf("Collect Earth Online"),
  ),
  IMPORT_FILE(
    label = "Import map features (GeoJSON / KML)",
    description = "Upload pre-defined plot boundaries, parcels, or points from a file.",
  ),
}

/** Pure domain rules for sorting Purpose Packs and resolving mode/sovereignty defaults. */
object SurveyWizardRules {
  /** Synthetic Purpose ID used when a survey organizer selects the "Other" purpose option. */
  const val OTHER_PURPOSE_ID = "other"

  private val FIELD_AFFINITY_ORDER =
    listOf(
      "producer_registration",
      "eudr_due_diligence",
      "ferm_restoration",
      "redd_plus_tfff",
      "lulc_training_data",
    )

  private val VISUAL_AFFINITY_ORDER =
    listOf(
      "lulc_training_data",
      "redd_plus_tfff",
      "ferm_restoration",
      "eudr_due_diligence",
      "producer_registration",
    )

  private val BOTH_AFFINITY_ORDER =
    listOf(
      "eudr_due_diligence",
      "redd_plus_tfff",
      "ferm_restoration",
      "lulc_training_data",
      "producer_registration",
    )

  /**
   * Orders [library] Purpose Packs with organization-defined packs first, followed by global packs
   * sorted by their affinity to [mode].
   */
  fun sortPurposePacks(
    library: ResolvedLibrary,
    mode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
  ): List<PurposePack> {
    val affinityOrder =
      when (mode) {
        SurveyCollectionMode.FIELD_SURVEY -> FIELD_AFFINITY_ORDER
        SurveyCollectionMode.VISUAL_CLASSIFICATION -> VISUAL_AFFINITY_ORDER
        SurveyCollectionMode.BOTH -> BOTH_AFFINITY_ORDER
      }
    return library.purposePacks.sortedWith(
      compareBy<PurposePack> { !library.isOrganizationEntry(it.id) }
        .thenBy { pack ->
          val idx = affinityOrder.indexOf(pack.id)
          if (idx >= 0) idx else Int.MAX_VALUE
        }
        .thenBy { it.id }
    )
  }

  /**
   * Default [WizardMapFeatureSource] for the primary Map layer driven by the selected [purposeIds].
   */
  fun defaultMapFeatureSource(purposeIds: List<String>): WizardMapFeatureSource =
    when {
      "lulc_training_data" in purposeIds || "redd_plus_tfff" in purposeIds ->
        WizardMapFeatureSource.SAMPLE_PLOTS
      else -> WizardMapFeatureSource.FIELD_FREEFORM
    }

  /**
   * Default [GeometryKind] for the primary Map layer driven by [purposeIds] and [mapFeatureSource].
   */
  fun defaultGeometryKind(
    purposeIds: List<String>,
    mapFeatureSource: WizardMapFeatureSource,
    eudrPlotsUnderFourHaConfirmed: Boolean = false,
  ): GeometryKind =
    when {
      requiresEudrPolygonGuardrail(purposeIds) && !eudrPlotsUnderFourHaConfirmed ->
        GeometryKind.POLYGON
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS -> GeometryKind.POLYGON
      purposeIds.any { it == "ferm_restoration" || it.endsWith(".coop_certification_audit") } ->
        GeometryKind.POLYGON
      else -> GeometryKind.POINT
    }

  /**
   * Resolves the effective [SurveyCollectionMode] (form availability, geometry capture, preview
   * target, and save-to defaults) from the selected [purposeIds] and [mapFeatureSource].
   */
  fun defaultCollectionMode(
    purposeIds: List<String>,
    mapFeatureSource: WizardMapFeatureSource,
  ): SurveyCollectionMode =
    when {
      "lulc_training_data" in purposeIds -> SurveyCollectionMode.VISUAL_CLASSIFICATION
      "redd_plus_tfff" in purposeIds -> SurveyCollectionMode.BOTH
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS ->
        SurveyCollectionMode.VISUAL_CLASSIFICATION
      else -> SurveyCollectionMode.FIELD_SURVEY
    }

  /**
   * Returns true when [purposeIds] includes `eudr_due_diligence`, activating the EUDR Article 9
   * polygon requirement guardrail for plots > 4 ha.
   */
  fun requiresEudrPolygonGuardrail(purposeIds: List<String>): Boolean =
    "eudr_due_diligence" in purposeIds

  /**
   * Returns true when CARE / Indigenous data sovereignty defaults should be pre-selected: either
   * the organization is an [OrganizationType.IPLC_ORGANIZATION] or the survey's active purpose/form
   * concepts include `iplc.*` concepts.
   */
  fun usesIplcCareDefaults(
    organizationType: OrganizationType?,
    conceptIds: Set<String>,
  ): Boolean =
    organizationType == OrganizationType.IPLC_ORGANIZATION ||
      conceptIds.any { it.startsWith("iplc.") }
}
