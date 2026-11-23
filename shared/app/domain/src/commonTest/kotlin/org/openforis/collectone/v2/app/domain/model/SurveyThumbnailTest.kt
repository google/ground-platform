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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

class SurveyThumbnailTest {
  @Test
  fun seededSurveys_frameDistinctSelectedAoisAndExcludeCrossContinentEntities() {
    val surveys = SampleSurveysData.defaultSampleSurveys()
    assertEquals(7, surveys.size)

    val expectedCentersById =
      mapOf(
        // Arusha, Tanzania
        "00000036-1a1c-5c51-997f-28b67d813aa3" to LatLng(-3.3800, 36.6800),
        // Pará, Brazil
        "00000034-c088-5ab9-8b98-e6bccb3dca71" to LatLng(-3.4600, -62.2100),
        // Ashanti Region, Ghana
        "00000031-ffc6-5c8e-934c-d14ec07533ad" to LatLng(6.6900, -1.6200),
        // Kakamega, Kenya
        "00000032-8d62-5717-8074-f4917e0cd76d" to LatLng(0.2800, 34.7500),
        // Nyeri County, Kenya
        "00000033-6b50-51cd-ac94-baea2ea06d65" to LatLng(-0.4190, 36.9500),
        // Central Kalimantan, Indonesia (Google Earth sample-plot interpretation)
        "00000037-7e4a-5b2c-9d18-3f8a61c4e902" to LatLng(-2.2136, 113.9213),
        // Mekong Delta, Vietnam
        "00000035-caf4-571e-b907-f506f92666ab" to LatLng(9.8200, 106.3400),
      )

    for (survey in surveys) {
      val thumbnail = survey.thumbnail
      val expectedCenter = expectedCentersById.getValue(survey.id)
      assertTrue(thumbnail.hasContent, "Expected thumbnail content for ${survey.id}")
      assertTrue(thumbnail.areaName.isNotBlank(), "Expected areaName for ${survey.id}")
      assertEquals(1, thumbnail.areaParts.size, "Expected explicit AOI ring for ${survey.id}")
      assertEquals(4, thumbnail.areaParts.first().size)
      assertTrue(thumbnail.placeLabels.isNotEmpty(), "Expected place labels for ${survey.id}")
      assertTrue(
        abs(thumbnail.center.latitude - expectedCenter.latitude) < 0.01,
        "Expected ${survey.id} latitude near ${expectedCenter.latitude}, got ${thumbnail.center.latitude}",
      )
      assertTrue(
        abs(thumbnail.center.longitude - expectedCenter.longitude) < 0.01,
        "Expected ${survey.id} longitude near ${expectedCenter.longitude}, got ${thumbnail.center.longitude}",
      )
      // Bounds stay tightly around the AOI (< 0.25°), never spanning continents to placeholder
      // Nyeri entities.
      assertTrue(thumbnail.bounds.east - thumbnail.bounds.west in 0.01..0.25)
      assertTrue(thumbnail.bounds.north - thumbnail.bounds.south in 0.01..0.25)
      assertTrue(thumbnail.features.all { f -> f.vertices.all { v -> v in thumbnail.bounds } })
    }

    val kenya = surveys.first { it.id == "00000033-6b50-51cd-ac94-baea2ea06d65" }
    assertEquals("Othaya Sub-County, Nyeri", kenya.thumbnail.areaName)
    assertTrue(kenya.thumbnail.placeLabels.any { it.name.contains("Othaya", ignoreCase = true) })
    assertEquals(5, kenya.thumbnail.features.size)
    assertTrue(kenya.thumbnail.features.any { it.type == GeometryType.POLYGON })
    assertTrue(kenya.thumbnail.features.any { it.type == GeometryType.LINE_STRING })
    assertTrue(kenya.thumbnail.features.any { it.type == GeometryType.POINT })

    // Every sample survey's entities sit inside its AOI and are framed tightly on its thumbnail.
    val para = surveys.first { it.id == "00000034-c088-5ab9-8b98-e6bccb3dca71" }
    assertEquals("Forest sample plots, Pará", para.thumbnail.areaName)
    assertEquals(5, para.thumbnail.features.size)

    val arusha = surveys.first { it.id == "00000036-1a1c-5c51-997f-28b67d813aa3" }
    assertEquals(3, arusha.thumbnail.features.size)

    val ashanti = surveys.first { it.id == "00000031-ffc6-5c8e-934c-d14ec07533ad" }
    assertEquals(3, ashanti.thumbnail.features.size)

    val kakamega = surveys.first { it.id == "00000032-8d62-5717-8074-f4917e0cd76d" }
    assertEquals(6, kakamega.thumbnail.features.size)

    val kalimantan = surveys.first { it.id == "00000037-7e4a-5b2c-9d18-3f8a61c4e902" }
    assertEquals("Sebangau Peat Dome, Central Kalimantan", kalimantan.thumbnail.areaName)
    assertEquals(5, kalimantan.thumbnail.features.size)
    assertTrue(kalimantan.thumbnail.features.all { it.type == GeometryType.POLYGON })

    // Mekong Delta has 5 in-area mangrove plots/transects/stations, framed tightly around those
    // features with smart feature framing rather than the outer regional AOI rectangle.
    val mekong = surveys.first { it.id == "00000035-caf4-571e-b907-f506f92666ab" }
    assertEquals(5, mekong.thumbnail.features.size)
    assertTrue(mekong.thumbnail.features.any { it.type == GeometryType.POLYGON })
    assertTrue(mekong.thumbnail.features.any { it.type == GeometryType.LINE_STRING })
    assertTrue(mekong.thumbnail.features.any { it.type == GeometryType.POINT })
    val mekongLngSpan = mekong.thumbnail.bounds.east - mekong.thumbnail.bounds.west
    val mekongLatSpan = mekong.thumbnail.bounds.north - mekong.thumbnail.bounds.south
    assertTrue(
      mekongLngSpan in 0.012..0.04 && mekongLatSpan in 0.012..0.04,
      "Expected smart neighborhood framing for Mekong Delta features ($mekongLngSpan × $mekongLatSpan)",
    )
    assertTrue(mekong.thumbnail.zoom >= 13.0)
  }

