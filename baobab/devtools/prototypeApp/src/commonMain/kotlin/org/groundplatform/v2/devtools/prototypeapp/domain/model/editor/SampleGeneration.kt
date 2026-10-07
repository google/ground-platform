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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import org.groundplatform.v2.core.sampling.Allocation
import org.groundplatform.v2.core.sampling.GeneratedPlot
import org.groundplatform.v2.core.sampling.PlotLayout
import org.groundplatform.v2.core.sampling.SAMPLING_ENGINE_VERSION
import org.groundplatform.v2.core.sampling.SampleDesign
import org.groundplatform.v2.core.sampling.SampleEncoding
import org.groundplatform.v2.core.sampling.SamplingArea
import org.groundplatform.v2.core.sampling.Stratum

/** Progress of a running sample generation, for the progress bar. */
data class SampleGenerationProgress(
  val datasetKey: String,
  val plotsSoFar: Int,
  /** Estimated total, or 0 if unknown. */
  val expectedPlots: Int,
) {
  /** Fraction done (0–1), or `null` when the total isn't known. */
  val fraction: Float?
    get() =
      if (expectedPlots <= 0) null
      else (plotsSoFar.toFloat() / maxOf(expectedPlots, plotsSoFar)).coerceIn(0f, 1f)
}

/** Result of sample plot generation. */
sealed interface SampleGenerationOutcome {
  data class Generated(val plotCount: Int) : SampleGenerationOutcome

  /** Stopped by a cancel request; existing plots are unchanged. */
  data object Cancelled : SampleGenerationOutcome

  /** The design couldn't be generated; existing plots are unchanged. */
  data class Failed(val message: String) : SampleGenerationOutcome

  /** Regeneration isn't allowed, e.g. because submissions reference the plots. */
  data class Blocked(val message: String) : SampleGenerationOutcome
}

/** The area a design is drawn from, or why it isn't available. */
sealed interface SamplingAreaResult {
  data class Ready(val area: SamplingArea) : SamplingAreaResult

  data class Unavailable(val message: String) : SamplingAreaResult
}

/** Stable fingerprint of a design's inputs, used to detect out-of-date sample plots. */
object SampleDesignInputs {

  /**
   * Hash of everything that determines the generated plots: engine version, the active design
   * parameters, the area source, and every coordinate of the sampling area. Parameters of other
   * methods (kept for convenience in [SampleDesignConfig]) don't count.
   */
  fun hash(area: SamplingArea, config: SampleDesignConfig): String {
    val h = Fnv64()
    h.update(SAMPLING_ENGINE_VERSION.toLong())
    h.update(canonical(config.toSampleDesign()))
    h.update(config.areaSource.toString())
    for (stratum in area.strata) {
      h.update(stratum.id)
      h.update(stratum.ring.size.toLong())
      for (c in stratum.ring) {
        h.update(c.lat.toRawBits())
        h.update(c.lng.toRawBits())
      }
    }
    return h.hex()
  }

  /** [design] as text with custom allocations sorted, so map order doesn't matter. */
  private fun canonical(design: SampleDesign): String {
    val layout = design.layout
    val sorted =
      if (layout is PlotLayout.StratifiedRandom && layout.allocation is Allocation.Custom) {
        val counts = (layout.allocation as Allocation.Custom).countsByStratum
        design.copy(
          layout =
            layout.copy(
              allocation =
                Allocation.Custom(
                  counts.entries.sortedBy { it.key }.associate { it.key to it.value }
                )
            )
        )
      } else {
        design
      }
    return sorted.toString()
  }

  /** 64-bit FNV-1a; small, fast and identical on every platform. */
  private class Fnv64 {
    private var value = -0x340d631b7bdddcdbL // 14695981039346656037

    fun update(v: Long) {
      var x = v
      repeat(8) {
        value = (value xor (x and 0xFF)) * 0x100000001b3L
        x = x ushr 8
      }
    }

    fun update(s: String) {
      update(s.length.toLong())
      for (ch in s) update(ch.code.toLong())
    }

    fun hex(): String = value.toULong().toString(16).padStart(16, '0')
  }
}

/** Converts engine plots to Map layer rows. */
internal object SamplePlotRows {

