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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Yard
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_layer_icon_agriculture
import org.openforis.collectone.v2.app.resources.app_layer_icon_apartment
import org.openforis.collectone.v2.app.resources.app_layer_icon_cell_tower
import org.openforis.collectone.v2.app.resources.app_layer_icon_church
import org.openforis.collectone.v2.app.resources.app_layer_icon_circle
import org.openforis.collectone.v2.app.resources.app_layer_icon_construction
import org.openforis.collectone.v2.app.resources.app_layer_icon_cottage
import org.openforis.collectone.v2.app.resources.app_layer_icon_eco
import org.openforis.collectone.v2.app.resources.app_layer_icon_emoji_nature
import org.openforis.collectone.v2.app.resources.app_layer_icon_factory
import org.openforis.collectone.v2.app.resources.app_layer_icon_fence
import org.openforis.collectone.v2.app.resources.app_layer_icon_flag
import org.openforis.collectone.v2.app.resources.app_layer_icon_forest
import org.openforis.collectone.v2.app.resources.app_layer_icon_grass
import org.openforis.collectone.v2.app.resources.app_layer_icon_groups
import org.openforis.collectone.v2.app.resources.app_layer_icon_hiking
import org.openforis.collectone.v2.app.resources.app_layer_icon_home
import org.openforis.collectone.v2.app.resources.app_layer_icon_landscape
import org.openforis.collectone.v2.app.resources.app_layer_icon_local_fire_department
import org.openforis.collectone.v2.app.resources.app_layer_icon_local_hospital
import org.openforis.collectone.v2.app.resources.app_layer_icon_park
import org.openforis.collectone.v2.app.resources.app_layer_icon_person
import org.openforis.collectone.v2.app.resources.app_layer_icon_pets
import org.openforis.collectone.v2.app.resources.app_layer_icon_photo_camera
import org.openforis.collectone.v2.app.resources.app_layer_icon_place
import org.openforis.collectone.v2.app.resources.app_layer_icon_plumbing
import org.openforis.collectone.v2.app.resources.app_layer_icon_recycling
import org.openforis.collectone.v2.app.resources.app_layer_icon_school
import org.openforis.collectone.v2.app.resources.app_layer_icon_science
import org.openforis.collectone.v2.app.resources.app_layer_icon_sensors
import org.openforis.collectone.v2.app.resources.app_layer_icon_signpost
import org.openforis.collectone.v2.app.resources.app_layer_icon_solar_power
import org.openforis.collectone.v2.app.resources.app_layer_icon_star
import org.openforis.collectone.v2.app.resources.app_layer_icon_storefront
import org.openforis.collectone.v2.app.resources.app_layer_icon_terrain
import org.openforis.collectone.v2.app.resources.app_layer_icon_warning
import org.openforis.collectone.v2.app.resources.app_layer_icon_water
import org.openforis.collectone.v2.app.resources.app_layer_icon_water_drop
import org.openforis.collectone.v2.app.resources.app_layer_icon_waves
import org.openforis.collectone.v2.app.resources.app_layer_icon_yard

/**
 * An icon a Map layer can show inside its point pins.
 *
 * @property name stable identifier stored on the layer style (the Material Symbols name).
 * @property labelResource human-readable name shown in the Survey editor's icon picker.
 */
data class LayerIcon(
  val name: String,
  val labelResource: StringResource,
  val vector: ImageVector,
) {
  /** Localized human-readable name ([labelResource]). */
  val label: String
    @Composable get() = stringResource(labelResource)
}

/**
 * Curated library of pin icons suited to field surveys, from Material Icons (Google, Apache License
 * 2.0, https://github.com/google/material-design-icons) via the Compose `material-icons-core` and
 * `material-icons-extended` artifacts. The same [ImageVector]s are drawn by Compose (icon picker,
 * list rows, chips) and converted to SVG for map pins ([svgPathElements]), so all surfaces match.
 */
