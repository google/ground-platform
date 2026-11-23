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
package org.openforis.collectone.v2.workbench.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.workbench.ui.DEFAULT_WORKBENCH_FORM_DEF
import org.openforis.collectone.v2.workbench.ui.DEFAULT_WORKBENCH_XFORMS_XML
import org.openforis.collectone.v2.workbench.ui.state.WorkbenchEvent
import org.openforis.collectone.v2.workbench.ui.state.WorkbenchUiState

/**
 * User intents for the workbench chrome (device preview bezel, XForms `<h:html>` preset state, and
 * debug / simulation tools). Implemented by [WorkbenchViewModel].
 */
interface WorkbenchActions {
  /** Selects the simulated device preview form factor (`Mobile` vs `Tablet`). */
  fun selectDeviceFormFactor(formFactor: DeviceFormFactor)

  /** Switches between `Mobile` and `Tablet`, resetting the orientation to the new one's default. */
  fun toggleDeviceFormFactor()

  /** Rotates the simulated device between `Portrait` and `Landscape` orientation (`90°` swap). */
  fun rotateDevice()

  /** Alias for [rotateDevice]: toggles between `Portrait` and `Landscape` orientation. */
  fun toggleDeviceOrientation()

  /** Explicitly sets the simulated device orientation (`Portrait` or `Landscape`). */
  fun selectDeviceOrientation(orientation: DeviceOrientation)

  /**
   * Simulates the device going offline ([enabled]) or back online: sets the app's connectivity, so
   * sync and the list search follow it, and tells the shell ([WorkbenchEvent.ConnectivityChanged])
   * so the app shows its offline or back-online notice, as on a real device.
   */
  fun updateAirplaneMode(enabled: Boolean)

  /** [updateAirplaneMode] with the opposite of the current state. */
  fun toggleAirplaneMode()

  /**
   * Parses the editor's XForms [xml] and hands the result to data collection
   * ([WorkbenchEvent.CustomFormDefChanged]): XML equal to an example form (ignoring surrounding
   * whitespace) selects that example; blank XML clears the form; XML that doesn't parse keeps the
   * text, shows the parser's message, and leaves an open form as it is rather than relaunching it.
   */
  fun updateCustomXFormsXml(xml: String)

  /**
   * Opens the stored survey whose primary form is [example]'s (nothing happens when no survey uses
   * it) and, when [launchImmediately], starts that form. The editor's XML follows once the survey
   * is active (see `onSurveyActivated`).
   */
  fun selectWorkbenchExampleForm(example: WorkbenchExampleForm, launchImmediately: Boolean = false)

  /**
   * Restores the default example form ([DEFAULT_WORKBENCH_XFORMS_XML]), first switching back to its
   * survey when another survey is open.
   */
  fun resetDefaultXFormsXml()

  /**
   * Simulates entity [entityId]'s upload reaching [newStatus]; `SYNCED` marks its submissions
   * synced too. Writes to the local store, so every screen shows the new status.
   */
  fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus)

  /** Cycles the [SyncStatus] of entity [entityId] (`Uploading` -> `Synced` -> `Failed`). */
  fun cycleEntitySyncStatus(entityId: String)

  /**
   * Simulates submission [submissionId]'s upload reaching [newStatus]; its entity's status is
   * derived again from all its submissions.
   */
  fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus)

  /** Cycles the [SyncStatus] of submission [submissionId] (`Uploading` -> `Synced` -> `Failed`). */
  fun cycleSubmissionSyncStatus(submissionId: String)

  /** Simulates downloading survey [surveyId] for offline use, or removing its offline copy. */
  fun toggleSurveyDownloaded(surveyId: String)

  /** Renames the active survey; a blank [title] or [description] keeps the current one. */
  fun updateActiveSurveyDetails(title: String, description: String)

  /**
   * Starts over: back online, the local store reset to the sample data, the mobile app back at Sign
   * in with its ViewModels at their defaults ([WorkbenchEvent.SampleDataReset]), and the default
   * example form. Counts the reset in [WorkbenchUiState.dataResetCount].
   */
  fun resetSampleData()
}

