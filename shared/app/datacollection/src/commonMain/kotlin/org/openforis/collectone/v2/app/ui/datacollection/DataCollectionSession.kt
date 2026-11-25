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

import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.core.forms.ui.FormWizardController

/**
 * Everything [DataCollectionViewModel] reads from the local data store, combined into one snapshot
 * so the screen state is always built from values of the same moment.
 */
internal data class DataCollectionData(
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val activeSurveyId: String = "",
  val content: SurveyContent = SurveyContent(),
  val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val profile: AuthProfile = AuthProfile("", "", ""),
  val organizations: List<Organization> = emptyList(),
) {
  /**
   * The survey [activeSurveyId] names, or the first survey on the device while the stored ID is
   * blank or not (yet) among [surveys], so the screen still has a title and access rules.
   */
  val activeSurvey: SurveyPreviewItem?
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.firstOrNull()
}

/** A form launch deferred until the collector accepts the survey's informed consent. */
internal sealed interface PendingFormLaunch {
  data class FromFab(val formId: String, val pickerMode: MainSurveyViewMode) : PendingFormLaunch

  data class ForEntity(val entityId: String, val formId: String) : PendingFormLaunch
}

/**
 * The session (in-memory) state of data collection. Only [selectedEntityId] and
 * [selectedSubmissionId] outlive the process, through the ViewModel's `SavedStateHandle`; the open
 * form, its controller, and the dialogs are lost with it. The PDF export dialogs keep their own
 * state in [DataCollectionPdfExports].
 */
internal data class DataCollectionSession(
  val selectedEntityId: String? = null,
  val selectedSubmissionId: String? = null,
  val controller: FormWizardController? = null,
  val activeEntityId: String? = null,
  val activeFormId: String? = null,
  val wasFormLaunchedWithoutEntity: Boolean = false,
  /** The open form's submission was recorded and its confirmation is showing. */
  val isSubmissionConfirmationShown: Boolean = false,
  val isAvailableFormsSheetOpen: Boolean = false,
  val entityRefSelectorViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
  val entityRefSearchQuery: String = "",
  val entityRefFramingEpoch: Long = 0L,
  val customFormDef: FormDef? = null,
  val webMapFramingRequest: MapFramingRequest? = null,
  val webFormFocusRequest: FormFocusRequest? = null,
  val activeQrCodeEntityId: String? = null,
  /** Survey IDs for which the user has accepted informed consent in this session. */
  val consentedSurveyIds: Set<String> = emptySet(),
  /** Pending form launch paused while the informed consent dialog is displayed. */
  val pendingConsentLaunch: PendingFormLaunch? = null,
)
