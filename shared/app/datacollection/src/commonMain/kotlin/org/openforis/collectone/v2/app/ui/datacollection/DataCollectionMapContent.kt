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

import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState

// The survey maps of SurveyMapContent built from the open form's data collection state.

/**
 * The main survey map (mobile and web dashboard) from the map viewport's [uiState] and the open
 * form's [dataCollection] state: the geometry being drawn and the form's geometry answers (web
 * dashboard).
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 * @param hoveredEntityId the map feature under the mouse pointer, if any (web dashboard).
 */
fun SurveyMapContent.main(
  uiState: SurveyMapUiState,
  dataCollection: DataCollectionUiState,
  pendingIds: Set<String>,
  showNavigation: Boolean,
  hoveredEntityId: String? = null,
): SurveyMap =
  main(
    uiState = uiState,
    pendingIds = pendingIds,
    showNavigation = showNavigation,
    draftGeometry = dataCollection.webMapDraftGeometry,
    formGeometries = dataCollection.webFormGeometries,
    hoveredEntityId = hoveredEntityId,
  )

/**
 * The map in a form's `entityref` step, listing [form]'s candidate map features from the open
 * form's [dataCollection] state and highlighting its target feature.
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
fun SurveyMapContent.entityRefForm(
  uiState: SurveyMapUiState,
  dataCollection: DataCollectionUiState,
  form: FormPreviewItem,
  pendingIds: Set<String>,
): SurveyMap =
  entityRefForm(
    uiState = uiState,
    entities = dataCollection.allDatasetEntitiesForForm(form),
    selectedEntityId = dataCollection.activeDataCollectionEntityId,
    pendingIds = pendingIds,
  )

/**
 * The map behind a GeoPoint question, highlighting the open form's target feature from its
 * [dataCollection] state.
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
fun SurveyMapContent.geoPointForm(
  uiState: SurveyMapUiState,
  dataCollection: DataCollectionUiState,
  pendingIds: Set<String>,
  isFollowingUser: Boolean,
): SurveyMap =
  geoPointForm(
    uiState = uiState,
    selectedEntityId = dataCollection.activeDataCollectionEntityId,
    pendingIds = pendingIds,
    isFollowingUser = isFollowingUser,
  )
