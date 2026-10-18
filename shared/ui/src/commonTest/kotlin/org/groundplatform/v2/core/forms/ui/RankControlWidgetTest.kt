/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class RankControlWidgetTest {

  @Test
  fun rankDragTargetIndex_roundsToNearestSlotAndClamps() {
    assertEquals(1, rankDragTargetIndex(from = 1, delta = 40f, pitch = 100f, count = 4))
    assertEquals(2, rankDragTargetIndex(from = 1, delta = 60f, pitch = 100f, count = 4))
    assertEquals(0, rankDragTargetIndex(from = 1, delta = -160f, pitch = 100f, count = 4))
    assertEquals(3, rankDragTargetIndex(from = 1, delta = 900f, pitch = 100f, count = 4))
  }

  @Test
  fun rankDragShiftFor_opensGapAtTarget() {
    // Dragging item 0 down to slot 2: items 1 and 2 shift up (-1) by one slot.
    assertEquals(listOf(0, -1, -1, 0), (0..3).map { rankDragShiftFor(it, from = 0, target = 2) })
    // Dragging item 2 up to slot 0: items 0 and 1 shift down (+1) by one slot.
    assertEquals(listOf(1, 1, 0, 0), (0..3).map { rankDragShiftFor(it, from = 2, target = 0) })
  }

  @Test
  fun movedRankItem_relocatesElementAndPreservesInvalidIndices() {
    assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").movedRankItem(0, 2))
    assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").movedRankItem(2, 0))
    assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedRankItem(1, 1))
    assertEquals(listOf("a", "b"), listOf("a", "b").movedRankItem(0, 5))
  }

  @Test
  fun rankReorderState_tracksLiveBadgesAndReportsDropTarget() {
    val drag = RankReorderState()
    val keys = listOf("riparian_buffer", "native_canopy", "soil_carbon")

    drag.start("riparian_buffer", index = 0, count = 3, pitch = 60f)
    assertTrue(drag.isDragging)
    assertEquals(0, drag.targetIndex)

    // Drag down past two slots (120px) so item 0 hovers over index 2 (#3).
    drag.dragBy(125f)
    assertEquals(2, drag.targetIndex)
    assertEquals(0, drag.shiftFor(0))
    assertEquals(-1, drag.shiftFor(1))
    assertEquals(-1, drag.shiftFor(2))

    // Live rank positions (0-based): dragged item shows #3 (index 2), siblings shift up to #1, #2.
    assertEquals(
      listOf(2, 0, 1),
      keys.mapIndexed { index, key -> drag.displayIndexFor(index, key, keys.size) },
    )

    // Clamped to half a pitch past the end (2 * 60 + 30 = 150f).
    drag.dragBy(5000f)
    assertEquals(150f, drag.offset)

    assertEquals("riparian_buffer" to 2, drag.end())
    assertFalse(drag.isDragging)
    assertNull(drag.end())
  }

  @Test
  fun draggingRankOptionUpdatesControllerFieldOrder() {
    val xml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Rank Form</h:title>
          <model>
            <instance>
              <data id="rank_form">
                <priority>riparian_buffer native_canopy soil_carbon</priority>
              </data>
            </instance>
            <bind nodeset="/data/priority" type="rank"/>
          </model>
        </h:head>
        <h:body>
          <rank ref="/data/priority">
            <label>Rank Conservation Interventions</label>
            <item><label>Riparian Stream Buffer Protection</label><value>riparian_buffer</value></item>
            <item><label>Indigenous Shade Tree Enrichment</label><value>native_canopy</value></item>
            <item><label>Organic Mulch &amp; Soil Carbon Retention</label><value>soil_carbon</value></item>
          </rank>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val controller = FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xml))
    val step = controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().single()
    val path = step.control.canonicalPath
    val initialValues =
      step.control.fieldState.value?.list_value?.values?.mapNotNull { it.string_value }.orEmpty()
    assertEquals(listOf("riparian_buffer", "native_canopy", "soil_carbon"), initialValues)

    // Simulate dragging item at index 2 ("soil_carbon") to the top (index 0).
    val reordered = initialValues.movedRankItem(from = 2, to = 0)
    controller.updateMultiSelect(path, reordered)

    val updatedStep = controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().single()
    val updatedValues =
      updatedStep.control.fieldState.value?.list_value?.values?.mapNotNull { it.string_value }
    assertEquals(listOf("soil_carbon", "riparian_buffer", "native_canopy"), updatedValues)
  }
}
