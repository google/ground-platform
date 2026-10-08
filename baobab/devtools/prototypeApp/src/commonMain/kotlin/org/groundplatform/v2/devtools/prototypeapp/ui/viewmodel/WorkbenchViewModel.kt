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
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.DEFAULT_PROTOTYPE_XFORMS_XML
import org.groundplatform.v2.devtools.prototypeapp.XFormsParseCache
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.deriveEntitySyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SampleDataRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GeneratePrototypeRandomSitesUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WorkbenchEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WorkbenchUiState

/**
 * User intents for the prototype workbench chrome (device preview bezel, XForms `<h:html>` editor &
 * example survey switcher, and debug / simulation tools). Implemented by [WorkbenchViewModel].
 */
interface WorkbenchActions {
  /** Selects the simulated device preview form factor (`Mobile` vs `Tablet`). */
  fun selectDeviceFormFactor(formFactor: DeviceFormFactor)

  /** Toggles between `Mobile` and `Tablet` device preview form factors. */
  fun toggleDeviceFormFactor()

  /** Rotates the simulated device between `Portrait` and `Landscape` orientation (`90°` swap). */
  fun rotateDevice()

  /** Alias for [rotateDevice]: toggles between `Portrait` and `Landscape` orientation. */
  fun toggleDeviceOrientation()

  /** Explicitly sets the simulated device orientation (`Portrait` or `Landscape`). */
  fun selectDeviceOrientation(orientation: DeviceOrientation)

  /**
   * Updates the workbench's custom XForms XML, parses it into a [FormDef], and notifies
   * `DataCollectionViewModel` via [WorkbenchEvent.CustomFormDefChanged].
   */
  fun updateCustomXFormsXml(xml: String)

  /**
   * Selects a swappable example survey & form in the workbench, switching to its survey and
   * optionally launching its form immediately.
   */
  fun selectWorkbenchExampleForm(example: WorkbenchExampleForm, launchImmediately: Boolean = false)

  /** Restores the default sample XForms XML definition (`DEFAULT_PROTOTYPE_XFORMS_XML`). */
  fun resetDefaultXFormsXml()

  /** Randomly generates and appends [count] polygon map features across the active survey. */
  fun addRandomSites(count: Int = 5_000)

  /** Updates the [SyncStatus] of entity [entityId] and synchronizes its submissions if `SYNCED`. */
  fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus)

  /** Cycles the [SyncStatus] of entity [entityId] (`Uploading` -> `Synced` -> `Failed`). */
  fun cycleEntitySyncStatus(entityId: String)

  /** Updates the [SyncStatus] of submission [submissionId] and recomputes its entity's status. */
  fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus)

  /** Cycles the [SyncStatus] of submission [submissionId] (`Uploading` -> `Synced` -> `Failed`). */
  fun cycleSubmissionSyncStatus(submissionId: String)

  /** Toggles the downloaded indicator of [surveyId] in the UX workbench simulator. */
  fun toggleSurveyDownloaded(surveyId: String)

  /** Updates the title and description of the currently active survey. */
  fun updateActiveSurveyDetails(title: String, description: String)

  /** Resets the local data store to sample data and returns the prototype flow to Sign In. */
  fun resetPrototypeFlow()
}

