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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import openforis.collectone.v1.survey.TimeSeriesSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_time_series_band_blue
import org.openforis.collectone.v2.web.resources.web_time_series_band_blue_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_evi2_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_evi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_green
import org.openforis.collectone.v2.web.resources.web_time_series_band_green_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_group_backscatter
import org.openforis.collectone.v2.web.resources.web_time_series_band_group_indices
import org.openforis.collectone.v2.web.resources.web_time_series_band_group_reflectance
import org.openforis.collectone.v2.web.resources.web_time_series_band_nbr_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_ndfi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_ndmi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_ndvi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_ndwi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_nir
import org.openforis.collectone.v2.web.resources.web_time_series_band_nir_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_red
import org.openforis.collectone.v2.web.resources.web_time_series_band_red_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_rfdi_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_swir1
import org.openforis.collectone.v2.web.resources.web_time_series_band_swir1_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_swir2
import org.openforis.collectone.v2.web.resources.web_time_series_band_swir2_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_vh_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_vv_detail
import org.openforis.collectone.v2.web.resources.web_time_series_band_vv_vh_detail
import org.openforis.collectone.v2.web.resources.web_time_series_source_landsat
import org.openforis.collectone.v2.web.resources.web_time_series_source_landsat_detail
import org.openforis.collectone.v2.web.resources.web_time_series_source_sentinel1
import org.openforis.collectone.v2.web.resources.web_time_series_source_sentinel1_detail
import org.openforis.collectone.v2.web.resources.web_time_series_source_sentinel2
import org.openforis.collectone.v2.web.resources.web_time_series_source_sentinel2_detail

/** A color and the lighter tone that keeps it legible on dark themes. */
internal data class ThemedColor(val light: Color, val dark: Color) {
  /** The tone for the current theme. */
  @Composable
  fun current(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light
}

/** Sections of the band menu. */
internal enum class BandGroup(val heading: StringResource) {
  INDICES(Res.string.web_time_series_band_group_indices),
  REFLECTANCE(Res.string.web_time_series_band_group_reflectance),
  BACKSCATTER(Res.string.web_time_series_band_group_backscatter),
}

/**
 * How the "Time series & imagery" panel shows a band: its menu section, icon, color (also used for
 * its chart), and a one-line explanation. [name] is `null` for index acronyms such as NDVI, which
 * are shown untranslated.
 */
internal data class BandStyle(
  val group: BandGroup,
  val icon: ImageVector,
  val color: ThemedColor,
  val detail: StringResource?,
  val name: StringResource? = null,
)

/** How the panel shows an imagery source: name, icon, color, and resolution and revisit. */
internal data class SourceStyle(
  val name: StringResource,
  val detail: StringResource,
  val icon: ImageVector,
  val color: ThemedColor,
)

/**
 * Icons and colors of the panel's bands and imagery sources. Bands of one source have distinct
 * colors; reflectance bands take the color of their light, and indices a color that suggests what
 * they measure (greens for vegetation, blues for water, orange for fire).
 */
internal object TimeSeriesStyles {
  private val BANDS: Map<String, BandStyle> =
    mapOf(
      "NDVI" to
        index(
          Icons.Outlined.Eco,
          0xFF2E7D32,
          0xFF81C784,
          Res.string.web_time_series_band_ndvi_detail,
        ),
      "NDFI" to
        index(
          Icons.Outlined.Forest,
          0xFF00695C,
          0xFF4DB6AC,
          Res.string.web_time_series_band_ndfi_detail,
        ),
      "EVI" to
        index(
          Icons.Outlined.Spa,
          0xFF558B2F,
          0xFFAED581,
          Res.string.web_time_series_band_evi_detail,
        ),
      "EVI2" to
        index(
          Icons.Outlined.Grass,
          0xFF827717,
          0xFFDCE775,
          Res.string.web_time_series_band_evi2_detail,
        ),
      "NBR" to
        index(
          Icons.Outlined.LocalFireDepartment,
          0xFFD84315,
          0xFFFF8A65,
          Res.string.web_time_series_band_nbr_detail,
        ),
      "NDMI" to
        index(
          Icons.Outlined.WaterDrop,
          0xFF00838F,
          0xFF4DD0E1,
          Res.string.web_time_series_band_ndmi_detail,
        ),
      "NDWI" to
        index(
          Icons.Outlined.Waves,
          0xFF1565C0,
          0xFF64B5F6,
          Res.string.web_time_series_band_ndwi_detail,
        ),
      "BLUE" to
        reflectance(
          Icons.Outlined.LightMode,
          0xFF1E88E5,
          0xFF90CAF9,
          Res.string.web_time_series_band_blue,
          Res.string.web_time_series_band_blue_detail,
        ),
      "GREEN" to
        reflectance(
          Icons.Outlined.LightMode,
          0xFF43A047,
          0xFFA5D6A7,
          Res.string.web_time_series_band_green,
          Res.string.web_time_series_band_green_detail,
        ),
      "RED" to
        reflectance(
          Icons.Outlined.LightMode,
          0xFFE53935,
          0xFFEF9A9A,
          Res.string.web_time_series_band_red,
          Res.string.web_time_series_band_red_detail,
        ),
      "NIR" to
        reflectance(
          Icons.Outlined.Flare,
          0xFFAD1457,
          0xFFF48FB1,
          Res.string.web_time_series_band_nir,
          Res.string.web_time_series_band_nir_detail,
        ),
      "SWIR1" to
        reflectance(
          Icons.Outlined.Flare,
          0xFF8D6E63,
          0xFFD7CCC8,
          Res.string.web_time_series_band_swir1,
          Res.string.web_time_series_band_swir1_detail,
        ),
      "SWIR2" to
        reflectance(
          Icons.Outlined.Landscape,
          0xFF5D4037,
          0xFFBCAAA4,
          Res.string.web_time_series_band_swir2,
          Res.string.web_time_series_band_swir2_detail,
        ),
      "VV" to
        BandStyle(
          BandGroup.BACKSCATTER,
          Icons.Outlined.Terrain,
          ThemedColor(Color(0xFF1E88E5), Color(0xFF90CAF9)),
          Res.string.web_time_series_band_vv_detail,
        ),
      "VH" to
        BandStyle(
          BandGroup.BACKSCATTER,
          Icons.Outlined.Park,
          ThemedColor(Color(0xFF00897B), Color(0xFF80CBC4)),
          Res.string.web_time_series_band_vh_detail,
        ),
      "VV/VH" to
        index(
          Icons.Outlined.Functions,
          0xFF8E24AA,
          0xFFCE93D8,
          Res.string.web_time_series_band_vv_vh_detail,
        ),
      "RFDI" to
        index(
          Icons.Outlined.Forest,
          0xFF6D4C41,
          0xFFBCAAA4,
          Res.string.web_time_series_band_rfdi_detail,
        ),
    )

