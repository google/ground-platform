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
package org.groundplatform.v2.web.domain.model.editor

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import org.groundplatform.v2.app.domain.model.editor.SampleAreaSource
import org.groundplatform.v2.app.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.core.geo.Wgs84
import org.groundplatform.v2.core.sampling.SamplingArea

/**
 * Systematic grid spacing that suits the size of the area being sampled: larger areas get wider
 * spacing, so a new design starts at about [TARGET_PLOTS] plots whatever the area's size.
 *
 * Designs whose [SampleDesignConfig.gridSpacingAuto] is set keep following the suggestion as the
 * area changes (see [applyTo]); changing the spacing by hand stops that.
 */
object SuggestedGridSpacing {
  /** Roughly how many plots the suggested spacing gives. */
  const val TARGET_PLOTS = 100

  const val MIN_SPACING_M = 10.0
  const val MAX_SPACING_M = 50_000.0

  /**
   * Leading digits of the "nice" spacings suggestions are rounded to (e.g. 200 m, 250 m, 500 m).
   */
  private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

  /**
   * Suggested spacing in meters for an area of [areaM2] square meters, rounded to a nice number, or
   * `null` if the area is empty.
   */
  fun forAreaM2(areaM2: Double): Double? {
    if (!(areaM2 > 0) || !areaM2.isFinite()) return null
    return nice(sqrt(areaM2 / TARGET_PLOTS)).coerceIn(MIN_SPACING_M, MAX_SPACING_M)
  }

  /** Suggested spacing in meters for [area], or `null` if it's empty. */
  fun forArea(area: SamplingArea): Double? = forAreaM2(areaM2(area))

  /** Total area of [area]'s strata in square meters. */
  fun areaM2(area: SamplingArea): Double =
    area.strata.sumOf { stratum ->
      val ring = stratum.ring
      Wgs84.ringAreaM2(
        DoubleArray(ring.size) { ring[it].lat },
        DoubleArray(ring.size) { ring[it].lng },
      )
    }

  /**
   * [after] (the draft following an edit of [before]) with the grid spacing of generated layers
   * whose spacing follows their area set to the suggestion for that area. Returns [after] itself
   * when nothing changes.
   *
   * Layers that have plots already only follow their area when the area itself changed in this edit
   * or the organizer just switched the spacing back to following the area (not when they're first
   * added with plots, e.g. copied), so opening and editing an existing survey never changes a
   * design behind the organizer's back. Layers whose area isn't available keep their spacing.
   */
  fun applyTo(before: SurveyEditorDraft, after: SurveyEditorDraft): SurveyEditorDraft {
    var changed = false
    val datasets =
      after.datasets.map { dataset ->
        val config = dataset.generator
        if (config == null || !config.gridSpacingAuto) return@map dataset
        if (config.lastRun != null) {
          val previous = before.datasets.firstOrNull { it.key == dataset.key }?.generator
          if (
            previous == null ||
              previous.gridSpacingAuto &&
                previous.areaSource == config.areaSource &&
                areaGeometry(before, config) == areaGeometry(after, config)
          ) {
            return@map dataset
          }
        }
        val area =
          SamplingAreas.samplingArea(config, after.details.surveyArea, after.datasets)
            as? SamplingAreaResult.Ready ?: return@map dataset
        val spacing = forArea(area.area) ?: return@map dataset
        if (spacing == config.gridSpacingM) return@map dataset
        changed = true
        dataset.copy(generator = config.copy(gridSpacingM = spacing))
      }
    return if (changed) after.copy(datasets = datasets) else after
  }

  /** The geometry [config] samples from in [draft], for detecting changes to the area. */
  private fun areaGeometry(draft: SurveyEditorDraft, config: SampleDesignConfig): Any? =
    when (val source = config.areaSource) {
      SampleAreaSource.SurveyArea -> draft.details.surveyArea?.parts
      is SampleAreaSource.StrataLayer ->
        draft.datasets.firstOrNull { it.key == source.datasetKey }?.rows?.map { it.geometry }
    }

  /** [value] rounded (on a log scale) to the nearest 1, 2, 2.5, or 5 times a power of ten. */
  internal fun nice(value: Double): Double {
    val magnitude = 10.0.pow(floor(log10(value)))
    val leading = value / magnitude
    val step = NICE_STEPS.minBy { abs(ln(leading / it)) }
    return step * magnitude
  }
}
