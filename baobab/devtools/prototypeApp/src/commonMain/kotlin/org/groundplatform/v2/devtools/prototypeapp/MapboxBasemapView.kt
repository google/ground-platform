/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity

/**
 * Synchronizes the DOM `#mapbox-basemap-container` viewport rectangle with the Compose layout
 * bounds of [MapboxBasemapView] and calls `map.resize()` on the underlying `mapboxgl.Map` instance.
 */
internal expect fun syncPlatformMapboxViewport(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  visible: Boolean,
)

/**
 * Synchronizes the active survey camera coordinates, basemap style (`NORMAL` vs `SATELLITE`),
 * offline basemap tile visibility, pan offsets, user GPS location, and GeoJSON features with
 * `window.GroundMapboxBridge`.
 */
internal expect fun syncPlatformMapboxBasemap(
  surveyId: String,
  basemapType: String,
  isOfflineVisible: Boolean,
  panOffsetX: Float,
  panOffsetY: Float,
  userGpsX: Float,
  userGpsY: Float,
  featuresGeoJson: String,
)

/** Pans the platform `mapboxgl.Map` instance by `(dxPx, dyPx)` CSS pixels during drag gestures. */
internal expect fun panPlatformMapboxBasemap(dxPx: Float, dyPx: Float)

/** Hides `#mapbox-basemap-container` when [MapboxBasemapView] leaves the composition. */
internal expect fun hidePlatformMapboxBasemap()

/**
 * Real Mapbox GL JS (`mapboxgl.Map`) basemap view for `SurveyMapView`.
 *
 * Positions `#mapbox-basemap-container` at the exact window coordinates of `SurveyMapView`,
 * punches a transparent viewport (`BlendMode.Clear`) through the Compose Skia canvas so the live
 * `mapboxgl.Map` satellite/topographic tiles shine through, and synchronizes camera pan/zoom,
 * basemap style (`NORMAL` vs `SATELLITE`), offline basemap visibility, geospatial entities,
 * dotted form submission geometries, and the user's GPS blue dot with `window.GroundMapboxBridge`.
 */
