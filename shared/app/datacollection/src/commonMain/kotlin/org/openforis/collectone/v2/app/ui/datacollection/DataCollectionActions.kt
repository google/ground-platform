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
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.map.LatLng as MapLatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * The web dashboard map's hooks into the open form: the subset of [DataCollectionActions] the main
 * map and its overlays need, so they don't take the whole data collection surface.
 */
interface FormMapInteraction {
  /**
   * Adds a main-map click at [latLng] to the geometry question being drawn (no-op when nothing is
   * being drawn). A `geopoint` is placed by its first click, which also stops the drawing.
   */
  fun addWebMapDrawingVertex(latLng: MapLatLng)

  /** Asks the web form panel to bring the question at [path] into view and highlight it. */
  fun focusWebFormQuestion(path: String)

  /** Clears [DataCollectionUiState.webFormFocusRequest] once the form panel has handled it. */
  fun consumeWebFormFocusRequest()

  /** Asks the main map to centre [bounds] in its visible area, zooming in at most to [maxZoom]. */
  fun requestWebMapFraming(bounds: LngLatBounds, maxZoom: Double)
}

/** User intents of data collection. Implemented by [DataCollectionViewModel]. */
interface DataCollectionActions : FormMapInteraction, RecordShareActions {
  // --- Selected record ---

  /**
   * Opens full details for [submissionId] (from a `1:N` entity bottom sheet, the list, or
   * `Uploads`), or closes the open submission when `null`. Opening a submission of another map
   * feature selects and frames that feature.
   */
  fun selectSubmissionDetail(submissionId: String?)

  /**
   * Selects a submission geometry polygon on the map and opens its submission (and parent entity if
   * attached, or standalone submission card if unattached).
   */
  fun selectSubmissionGeometry(geometryId: String)

  /** Clears the selected record and returns to the expanded searchable list (mobile). */
  fun returnToBottomSheetList()

  /** Returns the selected feature's details surface to its properties, closing any submission. */
  fun showEntityProperties()

  /** Clears the selected map feature and submission (switching to the list, selecting a layer). */
  fun clearSelection()

  // --- Opening forms ---

  /** Opens the Available Forms sheet triggered by the mobile FAB. */
  fun openAvailableFormsSheet()

  fun closeAvailableFormsSheet()

  fun toggleAvailableFormsSheet()

  /**
   * Opens [formId] from the FAB / **Collect data** menu, with no target feature pre-selected. A
   * form that needs one gets the `entityref` step, whose Map or List picker starts in [pickerMode].
   */
  fun launchFormFromFab(formId: String, pickerMode: MainSurveyViewMode = MainSurveyViewMode.MAP)

  /** Opens [formId] on map feature [entityId] (a form button in the feature's details). */
  fun launchFormForEntity(entityId: String, formId: String)

  /**
   * Workbench shortcut (`▶ Test / Launch Form Now`): opens the first form enabled on the selected
   * feature (or a fallback feature), or a standalone form when the survey has no features.
   */
  fun launchActiveOrDefaultFormForTesting()

  /** Closes the open form without recording anything. */
  fun closeActiveFormRunner()

  /**
   * Accepts the active survey's informed consent prompt
   * ([DataCollectionUiState.pendingCollectorConsent]) and proceeds to open the requested form.
   */
  fun acceptCollectorConsent()

  /** Dismisses the informed consent prompt without opening a form. */
  fun dismissCollectorConsent()

  /**
   * Records the open form's submission from the finalized [recordInstance] and [entityStates]
   * (defaulting to the controller's current state), then closes the form and reports what was
   * recorded in a notice.
   *
   * With [showConfirmation] (the mobile app) the form stays open instead, showing its confirmation
   * ([DataCollectionUiState.isSubmissionConfirmationShown]) until [dismissSubmissionConfirmation],
   * and there is no notice.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance? = null,
    entityStates: List<EntityState>? = null,
    showConfirmation: Boolean = false,
  )

  /**
   * Leaves the submission confirmation: closes the form and shows the map feature the submission
   * added or updated (its properties, framed on the map), or the submission itself when it has no
   * map feature.
   */
  fun dismissSubmissionConfirmation()

  /**
   * Runs the workbench's custom definition ([formDef], or the built-in one when `null`) in the open
   * form, if any ([relaunchOpenForm]; the workbench keeps the open form while its XML is invalid).
   * [isPreset] tells whether the definition is one of the workbench's presets, whose own
   * `entityref` questions aren't replaced by the picker step.
   */
  fun updateCustomFormDef(formDef: FormDef?, isPreset: Boolean, relaunchOpenForm: Boolean = true)

  // --- `entityref` step ---

  fun updateEntityRefSelectorViewMode(mode: MainSurveyViewMode)

  fun updateEntityRefSearchQuery(query: String)

  fun clearEntityRefSearchQuery()

  /**
   * Picks [entityId] as the open form's target feature at the `entityref` step: selects it, fills
   * the form's entity-reference questions, and frames it on the step's map when it has geometry.
   */
  fun selectEntityRefForActiveForm(entityId: String)
}

/**
 * The GeoID QR code and record PDF export intents of data collection: the subset of
 * [DataCollectionActions] the record share dialogs need.
 */
interface RecordShareActions {
  fun openEntityQrCode(entityId: String)

  fun closeEntityQrCode()

  /**
   * Generates the map feature's PDF and opens the Share PDF sheet to share, save, or preview it.
   */
  fun shareEntityPdf(entityId: String)

  /** Generates the submission's PDF and opens the Share PDF sheet to share, save, or preview it. */
  fun shareSubmissionPdf(submissionId: String)

  /** Generates the map feature's PDF and saves it straight away (web dashboard). */
  fun downloadEntityPdf(entityId: String)

  /** Generates the submission's PDF and saves it straight away (web dashboard). */
  fun downloadSubmissionPdf(submissionId: String)

  /** Opens the system share sheet for the PDF in the Share PDF sheet, titled [title]. */
  fun shareActivePdf(title: String)

  /** Saves the PDF in the Share PDF sheet to the device. */
  fun saveActivePdf()

  /** Opens the PDF in the Share PDF sheet in the platform's viewer. */
  fun previewActivePdf()

  fun closeSharePdfSheet()

  fun dismissPdfExportMessage()
}
