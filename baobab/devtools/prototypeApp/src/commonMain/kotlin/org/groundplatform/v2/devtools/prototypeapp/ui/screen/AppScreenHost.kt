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
package org.groundplatform.v2.devtools.prototypeapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.groundplatform.v2.devtools.prototypeapp.PrototypeApp
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyAppViewModel

/**
 * Presentation layer screen entry point (`ui/screen`) binding [SurveyAppViewModel] and
 * [PrototypeAppState] to the Compose Multiplatform UI hierarchy per Sections 3 & 6 of
 * `docs/technical/client/architecture.md`.
 */
@Composable
fun AppScreenHost(
  state: PrototypeAppState = remember { PrototypeAppState() },
  viewModel: SurveyAppViewModel = state.viewModel,
) {
  state.syncViewModelState()
  PrototypeApp(state = state)
}

/** Backward-compatible alias for [AppScreenHost]. */
@Composable
fun PrototypeAppScreen(
  state: PrototypeAppState = remember { PrototypeAppState() },
  viewModel: SurveyAppViewModel = state.viewModel,
) {
  AppScreenHost(state = state, viewModel = viewModel)
}
