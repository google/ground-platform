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
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapIds
import org.openforis.collectone.v2.app.ui.map.mainSurveyMap
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.surveyeditor.surveyEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/** The survey map's content after edits only the web console makes (layer styles, imagery). */
class WebSurveyMapContentTest {
  private val state = WebAppState(dataHolder = sampleDataHolder())

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun main_usesThePublishedSurveyEditorLayerStyle() {
    val editor = surveyEditorViewModel(state.dataHolder.appData.value.editorDraft)
    val plots = editor.ui.mapLayers.first { it.id == "shade_monitoring_plots" }
    editor.updateDataset(plots.key) {
      it.copy(style = it.style.copy(colorHex = "#AD1457", iconName = "flag"))
    }
    state.dataHolder.launch {
      state.dataHolder.surveyEditorRepository.saveDraft(
        state.viewModels.dashboard.uiState.value.activeSurveyId,
        editor.ui.draft,
      )
    }

    val layer =
      state.viewModels.dashboard.uiState.value.mapLayers.first {
        it.datasetId == "shade_monitoring_plots"
      }
    assertEquals(0xFFAD1457, layer.colorHex)
    assertEquals("flag", layer.iconName)
    val layerEntityIds =
      state.viewModels.surveyMap.uiState.value.visibleMapEntities
        .filter { it.layerId == layer.id }
        .map { it.id }
        .toSet()
    assertTrue(layerEntityIds.isNotEmpty())
    val map = state.viewModels.mainSurveyMap(showNavigation = true)
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
    // "All users" also has the preloaded Earth Engine layers; this test covers the XYZ one.
    val allUsersSource =
      state.viewModels.surveyMap.uiState.value.allUsersImagerySources.single {
        it.type == ImagerySourceType.XYZ_TILES
      }
    val surveyOrgSource =
      state.viewModels.surveyMap.uiState.value.activeSurveyOrganizationImagerySources.single()
    assertTrue(allUsersSource.isValidXyzUrl)
    assertTrue(surveyOrgSource.isValidXyzUrl)

    val beforeBasemap =
      state.viewModels.mainSurveyMap(showNavigation = false).content.basemap
        as org.openforis.collectone.v2.map.Basemap.MapboxStyle
    val baseLayerCount = beforeBasemap.overlays.size

    // Toggle both "All users" and survey-specific Organization imagery layers on.
    state.viewModels.surveyMap.toggleImagerySource(allUsersSource.id)
    state.viewModels.surveyMap.toggleImagerySource(surveyOrgSource.id)
    assertTrue(state.viewModels.surveyMap.uiState.value.isImagerySourceEnabled(allUsersSource.id))
    assertTrue(state.viewModels.surveyMap.uiState.value.isImagerySourceEnabled(surveyOrgSource.id))

    val afterBasemap =
      state.viewModels.mainSurveyMap(showNavigation = false).content.basemap
        as org.openforis.collectone.v2.map.Basemap.MapboxStyle
    assertEquals(baseLayerCount + 2, afterBasemap.overlays.size)
    assertEquals(
      listOf(allUsersSource.urlTemplate, surveyOrgSource.urlTemplate),
      afterBasemap.overlays.takeLast(2).map {
        (it as org.openforis.collectone.v2.map.RasterTileLayer).urlTemplate
      },
    )

    // Add a new XYZ tile source to "All users" with offline download permitted toggled off.
    val err =
      state.organization.addImagerySource(
        organizationId = Organization.ALL_USERS_ID,
        name = "USGS Topo",
        urlTemplate =
          "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}",
        allowOfflineDownload = false,
      )
    assertEquals(null, err)
    val added =
      state.viewModels.surveyMap.uiState.value.allUsersImagerySources.first {
        it.name == "USGS Topo"
      }
    assertFalse(added.allowOfflineDownload)
    state.organization.setImagerySourceOfflineAllowed(
      organizationId = Organization.ALL_USERS_ID,
      sourceId = added.id,
      allowOfflineDownload = true,
    )
    assertTrue(
      state.viewModels.surveyMap.uiState.value.allUsersImagerySources
        .first { it.id == added.id }
        .allowOfflineDownload
    )
  }
}
