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
package org.groundplatform.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.domain.model.FormAvailability

class CollectEarthIdmParserTest {
  @Test
  fun parsesCollectEarthIdmSchemaIntoEditorForm() {
    val xml =
      """
      <survey xmlns="http://www.openforis.org/idml/3.0">
        <project>Amazonia Land Use Assessment</project>
        <codeLists>
          <list name="land_use_category">
            <item>
              <code>forest</code>
              <label>Forest</label>
            </item>
            <item>
              <code>cropland</code>
              <label>Cropland</label>
            </item>
          </list>
        </codeLists>
        <schema>
          <entity name="plot">
            <code name="land_use" list="land_use_category" required="true">
              <label>IPCC Land Use Category</label>
              <description>Select the dominant land use category</description>
            </code>
            <number name="canopy_cover_pct" type="real">
              <label>Canopy cover (%)</label>
            </number>
            <boolean name="actively_used">
              <label>Is the plot actively managed?</label>
            </boolean>
            <coordinate name="plot_center">
              <label>Plot center coordinate</label>
            </coordinate>
          </entity>
        </schema>
      </survey>
      """
        .trimIndent()

    val imported =
      FormImport.fromXml(
        xml = xml,
        fallbackTitle = "Fallback Title",
        availability = FormAvailability.BOTH,
      )
    assertNotNull(imported)
    assertEquals("Amazonia Land Use Assessment", imported.form.title)
    assertEquals(FormAvailability.BOTH, imported.form.availability)
    assertEquals(4, imported.form.questions.size)

    val q1 = imported.form.questions[0]
    assertEquals("land_use", q1.name)
    assertEquals(EditorQuestionType.SELECT_ONE, q1.type)
    assertEquals("IPCC Land Use Category", q1.label)
    assertTrue(q1.required)
    assertEquals(listOf("forest", "cropland"), q1.choices.map { it.value })

    val q2 = imported.form.questions[1]
    assertEquals("canopy_cover_pct", q2.name)
    assertEquals(EditorQuestionType.NUMBER, q2.type)
    assertEquals(EditorQuestion.DEFAULT_DECIMAL_PLACES, q2.decimalPlaces)

    val q3 = imported.form.questions[2]
    assertEquals("actively_used", q3.name)
    assertEquals(EditorQuestionType.SELECT_ONE, q3.type)
    assertEquals(listOf("yes", "no"), q3.choices.map { it.value })

    val q4 = imported.form.questions[3]
    assertEquals("plot_center", q4.name)
    assertEquals(EditorQuestionType.LOCATION, q4.type)
  }

  @Test
  fun returnsNullForNonIdmOrEmptyXml() {
    assertNull(CollectEarthIdmParser.fromXml("<not-a-survey/>"))
    assertNull(CollectEarthIdmParser.fromXml("<survey><project>Empty</project></survey>"))
  }
}
