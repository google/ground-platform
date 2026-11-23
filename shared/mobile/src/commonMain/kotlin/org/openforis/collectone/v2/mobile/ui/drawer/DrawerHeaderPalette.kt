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
package org.openforis.collectone.v2.mobile.ui.drawer

import androidx.compose.ui.graphics.Color

/**
 * Colors of the drawer header landscape painted by [DrawerHeaderBackground].
 *
 * The landscape is an illustration rather than themed UI, so its colors live here instead of in the
 * Material color scheme: [Light] and [Dark] hold one [DrawerHeaderColors] per app theme.
 */
internal object DrawerHeaderPalette {
  /** Pastel morning valley shown in the light theme. */
  val Light: DrawerHeaderColors =
    DrawerHeaderColors(
      skyGradient = listOf(Color(0xFFEDFAEE), Color(0xFFE2F6E3), Color(0xFFEDF6DA)),
      sunOuterGlow = Color(0xFFFEF08A).copy(alpha = 0.45f),
      sunInnerGlow = Color(0xFFFDE047).copy(alpha = 0.58f),
      sunDisc = Color(0xFFFFFBEB).copy(alpha = 0.92f),
      cloud = Color(0xFFFFFFFF).copy(alpha = 0.78f),
      bird = Color(0xFF6BA877).copy(alpha = 0.55f),
      farRange = Color(0xFFC5ECC8),
      midRidge = Color(0xFFB3E4B8),
      leftHillGradient = listOf(Color(0xFFA9DFB0), Color(0xFF98D6A0)),
      terrace = Color(0xFFD8F6DA).copy(alpha = 0.72f),
      rightHillGradient = listOf(Color(0xFFA0DBA8), Color(0xFF8FD098)),
      riverBank = Color(0xFFE6DEC0).copy(alpha = 0.85f),
      riverWater = Color(0xFF97D3DF),
      riverHighlight = Color(0xFFD9F4FA).copy(alpha = 0.85f),
      foregroundLeftHill = Color(0xFF88CC93),
      foregroundRightHill = Color(0xFF7FC68B),
      leftBroadleafTree =
        BroadleafTreeColors(
          trunk = Color(0xFF7CB886),
          shadow = Color(0xFF78BD85),
          mid = Color(0xFF88CA94),
          highlight = Color(0xFFA2DCAC),
        ),
      rightBroadleafTree =
        BroadleafTreeColors(
          trunk = Color(0xFF76B380),
          shadow = Color(0xFF73B880),
          mid = Color(0xFF83C68F),
          highlight = Color(0xFF9CD8A6),
        ),
      acaciaTree =
        AcaciaTreeColors(
          bark = Color(0xFF78B583),
          shadowCanopy = Color(0xFF6FB67C),
          midCanopy = Color(0xFF7DC289),
          lightCanopyLeft = Color(0xFF8FD09B),
          lightCanopyRight = Color(0xFF97D5A2),
          topCanopyHighlight = Color(0xFFAAE2B4),
        ),
      grassTuft = Color(0xFF6AB278),
    )

  /** Deep emerald twilight valley shown in the dark theme. */
  val Dark: DrawerHeaderColors =
    DrawerHeaderColors(
      skyGradient = listOf(Color(0xFF0A2011), Color(0xFF102E19), Color(0xFF163B20)),
      sunOuterGlow = Color(0xFFFDE68A).copy(alpha = 0.10f),
      sunInnerGlow = Color(0xFFFCD34D).copy(alpha = 0.16f),
      sunDisc = Color(0xFFFEF3C7).copy(alpha = 0.28f),
      cloud = Color(0xFFD8F3DC).copy(alpha = 0.10f),
      bird = Color(0xFF74B87E).copy(alpha = 0.35f),
      farRange = Color(0xFF14381F),
      midRidge = Color(0xFF11301A),
      leftHillGradient = listOf(Color(0xFF153A20), Color(0xFF0E2916)),
      terrace = Color(0xFF2D633B).copy(alpha = 0.32f),
      rightHillGradient = listOf(Color(0xFF13351D), Color(0xFF0C2313)),
      riverBank = Color(0xFF3E3B2C).copy(alpha = 0.55f),
      riverWater = Color(0xFF1D4E59),
      riverHighlight = Color(0xFF56A8BC).copy(alpha = 0.35f),
      foregroundLeftHill = Color(0xFF0B2112),
      foregroundRightHill = Color(0xFF091B0F),
      leftBroadleafTree =
        BroadleafTreeColors(
          trunk = Color(0xFF223324),
          shadow = Color(0xFF0E2916),
          mid = Color(0xFF153820),
          highlight = Color(0xFF1F4D2D),
        ),
      rightBroadleafTree =
        BroadleafTreeColors(
          trunk = Color(0xFF1E2E20),
          shadow = Color(0xFF0C2413),
          mid = Color(0xFF12321B),
          highlight = Color(0xFF1B4628),
        ),
      acaciaTree =
        AcaciaTreeColors(
          bark = Color(0xFF1F3323),
          shadowCanopy = Color(0xFF0E2A17),
          midCanopy = Color(0xFF14381F),
          lightCanopyLeft = Color(0xFF1C4A2A),
          lightCanopyRight = Color(0xFF215230),
          topCanopyHighlight = Color(0xFF29613A),
        ),
      grassTuft = Color(0xFF14361E),
    )

  /**
   * Base of the legibility veil over the landscape in the light theme; the dark theme veils with
   * the header's own scrim color instead.
   */
  val LightThemeVeil: Color = Color(0xFFE6FAEA)

  /** The palette for the light or dark app theme. */
  fun forTheme(isDarkTheme: Boolean): DrawerHeaderColors = if (isDarkTheme) Dark else Light
}

/** Colors of every element of the drawer header landscape, in one theme. */
internal data class DrawerHeaderColors(
  val skyGradient: List<Color>,
  val sunOuterGlow: Color,
  val sunInnerGlow: Color,
  val sunDisc: Color,
  val cloud: Color,
  val bird: Color,
  val farRange: Color,
  val midRidge: Color,
  val leftHillGradient: List<Color>,
  val terrace: Color,
  val rightHillGradient: List<Color>,
  val riverBank: Color,
  val riverWater: Color,
  val riverHighlight: Color,
  val foregroundLeftHill: Color,
  val foregroundRightHill: Color,
  val leftBroadleafTree: BroadleafTreeColors,
  val rightBroadleafTree: BroadleafTreeColors,
  val acaciaTree: AcaciaTreeColors,
  val grassTuft: Color,
)

/** Trunk and three canopy tones of a broadleaf tree in the drawer header landscape. */
internal data class BroadleafTreeColors(
  val trunk: Color,
  val shadow: Color,
  val mid: Color,
  val highlight: Color,
)

/** Bark and layered canopy tones of the savanna acacia in the drawer header landscape. */
internal data class AcaciaTreeColors(
  val bark: Color,
  val shadowCanopy: Color,
  val midCanopy: Color,
  val lightCanopyLeft: Color,
  val lightCanopyRight: Color,
  val topCanopyHighlight: Color,
)
