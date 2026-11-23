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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnail
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailFeature
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailPlaceLabel
import org.openforis.collectone.v2.app.domain.model.WebMercatorThumbnailProjection
import org.openforis.collectone.v2.app.domain.repository.SurveyMapThumbnailRepository
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_downloaded
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Where [SurveyMapThumbnail] loads Mapbox Static Images snapshots from. The app session provides
 * it; without one (or when offline), thumbnails render the framed vector fallback map.
 */
val LocalSurveyMapThumbnailRepository =
  staticCompositionLocalOf<SurveyMapThumbnailRepository?> { null }

/**
 * In-memory LRU cache of decoded [ImageBitmap] snapshots rendered inside [SurveyMapThumbnail], so
 * scrolling or revisiting a survey list reuses already-decoded imagery without refetching.
 *
 * Holds at most [maxTiles] bitmaps and evicts the least recently used entry first. Not
 * synchronized: reads occur during composition and writes occur from `LaunchedEffect` on the UI
 * thread.
 */
class SurveyThumbnailTileCache(private val maxTiles: Int = DEFAULT_MAX_CACHED_IMAGES) {
  // Insertion order is recency order: hits move to the end, evictions take the first key.
  private val cache = LinkedHashMap<String, ImageBitmap>()
  private val inFlight = HashMap<String, CompletableDeferred<ImageBitmap?>>()

  val size: Int
    get() = cache.size

  fun get(url: String): ImageBitmap? {
    val hit = cache.remove(url) ?: return null
    cache[url] = hit
    return hit
  }

  fun clear() {
    cache.clear()
  }

  suspend fun fetchTile(repository: SurveyMapThumbnailRepository, url: String): ImageBitmap? {
    get(url)?.let {
      return it
    }
    inFlight[url]?.let {
      return it.await()
    }
    val deferred = CompletableDeferred<ImageBitmap?>()
    inFlight[url] = deferred
    var bitmap: ImageBitmap? = null
    return try {
      bitmap = repository.fetchTile(url)?.let(::decode)?.also { store(url, it) }
      bitmap
    } finally {
      inFlight.remove(url)
      deferred.complete(bitmap)
    }
  }

  private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
    bytes.decodeToImageBitmap()
  }
    .getOrNull()

  private fun store(url: String, bitmap: ImageBitmap) {
    if (cache.size >= maxTiles) {
      cache.keys.firstOrNull()?.let(cache::remove)
    }
    cache[url] = bitmap
  }

  private companion object {
    const val DEFAULT_MAX_CACHED_IMAGES = 256
  }
}

/** The [SurveyThumbnailTileCache] thumbnails decode images into; see there for its lifetime. */
val LocalSurveyThumbnailTileCache = staticCompositionLocalOf { SurveyThumbnailTileCache() }

private val CanopyBackdropBase = Color(0xFF142E21)
private val CanopyBackdropFill = Color(0xFF163223)
private val CanopyRidgePrimary = Color(0xFF1F422D)
private val CanopyRidgeSecondary = Color(0xFF284D34)
private val AoiAccentMint = Color(0xFF8BD6B1)
private val LabelHaloDark = Color(0xD90A1912)
private val LabelTextLight = Color(0xFFF5F7F6)

/**
 * Survey hero map thumbnail driven by [modifier] dimensions.
 *
 * When a public Mapbox access token is configured and reachable, fetches and LRU-caches a real
 * `mapbox/satellite-streets-v12` Static Images API image (with native Mapbox place/road/water
 * labels and the survey's GeoJSON map features overlay) and renders it edge-to-edge with
 * [ContentScale.Crop]. When offline, tokenless, or while loading, renders a proportional Web
 * Mercator vector fallback showing the dark canopy backdrop, in-bounds map features, and place
 * labels without stretching.
 */
@Composable
fun SurveyMapThumbnail(
  thumbnail: SurveyThumbnail,
  isDownloaded: Boolean,
  modifier: Modifier = Modifier,
) {
  val repository = LocalSurveyMapThumbnailRepository.current
  val tileCache = LocalSurveyThumbnailTileCache.current
  val mapboxToken =
    remember(repository) { repository?.mapboxPublicToken?.takeIf { it.isNotBlank() } }

  BoxWithConstraints(
    modifier =
      modifier
        .defaultMinSize(minWidth = 80.dp, minHeight = 80.dp)
        .clipToBounds()
        .clearAndSetSemantics {}
        .background(CanopyBackdropBase)
  ) {
    val (widthPx, heightPx) =
      remember(maxWidth, maxHeight) { staticImageDimensions(maxWidth, maxHeight) }
    val staticUrl =
      remember(thumbnail, mapboxToken, widthPx, heightPx) {
        mapboxToken?.let { token ->
          thumbnail.staticImageUrl(accessToken = token, widthPx = widthPx, heightPx = heightPx)
        }
      }
    var loadedBitmap by
      remember(staticUrl, tileCache) {
        mutableStateOf(staticUrl?.let(tileCache::get))
      }

    LaunchedEffect(staticUrl, repository, tileCache) {
      if (staticUrl == null || repository == null || loadedBitmap != null) return@LaunchedEffect
      val fetched = tileCache.fetchTile(repository, staticUrl)
      if (fetched != null) {
        loadedBitmap = fetched
      }
    }

    val bitmap = loadedBitmap
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      OfflineSurveyThumbnailCanvas(thumbnail = thumbnail, modifier = Modifier.fillMaxSize())
    }

    if (isDownloaded) {
      DownloadedThumbnailBadge()
    }
  }
}

