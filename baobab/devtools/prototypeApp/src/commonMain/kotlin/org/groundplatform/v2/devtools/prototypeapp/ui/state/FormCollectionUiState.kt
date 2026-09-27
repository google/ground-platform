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

import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode

/**
 * Immutable UI state slice representing active data collection form execution, wizard controller,
 * and `entityRef` selector state.
 */
data class FormCollectionUiState(
  val activeDataCollectionEntityId: String? = null,
  val activeDataCollectionFormId: String? = null,
  val activeFormWizardController: FormWizardController? = null,
  val isAvailableFormsSheetOpen: Boolean = false,
  val wasFormLaunchedWithoutEntity: Boolean = false,
  val entityRefSelectorViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
  val entityRefSearchQuery: String = "",
)