  @Test
  fun fromRuntime_withSurveyAreaWithoutParts_framesInAreaFeaturesOrCenterAtZoom() {
    val centerOnlyArea =
      SurveyArea(
        name = "Kumasi Urban Core",
        parts = emptyList(),
        center = LatLng(6.6900, -1.6200),
        zoom = 13.0,
      )
    val inBoundsEntity =
      GeospatialEntityItem(
        id = "in-bounds",
        label = "Plot A",
        datasetId = "plots",
        datasetName = "Plots",
        typeNoun = "Plot",
        layerId = "layer-plots",
        geoId = "P-1",
        geometry = Geometry.Point(LatLng(6.6910, -1.6190)),
        areaHectares = 0.1,
        perimeterMeters = 40,
        colorHex = 0xFF2E7D32,
        properties = emptyMap(),
        submissions = emptyList(),
      )
    val outOfBoundsEntity =
      inBoundsEntity.copy(
        id = "out-of-bounds",
        geometry = Geometry.Point(LatLng(-0.4198, 36.9512)),
      )

    val thumbnail =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "survey-center-only",
        surveyArea = centerOnlyArea,
        mapLayers = emptyList(),
        entities = listOf(inBoundsEntity, outOfBoundsEntity),
      )

    assertTrue(thumbnail.areaParts.isEmpty())
    assertEquals("Kumasi Urban Core", thumbnail.areaName)
    assertTrue(thumbnail.hasContent)
    assertEquals(LatLng(6.691, -1.619), thumbnail.center)
    assertEquals(listOf("in-bounds"), thumbnail.features.map { it.id })
    val spanLng = thumbnail.bounds.east - thumbnail.bounds.west
    assertTrue(
      spanLng >= SurveyThumbnailGenerator.MIN_FEATURE_FRAME_SPAN_DEG,
      "Expected minimum feature span, got $spanLng",
    )

    val urlWithFeature = thumbnail.staticImageUrl(accessToken = "pk.test_token")
    assertTrue(urlWithFeature.contains("/static/geojson("))
    assertFalse(urlWithFeature.contains("/auto/"))

