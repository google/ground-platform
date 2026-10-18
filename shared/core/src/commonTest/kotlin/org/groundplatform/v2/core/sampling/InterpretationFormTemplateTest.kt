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
package org.groundplatform.v2.core.sampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.engine.FormEnvironment
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.xpath.InMemorySecondaryInstanceProvider

/**
 * Spike for decision D1: an interpretation form creates one repeat instance per sample point stored
 * in the plot entity's `samples` property and pre-fills each instance's geopoint, using only
 * standard ODK XForms constructs (`jr:count`, `instance()`, `position(..)`, XPath 1.0
 * `string-length()` / `translate()` and ODK `selected-at()`), so the same form works in ODK Collect
 * and Enketo.
 *
 * Why not `selected-at(samples, i)` directly: `selected-at` splits on spaces (ODK XForms spec,
 * "Select functions"), while geotrace points are separated by `;` and each point itself contains
 * spaces. `translate(samples, ';', ' ')` (XPath 1.0 section 4.2) turns the geotrace into a flat
 * space-separated list in which point *i* (1-based) starts at token `4 * (i - 1)`. This relies on
 * the `samples` encoding written by [SampleEncoding]: exactly four numbers per point and no spaces
 * around `;`. The point count is the number of `;` plus one.
 */
class InterpretationFormTemplateTest {
  private val plotSamples =
    listOf(
      GeoCoord(-1.0001234, 37.0004321),
      GeoCoord(-1.0002, 37.0005),
      GeoCoord(-1.0003, 37.0006),
      GeoCoord(-0.9999999, 36.9999999),
    )

  private fun plotsInstance(sampleIds: String) =
    InMemorySecondaryInstanceProvider.fromStringTables(
      mapOf(
        "plots" to
          listOf(
            mapOf(
              "name" to "a3c5e2c0-0000-4000-8000-000000000001",
              "label" to "P000001",
              "plot_id" to "P000001",
              "samples" to SampleEncoding.encodeSamples(plotSamples),
              "sample_ids" to sampleIds,
            ),
            mapOf(
              "name" to "a3c5e2c0-0000-4000-8000-000000000002",
              "label" to "P000002",
              "plot_id" to "P000002",
              "samples" to "",
              "sample_ids" to "",
            ),
          )
      )
    )

  private fun repeatGroups(
    components: List<ComponentState>
  ): List<ComponentState.RepeatGroupState> = components.flatMap { c ->
    when (c) {
      is ComponentState.RepeatGroupState ->
        listOf(c) + c.instances.flatMap { repeatGroups(it.children) }
      is ComponentState.GroupState -> repeatGroups(c.children)
      else -> emptyList()
    }
  }

  private fun session(sampleIds: String): FormSession {
    val formDef = XFormsXmlSerializer.deserializeFormDef(TEMPLATE)
    return FormSession(
      formDef,
      environment = FormEnvironment(secondaryInstanceProvider = plotsInstance(sampleIds)),
    )
  }

  @Test
  fun repeatCountAndGeopointsComeFromPlotSamples() {
    val session = session(sampleIds = "")
    // No plot selected yet: no sample instances.
    assertEquals(0, repeatGroups(session.state.rootComponents).single().instances.size)

    session.updateString("plot", "a3c5e2c0-0000-4000-8000-000000000001")
    val repeat = repeatGroups(session.state.rootComponents).single()
    assertEquals(4, repeat.targetCount)
    assertEquals(4, repeat.instances.size)
    assertTrue(!repeat.canAddInstance && !repeat.canRemoveInstance)
    for ((i, expected) in plotSamples.withIndex()) {
      val n = i + 1
      val point =
        session.state
          .findFieldState("/data/sample[$n]/location")
          ?.value
          ?.scalar_value
          ?.geopoint_value
      assertNotNull(point, "sample $n has no location")
      assertEquals(expected.lat, point.latitude, 1e-9)
      assertEquals(expected.lng, point.longitude, 1e-9)
      assertEquals(
        n.toString(),
        session.state
          .findFieldState("/data/sample[$n]/sample_id")
          ?.value
          ?.scalar_value
          ?.string_value,
      )
    }

    // Switching to a plot without samples removes the instances.
    session.updateString("plot", "a3c5e2c0-0000-4000-8000-000000000002")
    assertEquals(0, repeatGroups(session.state.rootComponents).single().instances.size)
  }

  @Test
  fun sampleIdsPropertyKeysTheInstances() {
    val session = session(sampleIds = "NW NE SW SE")
    session.updateString("plot", "a3c5e2c0-0000-4000-8000-000000000001")
    val ids =
      (1..4).map {
        session.state
          .findFieldState("/data/sample[$it]/sample_id")
          ?.value
          ?.scalar_value
          ?.string_value
      }
    assertEquals(listOf("NW", "NE", "SW", "SE"), ids)
  }