/**
 * Computes Mapbox Static Images pixel dimensions matching the hero container's aspect ratio,
 * quantized to 16 px height increments for stable LRU cache keys across minor layout variations.
 */
private fun staticImageDimensions(maxWidth: Dp, maxHeight: Dp): Pair<Int, Int> {
  if (!maxWidth.isPositiveFinite() || !maxHeight.isPositiveFinite()) {
    return SurveyThumbnail.DEFAULT_STATIC_WIDTH_PX to SurveyThumbnail.DEFAULT_STATIC_HEIGHT_PX
  }
  val widthPx = SurveyThumbnail.DEFAULT_STATIC_WIDTH_PX
  val rawHeightPx = (widthPx.toFloat() * (maxHeight / maxWidth)).roundToInt().coerceIn(192, 480)
  val quantizedHeightPx = ((rawHeightPx + 8) / 16) * 16
  return widthPx to quantizedHeightPx
}

private fun Dp.isPositiveFinite(): Boolean = value.isFinite() && this > 0.dp

@Composable
private fun BoxScope.DownloadedThumbnailBadge() {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    border = BorderStroke(1.dp, Color.White),
    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = stringResource(Res.string.app_downloaded),
        modifier = Modifier.size(13.dp),
      )
    }
  }
}

@Composable
private fun OfflineSurveyThumbnailCanvas(
  thumbnail: SurveyThumbnail,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  Canvas(modifier = modifier) {
    drawCanopyBackdrop()
    val projection = ThumbnailViewportProjection.fromBounds(thumbnail.bounds, size)
    drawPolygonFeatures(thumbnail.features, projection)
    drawLineFeatures(thumbnail.features, projection)
    drawPointFeatures(thumbnail.features, projection)
    drawFallbackLabels(thumbnail, projection, textMeasurer)
  }
}

/**
 * Conformal Web Mercator viewport mapping [bounds] into [canvasSize] using a single isotropic scale
 * factor (`min(width / spanX, height / spanY)`) so rectangular AOIs fit wide hero cards without
 * geometric stretching.
 */
private class ThumbnailViewportProjection(
  private val canvasWidth: Float,
  private val canvasHeight: Float,
  private val centerNormX: Double,
  private val centerNormY: Double,
  private val worldCanvasPx: Double,
) {
  fun toOffset(point: LatLng): Offset {
    val normX = WebMercatorThumbnailProjection.lngToNormX(point.longitude)
    val normY = WebMercatorThumbnailProjection.latToNormY(point.latitude)
    return Offset(
      x = (canvasWidth / 2.0 + (normX - centerNormX) * worldCanvasPx).toFloat(),
      y = (canvasHeight / 2.0 + (normY - centerNormY) * worldCanvasPx).toFloat(),
    )
  }

  companion object {
    fun fromBounds(bounds: LngLatBounds, canvasSize: Size): ThumbnailViewportProjection {
      val westX = WebMercatorThumbnailProjection.lngToNormX(bounds.west)
      val eastX = WebMercatorThumbnailProjection.lngToNormX(bounds.east)
      val northY = WebMercatorThumbnailProjection.latToNormY(bounds.north)
      val southY = WebMercatorThumbnailProjection.latToNormY(bounds.south)
      val minNormX = min(westX, eastX)
      val maxNormX = max(westX, eastX)
      val minNormY = min(northY, southY)
      val maxNormY = max(northY, southY)
      val spanNormX = max(maxNormX - minNormX, 1e-7)
      val spanNormY = max(maxNormY - minNormY, 1e-7)
      val scaleX = canvasSize.width.toDouble() / spanNormX
      val scaleY = canvasSize.height.toDouble() / spanNormY
      return ThumbnailViewportProjection(
        canvasWidth = canvasSize.width,
        canvasHeight = canvasSize.height,
        centerNormX = (minNormX + maxNormX) / 2.0,
        centerNormY = (minNormY + maxNormY) / 2.0,
        worldCanvasPx = min(scaleX, scaleY),
      )
    }
  }
}

private fun DrawScope.drawCanopyBackdrop() {
  drawRect(color = CanopyBackdropFill)
  drawCircle(
    color = CanopyRidgePrimary,
    radius = size.maxDimension * 0.42f,
    center = Offset(size.width * 0.26f, size.height * 0.34f),
  )
  drawCircle(
    color = CanopyRidgeSecondary,
    radius = size.maxDimension * 0.36f,
    center = Offset(size.width * 0.74f, size.height * 0.70f),
  )
  val gridColor = AoiAccentMint.copy(alpha = 0.07f)
  val stepPx = max(28.dp.toPx(), min(size.width, size.height) / 3.5f)
  var x = stepPx
  while (x < size.width) {
    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    x += stepPx
  }
  var y = stepPx
  while (y < size.height) {
    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    y += stepPx
  }
}