  /** Style of bands the panel doesn't know, so a new server band still shows up. */
  private val UNKNOWN_BAND =
    BandStyle(
      BandGroup.INDICES,
      Icons.Outlined.ShowChart,
      ThemedColor(Color(0xFF546E7A), Color(0xFFB0BEC5)),
      detail = null,
    )

  private val SOURCES: Map<TimeSeriesSource, SourceStyle> =
    mapOf(
      TimeSeriesSource.LANDSAT to
        SourceStyle(
          Res.string.web_time_series_source_landsat,
          Res.string.web_time_series_source_landsat_detail,
          Icons.Outlined.SatelliteAlt,
          ThemedColor(Color(0xFF3949AB), Color(0xFF9FA8DA)),
        ),
      TimeSeriesSource.SENTINEL_2 to
        SourceStyle(
          Res.string.web_time_series_source_sentinel2,
          Res.string.web_time_series_source_sentinel2_detail,
          Icons.Outlined.SatelliteAlt,
          ThemedColor(Color(0xFF7B1FA2), Color(0xFFCE93D8)),
        ),
      TimeSeriesSource.SENTINEL_1 to
        SourceStyle(
          Res.string.web_time_series_source_sentinel1,
          Res.string.web_time_series_source_sentinel1_detail,
          Icons.Outlined.Radar,
          ThemedColor(Color(0xFF546E7A), Color(0xFFB0BEC5)),
        ),
    )

  /** Whether [band] has its own style. */
  fun isKnown(band: String): Boolean = band in BANDS

  fun band(band: String): BandStyle = BANDS[band] ?: UNKNOWN_BAND

  fun source(source: TimeSeriesSource): SourceStyle =
    SOURCES[source] ?: SOURCES.getValue(TimeSeriesSource.LANDSAT)

  private fun index(icon: ImageVector, light: Long, dark: Long, detail: StringResource) =
    BandStyle(BandGroup.INDICES, icon, ThemedColor(Color(light), Color(dark)), detail)

  private fun reflectance(
    icon: ImageVector,
    light: Long,
    dark: Long,
    name: StringResource,
    detail: StringResource,
  ) = BandStyle(BandGroup.REFLECTANCE, icon, ThemedColor(Color(light), Color(dark)), detail, name)
}

/** Display name of [band]: translated for reflectance bands, the acronym for indices. */
@Composable
internal fun bandLabel(band: String): String =
  TimeSeriesStyles.band(band).name?.let { stringResource(it) } ?: band