  @Test
  fun generatedPlotSamplesFeedTheForm() {
    // End to end: engine output → `samples` property → repeat instances.
    val area =
      SamplingArea.fromSurveyArea(
        listOf(
          listOf(
            GeoCoord(-1.0, 37.0),
            GeoCoord(-1.0, 37.1),
            GeoCoord(-0.9, 37.1),
            GeoCoord(-0.9, 37.0),
          )
        )
      )
    val plot =
      (SamplingEngine.Default.generate(
          area,
          SampleDesign(PlotLayout.SimpleRandom(1), subPlot = SubPlotLayout.Grid(5, 10.0)),
        ) as SamplingResult.Success)
        .plots
        .single()
    val provider =
      InMemorySecondaryInstanceProvider.fromStringTables(
        mapOf(
          "plots" to
            listOf(
              mapOf(
                "name" to plot.plotId,
                "samples" to SampleEncoding.encodeSamples(plot.samples),
                "sample_ids" to
                  SampleEncoding.encodeSampleIds(
                    SampleEncoding.defaultSampleIds(plot.samples.size)
                  ),
              )
            )
        )
      )
    val session =
      FormSession(
        XFormsXmlSerializer.deserializeFormDef(TEMPLATE),
        environment = FormEnvironment(secondaryInstanceProvider = provider),
      )
    session.updateString("plot", plot.plotId)
    assertEquals(25, repeatGroups(session.state.rootComponents).single().instances.size)
    val last =
      session.state.findFieldState("/data/sample[25]/location")?.value?.scalar_value?.geopoint_value
    assertNotNull(last)
    assertEquals(plot.samples[24].lat, last.latitude, 1e-9)
    assertEquals(plot.samples[24].lng, last.longitude, 1e-9)
  }

  @Test
  fun templateRoundTripsThroughProto() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(TEMPLATE)
    val xml = XFormsXmlSerializer.serialize(formDef)
    val again = XFormsXmlSerializer.deserializeFormDef(xml)
    assertEquals(formDef, again)
    assertEquals(xml, XFormsXmlSerializer.serialize(again))
    // The constructs the template depends on survive the trip.
    for (needle in
      listOf(
        "jr:count=\"/data/sample_count\"",
        "jr:noAddRemove=\"true()\"",
        "instance('plots')/root/item[name=/data/plot]/samples",
        "selected-at(translate(/data/samples, ';', ' '), 4 * (position(..) - 1))",
        "id=\"plots\"",
      )) {
      assertTrue(
        xml.contains(needle) || xml.contains(needle.replace("'", "&apos;")),
        "missing $needle in\n$xml",
      )
    }
  }

  @Test
  fun sampleEncodingIsStableAndReversible() {
    assertEquals(
      "-1.0001234 37.0004321 0 0;-1.0002 37.0005 0 0;-1.0003 37.0006 0 0;-0.9999999 36.9999999 0 0",
      SampleEncoding.encodeSamples(plotSamples),
    )
    assertEquals(
      "0 0 0 0;45 -7.5 0 0",
      SampleEncoding.encodeSamples(listOf(GeoCoord(-0.00000001, 0.0), GeoCoord(45.0, -7.5))),
    )
    assertEquals(
      plotSamples,
      SampleEncoding.decodeSamples(SampleEncoding.encodeSamples(plotSamples)),
    )
    assertEquals(
      listOf(GeoCoord(1.0, 2.0), GeoCoord(3.0, 4.0)),
      SampleEncoding.decodeSamples("1 2; 3 4 100 5"),
    )
    assertEquals(null, SampleEncoding.decodeSamples("1 2;oops"))
    assertEquals(listOf("a", "b"), SampleEncoding.decodeSampleIds(" a  b "))
  }

  companion object {
    /**
     * Recommended interpretation form template (XLSForm equivalent in the entity dataset docs). The
     * `plot` field holds the selected plot entity's `name`; Ground pre-fills it when the form is
     * launched from a plot, and in ODK it is a `select_one_from_file plots.csv`.
     */
    val TEMPLATE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms">
        <h:head>
          <h:title>Plot interpretation</h:title>
          <model odk:xforms-version="1.0.0">
            <instance>
              <data id="plot_interpretation" version="1">
                <plot/>
                <samples/>
                <sample_ids/>
                <sample_count/>
                <sample jr:template="">
                  <sample_id/>
                  <location/>
                  <land_cover/>
                </sample>
                <meta>
                  <instanceID/>
                </meta>
              </data>
            </instance>
            <instance id="plots" src="jr://file-csv/plots.csv"/>
            <bind nodeset="/data/plot" type="string" required="true()"/>
            <bind nodeset="/data/samples" type="string" calculate="instance('plots')/root/item[name=/data/plot]/samples"/>
            <bind nodeset="/data/sample_ids" type="string" calculate="instance('plots')/root/item[name=/data/plot]/sample_ids"/>
            <bind nodeset="/data/sample_count" type="int" calculate="if(string-length(/data/samples) = 0, 0, string-length(/data/samples) - string-length(translate(/data/samples, ';', '')) + 1)"/>
            <bind nodeset="/data/sample/sample_id" type="string" calculate="if(string-length(/data/sample_ids) = 0, position(..), selected-at(/data/sample_ids, position(..) - 1))"/>
            <bind nodeset="/data/sample/location" type="geopoint" calculate="concat(selected-at(translate(/data/samples, ';', ' '), 4 * (position(..) - 1)), ' ', selected-at(translate(/data/samples, ';', ' '), 4 * (position(..) - 1) + 1), ' 0 0')"/>
            <bind nodeset="/data/sample/land_cover" type="string" required="true()"/>
            <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/plot">
            <label>Plot</label>
          </input>
          <group ref="/data/sample">
            <label>Sample</label>
            <repeat nodeset="/data/sample" jr:count="/data/sample_count" jr:noAddRemove="true()">
              <input ref="/data/sample/location">
                <label>Sample location</label>
              </input>
              <select1 ref="/data/sample/land_cover">
                <label>Land cover</label>
                <item>
                  <label>Tree</label>
                  <value>tree</value>
                </item>
                <item>
                  <label>Shrub</label>
                  <value>shrub</value>
                </item>
                <item>
                  <label>Bare</label>
                  <value>bare</value>
                </item>
              </select1>
            </repeat>
          </group>
        </h:body>
      </h:html>
      """
        .trimIndent()
  }
}