/**
 * ViewModel for the prototype-only workbench chrome (`PrototypeXFormsWorkbenchPanel`,
 * `PrototypeDebugTools`, and the device bezel in `PrototypeApp`):
 * - Simulated device form factor (`Mobile` vs `Tablet`) and orientation (`Portrait` vs
 *   `Landscape`).
 * - Live XForms `<h:html>` editor, parse validation, and swappable example survey presets.
 * - Prototype simulation helpers (adding 5,000 random polygon features, cycling entity/submission
 *   sync status, toggling survey downloaded state, and resetting the prototype flow via
 *   [SampleDataRepository]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModel(
  private val surveyRepository: SurveyRepository,
  private val sampleDataRepository: SampleDataRepository,
  private val generateRandomSitesUseCase: GeneratePrototypeRandomSitesUseCase,
  private val scope: CoroutineScope,
) : WorkbenchActions {
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val surveyConfigs: Map<String, SurveyConfig> = emptyMap(),
    val surveyStats: Map<String, SurveyStats> = emptyMap(),
  )

  private data class Session(
    val deviceFormFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
    val deviceOrientation: DeviceOrientation = DeviceFormFactor.MOBILE.defaultOrientation,
    val customXFormsXml: String = DEFAULT_PROTOTYPE_XFORMS_XML,
    val selectedWorkbenchExampleForm: WorkbenchExampleForm? = WorkbenchExampleForm.ALL_FIELD_TYPES,
    val xformsXmlError: String? = null,
    val customFormDef: FormDef? = XFormsParseCache.formDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
    val dataResetCount: Int = 0,
  )

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        surveyRepository.observeSurveyConfigs(),
        surveyRepository.observeSurveyStats(),
      ) { surveys, (activeId, content), configs, stats ->
        Data(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          surveyConfigs = configs,
          surveyStats = stats,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

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
      .stateIn(scope, SharingStarted.Eagerly, buildUiState(Data(), Session()))

  private fun buildUiState(data: Data, session: Session): WorkbenchUiState =
    WorkbenchUiState(
      deviceFormFactor = session.deviceFormFactor,
      deviceOrientation = session.deviceOrientation,
      customXFormsXml = session.customXFormsXml,
      selectedWorkbenchExampleForm = session.selectedWorkbenchExampleForm,
      xformsXmlError = session.xformsXmlError,
      customFormDef = session.customFormDef,
      dataResetCount = session.dataResetCount,
      activeSurveyId = data.activeSurveyId,
      surveys = data.surveys,
      surveyConfigs = data.surveyConfigs,
      surveyStats = data.surveyStats,
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
   * form XML into the workbench editor and notifies `DataCollectionViewModel`.
   */
  fun onSurveyActivated(surveyId: String) {
    val xml = data.value.surveyConfigs[surveyId]?.primaryFormXml ?: return
    val example = WorkbenchExampleForm.entries.firstOrNull { it.xformsXml == xml }
    val formDef = XFormsParseCache.formDef(xml)
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

  override fun updateCustomXFormsXml(xml: String) {
    val matchedExample =
      WorkbenchExampleForm.entries.firstOrNull { it.xformsXml.trim() == xml.trim() }
        ?: if (xml.trim() == DEFAULT_PROTOTYPE_XFORMS_XML.trim()) {
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
    if (ui.activeSurveyId != "survey-kenya-coffee") {
      emit(WorkbenchEvent.DefaultSurveyRestored("survey-kenya-coffee"))
    }
    updateCustomXFormsXml(DEFAULT_PROTOTYPE_XFORMS_XML)
    session.update { it.copy(selectedWorkbenchExampleForm = WorkbenchExampleForm.ALL_FIELD_TYPES) }
  }

  override fun addRandomSites(count: Int) {
    if (count <= 0) return
    scope.launch {
      val result = generateRandomSitesUseCase(count) ?: return@launch
      emit(
        WorkbenchEvent.RandomSitesAdded(
          count = result.addedCount,
          totalCount = result.totalEntityCount,
          noticeMessage = result.noticeMessage,
        )
      )
    }
  }

  override fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus) {
    val entities = ui.entities
    val target = entities.firstOrNull { it.id == entityId } ?: return
    val updated = entities.map { item ->
      if (item.id == entityId) {
        val updatedSubmissions =
          if (newStatus == SyncStatus.SYNCED) {
            item.submissions.map { sub -> sub.copy(syncStatus = SyncStatus.SYNCED) }
          } else {
            item.submissions
          }
        item.copy(submissions = updatedSubmissions, syncStatus = newStatus)
      } else {
        item
      }
    }
    scope.launch { surveyRepository.setEntities(updated) }
    emit(WorkbenchEvent.Notice("${target.label}: Sync status set to ${newStatus.label}"))
  }

  override fun cycleEntitySyncStatus(entityId: String) {
    val entity = ui.entities.firstOrNull { it.id == entityId } ?: return
    updateEntitySyncStatus(entityId, entity.syncStatus.next())
  }

  override fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus) {
    val current = ui
    var updatedSubTitle: String? = null
    val updatedEntities =
      current.entities.map { item ->
        val hasTarget = item.submissions.any { it.id == submissionId }
        if (hasTarget) {
          val updatedSubmissions =
            item.submissions.map { sub ->
              if (sub.id == submissionId) {
                updatedSubTitle = sub.formTitle
                sub.copy(syncStatus = newStatus)
              } else {
                sub
              }
            }
          item.copy(
            submissions = updatedSubmissions,
            syncStatus =
              deriveEntitySyncStatus(
                updatedSubmissions,
                fallback =
                  if (newStatus == SyncStatus.SYNCED) SyncStatus.SYNCED else item.syncStatus,
              ),
          )
        } else {
          item
        }
      }
    scope.launch { surveyRepository.setEntities(updatedEntities) }
    if (current.standaloneSubmissions.any { it.id == submissionId }) {
      val updatedStandalone =
        current.standaloneSubmissions.map { sub ->
          if (sub.id == submissionId) {
            updatedSubTitle = sub.formTitle
            sub.copy(syncStatus = newStatus)
          } else {
            sub
          }
        }
      scope.launch { surveyRepository.setStandaloneSubmissions(updatedStandalone) }
    }
    updatedSubTitle?.let { title ->
      emit(WorkbenchEvent.Notice("$title: Sync status set to ${newStatus.label}"))
    }
  }

  override fun cycleSubmissionSyncStatus(submissionId: String) {
    val submission = ui.allSubmissions.firstOrNull { it.id == submissionId } ?: return
    updateSubmissionSyncStatus(submissionId, submission.syncStatus.next())
  }

  override fun toggleSurveyDownloaded(surveyId: String) {
    val surveys = ui.surveys
    var noticeMessage: String? = null
    val updated = surveys.map { item ->
      if (item.id == surveyId) {
        val nextState = !item.isDownloaded
        noticeMessage =
          if (nextState) {
            "Downloaded \"${item.title}\" (${item.offlineSizeLabel}) for offline use."
          } else {
            "Removed offline copy of \"${item.title}\"."
          }
        item.copy(isDownloaded = nextState)
      } else {
        item
      }
    }
    scope.launch { surveyRepository.setSurveys(updated) }
    noticeMessage?.let { emit(WorkbenchEvent.Notice(it)) }
  }

  override fun updateActiveSurveyDetails(title: String, description: String) {
    val activeId = ui.activeSurveyId
    val updated =
      ui.surveys.map { item ->
        if (item.id == activeId) {
          item.copy(
            title = title.ifBlank { item.title },
            description = description.ifBlank { item.description },
          )
        } else {
          item
        }
      }
    scope.launch { surveyRepository.setSurveys(updated) }
  }

  override fun resetPrototypeFlow() {
    emit(WorkbenchEvent.PrototypeReset)
    scope.launch { sampleDataRepository.resetToSampleData() }
    session.update { it.copy(dataResetCount = it.dataResetCount + 1) }
    resetDefaultXFormsXml()
  }
}
