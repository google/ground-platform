/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.PropertyType
import org.groundplatform.v2.web.domain.model.editor.Csv
import org.groundplatform.v2.web.domain.model.editor.DataTableImporter
import org.groundplatform.v2.web.domain.model.editor.DateOrder
import org.groundplatform.v2.web.domain.model.editor.ImportDates
import org.groundplatform.v2.web.ui.state.SurveyEditorSection
import org.groundplatform.v2.web.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.web.ui.viewmodel.ui

class DataTableImportTest {

  @Test
  fun csv_handlesQuotesNewlinesAndCrLf() {
    val records = Csv.parse("\uFEFFa,b\r\n\"x, y\",\"say \"\"hi\"\"\"\r\n\"multi\nline\",2")
    assertEquals(
      listOf(listOf("a", "b"), listOf("x, y", "say \"hi\""), listOf("multi\nline", "2")),
      records,
    )
  }

  @Test
  fun csv_detectsSemicolonAndTab() {
    assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a;b\n1;2\n"))
    assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a\tb\n1\t2"))
  }

  @Test
  fun plan_usesIdColumnAsKeyAndNameAsLabel_andInfersTypes() {
    val csv =
      """
      ID,Name,Count,Area (ha),Active,Code
      S1,North,3,1.5,yes,007
      S2,South,4,2,no,010
      """
        .trimIndent()
    val result = DataTableImporter.plan("sites.csv", csv)
    val plan = assertNotNull(result.plan)
    assertEquals("sites", plan.name)
    assertEquals("id", plan.keyProperty)
    assertEquals("name", plan.labelProperty)
    assertEquals(
      listOf("id", "name", "count", "area__ha_", "active", "code"),
      plan.properties.map { it.name },
    )
    val types = plan.properties.associate { it.name to it.type }
    assertEquals(PropertyType.TEXT, types["id"])
    assertEquals(PropertyType.INTEGER, types["count"])
    assertEquals(PropertyType.DECIMAL, types["area__ha_"])
    assertEquals(PropertyType.BOOLEAN, types["active"])
    assertEquals(PropertyType.TEXT, types["code"])
    assertEquals("Area (ha)", plan.properties.first { it.name == "area__ha_" }.label)
    assertEquals(
      mapOf(
        "id" to "S1",
        "name" to "North",
        "count" to "3",
        "area__ha_" to "1.5",
        "active" to "yes",
        "code" to "007",
      ),
      plan.rows.first(),
    )
    assertTrue(result.notes.isEmpty())
  }

  @Test
  fun plan_generatesKeyWhenIdIsMissingOrRepeated_andNotesRaggedRows() {
    val result = DataTableImporter.plan("x.csv", "id,species\nA,oak\nA,pine,extra\n\n,ash")
    val plan = assertNotNull(result.plan)
    assertEquals("row_id", plan.keyProperty)
    assertEquals("row_id", plan.properties.first().name)
    assertEquals(3, plan.rows.size)
    assertEquals("R-0002", plan.rows[1]["row_id"])
    assertEquals("pine", plan.rows[1]["species"])
    assertEquals(2, result.notes.size)
  }

  @Test
  fun plan_withoutDataRowsHasNoPlan() {
    assertNull(DataTableImporter.plan("x.csv", "").plan)
    assertNull(DataTableImporter.plan("x.csv", "a,b\n").plan)
  }

  private fun dates(vararg values: String, order: DateOrder = DateOrder.DAY_FIRST) =
    ImportDates.column(values.toList(), order)

  @Test
  fun dates_unambiguousFormatsConvertToIso() {
    assertEquals(
      listOf("2025-04-03", "2025-12-31", "", "2024-02-29"),
      assertNotNull(dates("2025-04-03", "2025/12/31", "", "2024.2.29")).isoValues,
    )
    assertEquals(
      listOf("2025-04-03", "2025-09-30", "2025-01-15", "2025-03-07"),
      assertNotNull(dates("3 Apr 2025", "30-Sept-2025", "January 15, 2025", "Mar 7th 2025"))
        .isoValues,
    )
  }