@Composable
fun MapboxBasemapView(
  state: PrototypeAppState,
  animatedShiftX: Float = state.mapWorldToScreenShiftX,
  animatedShiftY: Float = state.mapWorldToScreenShiftY,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current.density.coerceAtLeast(1f)
  var viewportLeftCssPx by remember { mutableStateOf(0f) }
  var viewportTopCssPx by remember { mutableStateOf(0f) }
  var viewportWidthCssPx by remember { mutableStateOf(0f) }
  var viewportHeightCssPx by remember { mutableStateOf(0f) }

  val visibleEntities = state.visibleMapEntities
  val visibleSubGeometries = state.visibleSubmissionGeometries
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val isNormalBasemap = state.selectedBasemapType == BasemapType.NORMAL

  val featuresPayloadJson =
    remember(
      visibleEntities,
      visibleSubGeometries,
      selectedEntity?.id,
      selectedSubmission?.id,
    ) {
      buildMapboxFeaturesPayloadJson(
        entities = visibleEntities,
        submissions = visibleSubGeometries,
        selectedEntityId = selectedEntity?.id,
        selectedSubmissionId = selectedSubmission?.id,
      )
    }

  SideEffect {
    val isVisible = viewportWidthCssPx > 4f && viewportHeightCssPx > 4f
    syncPlatformMapboxViewport(
      leftPx = viewportLeftCssPx,
      topPx = viewportTopCssPx,
      widthPx = viewportWidthCssPx,
      heightPx = viewportHeightCssPx,
      borderRadiusPx = 0f,
      visible = isVisible,
    )
    if (isVisible) {
      syncPlatformMapboxBasemap(
        surveyId = state.activeSurveyId,
        basemapType = state.selectedBasemapType.name,
        isOfflineVisible = state.isOfflineBasemapVisible,
        panOffsetX = state.mapPanOffsetX,
        panOffsetY = state.mapPanOffsetY,
        userGpsX = state.userGpsNormalizedX,
        userGpsY = state.userGpsNormalizedY,
        featuresGeoJson = featuresPayloadJson,
      )
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      hidePlatformMapboxBasemap()
    }
  }

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .onGloballyPositioned { coordinates ->
          val pos = coordinates.positionInWindow()
          val sz = coordinates.size
          viewportLeftCssPx = pos.x / density
          viewportTopCssPx = pos.y / density
          viewportWidthCssPx = sz.width / density
          viewportHeightCssPx = sz.height / density
        }
        .pointerInput(viewportWidthCssPx, viewportHeightCssPx, density) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val widthPx = size.width.toFloat()
            val heightPx = size.height.toFloat()
            if (widthPx > 0f && heightPx > 0f) {
              state.panMap(
                deltaNormalizedX = dragAmount.x / widthPx,
                deltaNormalizedY = dragAmount.y / heightPx,
              )
              panPlatformMapboxBasemap(
                dxPx = dragAmount.x / density,
                dyPx = dragAmount.y / density,
              )
            }
          }
        }
        .clickable {
          state.updateLayersSheetOpen(false)
        }
  ) {
    // 1. Clear the Skia canvas pixels in the map viewport to rgba(0, 0, 0, 0) so the real
    // mapboxgl.Map instance inside #mapbox-basemap-container (z-index: 1) shines right through.
    drawRect(
      color = Color.Transparent,
      blendMode = BlendMode.Clear,
    )

    val shiftPxX = animatedShiftX * size.width
    val shiftPxY = animatedShiftY * size.height

    // 2. Render crisp interactive overlays aligned with the Mapbox GL GeoJSON sources/layers
    withTransform({ translate(left = shiftPxX, top = shiftPxY) }) {
      // Offline Basemap cached tile sector boundary (only rendered when Offline Basemap is ON)
      if (state.isOfflineBasemapVisible) {
        drawRect(
          color =
            if (isNormalBasemap) {
              Color(0xFF1E6F50).copy(alpha = 0.52f)
            } else {
              Color(0xFF8BD6B1).copy(alpha = 0.48f)
            },
          topLeft = Offset(size.width * 0.05f, size.height * 0.14f),
          size = Size(size.width * 0.90f, size.height * 0.74f),
          style =
            Stroke(
              width = 1.6f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
            ),
        )
      }

      // A. Solid Polygon / Point footprints for visible Geospatial Entities
      visibleEntities.forEach { entity ->
        val cx = size.width * entity.normalizedX
        val cy = size.height * entity.normalizedY
        val isSelected = selectedEntity?.id == entity.id
        val entityColor = Color(entity.colorHex)

        if (entity.geometryTypeLabel == "Polygon") {
          val polyWidth = size.width * 0.22f
          val polyHeight = size.height * 0.11f
          val polyPath =
            Path().apply {
              moveTo(cx - polyWidth * 0.48f, cy - polyHeight * 0.42f)
              lineTo(cx + polyWidth * 0.45f, cy - polyHeight * 0.50f)
              lineTo(cx + polyWidth * 0.52f, cy + polyHeight * 0.38f)
              lineTo(cx - polyWidth * 0.40f, cy + polyHeight * 0.48f)
              close()
            }
          drawPath(
            path = polyPath,
            color = entityColor.copy(alpha = if (isSelected) 0.42f else 0.24f),
          )
          drawPath(
            path = polyPath,
            color = if (isSelected && !isNormalBasemap) Color.White else entityColor,
            style = Stroke(width = if (isSelected) 3.8f else 2.2f),
          )
        } else {
          drawCircle(
            color = entityColor.copy(alpha = if (isSelected) 0.40f else 0.22f),
            radius = if (isSelected) 28f else 20f,
            center = Offset(cx, cy),
          )
        }
      }

      // B. Dotted Polygon Outlines for visible Form Submission Geometries
      val dottedStrokeEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
      visibleSubGeometries.forEach { subGeom ->
        val gx = size.width * subGeom.normalizedX
        val gy = size.height * subGeom.normalizedY
        val gw = size.width * subGeom.widthFraction
        val gh = size.height * subGeom.heightFraction
        val isSelectedSub = selectedSubmission?.id == subGeom.submissionId
        val geomColor = Color(subGeom.colorHex)

        val vertices =
          listOf(
            Offset(gx - gw * 0.46f, gy - gh * 0.38f),
            Offset(gx + gw * 0.08f, gy - gh * 0.52f),
            Offset(gx + gw * 0.50f, gy - gh * 0.26f),
            Offset(gx + gw * 0.44f, gy + gh * 0.42f),
            Offset(gx - gw * 0.12f, gy + gh * 0.50f),
            Offset(gx - gw * 0.48f, gy + gh * 0.24f),
          )
        val subPolyPath =
          Path().apply {
            moveTo(vertices.first().x, vertices.first().y)
            for (i in 1 until vertices.size) {
              lineTo(vertices[i].x, vertices[i].y)
            }
            close()
          }

        drawPath(
          path = subPolyPath,
          color = geomColor.copy(alpha = if (isSelectedSub) 0.28f else 0.14f),
        )
        drawPath(
          path = subPolyPath,
          color = if (isSelectedSub && !isNormalBasemap) Color.White else geomColor,
          style =
            Stroke(
              width = if (isSelectedSub) 3.4f else 2.5f,
              pathEffect = dottedStrokeEffect,
            ),
        )
        vertices.forEach { vertex ->
          drawCircle(
            color = if (isSelectedSub && !isNormalBasemap) Color.White else geomColor,
            radius = if (isSelectedSub) 3.6f else 2.6f,
            center = vertex,
          )
        }
      }

      // C. User Current GPS Location Blue Dot
      val blueDotCenter =
        Offset(
          x = size.width * state.userGpsNormalizedX,
          y = size.height * state.userGpsNormalizedY,
        )
      drawCircle(
        color = Color(0xFF4285F4).copy(alpha = 0.24f),
        radius = 26f,
        center = blueDotCenter,
      )
      drawCircle(
        color = Color(0xFF4285F4).copy(alpha = 0.50f),
        radius = 26f,
        center = blueDotCenter,
        style = Stroke(width = 1.2f),
      )
      drawCircle(color = Color.White, radius = 9.5f, center = blueDotCenter)
      drawCircle(color = Color(0xFF1A73E8), radius = 7f, center = blueDotCenter)
    }
  }
}