  /**
   * Rows for [plots] in dataset [datasetKey]. Row keys derive from the plot ID, so they're stable
   * across regenerations. Values the organizer entered in non-reserved columns of [previous] rows
   * are kept for plots with the same plot ID.
   */
  fun toRows(
    datasetKey: String,
    plots: List<GeneratedPlot>,
    previous: List<EntityRow> = emptyList(),
  ): List<EntityRow> {
    val carried =
      previous
        .mapNotNull { row ->
          val id = row.values[SamplePlotProperties.PLOT_ID] ?: return@mapNotNull null
          val extra = row.values.filterKeys { it !in SamplePlotProperties.all }
          if (extra.isEmpty()) null else id to extra
        }
        .toMap()
    return plots.map { plot ->
      val values = buildMap {
        carried[plot.plotId]?.let { putAll(it) }
        put(SamplePlotProperties.PLOT_ID, plot.plotId)
        put(SamplePlotProperties.STRATUM, plot.stratum)
        put(SamplePlotProperties.SAMPLE_ORDER, plot.sampleOrder.toString())
        put(SamplePlotProperties.INCLUSION_WEIGHT, formatFixed(plot.inclusionWeightHa, 4))
        put(SamplePlotProperties.CLUSTER_ID, plot.clusterId)
        put(SamplePlotProperties.SAMPLES, SampleEncoding.encodeSamples(plot.samples))
        put(
          SamplePlotProperties.SAMPLE_IDS,
          SampleEncoding.encodeSampleIds(SampleEncoding.defaultSampleIds(plot.samples.size)),
        )
      }
      val boundary = plot.boundary.map { it.toLatLng() }
      val geometry =
        when {
          boundary.isEmpty() -> listOf(plot.center.toLatLng())
          boundary.size > 1 && boundary.first() == boundary.last() -> boundary.dropLast(1)
          else -> boundary
        }
      EntityRow(key = "$datasetKey-${plot.plotId}", values = values, geometry = geometry)
    }
  }
}

/** Formats epoch milliseconds as an ISO 8601 UTC timestamp (`2026-10-05T16:34:41Z`). */
internal fun isoUtc(epochMillis: Long): String {
  val totalSeconds = epochMillis.floorDiv(1000L)
  val days = totalSeconds.floorDiv(86_400L)
  val secondsOfDay = totalSeconds - days * 86_400L
  // Civil-from-days (H. Hinnant, public domain algorithm).
  val z = days + 719_468
  val era = z.floorDiv(146_097L)
  val doe = z - era * 146_097
  val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val day = doy - (153 * mp + 2) / 5 + 1
  val month = if (mp < 10) mp + 3 else mp - 9
  val year = yoe + era * 400 + if (month <= 2) 1 else 0
  fun two(v: Long) = v.toString().padStart(2, '0')
  val h = secondsOfDay / 3600
  val m = (secondsOfDay % 3600) / 60
  val s = secondsOfDay % 60
  return "$year-${two(month)}-${two(day)}T${two(h)}:${two(m)}:${two(s)}Z"
}

/** Resolves the area a sample design draws plots from out of the survey's draft. */
object SamplingAreas {

  /** The area [config] draws plots from, given the survey's [surveyArea] and [datasets]. */
  fun samplingArea(
    config: SampleDesignConfig,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): SamplingAreaResult =
    when (val source = config.areaSource) {
      SampleAreaSource.SurveyArea -> {
        val parts = surveyArea?.parts.orEmpty().filter { it.size >= 3 }
        if (parts.isEmpty()) {
          SamplingAreaResult.Unavailable(
            "Set a survey area in Survey details, or use a polygon map layer as strata."
          )
        } else {
          SamplingAreaResult.Ready(
            SamplingArea.fromSurveyArea(parts.map { part -> part.map { it.toGeoCoord() } })
          )
        }
      }
      is SampleAreaSource.StrataLayer -> {
        val layer = datasets.firstOrNull { it.key == source.datasetKey }
        val polygons = layer?.rows.orEmpty().filter { it.geometry.size >= 3 }
        when {
          layer == null ->
            SamplingAreaResult.Unavailable("The strata map layer was deleted. Choose another one.")
          layer.geometryKind != GeometryKind.POLYGON ->
            SamplingAreaResult.Unavailable("Strata must come from a polygon map layer.")
          polygons.isEmpty() ->
            SamplingAreaResult.Unavailable(
              "\"${layer.displayName}\" has no polygons yet. Add polygons to use it as strata."
            )
          else ->
            SamplingAreaResult.Ready(
              SamplingArea(
                polygons.map { row ->
                  Stratum(
                    id = stratumId(layer, row, source.stratumProperty),
                    ring = row.geometry.map { it.toGeoCoord() },
                  )
                }
              )
            )
        }
      }
    }

  /** Distinct stratum IDs of [config]'s strata layer, in order of first appearance. */
  fun strataIds(config: SampleDesignConfig, datasets: List<EntityDataset>): List<String> {
    val source = config.areaSource as? SampleAreaSource.StrataLayer ?: return emptyList()
    val layer = datasets.firstOrNull { it.key == source.datasetKey } ?: return emptyList()
    return layer.rows
      .filter { it.geometry.size >= 3 }
      .map { stratumId(layer, it, source.stratumProperty) }
      .distinct()
  }

  private fun stratumId(layer: EntityDataset, row: EntityRow, property: String): String =
    row.values[property]?.trim()?.takeIf { it.isNotEmpty() }
      ?: row.values[layer.keyProperty]?.trim()?.takeIf { it.isNotEmpty() }
      ?: row.key
}
