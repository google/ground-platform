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
package org.openforis.collectone.v2.web.ui.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.navigation.WebPageNavigation
import org.openforis.collectone.v2.web.navigation.WebRoute
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_discard_form_cancel
import org.openforis.collectone.v2.web.resources.web_discard_form_confirm
import org.openforis.collectone.v2.web.resources.web_discard_form_message
import org.openforis.collectone.v2.web.resources.web_discard_form_title

/**
 * Whether the user is actively filling out a data collection form (as opposed to still picking the
 * target feature for a form launched from the toolbar).
 */
internal val DataCollectionUiState.isActivelyFillingForm: Boolean
  get() {
    if (!isDataCollectionFormOpen) return false
    val form = activeDataCollectionForm
    val needsEntity = form != null && form.requiresEntity && activeDataCollectionEntity == null
    return !needsEntity
  }

/**
 * Coordinates discard confirmation when the user attempts to close the form or navigate away
 * (selecting another map feature, layer, place, starting another form, or leaving the dashboard
 * page) while actively filling out a data collection form.
 */
internal class DataCollectionExitGuard(
  private val currentState: () -> DataCollectionUiState,
  private val closeForm: () -> Unit,
) {
  var pendingExitAction: (() -> Unit)? by mutableStateOf(null)
    private set

  private val isFillingForm: Boolean
    get() = currentState().isActivelyFillingForm

  private val activeEntityId: String?
    get() = currentState().activeDataCollectionEntityId

  /** Closes the form immediately when only picking a target, or asks to confirm when filling it. */
  fun requestCloseForm() {
    if (isFillingForm) {
      pendingExitAction = { closeForm() }
    } else {
      closeForm()
    }
  }

  /** Runs [action] immediately unless actively filling a form, in which case prompts first. */
  fun guardNavigation(action: () -> Unit) {
    if (isFillingForm) {
      pendingExitAction = {
        closeForm()
        action()
      }
    } else {
      action()
    }
  }

  /**
   * Guards selecting another map feature; clicking the active form's own feature passes through.
   */
  fun guardEntitySelection(entityId: String?, action: () -> Unit) {
    if (isFillingForm && entityId != activeEntityId) {
      pendingExitAction = {
        closeForm()
        action()
      }
    } else {
      action()
    }
  }

  fun confirmExit() {
    val action = pendingExitAction
    pendingExitAction = null
    action?.invoke()
  }

  fun dismiss() {
    pendingExitAction = null
  }
}

internal class GuardedSurveyMapActions(
  private val delegate: SurveyMapActions,
  private val guard: DataCollectionExitGuard,
) : SurveyMapActions by delegate {
  override fun selectEntity(entityId: String?) {
    guard.guardEntitySelection(entityId) { delegate.selectEntity(entityId) }
  }

  override fun selectCluster(clusterId: String?) {
    if (clusterId != null) {
      guard.guardNavigation { delegate.selectCluster(clusterId) }
    } else {
      delegate.selectCluster(null)
    }
  }

  override fun selectPlace(place: SurveyPlaceItem) {
    guard.guardNavigation { delegate.selectPlace(place) }
  }
}

internal class GuardedSearchableListActions(
  private val delegate: SearchableListActions,
  private val guard: DataCollectionExitGuard,
) : SearchableListActions by delegate {
  override fun selectEntityFromList(entityId: String) {
    guard.guardEntitySelection(entityId) { delegate.selectEntityFromList(entityId) }
  }

  override fun selectLayer(datasetId: String?) {
    if (datasetId != null) {
      guard.guardNavigation { delegate.selectLayer(datasetId) }
    } else {
      delegate.selectLayer(null)
    }
  }

  override fun selectPlace(placeId: String) {
    guard.guardNavigation { delegate.selectPlace(placeId) }
  }
}

internal class GuardedWebPageNavigation(
  private val delegate: WebPageNavigation,
  private val guard: DataCollectionExitGuard,
) : WebPageNavigation {
  override fun open(route: WebRoute) {
    guard.guardNavigation { delegate.open(route) }
  }

  override fun returnTo(route: WebRoute) {
    guard.guardNavigation { delegate.returnTo(route) }
  }

  override fun update(route: WebRoute) = delegate.update(route)
}

internal class GuardedDataCollectionActions(
  private val delegate: DataCollectionActions,
  private val guard: DataCollectionExitGuard,
) : DataCollectionActions by delegate {
  override fun launchFormFromFab(formId: String, pickerMode: MainSurveyViewMode) {
    guard.guardNavigation { delegate.launchFormFromFab(formId, pickerMode) }
  }

  override fun launchFormForEntity(entityId: String, formId: String) {
    guard.guardNavigation { delegate.launchFormForEntity(entityId, formId) }
  }
}

/** Confirmation dialog shown when the user tries to close or navigate away from an active form. */
@Composable
internal fun DiscardDataCollectionFormDialog(guard: DataCollectionExitGuard) {
  if (guard.pendingExitAction == null) return
  AlertDialog(
    onDismissRequest = guard::dismiss,
    title = { Text(stringResource(Res.string.web_discard_form_title)) },
    text = { Text(stringResource(Res.string.web_discard_form_message)) },
    confirmButton = {
      TextButton(onClick = guard::confirmExit) {
        Text(stringResource(Res.string.web_discard_form_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = guard::dismiss) {
        Text(stringResource(Res.string.web_discard_form_cancel))
      }
    },
  )
}