private fun buildMapboxFeaturesPayloadJson(
  entities: List<GeospatialEntityItem>,
  submissions: List<SubmissionGeometryPolygon>,
  selectedEntityId: String?,
  selectedSubmissionId: String?,
): String {
  val entitiesJson =
    entities.joinToString(separator = ",") { ent ->
      val hex = colorHexToCssString(ent.colorHex)
      val selected = ent.id == selectedEntityId
      """{"id":"${ent.id}","geometryType":"${ent.geometryTypeLabel}","nx":${ent.normalizedX},"ny":${ent.normalizedY},"color":"$hex","selected":$selected}"""
    }
  val submissionsJson =
    submissions.joinToString(separator = ",") { sub ->
      val hex = colorHexToCssString(sub.colorHex)
      val selected = sub.submissionId == selectedSubmissionId
      """{"id":"${sub.id}","submissionId":"${sub.submissionId}","nx":${sub.normalizedX},"ny":${sub.normalizedY},"wf":${sub.widthFraction},"hf":${sub.heightFraction},"color":"$hex","selected":$selected}"""
    }
  return """{"entities":[$entitiesJson],"submissions":[$submissionsJson]}"""
}

private fun colorHexToCssString(colorHex: Long): String {
  val rgb = (colorHex and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()
  return "#$rgb"
}
