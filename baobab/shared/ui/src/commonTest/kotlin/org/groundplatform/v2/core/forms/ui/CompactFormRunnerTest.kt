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
package org.groundplatform.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class CompactFormRunnerTest {

  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms"
            xmlns:h="http://www.w3.org/1999/xhtml"
            xmlns:jr="http://openrosa.org/javarosa">
      <h:head>
        <h:title>Compact Layout</h:title>
        <model>
          <instance>
            <data id="compact" version="1">
              <species/>
              <height_m/>
              <giant_notes/>
              <site>
                <soil/>
                <slope/>
              </site>
              <branches>
                <branch_length_m/>
              </branches>
            </data>
          </instance>
          <bind nodeset="/data/species" type="string" required="true()"/>
          <bind nodeset="/data/height_m" type="decimal" constraint=". &gt; 0" jr:constraintMsg="Height must be positive"/>
          <bind nodeset="/data/giant_notes" type="string" relevant="/data/height_m &gt;= 20"/>
          <bind nodeset="/data/site/soil" type="string"/>
          <bind nodeset="/data/site/slope" type="string"/>
          <bind nodeset="/data/branches/branch_length_m" type="decimal"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/species"><label>Tree Species</label></input>
        <input ref="/data/height_m"><label>Height (meters)</label></input>
        <input ref="/data/giant_notes"><label>Giant Baobab Notes</label></input>
        <group ref="/data/site" appearance="field-list">
          <label>Site</label>
          <input ref="/data/site/soil"><label>Soil</label></input>
          <input ref="/data/site/slope"><label>Slope</label></input>
        </group>
        <group ref="/data/branches">
          <label>Branch Measurements</label>
          <repeat nodeset="/data/branches">
            <input ref="/data/branches/branch_length_m"><label>Branch Length (m)</label></input>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))

  private fun questionPaths(controller: FormWizardController) =
    buildCompactFormItems(controller.steps).filterIsInstance<CompactFormItem.Question>().map {
      it.step.stepKey
    }

  @Test
  fun buildCompactFormItems_flattensFieldListGroupsAndDropsReviewStep() {
    val controller = controller()
    val items = buildCompactFormItems(controller.steps)

    // Questions, a heading for the field-list group with its two questions, then the repeat hub.
    // /data/giant_notes is not relevant yet.
    assertEquals(
      listOf("/data/species", "/data/height_m", "/data/site/soil", "/data/site/slope"),
      questionPaths(controller),
    )
    val heading = assertIs<CompactFormItem.GroupHeading>(items[2])
    assertEquals("Site", heading.step.title)
    assertIs<CompactFormItem.RepeatHub>(items.last())

    // The controller still ends with the "Review & Submit" step (submission relies on it), but the
    // compact stack has no entry for it.
    val review = assertIs<FormWizardStep.SummaryStep>(controller.steps.last())
    assertEquals("Review & Submit", review.title)
    assertTrue(
      items.none {
        val step =
          when (it) {
            is CompactFormItem.Question -> it.step
            is CompactFormItem.GroupHeading -> it.step
            is CompactFormItem.RepeatHub -> it.step
          }
        step.stepKey == review.stepKey || step.title == review.title
      }
    )
  }

  @Test
  fun buildCompactFormItems_followsRelevance() {
    val controller = controller()
    controller.updateDouble("/data/height_m", 25.0)
    assertTrue("/data/giant_notes" in questionPaths(controller))
    controller.updateDouble("/data/height_m", 5.0)
    assertFalse("/data/giant_notes" in questionPaths(controller))
  }

  @Test
  fun compactQuestionStatus_reflectsValueSkipAndValidation() {
    val controller = controller()
    fun control(path: String) =
      buildCompactFormItems(controller.steps)
        .filterIsInstance<CompactFormItem.Question>()
        .first { it.step.stepKey == path }
        .step
        .control

    // Empty required question is pending (not invalid) until the collector tries to submit.
    assertEquals(
      CompactQuestionStatus.PENDING,
      compactQuestionStatus(control("/data/species"), isSkipped = false),
    )
    // A required question can't be skipped.
    assertEquals(
      CompactQuestionStatus.PENDING,
      compactQuestionStatus(control("/data/species"), isSkipped = true),
    )
    // Optional empty question marked skipped.
    assertEquals(
      CompactQuestionStatus.SKIPPED,
      compactQuestionStatus(control("/data/height_m"), isSkipped = true),
    )
    // A value wins over a stale skip mark.
    controller.updateDouble("/data/height_m", 12.0)
    assertEquals(
      CompactQuestionStatus.ANSWERED,
      compactQuestionStatus(control("/data/height_m"), isSkipped = true),
    )
    // A value violating its constraint is invalid.
    controller.updateDouble("/data/height_m", -3.0)
    assertEquals(
      CompactQuestionStatus.INVALID,
      compactQuestionStatus(control("/data/height_m"), isSkipped = false),
    )
  }

  @Test
  fun compactFormProgress_countsEveryStatus() {
    val controller = controller()
    val layout = CompactFormLayoutState()
    controller.updateString("/data/species", "Adansonia digitata")
    layout.skip("/data/site/soil")
    controller.updateDouble("/data/height_m", -1.0)

    val progress = compactFormProgress(buildCompactFormItems(controller.steps), layout::isSkipped)
    assertEquals(1, progress.answered)
    assertEquals(1, progress.skipped)
    assertEquals(1, progress.invalid)
    assertEquals(1, progress.pending) // /data/site/slope
    // Four question cards; the "Review & Submit" step is not counted in `n of m answered`.
    assertEquals(4, progress.total)
    assertEquals(0.5f, progress.fraction)
  }

  @Test
  fun layoutState_tracksExpansionAndSkips() {
    val layout = CompactFormLayoutState(initiallyExpandAll = false)
    assertFalse(layout.isExpanded("/data/species"))
    layout.toggle("/data/species")
    assertTrue(layout.isExpanded("/data/species"))

    layout.skip("/data/height_m")
    assertTrue(layout.isSkipped("/data/height_m"))
    assertFalse(layout.isExpanded("/data/height_m"))
    layout.unskip("/data/height_m")
    assertFalse(layout.isSkipped("/data/height_m"))

    layout.expandAll(listOf("/data/a", "/data/b"))
    assertTrue(layout.isExpanded("/data/a") && layout.isExpanded("/data/b"))
    layout.collapseAll(listOf("/data/a", "/data/b"))
    assertFalse(layout.isExpanded("/data/a") || layout.isExpanded("/data/b"))

    // Both the read-only preview and interactive sessions start with every card expanded.
    assertTrue(CompactFormLayoutState().isExpanded("/data/anything"))
    assertTrue(CompactFormLayoutState(initiallyExpandAll = true).isExpanded("/data/anything"))
  }

  @Test
  fun layoutState_autoCollapsesOnceWhenQuestionBecomesAnswered() {
    val layout = CompactFormLayoutState(autoCollapseOnAnswer = true)
    val path = "/data/species"
    assertTrue(layout.isExpanded(path))

    // The first status seen never collapses, even if it's already answered (defaults, calculated).
    assertFalse(layout.onStatusChanged("/data/prefilled", CompactQuestionStatus.ANSWERED))
    assertTrue(layout.isExpanded("/data/prefilled"))

    // Pending → pending keeps the card open; pending → answered collapses it.
    assertFalse(layout.onStatusChanged(path, CompactQuestionStatus.PENDING))
    assertFalse(layout.onStatusChanged(path, CompactQuestionStatus.PENDING))
    assertTrue(layout.isExpanded(path))
    assertTrue(layout.onStatusChanged(path, CompactQuestionStatus.ANSWERED))
    assertFalse(layout.isExpanded(path))

    // A manual re-open wins: an unchanged answered status doesn't collapse the card again.
    layout.toggle(path)
    assertTrue(layout.isExpanded(path))
    assertFalse(layout.onStatusChanged(path, CompactQuestionStatus.ANSWERED))
    assertTrue(layout.isExpanded(path))

    // Clearing the answer and re-answering is a new transition, so it collapses once more.
    assertFalse(layout.onStatusChanged(path, CompactQuestionStatus.PENDING))
    assertTrue(layout.isExpanded(path))
    assertTrue(layout.onStatusChanged(path, CompactQuestionStatus.ANSWERED))
    assertFalse(layout.isExpanded(path))

    // Invalid → answered also counts as becoming answered.
    assertFalse(layout.onStatusChanged("/data/height_m", CompactQuestionStatus.INVALID))
    assertTrue(layout.onStatusChanged("/data/height_m", CompactQuestionStatus.ANSWERED))
    assertFalse(layout.isExpanded("/data/height_m"))
  }

  @Test
  fun layoutState_withoutAutoCollapse_keepsAnsweredCardsExpanded() {
    val readOnly = CompactFormLayoutState(autoCollapseOnAnswer = false)
    assertFalse(readOnly.onStatusChanged("/data/species", CompactQuestionStatus.PENDING))
    assertFalse(readOnly.onStatusChanged("/data/species", CompactQuestionStatus.ANSWERED))
    assertTrue(readOnly.isExpanded("/data/species"))
  }
}