object LayerIcons {
  val all: List<LayerIcon> =
    listOf(
      LayerIcon("place", Res.string.app_layer_icon_place, Icons.Filled.Place),
      LayerIcon("flag", Res.string.app_layer_icon_flag, Icons.Filled.Flag),
      LayerIcon("star", Res.string.app_layer_icon_star, Icons.Filled.Star),
      LayerIcon("circle", Res.string.app_layer_icon_circle, Icons.Filled.Circle),
      LayerIcon("park", Res.string.app_layer_icon_park, Icons.Filled.Park),
      LayerIcon("forest", Res.string.app_layer_icon_forest, Icons.Filled.Forest),
      LayerIcon("eco", Res.string.app_layer_icon_eco, Icons.Filled.Eco),
      LayerIcon("grass", Res.string.app_layer_icon_grass, Icons.Filled.Grass),
      LayerIcon("agriculture", Res.string.app_layer_icon_agriculture, Icons.Filled.Agriculture),
      LayerIcon("yard", Res.string.app_layer_icon_yard, Icons.Filled.Yard),
      LayerIcon("emoji_nature", Res.string.app_layer_icon_emoji_nature, Icons.Filled.EmojiNature),
      LayerIcon("pets", Res.string.app_layer_icon_pets, Icons.Filled.Pets),
      LayerIcon("water_drop", Res.string.app_layer_icon_water_drop, Icons.Filled.WaterDrop),
      LayerIcon("water", Res.string.app_layer_icon_water, Icons.Filled.Water),
      LayerIcon("waves", Res.string.app_layer_icon_waves, Icons.Filled.Waves),
      LayerIcon("plumbing", Res.string.app_layer_icon_plumbing, Icons.Filled.Plumbing),
      LayerIcon("landscape", Res.string.app_layer_icon_landscape, Icons.Filled.Landscape),
      LayerIcon("terrain", Res.string.app_layer_icon_terrain, Icons.Filled.Terrain),
      LayerIcon("hiking", Res.string.app_layer_icon_hiking, Icons.Filled.Hiking),
      LayerIcon("signpost", Res.string.app_layer_icon_signpost, Icons.Filled.Signpost),
      LayerIcon("fence", Res.string.app_layer_icon_fence, Icons.Filled.Fence),
      LayerIcon("home", Res.string.app_layer_icon_home, Icons.Filled.Home),
      LayerIcon("cottage", Res.string.app_layer_icon_cottage, Icons.Filled.Cottage),
      LayerIcon("apartment", Res.string.app_layer_icon_apartment, Icons.Filled.Apartment),
      LayerIcon("school", Res.string.app_layer_icon_school, Icons.Filled.School),
      LayerIcon(
        "local_hospital",
        Res.string.app_layer_icon_local_hospital,
        Icons.Filled.LocalHospital,
      ),
      LayerIcon("church", Res.string.app_layer_icon_church, Icons.Filled.Church),
      LayerIcon("storefront", Res.string.app_layer_icon_storefront, Icons.Filled.Storefront),
      LayerIcon("factory", Res.string.app_layer_icon_factory, Icons.Filled.Factory),
      LayerIcon("construction", Res.string.app_layer_icon_construction, Icons.Filled.Construction),
      LayerIcon("solar_power", Res.string.app_layer_icon_solar_power, Icons.Filled.SolarPower),
      LayerIcon("cell_tower", Res.string.app_layer_icon_cell_tower, Icons.Filled.CellTower),
      LayerIcon("sensors", Res.string.app_layer_icon_sensors, Icons.Filled.Sensors),
      LayerIcon("photo_camera", Res.string.app_layer_icon_photo_camera, Icons.Filled.PhotoCamera),
      LayerIcon("science", Res.string.app_layer_icon_science, Icons.Filled.Science),
      LayerIcon("recycling", Res.string.app_layer_icon_recycling, Icons.Filled.Recycling),
      LayerIcon(
        "local_fire_department",
        Res.string.app_layer_icon_local_fire_department,
        Icons.Filled.LocalFireDepartment,
      ),
      LayerIcon("warning", Res.string.app_layer_icon_warning, Icons.Filled.Warning),
      LayerIcon("person", Res.string.app_layer_icon_person, Icons.Filled.Person),
      LayerIcon("groups", Res.string.app_layer_icon_groups, Icons.Filled.Groups),
    )

  private val byName = all.associateBy { it.name }

  /** The icon named [name], or `null` for a blank or unknown name (a plain pin). */
  fun forName(name: String?): LayerIcon? = name?.let { byName[it] }

  /**
   * SVG `<path>` elements drawing [icon] in its 24 × 24 viewport, filled with [fill]. Material
   * Icons have no group transforms, so nested groups are flattened.
   */
  fun svgPathElements(icon: LayerIcon, fill: String): String = buildString {
    fun visit(node: VectorNode) {
      when (node) {
        is VectorGroup -> node.forEach(::visit)
        is VectorPath -> {
          val rule = if (node.pathFillType == PathFillType.EvenOdd) "evenodd" else "nonzero"
          append("""<path d="${svgPathData(node.pathData)}" fill="$fill" fill-rule="$rule">""")
          append("</path>")
        }
      }
    }
    visit(icon.vector.root)
  }

