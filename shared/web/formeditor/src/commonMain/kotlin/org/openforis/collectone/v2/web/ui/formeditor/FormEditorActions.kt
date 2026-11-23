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

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoiceImage
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusBadge
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode

/** Undoing and redoing the survey mutations recorded in this Form editor. */
interface FormUndoRedoActions {
  /** Rewinds the most recent survey mutation recorded in this Form editor, if any. */
  fun undo()

  /** Replays the most recently rewound survey mutation in this Form editor, if any. */
  fun redo()
}

/** What the Form editor shows: the selection, preview target, language, and panels. */
interface FormSelectionActions {
  /** Selects question [key], or the Form itself when `null` or unknown. */
  fun select(key: String?)

  /** Selects the Form itself, showing Form properties. */
  fun selectForm()

  /** Selects section [sectionId] for properties editing, or deselects when `null`. */
  fun selectSection(sectionId: String?)

  /** Shows the Form settings (title, ID) in the properties panel instead of a question. */
  fun selectFormSettings()

  /** Switches the canvas and **Preview** between the mobile flow and the web layout. */
  fun selectPreviewTarget(target: FormPreviewTarget)

  /** Switches the active language being edited and previewed in the Form editor. */
  fun selectLanguage(language: String)

  fun setXmlViewerOpen(open: Boolean)

  fun setAdvancedExpanded(expanded: Boolean)

  /**
   * Sets the editor's right-hand properties panel width to [widthDp], clamped to
   * [FormEditorUiState.MIN_SIDE_PANEL_WIDTH_DP]..[FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP].
   * Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float)
}

/** The Form's own settings: title, button label, and where collectors can open it. */
interface FormSettingsActions {
  fun updateTitle(title: String)

  /** Sets the custom label shown on buttons that start data collection for this Form. */
  fun updateCtaLabel(ctaLabel: String)

  /** Sets where collectors can open this Form (mobile, web, both, or neither). */
  fun updateAvailability(availability: FormAvailability)

  /** Sets whether "Open in Google Earth" is enabled for this Form when available on web. */
  fun updateOpenInGoogleEarth(enabled: Boolean) {}

  /** Makes the Form available on the platform the canvas is previewing (the banner's action). */
  fun enableOnPreviewTarget()
}

/** The Form's questions: adding, ordering, and editing them. */
interface QuestionActions {
  /**
   * Inserts a new [type] question at [atIndex] (or after the selected one, or at the end if none
   * selected) and selects it.
   */
  fun addQuestion(type: EditorQuestionType, atIndex: Int? = null)

  fun addQuestion(
    type: EditorQuestionType,
    atIndex: Int?,
    sectionId: String?,
    afterSectionId: String? = null,
  )

  /** Inserts a copy of [key] right after it and selects the copy. */
  fun duplicateQuestion(key: String)

  /**
   * Duplicates question [key] along with all downstream questions whose conditional relevance
   * depends on it (transitively), remapping internal relevance and cascading parent keys.
   */
  fun duplicateQuestionBranch(key: String)

  /**
   * Removes [key]. Display logic on other questions that depended on it is cleared so the Form
   * stays valid, save-to references to it are dropped, and the selection moves to the nearest
   * remaining neighbor.
   */
  fun deleteQuestion(key: String)

  /** Moves [key] by [delta] positions (negative = earlier). */
  fun moveQuestion(key: String, delta: Int)

  /** Moves [key] so it lands at [targetIndex], optionally assigning to [targetSectionId]. */
  fun moveQuestionTo(key: String, targetIndex: Int, targetSectionId: String? = null)

  fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion)

  /** Updates the label of question [key] in the currently active language. */
  fun updateQuestionLabel(key: String, label: String)

  /** Updates the hint of question [key] in the currently active language. */
  fun updateQuestionHint(key: String, hint: String)

  /**
   * Changes the type of [key], seeding choices and dropping display logic, validation rules, and
   * type-specific settings (geometry capture, media source) that no longer fit.
   */
  fun changeType(key: String, type: EditorQuestionType)

  /** Sets how collectors record the geometry answer of [key] (ignored for non-geometry types). */
  fun updateCapture(key: String, capture: GeometryCapture)

  /** Sets how collectors provide the media answer of [key] (ignored for non-media types). */
  fun updateMediaSource(key: String, source: MediaSource)

  /**
   * Sets the number of decimal places allowed for [key] (`0` for whole numbers / integer; ignored
   * for non-numeric types).
   */
  fun updateDecimalPlaces(key: String, decimalPlaces: Int)

  /**
   * Updates the validation rule of [key]. A rule with no settings is stored as `null` so the
   * question exports without a `constraint`.
   */
  fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation)

  /** Turns display logic on for [key] using the nearest earlier question as a default source. */
  fun enableRelevance(key: String)

  fun disableRelevance(key: String)
}

/** Choices of select questions: manual lists, dataset-backed choices, and cascading filters. */
interface ChoiceActions {
  /**
   * Switches [key] between a manual choice list and choices pulled from a Map layer or Data table.
   */
  fun setChoiceSource(key: String, source: ChoiceSource)

