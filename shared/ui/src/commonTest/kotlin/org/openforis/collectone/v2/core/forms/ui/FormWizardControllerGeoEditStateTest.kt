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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

/** [FormWizardController.geoEditState] keeps one map state per geo question. */
class FormWizardControllerGeoEditStateTest {

  private val treeFormXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Trees</h:title>
        <model>
          <instance>
            <data id="trees" version="1"><plot/><tree><location/></tree></data>
          </instance>
          <bind nodeset="/data/plot" type="geoshape"/>
          <bind nodeset="/data/tree/location" type="geopoint"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/plot"><label>Plot</label></input>
        <group ref="/data/tree">
          <label>Tree</label>
          <repeat nodeset="/data/tree">
            <input ref="/data/tree/location"><label>Location</label></input>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(treeFormXml))

  @Test
  fun geoEditState_returnsTheSameStateForTheSamePath() {
    val controller = controller()

    assertSame(controller.geoEditState("/data/plot"), controller.geoEditState("/data/plot"))
  }

  @Test
  fun geoEditState_keepsSeparateStatesPerPath() {
    val controller = controller()

    assertNotSame(
      controller.geoEditState("/data/plot"),
      controller.geoEditState("/data/tree[1]/location"),
    )
  }

  @Test
  fun geoEditState_survivesNavigatingAwayAndBack() {
    val controller = controller()
    val plotState = controller.geoEditState("/data/plot")
    plotState.zoomBy(0.75f)

    controller.nextStep(enforceValidation = false)
    controller.previousStep()

    assertSame(plotState, controller.geoEditState("/data/plot"))
  }

  @Test
  fun removeRepeatInstance_forgetsTheMapStateOfShiftedEntries() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/tree")
    controller.addRepeatInstanceAndOpen("/data/tree")
    val firstTreeState = controller.geoEditState("/data/tree[1]/location")

    controller.removeRepeatInstance("/data/tree", 1)

    assertNotSame(firstTreeState, controller.geoEditState("/data/tree[1]/location"))
  }

  @Test
  fun removeRepeatInstance_keepsTheMapStateOutsideTheRepeat() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/tree")
    val plotState = controller.geoEditState("/data/plot")

    controller.removeRepeatInstance("/data/tree", 1)

    assertSame(plotState, controller.geoEditState("/data/plot"))
  }
}
