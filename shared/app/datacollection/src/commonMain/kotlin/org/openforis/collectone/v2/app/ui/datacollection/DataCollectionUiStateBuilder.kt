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
package org.openforis.collectone.v2.app.ui.datacollection

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.QuotaAndCostEnforcer
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin

// Pure functions behind DataCollectionViewModel: its screen state, who may collect data, the
// submission quota check, and the workbench's form choice. They read only their arguments, so the
// ViewModel stays a thin state holder and tests call them with plain values.

/**
 * The screen state of data collection, built from the store's [data], the [session], and the PDF
 * export state [pdf]. [webMapDrawing] and [canSharePdfFiles] are passed through unchanged.
 */
internal fun buildDataCollectionUiState(
  data: DataCollectionData,
  session: DataCollectionSession,
  pdf: PdfExportState,
  webMapDrawing: WebMapDrawingHost,
  canSharePdfFiles: Boolean,
): DataCollectionUiState {
  val activeSurvey = data.activeSurvey
  return DataCollectionUiState(
    activeSurveyId = data.activeSurveyId,
    activeSurveyTitle = activeSurvey?.title.orEmpty(),
    forms = data.content.forms,
    formDefs =
      buildList {
        data.content.config?.primaryForm?.proto?.let(::add)
        data.content.config?.formsById?.values?.forEach { add(it.proto) }
        session.customFormDef?.let(::add)
      },
    entities = data.content.entities,
    standaloneSubmissions = data.content.standaloneSubmissions,
    submissionGeometries = data.content.submissionGeometries,
    collectorName = data.profile.displayName,
    isActiveSurveyPublicData = activeSurvey?.isPublicData == true,
    isActiveSurveyPrivateSubmissions = activeSurvey?.isPrivateSubmissions == true,
    canCollectData = canCollectData(activeSurvey, data.profile.email, data.organizations),
    pendingCollectorConsent =
      if (session.pendingConsentLaunch != null && activeSurvey != null) {
        CollectorConsentPrompt(
          surveyId = activeSurvey.id,
          surveyTitle = activeSurvey.title,
          isPublicData = activeSurvey.isPublicData,
          customConsentText = activeSurvey.collectorConsentText,
        )
      } else {
        null
      },
    selectedEntityId = session.selectedEntityId,
    selectedSubmissionId = session.selectedSubmissionId,
    activeFormWizardController = session.controller,
    activeDataCollectionEntityId = session.activeEntityId,
    activeDataCollectionFormId = session.activeFormId,
    wasFormLaunchedWithoutEntity = session.wasFormLaunchedWithoutEntity,
    isSubmissionConfirmationShown = session.isSubmissionConfirmationShown,
    isAvailableFormsSheetOpen = session.isAvailableFormsSheetOpen,
    entityRefSelectorViewMode = session.entityRefSelectorViewMode,
    entityRefSearchQuery = session.entityRefSearchQuery,
    entityRefFramingEpoch = session.entityRefFramingEpoch,
    customFormDef = session.customFormDef,
    webMapDrawing = webMapDrawing,
    webMapFramingRequest = session.webMapFramingRequest,
    webFormFocusRequest = session.webFormFocusRequest,
    activeQrCodeEntityId = session.activeQrCodeEntityId,
    activeSharedPdfSheet = pdf.activeSharedPdfSheet,
    pdfExportMessage = pdf.pdfExportMessage,
    canSharePdfFiles = canSharePdfFiles,
  )
}

/**
 * Whether the user signed in as [email] may collect data in [activeSurvey]: never in a closed
 * survey or without one, always while nobody is signed in (the workbench and sample sessions have a
 * blank email), and otherwise as [SurveyAccess.canCollectData] decides from the survey's sharing
 * settings and its organization among [organizations].
 *
 * A manager of the all-users or a synthetic (platform) organization is a platform admin, who may
 * collect in every survey.
 */
internal fun canCollectData(
  activeSurvey: SurveyPreviewItem?,
  email: String,
  organizations: List<Organization>,
): Boolean {
  if (activeSurvey == null || activeSurvey.isClosed) return false
  if (email.isBlank()) return true
  val org =
    activeSurvey.organizationId?.let { orgId -> organizations.firstOrNull { it.id == orgId } }
  val isPlatformAdmin = organizations.hasPlatformAdmin(email)
  val sharing =
    SharingSettings(
      ownerEmail = activeSurvey.ownerEmail,
      policy = activeSurvey.sharingPolicy,
      peerDataVisibility = activeSurvey.peerDataVisibility,
      collectorConsentRequired = activeSurvey.collectorConsentRequired,
      collectorConsentText = activeSurvey.collectorConsentText,
      defaultRole = activeSurvey.defaultRole,
    )
  return SurveyAccess.canCollectData(
    email = email,
    sharing = sharing,
    organization = org,
    isPlatformAdmin = isPlatformAdmin,
  )
}