  /** Converts Compose [PathNode]s into SVG path data (`d` attribute syntax). */
  fun svgPathData(nodes: List<PathNode>): String =
    nodes.joinToString(" ") { node ->
      when (node) {
        is PathNode.Close -> "Z"
        is PathNode.MoveTo -> "M${n(node.x)} ${n(node.y)}"
        is PathNode.RelativeMoveTo -> "m${n(node.dx)} ${n(node.dy)}"
        is PathNode.LineTo -> "L${n(node.x)} ${n(node.y)}"
        is PathNode.RelativeLineTo -> "l${n(node.dx)} ${n(node.dy)}"
        is PathNode.HorizontalTo -> "H${n(node.x)}"
        is PathNode.RelativeHorizontalTo -> "h${n(node.dx)}"
        is PathNode.VerticalTo -> "V${n(node.y)}"
        is PathNode.RelativeVerticalTo -> "v${n(node.dy)}"
        is PathNode.CurveTo ->
          "C${n(node.x1)} ${n(node.y1)} ${n(node.x2)} ${n(node.y2)} ${n(node.x3)} ${n(node.y3)}"
        is PathNode.RelativeCurveTo ->
          "c${n(node.dx1)} ${n(node.dy1)} ${n(node.dx2)} ${n(node.dy2)} " +
            "${n(node.dx3)} ${n(node.dy3)}"
        is PathNode.ReflectiveCurveTo -> "S${n(node.x1)} ${n(node.y1)} ${n(node.x2)} ${n(node.y2)}"
        is PathNode.RelativeReflectiveCurveTo ->
          "s${n(node.dx1)} ${n(node.dy1)} ${n(node.dx2)} ${n(node.dy2)}"
        is PathNode.QuadTo -> "Q${n(node.x1)} ${n(node.y1)} ${n(node.x2)} ${n(node.y2)}"
        is PathNode.RelativeQuadTo -> "q${n(node.dx1)} ${n(node.dy1)} ${n(node.dx2)} ${n(node.dy2)}"
        is PathNode.ReflectiveQuadTo -> "T${n(node.x)} ${n(node.y)}"
        is PathNode.RelativeReflectiveQuadTo -> "t${n(node.dx)} ${n(node.dy)}"
        is PathNode.ArcTo ->
          "A${n(node.horizontalEllipseRadius)} ${n(node.verticalEllipseRadius)} " +
            "${n(node.theta)} ${flag(node.isMoreThanHalf)} ${flag(node.isPositiveArc)} " +
            "${n(node.arcStartX)} ${n(node.arcStartY)}"
        is PathNode.RelativeArcTo ->
          "a${n(node.horizontalEllipseRadius)} ${n(node.verticalEllipseRadius)} " +
            "${n(node.theta)} ${flag(node.isMoreThanHalf)} ${flag(node.isPositiveArc)} " +
            "${n(node.arcStartDx)} ${n(node.arcStartDy)}"
      }
    }

  private fun flag(value: Boolean) = if (value) "1" else "0"

  /** Formats [v] with at most 3 decimals and no exponent, e.g. `-0.5`, `12`, `3.142`. */
  private fun n(v: Float): String {
    val millis = (v * 1000.0).roundToLong()
    val sign = if (millis < 0) "-" else ""
    val abs = kotlin.math.abs(millis)
    val whole = abs / 1000
    val frac = (abs % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) "$sign$whole" else "$sign$whole.$frac"
  }
}

/**
 * Color for icons and text drawn on top of [backgroundArgb] (e.g. a pin filled with a layer color):
 * white on dark and saturated colors, near-black on light colors.
 */
fun contentColorOnArgb(backgroundArgb: Long): Long {
  val r = ((backgroundArgb shr 16) and 0xFF) / 255.0
  val g = ((backgroundArgb shr 8) and 0xFF) / 255.0
  val b = (backgroundArgb and 0xFF) / 255.0
  // Rec. 709 luma; light enough backgrounds get dark content.
  val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
  return if (luma > 0.6) 0xFF1F1F1FL else 0xFFFFFFFFL
}
