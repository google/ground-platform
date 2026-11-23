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
package org.openforis.collectone.v2.core.geo.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.core.geo.GeometryProtos

class KmlBalloonWriterStatusTest {
  private val sampleForm =
    KmlBalloonWriter.EmbeddedForm(
      id = "form-plot",
      title = "Plot Assessment",
      version = "1",
      formDef = FormDef(form_id = "form-plot", title = "Plot Assessment"),
    )

  private fun placemark(
    id: String,
    title: String,
    status: String = "",
    markerSymbol: String = "",
  ) =
    KmlBalloonWriter.PlacemarkSpec(
      id = id,
      title = title,
      geoId = id,
      status = status,
      markerSymbol = markerSymbol,
      geometry = GeometryProtos.point(latitude = -0.42, longitude = 36.95),
    )

  @Test
  fun unsetStatusPlacemarkWithAttachedForms_omitsStatusPrefixAndUsesBaseLayerStyle() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s-unset",
        surveyTitle = "Unset Status Survey",
        documentName = "Unset Status Survey",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-plots",
              datasetId = "plots",
              name = "Plots",
              forms = listOf(sampleForm),
              placemarks = listOf(placemark(id = "p1", title = "Plot 01")),
            )
          ),
      )

    assertTrue("<name>Plot 01</name>" in kml, kml)
    assertFalse("<name>○ Plot 01</name>" in kml, kml)
    assertTrue("<styleUrl>#collectone-layer-1</styleUrl>" in kml, kml)
    assertFalse("<Style id=\"collectone-layer-1-needs-review\">" in kml, kml)
    assertFalse("<Style id=\"collectone-layer-1-flagged\">" in kml, kml)
  }

  @Test
  fun statusSymbolsAndStyles_matchMainUiAcrossAllStatusMarkerSymbols() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s-all",
        surveyTitle = "All Statuses",
        documentName = "All Statuses",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-plots",
              datasetId = "plots",
              name = "Plots",
              forms = listOf(sampleForm),
              placemarks =
                listOf(
                  placemark(id = "p-none", title = "Plot None"),
                  placemark(
                    id = "p-open",
                    title = "Plot Open",
                    status = "Pending",
                    markerSymbol = "○",
                  ),
                  placemark(
                    id = "p-half",
                    title = "Plot Half",
                    status = "In progress",
                    markerSymbol = "◐",
                  ),
                  placemark(
                    id = "p-check",
                    title = "Plot Check",
                    status = "Completed",
                    markerSymbol = "✓",
                  ),
                  placemark(id = "p-surveyed", title = "Plot Surveyed", status = "Surveyed"),
                  placemark(
                    id = "p-alert",
                    title = "Plot Alert",
                    status = "Needs review",
                    markerSymbol = "!",
                  ),
                  placemark(
                    id = "p-cross",
                    title = "Plot Cross",
                    status = "Flagged",
                    markerSymbol = "✕",
                  ),
                  placemark(id = "p-custom", title = "Plot Custom", markerSymbol = "★"),
                ),
            )
          ),
      )

    assertTrue("<Style id=\"collectone-layer-1-needs-review\">" in kml, kml)
    assertTrue("<Style id=\"collectone-layer-1-flagged\">" in kml, kml)
    assertPlacemark(kml, "p-none", "Plot None", "#collectone-layer-1")
    assertPlacemark(kml, "p-open", "○ Plot Open", "#collectone-layer-1")
    assertPlacemark(kml, "p-half", "◐ Plot Half", "#collectone-layer-1-in-progress")
    assertPlacemark(kml, "p-check", "✓ Plot Check", "#collectone-layer-1-completed")
    assertPlacemark(kml, "p-surveyed", "✓ Plot Surveyed", "#collectone-layer-1-completed")
    assertPlacemark(kml, "p-alert", "! Plot Alert", "#collectone-layer-1-needs-review")
    assertPlacemark(kml, "p-cross", "✕ Plot Cross", "#collectone-layer-1-flagged")
    assertPlacemark(kml, "p-custom", "★ Plot Custom", "#collectone-layer-1")
  }

  @Test
  fun resolvePlacemarkStatusSymbol_andStripLeadingPrefix_coverCanonicalAndCustomValues() {
    assertEquals("", KmlBalloonWriter.resolvePlacemarkStatusSymbol("", ""))
    assertEquals("○", KmlBalloonWriter.resolvePlacemarkStatusSymbol("Pending", ""))
    assertEquals("◐", KmlBalloonWriter.resolvePlacemarkStatusSymbol("in_progress", ""))
    assertEquals("✓", KmlBalloonWriter.resolvePlacemarkStatusSymbol("Surveyed", ""))
    assertEquals("✓", KmlBalloonWriter.resolvePlacemarkStatusSymbol("Completed", ""))
    assertEquals("!", KmlBalloonWriter.resolvePlacemarkStatusSymbol("Needs review", ""))
    assertEquals("✕", KmlBalloonWriter.resolvePlacemarkStatusSymbol("Flagged", ""))
    assertEquals("★", KmlBalloonWriter.resolvePlacemarkStatusSymbol("", "★"))
    assertEquals("Plot 1", KmlBalloonWriter.stripLeadingStatusSymbol("○ Plot 1"))
    assertEquals("Plot 1", KmlBalloonWriter.stripLeadingStatusSymbol("◐ Plot 1"))
    assertEquals("Plot 1", KmlBalloonWriter.stripLeadingStatusSymbol("✓ Plot 1"))
    assertEquals("Plot 1", KmlBalloonWriter.stripLeadingStatusSymbol("! Plot 1"))
    assertEquals("Plot 1", KmlBalloonWriter.stripLeadingStatusSymbol("✕ Plot 1"))
  }

  private fun assertPlacemark(
    kml: String,
    id: String,
    expectedName: String,
    expectedStyle: String,
  ) {
    val block =
      Regex("""<Placemark id="$id">[\s\S]*?</Placemark>""").find(kml)?.value
        ?: error("Placemark $id not found in:\n$kml")
    assertTrue(
      "<name>$expectedName</name>" in block,
      "Expected <name>$expectedName</name> in:\n$block",
    )
    assertTrue(
      "<styleUrl>$expectedStyle</styleUrl>" in block,
      "Expected <styleUrl>$expectedStyle</styleUrl> in:\n$block",
    )
  }
}
