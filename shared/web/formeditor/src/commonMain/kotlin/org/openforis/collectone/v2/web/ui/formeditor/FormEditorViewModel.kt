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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.deviceFormEnvironment
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoiceImage
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusBadge
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.model.editor.toEditorDataset
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.web.domain.model.editor.SurveyEditorMutation
import org.openforis.collectone.v2.web.domain.model.editor.SurveyUndoRedoStack
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_preview_load_error
import org.openforis.collectone.v2.web.ui.text.EditorDefaultNames

/**
 * ViewModel of the Form editor page for one Form of the survey being edited.
 *
 * The Form itself lives in the Survey editor's draft: it's read from [formFlow] and every edit goes
 * through [updateForm], so the Survey editor sees it at once (and its linked datasets follow). The
 * survey's Map layers and Data tables, which the save-to logic targets, come from [datasetsFlow].
 * Selection, the undo/redo stack, and the preview session are this ViewModel's own.
 *
 * Questions, sections, choices, save-to settings, status markers, and dictionary concept links are
 * implemented by [FormQuestionHandler], [FormChoiceHandler], [FormSaveToHandler],
 * [FormStatusRuleHandler], and [FormConceptLinkHandler], which change the Form through
 * [FormDraftSession]; this ViewModel forwards those intents to them.
 *
 * Each change in the Form editor is tracked on [Session.undoRedo] as a [SurveyEditorMutation] of
 * the enclosing [SurveyEditorDraft] so rewinding or replaying an edit restores both the Form and
 * any datasets or Forms mutated alongside it.
 *
 * The preview's [FormWizardController] is UI infrastructure from `core.forms` (it runs the
 * generated XForms and holds the preview's answers); like the PDF export client, it's owned here
 * rather than by the view so the preview overlay stays passive.
 */
