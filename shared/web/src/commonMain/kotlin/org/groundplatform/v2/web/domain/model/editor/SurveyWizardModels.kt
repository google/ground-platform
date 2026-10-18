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
package org.groundplatform.v2.web.domain.model.editor

import org.groundplatform.v2.app.domain.model.FormAvailability
import org.groundplatform.v2.app.domain.model.OrganizationType
import org.groundplatform.v2.app.domain.model.PurposePack
import org.groundplatform.v2.app.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.app.domain.model.editor.GeometryCapture
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.SaveToMode
import org.groundplatform.v2.app.domain.usecase.ProfileExportPlan
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
    label = "Forms",
    subtitle = "Questionnaires, library concepts, and export mapping",
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
    label = "Import from file",
    description = "Import a form definition from ODK XForms (.xml) or Collect Earth (.cep).",
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
      "protected_area_management",
      "iplc_land_mapping",
      "timber_traceability",
      "redd_plus_tfff",
      "lulc_training_data",
    )

  private val VISUAL_AFFINITY_ORDER =
    listOf(
      "lulc_training_data",
      "redd_plus_tfff",
      "ferm_restoration",
      "eudr_due_diligence",
      "protected_area_management",
      "iplc_land_mapping",
      "timber_traceability",
      "producer_registration",
    )

  private val BOTH_AFFINITY_ORDER =
    listOf(
      "eudr_due_diligence",
      "redd_plus_tfff",
      "ferm_restoration",
      "lulc_training_data",
      "protected_area_management",
      "iplc_land_mapping",
      "timber_traceability",
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
    purposeMapFeatureSource(purposeIds) ?: WizardMapFeatureSource.FIELD_FREEFORM

  /**
   * The [WizardMapFeatureSource] the selected [purposeIds] call for, or `null` when none of them
   * calls for a particular one (and [defaultMapFeatureSource] falls back to free-form collection).
   */
  fun purposeMapFeatureSource(purposeIds: List<String>): WizardMapFeatureSource? =
    when {
      "lulc_training_data" in purposeIds || "redd_plus_tfff" in purposeIds ->
        WizardMapFeatureSource.SAMPLE_PLOTS
      else -> null
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
      purposeIds.any {
        it == "ferm_restoration" ||
          it == "iplc_land_mapping" ||
          it.endsWith(".coop_certification_audit")
      } -> GeometryKind.POLYGON
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

  /**
   * Returns true when [pack] is a regulatory compliance or supply-chain verification framework
   * (such as EUDR due diligence, timber legality/traceability, or certification audits) rather than
   * a general monitoring domain purpose.
   */
  fun isComplianceFrameworkPack(pack: PurposePack): Boolean =
    pack.exportProfileIds.isNotEmpty() ||
      pack.id == "eudr_due_diligence" ||
      pack.id == "timber_traceability" ||
      pack.id.endsWith(".coop_certification_audit")

  /**
   * Default collector consent statement populated when enabling general collector consent in the
   * wizard without CARE sovereignty defaults.
   */
  const val DEFAULT_COLLECTOR_CONSENT_TEXT: String =
    "I consent to share the field observations and geospatial data collected in this survey with " +
      "the organizing institution under its data governance policy."

  /**
   * Default Free, Prior and Informed Consent (FPIC) & CARE data sovereignty notice populated when
   * applying CARE defaults in the wizard.
   */
  const val DEFAULT_FPIC_CONSENT_TEXT: String =
    "Free, Prior and Informed Consent (FPIC): In accordance with the CARE Principles for " +
      "Indigenous Data Governance and FAO guidelines on Indigenous Peoples and local " +
      "communities' data sovereignty, data collected in this survey remains under community " +
      "authority and control. Participation is voluntary, and contributors may review, " +
      "restrict, or withdraw consent at any time."

  /** Default 5-criterion CARE & FAO FPIC governance rubric shown in Step 6 (`Sharing`). */
  val CARE_GOVERNANCE_CRITERIA: List<CareGovernanceCriterion> =
    listOf(
      CareGovernanceCriterion(
        code = "C",
        title = "Collective Benefit",
        standard = "CARE & FAO Indigenous Data Policy",
        description =
          "Ecosystem and community indicators use shared library concepts for community-owned evidence.",
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "A",
        title = "Authority to Control",
        standard = "CARE & FAO VGGT Tenure Governance",
        description =
          "Access is Restricted so community stewards control who can view or collect survey records.",
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "R",
        title = "Responsibility",
        standard = "CARE Principles",
        description =
          "Peer visibility is limited to each contributor's own submissions plus survey organizers.",
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "E",
        title = "Ethics",
        standard = "CARE Principles",
        description = "Sensitive community data is protected from unapproved external aggregation.",
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "FPIC",
        title = "Free, Prior and Informed Consent",
        standard = "FAO FPIC Manual",
        description =
          "Explicit collector/participant consent is required before recording field data.",
        isSatisfied = true,
      ),
    )
}

/**
 * One CARE Principle or FAO Indigenous Peoples' data governance & FPIC criterion displayed in the
 * wizard's Sharing & data sovereignty step.
 */
data class CareGovernanceCriterion(
  val code: String,
  val title: String,
  val standard: String,
  val description: String,
  val isSatisfied: Boolean,
)

/** Live impact and export readiness summary displayed in Step 5 and Step 6 of the wizard. */
data class WizardImpactReadiness(
  val selectedPackTitles: List<String> = emptyList(),
  val selectedProgramLabels: List<String> = emptyList(),
  val linkedConceptCount: Int = 0,
  val totalQuestionCount: Int = 0,
  val goalTitles: List<String> = emptyList(),
  val exportPlans: List<ProfileExportPlan> = emptyList(),
  val careSovereigntyApplied: Boolean = false,
  val excludeFromPlatformAggregates: Boolean = false,
) {
  /** Number of enabled export profiles whose required fields are 100% mapped. */
  val readyExportProfileCount: Int
    get() = exportPlans.count { it.unmappedFields.isEmpty() }
}
