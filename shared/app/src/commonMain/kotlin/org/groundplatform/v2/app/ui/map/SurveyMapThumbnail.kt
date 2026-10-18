/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull
import org.groundplatform.v2.app.domain.model.EntityShapeKind
import org.groundplatform.v2.app.domain.model.SurveyThumbnail
import org.groundplatform.v2.app.domain.model.WebMercatorThumbnailProjection
import org.groundplatform.v2.app.ui.viewmodel.createThumbnailHttpClient
import org.groundplatform.v2.app.ui.viewmodel.resolveMapboxPublicToken
import org.groundplatform.v2.app.ui.viewmodel.reverseGeocodeThumbnailLabels
import org.groundplatform.v2.map.LatLng
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * In-memory cache and HTTP loader for 256×256 Web Mercator satellite raster tiles rendered inside
 * [SurveyMapThumbnail].
 */
private object SurveyThumbnailTileCache {
  private const val MAX_CACHED_TILES = 256
  private const val TILE_FETCH_TIMEOUT_MS = 5_000L
  private val httpClient: HttpClient? by lazy { createThumbnailHttpClient() }
  private val cache = LinkedHashMap<String, ImageBitmap>()

  fun get(url: String): ImageBitmap? = cache[url]

  suspend fun fetchTile(url: String): ImageBitmap? {
    cache[url]?.let {
      return it
    }
    val client = httpClient ?: return null
    return try {
      withTimeoutOrNull(TILE_FETCH_TIMEOUT_MS) {
        val response = client.get(url)
        if (!response.status.isSuccess()) return@withTimeoutOrNull null
        val bytes = response.body<ByteArray>()
        if (bytes.isEmpty()) return@withTimeoutOrNull null
        val bitmap = bytes.decodeToImageBitmap()
        if (cache.size >= MAX_CACHED_TILES) {
          cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[url] = bitmap
        bitmap
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }
}

/**
 * Client-side satellite map thumbnail showing the survey's selected area of interest boundary
 * (transparent fill with dotted outline like the main map), Mapbox place labels, and map features
 * (polygons, lines, and points) projected over real XYZ satellite raster tiles.
 */
@Composable
fun SurveyMapThumbnail(
  thumbnail: SurveyThumbnail,
  isDownloaded: Boolean,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val mapboxToken = remember { resolveMapboxPublicToken() }
  val effectiveSatelliteTemplate =
    remember(thumbnail.tileUrlTemplate, mapboxToken) {
      if (
        mapboxToken != null &&
          thumbnail.tileUrlTemplate == SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL
      ) {
        "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/tiles/256/{z}/{x}/{y}?access_token=$mapboxToken"
      } else {
        thumbnail.tileUrlTemplate
      }
    }
  val coveringTiles =
    remember(thumbnail.bounds, thumbnail.zoom) {
      WebMercatorThumbnailProjection.coveringTiles(thumbnail.bounds, thumbnail.zoom)
    }
  var loadedTiles by
    remember(coveringTiles, effectiveSatelliteTemplate) {
      mutableStateOf(
        coveringTiles
          .mapNotNull { coord ->
            val url = coord.resolveUrl(effectiveSatelliteTemplate)
            SurveyThumbnailTileCache.get(url)?.let { coord to it }
          }
          .toMap()
      )
    }
  var loadedLabelTiles by
    remember(coveringTiles, thumbnail.labelTileUrlTemplate, mapboxToken) {
      mutableStateOf(
        if (mapboxToken != null || thumbnail.labelTileUrlTemplate.isBlank()) {
          emptyMap()
        } else {
          coveringTiles
            .mapNotNull { coord ->
              val url = coord.resolveUrl(thumbnail.labelTileUrlTemplate)
              SurveyThumbnailTileCache.get(url)?.let { coord to it }
            }
            .toMap()
        }
      )
    }
  var placeLabels by remember(thumbnail.placeLabels) { mutableStateOf(thumbnail.placeLabels) }

  LaunchedEffect(coveringTiles, effectiveSatelliteTemplate, thumbnail.labelTileUrlTemplate) {
    for (coord in coveringTiles) {
      if (coord !in loadedTiles) {
        val url = coord.resolveUrl(effectiveSatelliteTemplate)
        val bitmap = SurveyThumbnailTileCache.fetchTile(url)
        if (bitmap != null) {
          loadedTiles = loadedTiles + (coord to bitmap)
        }
      }
      if (
        mapboxToken == null &&
          thumbnail.labelTileUrlTemplate.isNotBlank() &&
          coord !in loadedLabelTiles
      ) {
        val labelUrl = coord.resolveUrl(thumbnail.labelTileUrlTemplate)
        val labelBitmap = SurveyThumbnailTileCache.fetchTile(labelUrl)
        if (labelBitmap != null) {
          loadedLabelTiles = loadedLabelTiles + (coord to labelBitmap)
        }
      }
    }
  }

  LaunchedEffect(thumbnail.center, mapboxToken) {
    if (mapboxToken != null) {
      val geocoded = reverseGeocodeThumbnailLabels(thumbnail.center, limit = 2)
      if (geocoded.isNotEmpty()) {
        placeLabels = geocoded
      }
    }
  }

  Box(
    modifier =
      modifier
        .size(80.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(Color(0xFF142E21))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Base satellite canopy/earth texture shown behind or before raster tiles resolve
      drawRect(color = Color(0xFF163223))
      drawCircle(
        color = Color(0xFF1F422D),
        radius = size.minDimension * 0.55f,
        center = Offset(size.width * 0.30f, size.height * 0.35f),
      )
      drawCircle(
        color = Color(0xFF284D34),
        radius = size.minDimension * 0.45f,
        center = Offset(size.width * 0.72f, size.height * 0.68f),
      )

      val minNormX =
        min(
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.west),
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.east),
        )
      val maxNormX =
        max(
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.west),
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.east),
        )
      val minNormY =
        min(
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.north),
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.south),
        )
      val maxNormY =
        max(
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.north),
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.south),
        )
      val centerNormX = (minNormX + maxNormX) / 2.0
      val centerNormY = (minNormY + maxNormY) / 2.0
      val spanNorm = max(max(maxNormX - minNormX, maxNormY - minNormY), 1e-6)
      val worldCanvasPx = min(size.width, size.height).toDouble() / spanNorm

      fun toCanvasX(normX: Double): Float =
        (size.width / 2.0 + (normX - centerNormX) * worldCanvasPx).toFloat()

      fun toCanvasY(normY: Double): Float =
        (size.height / 2.0 + (normY - centerNormY) * worldCanvasPx).toFloat()

      fun toOffset(point: LatLng): Offset =
        Offset(
          x = toCanvasX(WebMercatorThumbnailProjection.lngToNormX(point.longitude)),
          y = toCanvasY(WebMercatorThumbnailProjection.latToNormY(point.latitude)),
        )

      // 1. Draw covering XYZ satellite raster tiles
      for ((coord, bitmap) in loadedTiles) {
        val left = floor(toCanvasX(coord.normLeft)).toInt()
        val top = floor(toCanvasY(coord.normTop)).toInt()
        val right = ceil(toCanvasX(coord.normRight)).toInt()
        val bottom = ceil(toCanvasY(coord.normBottom)).toInt()
        drawImage(
          image = bitmap,
          dstOffset = IntOffset(left, top),
          dstSize = IntSize(max(1, right - left), max(1, bottom - top)),
        )
      }

      // 1b. Draw reference boundaries & place label tiles (matches SurveyBasemaps.Satellite)
      for ((coord, labelBitmap) in loadedLabelTiles) {
        val left = floor(toCanvasX(coord.normLeft)).toInt()
        val top = floor(toCanvasY(coord.normTop)).toInt()
        val right = ceil(toCanvasX(coord.normRight)).toInt()
        val bottom = ceil(toCanvasY(coord.normBottom)).toInt()
        drawImage(
          image = labelBitmap,
          dstOffset = IntOffset(left, top),
          dstSize = IntSize(max(1, right - left), max(1, bottom - top)),
          alpha = 0.88f,
        )
      }

      // 2. Draw Area of Interest square: transparent fill with dotted outline like the main map
      val aoiDashEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()), 0f)
      val aoiStrokeColor = Color(0xFF8BD6B1).copy(alpha = 0.85f)
      for (ring in thumbnail.areaParts) {
        if (ring.size < 3) continue
        val offsets = ring.map(::toOffset)
        val path =
          Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            for (i in 1 until offsets.size) {
              lineTo(offsets[i].x, offsets[i].y)
            }
            close()
          }
        drawPath(
          path = path,
          color = Color.Black.copy(alpha = 0.45f),
          style =
            Stroke(width = 2.6.dp.toPx(), join = StrokeJoin.Round, pathEffect = aoiDashEffect),
        )
        drawPath(
          path = path,
          color = aoiStrokeColor,
          style =
            Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round, pathEffect = aoiDashEffect),
        )
      }

      // Helper to ensure small polygon/line features remain legible when framed inside a wide AOI
      fun ensureMinSpan(points: List<Offset>, minSpanPx: Float): List<Offset> {
        if (points.size < 2) return points
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val span = max(maxX - minX, maxY - minY)
        if (span >= minSpanPx || span <= 1e-3f) return points
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val factor = minSpanPx / span
        return points.map { pt -> Offset(cx + (pt.x - cx) * factor, cy + (pt.y - cy) * factor) }
      }

      // 3. Draw Polygon Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.POLYGON || feature.vertices.size < 3) continue
        val pts = ensureMinSpan(feature.vertices.map(::toOffset), minSpanPx = 7.dp.toPx())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        val path =
          Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
              lineTo(pts[i].x, pts[i].y)
            }
            close()
          }
        drawPath(path = path, color = featureColor.copy(alpha = 0.48f))
        drawPath(
          path = path,
          color = Color.White.copy(alpha = 0.90f),
          style = Stroke(width = 2.0.dp.toPx(), join = StrokeJoin.Round),
        )
        drawPath(
          path = path,
          color = featureColor,
          style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
        )
      }

      // 4. Draw Line Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.LINE || feature.vertices.size < 2) continue
        val pts = ensureMinSpan(feature.vertices.map(::toOffset), minSpanPx = 8.dp.toPx())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        val path =
          Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
              lineTo(pts[i].x, pts[i].y)
            }
          }
        drawPath(
          path = path,
          color = Color.White.copy(alpha = 0.90f),
          style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
          path = path,
          color = featureColor,
          style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }

      // 5. Draw Point Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.POINT || feature.vertices.isEmpty()) continue
        val pt = toOffset(feature.vertices.first())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        drawCircle(
          color = Color.Black.copy(alpha = 0.35f),
          radius = 3.8.dp.toPx(),
          center = Offset(pt.x, pt.y + 0.6.dp.toPx()),
        )
        drawCircle(color = Color.White, radius = 3.3.dp.toPx(), center = pt)
        drawCircle(color = featureColor, radius = 2.2.dp.toPx(), center = pt)
      }

      // 6. Draw Mapbox Place Labels (haloed satellite place names)
      val haloTextStyle =
        TextStyle(
          color = Color(0xD90A1912),
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      val labelTextStyle =
        TextStyle(
          color = Color(0xFFF5F7F6),
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      placeLabels.take(2).forEachIndexed { index, place ->
        val rawAnchor = toOffset(place.position)
        val measured =
          textMeasurer.measure(
            text = place.name,
            style = labelTextStyle,
            maxLines = 1,
            softWrap = false,
          )
        val textW = measured.size.width.toFloat()
        val textH = measured.size.height.toFloat()
        val pad = 3.dp.toPx()
        val defaultY =
          if (index == 0) {
            rawAnchor.y.coerceIn(pad + textH / 2f, size.height * 0.32f)
          } else {
            rawAnchor.y.coerceIn(size.height * 0.68f, size.height - pad - textH / 2f)
          }
        val topLeftX = (rawAnchor.x - textW / 2f).coerceIn(pad, max(pad, size.width - textW - pad))
        val topLeftY = (defaultY - textH / 2f).coerceIn(pad, max(pad, size.height - textH - pad))

        val haloLayout =
          textMeasurer.measure(
            text = place.name,
            style = haloTextStyle,
            maxLines = 1,
            softWrap = false,
          )
        val haloOffset = 0.9.dp.toPx()
        for (dx in listOf(-haloOffset, haloOffset)) {
          for (dy in listOf(-haloOffset, haloOffset)) {
            drawText(
              textLayoutResult = haloLayout,
              topLeft = Offset(topLeftX + dx, topLeftY + dy),
            )
          }
        }
        drawText(
          textLayoutResult = measured,
          topLeft = Offset(topLeftX, topLeftY),
        )
      }
    }

    // Top-right mini offline check icon on map thumbnail when downloaded
    if (isDownloaded) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Downloaded",
            modifier = Modifier.size(11.dp),
          )
        }
      }
    }
  }
}
