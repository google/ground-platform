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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.map.FeatureFilter
import org.openforis.collectone.v2.map.MapFeature
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.MarkerAnchor
import org.openforis.collectone.v2.map.StyleValue

/**
 * The part of [SurveyMapContent] that shows which map feature the mouse pointer is over (web
 * dashboard): a copy of the hovered feature, and the layers that draw it slightly emphasized on top
 * of the feature's own.
 */
internal object SurveyMapHover {
  const val KIND_POINT = "hover-point"
  const val KIND_LINE = "hover-line"
  const val KIND_POLYGON = "hover-polygon"

  /** The hover kind drawn over an entity feature of each kind; vertices and sub-plots have none. */
  private val hoverKinds =
    mapOf(
      SurveyMapContent.KIND_POINT to KIND_POINT,
      SurveyMapContent.KIND_LINE to KIND_LINE,
      SurveyMapContent.KIND_POLYGON to KIND_POLYGON,
    )

  /**
   * Copies of the hovered entity's pin, line, or polygon among [entityFeatures], for the overlay
   * source. That source is small, so moving the pointer from one feature to the next doesn't resend
   * every entity. The selected entity is already emphasized and gets none.
   */
  fun features(scene: SurveyMapScene, entityFeatures: List<MapFeature>): List<MapFeature> {
    val hovered = scene.hoveredEntityId?.takeIf { it != scene.selectedEntityId }
    if (hovered == null) return emptyList()
    val featureId = SurveyMapIds.entity(hovered)
    return entityFeatures.mapNotNull { feature ->
      val hoverKind = hoverKinds[feature.properties[SurveyMapContent.PROP_KIND]]
      if (feature.id != featureId || hoverKind == null) return@mapNotNull null
      feature.copy(properties = feature.properties + (SurveyMapContent.PROP_KIND to hoverKind))
    }
  }

  /**
   * A light wash and a slightly heavier outline over the hovered polygon or line: enough to show it
   * can be clicked, and clearly less than the selected style.
   */
  fun shapeLayers(strokeColor: StyleValue<Color>): List<MapLayer> =
    listOf(
      MapLayer.Fill(
        id = "entity-hover-fill",
        sourceId = SurveyMapContent.OVERLAY_SOURCE,
        filter = FeatureFilter.Equals(SurveyMapContent.PROP_KIND, KIND_POLYGON),
        color = StyleValue.Constant(Color.White),
        opacity = StyleValue.Constant(0.16f),
      ),
      MapLayer.Line(
        id = "entity-hover-outline",
        sourceId = SurveyMapContent.OVERLAY_SOURCE,
        filter = FeatureFilter.In(SurveyMapContent.PROP_KIND, setOf(KIND_LINE, KIND_POLYGON)),
        color = strokeColor,
        width = StyleValue.Match(SurveyMapContent.PROP_KIND, mapOf(KIND_LINE to 3.8.dp), 3.dp),
      ),
    )

  /** The hovered pin, drawn a little larger over its own; smaller than a selected pin. */
  fun pinLayer(pinIds: List<String>): MapLayer =
    MapLayer.Symbol(
      id = "entity-hover-pin",
      sourceId = SurveyMapContent.OVERLAY_SOURCE,
      filter = FeatureFilter.Equals(SurveyMapContent.PROP_KIND, KIND_POINT),
      iconId =
        StyleValue.Match(
          SurveyMapContent.PROP_PIN,
          pinIds.associateWith { it },
          pinIds.firstOrNull() ?: "",
        ),
      iconSize = StyleValue.Constant(1.66f),
      iconAnchor = MarkerAnchor.BOTTOM,
      iconOffset = DpOffset(0.dp, (AppPin.HEIGHT - AppPin.TIP_Y).toFloat().dp),
    )
}
