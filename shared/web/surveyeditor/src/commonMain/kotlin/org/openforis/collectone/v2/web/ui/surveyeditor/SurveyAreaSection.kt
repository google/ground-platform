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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.ui.map.SurveyBasemaps
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.map.AppMap
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.FeatureFilter
import org.openforis.collectone.v2.map.GeoJsonSource
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.map.MapCameraState
import org.openforis.collectone.v2.map.MapContent
import org.openforis.collectone.v2.map.MapFeature
import org.openforis.collectone.v2.map.MapInsets
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.StyleValue
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_default_location
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_edit
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_empty_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_empty_title
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_required
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_set
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_source
import org.openforis.collectone.v2.web.resources.surveyeditor_details_area_title
import org.openforis.collectone.v2.web.ui.common.ValidationErrorBanner

@Composable
fun SurveyAreaSection(
  uiState: SurveyEditorUiState,
  actions: SurveyDetailsActions,
  localPlaces: List<SurveyPlaceItem>,
  searchPlaces: PlaceSearch,
  isError: Boolean = !uiState.isReadOnly && uiState.detailsIssues.isNotEmpty(),
) {
  val area = uiState.details.surveyArea
  var showEditor by remember { mutableStateOf(false) }

  if (showEditor && !uiState.isReadOnly) {
    SurveyAreaEditorDialog(
      surveyId = uiState.details.surveyId,
      surveyLocationLabel =
        uiState.details.title.ifBlank {
          stringResource(Res.string.surveyeditor_details_area_default_location)
        },
      current = area,
      localPlaces = localPlaces,
      searchPlaces = searchPlaces,
      onSave = { newArea -> actions.setSurveyArea(newArea) },
      onDismiss = { showEditor = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          stringResource(Res.string.surveyeditor_details_area_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          stringResource(Res.string.surveyeditor_details_area_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (area != null) {
      OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Small static, non-pan/zoomable map thumbnail preview
          SurveyAreaThumbnail(
            area = area,
            modifier = Modifier.size(width = 160.dp, height = 120.dp),
          )

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = area.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            val summary = areaSummaryText(area)
            Text(
              text = summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                stringResource(Res.string.surveyeditor_details_area_source, area.source.text()),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!uiState.isReadOnly) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
              ) {
                OutlinedButton(
                  onClick = { showEditor = true },
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(Res.string.surveyeditor_details_area_edit))
                }
              }
            }
          }
        }
      }
    } else {
      val effectiveError = !uiState.isReadOnly && isError
      if (effectiveError) {
        ValidationErrorBanner(
          message = stringResource(Res.string.surveyeditor_details_area_required)
        )
      }
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border =
          if (effectiveError) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
          else CardDefaults.outlinedCardBorder(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(width = 160.dp, height = 100.dp)
                .background(
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Outlined.Map,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(36.dp),
            )
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(Res.string.surveyeditor_details_area_empty_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color =
                if (effectiveError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(Res.string.surveyeditor_details_area_empty_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!uiState.isReadOnly) {
              OutlinedButton(
                onClick = { showEditor = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 4.dp),
              ) {
                Icon(
                  Icons.Outlined.Map,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.surveyeditor_details_area_set))
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Small, static (not pan-able or zoom-able) map thumbnail showing a satellite basemap with the
 * survey area's parts and center point on top.
 */
@Composable
internal fun SurveyAreaThumbnail(area: SurveyArea, modifier: Modifier = Modifier) {
  val boundaryColor = MaterialTheme.colorScheme.primary
  val cameraState = remember(area) { MapCameraState(CameraPosition(area.center, area.zoom)) }
  val viewport = cameraState.viewportSize
  val viewportReady = viewport.width > 4.dp && viewport.height > 4.dp
  LaunchedEffect(area, viewportReady) {
    if (!viewportReady) return@LaunchedEffect
    val pts = area.allVertices.ifEmpty { listOf(area.center) }
    val lngSpan = pts.maxOf { it.longitude } - pts.minOf { it.longitude }
    // Bounds spanning > 180° longitude (e.g. across the antimeridian) would center on the wrong
    // side of the world, so anchor on the area's own center and zoom instead.
    val zoom =
      if (lngSpan >= 180.0) {
        area.zoom
      } else {
        val padding = MapInsets(16.dp, 16.dp, 16.dp, 16.dp)
        cameraState.fitBounds(LngLatBounds.of(pts), padding, durationMs = 0).zoom
      }
    cameraState.move(CameraPosition(area.center, zoom))
  }
  val content = remember(area, boundaryColor) { surveyAreaThumbnailContent(area, boundaryColor) }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(8.dp)),
    shape = RoundedCornerShape(8.dp),
    color = Color.Transparent,
    shadowElevation = 1.dp,
  ) {
    AppMap(
      content = content,
      cameraState = cameraState,
      onEvent = {},
      modifier = Modifier.fillMaxSize(),
      gesturesEnabled = false,
    )
  }
}

/** Vertex dots are only drawn when the simplified area has at most this many vertices. */
internal const val THUMBNAIL_MAX_VERTEX_DOTS = 200

/**
 * The survey area's parts (fill, outline and, for simple areas, vertices) and center over a
 * satellite basemap. Parts are simplified for display, so large uploaded boundaries stay cheap.
 */
internal fun surveyAreaThumbnailContent(area: SurveyArea, boundaryColor: Color): MapContent {
  val src = "survey-area"
  val parts = SurveyAreaGeometry.displayParts(area.parts).filter { it.size >= 3 }
  val showVertices = parts.sumOf { it.size } <= THUMBNAIL_MAX_VERTEX_DOTS
  val features = buildList {
    parts.forEachIndexed { i, part -> add(MapFeature("part-$i", Geometry.Polygon(listOf(part)))) }
    if (showVertices) {
      var v = 0
      parts.forEach { part ->
        part.forEach { p ->
          add(MapFeature("vertex-${v++}", Geometry.Point(p), mapOf("kind" to "vertex")))
        }
      }
    }
    add(MapFeature("center", Geometry.Point(area.center), mapOf("kind" to "center")))
  }
  return MapContent(
    basemap = SurveyBasemaps.Streets,
    sources = listOf(GeoJsonSource(src, features)),
    layers =
      listOf(
        MapLayer.Fill(
          id = "survey-area-fill",
          sourceId = src,
          color = StyleValue.Constant(boundaryColor),
          opacity = StyleValue.Constant(0.28f),
        ),
        MapLayer.Line(
          id = "survey-area-line",
          sourceId = src,
          filter = FeatureFilter.GeometryTypeIs(GeometryType.POLYGON),
          color = StyleValue.Constant(boundaryColor),
          width = StyleValue.Constant(2.5.dp),
        ),
        MapLayer.Circle(
          id = "survey-area-vertices",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "vertex"),
          color = StyleValue.Constant(Color.White),
          radius = StyleValue.Constant(3.5.dp),
          strokeColor = StyleValue.Constant(boundaryColor),
          strokeWidth = 1.5.dp,
        ),
        MapLayer.Circle(
          id = "survey-area-center",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "center"),
          color = StyleValue.Constant(boundaryColor),
          radius = StyleValue.Constant(4.dp),
          strokeColor = StyleValue.Constant(Color.White),
          strokeWidth = 1.dp,
        ),
      ),
  )
}
