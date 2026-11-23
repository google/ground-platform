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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.math.roundToLong
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.measurementText

/**
 * An area of [squareMeters] in the user's language (`8.50 ha`, `850 ha`, `1,234 km²`), as
 * [SurveyAreaGeometry.displayArea] rounds it, with thousands separators.
 */
fun surveyAreaText(squareMeters: Double): UiText {
  val area = SurveyAreaGeometry.displayArea(squareMeters)
  val decimals = area.decimals ?: 0
  val amount =
    if (decimals == 0) SurveyAreaGeometry.groupThousands(area.value.roundToLong())
    else formatFixed(area.value, decimals)
  return measurementText(amount, area.unit)
}
