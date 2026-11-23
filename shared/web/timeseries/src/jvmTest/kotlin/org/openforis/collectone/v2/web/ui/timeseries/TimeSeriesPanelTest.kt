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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import openforis.collectone.v1.survey.TimeSeriesSource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_PANEL_BADGE_TAG
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.client.timeseries.PlotImageryRepository

/**
 * Renders the "Time series & imagery" panel from the seed data. With `TIME_SERIES_PANEL_PNG` set to
 * a `.png` path, it also writes screenshots of the panel and its menus next to it.
 */
@OptIn(ExperimentalTestApi::class)
class TimeSeriesPanelTest {
  private val plot =
    GeospatialEntityItem(
      id = "plot-104",
      label = "Plot 104 · Pará North",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots-layer",
      geoId = "",
      geometry =
        Geometry.Polygon(
          listOf(
            listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520))
          )
        ),
      areaHectares = 4.82,
      perimeterMeters = 880,
      colorHex = 0L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  private val screenshotPath: String? = System.getenv("TIME_SERIES_PANEL_PNG")

  private fun ComposeUiTest.showPanel(): TimeSeriesViewModel {
    val viewModel =
      TimeSeriesViewModel(repository = PlotImageryRepository(SeedUserDataService().client()))
    viewModel.follow("survey-1", plot, visible = true)
    setContent {
      AppTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
          Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            val uiState by viewModel.uiState.collectAsState()
            uiState.pinnedScene?.let { PinnedSceneBanner(it, viewModel) }
            TimeSeriesPanel(uiState = uiState, actions = viewModel)
          }
        }
      }
    }
    waitForIdle()
    return viewModel
  }

  /** Writes the open menu (the newest root) as `<screenshot>_<name>.png`. */
  private fun ComposeUiTest.screenshotMenu(name: String) {
    val path = screenshotPath ?: return
    val roots = onAllNodes(isRoot())
    val menu = roots[roots.fetchSemanticsNodes().size - 1]
    menu.captureToImage().writePng(path.removeSuffix(".png") + "_$name.png")
  }

  private fun ImageBitmap.writePng(path: String) {
    val png = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
    File(path).writeBytes(png!!.bytes)
  }

  @Test
  fun panel_chartsTheSelectedFeature_andPinsAClickedObservation() =
    runDesktopComposeUiTest(width = 1280, height = 420) {
      val viewModel = showPanel()

      onNodeWithText("Plot 104 · Pará North").assertExists()
      onNodeWithTag(EARTH_ENGINE_PANEL_BADGE_TAG).assertExists()
      onNodeWithText("Powered by Google Earth Engine").assertExists()
      onNodeWithContentDescription("Imagery source: Landsat").assertExists()
      val count = viewModel.uiState.value.loadedSeries!!.validCount("NDVI")
      val chart = onNodeWithContentDescription("NDVI time series chart with $count observations")
      chart.assertExists()

      // Click roughly at the August 2023 clearing (about three quarters of the way along).
      chart.performTouchInput { click(percentOffset(0.76f, 0.8f)) }
      waitForIdle()
      val pinned = assertNotNull(viewModel.uiState.value.pinnedScene)
      onNodeWithText("Landsat 8 · ${pinned.observation.date}").assertExists()

      // Switching the band from its menu keeps the selection; the banner offers the other
      // stretches.
      onNodeWithContentDescription("Band: NDVI").performClick()
      onNodeWithText("Forest canopy degradation").assertExists()
      onNodeWithText("NDFI").performClick()
      assertEquals("NDFI", viewModel.uiState.value.band)
      waitForIdle()
      // Focus returns to the menu's button.
      onNodeWithContentDescription("Band: NDFI").assertIsFocused()
      onNodeWithText("True color").assertExists()

      screenshotPath?.let { path ->
        // Hover the selected observation rather than wherever the menu item was.
        val ndfiCount = viewModel.uiState.value.loadedSeries!!.validCount("NDFI")
        onNodeWithContentDescription("NDFI time series chart with $ndfiCount observations")
          .performMouseInput { moveTo(percentOffset(0.76f, 0.3f)) }
        waitForIdle()
        onRoot().captureToImage().writePng(path)
      }
    }

  @Test
  fun menus_explainSourcesAndBands_andSwitchToRadar() =
    runDesktopComposeUiTest(width = 1280, height = 840) {
      val viewModel = showPanel()

      // Landsat's bands, in two sections, each with its explanation.
      onNodeWithContentDescription("Band: NDVI").performClick()
      onNodeWithText("Indices").assertExists()
      onNodeWithText("Reflectance").assertExists()
      onNodeWithText("Near infrared, reflected by leaves").assertExists()
      screenshotMenu("band_menu")
      onNodeWithText("NBR").performClick()
      assertEquals("NBR", viewModel.uiState.value.band)

      onNodeWithContentDescription("Imagery source: Landsat").performClick()
      onNodeWithText("Optical · 10 m · every 5 days").assertExists()
      onNodeWithText("Radar · 10 m · sees through clouds").assertExists()
      screenshotMenu("source_menu")
      onNodeWithText("Sentinel-1").performClick()
      waitForIdle()
      assertEquals(TimeSeriesSource.SENTINEL_1, viewModel.uiState.value.source)
      // Radar has no NBR, so the band falls back to Sentinel-1's first.
      assertEquals("VV", viewModel.uiState.value.band)

      onNodeWithContentDescription("Band: VV").performClick()
      onNodeWithText("Backscatter").assertExists()
      screenshotMenu("radar_band_menu")
      onNodeWithText("RFDI").performClick()
      assertEquals("RFDI", viewModel.uiState.value.band)
      onNodeWithContentDescription("Band: RFDI").assertExists()
    }

  @Test
  fun panel_showsEarthEngineBadgeBeforeAnyFeatureIsSelected() =
    runDesktopComposeUiTest(width = 1280, height = 320) {
      val viewModel =
        TimeSeriesViewModel(repository = PlotImageryRepository(SeedUserDataService().client()))
      setContent {
        AppTheme {
          Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            val uiState by viewModel.uiState.collectAsState()
            TimeSeriesPanel(uiState = uiState, actions = viewModel)
          }
        }
      }
      waitForIdle()

      onNodeWithText("Select a point or polygon on the map to see its satellite time series.")
        .assertExists()
      onNodeWithTag(EARTH_ENGINE_PANEL_BADGE_TAG).assertExists()
      onNodeWithText("Powered by Google Earth Engine").assertExists()
    }
}