  @Test
  fun dates_orderSettledByData() {
    // 25 can only be a day, so the whole column is day first, even in a month-first locale.
    val dayFirst = assertNotNull(dates("03/04/2025", "25/12/2025", order = DateOrder.MONTH_FIRST))
    assertEquals(listOf("2025-04-03", "2025-12-25"), dayFirst.isoValues)
    assertFalse(dayFirst.ambiguous)

    val monthFirst = assertNotNull(dates("03/04/2025", "12/25/2025"))
    assertEquals(listOf("2025-03-04", "2025-12-25"), monthFirst.isoValues)
    assertFalse(monthFirst.ambiguous)

    // Conflicting evidence: not a date column.
    assertNull(dates("25/12/2025", "12/25/2025"))
  }

  @Test
  fun dates_ambiguousUsesPreferredOrder() {
    val us = assertNotNull(dates("03/04/2025", "1-2-25", order = DateOrder.MONTH_FIRST))
    assertEquals(listOf("2025-03-04", "2025-01-02"), us.isoValues)
    assertTrue(us.ambiguous)

    val eu = assertNotNull(dates("03.04.2025", "01.02.1999", order = DateOrder.DAY_FIRST))
    assertEquals(listOf("2025-04-03", "1999-02-01"), eu.isoValues)
    assertTrue(eu.ambiguous)

    // Same day and month: no ambiguity.
    assertFalse(assertNotNull(dates("05/05/2025")).ambiguous)
  }

  @Test
  fun dates_rejectsNonDates() {
    assertNull(dates("2025-02-30"))
    assertNull(dates("31/04/2025"))
    assertNull(dates("1.2.25")) // Looks like a version number.
    assertNull(dates("2025-04-03T10:00:00"))
    assertNull(dates("3 Foo 2025"))
    assertNull(dates("2025-04-03", "soon"))
    assertNull(dates("", " "))
  }

  @Test
  fun dateOrder_forRegion() {
    assertEquals(DateOrder.MONTH_FIRST, DateOrder.forRegion("US"))
    assertEquals(DateOrder.MONTH_FIRST, DateOrder.forRegion("ph"))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion("GB"))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion("KE"))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion(""))
    assertEquals(DateOrder.DAY_FIRST, DateOrder.forRegion(null))
  }

  @Test
  fun plan_guessesDateColumnsAndNotesAmbiguity() {
    val csv = "name,visited,planted\nA,03/04/2025,2020-01-01\nB,04/05/2025,\n"
    val us = DataTableImporter.plan("x.csv", csv, DateOrder.MONTH_FIRST)
    val plan = assertNotNull(us.plan)
    assertEquals(PropertyType.DATE, plan.properties.first { it.name == "visited" }.type)
    assertEquals(PropertyType.DATE, plan.properties.first { it.name == "planted" }.type)
    assertEquals("2025-03-04", plan.rows[0]["visited"])
    assertEquals("2020-01-01", plan.rows[0]["planted"])
    assertNull(plan.rows[1]["planted"])
    assertEquals(listOf("visited"), plan.ambiguousDateColumns)
    assertTrue(us.notes.single().contains("MM/DD/YYYY"))

    val preview = DataTableImportPreview.of("x.csv", csv, DateOrder.MONTH_FIRST)
    val flipped = preview.withDateOrder(DateOrder.DAY_FIRST)
    assertEquals("2025-04-03", assertNotNull(flipped.plan).rows[0]["visited"])
    assertEquals(DateOrder.DAY_FIRST, flipped.plan.dateOrder)
  }

  @Test
  fun importDataTable_createsAndOpensTable() {
    val state = surveyEditorViewModel()
    val preview = DataTableImportPreview.of("Crop varieties.csv", "code,name\nQR,Oak\nPS,Pine")
    val key = state.importDataTable(assertNotNull(preview.plan))

    val dataset = state.ui.datasets.first { it.key == key }
    assertEquals(DatasetKind.DATA_TABLE, dataset.kind)
    assertEquals("Crop varieties", dataset.displayName)
    assertEquals(2, dataset.rows.size)
    assertTrue(dataset.rows.all { it.geometry.isEmpty() })
    assertEquals("Oak", dataset.rows.first().values["name"])
    assertEquals(SurveyEditorSection.Dataset(key), state.ui.section)
    assertTrue(state.ui.datasetIssues(dataset).isEmpty())
  }
}
