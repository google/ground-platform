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
package org.openforis.collectone.v2.app.ui.text

import androidx.compose.runtime.Composable
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_wayfinding_badge

/** The distance and bearing of [vector] in the user's language, e.g. `495 m • 319° NW`. */
fun wayfindingBadgeText(vector: StraightLineVector): UiText =
  Res.string.app_wayfinding_badge.toUiText(vector.distance.toUiText(), vector.formattedBearing)

/** [wayfindingBadgeText] of [vector] in the current language, or `""` without a [vector]. */
@Composable
fun wayfindingBadge(vector: StraightLineVector?): String =
  vector?.let { wayfindingBadgeText(it).resolve() }.orEmpty()
