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
package org.openforis.collectone.v2.web.ui.state

import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.ConceptLinkValidator
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssue
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorXFormsGenerator
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdge
import org.openforis.collectone.v2.app.domain.model.editor.FormEditorValidator
import org.openforis.collectone.v2.app.domain.model.editor.FormFlowGraph
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SaveToValidator
import org.openforis.collectone.v2.app.domain.model.editor.estimatedMinutes
import org.openforis.collectone.v2.app.domain.model.editor.longestPathQuestionCount
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.web.domain.model.editor.SurveyEditorMutation

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
  /** Selected section ID, or `null` when not editing section properties. */
  val selectedSectionId: String? = null,
  /**
   * Language tag currently being edited and previewed in the Form editor, or `null` to follow the
   * survey's default language ([defaultLanguage]).
   */
  val selectedLanguage: String? = null,
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
  /** Survey mutations recorded in this Form editor that can be rewound, oldest first. */
  val undoStack: List<SurveyEditorMutation> = emptyList(),
  /** Rewound survey mutations in this Form editor that can be replayed, oldest first. */
  val redoStack: List<SurveyEditorMutation> = emptyList(),

  // --- Dictionary (concept links) ---
  /** The survey's resolved library and who may add to it. */
  val library: FormLibraryContext = FormLibraryContext(),
  /** Concept suggestions open for a question, or `null`. */
  val conceptSuggestions: ConceptSuggestionsState? = null,
  /**
   * Questions of a just-imported Form that match standard fields ("We found N fields that match
   * standard definitions"), or empty when the card is closed.
   */
  val importMatches: List<ImportMatch> = emptyList(),
) {
  /** Primary (default) language of the survey, e.g. `"en"`. */
  val defaultLanguage: String
    get() = library.language.ifBlank { "en" }

  /**
   * Languages selected for the survey, starting with [defaultLanguage], plus any additional
   * languages present in the Form's question or choice translations (e.g. from an imported XForm).
   */
  val supportedLanguages: List<String>
    get() =
      (listOf(defaultLanguage) + library.supportedLanguages + form.translationLanguages)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("default", ignoreCase = true) }
        .distinctBy { it.lowercase() }

  /** Language currently being edited and previewed in the Form editor. */
  val activeLanguage: String
    get() =
      selectedLanguage?.let { sel ->
        supportedLanguages.firstOrNull { it.equals(sel, ignoreCase = true) }
      } ?: defaultLanguage

  /** Whether the Form editor is currently editing the survey's default language. */
  val isDefaultLanguageSelected: Boolean
    get() = activeLanguage.equals(defaultLanguage, ignoreCase = true)

  /** Whether there is at least one survey mutation to rewind. */
  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  /** Whether there is at least one rewound survey mutation to replay. */
  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  val selectedQuestion: EditorQuestion?
    get() = form.find(selectedKey)

  val selectedSection: org.openforis.collectone.v2.app.domain.model.editor.EditorSection?
    get() = selectedSectionId?.let { form.section(it) }

  val selectedIndex: Int
    get() = selectedKey?.let { form.indexOf(it) } ?: -1

  /** Whether the Form itself (rather than a question or section) is selected. */
  val isFormSelected: Boolean
    get() = selectedQuestion == null && selectedSection == null

  /** Whether the properties panel shows Form settings rather than a question or section. */
  val isFormSettingsSelected: Boolean
    get() = selectedQuestion == null && selectedSection == null

  val flowEdges: List<FlowEdge>
    get() = FormFlowGraph.edges(form)

  val pathCount: Long
    get() = FormFlowGraph.countPaths(form)

  /** Maximum number of question screens along any valid path from Start to Review & submit. */
  val longestPathQuestionCount: Int
    get() = FormFlowGraph.longestPathQuestionCount(form, flowEdges)

  /** Estimated completion time in minutes (~2 min per question along the longest path). */
  val estimatedCompletionMinutes: Int
    get() = FormFlowGraph.estimatedMinutes(form, flowEdges)

  /**
   * Whether the longest path exceeds [FATIGUE_WARNING_QUESTION_THRESHOLD] questions (> 20 min
   * estimated duration), indicating potential respondent fatigue.
   */
  val isFatigueWarning: Boolean
    get() = longestPathQuestionCount > FATIGUE_WARNING_QUESTION_THRESHOLD

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

  /** Concept link warnings, which never block publishing. */
  val warnings: List<EditorIssue>
    get() = ConceptLinkValidator.validate(form) { library.concept(it) }

  fun warningsFor(key: String): List<EditorIssue> = warnings.filter { it.questionKey == key }

  /** The concept [question] is linked to, if it's in the survey's library. */
  fun conceptOf(question: EditorQuestion): LibraryConcept? =
    library.concept(question.conceptLink?.conceptId)

  /**
   * Why deleting linked [question] matters, for the soft confirmation (e.g. "This field feeds your
   * impact report for EUDR due diligence."), or `null` when it isn't linked.
   */
  fun deleteWarningFor(question: EditorQuestion): String? {
    val link = question.conceptLink ?: return null
    val concept = library.concept(link.conceptId)
    val purposes =
      library.library.pickablePurposePacks
        .filter { pack ->
          pack.formTemplateIds.any { id ->
            library.library.formTemplate(id)?.conceptIds?.contains(link.conceptId) == true
          }
        }
        .map { it.title.get(library.language) }
    val name = concept?.label?.get(library.language) ?: link.conceptId
    return if (purposes.isNotEmpty()) {
      "This field feeds your impact report for ${purposes.joinToString(" and ")}."
    } else {
      "This field is linked to the standard field \"$name\", so its answers count toward impact " +
        "reports."
    }
  }

  /** Issues that aren't about a single question, shown in Form properties. */
  val formIssues: List<EditorIssue>
    get() = issues.filter { it.questionKey == null }

  fun issuesFor(key: String): List<EditorIssue> = issues.filter { it.questionKey == key }

  /** Exported XForms; updates reference the target's features as a CSV attachment. */
  val xformsXml: String
    get() =
      EditorXFormsGenerator.toXml(
        form,
        saveTarget,
        datasets = datasets,
        defaultLanguage = defaultLanguage,
      )

  /** Compiled [FormDefinition] run by the preview, with the target's features embedded. */
  val previewForm: FormDefinition
    get() =
      EditorXFormsGenerator.compile(
        form,
        saveTarget,
        inlineRows = true,
        datasets = datasets,
        defaultLanguage = defaultLanguage,
      )

  /**
   * Builds a fresh [FormWizardController] from the current [previewForm] for the web canvas and the
   * preview. Callers cache the result per [previewForm] (e.g. `remember(previewForm)`), so the
   * canvas tracks every edit.
   */
  fun parsePreviewController(): Result<FormWizardController> = runCatching {
    FormWizardController(formDef = previewForm.proto).also {
      if (activeLanguage.isNotBlank()) it.setLanguage(activeLanguage)
    }
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

    /** Longest-path question count above which the fatigue warning triggers (> 10 questions). */
    const val FATIGUE_WARNING_QUESTION_THRESHOLD = 10
  }
}