/**
 * Checks the survey's and its organization's quotas before a submission is recorded, or returns
 * `null` when [data]'s active survey ID names no survey on the device (nothing to enforce).
 *
 * Unlike [DataCollectionData.activeSurvey], this doesn't fall back to the first survey: quotas are
 * checked only against the survey the submission is recorded in. The counts are of the signed-in
 * collector's own submissions on [activeEntityId] and of the field-created map features they
 * submitted to (field-created features without any submission count too), the synthetic (platform)
 * organization's limits apply as the global ones, and a submission is a new field-created feature
 * when the form opened without a target and none was picked ([wasFormLaunchedWithoutEntity] and no
 * [activeEntityId]).
 */
internal fun checkSubmissionQuota(
  data: DataCollectionData,
  activeEntityId: String?,
  wasFormLaunchedWithoutEntity: Boolean,
): QuotaCheckResult? {
  val activeSurvey = data.surveys.firstOrNull { it.id == data.activeSurveyId } ?: return null
  val email = data.profile.email
  val entity = activeEntityId?.let { id -> data.content.entities.firstOrNull { it.id == id } }
  val userSubmissionsForEntity =
    entity?.submissions?.count { it.collectorEmail.equals(email, ignoreCase = true) } ?: 0
  val userAdhocEntities =
    data.content.entities.count { ent ->
      ent.isFieldCreated &&
        (ent.submissions.isEmpty() ||
          ent.submissions.any { it.collectorEmail.equals(email, ignoreCase = true) })
    }
  val org =
    activeSurvey.organizationId?.let { id -> data.organizations.firstOrNull { it.id == id } }
  return QuotaAndCostEnforcer.checkCanCollectSubmission(
    userEmail = email,
    survey = activeSurvey,
    isNewAdhocEntity = wasFormLaunchedWithoutEntity && activeEntityId == null,
    userSubmissionsForEntityCount = userSubmissionsForEntity,
    userAdhocEntitiesCount = userAdhocEntities,
    organization = org,
    globalLimits = data.organizations.firstOrNull { it.isSynthetic }?.quotaLimits,
  )
}

/** The form the workbench's `▶ Test / Launch Form Now` opens; see [chooseTestingFormLaunch]. */
internal sealed interface TestingFormLaunch {
  /** Opens [formId] with no target feature, as from the FAB (map picker). */
  data class FromFab(val formId: String) : TestingFormLaunch

  /** Opens [formId] on the map feature [entityId]. */
  data class ForEntity(val entityId: String, val formId: String) : TestingFormLaunch
}

/**
 * The seeded sample map feature the workbench opens a form on when nothing is selected, so its
 * shortcut shows the same feature every time.
 */
internal const val WORKBENCH_DEFAULT_ENTITY_ID = "0000000a-56d7-5c1d-9457-7457267120a3"

/**
 * The form the workbench's shortcut opens in [ui], or `null` when the survey has no forms. In order
 * of preference: the first enabled form on the selected feature (or [WORKBENCH_DEFAULT_ENTITY_ID],
 * or the first feature); the first enabled form on any feature; a standalone form. A survey without
 * features opens its first form that needs no target, or else its first form.
 */
internal fun chooseTestingFormLaunch(ui: DataCollectionUiState): TestingFormLaunch? {
  val entities = ui.entities
  val forms = ui.forms
  if (entities.isEmpty()) {
    val standaloneForm = forms.firstOrNull { !it.requiresEntity } ?: forms.firstOrNull()
    return standaloneForm?.let { TestingFormLaunch.FromFab(it.id) }
  }
  fun enabledLaunchOn(entity: GeospatialEntityItem?): TestingFormLaunch? = entity?.let { e ->
    ui
      .formsForEntity(e)
      .firstOrNull { ui.isFormButtonEnabled(e, it) }
      ?.let { TestingFormLaunch.ForEntity(e.id, it.id) }
  }
  val currentEntity =
    ui.selectedEntity
      ?: entities.firstOrNull { it.id == WORKBENCH_DEFAULT_ENTITY_ID }
      ?: entities.firstOrNull()
  return enabledLaunchOn(currentEntity)
    ?: entities.firstNotNullOfOrNull { enabledLaunchOn(it) }
    ?: forms.firstOrNull()?.let { TestingFormLaunch.FromFab(it.id) }
}
