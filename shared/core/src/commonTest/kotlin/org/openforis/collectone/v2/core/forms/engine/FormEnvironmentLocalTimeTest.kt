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
package org.openforis.collectone.v2.core.forms.engine

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema

/**
 * A new record takes its dates and times of day from the [FormEnvironment]'s local time zone (as
 * ODK Collect does), while its timestamps stay UTC instants.
 */
class FormEnvironmentLocalTimeTest {
  /** 2026-11-10T02:30:15Z: still 2026-11-09 21:30:15 in New York (UTC-5). */
  private val nowMillis = 1_794_277_815_000L

  private val newYork =
    FormEnvironment(clockEpochMillis = { nowMillis }, utcOffsetMillis = { -5 * HOUR })

  /** 2026-11-09T20:00:00Z: already 2026-11-10 03:00 in Vientiane (UTC+7). */
  private val vientiane =
    FormEnvironment(
      clockEpochMillis = { nowMillis - 6 * HOUR - 30 * MINUTE - 15_000L },
      utcOffsetMillis = { 7 * HOUR },
    )

  @Test
  fun todayPreload_isTheLocalDate() {
    assertEquals(Date(2026, 11, 9), dateAt("visit_date", newYork))
    assertEquals(Date(2026, 11, 10), dateAt("visit_date", vientiane))
  }

  @Test
  fun todayFunction_isTheLocalDate() {
    assertEquals(Date(2026, 11, 9), dateAt("calculated_date", newYork))
    assertEquals(Date(2026, 11, 10), dateAt("calculated_date", vientiane))
  }

  @Test
  fun metaToday_isTheLocalDate() {
    val state = FormEngine.initialize(form, environment = newYork)

    assertEquals(Date(2026, 11, 9), state.recordInstance.metadata?.today)
  }

  @Test
  fun timePreload_isTheLocalTimeOfDay() {
    val state = FormEngine.initialize(form, environment = newYork)

    assertEquals(
      TimeOfDay(hours = 21, minutes = 30, seconds = 15),
      state.findFieldState("visit_time")?.value?.scalar_value?.time_value,
    )
  }

  @Test
  fun startPreload_staysTheUtcInstant() {
    val state = FormEngine.initialize(form, environment = newYork)

    assertEquals(
      ofEpochSecond(nowMillis / 1000L, 0L),
      state.findFieldState("started")?.value?.scalar_value?.timestamp_value,
    )
    assertEquals(ofEpochSecond(nowMillis / 1000L, 0L), state.recordInstance.metadata?.start_time)
  }

  @Test
  fun withoutAnOffset_datesAreUtc() {
    val utc = FormEnvironment(clockEpochMillis = { nowMillis })

    assertEquals(Date(2026, 11, 10), dateAt("visit_date", utc))
    assertEquals(Date(2026, 11, 10), dateAt("calculated_date", utc))
  }

  @Test
  fun localClockEpochMillis_shiftsTheClockByTheOffset() {
    assertEquals(nowMillis - 5 * HOUR, newYork.localClockEpochMillis())
  }

  private fun dateAt(name: String, environment: FormEnvironment): Date? =
    FormEngine.initialize(form, environment = environment)
      .findFieldState(name)
      ?.value
      ?.scalar_value
      ?.date_value

  private val form: FormDef =
    FormDef(
      form_id = "site_visit",
      version = "1",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "visit",
                  fields =
                    listOf(
                      FieldDefinition(name = "visit_date", type = DataType.TYPE_DATE),
                      FieldDefinition(name = "calculated_date", type = DataType.TYPE_DATE),
                      FieldDefinition(name = "visit_time", type = DataType.TYPE_TIME),
                      FieldDefinition(name = "started", type = DataType.TYPE_DATETIME),
                    ),
                )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "visit_date",
                type = DataType.TYPE_DATE,
                preload = PreloadType.PRELOAD_DATE,
                preload_param = "today",
              ),
              FieldBinding(
                field_path = "calculated_date",
                type = DataType.TYPE_DATE,
                calculate_expression = "today()",
              ),
              FieldBinding(
                field_path = "visit_time",
                type = DataType.TYPE_TIME,
                preload = PreloadType.PRELOAD_TIME,
              ),
              FieldBinding(
                field_path = "started",
                type = DataType.TYPE_DATETIME,
                preload = PreloadType.PRELOAD_TIMESTAMP,
                preload_param = "start",
              ),
            ),
        ),
    )

  private companion object {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
  }
}