    val emptyAreaThumb =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "survey-center-only-empty",
        surveyArea = centerOnlyArea,
        mapLayers = emptyList(),
        entities = listOf(outOfBoundsEntity),
      )
    assertEquals(LatLng(6.69, -1.62), emptyAreaThumb.center)
    assertEquals(13.0, emptyAreaThumb.zoom)
    assertTrue(emptyAreaThumb.features.isEmpty())
    val urlWithoutFeatures = emptyAreaThumb.staticImageUrl(accessToken = "pk.test_token")
    assertTrue(urlWithoutFeatures.contains("/static/["))
    assertFalse(urlWithoutFeatures.contains("/geojson("))
  }

  @Test
  fun fromRuntime_withoutSurveyArea_framesFeaturesOrAnchorFallback() {
    val customEntity =
      GeospatialEntityItem(
        id = "andean-1",
        label = "Watershed Gauge",
        datasetId = "gauges",
        datasetName = "Gauges",
        typeNoun = "Gauge",
        layerId = "layer-gauges",
        geoId = "G-1",
        geometry = Geometry.Point(LatLng(-13.5310, -71.9680)),
        areaHectares = 0.01,
        perimeterMeters = 10,
        colorHex = 0xFF1565C0,
        properties = emptyMap(),
        submissions = emptyList(),
      )
    val featureFramed =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "survey-no-area",
        surveyArea = null,
        mapLayers = emptyList(),
        entities = listOf(customEntity),
      )
    assertTrue(featureFramed.areaParts.isEmpty())
    assertEquals(1, featureFramed.features.size)
    assertTrue(abs(featureFramed.center.latitude - (-13.531)) < 0.005)
    assertTrue(abs(featureFramed.center.longitude - (-71.968)) < 0.005)
    assertTrue(
      featureFramed.bounds.east - featureFramed.bounds.west >=
        SurveyThumbnailGenerator.MIN_FEATURE_FRAME_SPAN_DEG
    )

    val anchorFramed = SurveyThumbnailGenerator.forNewSurvey("survey-andean-watershed")
    assertTrue(anchorFramed.areaParts.isEmpty())
    assertTrue(anchorFramed.features.isEmpty())
    assertTrue(abs(anchorFramed.center.latitude - (-13.532)) < 0.02)
    assertTrue(abs(anchorFramed.center.longitude - (-71.9675)) < 0.02)
  }

  @Test
  fun staticImageUrl_neverEmitsAoiBoundingBoxAndOmitsGeoJsonWhenFeaturesEmpty() {
    val secondRing =
      listOf(
        LatLng(-3.5100, -62.2600),
        LatLng(-3.5100, -62.2400),
        LatLng(-3.5300, -62.2400),
        LatLng(-3.5300, -62.2600),
      )
    val aoiOnlyThumb =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "para-aoi-only",
        surveyArea = SurveyArea(name = "Pará Islands", parts = listOf(denseParaRing(), secondRing)),
        mapLayers = emptyList(),
        entities = emptyList(),
      )
    assertEquals(2, aoiOnlyThumb.areaParts.size)
    assertTrue(aoiOnlyThumb.features.isEmpty())

    val url =
      aoiOnlyThumb.staticImageUrl(
        accessToken = "pk.test_123",
        widthPx = 640,
        heightPx = 280,
        highDpi = true,
      )
    assertFalse(url.contains("/geojson("), "AOI rings must never be rendered in geojson(...)")
    assertFalse(url.contains("/auto/"), "Viewport must use explicit [west,south,east,north]")
    assertTrue(
      url.startsWith("https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/[")
    )
    assertTrue(url.endsWith("]/640x280@2x?attribution=false&logo=false&access_token=pk.test_123"))
  }

  @Test
  fun staticImageUrl_encodesPolygonLineAndPointFeatureCollectionWithoutAoiOutline() {
    val thumb = buildParaFeatureThumbnail()
    val url =
      thumb.staticImageUrl(
        accessToken = "pk.test_123",
        widthPx = 640,
        heightPx = 280,
        highDpi = true,
      )
    assertTrue(
      url.startsWith(
        "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/geojson("
      )
    )
    assertFalse(url.contains("/auto/"))
    assertTrue(url.endsWith("/640x280@2x?attribution=false&logo=false&access_token=pk.test_123"))

    val encodedPayload = url.substringAfter("geojson(").substringBefore(")/[")
    for (rawChar in listOf('{', '}', '[', ']', '"', ':', ',', '#', ' ')) {
      assertFalse(encodedPayload.contains(rawChar), "Expected '$rawChar' to be percent-encoded")
    }
    val decodedJson = decodePercentEncoded(encodedPayload)
    assertTrue(decodedJson.startsWith("""{"type":"FeatureCollection","features":["""))
    assertFalse(decodedJson.contains("-62.3"), "AOI corner must never appear in GeoJSON overlay")
    assertTrue(
      decodedJson.contains(
        """"properties":{"stroke":"#2e7d32","stroke-width":2,"stroke-opacity":0.95,"fill":"#2e7d32","fill-opacity":0.32}"""
      )
    )
    assertTrue(
      decodedJson.contains(
        """"properties":{"stroke":"#0277bd","stroke-width":2.5,"stroke-opacity":0.95},"geometry":{"type":"LineString""""
      )
    )
    assertTrue(
      decodedJson.contains(
        """"properties":{"marker-size":"small","marker-color":"#ef6c00"},"geometry":{"type":"Point","coordinates":[-62.21568,-3.46012]}"""
      )
    )
    assertSimplifiedPolygonAndLinePrecision(decodedJson)
  }

  private fun buildParaFeatureThumbnail(): SurveyThumbnail {
    val aoiRing =
      listOf(
        LatLng(-3.4000, -62.3000),
        LatLng(-3.4000, -62.1000),
        LatLng(-3.5500, -62.1000),
        LatLng(-3.5500, -62.3000),
      )
    val lineVertices =
      (0 until 32).map { index ->
        LatLng(
          latitude = -3.462000 + index * 0.00025,
          longitude = -62.218000 + index * 0.00031,
        )
      }
    return SurveyThumbnailGenerator.fromOverviewFeatures(
      surveyId = "para-features",
      surveyArea = SurveyArea(name = "Pará Reserve", parts = listOf(aoiRing)),
      features =
        listOf(
          SurveyThumbnailFeature(
            id = "poly-1",
            type = GeometryType.POLYGON,
            vertices = denseParaRing(),
            colorHex = 0xFF2E7D32L,
          ),
          SurveyThumbnailFeature(
            id = "line-1",
            type = GeometryType.LINE_STRING,
            vertices = lineVertices,
            colorHex = 0xFF0277BDL,
          ),
          SurveyThumbnailFeature(
            id = "pt-1",
            type = GeometryType.POINT,
            vertices = listOf(LatLng(-3.46012345, -62.21567891)),
            colorHex = 0xFFEF6C00L,
          ),
        ),
    )
  }

  private fun assertSimplifiedPolygonAndLinePrecision(decodedJson: String) {
    val polyCoordsBlock =
      decodedJson.substringAfter(""""type":"Polygon","coordinates":[[""").substringBefore("]]}")
    val polyPairs = polyCoordsBlock.split("],[").map { it.removePrefix("[").removeSuffix("]") }
    assertTrue(
      polyPairs.size in 4..25,
      "Expected simplified closed polygon <= 25 pairs, got ${polyPairs.size}",
    )
    assertEquals(polyPairs.first(), polyPairs.last(), "Polygon ring must be closed (first == last)")
    for (pair in polyPairs) {
      val parts = pair.split(',')
      assertEquals(2, parts.size)
      for (coord in parts) {
        val decimals = coord.substringAfter('.', missingDelimiterValue = "").length
        assertTrue(decimals <= 5, "Expected <= 5 decimals in '$coord'")
      }
    }

    val lineCoordsBlock =
      decodedJson.substringAfter(""""type":"LineString","coordinates":[""").substringBefore("]}")
    val linePairs = lineCoordsBlock.split("],[").map { it.removePrefix("[").removeSuffix("]") }
    assertTrue(
      linePairs.size in 2..24,
      "Expected simplified line <= 24 vertices, got ${linePairs.size}",
    )
  }

  private fun denseParaRing(): List<LatLng> =
    (0 until 90).map { i ->
      val angle = 2.0 * PI * i / 90.0
      LatLng(
        latitude = -3.46123456 + 0.008 * sin(angle),
        longitude = -62.21456789 + 0.011 * cos(angle),
      )
    }

  @Test
  fun staticImageUrl_greedilyIncludesFeaturesWithinMaxUrlLengthAndSanitizesBounds() {
    val manyFeatures =
      (0 until 40).map { index ->
        val baseLat = -2.0 - index * 0.01
        val baseLng = 35.0 + index * 0.01
        SurveyThumbnailFeature(
          id = "poly-$index",
          type = GeometryType.POLYGON,
          vertices =
            (0 until 30).map { v ->
              val angle = 2.0 * PI * v / 30.0
              LatLng(
                latitude = baseLat + 0.00345 * sin(angle),
                longitude = baseLng + 0.00456 * cos(angle),
              )
            },
          colorHex = 0xFF2E7D32L,
        )
      }
    val hugeThumbnail =
      SurveyThumbnail(
        areaName = "Archipelago",
        features = manyFeatures,
        bounds = LngLatBounds(west = 190.0, south = 89.0, east = -195.0, north = -89.0),
      )

    val greedyUrl = hugeThumbnail.staticImageUrl(accessToken = "pk.test_123")
    assertTrue(greedyUrl.contains("/geojson("))
    assertTrue(greedyUrl.contains(")/[-180,-85,180,85]/640x280@2x?"))
    assertTrue(
      greedyUrl.length <= SurveyThumbnail.MAX_STATIC_URL_LENGTH,
      "Expected URL <= ${SurveyThumbnail.MAX_STATIC_URL_LENGTH}, got ${greedyUrl.length}",
    )
    val decoded = decodePercentEncoded(greedyUrl.substringAfter("geojson(").substringBefore(")/["))
    val includedCount = decoded.split(""""type":"Feature"""").size - 1
    assertTrue(
      includedCount in 2 until manyFeatures.size,
      "Expected greedy subset, got $includedCount",
    )

    // Zero-span bounds with degenerate features omit geojson(...) and expand bounds safely.
    val degenerateBoundsThumb =
      SurveyThumbnail(
        areaParts = emptyList(),
        features =
          listOf(
            SurveyThumbnailFeature(
              id = "degenerate-poly",
              type = GeometryType.POLYGON,
              vertices = listOf(LatLng(-3.38, 36.68), LatLng(-3.38, 36.68)),
              colorHex = 0xFF2E7D32L,
            )
          ),
        bounds = LngLatBounds(west = 36.68, south = -3.38, east = 36.68, north = -3.38),
      )
    val degenerateUrl = degenerateBoundsThumb.staticImageUrl(accessToken = "pk.test_123")
    assertFalse(degenerateUrl.contains("/geojson("))
    val bboxSegment =
      degenerateUrl.substringAfter("/static/[").substringBefore("]/").split(',').map {
        it.toDouble()
      }
    assertEquals(4, bboxSegment.size)
    assertTrue(bboxSegment[0] < bboxSegment[2], "Expected west < east in $bboxSegment")
    assertTrue(bboxSegment[1] < bboxSegment[3], "Expected south < north in $bboxSegment")
  }

  @Test
  fun saveDraft_regeneratesAndStoresSurveyThumbnailWithUpdatedAreaAndFeatures() {
    val store = seededStore()
    val surveyRepo = SurveyRepositoryImpl(store)
    val editorRepo = SurveyEditorRepositoryImpl(store)
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"

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
    val newRow =
      EntityRow(
        key = "station-new",
        values =
          mapOf(
            newPointLayer.keyProperty to "station-new",
            newPointLayer.labelProperty to "New Mill",
          ),
        geometry = listOf(LatLng(-0.4180, 36.9480)),
      )
    val updatedDraft =
      draft.copy(
        details = draft.details.copy(surveyArea = customArea),
        datasets =
          draft.datasets.map { d ->
            if (d.key == newPointLayer.key) {
              d.copy(rows = d.rows + newRow)
            } else {
              d
            }
          },
      )

    runNow { editorRepo.saveDraft(surveyId, updatedDraft) }

    val expectedNewFeatureId = SurveyEditorDerivation.entityIdOf(newPointLayer, newRow, surveyId)
    val updatedSurvey = runNow { surveyRepo.getSurveys() }.first { it.id == surveyId }
    assertEquals("Custom Conservation Block", updatedSurvey.thumbnail.areaName)
    assertEquals(1, updatedSurvey.thumbnail.areaParts.size)
    assertEquals(4, updatedSurvey.thumbnail.areaParts.first().size)
    assertTrue(
      updatedSurvey.thumbnail.features.any {
        it.id == expectedNewFeatureId && it.type == GeometryType.POINT
      }
    )
    assertTrue(updatedSurvey.thumbnail.features.any { it.id == mapDataset.rows.first().key })
    assertTrue(
      updatedSurvey.thumbnail.features.all { f ->
        f.vertices.all { v -> v in updatedSurvey.thumbnail.bounds }
      }
    )
  }

  @Test
  fun coveringTiles_computesValidWebMercatorTileGridAndUrls() {
    val kenya =
      SampleSurveysData.defaultSampleSurveys().first {
        it.id == "00000033-6b50-51cd-ac94-baea2ea06d65"
      }
    val tiles =
      WebMercatorThumbnailProjection.coveringTiles(
        bounds = kenya.thumbnail.bounds,
        fallbackZoom = kenya.thumbnail.zoom,
      )
    assertTrue(tiles.isNotEmpty())
    assertTrue(tiles.size <= 9, "Thumbnail should only need a compact tile grid")
    val firstUrl = tiles.first().resolveUrl(kenya.thumbnail.tileUrlTemplate)
    assertTrue(
      firstUrl.startsWith("https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/")
    )
    assertTrue(!firstUrl.contains("{z}") && !firstUrl.contains("{x}") && !firstUrl.contains("{y}"))
  }

  @Test
  fun fromDraftAndRuntime_fallsBackToDraftDatasetsWhenRuntimeEntitiesAreEmpty() {
    val plotVertices =
      listOf(
        LatLng(-0.4180, 36.9480),
        LatLng(-0.4180, 36.9490),
        LatLng(-0.4190, 36.9490),
        LatLng(-0.4190, 36.9480),
      )
    val dataset =
      EntityDataset(
        key = "d-plots",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        geometryType = GeometryType.POLYGON,
        keyProperty = "id",
        labelProperty = "label",
        properties = emptyList(),
        style = LayerStyle(colorHex = "#E65100", visibleByDefault = true),
        rows = listOf(EntityRow(key = "plot-1", values = emptyMap(), geometry = plotVertices)),
      )
    val draft =
      SurveyEditorDraft(
        details =
          SurveyDetails(
            surveyId = "draft-survey-1",
            title = "Draft with plots",
            description = "",
            surveyArea =
              SurveyArea(
                name = "Nyeri Block",
                center = LatLng(-0.4185, 36.9485),
                zoom = 14.0,
                parts =
                  listOf(
                    listOf(
                      LatLng(-0.4100, 36.9400),
                      LatLng(-0.4100, 36.9600),
                      LatLng(-0.4300, 36.9600),
                      LatLng(-0.4300, 36.9400),
                    )
                  ),
              ),
          ),
        datasets = listOf(dataset),
        sharing = SharingSettings(ownerEmail = "owner@example.org"),
      )
    val mapLayer =
      MapLayerItem(
        id = "layer-sample_plots",
        label = "Sample plots",
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        colorHex = 0xFFE65100L,
      )

    val thumbnail =
      SurveyThumbnailGenerator.fromDraftAndRuntime(
        surveyId = "draft-survey-1",
        draft = draft,
        mapLayers = listOf(mapLayer),
        entities = emptyList(),
      )

    assertEquals(1, thumbnail.features.size)
    assertEquals(GeometryType.POLYGON, thumbnail.features.single().type)
  }

  @Test
  fun staticImageUrl_compactsDensePlotGridIntoMultiPolygonAndExpandsTinyPlots() {
    val gridPlots = buildList {
      for (r in 0 until 20) {
        for (c in 0 until 15) {
          val lat = 4.0 - r * 0.4
          val lng = 34.0 + c * 0.4
          val d = 0.00009 // ~20m x 20m plot
          add(
            SurveyThumbnailFeature(
              id = "plot-$r-$c",
              type = GeometryType.POLYGON,
              vertices =
                listOf(
                  LatLng(lat - d, lng - d),
                  LatLng(lat - d, lng + d),
                  LatLng(lat + d, lng + d),
                  LatLng(lat + d, lng - d),
                ),
              colorHex = 0xFFE65100L,
            )
          )
        }
      }
    }
    val thumbnail =
      SurveyThumbnailGenerator.fromOverviewFeatures(
        surveyId = "grid-survey",
        surveyArea = null,
        features = gridPlots,
      )

    // Even sampling preserves both the first (northernmost) and last (southernmost) grid plots.
    assertEquals(150, thumbnail.features.size)
    assertEquals("plot-0-0", thumbnail.features.first().id)
    assertEquals("plot-19-14", thumbnail.features.last().id)

    val url = thumbnail.staticImageUrl(accessToken = "pk.test_123")
    assertTrue(url.length <= SurveyThumbnail.MAX_STATIC_URL_LENGTH)
    val decoded = decodePercentEncoded(url.substringAfter("geojson(").substringBefore(")/["))
    assertTrue(decoded.contains(""""type":"MultiPolygon""""), "Expected MultiPolygon in $decoded")
  }

  private fun decodePercentEncoded(encoded: String): String {
    val out = ArrayList<Byte>(encoded.length)
    var i = 0
    while (i < encoded.length) {
      if (encoded[i] == '%' && i + 2 < encoded.length) {
        out.add(encoded.substring(i + 1, i + 3).toInt(16).toByte())
        i += 3
      } else {
        out.add(encoded[i].code.toByte())
        i++
      }
    }
    return out.toByteArray().decodeToString()
  }
}
