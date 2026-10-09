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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.core.forms.model.FormDefinition
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorXFormsGenerator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FlowEdge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToValidator

/**
 * Screen state of the Form editor page: the [EditorForm] under edit, the selected screen, and the
 * in-browser flow preview session, observed as an immutable snapshot.
 *
 * [datasets] lists the survey's Map layers and Data tables, which the Form's save-to logic adds
 * features to or updates.
 *
 * [previewController] is UI infrastructure (a `core.forms` wizard runner holding the live preview's
 * answers) rather than data; it's carried here, like the PDF export client, so the preview overlay
 * stays a passive view of the ViewModel's session.
 */
data class FormEditorUiState(
  // --- Data ---
  val form: EditorForm =
    EditorFormTemplates.blank(formId = FormIds.newFormId(), title = "Untitled form"),
  /** The survey's Map layers and Data tables. */
  val datasets: List<EditorDataset> = emptyList(),

  // --- Session ---
  /** Selected question, or `null` when the Form itself is selected (Form properties). */
  val selectedKey: String? = null,
  /**
   * Which platform the editor's canvas and **Preview** reproduce: the mobile
   * one-question-per-screen flow, or the web dashboard's stacked read-only fields
   * ([FormPreviewTarget.WEB]).
   */
  val previewTarget: FormPreviewTarget = FormPreviewTarget.MOBILE,
  /** Live preview runner, or `null` when the preview overlay is closed. */
  val previewController: FormWizardController? = null,
  /** Message shown when the generated XForms cannot be parsed for preview. */
  val previewError: String? = null,
  /** Whether the preview reached a validated submission. */
  val previewSubmitted: Boolean = false,
  val isXmlViewerOpen: Boolean = false,
  /**
   * Whether the question panel's "Advanced" section is open. Kept here, like the app's other panel
   * expansion flags, so it stays open or closed as the author moves between questions.
   */
  val isAdvancedExpanded: Boolean = false,
  /**
   * Width of the editor's right-hand properties panel, in dp; always within
   * [MIN_SIDE_PANEL_WIDTH_DP]..[MAX_SIDE_PANEL_WIDTH_DP].
   */
  val sidePanelWidthDp: Float = DEFAULT_SIDE_PANEL_WIDTH_DP,
) {
  val selectedQuestion: EditorQuestion?
    get() = form.find(selectedKey)

  val selectedIndex: Int
    get() = selectedKey?.let { form.indexOf(it) } ?: -1

  /** Whether the Form itself (rather than a question) is selected. */
  val isFormSelected: Boolean
    get() = selectedQuestion == null

  /** Whether the properties panel shows Form settings rather than a question. */
  val isFormSettingsSelected: Boolean
    get() = selectedQuestion == null

  val flowEdges: List<FlowEdge>
    get() = FormFlowGraph.edges(form)

  val pathCount: Long
    get() = FormFlowGraph.countPaths(form)

  /** Dataset a submission adds a feature to or updates, if any. */
  val saveTarget: EditorDataset?
    get() = SaveToRules.saveTarget(form, datasets)

  /** Datasets this Form can update: all but the one it adds features to. */
  val updateTargets: List<EditorDataset>
    get() = datasets.filterNot { it.isLinkedToThisForm }

  /**
   * Datasets a select question can pull choices from: all but the one this Form adds features to.
   */
  val choiceDatasets: List<EditorDataset>
    get() = datasets.filterNot { it.isLinkedToThisForm }

  /** Datasets matching [source] (Map layers or Data tables) that a select question can use. */
  fun choiceDatasetsFor(source: ChoiceSource): List<EditorDataset> =
    when (source) {
      ChoiceSource.MANUAL -> emptyList()
      ChoiceSource.MAP_LAYER -> choiceDatasets.filter { it.isMapLayer }
      ChoiceSource.DATA_TABLE -> choiceDatasets.filterNot { it.isMapLayer }
    }

  /** Effective [ChoiceSource] for [question] resolved against [datasets]. */
  fun choiceSourceFor(question: EditorQuestion): ChoiceSource =
    question.effectiveChoiceSource(datasets)

  /** Dataset backing [question]'s choices, if any. */
  fun choiceDatasetFor(question: EditorQuestion): EditorDataset? =
    question.choiceDatasetId
      ?.takeIf { it.isNotBlank() }
      ?.let { id -> datasets.firstOrNull { it.id == id } }

  val issues: List<EditorIssue>
    get() = FormEditorValidator.validate(form, datasets) + SaveToValidator.validate(form, datasets)

  /** Issues that aren't about a single question, shown in Form properties. */
  val formIssues: List<EditorIssue>
    get() = issues.filter { it.questionKey == null }

  fun issuesFor(key: String): List<EditorIssue> = issues.filter { it.questionKey == key }

  /** Exported XForms; updates reference the target's features as a CSV attachment. */
  val xformsXml: String
    get() = EditorXFormsGenerator.toXml(form, saveTarget, datasets = datasets)

  /** Compiled [FormDefinition] run by the preview, with the target's features embedded. */
  val previewForm: FormDefinition
    get() = EditorXFormsGenerator.compile(form, saveTarget, inlineRows = true, datasets = datasets)

  /**
   * Builds a fresh [FormWizardController] from the current [previewForm] for the web canvas and the
   * preview. Callers cache the result per [previewForm] (e.g. `remember(previewForm)`), so the
   * canvas tracks every edit.
   */
  fun parsePreviewController(): Result<FormWizardController> = runCatching {
    FormWizardController(formDef = previewForm.proto)
  }

  /**
   * Instance path (`/data/<name>`) of question [key] in the generated XForms, used to match the
   * compact web cards to editor questions.
   */
  fun pathOf(key: String): String? = form.find(key)?.let { "/data/${it.name}" }

  /** Editor question whose generated instance node is [path], if any. */
  fun keyForPath(path: String): String? =
    form.questions.firstOrNull { "/data/${it.name}" == path }?.key

  /** Whether collectors can open this Form on the platform the canvas is previewing. */
  val isEnabledOnPreviewTarget: Boolean
    get() =
      when (previewTarget) {
        FormPreviewTarget.MOBILE -> form.availability.includesMobile
        FormPreviewTarget.WEB -> form.availability.includesWeb
      }

  /** Geometry questions the web dashboard can't answer while the Form is available on web. */
  val webIncompatibleGeometryQuestions: List<EditorQuestion>
    get() = form.webIncompatibleGeometryQuestions()

  companion object {
    /** Default width of the Form editor's right-hand properties panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = 380f

    /** Narrowest the Form editor's right-hand properties panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = 280f

    /** Widest the Form editor's right-hand properties panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = 640f
  }
}
