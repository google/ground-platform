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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

/**
 * Repeat entry statuses ([RepeatEntryStatus]) and the web runner's framing of repeat entries
 * ([buildCompactFormBlocks]), including a repeat nested in another.
 */
class RepeatEntryLayoutTest {

  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml"
            xmlns:jr="http://openrosa.org/javarosa">
      <h:head>
        <h:title>Plots</h:title>
        <model>
          <instance>
            <data id="plots">
              <site_name/>
              <plot><plot_id/><area/><tree><species/></tree></plot>
            </data>
          </instance>
          <bind nodeset="/data/site_name" type="string"/>
          <bind nodeset="/data/plot/plot_id" type="string" required="true()"/>
          <bind nodeset="/data/plot/area" type="decimal" constraint=". &gt; 0"
                jr:constraintMsg="Area must be positive"/>
          <bind nodeset="/data/plot/tree/species" type="string"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/site_name"><label>Site name</label></input>
        <group ref="/data/plot">
          <label>Plot</label>
          <repeat nodeset="/data/plot">
            <input ref="/data/plot/plot_id"><label>Plot ID</label></input>
            <input ref="/data/plot/area"><label>Area</label></input>
            <group ref="/data/plot/tree">
              <label>Tree</label>
              <repeat nodeset="/data/plot/tree">
                <input ref="/data/plot/tree/species"><label>Species</label></input>
              </repeat>
            </group>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))

  private fun FormWizardController.plotSummary(index: Int): RepeatInstanceSummary =
    steps
      .filterIsInstance<FormWizardStep.RepeatHubStep>()
      .first { it.repeatGroup.canonicalPath == "/data/plot" }
      .summaries
      .first { it.repeatIndex == index }

  private fun blocks(controller: FormWizardController) =
    buildCompactFormBlocks(buildCompactFormItems(controller.steps))

  @Test
  fun entryStatus_followsTheEntrysAnswers() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/plot")
    val plot = controller.plotSummary(1).instancePath
    assertEquals(RepeatEntryStatus.NOT_STARTED, controller.plotSummary(1).status)

    controller.updateDouble("$plot/area", 12.5)
    assertEquals(RepeatEntryStatus.IN_PROGRESS, controller.plotSummary(1).status)

    controller.updateDouble("$plot/area", -1.0)
    assertEquals(RepeatEntryStatus.NEEDS_ATTENTION, controller.plotSummary(1).status)

    controller.updateDouble("$plot/area", 3.0)
    controller.updateString("$plot/plot_id", "P-1")
    assertEquals(RepeatEntryStatus.COMPLETE, controller.plotSummary(1).status)
    assertEquals(2, controller.plotSummary(1).answeredCount)

    // Answers inside a nested repeat entry count toward the parent entry's answeredCount too.
    controller.addRepeatInstanceAndOpen("$plot/tree")
    controller.updateString("$plot/tree[1]/species", "Baobab")
    assertEquals(3, controller.plotSummary(1).answeredCount)
  }

  @Test
  fun siblingNavigation_preservesRelativeQuestionStepAcrossEntries() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/plot")
    controller.addRepeatInstanceAndOpen("/data/plot")
    controller.jumpToField("/data/plot[1]/area")

    val ctx1 = assertNotNull(controller.currentStep.repeatContext)
    assertNull(controller.previousRepeatInstancePath(ctx1))
    val next = assertNotNull(controller.nextRepeatInstancePath(ctx1))
    assertEquals("/data/plot[2]", next)

    controller.openSiblingRepeatInstance(ctx1.instancePath, next)
    assertEquals("/data/plot[2]/area", controller.currentStep.stepKey)

    val ctx2 = assertNotNull(controller.currentStep.repeatContext)
    assertNull(controller.nextRepeatInstancePath(ctx2))
    assertEquals("/data/plot[1]", controller.previousRepeatInstancePath(ctx2))
  }

  @Test
  fun compactBlocks_emptyRepeat_isASectionWithItsHubAndNoEntries() {
    val blocks = blocks(controller())

    assertEquals(2, blocks.size)
    val siteName = assertIs<CompactFormBlock.Single>(blocks[0])
    assertEquals("/data/site_name", assertIs<CompactFormItem.Question>(siteName.item).step.stepKey)
    val plots = assertIs<CompactFormBlock.RepeatSection>(blocks[1])
    assertEquals("/data/plot", plots.repeatPath)
    assertEquals("Plot", plots.label)
    assertEquals(emptyList(), plots.entries)
    assertNotNull(plots.hub)
  }

  @Test
  fun compactBlocks_frameEachEntrysQuestionsAndNestedRepeats() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/plot")
    controller.addRepeatInstanceAndOpen("/data/plot")
    val firstPlot = controller.plotSummary(1).instancePath
    controller.addRepeatInstanceAndOpen("$firstPlot/tree")
    controller.updateString("$firstPlot/plot_id", "P-1")

    val plots = assertIs<CompactFormBlock.RepeatSection>(blocks(controller)[1])
    assertEquals(listOf(1, 2), plots.entries.map { it.repeatIndex })
    assertEquals(listOf("Plot #1", "Plot #2"), plots.entries.map { it.title })
    assertEquals(RepeatEntryStatus.COMPLETE, plots.entries[0].summary?.status)

    // The first plot holds its own two questions, then the section of its trees.
    val first = plots.entries[0]
    val questions =
      first.blocks.filterIsInstance<CompactFormBlock.Single>().map {
        assertIs<CompactFormItem.Question>(it.item).step.stepKey
      }
    assertEquals(listOf("$firstPlot/plot_id", "$firstPlot/area"), questions)
    val trees = assertIs<CompactFormBlock.RepeatSection>(first.blocks.last())
    assertEquals("Tree", trees.label)
    assertEquals(1, trees.entries.size)
    val species = assertIs<CompactFormBlock.Single>(trees.entries.single().blocks.single())
    assertEquals(
      "${trees.entries.single().instancePath}/species",
      assertIs<CompactFormItem.Question>(species.item).step.stepKey,
    )

    // The second plot has no trees yet: an empty nested section, still with its hub.
    val secondTrees = assertIs<CompactFormBlock.RepeatSection>(plots.entries[1].blocks.last())
    assertEquals(emptyList(), secondTrees.entries)
    assertNotNull(secondTrees.hub)
  }

  @Test
  fun compactBlocks_withoutAHub_stillFrameEntriesFromTheirContext() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/plot")
    val items = buildCompactFormItems(controller.steps).filter { it !is CompactFormItem.RepeatHub }

    val plots = assertIs<CompactFormBlock.RepeatSection>(buildCompactFormBlocks(items)[1])
    assertNull(plots.hub)
    assertEquals("Plot", plots.label)
    assertEquals("Plot #1", plots.entries.single().title)
    assertNull(plots.entries.single().summary)
    assertEquals(RepeatEntryStatus.NOT_STARTED, plots.entries.single().status)

    controller.updateString("/data/plot[1]/plot_id", "P-1")
    val updatedItems =
      buildCompactFormItems(controller.steps).filter { it !is CompactFormItem.RepeatHub }
    val updatedPlots =
      assertIs<CompactFormBlock.RepeatSection>(buildCompactFormBlocks(updatedItems)[1])
    assertEquals(RepeatEntryStatus.COMPLETE, updatedPlots.entries.single().status)
  }

  @Test
  fun forgetRepeatEntries_clearsOnlyThatRepeatsCardState() {
    val layout = CompactFormLayoutState(initiallyExpandAll = true)
    layout.setExpanded("/data/plot[2]/area", false)
    layout.skip("/data/plot[1]/area")
    layout.setExpanded("/data/site_name", false)

    layout.forgetRepeatEntries("/data/plot")

    assertEquals(true, layout.isExpanded("/data/plot[2]/area"))
    assertEquals(false, layout.isSkipped("/data/plot[1]/area"))
    assertEquals(false, layout.isExpanded("/data/site_name"))
  }

  @Test
  fun repeatInstancePrefixes_listsEveryIndexedAncestor() {
    assertEquals(
      listOf("/data/plot[1]", "/data/plot[1]/tree[2]"),
      repeatInstancePrefixes("/data/plot[1]/tree[2]/species"),
    )
    assertEquals(listOf("/data/plot[3]"), repeatInstancePrefixes("/data/plot[3]/tree"))
    assertEquals(emptyList(), repeatInstancePrefixes("/data/site_name"))
  }

  @Test
  fun breadcrumbsWithinEntry_dropsTheEntryAndWhatIsAboveIt() {
    val context =
      RepeatStepContext(
        repeatGroupPath = "/data/plot",
        repeatGroupLabel = "Plot",
        instancePath = "/data/plot[1]",
        repeatIndex = 1,
        totalInstances = 1,
        instanceLabel = "Plot #1",
        canAddInstance = true,
        canRemoveInstance = true,
      )

    assertEquals(
      listOf("Soil"),
      breadcrumbsWithinEntry(listOf("Survey", "Plot #1", "Soil"), context),
    )
    assertEquals(emptyList(), breadcrumbsWithinEntry(listOf("Plot #1"), context))
    assertEquals(listOf("Site"), breadcrumbsWithinEntry(listOf("Site"), null))
  }
}
