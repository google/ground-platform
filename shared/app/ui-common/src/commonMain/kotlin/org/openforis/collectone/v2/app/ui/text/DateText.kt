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
package org.openforis.collectone.v2.app.ui.text

import kotlinx.datetime.LocalDate
import org.openforis.collectone.v2.app.domain.model.editor.isoDateOrNull
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.date_medium
import org.openforis.collectone.v2.app.resources.date_month_short_1
import org.openforis.collectone.v2.app.resources.date_month_short_10
import org.openforis.collectone.v2.app.resources.date_month_short_11
import org.openforis.collectone.v2.app.resources.date_month_short_12
import org.openforis.collectone.v2.app.resources.date_month_short_2
import org.openforis.collectone.v2.app.resources.date_month_short_3
import org.openforis.collectone.v2.app.resources.date_month_short_4
import org.openforis.collectone.v2.app.resources.date_month_short_5
import org.openforis.collectone.v2.app.resources.date_month_short_6
import org.openforis.collectone.v2.app.resources.date_month_short_7
import org.openforis.collectone.v2.app.resources.date_month_short_8
import org.openforis.collectone.v2.app.resources.date_month_short_9

/** Abbreviated month names, January first. */
private val SHORT_MONTH_NAMES =
  listOf(
    Res.string.date_month_short_1,
    Res.string.date_month_short_2,
    Res.string.date_month_short_3,
    Res.string.date_month_short_4,
    Res.string.date_month_short_5,
    Res.string.date_month_short_6,
    Res.string.date_month_short_7,
    Res.string.date_month_short_8,
    Res.string.date_month_short_9,
    Res.string.date_month_short_10,
    Res.string.date_month_short_11,
    Res.string.date_month_short_12,
  )

/**
 * [date] in the user's language with an abbreviated month, e.g. "Mar 5, 2026" in English or "5 mars
 * 2026" in French.
 */
fun mediumDateText(date: LocalDate): UiText =
  Res.string.date_medium.toUiText(SHORT_MONTH_NAMES[date.month.ordinal], date.day, date.year)

/** [iso] (`YYYY-MM-DD`) as [mediumDateText], or [iso] as it is when it isn't a real date. */
fun isoDateText(iso: String): UiText =
  isoDateOrNull(iso)?.let(::mediumDateText) ?: UiText.Plain(iso)
