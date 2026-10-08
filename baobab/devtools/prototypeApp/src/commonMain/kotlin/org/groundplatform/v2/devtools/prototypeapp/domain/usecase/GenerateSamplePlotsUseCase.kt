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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.yield
import org.groundplatform.v2.core.sampling.GeneratedPlot
import org.groundplatform.v2.core.sampling.SampleEstimate
import org.groundplatform.v2.core.sampling.SamplingArea
import org.groundplatform.v2.core.sampling.SamplingEngine
import org.groundplatform.v2.core.sampling.SamplingException
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GenerationRecord
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignInputs
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotRows
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplingAreaResult
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplingAreas
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind

/**
 * Generates the sample plots of a generated Map layer from its sample design with a
 * [SamplingEngine] (by default [SamplingEngine.Default], resolved on first use).
 *
 * Plots are produced in chunks; between chunks [onProgress] is called and [yieldBetweenChunks]
 * runs, so single-threaded platforms (WasmJS) stay responsive. Generation stops at the next chunk
 * boundary once [isCancelled] returns `true`.
 */
class GenerateSamplePlotsUseCase(
  samplingEngine: SamplingEngine? = null,
  private val yieldBetweenChunks: suspend () -> Unit = { yield() },
  private val now: () -> String = { "2026-01-01T00:00:00Z" },
) {
  private val engine: SamplingEngine by lazy { samplingEngine ?: SamplingEngine.Default }

  /** Outcome of [invoke]. */
  sealed interface Result {
    /** [dataset] with its new plots as rows and the run recorded in its design. */
    data class Generated(val dataset: EntityDataset, val plotCount: Int) : Result

    /** Stopped by the caller; the layer's existing plots should be kept. */
    data object Cancelled : Result

    /** The design couldn't be generated; the layer's existing plots should be kept. */
    data class Failed(val message: String) : Result
  }

  /** The area the design of [dataset] draws plots from, if it's available. */
  fun samplingArea(
    config: SampleDesignConfig,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): SamplingAreaResult = SamplingAreas.samplingArea(config, surveyArea, datasets)

  /**
   * Live estimate (area and approximate plot count) for generated layer [dataset], or `null` if the
   * area isn't available or the engine can't estimate.
   */
  fun estimate(
    dataset: EntityDataset,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): SampleEstimate? {
    val config = dataset.generator ?: return null
    val area = readyArea(config, surveyArea, datasets) ?: return null
    return try {
      engine.estimate(area, config.toSampleDesign())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // NotImplementedError while the default engine is being built, or bad parameters.
      null
    }
  }

  /**
   * Fingerprint of generated layer [dataset]'s current inputs (area and design), or `null` if the
   * area isn't available.
   */
  fun inputHash(
    dataset: EntityDataset,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): String? {
    val config = dataset.generator ?: return null
    val area = readyArea(config, surveyArea, datasets) ?: return null
    return SampleDesignInputs.hash(area, config)
  }

  /**
   * Generates the plots of [dataset] (a generated Map layer of a survey with [surveyArea] and
   * [datasets]), reporting `(plotsSoFar, expectedPlots)` to [onProgress].
   */
  suspend operator fun invoke(
    dataset: EntityDataset,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
    onProgress: (plotsSoFar: Int, expectedPlots: Int) -> Unit = { _, _ -> },
    isCancelled: () -> Boolean = { false },
  ): Result {
    val config = dataset.generator ?: return Result.Failed(NO_DESIGN)
    val area =
      when (val result = samplingArea(config, surveyArea, datasets)) {
        is SamplingAreaResult.Unavailable -> return Result.Failed(result.message)
        is SamplingAreaResult.Ready -> result.area
      }
    val design = config.toSampleDesign()
    val inputHash = SampleDesignInputs.hash(area, config)
    val expected =
      try {
        engine.estimate(area, design).estimatedPlotCount
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        0
      }
    val plots = ArrayList<GeneratedPlot>()
    onProgress(0, expected)
    try {
      yieldBetweenChunks()
      val chunks = engine.generateChunks(area, design).iterator()
      while (true) {
        if (isCancelled()) return Result.Cancelled
        if (!chunks.hasNext()) break
        plots.addAll(chunks.next())
        onProgress(plots.size, expected)
        yieldBetweenChunks()
      }
      if (isCancelled()) return Result.Cancelled
    } catch (e: CancellationException) {
      throw e
    } catch (e: SamplingException) {
      // The design can't be satisfied (e.g. too many plots, or they can't fit that far apart).
      val message = e.message.orEmpty().ifBlank { "This design can't be generated" }
      return Result.Failed(if (message.endsWith('.')) message else "$message.")
    } catch (e: Throwable) {
      return Result.Failed(
        e.message?.takeIf { it.isNotBlank() }?.let { "Couldn't generate sample plots: $it" }
          ?: "Couldn't generate sample plots. Check the design and try again."
      )
    }
    if (plots.isEmpty()) {
      return Result.Failed(
        "No sample plots fit in the area. Try a smaller spacing or plot size, or a larger area."
      )
    }
    return Result.Generated(
      dataset.copy(
        rows = SamplePlotRows.toRows(dataset.key, plots, previous = dataset.rows),
        geometryKind = config.geometryKind,
        generator = config.copy(lastRun = GenerationRecord(now(), inputHash, plots.size)),
      ),
      plots.size,
    )
  }

  private fun readyArea(
    config: SampleDesignConfig,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): SamplingArea? = (samplingArea(config, surveyArea, datasets) as? SamplingAreaResult.Ready)?.area

  companion object {
    const val NO_DESIGN = "This map layer doesn't have a sample design."
  }
}
