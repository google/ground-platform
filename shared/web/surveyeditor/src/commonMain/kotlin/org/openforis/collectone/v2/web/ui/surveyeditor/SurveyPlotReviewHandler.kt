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

import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * [PlotReviewActions] of the Survey editor: the review Forms and layers of a sample plot layer.
 * [onFormsRemoved] hears about Forms the change removed, so their editors can be closed.
 */
internal class SurveyPlotReviewHandler(
  private val session: SurveyDraftSession,
  private val onFormsRemoved: (Set<String>) -> Unit,
) : PlotReviewActions {
  override fun updatePlotReview(datasetKey: String, options: PlotReviewOptions) = editPlotReview {
    PlotReviewWorkflow.apply(it, datasetKey, options)
  }

  override fun removePlotReview(datasetKey: String) = editPlotReview {
    PlotReviewWorkflow.remove(it, datasetKey)
  }

  /**
   * Replaces the draft's Forms and datasets with [transform]'s; removed Forms drop their editors.
   */
  private fun editPlotReview(transform: (SurveyEditorDraft) -> SurveyEditorDraft) {
    var removedFormKeys = emptySet<String>()
    session.edit {
      val before = toDraft()
      val after = transform(before)
      forms = after.forms
      datasets = after.datasets
      nextId = after.nextKeyId
      removedFormKeys = before.forms.map { it.key }.toSet() - after.forms.map { it.key }.toSet()
    }
    onFormsRemoved(removedFormKeys)
  }
}
