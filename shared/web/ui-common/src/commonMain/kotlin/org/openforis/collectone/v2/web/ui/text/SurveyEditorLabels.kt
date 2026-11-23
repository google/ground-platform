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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_date_order_day_first
import org.openforis.collectone.v2.web.resources.surveyeditor_date_order_month_first

/** The name of this date order, shown next to its [DateOrder.example] when importing a file. */
val DateOrder.labelRes: StringResource
  get() =
    when (this) {
      DateOrder.DAY_FIRST -> Res.string.surveyeditor_date_order_day_first
      DateOrder.MONTH_FIRST -> Res.string.surveyeditor_date_order_month_first
    }
