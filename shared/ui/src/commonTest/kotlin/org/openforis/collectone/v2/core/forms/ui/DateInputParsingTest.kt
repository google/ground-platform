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
package org.openforis.collectone.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

class DateInputParsingTest {

  @Test
  fun parseDateInput_acceptsValidIsoDatesAndLeapYears() {
    assertEquals(DateInput.Valid(2026, 6, 15), parseDateInput("2026-06-15"))
    assertEquals(DateInput.Valid(2024, 2, 29), parseDateInput("2024-02-29"))
    assertEquals(DateInput.Valid(2000, 2, 29), parseDateInput("2000-02-29"))
    assertEquals(DateInput.Valid(2026, 10, 11), parseDateInput("  2026-10-11  "))
  }

  @Test
  fun parseDateInput_treatsBlankAsEmpty() {
    assertEquals(DateInput.Empty, parseDateInput(""))
    assertEquals(DateInput.Empty, parseDateInput("   "))
  }

  @Test
  fun parseDateInput_rejectsInvalidFormatAndImpossibleDates() {
    assertIs<DateInput.Invalid>(parseDateInput("2026/06/15"))
    assertIs<DateInput.Invalid>(parseDateInput("2026-6-15"))
    assertIs<DateInput.Invalid>(parseDateInput("2026-02-29"))
    assertIs<DateInput.Invalid>(parseDateInput("1900-02-29"))
    assertIs<DateInput.Invalid>(parseDateInput("2026-04-31"))
    assertIs<DateInput.Invalid>(parseDateInput("2026-13-01"))
    assertIs<DateInput.Invalid>(parseDateInput("2026-00-10"))
    val invalid = assertIs<DateInput.Invalid>(parseDateInput("not-a-date"))
    assertEquals("Enter a valid date in YYYY-MM-DD format", invalid.message)
  }

  @Test
  fun civilDateAndUtcMillis_roundTripAcrossEpochsAndLeapYears() {
    val samples =
      listOf(
        DateInput.Valid(1970, 1, 1),
        DateInput.Valid(1969, 12, 31),
        DateInput.Valid(2000, 2, 29),
        DateInput.Valid(2024, 2, 29),
        DateInput.Valid(2026, 10, 11),
        DateInput.Valid(2099, 12, 31),
      )
    for (sample in samples) {
      val millis = civilDateToUtcMillis(sample.year, sample.month, sample.day)
      assertEquals(sample, utcMillisToCivilDate(millis))
    }
    assertEquals(0L, civilDateToUtcMillis(1970, 1, 1))
    assertEquals(-86_400_000L, civilDateToUtcMillis(1969, 12, 31))
  }

  @Test
  fun formatIsoDate_padsYearMonthAndDay() {
    assertEquals("2026-06-05", formatIsoDate(2026, 6, 5))
    assertEquals("0099-12-31", formatIsoDate(99, 12, 31))
  }

  @Test
  fun dateQuestion_updatesFromCalendarUtcMillisFormatsAndClears() {
    val xml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Date Survey</h:title>
          <model>
            <instance>
              <data id="date_survey">
                <visit_date/>
              </data>
            </instance>
            <bind nodeset="/data/visit_date" type="date" required="true()"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/visit_date">
            <label>Visit date</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()
    val controller = FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xml))
    val pickedMillis = civilDateToUtcMillis(2026, 10, 15)
    val picked = utcMillisToCivilDate(pickedMillis)
    controller.updateDate("/data/visit_date", picked.year, picked.month, picked.day)

    val step = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    val dateProto = step.control.fieldState.value?.scalar_value?.date_value
    assertEquals(2026, dateProto?.year)
    assertEquals(10, dateProto?.month)
    assertEquals(15, dateProto?.day)
    assertEquals(
      "2026-10-15",
      formatFieldValueForDisplay(step.control.fieldState.value, DataType.TYPE_DATE),
    )

    controller.clearField("/data/visit_date")
    val clearedStep = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertTrue(clearedStep.control.fieldState.isEmpty)
  }
}
