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
package org.openforis.collectone.v2.web.ui.text

import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.measurementText
import org.openforis.collectone.v2.web.domain.model.ImpactFormat

/**
 * [measurement] as impact views and reports show it, e.g. `1,234.5 ha`: the number like other
 * impact numbers (thousands separators, trailing zeros cut) and the unit in the user's language.
 */
fun impactMeasurementText(measurement: Measurement): UiText =
  measurementText(
    ImpactFormat.number(measurement.value, measurement.decimals ?: 1),
    measurement.unit,
  )
