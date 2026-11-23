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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.usecase.ProfileExportPlan
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.ImpactText

/** The 6 guided steps of the Open Foris Collect One Survey Creation Wizard. */
enum class CreateSurveyWizardStep(val stepNumber: Int) {
  OVERVIEW(stepNumber = 1),
  MODE_AND_PURPOSE(stepNumber = 2),
  SURVEY_AREA(stepNumber = 3),
  MAP_FEATURES(stepNumber = 4),
  FORM_QUESTIONS(stepNumber = 5),
  SHARING(stepNumber = 6),
}

/** How the survey structure is initialized in Step 1 (`Overview`). */
enum class WizardStartMethod {
  FROM_SCRATCH,
  COPY_EXISTING,
  IMPORT_XFORMS,
}

/**
 * Primary collection mode selected in Step 2 (`Mode & purpose`), bridging Ground field data
 * collection and Collect Earth Online (CEO) desk-based visual classification workflows.
 */
enum class SurveyCollectionMode(
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
    tags = listOf("Ground"),
    formAvailability = FormAvailability.BOTH,
    defaultGeometryCapture = GeometryCapture.GPS_OR_MAP,
    defaultFieldCreationEnabled = true,
    defaultSaveToMode = SaveToMode.CREATE,
    defaultFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
    defaultPreviewTarget = FormPreviewTarget.MOBILE,
    surveyAreaRequired = true,
  ),
  VISUAL_CLASSIFICATION(
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
enum class WizardMapFeatureSource(val tags: List<String> = emptyList()) {
  FIELD_FREEFORM(tags = listOf("Ground")),
  SAMPLE_PLOTS(tags = listOf("Collect Earth Online")),
  IMPORT_FILE,
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
      "forest_carbon_inventory",
      "protected_area_management",
      "iplc_land_mapping",
      "timber_traceability",
      "deforestation_activity_data",
      "lulc_training_data",
    )

  private val VISUAL_AFFINITY_ORDER =
    listOf(
      "deforestation_activity_data",
      "lulc_training_data",
      "forest_carbon_inventory",
      "ferm_restoration",
      "eudr_due_diligence",
      "protected_area_management",
      "iplc_land_mapping",
      "timber_traceability",
      "producer_registration",
    )

  private val BOTH_AFFINITY_ORDER =
    listOf(
      "deforestation_activity_data",
      "forest_carbon_inventory",
      "eudr_due_diligence",
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
      "lulc_training_data" in purposeIds ||
        "deforestation_activity_data" in purposeIds ||
        "forest_carbon_inventory" in purposeIds -> WizardMapFeatureSource.SAMPLE_PLOTS
      else -> null
    }

  /**
   * Default [GeometryType] for the primary Map layer driven by [purposeIds] and [mapFeatureSource].
   */
  fun defaultGeometryType(
    purposeIds: List<String>,
    mapFeatureSource: WizardMapFeatureSource,
    eudrPlotsUnderFourHaConfirmed: Boolean = false,
    starterTemplateIds: Map<String, String> = emptyMap(),
  ): GeometryType =
    when {
      requiresEudrPolygonGuardrail(purposeIds, starterTemplateIds) &&
        !eudrPlotsUnderFourHaConfirmed -> GeometryType.POLYGON
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS -> GeometryType.POLYGON
      purposeIds.any {
        it == "ferm_restoration" ||
          it == "iplc_land_mapping" ||
          it.endsWith(".coop_certification_audit")
      } -> GeometryType.POLYGON
      else -> GeometryType.POINT
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
      "lulc_training_data" in purposeIds || "deforestation_activity_data" in purposeIds ->
        SurveyCollectionMode.VISUAL_CLASSIFICATION
      "forest_carbon_inventory" in purposeIds -> SurveyCollectionMode.FIELD_SURVEY
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS ->
        SurveyCollectionMode.VISUAL_CLASSIFICATION
      else -> SurveyCollectionMode.FIELD_SURVEY
    }

  /**
   * Whether sample plots can be verified in the field after a desk check (see
   * `PlotReviewWorkflow`): the plots are interpreted on imagery, so the primary Form can be the
   * desk check.
   */
  fun offersPlotFieldValidation(
    mapFeatureSource: WizardMapFeatureSource,
    mode: SurveyCollectionMode,
  ): Boolean =
    mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS &&
      mode != SurveyCollectionMode.FIELD_SURVEY

  /** Starter template of the EUDR purpose whose rules include the Article 9 polygon requirement. */
  const val EUDR_STARTER_TEMPLATE_ID = "eudr"

  /**
   * Returns true when [purposeIds] includes `eudr_due_diligence` and its starter template (in
   * [starterTemplateIds]) is EUDR or the default, activating the EUDR Article 9 polygon requirement
   * guardrail for plots > 4 ha. Other starter templates (such as UK FRC) don't have this rule.
   */
  fun requiresEudrPolygonGuardrail(
    purposeIds: List<String>,
    starterTemplateIds: Map<String, String> = emptyMap(),
  ): Boolean {
    if (EUDR_PURPOSE_ID !in purposeIds) return false
    val starterTemplateId = starterTemplateIds[EUDR_PURPOSE_ID].orEmpty()
    return starterTemplateId.isBlank() || starterTemplateId == EUDR_STARTER_TEMPLATE_ID
  }

  /** Purpose Pack of EUDR / UK FRC deforestation compliance. */
  const val EUDR_PURPOSE_ID = "eudr_due_diligence"

  /**
   * Returns true when CARE / Indigenous data sovereignty defaults should be pre-selected: either
   * the organization is an [OrganizationType.IPLC_ORGANIZATION] or the survey's active purpose/form
   * concepts include `iplc.*` concepts.
   */
  fun usesIplcCareDefaults(
    organizationType: OrganizationType?,
    conceptIds: Set<String>,
  ): Boolean = careDefaultsReason(organizationType, conceptIds) != null

  /**
   * Why CARE / Indigenous data sovereignty defaults should be pre-selected, or `null` when they
   * shouldn't: the organization's type takes precedence over the survey's `iplc.*` concepts.
   */
  fun careDefaultsReason(
    organizationType: OrganizationType?,
    conceptIds: Set<String>,
  ): CareDefaultsReason? {
    if (organizationType == OrganizationType.IPLC_ORGANIZATION) {
      return CareDefaultsReason.IplcOrganization
    }
    val iplcConceptIds = conceptIds.filter { it.startsWith(IPLC_CONCEPT_PREFIX) }.sorted()
    return if (iplcConceptIds.isEmpty()) null else CareDefaultsReason.IplcConcepts(iplcConceptIds)
  }

  /**
   * ID prefix of the dictionary concepts that describe Indigenous Peoples and local communities.
   */
  private const val IPLC_CONCEPT_PREFIX = "iplc."

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
}

/** Why the wizard applied CARE data sovereignty defaults on its own. */
sealed interface CareDefaultsReason {
  /** The survey's organization is an Indigenous Peoples or local community (IPLC) organization. */
  data object IplcOrganization : CareDefaultsReason

  /** The survey links Indigenous and community concepts ([conceptIds], sorted). */
  data class IplcConcepts(val conceptIds: List<String>) : CareDefaultsReason
}

/** Live impact and export readiness summary displayed in Step 5 and Step 6 of the wizard. */
data class WizardImpactReadiness(
  val selectedPackTitles: List<ImpactText> = emptyList(),
  val selectedProgramLabels: List<String> = emptyList(),
  val linkedConceptCount: Int = 0,
  val totalQuestionCount: Int = 0,
  val goalTitles: List<ImpactText> = emptyList(),
  val exportPlans: List<ProfileExportPlan> = emptyList(),
  val careSovereigntyApplied: Boolean = false,
  val excludeFromPlatformAggregates: Boolean = false,
) {
  /** Number of enabled export profiles whose required fields are 100% mapped. */
  val readyExportProfileCount: Int
    get() = exportPlans.count { it.unmappedFields.isEmpty() }
}
