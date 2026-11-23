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

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.DatasetIds
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.SampleAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.core.sampling.SampleEstimate
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.SampleGenerationProgress
import org.openforis.collectone.v2.web.domain.model.editor.SampleQuotaPolicy
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreas
import org.openforis.collectone.v2.web.domain.model.editor.SamplingMessage
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_already_generating
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_layer_missing
import org.openforis.collectone.v2.web.ui.text.EditorText
import org.openforis.collectone.v2.web.ui.text.toEditorText

/**
 * [SamplePlotActions] of the Survey editor: sample plot layers, their designs, and generating their
 * plots with [generateSamplePlots]. Progress and failures go to [status]; [organizations] supplies
 * the quota limits a generated sample must stay within.
 */
internal class SurveySamplePlotHandler(
  private val session: SurveyDraftSession,
  private val generateSamplePlots: GenerateSamplePlotsUseCase,
  private val scope: CoroutineScope,
  private val status: SampleGenerationStatus,
  private val organizations: () -> List<Organization>,
) : SamplePlotActions {
  /** The running sample generation, which [cancelGeneration] cancels. */
  private var generationJob: Job? = null

  override fun addSamplePlotsLayer(): String {
    var key = ""
    session.edit {
      val title = FormDatasetLinks.uniqueTitle("Sample plots", datasets.map { it.displayName })
      val strata = mapLayers.firstOrNull {
        it.geometryType == GeometryType.POLYGON &&
          !it.isGenerated &&
          it.linkedFormKey == null &&
          it.rows.isNotEmpty() &&
          forms.none { f ->
            f.form.saveTo.mode == SaveToMode.UPDATE && f.form.saveTo.targetDatasetId == it.id
          }
      }
      val design =
        if (!details.surveyArea?.parts.isNullOrEmpty() || strata == null) {
          SampleDesignConfig()
        } else {
          SampleDesignConfig(
            method = SampleMethod.STRATIFIED_RANDOM,
            areaSource = SampleAreaSource.StrataLayer(strata.key, strata.labelProperty),
          )
        }
      key = newKey("d")
      val dataset =
        EntityDataset(
          key = key,
          kind = DatasetKind.MAP_LAYER,
          id = DatasetIds.forName(title, datasets.map { it.id }),
          displayName = title,
          description = "Sample plots generated from a statistical sample design.",
          geometryType = design.geometryType,
          keyProperty = SamplePlotProperties.PLOT_ID,
          labelProperty = SamplePlotProperties.PLOT_ID,
          properties = SamplePlotProperties.schema,
          style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
          generator = design,
        )
      datasets = datasets + dataset
      section = SurveyEditorSection.Dataset(key)
    }
    return key
  }

  override fun updateSampleDesign(
    key: String,
    transform: (SampleDesignConfig) -> SampleDesignConfig,
  ) =
    session.updateDataset(key) { d ->
      d.generator?.let { config ->
        val next = transform(config)
        // A spacing set by hand no longer follows the area's size.
        val manualSpacing =
          next.gridSpacingM != config.gridSpacingM && next.gridSpacingAuto == config.gridSpacingAuto
        d.copy(generator = if (manualSpacing) next.copy(gridSpacingAuto = false) else next)
      } ?: d
    }

  override fun rerollSeed(key: String) =
    updateSampleDesign(key) { it.copy(seed = Random.nextLong(1, 1_000_000)) }

  override fun samplingArea(config: SampleDesignConfig): SamplingAreaResult =
    SamplingAreas.samplingArea(config, session.draft.details.surveyArea, session.draft.datasets)

  override fun strataIds(config: SampleDesignConfig): List<String> =
    SamplingAreas.strataIds(config, session.draft.datasets)

  override fun estimateSample(key: String): SampleEstimate? {
    val dataset = session.draft.datasets.firstOrNull { it.key == key } ?: return null
    return generateSamplePlots.estimate(
      dataset,
      session.draft.details.surveyArea,
      session.draft.datasets,
    )
  }

  private class HashCacheEntry(
    val areaRef: Any?,
    val property: String?,
    val design: SampleDesignConfig,
    val hash: String?,
  )

  /** Per-dataset cache so the hash is only recomputed when its inputs change (by identity). */
  private val hashCache = mutableMapOf<String, HashCacheEntry>()

  /**
   * Fingerprint of generated layer [dataset]'s current inputs (area and design), or `null` if the
   * area isn't available. Cached, so calling it on every recomposition is cheap.
   */
  fun currentInputHash(dataset: EntityDataset): String? {
    val config = dataset.generator ?: return null
    val design = config.withoutProvenance()
    val current = session.draft
    val (areaRef, property) =
      when (val source = config.areaSource) {
        SampleAreaSource.SurveyArea -> current.details.surveyArea?.parts to null
        is SampleAreaSource.StrataLayer ->
          current.datasets.firstOrNull { it.key == source.datasetKey }?.rows to
            source.stratumProperty
      }
    hashCache[dataset.key]?.let { c ->
      if (c.areaRef === areaRef && c.property == property && c.design == design) return c.hash
    }
    val hash = generateSamplePlots.inputHash(dataset, current.details.surveyArea, current.datasets)
    hashCache[dataset.key] = HashCacheEntry(areaRef, property, design, hash)
    return hash
  }

  override fun isDesignStale(dataset: EntityDataset): Boolean {
    val lastRun = dataset.generator?.lastRun ?: return false
    return currentInputHash(dataset) != lastRun.inputHash
  }

  /**
   * Whether generated layer [dataset] should be (re)generated before its design is saved: it has no
   * plots or no recorded run, or its area or design changed since the last run. Layers whose
   * regeneration is blocked (e.g. plots already have submissions) or whose sampling area isn't
   * available are left alone.
   */
  internal fun needsSampleGeneration(dataset: EntityDataset): Boolean {
    val config = dataset.generator ?: return false
    if (session.uiState.regenerateBlockedReason(dataset) != null) return false
    if (samplingArea(config) !is SamplingAreaResult.Ready) return false
    return dataset.rows.isEmpty() || config.lastRun == null || isDesignStale(dataset)
  }

  /**
   * Generates, one layer after another, every layer of the draft that [needsSampleGeneration]; run
   * when saving so a published survey never carries stale plots.
   */
  internal suspend fun generatePendingSamplePlots() {
    val keys = session.draft.datasets.filter(::needsSampleGeneration).map { it.key }
    for (key in keys) {
      generateSample(key)
    }
  }

  override fun generateSamplePlots(datasetKey: String) {
    scope.launch { generateSample(datasetKey) }
  }

  /**
   * Generates the sample plots of layer [datasetKey] from its design, replacing its features.
   *
   * Plots are produced in chunks; between chunks progress is published in
   * [SurveyEditorUiState.generation]. Features are only replaced once generation succeeds, so
   * cancelling ([cancelGeneration]) or a failure leaves the previous plots untouched. Failures are
   * also reported as a dataset issue until the next attempt.
   */
  suspend fun generateSample(datasetKey: String): SampleGenerationOutcome {
    if (status.progress != null) {
      return SampleGenerationOutcome.Failed(
        EditorText.Resource(Res.string.surveyeditor_sampling_already_generating)
      )
    }
    session.uiState.readOnlyMessage?.let {
      return SampleGenerationOutcome.Failed(EditorText.Resource(it))
    }
    val current = session.draft
    val dataset =
      current.datasets.firstOrNull { it.key == datasetKey }
        ?: return SampleGenerationOutcome.Failed(
          EditorText.Resource(Res.string.surveyeditor_sampling_layer_missing)
        )
    val config =
      dataset.generator
        ?: return SampleGenerationOutcome.Failed(SamplingMessage.NO_DESIGN.message().toEditorText())
    session.uiState.regenerateBlockedReason(dataset)?.let {
      return SampleGenerationOutcome.Blocked(it)
    }
    (samplingArea(config) as? SamplingAreaResult.Unavailable)?.let {
      return fail(datasetKey, it.message.toEditorText())
    }
    status.setError(datasetKey, null)
    val job = Job(currentCoroutineContext()[Job])
    generationJob = job
    val result =
      try {
        withContext(job) {
          generateSamplePlots(
            dataset = dataset,
            surveyArea = current.details.surveyArea,
            datasets = current.datasets,
            onProgress = { plotsSoFar, expected ->
              status.setProgress(SampleGenerationProgress(datasetKey, plotsSoFar, expected))
            },
          )
        }
      } catch (e: CancellationException) {
        // Only [cancelGeneration] is reported as an outcome; a cancelled caller stops here too.
        if (!job.isCancelled || !currentCoroutineContext().isActive) throw e
        return SampleGenerationOutcome.Cancelled
      } finally {
        job.complete()
        generationJob = null
        status.setProgress(null)
      }
    return when (result) {
      is GenerateSamplePlotsUseCase.Result.Failure -> fail(datasetKey, result.toEditorText())
      is GenerateSamplePlotsUseCase.Result.Generated -> {
        val quota = SampleQuotaPolicy.check(current, datasetKey, result.plotCount, organizations())
        if (quota is SampleQuotaPolicy.Result.Exceeded) {
          return fail(
            datasetKey,
            EditorText.Verbatim(
              "Sample design generated ${quota.totalPlots} plots, exceeding the limit of ${quota.limit}."
            ),
          )
        }
        val generated = result.dataset
        session.updateDataset(datasetKey) { d ->
          d.copy(
            rows = generated.rows,
            geometryType = generated.geometryType,
            generator = (d.generator ?: config).copy(lastRun = generated.generator?.lastRun),
          )
        }
        SampleGenerationOutcome.Generated(result.plotCount)
      }
    }
  }

  /** Same as [generateSample]; reads better at call sites that replace existing plots. */
  suspend fun regenerateSample(datasetKey: String): SampleGenerationOutcome =
    generateSample(datasetKey)

  override fun cancelGeneration() {
    generationJob?.cancel()
  }

  private fun fail(datasetKey: String, message: EditorText): SampleGenerationOutcome.Failed {
    status.setError(datasetKey, message)
    return SampleGenerationOutcome.Failed(message)
  }

  /** Forgets the cached design fingerprints, as when a new editing session starts. */
  fun resetInputHashes() {
    hashCache.clear()
  }
}

/** Where [SurveySamplePlotHandler] reports the progress and failures of sample generation. */
internal interface SampleGenerationStatus {
  /** The running generation's progress, or `null` while none is running. */
  val progress: SampleGenerationProgress?

  /** Publishes the running generation's [progress]; `null` when it ends, succeeded or not. */
  fun setProgress(progress: SampleGenerationProgress?)

  /** Reports [message] as dataset [datasetKey]'s generation failure; `null` clears it. */
  fun setError(datasetKey: String, message: EditorText?)
}