class FormEditorViewModel(
  private val formFlow: StateFlow<EditorForm>,
  private val datasetsFlow: StateFlow<List<EditorDataset>>,
  private val updateForm: (transform: (EditorForm) -> EditorForm) -> Unit,
  /**
   * The editor's `viewModelScope`. A Form editor owned by the Survey editor runs in a child of the
   * Survey editor's scope, so it stops with it; a standalone one gets its own.
   */
  coroutineScope: CoroutineScope = viewModelScopeOn(ViewModelDispatcher),
  /** The survey's dictionary; empty for the standalone editor. */
  private val libraryFlow: StateFlow<FormLibraryContext> = MutableStateFlow(FormLibraryContext()),
  /**
   * Adds a draft concept to the survey organization's dictionary; returns an error message, or
   * `null` when it was added.
   */
  private val addOrganizationConcept: (LibraryConcept) -> String? = {
    "This survey has no organization dictionary."
  },
  private val searchConcepts: SearchConceptsUseCase = SearchConceptsUseCase(),
  /**
   * Saves a Form as a template (form, title, description, to the global library); returns an error
   * message, or `null` when it was saved.
   */
  private val saveTemplate:
    (form: EditorForm, title: String, description: String, toGlobal: Boolean) -> String? =
    { _, _, _, _ ->
      "Templates can only be saved from a survey in an organization."
    },
  /** Key identifying this Form within its enclosing [SurveyEditorDraft]. */
  private val formKey: String = DEFAULT_STANDALONE_FORM_KEY,
  /**
   * Returns the current [SurveyEditorDraft] backing this Form editor, or `null` when running
   * standalone without an enclosing `SurveyEditorViewModel`.
   */
  private val currentSurveyDraft: (() -> SurveyEditorDraft)? = null,
  /**
   * Replaces the enclosing `SurveyEditorViewModel`'s working [SurveyEditorDraft] when a
   * [SurveyEditorMutation] is rewound or replayed, or `null` when running standalone.
   */
  private val applySurveyDraft: ((SurveyEditorDraft) -> Unit)? = null,
  /** Whether the enclosing survey is read-only (closed or archived). */
  private val isSurveyReadOnly: () -> Boolean = { false },
  /** Reactive read-only state from the enclosing survey editor, when embedded. */
  private val isReadOnlyFlow: StateFlow<Boolean> = MutableStateFlow(isSurveyReadOnly()),
  private val createDatasetForChoiceQuestionCallback:
    ((questionIndex: Int, kind: DatasetKind) -> Unit)? =
    null,
  /** Starting names of new questions, in the language the editor shows. */
  private val defaultNames: () -> EditorDefaultNames = { EditorDefaultNames.ENGLISH },
  /** The clock the preview runs on, so `today()` and date defaults show the real date. */
  private val clock: Clock = Clock.System,
  /** Whether the Form is a library template's; see [FormEditorUiState.isTemplate]. */
  private val isTemplate: Boolean = false,
) : ViewModel(coroutineScope), FormEditorActions {
  /** Session (non-persisted) state of the Form editor. */
  private data class Session(
    val selectedKey: String?,
    val selectedSectionId: String? = null,
    val selectedLanguage: String? = null,
    val previewTarget: FormPreviewTarget = FormPreviewTarget.MOBILE,
    val isPreviewOpen: Boolean = false,
    val previewError: FormEditorMessage? = null,
    val previewSubmitted: Boolean = false,
    val isXmlViewerOpen: Boolean = false,
    val isAdvancedExpanded: Boolean = false,
    val sidePanelWidthDp: Float = FormEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
    val undoRedo: SurveyUndoRedoStack = SurveyUndoRedoStack(),
    val conceptSuggestions: ConceptSuggestionsState? = null,
    val importMatches: List<ImportMatch> = emptyList(),
  )

  private val session =
    MutableStateFlow(Session(selectedKey = formFlow.value.questions.firstOrNull()?.key))

  private val keys = QuestionKeyAllocator(nextId = formFlow.value.questions.size + 1)

  private val _previewController = MutableStateFlow<FormWizardController?>(null)

  /**
   * Runner of the open preview, or `null` while it's closed or the Form can't be compiled. Kept
   * apart from [uiState] because it's mutable: it holds the answers entered in the preview.
   */
  val previewController: StateFlow<FormWizardController?> = _previewController.asStateFlow()

  /** Guards against double-recording when [mutateForm], [undo], or [redo] invokes [updateForm]. */
  private var isRecordingSuppressed = false

  private fun isReadOnly(): Boolean = isReadOnlyFlow.value || isSurveyReadOnly()

  val uiState: StateFlow<FormEditorUiState> =
    combine(formFlow, datasetsFlow, session, libraryFlow, isReadOnlyFlow) {
        form,
        datasets,
        session,
        library,
        readOnly ->
        val effectiveReadOnly = readOnly || isSurveyReadOnly()
        FormEditorUiState(
          form = form,
          datasets = datasets,
          selectedKey = session.selectedKey,
          selectedSectionId = session.selectedSectionId,
          selectedLanguage = session.selectedLanguage,
          previewTarget = session.previewTarget,
          isPreviewOpen = session.isPreviewOpen,
          previewError = session.previewError,
          previewSubmitted = session.previewSubmitted,
          isXmlViewerOpen = session.isXmlViewerOpen,
          isAdvancedExpanded = session.isAdvancedExpanded,
          sidePanelWidthDp = session.sidePanelWidthDp,
          undoStack = session.undoRedo.undoStack,
          redoStack = session.undoRedo.redoStack,
          library = library,
          conceptSuggestions = if (effectiveReadOnly) null else session.conceptSuggestions,
          importMatches = if (effectiveReadOnly) emptyList() else session.importMatches,
          isReadOnly = effectiveReadOnly,
          isTemplate = isTemplate,
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FormEditorUiState(
          form = formFlow.value,
          datasets = datasetsFlow.value,
          selectedKey = session.value.selectedKey,
          selectedLanguage = session.value.selectedLanguage,
          undoStack = session.value.undoRedo.undoStack,
          redoStack = session.value.undoRedo.redoStack,
          library = libraryFlow.value,
          isReadOnly = isReadOnly(),
          isTemplate = isTemplate,
        ),
      )

  private val form: EditorForm
    get() =
      currentSurveyDraft?.invoke()?.forms?.firstOrNull { it.key == formKey }?.form ?: formFlow.value

  private val datasets: List<EditorDataset>
    get() =
      currentSurveyDraft?.invoke()?.let { draft ->
        draft.datasets.map { it.toEditorDataset(formKey, draft.forms) }
      } ?: datasetsFlow.value

  /** The Form editor's session as the action handlers below see it. */
  private val draftSession =
    object : FormDraftSession {
      override val uiState: FormEditorUiState
        get() = this@FormEditorViewModel.uiState.value

      override val form: EditorForm
        get() = this@FormEditorViewModel.form

      override val observedForm: EditorForm
        get() = formFlow.value

      override val datasets: List<EditorDataset>
        get() = this@FormEditorViewModel.datasets

      override val isReadOnly: Boolean
        get() = isReadOnly()

      override val selectedKey: String?
        get() = session.value.selectedKey

      override fun selectSection(sectionId: String?) =
        this@FormEditorViewModel.selectSection(sectionId)

      override fun mutateForm(
        selectAfter: (EditorForm) -> String?,
        transform: (EditorForm) -> EditorForm,
      ) = this@FormEditorViewModel.mutateForm(selectAfter = selectAfter, transform = transform)
    }

  private val questionHandler = FormQuestionHandler(draftSession, keys, defaultNames)

  private val statusRuleHandler = FormStatusRuleHandler(draftSession)

  private val saveToHandler = FormSaveToHandler(draftSession)

  private val conceptLinkHandler =
    FormConceptLinkHandler(
      draftSession,
      linkState =
        object : ConceptLinkState {
          override var conceptSuggestions: ConceptSuggestionsState?
            get() = session.value.conceptSuggestions
            set(value) = session.update { it.copy(conceptSuggestions = value) }

          override var importMatches: List<ImportMatch>
            get() = session.value.importMatches
            set(value) = session.update { it.copy(importMatches = value) }
        },
      libraryFlow,
      searchConcepts,
      addOrganizationConcept,
    )

  private val choiceHandler =
    FormChoiceHandler(draftSession, createDatasetForChoiceQuestionCallback)

  private fun standaloneSurveyDraft(currentForm: EditorForm = formFlow.value): SurveyEditorDraft =
    SurveyEditorDraft(
      details = SurveyDetails(surveyId = "standalone", title = currentForm.title, description = ""),
      sharing = SharingSettings(ownerEmail = ""),
      forms = listOf(SurveyEditorForm(key = formKey, form = currentForm)),
    )

  private fun currentDraftSnapshot(): SurveyEditorDraft =
    currentSurveyDraft?.invoke() ?: standaloneSurveyDraft()

  /**
   * Applies [transform] to this Form and records the resulting [SurveyEditorMutation] on
   * [Session.undoRedo], clearing any redo history. A transform that changes nothing is ignored.
   */
  private fun mutateForm(
    selectAfter: (EditorForm) -> String? = { session.value.selectedKey },
    transform: (EditorForm) -> EditorForm,
  ) {
    if (isReadOnly()) return
    val selectedBefore = session.value.selectedKey
    val beforeDraft = currentDraftSnapshot()
    val beforeForm = beforeDraft.forms.firstOrNull { it.key == formKey }?.form ?: formFlow.value
    val updatedForm = transform(beforeForm)
    if (updatedForm == beforeForm) return
    isRecordingSuppressed = true
    try {
      updateForm { updatedForm }
    } finally {
      isRecordingSuppressed = false
    }
    val afterDraft = currentSurveyDraft?.invoke() ?: standaloneSurveyDraft(updatedForm)
    val afterForm = afterDraft.forms.firstOrNull { it.key == formKey }?.form ?: updatedForm
    val resolvedSelectedAfter = selectAfter(afterForm)?.takeIf { afterForm.indexOf(it) >= 0 }
    val mutation =
      SurveyEditorMutation.between(
        before = beforeDraft,
        after = afterDraft,
        formKey = formKey,
        selectedQuestionKeyBefore = selectedBefore,
        selectedQuestionKeyAfter = resolvedSelectedAfter,
      )
    session.update { s ->
      s.copy(
        selectedKey = resolvedSelectedAfter,
        undoRedo = if (mutation != null) s.undoRedo.record(mutation) else s.undoRedo,
      )
    }
  }

  /**
   * Records a mutation of the enclosing [SurveyEditorDraft] that was initiated outside
   * [FormEditorViewModel] (for example switching [SaveToMode], creating/unlinking a linked dataset,
   * or creating a Form for a choice dataset from the Form editor).
   */
  fun recordExternalSurveyMutation(before: SurveyEditorDraft, after: SurveyEditorDraft) {
    if (isRecordingSuppressed || isReadOnly()) return
    val formBefore = before.forms.firstOrNull { it.key == formKey }?.form ?: form
    val formAfter = after.forms.firstOrNull { it.key == formKey }?.form ?: form
    val selectedBefore = session.value.selectedKey?.takeIf { formBefore.indexOf(it) >= 0 }
    val selectedAfter = session.value.selectedKey?.takeIf { formAfter.indexOf(it) >= 0 }
    val mutation =
      SurveyEditorMutation.between(
        before = before,
        after = after,
        formKey = formKey,
        selectedQuestionKeyBefore = selectedBefore,
        selectedQuestionKeyAfter = selectedAfter,
      ) ?: return
    session.update { it.copy(selectedKey = selectedAfter, undoRedo = it.undoRedo.record(mutation)) }
  }

  /** Clears the undo and redo stacks (used when draft changes are published or discarded). */
  fun clearUndoRedo() {
    session.update { it.copy(undoRedo = it.undoRedo.clear()) }
  }

  // --- Undo / redo ---

  override fun undo() = rewind(toUndo = true)

  override fun redo() = rewind(toUndo = false)

  /**
   * Undoes ([toUndo]) or redoes the last edit: puts back the whole survey draft it was recorded
   * against (so datasets and other Forms changed with it follow) and restores the selection from
   * before (undo) or after (redo) that edit. Open concept suggestions are dropped, since they may
   * refer to a question that no longer exists.
   */
  private fun rewind(toUndo: Boolean) {
    if (isReadOnly()) return
    val currentDraft = currentDraftSnapshot()
    val stack = session.value.undoRedo
    val transition = (if (toUndo) stack.undo(currentDraft) else stack.redo(currentDraft)) ?: return
    val targetForm = transition.draft.forms.firstOrNull { it.key == formKey }?.form
    isRecordingSuppressed = true
    try {
      if (applySurveyDraft != null) {
        applySurveyDraft.invoke(transition.draft)
      } else if (targetForm != null) {
        updateForm { targetForm }
      }
    } finally {
      isRecordingSuppressed = false
    }
    val formAfter = targetForm ?: form
    val selection =
      if (toUndo) transition.mutation.selectedQuestionKeyBefore
      else transition.mutation.selectedQuestionKeyAfter
    session.update {
      it.copy(
        selectedKey = selection?.takeIf { key -> formAfter.indexOf(key) >= 0 },
        undoRedo = transition.stack,
        conceptSuggestions = null,
      )
    }
  }

  // --- Selection & panels ---

  override fun select(key: String?) {
    session.update {
      it.copy(
        selectedKey = key?.takeIf { k -> form.indexOf(k) >= 0 },
        selectedSectionId = null,
      )
    }
  }

  override fun selectForm() {
    session.update { it.copy(selectedKey = null, selectedSectionId = null) }
  }

  override fun selectSection(sectionId: String?) {
    session.update { it.copy(selectedSectionId = sectionId, selectedKey = null) }
  }

  override fun selectFormSettings() = selectForm()

  override fun selectPreviewTarget(target: FormPreviewTarget) {
    session.update { it.copy(previewTarget = target) }
  }

  override fun selectLanguage(language: String) {
    val trimmed = language.trim()
    session.update { it.copy(selectedLanguage = trimmed.ifEmpty { null }) }
    val active = uiState.value.activeLanguage
    if (active.isNotBlank()) {
      _previewController.value?.setLanguage(active)
    }
  }

  override fun setXmlViewerOpen(open: Boolean) {
    session.update { it.copy(isXmlViewerOpen = open) }
  }

  override fun setAdvancedExpanded(expanded: Boolean) {
    session.update { it.copy(isAdvancedExpanded = expanded) }
  }

  override fun updateSidePanelWidth(widthDp: Float) {
    if (!widthDp.isFinite()) return
    session.update {
      it.copy(
        sidePanelWidthDp =
          widthDp.coerceIn(
            FormEditorUiState.MIN_SIDE_PANEL_WIDTH_DP,
            FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP,
          )
      )
    }
  }

  // --- Form ---

  override fun updateTitle(title: String) = mutateForm { it.copy(title = title) }

  override fun updateCtaLabel(ctaLabel: String) = mutateForm { it.copy(ctaLabel = ctaLabel) }

  override fun updateAvailability(availability: FormAvailability) = mutateForm {
    val next = it.copy(availability = availability)
    next.copy(
      openInGoogleEarth =
        next.openInGoogleEarth && !isTemplate && next.canOpenInGoogleEarth(datasets)
    )
  }

  override fun updateOpenInGoogleEarth(enabled: Boolean) = mutateForm {
    it.copy(openInGoogleEarth = enabled && !isTemplate && it.canOpenInGoogleEarth(datasets))
  }

  override fun enableOnPreviewTarget() {
    updateAvailability(
      when (session.value.previewTarget) {
        FormPreviewTarget.MOBILE -> form.availability.withMobile(true)
        FormPreviewTarget.WEB -> form.availability.withWeb(true)
      }
    )
  }

  // --- Questions ---

  override fun addQuestion(type: EditorQuestionType, atIndex: Int?) =
    questionHandler.addQuestion(type, atIndex)

  override fun addQuestion(
    type: EditorQuestionType,
    atIndex: Int?,
    sectionId: String?,
    afterSectionId: String?,
  ) = questionHandler.addQuestion(type, atIndex, sectionId, afterSectionId)

  override fun duplicateQuestion(key: String) = questionHandler.duplicateQuestion(key)

  override fun duplicateQuestionBranch(key: String) = questionHandler.duplicateQuestionBranch(key)

  override fun duplicateSection(sectionId: String) = questionHandler.duplicateSection(sectionId)

  override fun deleteQuestion(key: String) = questionHandler.deleteQuestion(key)

  override fun moveQuestion(key: String, delta: Int) = questionHandler.moveQuestion(key, delta)

  override fun moveQuestionTo(key: String, targetIndex: Int, targetSectionId: String?) =
    questionHandler.moveQuestionTo(key, targetIndex, targetSectionId)

  override fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion) =
    questionHandler.updateQuestion(key, transform)

  override fun updateQuestionLabel(key: String, label: String) =
    questionHandler.updateQuestionLabel(key, label)

  override fun updateQuestionHint(key: String, hint: String) =
    questionHandler.updateQuestionHint(key, hint)

  override fun changeType(key: String, type: EditorQuestionType) =
    questionHandler.changeType(key, type)

  override fun updateCapture(key: String, capture: GeometryCapture) =
    questionHandler.updateCapture(key, capture)

  override fun updateMediaSource(key: String, source: MediaSource) =
    questionHandler.updateMediaSource(key, source)

  override fun updateDecimalPlaces(key: String, decimalPlaces: Int) =
    questionHandler.updateDecimalPlaces(key, decimalPlaces)

  override fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation) =
    questionHandler.updateValidation(key, transform)

  override fun enableRelevance(key: String) = questionHandler.enableRelevance(key)

  override fun disableRelevance(key: String) = questionHandler.disableRelevance(key)

  // --- Choices ---

  override fun setChoiceSource(key: String, source: ChoiceSource) =
    choiceHandler.setChoiceSource(key, source)

  override fun setChoiceDataset(key: String, datasetId: String?) =
    choiceHandler.setChoiceDataset(key, datasetId)

  override fun setAllowAddEntity(key: String, allow: Boolean) =
    choiceHandler.setAllowAddEntity(key, allow)

  override fun updateChoiceLabel(key: String, index: Int, label: String) =
    choiceHandler.updateChoiceLabel(key, index, label)

  override fun updateChoice(key: String, index: Int, choice: EditorChoice) =
    choiceHandler.updateChoice(key, index, choice)

  override fun addChoice(key: String) = choiceHandler.addChoice(key)

  override fun removeChoice(key: String, index: Int) = choiceHandler.removeChoice(key, index)

  override fun setChoiceColor(key: String, index: Int, colorHex: String?) =
    choiceHandler.setChoiceColor(key, index, colorHex)

  override fun setChoiceImage(key: String, index: Int, image: EditorChoiceImage?): Boolean =
    choiceHandler.setChoiceImage(key, index, image)

  override fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind) =
    choiceHandler.createDatasetForChoiceQuestion(questionIndex, kind)

  override fun createDatasetForChoiceQuestion(key: String, kind: DatasetKind) =
    choiceHandler.createDatasetForChoiceQuestion(key, kind)

  override fun updateQuestionParentKey(questionIndex: Int, parentQuestionKey: String) =
    choiceHandler.updateQuestionParentKey(questionIndex, parentQuestionKey)

  override fun updateQuestionParentKey(key: String, parentQuestionKey: String) =
    choiceHandler.updateQuestionParentKey(key, parentQuestionKey)

  override fun updateChoiceParentValue(questionIndex: Int, choiceIndex: Int, parentValue: String) =
    choiceHandler.updateChoiceParentValue(questionIndex, choiceIndex, parentValue)

  override fun updateChoiceParentValue(key: String, choiceIndex: Int, parentValue: String) =
    choiceHandler.updateChoiceParentValue(key, choiceIndex, parentValue)

  override fun importQuestionChoices(
    questionIndex: Int,
    importedChoices: List<EditorChoice>,
    append: Boolean,
  ) = choiceHandler.importQuestionChoices(questionIndex, importedChoices, append)

  override fun importQuestionChoices(
    key: String,
    importedChoices: List<EditorChoice>,
    append: Boolean,
  ) = choiceHandler.importQuestionChoices(key, importedChoices, append)

  override fun importChoicesFromFile(
    key: String,
    text: String,
    fileName: String,
  ): ChoiceFileImport = choiceHandler.importChoicesFromFile(key, text, fileName)

  // --- Sections ---

  override fun addSection(
    title: String,
    repeatable: Boolean,
    atIndex: Int?,
    afterSectionId: String?,
  ): String = questionHandler.addSection(title, repeatable, atIndex, afterSectionId)

  override fun updateSection(sectionId: String, transform: (EditorSection) -> EditorSection) =
    questionHandler.updateSection(sectionId, transform)

  override fun deleteSection(sectionId: String) = questionHandler.deleteSection(sectionId)

  override fun setQuestionSection(questionIndex: Int, sectionId: String) =
    questionHandler.setQuestionSection(questionIndex, sectionId)

  override fun setQuestionSection(key: String, sectionId: String) =
    questionHandler.setQuestionSection(key, sectionId)

  override fun moveSection(sectionId: String, delta: Int) =
    questionHandler.moveSection(sectionId, delta)

  override fun enableSectionRelevance(sectionId: String) =
    questionHandler.enableSectionRelevance(sectionId)

  override fun disableSectionRelevance(sectionId: String) =
    questionHandler.disableSectionRelevance(sectionId)

  // --- Save-to logic ---

  override fun setSaveToMode(mode: SaveToMode) = saveToHandler.setSaveToMode(mode)

  override fun setTargetDataset(datasetId: String) = saveToHandler.setTargetDataset(datasetId)

  override fun setIdSource(source: EntityIdSource) = saveToHandler.setIdSource(source)

  override fun setIdQuestion(questionKey: String) = saveToHandler.setIdQuestion(questionKey)

  override fun setIdMatchProperty(property: String) = saveToHandler.setIdMatchProperty(property)

  override fun setMapping(questionKey: String, property: String?) =
    saveToHandler.setMapping(questionKey, property)

  /** Follows a rename of dataset [oldId] so this Form keeps updating it. */
  fun renameTargetDataset(oldId: String, newId: String) =
    saveToHandler.renameTargetDataset(oldId, newId)

  /**
   * Follows a rename of property [oldName] of dataset [datasetId] in lookup, mappings, and status
   * rules.
   */
  fun renameTargetProperty(datasetId: String, oldName: String, newName: String) =
    saveToHandler.renameTargetProperty(datasetId, oldName, newName)

  // --- Status markers ---

  override fun setStatusEnabled(enabled: Boolean) = statusRuleHandler.setStatusEnabled(enabled)

  override fun addStatusRule() = statusRuleHandler.addStatusRule()

  override fun updateStatusRule(index: Int, transform: (EditorStatusRule) -> EditorStatusRule) =
    statusRuleHandler.updateStatusRule(index, transform)

  override fun removeStatusRule(index: Int) = statusRuleHandler.removeStatusRule(index)

  override fun moveStatusRule(index: Int, delta: Int) =
    statusRuleHandler.moveStatusRule(index, delta)

  override fun updateDefaultStatusBadge(transform: (EditorStatusBadge) -> EditorStatusBadge) =
    statusRuleHandler.updateDefaultStatusBadge(transform)

  // --- Preview ---

  override fun startPreview() {
    val state = uiState.value
    val controller = runCatching {
      FormWizardController(
          formDef = state.previewForm.proto,
          environment = deviceFormEnvironment(clock),
        )
        .also {
          if (state.activeLanguage.isNotBlank()) it.setLanguage(state.activeLanguage)
        }
    }
    _previewController.value = controller.getOrNull()
    session.update {
      it.copy(
        isPreviewOpen = true,
        previewSubmitted = false,
        previewError =
          controller.exceptionOrNull()?.let { e ->
            FormEditorMessage.Resource(
              Res.string.formeditor_preview_load_error,
              listOf(e.message ?: e.toString()),
            )
          },
      )
    }
  }

  override fun restartPreview() = startPreview()

  override fun markPreviewSubmitted() {
    session.update { it.copy(previewSubmitted = true) }
  }

  override fun closePreview() {
    _previewController.value = null
    session.update {
      it.copy(isPreviewOpen = false, previewError = null, previewSubmitted = false)
    }
  }

  // --- Dictionary (concept links) ---

  override fun requestConceptSuggestions(key: String, query: String) =
    conceptLinkHandler.requestConceptSuggestions(key, query)

  override fun openConceptSearch(key: String) = conceptLinkHandler.openConceptSearch(key)

  override fun updateConceptSearchQuery(query: String) =
    conceptLinkHandler.updateConceptSearchQuery(query)

  override fun dismissConceptSuggestions() = conceptLinkHandler.dismissConceptSuggestions()

  override fun linkConcept(key: String, conceptId: String) =
    conceptLinkHandler.linkConcept(key, conceptId)

  override fun unlinkConcept(key: String) = conceptLinkHandler.unlinkConcept(key)

  override fun useStandardLabel(key: String) = conceptLinkHandler.useStandardLabel(key)

  override fun addLabelToDictionary(key: String): FormEditorMessage? =
    conceptLinkHandler.addLabelToDictionary(key)

  override fun setChoiceCode(key: String, index: Int, code: String?) =
    conceptLinkHandler.setChoiceCode(key, index, code)

  override fun suggestImportMatches() = conceptLinkHandler.suggestImportMatches()

  override fun setImportMatchChecked(key: String, checked: Boolean) =
    conceptLinkHandler.setImportMatchChecked(key, checked)

  override fun linkImportMatches() = conceptLinkHandler.linkImportMatches()

  override fun dismissImportMatches() = conceptLinkHandler.dismissImportMatches()

  override fun saveAsTemplate(title: String, description: String, toGlobal: Boolean): String? =
    saveTemplate(form, title, description, toGlobal)

  override fun applyTemplateUpdate(
    addedQuestions: List<EditorQuestion>,
    removedQuestionKeys: Set<String>,
  ) {
    if (addedQuestions.isEmpty() && removedQuestionKeys.isEmpty()) return
    val diff = uiState.value.templateUpdateDiff
    mutateForm {
      it.withTemplateUpdate(
        addedQuestions = addedQuestions,
        removedQuestionKeys = removedQuestionKeys,
        keys = keys,
        templateId = diff?.templateId,
        templateVersion = diff?.templateVersion,
      )
    }
  }

  companion object {
    /** Characters typed into a label before standard fields are suggested. */
    const val MIN_SUGGESTION_QUERY = 3

    /** Suggestions shown under the Label field. */
    const val MAX_SUGGESTIONS = 5

    /** Key used for the synthetic [SurveyEditorForm] in a standalone [FormEditorViewModel]. */
    const val DEFAULT_STANDALONE_FORM_KEY = "standalone_form"

    /**
     * A Form editor over its own copy of [initialForm] with a fixed dataset catalog, for the
     * standalone Form editor and tests. Edits stay in the returned ViewModel.
     */
    fun standalone(
      initialForm: EditorForm =
        EditorFormTemplates.blank(formId = FormIds.newFormId(), title = "Untitled form"),
      datasets: List<EditorDataset> = emptyList(),
      library: FormLibraryContext = FormLibraryContext(),
    ): FormEditorViewModel {
      val form = MutableStateFlow(initialForm)
      return FormEditorViewModel(
        formFlow = form,
        datasetsFlow = MutableStateFlow(datasets),
        updateForm = { transform -> form.update(transform) },
        libraryFlow = MutableStateFlow(library),
      )
    }

    /**
     * A Form editor over a copy of library template Form [initialForm], for an organization's
     * **Templates** tab. A template has no survey datasets, so [FormEditorUiState.isTemplate] hides
     * the survey-specific settings; questions link to the concepts of [library]. Edits stay in the
     * returned ViewModel until its host saves them.
     */
    fun forTemplate(
      initialForm: EditorForm,
      library: FormLibraryContext,
      coroutineScope: CoroutineScope = viewModelScopeOn(ViewModelDispatcher),
    ): FormEditorViewModel {
      val form = MutableStateFlow(initialForm)
      return FormEditorViewModel(
        formFlow = form,
        datasetsFlow = MutableStateFlow(emptyList()),
        updateForm = { transform -> form.update(transform) },
        coroutineScope = coroutineScope,
        libraryFlow = MutableStateFlow(library),
        isTemplate = true,
      )
    }
  }
}
