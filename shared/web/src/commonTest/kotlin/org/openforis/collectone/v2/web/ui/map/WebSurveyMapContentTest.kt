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
package org.openforis.collectone.v2.web.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapIds
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.viewmodel.surveyEditorViewModel
import org.openforis.collectone.v2.web.ui.viewmodel.ui

/** The survey map's content after edits only the web console makes (layer styles, imagery). */
class WebSurveyMapContentTest {
  private val state = WebAppState(dataHolder = sampleDataHolder())

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun main_usesThePublishedSurveyEditorLayerStyle() {
    val editor = surveyEditorViewModel(state.activeSurveyEditorDraft)
    val plots = editor.ui.mapLayers.first { it.id == "shade_monitoring_plots" }
    editor.updateDataset(plots.key) {
      it.copy(style = it.style.copy(colorHex = "#AD1457", iconName = "flag"))
    }
    state.saveSurveyEditorDraft(state.activeSurveyId, editor.ui.draft)

    val layer = state.mapLayers.first { it.datasetId == "shade_monitoring_plots" }
    assertEquals(0xFFAD1457, layer.colorHex)
    assertEquals("flag", layer.iconName)
    val layerEntityIds =
      state.visibleMapEntities.filter { it.layerId == layer.id }.map { it.id }.toSet()
    assertTrue(layerEntityIds.isNotEmpty())
    val map = SurveyMapContent.main(state, showNavigation = true)
    map
      .entityFeatures()
      .filter {
        SurveyMapIds.entityIdOf(it.id) in layerEntityIds &&
          it.properties[SurveyMapContent.PROP_KIND] != SurveyMapContent.KIND_VERTEX
      }
      .forEach { assertEquals("#AD1457", it.properties["stroke"]) }
  }

  @Test
  fun imagerySources_fromAllUsersAndSurveyOrgCanBeToggledAndRenderedOnBasemap() {
    val allUsersSource = state.allUsersImagerySources.single()
    val surveyOrgSource = state.activeSurveyOrganizationImagerySources.single()
    assertTrue(allUsersSource.isValidXyzUrl)
    assertTrue(surveyOrgSource.isValidXyzUrl)

    val beforeBasemap =
      SurveyMapContent.main(state, showNavigation = false).content.basemap
        as org.openforis.collectone.v2.map.Basemap.RasterTiles
    val baseLayerCount = beforeBasemap.layers.size

    // Toggle both "All users" and survey-specific Organization imagery layers on.
    state.toggleImagerySource(allUsersSource.id)
    state.toggleImagerySource(surveyOrgSource.id)
    assertTrue(state.isImagerySourceEnabled(allUsersSource.id))
    assertTrue(state.isImagerySourceEnabled(surveyOrgSource.id))

    val afterBasemap =
      SurveyMapContent.main(state, showNavigation = false).content.basemap
        as org.openforis.collectone.v2.map.Basemap.RasterTiles
    assertEquals(baseLayerCount + 2, afterBasemap.layers.size)
    assertEquals(
      listOf(allUsersSource.urlTemplate, surveyOrgSource.urlTemplate),
      afterBasemap.layers.takeLast(2).map { it.urlTemplate },
    )

    // Add a new XYZ tile source to "All users" with offline download permitted toggled off.
    val err =
      state.addOrganizationImagerySource(
        organizationId = state.allUsersOrganization!!.id,
        name = "USGS Topo",
        urlTemplate =
          "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}",
        allowOfflineDownload = false,
      )
    assertEquals(null, err)
    val added = state.allUsersImagerySources.first { it.name == "USGS Topo" }
    assertFalse(added.allowOfflineDownload)
    state.setOrganizationImagerySourceOfflineAllowed(
      organizationId = state.allUsersOrganization!!.id,
      sourceId = added.id,
      allowOfflineDownload = true,
    )
    assertTrue(state.allUsersImagerySources.first { it.id == added.id }.allowOfflineDownload)
  }
}