  /** Sets the dataset ID that [key] pulls choices from (or clears it when `null`). */
  fun setChoiceDataset(key: String, datasetId: String?)

  /**
   * Sets whether data collectors may add a new map feature or table row inline while answering
   * dataset-backed choice question [key].
   */
  fun setAllowAddEntity(key: String, allow: Boolean)

  /**
   * Replaces the label of choice [index] of [key]. If the choice value was derived from its label,
   * it follows label edits; dependents comparing against the old value are rewritten to the new
   * one.
   */
  fun updateChoiceLabel(key: String, index: Int, label: String)

  fun updateChoice(key: String, index: Int, choice: EditorChoice)

  fun addChoice(key: String)

  fun removeChoice(key: String, index: Int)

  /** Sets (or with `null`, clears) the display color of choice [index] of [key]. */
  fun setChoiceColor(key: String, index: Int, colorHex: String?)

  /**
   * Attaches [image] to choice [index] of [key], or removes it when `null`. Returns `false` (and
   * changes nothing) if the image exceeds [EditorChoiceImage.MAX_BYTES] or isn't an image.
   */
  fun setChoiceImage(key: String, index: Int, image: EditorChoiceImage?): Boolean

  /**
   * Creates a new Map layer or Data table of [kind] and automatically binds question at
   * [questionIndex] (or [key]) to it, keeping the user in the Form editor.
   */
  fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind)

  fun createDatasetForChoiceQuestion(key: String, kind: DatasetKind)

  /**
   * Sets the cascading parent question for question [questionIndex] (or [key]). Choices in this
   * question can then be filtered based on the answer selected in the parent question.
   */
  fun updateQuestionParentKey(questionIndex: Int, parentQuestionKey: String)

  fun updateQuestionParentKey(key: String, parentQuestionKey: String)

  /**
   * Sets the parent value that must be selected in the parent question for choice [choiceIndex] of
   * question [questionIndex] (or [key]) to be shown.
   */
  fun updateChoiceParentValue(questionIndex: Int, choiceIndex: Int, parentValue: String)

  fun updateChoiceParentValue(key: String, choiceIndex: Int, parentValue: String)

  /**
   * Replaces (or with [append], adds) the choices of question [questionIndex] (or [key]) with
   * [importedChoices].
   */
  fun importQuestionChoices(
    questionIndex: Int,
    importedChoices: List<EditorChoice>,
    append: Boolean = false,
  )

  fun importQuestionChoices(
    key: String,
    importedChoices: List<EditorChoice>,
    append: Boolean = false,
  )

  /**
   * Reads the choices of question [key] from the CSV, TSV, or plain text file [fileName] with
   * contents [text]. A question with no choices, or only the two placeholder options a new select
   * question starts with, gets them right away; otherwise the user decides how to apply them with
   * [importQuestionChoices].
   */
  fun importChoicesFromFile(key: String, text: String, fileName: String): ChoiceFileImport
}

/** The Form's sections (singleton and repeated groups). */
interface SectionActions {
  /** Duplicates section [sectionId] and all questions inside it. */
  fun duplicateSection(sectionId: String)

  /** Selects section [sectionId] for properties editing, or deselects when `null`. */
  fun selectSection(sectionId: String?)

  /**
   * Adds a new section to the form at [atIndex] (or after the selected question/section, or at the
   * end if none is selected) and returns its unique ID.
   */
  fun addSection(
    title: String = "Section",
    repeatable: Boolean = false,
    atIndex: Int? = null,
    afterSectionId: String? = null,
  ): String

  /** Updates section [sectionId] with [transform]. */
  fun updateSection(sectionId: String, transform: (EditorSection) -> EditorSection)

  /** Deletes section [sectionId], removing it from the form and unassigning its questions. */
  fun deleteSection(sectionId: String)

  /**
   * Moves question at [questionIndex] (or [key]) into section [sectionId] (or out of every section
   * if blank), next to the section's other questions so sections stay contiguous.
   */
  fun setQuestionSection(questionIndex: Int, sectionId: String)

  fun setQuestionSection(key: String, sectionId: String)

  /**
   * Moves all of section [sectionId]'s questions past [delta] neighboring questions or sections
   * (negative = earlier).
   */
  fun moveSection(sectionId: String, delta: Int)

  /**
   * Makes section [sectionId] conditional: all its questions are shown only when its display logic
   * is true, testing the closest earlier question it can depend on.
   */
  fun enableSectionRelevance(sectionId: String)

  /** Removes section [sectionId]'s display logic, so its questions are always shown. */
  fun disableSectionRelevance(sectionId: String)
}

/** Where submissions are saved: the Map layer or Data table they add to or update. */
interface SaveToActions {
  /**
   * Switches between adding new features and updating existing ones. Switching to updates picks a
   * default target, feature lookup, and field mapping unless a valid target is already set.
   */
  fun setSaveToMode(mode: SaveToMode)

  /** Updates features of the dataset with ID [datasetId], resetting lookup and mappings. */
  fun setTargetDataset(datasetId: String)