/**
 * ViewModel for the workbench chrome (the device bezel in `MobileWorkbenchPage`):
 * - Simulated device form factor (`Mobile` vs `Tablet`) and orientation (`Portrait` vs
 *   `Landscape`).
 * - XForms `<h:html>` parse validation and swappable example survey presets.
 * - Simulation helpers (cycling entity/submission sync status, toggling survey downloaded state,
 *   and resetting to the sample data via [SampleDataRepository]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModel(
  private val surveyRepository: SurveyRepository,
  private val sampleDataRepository: SampleDataRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val connectivityRepository: ConnectivityRepository = ConnectivityRepository(),
) : ViewModel(viewModelScopeOn(dispatcher)), WorkbenchActions {
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val surveyConfigs: Map<String, SurveyConfig> = emptyMap(),
    val isOnline: Boolean = true,
  )

  private data class Session(
    val deviceFormFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
    val deviceOrientation: DeviceOrientation = DeviceFormFactor.MOBILE.defaultOrientation,
    val customXFormsXml: String = DEFAULT_WORKBENCH_XFORMS_XML,
    val selectedWorkbenchExampleForm: WorkbenchExampleForm? = WorkbenchExampleForm.ALL_FIELD_TYPES,
    val xformsXmlError: String? = null,
    val customFormDef: FormDef? = DEFAULT_WORKBENCH_FORM_DEF,
    val dataResetCount: Int = 0,
  )

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        surveyRepository.observeSurveyConfigs(),
        connectivityRepository.observeIsOnline(),
      ) { surveys, (activeId, content), configs, isOnline ->
        Data(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          surveyConfigs = configs,
          isOnline = isOnline,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<WorkbenchEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of workbench actions that the app shell applies to other ViewModels or navigation. */
  val events: Flow<WorkbenchEvent> = _events.asSharedFlow()

  val uiState: StateFlow<WorkbenchUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, buildUiState(Data(), Session()))

  private fun buildUiState(data: Data, session: Session): WorkbenchUiState =
    WorkbenchUiState(
      deviceFormFactor = session.deviceFormFactor,
      deviceOrientation = session.deviceOrientation,
      isAirplaneMode = !data.isOnline,
      customXFormsXml = session.customXFormsXml,
      selectedWorkbenchExampleForm = session.selectedWorkbenchExampleForm,
      xformsXmlError = session.xformsXmlError,
      customFormDef = session.customFormDef,
      dataResetCount = session.dataResetCount,
      activeSurveyId = data.activeSurveyId,
      surveys = data.surveys,
      surveyConfigs = data.surveyConfigs,
      entities = data.content.entities,
      standaloneSubmissions = data.content.standaloneSubmissions,
    )

  private val ui: WorkbenchUiState
    get() = uiState.value

  private fun emit(event: WorkbenchEvent) {
    _events.tryEmit(event)
  }

  /**
   * Called by the app shell when [surveyId] becomes the active survey: loads the survey's primary
   * form into the workbench editor and notifies `DataCollectionViewModel`.
   */
  fun onSurveyActivated(surveyId: String) {
    val primaryForm = data.value.surveyConfigs[surveyId]?.primaryForm ?: return
    val example = WorkbenchExampleForm.entries.firstOrNull { it.formDefinition == primaryForm }
    val formDef = primaryForm.proto
    val xml = example?.xformsXml ?: XFormsXmlSerializer.serialize(formDef, prettyPrint = true)
    session.update {
      it.copy(
        selectedWorkbenchExampleForm = example,
        customXFormsXml = xml,
        xformsXmlError = null,
        customFormDef = formDef,
      )
    }
    emit(WorkbenchEvent.CustomFormDefChanged(formDef = formDef, isPreset = example != null))
  }

  override fun selectDeviceFormFactor(formFactor: DeviceFormFactor) {
    session.update {
      it.copy(deviceFormFactor = formFactor, deviceOrientation = formFactor.defaultOrientation)
    }
  }

  override fun toggleDeviceFormFactor() {
    val next =
      if (ui.deviceFormFactor == DeviceFormFactor.MOBILE) {
        DeviceFormFactor.TABLET
      } else {
        DeviceFormFactor.MOBILE
      }
    selectDeviceFormFactor(next)
  }

  override fun rotateDevice() {
    session.update {
      val next =
        if (it.deviceOrientation == DeviceOrientation.PORTRAIT) {
          DeviceOrientation.LANDSCAPE
        } else {
          DeviceOrientation.PORTRAIT
        }
      it.copy(deviceOrientation = next)
    }
  }

  override fun toggleDeviceOrientation() = rotateDevice()

  override fun selectDeviceOrientation(orientation: DeviceOrientation) {
    session.update { it.copy(deviceOrientation = orientation) }
  }

  override fun updateAirplaneMode(enabled: Boolean) {
    val isOnline = !enabled
    connectivityRepository.setOnline(isOnline)
    emit(WorkbenchEvent.ConnectivityChanged(isOnline = isOnline))
  }

  override fun toggleAirplaneMode() {
    updateAirplaneMode(!ui.isAirplaneMode)
  }

  override fun updateCustomXFormsXml(xml: String) {
    val matchedExample =
      WorkbenchExampleForm.entries.firstOrNull { it.xformsXml.trim() == xml.trim() }
        ?: if (xml.trim() == DEFAULT_WORKBENCH_XFORMS_XML.trim()) {
          WorkbenchExampleForm.ALL_FIELD_TYPES
        } else {
          null
        }
    if (xml.isBlank()) {
      session.update {
        it.copy(
          customXFormsXml = xml,
          selectedWorkbenchExampleForm = matchedExample,
          xformsXmlError = null,
          customFormDef = null,
        )
      }
      emit(WorkbenchEvent.CustomFormDefChanged(formDef = null, isPreset = false))
      return
    }
    try {
      val parsed = XFormsXmlSerializer.deserializeFormDef(xml)
      session.update {
        it.copy(
          customXFormsXml = xml,
          selectedWorkbenchExampleForm = matchedExample,
          xformsXmlError = null,
          customFormDef = parsed,
        )
      }
      emit(WorkbenchEvent.CustomFormDefChanged(formDef = parsed, isPreset = matchedExample != null))
    } catch (e: Exception) {
      val errorMsg = e.message ?: "Invalid XForms XML"
      session.update {
        it.copy(
          customXFormsXml = xml,
          selectedWorkbenchExampleForm = matchedExample,
          xformsXmlError = errorMsg,
          customFormDef = null,
        )
      }
      emit(
        WorkbenchEvent.CustomFormDefChanged(
          formDef = null,
          isPreset = false,
          relaunchOpenForm = false,
        )
      )
    }
  }

  override fun selectWorkbenchExampleForm(
    example: WorkbenchExampleForm,
    launchImmediately: Boolean,
  ) {
    val targetSurveyId = ui.surveyIdForExampleForm(example) ?: return
    emit(WorkbenchEvent.ExampleSurveySelected(targetSurveyId, launchImmediately))
    session.update { it.copy(selectedWorkbenchExampleForm = example) }
    val current = ui
    val activeSurvey =
      current.surveys.firstOrNull { it.id == current.activeSurveyId }
        ?: current.surveys.firstOrNull()
    if (activeSurvey != null) {
      emit(
        WorkbenchEvent.Notice(
          "Switched to survey \"${activeSurvey.title}\" (${current.entities.size} entities, ${current.allSubmissions.size} preloaded submissions)."
        )
      )
    }
  }

  override fun resetDefaultXFormsXml() {
    if (ui.activeSurveyId != DEFAULT_EXAMPLE_SURVEY_ID) {
      emit(WorkbenchEvent.DefaultSurveyRestored(DEFAULT_EXAMPLE_SURVEY_ID))
    }
    updateCustomXFormsXml(DEFAULT_WORKBENCH_XFORMS_XML)
    session.update { it.copy(selectedWorkbenchExampleForm = WorkbenchExampleForm.ALL_FIELD_TYPES) }
  }

  override fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus) {
    val entities = ui.entities
    val target = entities.firstOrNull { it.id == entityId } ?: return
    val updated = entities.withEntitySyncStatus(entityId, newStatus) ?: return
    viewModelScope.launch { surveyRepository.setEntities(updated) }
    emit(WorkbenchEvent.Notice("${target.label}: Sync status set to ${newStatus.devName}"))
  }

  override fun cycleEntitySyncStatus(entityId: String) {
    val entity = ui.entities.firstOrNull { it.id == entityId } ?: return
    updateEntitySyncStatus(entityId, entity.syncStatus.next())
  }

  override fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus) {
    val current = ui
    val change =
      withSubmissionSyncStatus(
        current.entities,
        current.standaloneSubmissions,
        submissionId,
        newStatus,
      )
    viewModelScope.launch { surveyRepository.setEntities(change.entities) }
    change.standaloneSubmissions?.let { standalone ->
      viewModelScope.launch { surveyRepository.setStandaloneSubmissions(standalone) }
    }
    change.formTitle?.let { title ->
      emit(WorkbenchEvent.Notice("$title: Sync status set to ${newStatus.devName}"))
    }
  }

  override fun cycleSubmissionSyncStatus(submissionId: String) {
    val submission = ui.allSubmissions.firstOrNull { it.id == submissionId } ?: return
    updateSubmissionSyncStatus(submissionId, submission.syncStatus.next())
  }

  override fun toggleSurveyDownloaded(surveyId: String) {
    val (updated, toggled) = ui.surveys.withDownloadToggled(surveyId)
    viewModelScope.launch { surveyRepository.setSurveys(updated) }
    toggled?.let {
      val notice =
        if (it.isDownloaded) {
          "Downloaded \"${it.title}\" for offline use."
        } else {
          "Removed offline copy of \"${it.title}\"."
        }
      emit(WorkbenchEvent.Notice(notice))
    }
  }

  override fun updateActiveSurveyDetails(title: String, description: String) {
    val updated = ui.surveys.withDetails(ui.activeSurveyId, title, description)
    viewModelScope.launch { surveyRepository.setSurveys(updated) }
  }

  override fun resetSampleData() {
    connectivityRepository.reset()
    emit(WorkbenchEvent.SampleDataReset)
    viewModelScope.launch { sampleDataRepository.resetToSampleData() }
    session.update { it.copy(dataResetCount = it.dataResetCount + 1) }
    resetDefaultXFormsXml()
  }
}

/** ID of the sample survey whose primary form is the default example form (`All field types`). */
private const val DEFAULT_EXAMPLE_SURVEY_ID = "00000033-6b50-51cd-ac94-baea2ea06d65"

/**
 * English name of this sync status for the workbench's notices. The workbench is developer tooling
 * that isn't shipped, so its notices stay English (like `DeviceOrientation` and
 * `DeviceFormFactor`).
 */
private val SyncStatus.devName: String
  get() =
    when (this) {
      SyncStatus.UPLOADING -> "Uploading"
      SyncStatus.SYNCED -> "Synced"
      SyncStatus.FAILED -> "Failed"
    }