private fun DrawScope.drawPolygonFeatures(
  features: List<SurveyThumbnailFeature>,
  projection: ThumbnailViewportProjection,
) {
  val minSpanPx = 7.dp.toPx()
  for (feature in features) {
    if (feature.type != GeometryType.POLYGON || feature.vertices.size < 3) continue
    val pts = ensureMinSpan(feature.vertices.map(projection::toOffset), minSpanPx)
    val featureColor = opaqueFeatureColor(feature.colorHex)
    val path = buildClosedPath(pts)
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
}

private fun DrawScope.drawLineFeatures(
  features: List<SurveyThumbnailFeature>,
  projection: ThumbnailViewportProjection,
) {
  val minSpanPx = 8.dp.toPx()
  for (feature in features) {
    if (feature.type != GeometryType.LINE_STRING || feature.vertices.size < 2) continue
    val pts = ensureMinSpan(feature.vertices.map(projection::toOffset), minSpanPx)
    val featureColor = opaqueFeatureColor(feature.colorHex)
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
}

private fun DrawScope.drawPointFeatures(
  features: List<SurveyThumbnailFeature>,
  projection: ThumbnailViewportProjection,
) {
  for (feature in features) {
    if (feature.type != GeometryType.POINT || feature.vertices.isEmpty()) continue
    val pt = projection.toOffset(feature.vertices.first())
    val featureColor = opaqueFeatureColor(feature.colorHex)
    drawCircle(
      color = Color.Black.copy(alpha = 0.35f),
      radius = 4.0.dp.toPx(),
      center = Offset(pt.x, pt.y + 0.6.dp.toPx()),
    )
    drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = pt)
    drawCircle(color = featureColor, radius = 2.3.dp.toPx(), center = pt)
  }
}

private fun DrawScope.drawFallbackLabels(
  thumbnail: SurveyThumbnail,
  projection: ThumbnailViewportProjection,
  textMeasurer: TextMeasurer,
) {
  val labelsToDraw =
    if (thumbnail.placeLabels.isNotEmpty()) {
      thumbnail.placeLabels.take(2)
    } else if (thumbnail.areaName.isNotBlank()) {
      listOf(SurveyThumbnailPlaceLabel(thumbnail.areaName, thumbnail.center))
    } else {
      emptyList()
    }
  val haloStyle =
    TextStyle(color = LabelHaloDark, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
  val textStyle =
    TextStyle(color = LabelTextLight, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
  val pad = 6.dp.toPx()
  val haloOffset = 1.dp.toPx()

  labelsToDraw.forEachIndexed { index, place ->
    val rawAnchor = projection.toOffset(place.position)
    val measured =
      textMeasurer.measure(text = place.name, style = textStyle, maxLines = 1, softWrap = false)
    val haloLayout =
      textMeasurer.measure(text = place.name, style = haloStyle, maxLines = 1, softWrap = false)
    val textW = measured.size.width.toFloat()
    val textH = measured.size.height.toFloat()
    val targetY =
      if (labelsToDraw.size == 1) {
        rawAnchor.y.coerceIn(
          pad + textH / 2f,
          max(pad + textH / 2f, size.height - pad - textH / 2f),
        )
      } else if (index == 0) {
        rawAnchor.y.coerceIn(pad + textH / 2f, max(pad + textH / 2f, size.height * 0.36f))
      } else {
        rawAnchor.y.coerceIn(
          min(size.height * 0.68f, size.height - pad - textH / 2f),
          max(pad + textH / 2f, size.height - pad - textH / 2f),
        )
      }
    val topLeftX = (rawAnchor.x - textW / 2f).coerceIn(pad, max(pad, size.width - textW - pad))
    val topLeftY = (targetY - textH / 2f).coerceIn(pad, max(pad, size.height - textH - pad))

    for (dx in listOf(-haloOffset, haloOffset)) {
      for (dy in listOf(-haloOffset, haloOffset)) {
        drawText(textLayoutResult = haloLayout, topLeft = Offset(topLeftX + dx, topLeftY + dy))
      }
    }
    drawText(textLayoutResult = measured, topLeft = Offset(topLeftX, topLeftY))
  }
}

private fun opaqueFeatureColor(colorHex: Long): Color =
  Color(0xFF000000L or (colorHex and 0xFFFFFFL))

private fun buildClosedPath(points: List<Offset>): Path =
  Path().apply {
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
      lineTo(points[i].x, points[i].y)
    }
    close()
  }

/** Ensures tiny polygon/line features remain legible when framed inside a wide regional AOI. */
private fun ensureMinSpan(points: List<Offset>, minSpanPx: Float): List<Offset> {
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
