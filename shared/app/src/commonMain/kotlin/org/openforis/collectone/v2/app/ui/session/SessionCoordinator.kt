/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_centered_on_place
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.dashboard.DashboardEvent
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionEvent
import org.openforis.collectone.v2.app.ui.map.SurveyMapEvent
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingEvent
import org.openforis.collectone.v2.app.ui.settings.SettingsEvent
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.nameText
import org.openforis.collectone.v2.app.ui.text.toUiText

/**
 * Wires one client session's feature ViewModels ([viewModels]) together: applies each ViewModel's
 * events to the other ViewModels they concern, shows their notices through [notices], and tells the
 * client's [shell] what to show. The ViewModels own their state, and the screens read it from them
 * directly; this class holds no UI state of its own.
 *
 * Several sessions may share one [AppDataHolder], in which case data changes made in one are live
 * in the other, while UI state such as selection and navigation stays per session.
 */
class SessionCoordinator(
  private val dataHolder: AppDataHolder,
  private val viewModels: AppViewModels,
  private val notices: NoticeController,
  private val shell: SessionShell,
) : AutoCloseable {
  /**
   * Scope of the routing. [close] cancels it, so a session rebuilt around the same ViewModels (for
   * example after an Android configuration change) doesn't route their events twice. Unconfined, so
   * an event is applied to the other ViewModels inside the intent that emitted it, and their states
   * agree by the time that intent returns.
   */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  private val onboarding = viewModels.onboarding
  private val surveyMap = viewModels.surveyMap
  private val dashboard = viewModels.dashboard
  private val dataCollection = viewModels.dataCollection

  init {
    // A sign-out from another session sharing the store emits no event here, only a state change:
    // watch for the signed-in to signed-out transition. This session's own sign-out also gets its
    // OnboardingEvent.SignedOut, which onOnboardingEvent handles.
    var wasSignedIn = onboarding.uiState.value.isSignedIn
    scope.launch {
      onboarding.uiState.collect {
        if (wasSignedIn && !it.isSignedIn) shell.onSignedOutElsewhere()
        wasSignedIn = it.isSignedIn
      }
    }
    scope.launch { onboarding.events.collect(::onOnboardingEvent) }
    scope.launch {
      viewModels.settings.events.collect { event ->
        when (event) {
          is SettingsEvent.Notice -> notices.show(event.message)
        }
      }
    }
    scope.launch { surveyMap.events.collect(::onSurveyMapEvent) }
    scope.launch { dashboard.events.collect(::onDashboardEvent) }
    scope.launch { dataCollection.events.collect(::onDataCollectionEvent) }
    // A selection data collection restored after Android process death is highlighted on the map.
    val restoredEntityId = dataCollection.uiState.value.selectedEntityId
    if (restoredEntityId != null && restoredEntityId != surveyMap.uiState.value.selectedEntityId) {
      surveyMap.setSelectedEntity(restoredEntityId)
    }
  }

  /** Stops routing the ViewModels' events. Hosts call it when the session leaves composition. */
  override fun close() {
    scope.cancel()
  }

  /**
   * Resets every ViewModel of the session and its notice, as when the workbench resets the store to
   * its sample data.
   */
  fun reset() {
    onboarding.reset()
    viewModels.settings.reset()
    surveyMap.reset()
    dashboard.reset()
    dataCollection.reset()
    viewModels.listSearch.reset()
    notices.clear()
  }

  /**
   * Applies a data collection outcome to the rest of the session (map highlight and framing, bottom
   * sheet, dashboard panes, notices).
   */
  private fun onDataCollectionEvent(event: DataCollectionEvent) {
    when (event) {
      is DataCollectionEvent.EntityHighlighted ->
        surveyMap.setSelectedEntity(event.entityId, bumpSelectionEpoch = event.frameAgain)
      is DataCollectionEvent.MapRecenteredOnEntity -> surveyMap.recenterMapOnEntity(event.entity)
      is DataCollectionEvent.EntityBottomSheetExpanded ->
        surveyMap.updateEntityBottomSheetExpanded(event.expanded)
      DataCollectionEvent.LayersSheetClosed -> surveyMap.updateLayersSheetOpen(false)
      DataCollectionEvent.PlaceSelectionCleared -> surveyMap.clearSelectedPlace()
      DataCollectionEvent.FormsSheetOpened -> shell.onFormsSheetOpened()
      is DataCollectionEvent.SubmissionOpened -> dashboard.onSubmissionOpened(event.tableDatasetId)
      DataCollectionEvent.DetailsReturnedToProperties -> dashboard.showEntityProperties()
      DataCollectionEvent.ListShown -> shell.onListShown()
      DataCollectionEvent.FormOpened -> {
        dashboard.onFormOpened()
        shell.onFormOpened()
      }
      DataCollectionEvent.FormClosed -> dashboard.onFormClosed()
      is DataCollectionEvent.Notice -> notices.show(event.message)
    }
  }

  /** Applies a dashboard outcome to the rest of the session (survey switch, Uploads, notices). */
  private fun onDashboardEvent(event: DashboardEvent) {
    when (event) {
      is DashboardEvent.SurveyActivated -> clearSelectionsForActivatedSurvey(event.surveyId)
      DashboardEvent.UploadsOpened -> shell.onUploadsOpened()
      is DashboardEvent.Notice -> notices.show(event.message)
    }
  }

  /** Applies a map outcome to the rest of the session (record selection, notices). */
  private fun onSurveyMapEvent(event: SurveyMapEvent) {
    when (event) {
      is SurveyMapEvent.EntitySelected -> {
        dataCollection.onEntitySelected(event.entityId)
        shell.onMapShown()
        dashboard.onEntitySelected(event.entityId)
      }
      is SurveyMapEvent.PlaceSelected -> {
        dataCollection.onEntitySelected(null)
        dashboard.selectLayer(null)
        dashboard.closeTimeSeries()
        shell.onMapShown()
        val place = event.place
        notices.show(
          Res.string.app_map_centered_on_place.toUiText(
            place.nameText(),
            coordinatesText(place.position),
          )
        )
      }
      is SurveyMapEvent.ClusterSelected -> {
        dataCollection.onEntitySelected(null)
        dashboard.closeTimeSeries()
        notices.show(surveyMap.uiState.value.clusterCountText(event.cluster.siteCount))
      }
      SurveyMapEvent.SelectionCleared -> {
        dataCollection.onEntitySelected(null)
        dashboard.closeTimeSeries()
      }
      is SurveyMapEvent.NavigationStarted -> {
        dataCollection.onNavigationStarted(
          entityId = surveyMap.uiState.value.selectedEntityId,
          submissionId = event.submissionId,
        )
        shell.onNavigationStarted()
        notices.show(event.notice)
      }
      SurveyMapEvent.NavigationStopped -> notices.clear()
      is SurveyMapEvent.Notice -> notices.show(event.message)
    }
  }

  /** Applies an onboarding outcome to the session (active survey, notices, the shell). */
  private fun onOnboardingEvent(event: OnboardingEvent) {
    when (event) {
      is OnboardingEvent.ShowScreen -> {
        shell.onScreenRequested(event.screen)
        if (event.screen == AppScreen.SIGN_IN) notices.clear()
      }
      is OnboardingEvent.SurveyOpened -> {
        activateSurvey(event.surveyId)
        shell.onSurveyOpened()
        notices.show(event.notice)
      }
      OnboardingEvent.ReturnToSurveyList -> {
        notices.clear()
        shell.onReturnToSurveyList()
      }
      OnboardingEvent.SignedOut -> {
        shell.onSignedOut()
        notices.clear()
      }
      is OnboardingEvent.Notice -> notices.show(event.message)
      is OnboardingEvent.Failure -> notices.show(event.message)
    }
  }

  /** Makes [surveyId] the active survey and clears every selection scoped to the previous one. */
  private fun activateSurvey(surveyId: String) {
    dataHolder.launch { dataHolder.surveyRepository.setActiveSurveyId(surveyId) }
    dashboard.clearLayerSelection()
    clearSelectionsForActivatedSurvey(surveyId)
  }

  /**
   * Clears every selection scoped to the survey active before [surveyId] (map feature, submission,
   * open form, status filter) and tells the [shell].
   */
  private fun clearSelectionsForActivatedSurvey(surveyId: String) {
    dataCollection.onSurveyActivated()
    viewModels.listSearch.onSurveyActivated()
    shell.onSurveyActivated(surveyId)
  }
}
