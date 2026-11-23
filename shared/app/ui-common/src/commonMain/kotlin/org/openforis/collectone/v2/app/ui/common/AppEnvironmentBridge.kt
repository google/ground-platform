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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.openforis.collectone.v2.app.domain.model.AppEnvironment

/**
 * Overrides the detected [AppEnvironment] inside a Compose subtree (for previews and UI tests).
 * When null, [currentAppEnvironment] delegates to [platformAppEnvironment].
 */
val LocalAppEnvironment = staticCompositionLocalOf<AppEnvironment?> { null }

/** Detects the host platform's active [AppEnvironment]. */
@Composable expect fun platformAppEnvironment(): AppEnvironment

/** Returns the active [AppEnvironment], honoring [LocalAppEnvironment] when provided. */
@Composable
fun currentAppEnvironment(): AppEnvironment =
  LocalAppEnvironment.current ?: platformAppEnvironment()
