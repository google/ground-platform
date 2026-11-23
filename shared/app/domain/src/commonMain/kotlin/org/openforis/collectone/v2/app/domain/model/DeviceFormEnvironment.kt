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
package org.openforis.collectone.v2.app.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment

/**
 * The form engine environment of a device collecting data: the time is [clock]'s, and dates and
 * times of day (`today()`, the `date` and `time` preloads, the time question's Now button) are in
 * [timeZone], the device's own by default.
 *
 * Forms opened without it run on [FormEnvironment.DEFAULT], whose clock is fixed at 2026-09-17 for
 * deterministic tests, so every new submission would get that date.
 */
fun deviceFormEnvironment(
  clock: Clock,
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
): FormEnvironment =
  FormEnvironment(
    clockEpochMillis = { clock.now().toEpochMilliseconds() },
    utcOffsetMillis = { epochMillis ->
      timeZone.offsetAt(Instant.fromEpochMilliseconds(epochMillis)).totalSeconds * 1_000L
    },
  )
