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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.app.client.pdf.GeneratedPdf
import org.openforis.collectone.v2.app.client.pdf.PdfExportClient
import org.openforis.collectone.v2.app.client.pdf.PdfExportResult
import org.openforis.collectone.v2.app.client.pdf.PlatformPdfExportClient
import org.openforis.collectone.v2.app.client.pdf.RecordPdfReports
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.deviceFormEnvironment
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.relatedEntityForPropertyValue
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.LocationRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.CompleteFormSubmissionUseCase
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.saveInto
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.map.LatLng as MapLatLng
import org.openforis.collectone.v2.map.LngLatBounds

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
 * [LaunchFormUseCase] and complete through [CompleteFormSubmissionUseCase]. PDF exports are
 * delegated to [DataCollectionPdfExports], which generates the reports (asynchronously, in the
 * language selected when they're asked for), delivers them through [PdfExportClient] (UI
 * infrastructure a ViewModel may call directly; see the client's documentation), and records each
 * as a receipt through [RecordImpactEventUseCase]. The screen state is a pure function of the
 * store's data, the session state, and the PDF export state ([buildDataCollectionUiState]).
 *
 * All actions are expected on the main thread ([dispatcher] is immediate by default), so a session
 * update is visible in [uiState] before the action returns; only submissions and PDF exports finish
 * later, in [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataCollectionViewModel(
  surveyRepository: SurveyRepository,
  settingsRepository: SettingsRepository,
  authRepository: AuthRepository,
  private val locationRepository: LocationRepository,
  private val completeFormSubmission: CompleteFormSubmissionUseCase,
  private val launchForm: LaunchFormUseCase,
  private val pdfExportClient: PdfExportClient = PlatformPdfExportClient(),
  private val clock: Clock = Clock.System,
  private val connectivityRepository: ConnectivityRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** Records receipt activity (PDF reports generated and shared); `null` records nothing. */
  recordImpactEvent: RecordImpactEventUseCase? = null,
  private val organizationRepository: OrganizationRepository? = null,
  /** Keeps the selected map feature and submission across Android process death. */
  private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(viewModelScopeOn(dispatcher)), DataCollectionActions {
  private val data: StateFlow<DataCollectionData> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        settingsRepository.observeUserSettings(),
        authRepository.observeSession(),
        organizationRepository?.observeOrganizations() ?: flowOf(emptyList()),
      ) { surveys, (activeId, content), settings, auth, orgs ->
        DataCollectionData(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          unitSystem = settings.measurementUnits,
          profile = auth.profile,
          organizations = orgs,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, DataCollectionData())

  private val session =
    MutableStateFlow(
      DataCollectionSession(
        selectedEntityId = savedState[SELECTED_ENTITY_KEY],
        selectedSubmissionId = savedState[SELECTED_SUBMISSION_KEY],
      )
    )

  init {
    saveInto(savedState, SELECTED_ENTITY_KEY, session) { it.selectedEntityId }
    saveInto(savedState, SELECTED_SUBMISSION_KEY, session) { it.selectedSubmissionId }
  }

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

  private val pdfReportPort =
    object : PdfReportPort {
      override suspend fun generateEntityReport(
        entityId: String,
        ignoreOffline: Boolean,
      ): ExportedPdf? = generateEntityPdf(entityId, ignoreOffline)?.toExportedPdf()

      override suspend fun generateSubmissionReport(submissionId: String): ExportedPdf? =
        generateSubmissionPdf(submissionId)?.toExportedPdf()

      override fun save(fileName: String, bytes: ByteArray) = pdfExportClient.save(fileName, bytes)

      override fun preview(fileName: String, bytes: ByteArray) =
        pdfExportClient.preview(fileName, bytes)

      override fun share(
        fileName: String,
        title: String,
        bytes: ByteArray,
        onOutcome: (PdfShareOutcome) -> Unit,
      ) = pdfExportClient.share(fileName, title, bytes) { onOutcome(it.toShareOutcome()) }
    }

  private val pdfExports =
    DataCollectionPdfExports(
      scope = viewModelScope,
      port = pdfReportPort,
      recordImpactEvent = recordImpactEvent,
      data = { data.value },
      ui = { uiState.value },
    )

  val uiState: StateFlow<DataCollectionUiState> =
    combine(data, session, pdfExports.state, ::buildUiState)
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        buildUiState(DataCollectionData(), DataCollectionSession(), PdfExportState()),
      )

  /** Increasing tokens that make repeated framing / focus requests for the same target distinct. */
  private var framingToken = 0L
  private var focusToken = 0L

  private fun buildUiState(
    data: DataCollectionData,
    session: DataCollectionSession,
    pdf: PdfExportState,
  ): DataCollectionUiState =
    buildDataCollectionUiState(
      data = data,
      session = session,
      pdf = pdf,
      webMapDrawing = webMapDrawing,
      canSharePdfFiles = pdfExportClient.canShareFiles,
    )

  private val ui: DataCollectionUiState
    get() = uiState.value

  private fun emit(event: DataCollectionEvent) {
    _events.tryEmit(event)
  }

  /** Returns session state to its defaults (used by the workbench's Reset). */
  fun reset() {
    setController(null)
    session.value = DataCollectionSession(customFormDef = session.value.customFormDef)
    pdfExports.reset()
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
        pendingConsentLaunch = null,
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
        tableDatasetId = if (framesOtherEntity) parentEntity.datasetId else null
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
    if (!ui.canCollectData) return
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

  private fun requiresConsentBeforeLaunch(): Boolean {
    val survey = data.value.activeSurvey ?: return false
    return survey.requiresCollectorConsent && survey.id !in session.value.consentedSurveyIds
  }

  override fun acceptCollectorConsent() {
    val pending = session.value.pendingConsentLaunch ?: return
    val surveyId = data.value.activeSurvey?.id ?: data.value.activeSurveyId
    session.update {
      it.copy(
        consentedSurveyIds =
          if (surveyId.isNotBlank()) it.consentedSurveyIds + surveyId else it.consentedSurveyIds,
        pendingConsentLaunch = null,
      )
    }
    when (pending) {
      is PendingFormLaunch.FromFab -> launchFormFromFab(pending.formId, pending.pickerMode)
      is PendingFormLaunch.ForEntity -> launchFormForEntity(pending.entityId, pending.formId)
    }
  }

  override fun dismissCollectorConsent() {
    session.update { it.copy(pendingConsentLaunch = null) }
  }

  override fun launchFormFromFab(formId: String, pickerMode: MainSurveyViewMode) {
    val current = ui
    if (!current.canCollectData) return
    val form = current.forms.firstOrNull { it.id == formId } ?: return
    if (requiresConsentBeforeLaunch()) {
      session.update {
        it.copy(
          isAvailableFormsSheetOpen = false,
          pendingConsentLaunch = PendingFormLaunch.FromFab(formId, pickerMode),
        )
      }
      return
    }
    val controller =
      formWizardFor(
        launchForm(
          customFormDef = current.customFormDef,
          form = form,
          entity = null,
          candidateEntities = current.eligibleEntitiesForForm(form),
          includeEntityRefStep = form.requiresEntity,
          surveyConfig = data.value.content.config,
        ),
        deviceFormEnvironment(clock),
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
    if (!current.canCollectData) return
    val entity = current.entities.firstOrNull { it.id == entityId } ?: return
    val form = current.forms.firstOrNull { it.id == formId } ?: return
    if (!current.isFormButtonEnabled(entity, form)) return
    if (requiresConsentBeforeLaunch()) {
      session.update {
        it.copy(
          isAvailableFormsSheetOpen = false,
          pendingConsentLaunch = PendingFormLaunch.ForEntity(entityId, formId),
        )
      }
      return
    }
    val controller =
      formWizardFor(
        launchForm(
          customFormDef = current.customFormDef,
          form = form,
          entity = entity,
          surveyConfig = data.value.content.config,
        ),
        deviceFormEnvironment(clock),
      )
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
    when (val launch = chooseTestingFormLaunch(ui)) {
      is TestingFormLaunch.ForEntity -> launchFormForEntity(launch.entityId, launch.formId)
      is TestingFormLaunch.FromFab -> launchFormFromFab(launch.formId)
      null -> Unit
    }
  }

  override fun closeActiveFormRunner() {
    val wasOpen = ui.isDataCollectionFormOpen
    setController(null)
    session.update {
      it.copy(
        activeEntityId = null,
        activeFormId = null,
        wasFormLaunchedWithoutEntity = false,
        isSubmissionConfirmationShown = false,
        entityRefSearchQuery = "",
      )
    }
    if (wasOpen) emit(DataCollectionEvent.FormClosed)
  }

  override fun completeActiveFormSubmission(
    recordInstance: RecordInstance?,
    entityStates: List<EntityState>?,
    showConfirmation: Boolean,
  ) {
    val current = ui
    // The submission is recorded once: its confirmation is already showing.
    if (!current.canCollectData || current.isSubmissionConfirmationShown) return
    val controller = current.activeFormWizardController
    val location = locationRepository.getLocationSnapshot()
    val profile = data.value.profile
    val unitSystem = data.value.unitSystem
    val check =
      checkSubmissionQuota(
        data = data.value,
        activeEntityId = current.activeDataCollectionEntityId,
        wasFormLaunchedWithoutEntity = current.wasFormLaunchedWithoutEntity,
      )
    if (check is QuotaCheckResult.Exceeded) {
      viewModelScope.launch { emit(DataCollectionEvent.Notice(check.message.toUiText())) }
      return
    }

    viewModelScope.launch {
      val result =
        completeFormSubmission(
          recordInstance =
            recordInstance ?: controller?.formState?.recordInstance ?: RecordInstance(),
          entityStates = entityStates ?: controller?.formState?.entityStates ?: emptyList(),
          formRunner = controller?.let(::WizardFormRunner),
          activeDataCollectionFormId = current.activeDataCollectionFormId,
          activeDataCollectionEntityId = current.activeDataCollectionEntityId,
          wasFormLaunchedWithoutEntity = current.wasFormLaunchedWithoutEntity,
          selectedEntityId = current.selectedEntityId,
          customFormDef = current.customFormDef,
          gnssAccuracy = unitSystem.gnssAccuracy(location.gnssAccuracyMeters),
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
            when {
              // The confirmation leads to the saved map feature, not to the submission's answers.
              showConfirmation && result.selectedEntityId != null -> null
              result.updateSelectedSubmissionId -> result.selectedSubmissionId
              else -> it.selectedSubmissionId
            },
          isSubmissionConfirmationShown = showConfirmation,
        )
      }
      emit(DataCollectionEvent.EntityHighlighted(result.selectedEntityId))
      if (showConfirmation) return@launch
      emit(DataCollectionEvent.Notice(result.notice.toUiText()))
      closeActiveFormRunner()
    }
  }

  override fun dismissSubmissionConfirmation() {
    val current = ui
    if (!current.isSubmissionConfirmationShown) return
    closeActiveFormRunner()
    val entityId = current.selectedEntityId ?: return
    emit(DataCollectionEvent.DetailsReturnedToProperties)
    emit(DataCollectionEvent.EntityHighlighted(entityId, frameAgain = true))
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
      formWizardFor(
        launchForm(
          customFormDef = formDef,
          form = form,
          entity = null,
          candidateEntities = current.eligibleEntitiesForForm(form),
          includeEntityRefStep = includeEntityRefStep,
          // The refreshed form keeps pointing at the feature already picked, if any.
          defaultSelectedEntityId = current.activeDataCollectionEntityId.orEmpty(),
          surveyConfig = data.value.content.config,
        ),
        deviceFormEnvironment(clock),
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
      prepopulateEntityReference(ctrl, entity.id)
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
   * Generates a PDF report for the map feature [entityId] in the language selected when called, or
   * returns `null` for unknown IDs. Offline, unless [ignoreOffline], only the signed-in collector's
   * own submissions are included; see [effectiveEntityForPdf].
   */
  internal suspend fun generateEntityPdf(
    entityId: String,
    ignoreOffline: Boolean = false,
  ): GeneratedPdf? {
    val current = data.value
    val entities = current.content.entities
    val effectiveEntity =
      effectiveEntityForPdf(
        entities = entities,
        entityId = entityId,
        collectorEmail = current.profile.email,
        includeOthersSubmissions = ignoreOffline || connectivityRepository.isOnline(),
      ) ?: return null
    return RecordPdfReports.entityReport(
      entity = effectiveEntity,
      surveyTitle = current.activeSurvey?.title.orEmpty(),
      geometry = effectiveEntity.geometry,
      unitSystem = current.unitSystem,
      generatedAt = clock.now(),
      relatedLabelFor = { value ->
        entities.relatedEntityForPropertyValue(effectiveEntity, value)?.label
      },
    )
  }

  /** Generates the submission [submissionId]'s PDF report, or `null` for unknown IDs. */
  internal suspend fun generateSubmissionPdf(submissionId: String): GeneratedPdf? {
    val current = data.value
    val submission = ui.allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    return RecordPdfReports.submissionReport(
      submission = submission,
      surveyTitle = current.activeSurvey?.title.orEmpty(),
      entity = current.content.entities.firstOrNull { it.id == submission.entityId },
      generatedAt = clock.now(),
    )
  }

  override fun shareEntityPdf(entityId: String) = pdfExports.shareEntityPdf(entityId)

  override fun shareSubmissionPdf(submissionId: String) =
    pdfExports.shareSubmissionPdf(submissionId)

  override fun downloadEntityPdf(entityId: String) =
    downloadEntityPdf(entityId, ignoreOffline = false)

  /**
   * Like [downloadEntityPdf], but with [ignoreOffline] (the web console) the report lists every
   * submission and the receipt is recorded as if online.
   */
  fun downloadEntityPdf(entityId: String, ignoreOffline: Boolean) =
    pdfExports.downloadEntityPdf(entityId, ignoreOffline)

  override fun downloadSubmissionPdf(submissionId: String) =
    downloadSubmissionPdf(submissionId, ignoreOffline = false)

  /** Like [downloadSubmissionPdf], recording the receipt as if online with [ignoreOffline]. */
  fun downloadSubmissionPdf(submissionId: String, ignoreOffline: Boolean) =
    pdfExports.downloadSubmissionPdf(submissionId, ignoreOffline)

  override fun shareActivePdf(title: String) = pdfExports.shareActivePdf(title)

  override fun saveActivePdf() = pdfExports.saveActivePdf()

  override fun previewActivePdf() = pdfExports.previewActivePdf()

  override fun closeSharePdfSheet() = pdfExports.closeSharePdfSheet()

  override fun dismissPdfExportMessage() = pdfExports.dismissPdfExportMessage()
}

private fun GeneratedPdf.toExportedPdf(): ExportedPdf =
  ExportedPdf(
    fileName = fileName,
    bytes = bytes,
    pageCount = pageCount,
    sizeLabel = sizeLabel,
    summaryText = summaryText,
  )

private fun PdfExportResult.toShareOutcome(): PdfShareOutcome =
  when (this) {
    PdfExportResult.SHARED -> PdfShareOutcome.SHARED
    PdfExportResult.SAVED -> PdfShareOutcome.SAVED
    PdfExportResult.CANCELLED -> PdfShareOutcome.CANCELLED
    PdfExportResult.FAILED -> PdfShareOutcome.FAILED
  }

/** [SavedStateHandle] key of the selected map feature. */
private const val SELECTED_ENTITY_KEY = "selectedEntityId"

/** [SavedStateHandle] key of the selected submission. */
private const val SELECTED_SUBMISSION_KEY = "selectedSubmissionId"
