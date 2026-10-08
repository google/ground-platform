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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SharedPdfSheetState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.LaunchFormUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.map.DraftGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.map.FormGeometryOverlay

/**
 * Outcomes of data collection actions that reach beyond the slice, applied by the app shell to the
 * map viewport, the dashboard, or the shell's own navigation.
 */
sealed interface DataCollectionEvent {
  /**
   * The selected record changed: the map highlights [entityId] (or nothing), framing it again when
   * [frameAgain] even if it was already selected.
   */
  data class EntityHighlighted(val entityId: String?, val frameAgain: Boolean = false) :
    DataCollectionEvent

  /** The map should pan to [entity] (a feature picked at the `entityref` step). */
  data class MapRecenteredOnEntity(val entity: GeospatialEntityItem) : DataCollectionEvent

  /** The entity bottom sheet should expand (full details) or collapse (peek). */
  data class EntityBottomSheetExpanded(val expanded: Boolean) : DataCollectionEvent

  /** The `Layers` sheet should close (a record took over the map). */
  data object LayersSheetClosed : DataCollectionEvent

  /** The selected place (search result) should clear (a record took over the map). */
  data object PlaceSelectionCleared : DataCollectionEvent

  /** The available-forms sheet opened: the shell closes the navigation drawer. */
  data object FormsSheetOpened : DataCollectionEvent

  /**
   * A submission was opened for inspection: the dashboard shows the submissions pane and switches
   * its table to [tableDatasetId] (the parent feature's dataset) when given.
   */
  data class SubmissionOpened(val tableDatasetId: String?) : DataCollectionEvent

  /** The details surface returned to the selected feature's properties. */
  data object DetailsReturnedToProperties : DataCollectionEvent

  /** The mobile bottom sheet should show the searchable list again. */
  data object ListShown : DataCollectionEvent

  /** A form opened: the shell shows the Main Survey screen and closes the drawer. */
  data object FormOpened : DataCollectionEvent

  /** A message to show in the active survey notice. */
  data class Notice(val message: String) : DataCollectionEvent
}

/**
 * Screen state of data collection: the selected record (map feature and / or submission) shown in
 * the entity bottom sheet and the dashboard's details card, the open form (its wizard controller,
 * target feature, and `entityref` picker), the web dashboard's draw-on-map session, and the QR code
 * and PDF export dialogs.
 *
 * [activeFormWizardController] and [webMapDrawing] are UI infrastructure from `shared/ui`, the only
 * non-domain values in the state: the controller drives the shared form runner and holds the form's
 * answers as Compose snapshot state, and the drawing host writes map clicks into it. Properties
 * that read them ([isCurrentFormStepEntityRef], [webFormGeometries], ...) are computed on access so
 * composables see the latest answers.
 */
