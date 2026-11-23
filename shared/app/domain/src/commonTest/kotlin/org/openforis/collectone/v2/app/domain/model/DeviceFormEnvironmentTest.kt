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

import com.google.type.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import org.openforis.collectone.v2.app.testing.FakeClock
import org.openforis.collectone.v2.core.forms.engine.FormSession

/** [deviceFormEnvironment] runs forms on the device's clock, in its time zone. */
class DeviceFormEnvironmentTest {
  private val clock = FakeClock(Instant.parse("2026-11-10T02:30:00Z"))
  private val newYork = FixedOffsetTimeZone(UtcOffset(hours = -5))

  @Test
  fun clock_isTheDeviceClock() {
    val environment = deviceFormEnvironment(clock, newYork)

    assertEquals(clock.current.toEpochMilliseconds(), environment.clockEpochMillis())
    clock.advanceBy(1.hours)
    assertEquals(clock.current.toEpochMilliseconds(), environment.clockEpochMillis())
  }

  @Test
  fun utcOffset_isTheTimeZoneOffset() {
    val environment = deviceFormEnvironment(clock, newYork)

    assertEquals(-5 * 3_600_000L, environment.utcOffsetMillis(clock.current.toEpochMilliseconds()))
    assertEquals(0L, deviceFormEnvironment(clock, TimeZone.UTC).utcOffsetMillis(0L))
  }

  @Test
  fun newRecord_getsTheDeviceDate_notTheEngineDefault() {
    val session =
      FormSession(formDef = todayForm, environment = deviceFormEnvironment(clock, newYork))

    // The engine's default clock is fixed at 2026-09-17; the device's local date is 2026-11-09.
    assertEquals(Date(2026, 11, 9), session.state.recordInstance.metadata?.today)
    assertEquals(
      Date(2026, 11, 9),
      session.state.fieldStates["/visit/visit_date"]?.value?.scalar_value?.date_value,
    )
  }

  private val todayForm =
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
                  fields = listOf(FieldDefinition(name = "visit_date", type = DataType.TYPE_DATE)),
                )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "visit_date",
                type = DataType.TYPE_DATE,
                preload = PreloadType.PRELOAD_DATE,
                preload_param = "today",
              )
            ),
        ),
    )
}
