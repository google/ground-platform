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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.GenerationRecord
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.core.sampling.GeneratedPlot
import org.openforis.collectone.v2.core.sampling.SampleEstimate
import org.openforis.collectone.v2.core.sampling.SamplingArea
import org.openforis.collectone.v2.core.sampling.SamplingEngine
import org.openforis.collectone.v2.core.sampling.SamplingException
import org.openforis.collectone.v2.web.domain.model.editor.SampleDesignInputs
import org.openforis.collectone.v2.web.domain.model.editor.SamplePlotRows
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreas
import org.openforis.collectone.v2.web.domain.model.editor.SamplingMessage

/**
 * Generates the sample plots of a generated Map layer from its sample design with a
 * [SamplingEngine] (by default [SamplingEngine.Default], resolved on first use).
 *
 * Plots are produced in chunks; between chunks [onProgress] is called and [yieldBetweenChunks]
 * runs, so single-threaded platforms (WasmJS) stay responsive. Cancelling the calling coroutine
 * stops generation at the next chunk boundary with a [CancellationException].
 */
class GenerateSamplePlotsUseCase(
  samplingEngine: SamplingEngine? = null,
  private val yieldBetweenChunks: suspend () -> Unit = { yield() },
  /** Dates each run ([GenerationRecord.generatedAt]). */
  private val clock: Clock = Clock.System,
) {
  private val engine: SamplingEngine by lazy { samplingEngine ?: SamplingEngine.Default }

  /** Outcome of [invoke]. */
  sealed interface Result {
    /** [dataset] with its new plots as rows and the run recorded in its design. */
    data class Generated(val dataset: EntityDataset, val plotCount: Int) : Result

    /** The design couldn't be generated; the layer's existing plots should be kept. */
    sealed interface Failure : Result

    /** Generation failed for the reason [message]. */
    data class Failed(val message: DomainMessage) : Failure

    /** The sampling engine can't satisfy the design, as its [explanation] (a sentence) says. */
    data class DesignRejected(val explanation: String) : Failure
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
  ): Result {
    val config = dataset.generator ?: return Result.Failed(SamplingMessage.NO_DESIGN.message())
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
        currentCoroutineContext().ensureActive()
        if (!chunks.hasNext()) break
        plots.addAll(chunks.next())
        onProgress(plots.size, expected)
        yieldBetweenChunks()
      }
      currentCoroutineContext().ensureActive()
    } catch (e: CancellationException) {
      throw e
    } catch (e: SamplingException) {
      // The design can't be satisfied (e.g. too many plots, or they can't fit that far apart).
      val message = e.message.orEmpty()
      return when {
        message.isBlank() -> Result.Failed(SamplingMessage.CANT_GENERATE_DESIGN.message())
        message.endsWith('.') -> Result.DesignRejected(message)
        else -> Result.DesignRejected("$message.")
      }
    } catch (e: Throwable) {
      return Result.Failed(
        e.message
          ?.takeIf { it.isNotBlank() }
          ?.let { SamplingMessage.GENERATE_FAILED_WITH_REASON.message(it) }
          ?: SamplingMessage.GENERATE_FAILED.message()
      )
    }
    if (plots.isEmpty()) {
      return Result.Failed(SamplingMessage.NO_PLOTS_FIT.message())
    }
    val generated =
      dataset.copy(
        rows = SamplePlotRows.toRows(dataset.key, plots, previous = dataset.rows),
        geometryType = config.geometryType,
        generator =
          config.copy(lastRun = GenerationRecord(isoUtc(clock.now()), inputHash, plots.size)),
      )
    return Result.Generated(
      SurveyEditorDerivation.normalizeSamplePlotDataset(generated),
      plots.size,
    )
  }

  private fun readyArea(
    config: SampleDesignConfig,
    surveyArea: SurveyArea?,
    datasets: List<EntityDataset>,
  ): SamplingArea? = (samplingArea(config, surveyArea, datasets) as? SamplingAreaResult.Ready)?.area
}
