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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.core.forms.model.EntityState
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.GeneratedPdf
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportClient
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportResult
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PlatformPdfExportClient
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.RecordPdfReports
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SharedPdfSheetState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LocationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.LaunchFormUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.common.platformEpochMillis
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.ENTITY_REF_FIELD_PATH
import org.groundplatform.v2.devtools.prototypeapp.ui.map.EntityGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormFocusRequest
import org.groundplatform.v2.devtools.prototypeapp.ui.state.MapFramingRequest
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WebMapDrawingHost
import org.groundplatform.v2.map.LatLng as MapLatLng
import org.groundplatform.v2.map.LngLatBounds

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
interface DataCollectionActions : FormMapInteraction {
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
   * Records the open form's submission from the finalized [recordInstance] and [entityStates]
   * (defaulting to the controller's current state), then closes the form.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance? = null,
    entityStates: List<EntityState>? = null,
  )

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

  // --- QR code & PDF export ---

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

  /** Opens the system share sheet for the PDF in the Share PDF sheet. */
  fun shareActivePdf()

  /** Saves the PDF in the Share PDF sheet to the device. */
  fun saveActivePdf()

  /** Opens the PDF in the Share PDF sheet in the platform's viewer. */
  fun previewActivePdf()

  fun closeSharePdfSheet()

  fun dismissPdfExportMessage()
}