data class DataCollectionUiState(
  // --- Data (from the local data store) ---
  val activeSurveyId: String = "",
  val activeSurveyTitle: String = "",
  val forms: List<FormPreviewItem> = emptyList(),
  val entities: List<GeospatialEntityItem> = emptyList(),
  /** Submissions recorded without an attached map feature (`entityId == ""`). */
  val standaloneSubmissions: List<SubmissionPreviewItem> = emptyList(),
  val submissionGeometries: List<SubmissionGeometryPolygon> = emptyList(),
  /** The signed-in collector's name, shown on standalone records. */
  val collectorName: String = "",

  // --- Selected record ---
  /** ID of the selected map feature (the entity bottom sheet / details card), or `null`. */
  val selectedEntityId: String? = null,
  /** ID of the submission open for inspection, or `null`. */
  val selectedSubmissionId: String? = null,

  // --- Open form ---
  /**
   * Active [FormWizardController] driving the embedded form runner (mobile) or the compact form
   * panel (web), or `null` when no form is open.
   */
  val activeFormWizardController: FormWizardController? = null,
  /** Target map feature of the open form, or `null` (not picked yet, or a standalone form). */
  val activeDataCollectionEntityId: String? = null,
  /** The Form that is open, or `null`. */
  val activeDataCollectionFormId: String? = null,
  /**
   * True when the open form was launched from the FAB / **Collect data** menu without a target
   * feature, so a form that needs one shows the `entityref` Map or List selector.
   */
  val wasFormLaunchedWithoutEntity: Boolean = false,
  /** Whether the Available Forms sheet (mobile FAB) is open. */
  val isAvailableFormsSheetOpen: Boolean = false,
  /** `Map` or `List` picker at the `entityref` step. */
  val entityRefSelectorViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
  /** Search query of the `entityref` step's `List` picker. */
  val entityRefSearchQuery: String = "",
  /**
   * Incremented each time a map feature is picked at the `entityref` step, so the step's map frames
   * it again even when the same feature is picked twice.
   */
  val entityRefFramingEpoch: Long = 0L,
  /**
   * The workbench's custom XForms definition, run instead of a Form's built-in definition when a
   * form opens (`null` runs the built-in one).
   */
  val customFormDef: FormDef? = null,

  // --- Web dashboard: draw on map, framing, focus ---
  /** Draw-on-map host for the open form's geometry questions (web dashboard). */
  val webMapDrawing: WebMapDrawingHost? = null,
  /** Bounds the dashboard's main map should frame next (**Zoom to fit**), or `null`. */
  val webMapFramingRequest: MapFramingRequest? = null,
  /** The question the web form panel should scroll to and highlight, or `null`. */
  val webFormFocusRequest: FormFocusRequest? = null,

  // --- QR code & PDF export ---
  /** Map feature whose GeoID QR code dialog is open, or `null`. */
  val activeQrCodeEntityId: String? = null,
  /** The open Share PDF sheet, or `null`. */
  val activeSharedPdfSheet: SharedPdfSheetState? = null,
  /** Short confirmation or error after a PDF action (e.g. `"Saved …pdf"`), or `null`. */
  val pdfExportMessage: String? = null,
  /** True when the platform can hand files to other apps via the system share sheet. */
  val canSharePdfFiles: Boolean = false,
) {
  /** Forms collectors can start from the mobile app's entry points. */
  val mobileForms: List<FormPreviewItem>
    get() = forms.filter { it.availability.includesMobile }

  /** Forms collectors can start from the web dashboard's entry points. */
  val webForms: List<FormPreviewItem>
    get() = forms.filter { it.availability.includesWeb }

  /** All submissions (both entity-attached and standalone) in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions } + standaloneSubmissions

  val selectedEntity: GeospatialEntityItem?
    get() = selectedEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  val selectedSubmission: SubmissionPreviewItem?
    get() = selectedSubmissionId?.let { id -> allSubmissions.firstOrNull { it.id == id } }

  /** Whether a map feature or submission is selected. */
  val hasSelectedRecord: Boolean
    get() = selectedEntityId != null || selectedSubmissionId != null

  /** True when a form is open (the embedded runner or the web form panel). */
  val isDataCollectionFormOpen: Boolean
    get() = activeFormWizardController != null

  val activeDataCollectionEntity: GeospatialEntityItem?
    get() = activeDataCollectionEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  val activeDataCollectionForm: FormPreviewItem?
    get() = activeDataCollectionFormId?.let { id -> forms.firstOrNull { it.id == id } }

  val activeQrCodeEntity: GeospatialEntityItem?
    get() = activeQrCodeEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /**
   * The forms that request entities of [entity]'s dataset type and are available on the asking
   * platform: the web dashboard when [onWeb], otherwise the mobile app.
   */
  fun formsForEntity(entity: GeospatialEntityItem, onWeb: Boolean = false): List<FormPreviewItem> =
    (if (onWeb) webForms else mobileForms).filter { it.targetDatasetId == entity.datasetId }

  /**
   * Whether the organizer-defined action button for [form] is enabled on [entity]. Because all
   * forms are `1:N` with entities, any form targeting [entity]'s dataset is enabled.
   */
  fun isFormButtonEnabled(entity: GeospatialEntityItem, form: FormPreviewItem): Boolean =
    !form.requiresEntity || entity.datasetId == form.targetDatasetId

  /** All map features in [form]'s target dataset (`form.targetDatasetId`). */
  fun allDatasetEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    if (form.requiresEntity) entities.filter { it.datasetId == form.targetDatasetId }
    else emptyList()

  /** The map features in [form]'s target dataset that can accept a new submission for [form]. */
  fun eligibleEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    allDatasetEntitiesForForm(form).filter { isFormButtonEnabled(it, form) }

  /** Eligible map features for the open form. */
  val eligibleEntitiesForActiveForm: List<GeospatialEntityItem>
    get() = activeDataCollectionForm?.let { eligibleEntitiesForForm(it) } ?: emptyList()

  /**
   * Candidate features of the open form's target dataset filtered by [entityRefSearchQuery] (the
   * `entityref` step's `List` picker).
   */
  val filteredEntityRefCandidates: List<GeospatialEntityItem>
    get() {
      val form = activeDataCollectionForm ?: return emptyList()
      val base = allDatasetEntitiesForForm(form)
      val q = entityRefSearchQuery.trim()
      if (q.isEmpty()) return base
      return base.filter { entity ->
        entity.label.contains(q, ignoreCase = true) ||
          entity.geoId.contains(q, ignoreCase = true) ||
          entity.datasetName.contains(q, ignoreCase = true) ||
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /** True when the open form's current step is an `entityref` step (pick the target feature). */
  val isCurrentFormStepEntityRef: Boolean
    get() = LaunchFormUseCase.isEntityRefStep(activeFormWizardController?.currentStep)

  /** True when the open form's current step holds a geometry question. */
  val isCurrentFormStepGeoPoint: Boolean
    get() = LaunchFormUseCase.isGeometryStep(activeFormWizardController?.currentStep)

  /** True while the dashboard's main map is turning clicks into a geometry answer. */
  val isWebMapDrawing: Boolean
    get() = webMapDrawing?.isDrawing == true

  /** The geometry being drawn on the main map, for its overlay, or `null` when not drawing. */
  val webMapDraftGeometry: DraftGeometry?
    get() = webMapDrawing?.draftGeometry

  /**
   * Every geometry answer held by the open form (web dashboard), for the main map's in-flow
   * overlay: each `geopoint` / `geotrace` / `geoshape` field with a value, except the one being
   * drawn right now (the draft overlay shows that one). Empty when no form is open.
   */
  val webFormGeometries: List<FormGeometryOverlay>
    get() {
      val controller = activeFormWizardController ?: return emptyList()
      return formGeometryOverlays(controller, excludePath = webMapDrawing?.activeDrawingPath)
    }
}
