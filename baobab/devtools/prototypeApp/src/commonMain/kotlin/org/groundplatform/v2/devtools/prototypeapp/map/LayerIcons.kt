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
package org.groundplatform.v2.devtools.prototypeapp.map

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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.math.roundToLong

/**
 * An icon a Map layer can show inside its point pins.
 *
 * @property name stable identifier stored on the layer style (the Material Symbols name).
 * @property label human-readable name shown in the Survey editor's icon picker.
 */
internal data class LayerIcon(val name: String, val label: String, val vector: ImageVector)

/**
 * Curated library of pin icons suited to field surveys, from Material Icons (Google, Apache License
 * 2.0, https://github.com/google/material-design-icons) via the Compose `material-icons-core` and
 * `material-icons-extended` artifacts. The same [ImageVector]s are drawn by Compose (icon picker,
 * list rows, chips) and converted to SVG for map pins ([svgPathElements]), so all surfaces match.
 */
internal object LayerIcons {
  val all: List<LayerIcon> =
    listOf(
      LayerIcon("place", "Place", Icons.Filled.Place),
      LayerIcon("flag", "Flag", Icons.Filled.Flag),
      LayerIcon("star", "Star", Icons.Filled.Star),
      LayerIcon("circle", "Circle", Icons.Filled.Circle),
      LayerIcon("park", "Tree", Icons.Filled.Park),
      LayerIcon("forest", "Forest", Icons.Filled.Forest),
      LayerIcon("eco", "Leaf", Icons.Filled.Eco),
      LayerIcon("grass", "Grass", Icons.Filled.Grass),
      LayerIcon("agriculture", "Agriculture", Icons.Filled.Agriculture),
      LayerIcon("yard", "Garden", Icons.Filled.Yard),
      LayerIcon("emoji_nature", "Wildlife", Icons.Filled.EmojiNature),
      LayerIcon("pets", "Animals", Icons.Filled.Pets),
      LayerIcon("water_drop", "Water drop", Icons.Filled.WaterDrop),
      LayerIcon("water", "Water", Icons.Filled.Water),
      LayerIcon("waves", "Waves", Icons.Filled.Waves),
      LayerIcon("plumbing", "Well / pump", Icons.Filled.Plumbing),
      LayerIcon("landscape", "Landscape", Icons.Filled.Landscape),
      LayerIcon("terrain", "Terrain", Icons.Filled.Terrain),
      LayerIcon("hiking", "Trail", Icons.Filled.Hiking),
      LayerIcon("signpost", "Signpost", Icons.Filled.Signpost),
      LayerIcon("fence", "Fence", Icons.Filled.Fence),
      LayerIcon("home", "Home", Icons.Filled.Home),
      LayerIcon("cottage", "Cottage", Icons.Filled.Cottage),
      LayerIcon("apartment", "Building", Icons.Filled.Apartment),
      LayerIcon("school", "School", Icons.Filled.School),
      LayerIcon("local_hospital", "Health facility", Icons.Filled.LocalHospital),
      LayerIcon("church", "Place of worship", Icons.Filled.Church),
      LayerIcon("storefront", "Market", Icons.Filled.Storefront),
      LayerIcon("factory", "Factory", Icons.Filled.Factory),
      LayerIcon("construction", "Construction", Icons.Filled.Construction),
      LayerIcon("solar_power", "Solar power", Icons.Filled.SolarPower),
      LayerIcon("cell_tower", "Cell tower", Icons.Filled.CellTower),
      LayerIcon("sensors", "Sensor", Icons.Filled.Sensors),
      LayerIcon("photo_camera", "Camera", Icons.Filled.PhotoCamera),
      LayerIcon("science", "Sample", Icons.Filled.Science),
      LayerIcon("recycling", "Recycling", Icons.Filled.Recycling),
      LayerIcon("local_fire_department", "Fire", Icons.Filled.LocalFireDepartment),
      LayerIcon("warning", "Hazard", Icons.Filled.Warning),
      LayerIcon("person", "Person", Icons.Filled.Person),
      LayerIcon("groups", "Group", Icons.Filled.Groups),
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
internal fun contentColorOnArgb(backgroundArgb: Long): Long {
  val r = ((backgroundArgb shr 16) and 0xFF) / 255.0
  val g = ((backgroundArgb shr 8) and 0xFF) / 255.0
  val b = (backgroundArgb and 0xFF) / 255.0
  // Rec. 709 luma; light enough backgrounds get dark content.
  val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
  return if (luma > 0.6) 0xFF1F1F1FL else 0xFFFFFFFFL
}
