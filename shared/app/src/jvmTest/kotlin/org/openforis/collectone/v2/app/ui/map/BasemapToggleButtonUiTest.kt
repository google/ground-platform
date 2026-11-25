/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.core.forms.ui.AppTheme

/** The one-tap basemap toggle shown on the maps of a data collection form. */
@OptIn(ExperimentalTestApi::class)
class BasemapToggleButtonUiTest {

  @Test
  fun tappingTheButton_switchesBetweenSatelliteAndTerrain() = runComposeUiTest {
    var basemap by mutableStateOf(BasemapType.SATELLITE)
    setContent {
      AppTheme {
        BasemapToggleButton(
          selectedBasemapType = basemap,
          onToggle = {
            basemap =
              if (basemap == BasemapType.SATELLITE) BasemapType.NORMAL else BasemapType.SATELLITE
          },
        )
      }
    }

    onNodeWithContentDescription("Basemap").assert(showsBasemap("Satellite")).performClick()
    waitForIdle()
    assertEquals(BasemapType.NORMAL, basemap)

    onNodeWithContentDescription("Basemap").assert(showsBasemap("Terrain")).performClick()
    waitForIdle()
    assertEquals(BasemapType.SATELLITE, basemap)
  }

  private fun showsBasemap(label: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)
}
