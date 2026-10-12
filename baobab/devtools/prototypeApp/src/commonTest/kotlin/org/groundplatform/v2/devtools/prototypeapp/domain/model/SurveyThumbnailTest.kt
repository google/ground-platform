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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea

class SurveyThumbnailTest {
  @Test
  fun seededSurveys_haveSatelliteThumbnailsWithAreaOfInterestAndMapFeatures() {
    val surveys = PrototypeFakeSurveysData.defaultSampleSurveys()
    assertTrue(surveys.isNotEmpty())
    for (survey in surveys) {
      val thumbnail = survey.thumbnail
      assertTrue(thumbnail.hasContent, "Expected thumbnail content for ${survey.id}")
      assertTrue(thumbnail.areaParts.isNotEmpty(), "Expected AOI polygon for ${survey.id}")
      assertTrue(
        thumbnail.areaParts.all { it.size == 4 },
        "Expected 4-vertex rectangular AOI square for ${survey.id}",
      )
      assertTrue(thumbnail.features.isNotEmpty(), "Expected map features for ${survey.id}")
      assertTrue(
        thumbnail.placeLabels.isNotEmpty(),
        "Expected Mapbox place labels for ${survey.id}",
      )
      assertTrue(
        thumbnail.labelTileUrlTemplate.isNotBlank(),
        "Expected reference label tile URL template for ${survey.id}",
      )
      assertTrue(
        thumbnail.bounds.east > thumbnail.bounds.west &&
          thumbnail.bounds.north > thumbnail.bounds.south,
        "Expected non-empty bounds for ${survey.id}",
      )
    }

    val kenya = surveys.first { it.id == "survey-kenya-coffee" }
    assertEquals("Othaya Sub-County, Nyeri", kenya.thumbnail.areaName)
    assertTrue(kenya.thumbnail.placeLabels.any { it.name.contains("Othaya", ignoreCase = true) })
    assertEquals(5, kenya.thumbnail.features.size)
    assertTrue(kenya.thumbnail.features.any { it.kind == EntityShapeKind.POLYGON })
    assertTrue(kenya.thumbnail.features.any { it.kind == EntityShapeKind.LINE })
    assertTrue(kenya.thumbnail.features.any { it.kind == EntityShapeKind.POINT })
  }

  @Test
  fun saveDraft_regeneratesAndStoresSurveyThumbnailWithUpdatedAreaAndFeatures() {
    val store = seededStore()
    val surveyRepo = SurveyRepositoryImpl(store)
    val editorRepo = SurveyEditorRepositoryImpl(store)
    val surveyId = "survey-kenya-coffee"

    val draft = runNow { editorRepo.getDraft(surveyId) }
    val customArea =
      SurveyArea(
        name = "Custom Conservation Block",
        center = LatLng(-0.4200, 36.9500),
        zoom = 14.0,
        parts =
          listOf(
            listOf(
              LatLng(-0.4150, 36.9450),
              LatLng(-0.4150, 36.9550),
              LatLng(-0.4250, 36.9550),
              LatLng(-0.4250, 36.9450),
            )
          ),
      )
    val mapDataset = draft.datasets.first { it.kind == DatasetKind.MAP_LAYER }
    val newPointLayer = draft.datasets.first { it.id == "washing_stations" }
    val updatedDraft =
      draft.copy(
        details = draft.details.copy(surveyArea = customArea),
        datasets =
          draft.datasets.map { d ->
            if (d.key == newPointLayer.key) {
              d.copy(
                rows =
                  d.rows +
                    EntityRow(
                      key = "station-new",
                      values = mapOf(d.keyProperty to "station-new", d.labelProperty to "New Mill"),
                      geometry = listOf(LatLng(-0.4180, 36.9480)),
                    )
              )
            } else {
              d
            }
          },
      )

    runNow { editorRepo.saveDraft(surveyId, updatedDraft) }

    val updatedSurvey = runNow { surveyRepo.getSurveys() }.first { it.id == surveyId }
    assertEquals("Custom Conservation Block", updatedSurvey.thumbnail.areaName)
    assertEquals(1, updatedSurvey.thumbnail.areaParts.size)
    assertEquals(4, updatedSurvey.thumbnail.areaParts.first().size)
    assertEquals(6, updatedSurvey.thumbnail.features.size)
    assertTrue(
      updatedSurvey.thumbnail.features.any {
        it.id == "station-new" && it.kind == EntityShapeKind.POINT
      }
    )
    assertTrue(updatedSurvey.thumbnail.features.any { it.id == mapDataset.rows.first().key })
  }

  @Test
  fun coveringTiles_computesValidWebMercatorTileGridAndUrls() {
    val kenya =
      PrototypeFakeSurveysData.defaultSampleSurveys().first { it.id == "survey-kenya-coffee" }
    val tiles =
      WebMercatorThumbnailProjection.coveringTiles(
        bounds = kenya.thumbnail.bounds,
        fallbackZoom = kenya.thumbnail.zoom,
      )
    assertTrue(tiles.isNotEmpty())
    assertTrue(tiles.size <= 9, "Thumbnail should only need a compact tile grid")
    val firstUrl = tiles.first().resolveUrl(kenya.thumbnail.tileUrlTemplate)
    assertTrue(firstUrl.startsWith("https://server.arcgisonline.com/ArcGIS/rest/services/"))
    assertTrue(!firstUrl.contains("{z}") && !firstUrl.contains("{x}") && !firstUrl.contains("{y}"))
  }
}