  fun setIdSource(source: EntityIdSource)

  fun setIdQuestion(questionKey: String)

  fun setIdMatchProperty(property: String)

  /** Saves the answer to [questionKey] into [property], or stops saving it when `null`. */
  fun setMapping(questionKey: String, property: String?)
}

/** Conditional status markers that submissions set on the features they save to. */
interface StatusRuleActions {
  /** Turns conditional status marker rules on or off, seeding sensible defaults when empty. */
  fun setStatusEnabled(enabled: Boolean)

  /**
   * Adds a new status rule. If the last rule is the default catch-all ("When form is submitted"),
   * inserts the new rule right before it so specific conditions are evaluated first.
   */
  fun addStatusRule()

  fun updateStatusRule(index: Int, transform: (EditorStatusRule) -> EditorStatusRule)

  fun removeStatusRule(index: Int)

  fun moveStatusRule(index: Int, delta: Int)

  fun updateDefaultStatusBadge(transform: (EditorStatusBadge) -> EditorStatusBadge)
}

/** The in-browser preview that runs the generated Form. */
interface PreviewActions {
  fun startPreview()

  fun restartPreview()

  fun markPreviewSubmitted()

  fun closePreview()
}

/** Links from questions to the standard fields of the survey's dictionary. */
interface ConceptLinkActions {
  /**
   * Suggests standard fields for question [key] while its label is being typed (debounced by the
   * view): opens the top 5 matches for [query] once it has 3 or more characters, otherwise closes
   * the list. Never links anything by itself.
   */
  fun requestConceptSuggestions(key: String, query: String)

  /** Opens **Link to standard field…** for [key], searching its current label. */
  fun openConceptSearch(key: String)

  /** Updates the query of the open **Link to standard field…** search. */
  fun updateConceptSearchQuery(query: String)

  fun dismissConceptSuggestions()

  /**
   * Links [key] to [conceptId]. A question still in its default state also gets the concept's type,
   * name, choices, unit hint, and simple validation; the label is kept.
   */
  fun linkConcept(key: String, conceptId: String)

  fun unlinkConcept(key: String)

  /** Replaces [key]'s label with its linked concept's label (in the survey's language). */
  fun useStandardLabel(key: String)

  /**
   * Adds [key]'s label as a draft concept to the survey organization's dictionary (Managers only),
   * built from the question's type and choices, and links it. Returns an error message, or `null`.
   */
  fun addLabelToDictionary(key: String): FormEditorMessage?

  /** Maps choice [index] of linked question [key] to code-list value [code], or clears it. */
  fun setChoiceCode(key: String, index: Int, code: String?)

  /**
   * Opens the "We found N fields that match standard definitions" card for unlinked questions (used
   * after an import). Only exact or near-exact matches are pre-checked.
   */
  fun suggestImportMatches()

  fun setImportMatchChecked(key: String, checked: Boolean)

  /** Links every checked import match and closes the card. */
  fun linkImportMatches()

  fun dismissImportMatches()
}

/** Saving the Form as a template and applying template updates. */
interface TemplateActions {
  /**
   * Saves a copy of this Form, with its concept links and choice codes, as a template titled
   * [title] in the survey organization's library (or the global library when [toGlobal]). Returns
   * why it can't be saved, or `null`.
   */
  fun saveAsTemplate(title: String, description: String, toGlobal: Boolean = false): String?

  /**
   * Applies selected question additions ([addedQuestions]) and removals ([removedQuestionKeys])
   * from a regulatory or library template update after human-in-the-loop review.
   */
  fun applyTemplateUpdate(
    addedQuestions: List<EditorQuestion>,
    removedQuestionKeys: Set<String>,
  )
}

/**
 * User intents of the Form editor page, grouped into focused interfaces so each panel can depend on
 * just the ones it uses. Implemented by [FormEditorViewModel].
 */
interface FormEditorActions :
  FormUndoRedoActions,
  FormSelectionActions,
  FormSettingsActions,
  QuestionActions,
  ChoiceActions,
  SectionActions,
  SaveToActions,
  StatusRuleActions,
  PreviewActions,
  ConceptLinkActions,
  TemplateActions

/**
 * A user-facing Form editor message: a [StringResource] the UI resolves, or [Text] that is already
 * display-ready (e.g. an error passed through from another component).
 */
sealed interface FormEditorMessage {
  /** [resource] formatted with [args]. */
  data class Resource(val resource: StringResource, val args: List<String> = emptyList()) :
    FormEditorMessage

  data class Text(val text: String) : FormEditorMessage
}

/** Outcome of [ChoiceActions.importChoicesFromFile]. */
sealed interface ChoiceFileImport {
  /** The file has no choices (or the question is gone); nothing changed. */
  data object NothingToImport : ChoiceFileImport

  /** The question's choices were replaced with the file's. */
  data object Replaced : ChoiceFileImport

  /** The question already has choices: replace or append [choices]? Nothing changed yet. */
  data class NeedsConfirmation(val choices: List<EditorChoice>) : ChoiceFileImport
}