/**
 * ViewModel of data collection: the selected record (map feature and / or submission) that the
 * entity bottom sheet and the dashboard's details card show, the form being filled in (opened from
 * the FAB, the **Collect data** menu, or a feature's form buttons), the web dashboard's draw-on-map
 * session, and the GeoID QR code and PDF export dialogs.
 *
 * This is the one owner of record selection. The map viewport highlights the same feature, and
 * selections made on the map come back through the app shell ([onEntitySelected],
 * [onNavigationStarted]); selections made here go out as [DataCollectionEvent]s the shell applies
 * to the map and dashboard.
 *
 * Survey content comes from [SurveyRepository], the collector from [AuthRepository], the unit
 * system from [SettingsRepository], and the GPS fix from [LocationRepository]. Forms open through
 * [LaunchFormUseCase] and complete through [CompleteFormSubmissionUseCase]. PDFs are generated by
 * `RecordPdfReports` and delivered by [PdfExportClient], UI infrastructure this ViewModel may call
 * directly (see the client's documentation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataCollectionViewModel(
  surveyRepository: SurveyRepository,
  settingsRepository: SettingsRepository,
  authRepository: AuthRepository,
  private val locationRepository: LocationRepository,
  private val completeFormSubmission: CompleteFormSubmissionUseCase,
  private val launchForm: LaunchFormUseCase = LaunchFormUseCase(),
  private val pdfExportClient: PdfExportClient = PlatformPdfExportClient(),
  private val now: () -> Long = { platformEpochMillis() },
  private val scope: CoroutineScope,
) : DataCollectionActions {
  /** Everything data collection reads from the local data store. */
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
    val profile: AuthProfile = AuthProfile("", "", ""),
  ) {
    val activeSurvey: SurveyPreviewItem?
      get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.firstOrNull()

    val anchor: SurveyMapAnchor
      get() = SurveyMapAnchor.forSurvey(activeSurveyId)
  }

  /** Session (non-persisted) state of data collection. */
  private data class Session(
    val selectedEntityId: String? = null,
    val selectedSubmissionId: String? = null,
    val controller: FormWizardController? = null,
    val activeEntityId: String? = null,
    val activeFormId: String? = null,
    val wasFormLaunchedWithoutEntity: Boolean = false,
    val isAvailableFormsSheetOpen: Boolean = false,
    val entityRefSelectorViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
    val entityRefSearchQuery: String = "",
    val entityRefFramingEpoch: Long = 0L,
    val customFormDef: FormDef? = null,
    val webMapFramingRequest: MapFramingRequest? = null,
    val webFormFocusRequest: FormFocusRequest? = null,
    val activeQrCodeEntityId: String? = null,
    val activeSharedPdfSheet: SharedPdfSheetState? = null,
    /** The PDF generated for [activeSharedPdfSheet] (`null` when the sheet is closed). */
    val activePdf: GeneratedPdf? = null,
    val pdfExportMessage: String? = null,
  )

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        settingsRepository.observeUserSettings(),
        authRepository.observeSession(),
      ) { surveys, (activeId, content), settings, auth ->
        Data(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          unitSystem = settings.measurementUnits,
          profile = auth.profile,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<DataCollectionEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of data collection actions that the app shell applies outside this slice. */
  val events: Flow<DataCollectionEvent> = _events.asSharedFlow()

  /**
   * The web dashboard's draw-on-map host for the open form's geometry questions. Mobile never
   * starts drawing (its widgets capture the device GPS), so [WebMapDrawingHost.isDrawing] stays
   * `false` there.
   */
  val webMapDrawing =
    WebMapDrawingHost(
      controller = { session.value.controller },
      onFrame = { bounds, maxZoom -> requestWebMapFraming(bounds, maxZoom) },
    )

  val uiState: StateFlow<DataCollectionUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(scope, SharingStarted.Eagerly, buildUiState(Data(), Session()))

  private var framingToken = 0L
  private var focusToken = 0L

  private fun buildUiState(data: Data, session: Session): DataCollectionUiState =
    DataCollectionUiState(
      activeSurveyId = data.activeSurveyId,
      activeSurveyTitle = data.activeSurvey?.title.orEmpty(),
      forms = data.content.forms,
      entities = data.content.entities,
      standaloneSubmissions = data.content.standaloneSubmissions,
      submissionGeometries = data.content.submissionGeometries,
      collectorName = data.profile.displayName,
      selectedEntityId = session.selectedEntityId,
      selectedSubmissionId = session.selectedSubmissionId,
      activeFormWizardController = session.controller,
      activeDataCollectionEntityId = session.activeEntityId,
      activeDataCollectionFormId = session.activeFormId,
      wasFormLaunchedWithoutEntity = session.wasFormLaunchedWithoutEntity,
      isAvailableFormsSheetOpen = session.isAvailableFormsSheetOpen,
      entityRefSelectorViewMode = session.entityRefSelectorViewMode,
      entityRefSearchQuery = session.entityRefSearchQuery,
      entityRefFramingEpoch = session.entityRefFramingEpoch,
      customFormDef = session.customFormDef,
      webMapDrawing = webMapDrawing,
      webMapFramingRequest = session.webMapFramingRequest,
      webFormFocusRequest = session.webFormFocusRequest,
      activeQrCodeEntityId = session.activeQrCodeEntityId,
      activeSharedPdfSheet = session.activeSharedPdfSheet,
      pdfExportMessage = session.pdfExportMessage,
      canSharePdfFiles = pdfExportClient.canShareFiles,
    )

  private val ui: DataCollectionUiState
    get() = uiState.value

  private fun emit(event: DataCollectionEvent) {
    _events.tryEmit(event)
  }

  /** Returns session state to its defaults (used by the prototype's Reset). */
  fun reset() {
    setController(null)
    session.value = Session(customFormDef = session.value.customFormDef)
  }

  /**
   * A different survey became active: drops the selected record, the open form, and the forms
   * sheet, which were scoped to the previous survey.
   */
  fun onSurveyActivated() {
    setController(null)
    session.update {
      it.copy(
        selectedEntityId = null,
        selectedSubmissionId = null,
        activeEntityId = null,
        activeFormId = null,
        isAvailableFormsSheetOpen = false,
      )
    }
    emit(DataCollectionEvent.EntityHighlighted(null))
    emit(DataCollectionEvent.EntityBottomSheetExpanded(false))
  }

  /**
   * Follows a map feature selected (or cleared) on the map, in a list, or by a cluster / place /
   * layer change: it becomes the selected record and any open submission closes.
   */
  fun onEntitySelected(entityId: String?) {
    session.update { it.copy(selectedEntityId = entityId, selectedSubmissionId = null) }
  }

  /** Follows straight-line navigation starting toward [entityId] and / or [submissionId]. */
  fun onNavigationStarted(entityId: String?, submissionId: String?) {
    session.update { it.copy(selectedEntityId = entityId, selectedSubmissionId = submissionId) }
  }

  /**
   * Sets the open form's controller. Opening, replacing, or closing a form ends any map drawing
   * started for the previous one, and drops a pending jump to one of its questions.
   */
  private fun setController(controller: FormWizardController?) {
    if (controller !== session.value.controller) {
      webMapDrawing.stopDrawing()
      session.update { it.copy(controller = controller, webFormFocusRequest = null) }
    }
  }

  // --- Selected record ---

  override fun selectSubmissionDetail(submissionId: String?) {
    if (submissionId == null) {
      session.update { it.copy(selectedSubmissionId = null) }
      return
    }
    val parentEntity = ui.entities.firstOrNull { e -> e.submissions.any { it.id == submissionId } }
    // Opening a submission of another map feature (e.g. from `Uploads`) frames that feature.
    val framesOtherEntity = parentEntity != null && parentEntity.id != ui.selectedEntityId
    session.update {
      it.copy(selectedSubmissionId = submissionId, selectedEntityId = parentEntity?.id)
    }
    emit(DataCollectionEvent.EntityBottomSheetExpanded(true))
    emit(
      DataCollectionEvent.SubmissionOpened(
        tableDatasetId = if (framesOtherEntity) parentEntity?.datasetId else null
      )
    )
    emit(DataCollectionEvent.EntityHighlighted(parentEntity?.id, frameAgain = framesOtherEntity))
  }

  override fun selectSubmissionGeometry(geometryId: String) {
    val geom = ui.submissionGeometries.firstOrNull { it.id == geometryId } ?: return
    val entityId = geom.entityId.takeIf { it.isNotBlank() }
    session.update {
      it.copy(selectedEntityId = entityId, selectedSubmissionId = geom.submissionId)
    }
    emit(DataCollectionEvent.PlaceSelectionCleared)
    emit(DataCollectionEvent.EntityHighlighted(entityId))
    emit(DataCollectionEvent.EntityBottomSheetExpanded(true))
    emit(DataCollectionEvent.LayersSheetClosed)
  }

  override fun returnToBottomSheetList() {
    session.update { it.copy(selectedEntityId = null, selectedSubmissionId = null) }
    emit(DataCollectionEvent.EntityHighlighted(null))
    emit(DataCollectionEvent.DetailsReturnedToProperties)
    emit(DataCollectionEvent.EntityBottomSheetExpanded(true))
    emit(DataCollectionEvent.ListShown)
    emit(DataCollectionEvent.LayersSheetClosed)
  }

  override fun showEntityProperties() {
    session.update { it.copy(selectedSubmissionId = null) }
    emit(DataCollectionEvent.DetailsReturnedToProperties)
  }

  override fun clearSelection() {
    session.update { it.copy(selectedEntityId = null, selectedSubmissionId = null) }
    emit(DataCollectionEvent.EntityHighlighted(null))
  }

  // --- Opening forms ---

  override fun openAvailableFormsSheet() {
    session.update { it.copy(isAvailableFormsSheetOpen = true) }
    emit(DataCollectionEvent.LayersSheetClosed)
    emit(DataCollectionEvent.FormsSheetOpened)
  }

  override fun closeAvailableFormsSheet() {
    session.update { it.copy(isAvailableFormsSheetOpen = false) }
  }

  override fun toggleAvailableFormsSheet() {
    if (ui.isAvailableFormsSheetOpen) closeAvailableFormsSheet() else openAvailableFormsSheet()
  }

  override fun launchFormFromFab(formId: String, pickerMode: MainSurveyViewMode) {
    val current = ui
    val form = current.forms.firstOrNull { it.id == formId } ?: return
    val controller =
      launchForm(
        customFormDef = current.customFormDef,
        form = form,
        entity = null,
        candidateEntities = current.eligibleEntitiesForForm(form),
        includeEntityRefStep = form.requiresEntity,
      )
    setController(controller)
    session.update {
      it.copy(
        isAvailableFormsSheetOpen = false,
        wasFormLaunchedWithoutEntity = true,
        entityRefSelectorViewMode = pickerMode,
        entityRefSearchQuery = "",
        activeEntityId = null,
        activeFormId = form.id,
      )
    }
    emit(DataCollectionEvent.LayersSheetClosed)
    emit(DataCollectionEvent.FormOpened)
  }

  override fun launchFormForEntity(entityId: String, formId: String) {
    val current = ui
    val entity = current.entities.firstOrNull { it.id == entityId } ?: return
    val form = current.forms.firstOrNull { it.id == formId } ?: return
    if (!current.isFormButtonEnabled(entity, form)) return
    val controller = launchForm(customFormDef = current.customFormDef, form = form, entity = entity)
    setController(controller)
    session.update {
      it.copy(
        isAvailableFormsSheetOpen = false,
        wasFormLaunchedWithoutEntity = false,
        selectedEntityId = entity.id,
        activeEntityId = entity.id,
        activeFormId = form.id,
      )
    }
    emit(DataCollectionEvent.EntityHighlighted(entity.id))
    emit(DataCollectionEvent.LayersSheetClosed)
    emit(DataCollectionEvent.FormOpened)
  }

  override fun launchActiveOrDefaultFormForTesting() {
    val current = ui
    val entities = current.entities
    val forms = current.forms
    if (entities.isEmpty()) {
      val standaloneForm = forms.firstOrNull { !it.requiresEntity } ?: forms.firstOrNull() ?: return
      launchFormFromFab(standaloneForm.id)
      return
    }
    fun enabledFormOn(entity: GeospatialEntityItem) =
      current.formsForEntity(entity).firstOrNull { current.isFormButtonEnabled(entity, it) }
    val currentEntity =
      current.selectedEntity
        ?: entities.firstOrNull { it.id == "entity-shade-201" }
        ?: entities.firstOrNull()
    if (currentEntity != null) {
      enabledFormOn(currentEntity)?.let { form ->
        launchFormForEntity(currentEntity.id, form.id)
        return
      }
    }
    val fallbackEntity = entities.firstOrNull { enabledFormOn(it) != null }
    if (fallbackEntity != null) {
      val fallbackForm = enabledFormOn(fallbackEntity) ?: return
      launchFormForEntity(fallbackEntity.id, fallbackForm.id)
      return
    }
    val standaloneFallback = forms.firstOrNull() ?: return
    launchFormFromFab(standaloneFallback.id)
  }

  override fun closeActiveFormRunner() {
    setController(null)
    session.update {
      it.copy(
        activeEntityId = null,
        activeFormId = null,
        wasFormLaunchedWithoutEntity = false,
        entityRefSearchQuery = "",
      )
    }
  }

  override fun completeActiveFormSubmission(
    recordInstance: RecordInstance?,
    entityStates: List<EntityState>?,
  ) {
    val current = ui
    val controller = current.activeFormWizardController
    val location = locationRepository.getLocationSnapshot()
    val profile = data.value.profile
    val unitSystem = data.value.unitSystem
    scope.launch {
      val result =
        completeFormSubmission(
          recordInstance =
            recordInstance ?: controller?.formState?.recordInstance ?: RecordInstance(),
          entityStates = entityStates ?: controller?.formState?.entityStates ?: emptyList(),
          controller = controller,
          activeDataCollectionFormId = current.activeDataCollectionFormId,
          activeDataCollectionEntityId = current.activeDataCollectionEntityId,
          wasFormLaunchedWithoutEntity = current.wasFormLaunchedWithoutEntity,
          selectedEntityId = current.selectedEntityId,
          customFormDef = current.customFormDef,
          gnssStatusChipLabel = unitSystem.formatGnssAccuracy(location.gnssAccuracyMeters),
          signedInUserName = profile.displayName,
          signedInUserEmail = profile.email,
          userGpsCoordinatesLabel = location.coordinatesLabel,
          userGpsNormalizedX = location.normalizedX,
          userGpsNormalizedY = location.normalizedY,
        ) ?: return@launch
      session.update {
        it.copy(
          selectedEntityId = result.selectedEntityId,
          selectedSubmissionId =
            if (result.updateSelectedSubmissionId) result.selectedSubmissionId
            else it.selectedSubmissionId,
        )
      }
      emit(DataCollectionEvent.EntityHighlighted(result.selectedEntityId))
      emit(DataCollectionEvent.Notice(result.noticeMessage))
      closeActiveFormRunner()
    }
  }

  override fun updateCustomFormDef(
    formDef: FormDef?,
    isPreset: Boolean,
    relaunchOpenForm: Boolean,
  ) {
    session.update { it.copy(customFormDef = formDef) }
    val current = ui
    if (!relaunchOpenForm || current.activeFormWizardController == null) return
    val form = current.activeDataCollectionForm ?: current.forms.firstOrNull() ?: return
    val includeEntityRefStep =
      current.wasFormLaunchedWithoutEntity && form.requiresEntity && (formDef == null || !isPreset)
    setController(
      launchForm(
        customFormDef = formDef,
        form = form,
        entity = null,
        candidateEntities = current.eligibleEntitiesForForm(form),
        includeEntityRefStep = includeEntityRefStep,
        // The refreshed form keeps pointing at the feature already picked, if any.
        defaultSelectedEntityId = current.activeDataCollectionEntityId.orEmpty(),
      )
    )
  }

  // --- `entityref` step ---

  override fun updateEntityRefSelectorViewMode(mode: MainSurveyViewMode) {
    session.update { it.copy(entityRefSelectorViewMode = mode) }
  }

  override fun updateEntityRefSearchQuery(query: String) {
    session.update { it.copy(entityRefSearchQuery = query) }
  }

  override fun clearEntityRefSearchQuery() = updateEntityRefSearchQuery("")

  override fun selectEntityRefForActiveForm(entityId: String) {
    val current = ui
    val entity = current.entities.firstOrNull { it.id == entityId } ?: return
    val form = current.activeDataCollectionForm
    if (form != null && form.requiresEntity) {
      if (entity.datasetId != form.targetDatasetId || !current.isFormButtonEnabled(entity, form)) {
        return
      }
    }
    session.update {
      it.copy(
        activeEntityId = entity.id,
        selectedEntityId = entity.id,
        entityRefFramingEpoch =
          if (entity.hasGeometry) it.entityRefFramingEpoch + 1 else it.entityRefFramingEpoch,
      )
    }
    emit(DataCollectionEvent.EntityHighlighted(entity.id))
    if (entity.hasGeometry) emit(DataCollectionEvent.MapRecenteredOnEntity(entity))
    current.activeFormWizardController?.let { ctrl ->
      ctrl.updateString(LaunchFormUseCase.ENTITY_REF_FIELD_PATH, entity.id)
      launchForm.prepopulateEntityReference(ctrl, entity.id)
    }
  }

  // --- Web dashboard: draw on map, framing, focus ---

  override fun addWebMapDrawingVertex(latLng: MapLatLng) = webMapDrawing.addVertex(latLng)

  override fun focusWebFormQuestion(path: String) {
    focusToken += 1
    session.update { it.copy(webFormFocusRequest = FormFocusRequest(path, focusToken)) }
  }

  override fun consumeWebFormFocusRequest() {
    session.update { it.copy(webFormFocusRequest = null) }
  }

  override fun requestWebMapFraming(bounds: LngLatBounds, maxZoom: Double) {
    framingToken += 1
    session.update {
      it.copy(webMapFramingRequest = MapFramingRequest(bounds, maxZoom, framingToken))
    }
  }

  // --- QR code & PDF export ---

  override fun openEntityQrCode(entityId: String) {
    session.update { it.copy(activeQrCodeEntityId = entityId) }
  }

  override fun closeEntityQrCode() {
    session.update { it.copy(activeQrCodeEntityId = null) }
  }

  /**
   * Generates a PDF report for the map feature [entityId] on the device (offline): its status,
   * details, location, properties, and submissions. Returns `null` for unknown IDs.
   */
  internal fun generateEntityPdf(entityId: String): GeneratedPdf? {
    val current = data.value
    val entities = current.content.entities
    val entity = entities.firstOrNull { it.id == entityId } ?: return null
    return RecordPdfReports.entityReport(
      entity = entity,
      surveyTitle = current.activeSurvey?.title.orEmpty(),
      geometry = EntityGeometry.of(entity, current.anchor),
      unitSystem = current.unitSystem,
      generatedAtEpochMillis = now(),
      relatedLabelFor = { value -> entities.relatedEntityForPropertyValue(entity, value)?.label },
    )
  }

  /** Generates a PDF report for the submission [submissionId] on the device (offline). */
  internal fun generateSubmissionPdf(submissionId: String): GeneratedPdf? {
    val current = data.value
    val submission = ui.allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    return RecordPdfReports.submissionReport(
      submission = submission,
      surveyTitle = current.activeSurvey?.title.orEmpty(),
      entity = current.content.entities.firstOrNull { it.id == submission.entityId },
      generatedAtEpochMillis = now(),
    )
  }

  override fun shareEntityPdf(entityId: String) {
    val entity = ui.entities.firstOrNull { it.id == entityId } ?: return
    val pdf = generateEntityPdf(entityId) ?: return
    openPdfSheet(
      pdf,
      SharedPdfSheetState(
        targetId = entity.id,
        title = "${entity.singularTypeLabel} report",
        subtitle = "${entity.label} • GeoID ${entity.geoId}",
        pdfFileName = pdf.fileName,
        targetKindLabel = "${entity.singularTypeLabel} report",
        pageCount = pdf.pageCount,
        fileSizeLabel = pdf.sizeLabel,
      ),
    )
  }

  override fun shareSubmissionPdf(submissionId: String) {
    val sub = ui.allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val pdf = generateSubmissionPdf(submissionId) ?: return
    openPdfSheet(
      pdf,
      SharedPdfSheetState(
        targetId = sub.id,
        title = "${sub.formTitle} submission",
        subtitle = "${sub.collectorName} • ${sub.timestamp}",
        pdfFileName = pdf.fileName,
        targetKindLabel = "${sub.formTitle} submission",
        pageCount = pdf.pageCount,
        fileSizeLabel = pdf.sizeLabel,
      ),
    )
  }

  override fun downloadEntityPdf(entityId: String) {
    generateEntityPdf(entityId)?.let(::savePdf)
  }

  override fun downloadSubmissionPdf(submissionId: String) {
    generateSubmissionPdf(submissionId)?.let(::savePdf)
  }

  override fun shareActivePdf() {
    val pdf = session.value.activePdf ?: return
    val title = session.value.activeSharedPdfSheet?.title ?: pdf.fileName
    pdfExportClient.share(pdf.fileName, title, pdf.bytes) { result ->
      when (result) {
        PdfExportResult.SHARED -> closeSharePdfSheet()
        PdfExportResult.SAVED -> {
          closeSharePdfSheet()
          session.update { it.copy(pdfExportMessage = "Saved ${pdf.fileName}") }
        }
        PdfExportResult.CANCELLED -> Unit
        PdfExportResult.FAILED ->
          session.update { it.copy(pdfExportMessage = "Couldn't share ${pdf.fileName}") }
      }
    }
  }

  override fun saveActivePdf() {
    val pdf = session.value.activePdf ?: return
    savePdf(pdf)
    closeSharePdfSheet()
  }

  override fun previewActivePdf() {
    val pdf = session.value.activePdf ?: return
    pdfExportClient.preview(pdf.fileName, pdf.bytes)
  }

  override fun closeSharePdfSheet() {
    session.update { it.copy(activeSharedPdfSheet = null, activePdf = null) }
  }

  override fun dismissPdfExportMessage() {
    session.update { it.copy(pdfExportMessage = null) }
  }

  private fun openPdfSheet(pdf: GeneratedPdf, sheet: SharedPdfSheetState) {
    session.update { it.copy(activePdf = pdf, activeSharedPdfSheet = sheet) }
  }

  private fun savePdf(pdf: GeneratedPdf) {
    pdfExportClient.save(pdf.fileName, pdf.bytes)
    session.update { it.copy(pdfExportMessage = "Saved ${pdf.fileName} (${pdf.summaryLabel})") }
  }
}
