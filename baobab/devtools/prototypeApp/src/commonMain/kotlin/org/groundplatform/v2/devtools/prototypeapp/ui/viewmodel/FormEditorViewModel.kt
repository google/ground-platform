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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CodeListItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptAggregation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceColors
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ConceptLinking
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoiceImage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorRelevance
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorSaveTo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusRule
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorValidation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityIdSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.MediaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.StatusConditionSubject
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ValidationRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.slugify
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withRenamedTargetDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withRenamedTargetProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ConceptMatchKind
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchConceptsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ConceptSuggestion
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ConceptSuggestionsState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormLibraryContext
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ImportMatch

/** User intents of the Form editor page. Implemented by [FormEditorViewModel]. */
interface FormEditorActions {
  // --- Selection & panels ---

  /** Selects question [key], or the Form itself when `null` or unknown. */
  fun select(key: String?)

  /** Selects the Form itself, showing Form properties. */
  fun selectForm()

  /** Shows the Form settings (title, ID) in the properties panel instead of a question. */
  fun selectFormSettings()

  /** Switches the canvas and **Preview** between the mobile flow and the web layout. */
  fun selectPreviewTarget(target: FormPreviewTarget)

  fun setXmlViewerOpen(open: Boolean)

  fun setAdvancedExpanded(expanded: Boolean)

  /**
   * Sets the editor's right-hand properties panel width to [widthDp], clamped to
   * [FormEditorUiState.MIN_SIDE_PANEL_WIDTH_DP]..[FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP].
   * Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float)

  // --- Form ---

  fun updateTitle(title: String)

  /** Sets where collectors can open this Form (mobile, web, both, or neither). */
  fun updateAvailability(availability: FormAvailability)

  /** Makes the Form available on the platform the canvas is previewing (the banner's action). */
  fun enableOnPreviewTarget()

  /**
   * Switches every GPS-only geometry question to [GeometryCapture.GPS_OR_MAP] so the Form can be
   * answered in the web dashboard (the "Fix all" action).
   */
  fun makeGeometryQuestionsWebCompatible()

  // --- Questions ---

  /**
   * Inserts a new [type] question at [atIndex] (or after the selected one, or at the end if none
   * selected) and selects it.
   */
  fun addQuestion(type: EditorQuestionType, atIndex: Int? = null)

  /** Inserts a copy of [key] right after it and selects the copy. */
  fun duplicateQuestion(key: String)

  /**
   * Removes [key]. Display logic on other questions that depended on it is cleared so the Form
   * stays valid, save-to references to it are dropped, and the selection moves to the nearest
   * remaining neighbor.
   */
  fun deleteQuestion(key: String)

  /** Moves [key] by [delta] positions (negative = earlier). */
  fun moveQuestion(key: String, delta: Int)

  /** Moves [key] so it lands at [targetIndex]. */
  fun moveQuestionTo(key: String, targetIndex: Int)

  fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion)

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
   * Updates the validation rule of [key]. A rule with no settings is stored as `null` so the
   * question exports without a `constraint`.
   */
  fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation)

  /** Turns display logic on for [key] using the nearest earlier question as a default source. */
  fun enableRelevance(key: String)

  fun disableRelevance(key: String)

  // --- Choices ---

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

  // --- Save-to logic ---

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

  // --- Preview ---

  fun startPreview()

  fun restartPreview()

  fun markPreviewSubmitted()

  fun closePreview()

  // --- Dictionary (concept links) ---

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
  fun addLabelToDictionary(key: String): String?

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

/**
 * ViewModel of the Form editor page for one Form of the survey being edited.
 *
 * The Form itself lives in the Survey editor's draft: it's read from [formFlow] and every edit goes
 * through [updateForm], so the Survey editor sees it at once (and its linked datasets follow). The
 * survey's Map layers and Data tables, which the save-to logic targets, come from [datasetsFlow].
 * Selection and the preview session are this ViewModel's own.
 *
 * The preview's [FormWizardController] is UI infrastructure from `core.forms` (it runs the
 * generated XForms and holds the preview's answers); like the PDF export client, it's owned here
 * rather than by the view so the preview overlay stays passive.
 */
class FormEditorViewModel(
  private val formFlow: StateFlow<EditorForm>,
  private val datasetsFlow: StateFlow<List<EditorDataset>>,
  private val updateForm: (transform: (EditorForm) -> EditorForm) -> Unit,
  scope: CoroutineScope,
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
) : FormEditorActions {
  /** Session (non-persisted) state of the Form editor. */
  private data class Session(
    val selectedKey: String?,
    val previewTarget: FormPreviewTarget = FormPreviewTarget.MOBILE,
    val previewController: FormWizardController? = null,
    val previewError: String? = null,
    val previewSubmitted: Boolean = false,
    val isXmlViewerOpen: Boolean = false,
    val isAdvancedExpanded: Boolean = false,
    val sidePanelWidthDp: Float = FormEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
    val conceptSuggestions: ConceptSuggestionsState? = null,
    val importMatches: List<ImportMatch> = emptyList(),
  )

  private val session =
    MutableStateFlow(Session(selectedKey = formFlow.value.questions.firstOrNull()?.key))

  private var nextKeyId = formFlow.value.questions.size + 1

  val uiState: StateFlow<FormEditorUiState> =
    combine(formFlow, datasetsFlow, session, libraryFlow) { form, datasets, session, library ->
        FormEditorUiState(
          form = form,
          datasets = datasets,
          selectedKey = session.selectedKey,
          previewTarget = session.previewTarget,
          previewController = session.previewController,
          previewError = session.previewError,
          previewSubmitted = session.previewSubmitted,
          isXmlViewerOpen = session.isXmlViewerOpen,
          isAdvancedExpanded = session.isAdvancedExpanded,
          sidePanelWidthDp = session.sidePanelWidthDp,
          library = library,
          conceptSuggestions = session.conceptSuggestions,
          importMatches = session.importMatches,
        )
      }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        FormEditorUiState(
          form = formFlow.value,
          datasets = datasetsFlow.value,
          selectedKey = session.value.selectedKey,
          library = libraryFlow.value,
        ),
      )

  private val form: EditorForm
    get() = formFlow.value

  private val datasets: List<EditorDataset>
    get() = datasetsFlow.value

  private val selectedIndex: Int
    get() = session.value.selectedKey?.let { form.indexOf(it) } ?: -1

  private val saveTarget: EditorDataset?
    get() = SaveToRules.saveTarget(form, datasets)

  private val updateTargets: List<EditorDataset>
    get() = datasets.filterNot { it.isLinkedToThisForm }

  // --- Selection & panels ---

  override fun select(key: String?) {
    session.update { it.copy(selectedKey = key?.takeIf { k -> form.indexOf(k) >= 0 }) }
  }

  override fun selectForm() {
    session.update { it.copy(selectedKey = null) }
  }

  override fun selectFormSettings() = selectForm()

  override fun selectPreviewTarget(target: FormPreviewTarget) {
    session.update { it.copy(previewTarget = target) }
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

  override fun updateTitle(title: String) = updateForm { it.copy(title = title) }

  override fun updateAvailability(availability: FormAvailability) = updateForm {
    it.copy(availability = availability)
  }

  override fun enableOnPreviewTarget() {
    updateAvailability(
      when (session.value.previewTarget) {
        FormPreviewTarget.MOBILE -> form.availability.withMobile(true)
        FormPreviewTarget.WEB -> form.availability.withWeb(true)
      }
    )
  }

  override fun makeGeometryQuestionsWebCompatible() = updateForm { form ->
    form.copy(
      questions =
        form.questions.map {
          if (it.isWebIncompatible) it.copy(capture = GeometryCapture.GPS_OR_MAP) else it
        }
    )
  }

  // --- Questions ---

  override fun addQuestion(type: EditorQuestionType, atIndex: Int?) {
    val key = newKey()
    val question =
      EditorQuestion(
        key = key,
        name = uniqueName(type.defaultNamePrefix),
        type = type,
        label =
          if (type == EditorQuestionType.NOTE) "New note"
          else "New ${type.label.lowercase()} question",
        choices = if (type.hasChoices) defaultChoices() else emptyList(),
      )
    val insertAt =
      when {
        atIndex != null -> atIndex.coerceIn(0, form.questions.size)
        selectedIndex >= 0 -> selectedIndex + 1
        else -> form.questions.size
      }
    updateForm {
      it.copy(questions = it.questions.toMutableList().apply { add(insertAt, question) })
    }
    session.update { it.copy(selectedKey = key) }
  }

  override fun duplicateQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val original = form.questions[index]
    val copy = original.copy(key = newKey(), name = uniqueName(original.name))
    updateForm { it.copy(questions = it.questions.toMutableList().apply { add(index + 1, copy) }) }
    session.update { it.copy(selectedKey = copy.key) }
  }

  override fun deleteQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val remaining =
      form.questions
        .filterNot { it.key == key }
        .map { if (it.relevance?.sourceQuestionKey == key) it.copy(relevance = null) else it }
    updateForm { it.copy(questions = remaining, saveTo = it.saveTo.withoutQuestion(key)) }
    if (session.value.selectedKey == key) {
      session.update {
        it.copy(selectedKey = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key)
      }
    }
  }

  override fun moveQuestion(key: String, delta: Int) {
    val index = form.indexOf(key)
    val target = index + delta
    if (index < 0 || target !in form.questions.indices) return
    updateForm { form ->
      val list = form.questions.toMutableList()
      val item = list.removeAt(index)
      list.add(target, item)
      form.copy(questions = list)
    }
  }

  override fun moveQuestionTo(key: String, targetIndex: Int) {
    val index = form.indexOf(key)
    if (index < 0) return
    moveQuestion(key, targetIndex.coerceIn(0, form.questions.lastIndex) - index)
  }

  override fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion) =
    updateForm { form ->
      form.copy(questions = form.questions.map { if (it.key == key) transform(it) else it })
    }

  override fun changeType(key: String, type: EditorQuestionType) {
    updateQuestion(key) { q ->
      q.copy(
        type = type,
        choices = if (type.hasChoices && q.choices.isEmpty()) defaultChoices() else q.choices,
        choiceDatasetId = if (type.hasChoices) q.choiceDatasetId else null,
        choiceSource = if (type.hasChoices) q.choiceSource else ChoiceSource.MANUAL,
        allowAddEntity = if (type.hasChoices) q.allowAddEntity else false,
        required = q.required && !type.isReadOnly,
        validation = ValidationRules.adaptToType(q.type, type, q.validation),
        capture = if (type.isGeometry) q.capture else GeometryCapture.GPS_ONLY,
        mediaSource = if (type.isMedia) q.mediaSource else MediaSource.CAPTURE_OR_UPLOAD,
      )
    }
    // Dependents whose operator is incompatible with the new type fall back to "is answered".
    val allowed = RelevanceOperator.availableFor(type)
    updateForm { form ->
      form.copy(
        questions =
          form.questions.map { q ->
            val relevance = q.relevance
            if (relevance?.sourceQuestionKey == key && relevance.operator !in allowed) {
              q.copy(relevance = relevance.copy(operator = allowed.first(), value = ""))
            } else {
              q
            }
          },
        saveTo = saveToAfterTypeChange(form.saveTo, key, type),
      )
    }
  }

  private fun saveToAfterTypeChange(
    saveTo: EditorSaveTo,
    key: String,
    type: EditorQuestionType,
  ): EditorSaveTo {
    if (type == EditorQuestionType.NOTE) return saveTo.withoutQuestion(key)
    val allowed = RelevanceOperator.availableFor(type)
    val updatedMapping =
      if (!type.isGeometry && saveTo.propertyFor(key) == SaveToRules.GEOMETRY_PROPERTY) {
        saveTo.withMapping(key, null)
      } else {
        saveTo
      }
    return updatedMapping.copy(
      status =
        updatedMapping.status.copy(
          rules =
            updatedMapping.status.rules.map { rule ->
              if (
                rule.subject == StatusConditionSubject.QUESTION &&
                  rule.questionKey == key &&
                  rule.operator !in allowed
              ) {
                rule.copy(operator = allowed.first(), value = "")
              } else {
                rule
              }
            }
        )
    )
  }

  override fun updateCapture(key: String, capture: GeometryCapture) =
    updateQuestion(key) { it.copy(capture = capture) }

  override fun updateMediaSource(key: String, source: MediaSource) =
    updateQuestion(key) { it.copy(mediaSource = source) }

  override fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation) =
    updateQuestion(key) { q ->
      val updated = transform(q.validation ?: EditorValidation())
      q.copy(validation = updated.takeIf { it != EditorValidation() })
    }

  override fun enableRelevance(key: String) {
    val source = form.eligibleRelevanceSources(key).lastOrNull() ?: return
    val operator = RelevanceOperator.availableFor(source.type).first()
    val value =
      if (operator.needsValue) source.resolvedChoices(datasets).firstOrNull()?.value.orEmpty()
      else ""
    updateQuestion(key) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  override fun disableRelevance(key: String) = updateQuestion(key) { it.copy(relevance = null) }

  // --- Choices ---

  override fun setChoiceSource(key: String, source: ChoiceSource) =
    updateQuestion(key) { q ->
      when (source) {
        ChoiceSource.MANUAL ->
          q.copy(
            choiceSource = ChoiceSource.MANUAL,
            choiceDatasetId = null,
            allowAddEntity = false,
            choices = if (q.choices.isEmpty()) defaultChoices() else q.choices,
          )
        ChoiceSource.MAP_LAYER,
        ChoiceSource.DATA_TABLE -> {
          val wantMapLayer = source == ChoiceSource.MAP_LAYER
          val candidates =
            updateTargets
              .filter { it.isMapLayer == wantMapLayer }
              .ifEmpty { datasets.filter { it.isMapLayer == wantMapLayer } }
          val currentId = q.choiceDatasetId?.takeIf { id -> candidates.any { it.id == id } }
          val nextId = currentId ?: candidates.firstOrNull()?.id.orEmpty()
          val nextDataset = candidates.firstOrNull { it.id == nextId }
          val keepAllowAdd =
            q.allowAddEntity &&
              nextDataset != null &&
              !nextDataset.isGenerated &&
              (nextDataset.hasCreationForm || nextDataset.key.isEmpty())
          q.copy(
            choiceSource = source,
            choiceDatasetId = nextId,
            allowAddEntity = keepAllowAdd,
          )
        }
      }
    }

  override fun setChoiceDataset(key: String, datasetId: String?) =
    updateQuestion(key) { q ->
      val dataset = datasetId?.let { id -> datasets.firstOrNull { it.id == id } }
      val source =
        when {
          datasetId == null -> ChoiceSource.MANUAL
          dataset != null ->
            if (dataset.isMapLayer) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
          q.choiceSource.isDataset -> q.choiceSource
          else -> ChoiceSource.MAP_LAYER
        }
      val keepAllowAdd =
        q.allowAddEntity &&
          datasetId != null &&
          (dataset == null ||
            (!dataset.isGenerated && (dataset.hasCreationForm || dataset.key.isEmpty())))
      q.copy(
        choiceSource = source,
        choiceDatasetId = datasetId,
        allowAddEntity = keepAllowAdd,
      )
    }

  override fun setAllowAddEntity(key: String, allow: Boolean) =
    updateQuestion(key) { q ->
      if (!q.type.hasChoices || !q.usesDatasetChoices) q else q.copy(allowAddEntity = allow)
    }

  override fun updateChoiceLabel(key: String, index: Int, label: String) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val newValue =
      if (old.value == slugify(old.label, old.value)) slugify(label, old.value) else old.value
    updateChoice(key, index, old.copy(value = newValue, label = label))
  }

  override fun updateChoice(key: String, index: Int, choice: EditorChoice) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val choices = question.choices.toMutableList().apply { set(index, choice) }
    updateForm { form ->
      form.copy(
        questions =
          form.questions.map { q ->
            val relevance = q.relevance
            when {
              q.key == key -> q.copy(choices = choices)
              relevance?.sourceQuestionKey == key && relevance.value == old.value ->
                q.copy(relevance = relevance.copy(value = choice.value))
              else -> q
            }
          },
        saveTo =
          form.saveTo.copy(
            status =
              form.saveTo.status.copy(
                rules =
                  form.saveTo.status.rules.map { rule ->
                    if (
                      rule.subject == StatusConditionSubject.QUESTION &&
                        rule.questionKey == key &&
                        rule.value == old.value
                    ) {
                      rule.copy(value = choice.value)
                    } else {
                      rule
                    }
                  }
              )
          ),
      )
    }
  }

  override fun addChoice(key: String) =
    updateQuestion(key) { q ->
      val taken = q.choices.map { it.value }.toSet()
      var n = q.choices.size + 1
      while ("option_$n" in taken) n++
      q.copy(choices = q.choices + EditorChoice("option_$n", "Option $n"))
    }

  override fun removeChoice(key: String, index: Int) =
    updateQuestion(key) { q -> q.copy(choices = q.choices.filterIndexed { i, _ -> i != index }) }

  override fun setChoiceColor(key: String, index: Int, colorHex: String?) {
    val color = colorHex?.uppercase()?.takeIf(ChoiceColors::isValidHex)
    if (colorHex != null && color == null) return
    updateChoiceAt(key, index) { it.copy(colorHex = color) }
  }

  override fun setChoiceImage(key: String, index: Int, image: EditorChoiceImage?): Boolean {
    if (
      image != null &&
        (image.sizeBytes > EditorChoiceImage.MAX_BYTES || !image.mimeType.startsWith("image/"))
    ) {
      return false
    }
    if (form.find(key)?.choices?.getOrNull(index) == null) return false
    updateChoiceAt(key, index) { it.copy(image = image) }
    return true
  }

  private fun updateChoiceAt(key: String, index: Int, transform: (EditorChoice) -> EditorChoice) =
    updateQuestion(key) { q ->
      q.copy(choices = q.choices.mapIndexed { i, c -> if (i == index) transform(c) else c })
    }

  // --- Save-to logic ---

  override fun setSaveToMode(mode: SaveToMode) {
    val saveTo = form.saveTo
    if (saveTo.mode == mode) return
    val updated =
      if (mode == SaveToMode.UPDATE) withUpdateDefaults(saveTo.copy(mode = mode))
      else saveTo.copy(mode = mode)
    updateForm { it.copy(saveTo = updated) }
  }

  override fun setTargetDataset(datasetId: String) {
    val target = datasets.firstOrNull { it.id == datasetId } ?: return
    if (form.saveTo.targetDatasetId == datasetId) return
    val updated = defaultsFor(form.saveTo, target)
    updateForm { it.copy(saveTo = updated) }
  }

  override fun setIdSource(source: EntityIdSource) {
    val saveTo = form.saveTo
    if (saveTo.idSource == source) return
    val target = saveTarget
    val updated =
      if (source == EntityIdSource.QUESTION && target != null) {
        val question =
          form.find(saveTo.idQuestionKey) ?: SaveToRules.defaultIdQuestion(form, target)
        saveTo.copy(
          idSource = source,
          idQuestionKey = question?.key,
          idMatchProperty = saveTo.idMatchProperty ?: target.keyProperty,
        )
      } else {
        saveTo.copy(idSource = source)
      }
    updateForm { it.copy(saveTo = updated) }
  }

  override fun setIdQuestion(questionKey: String) = updateForm {
    it.copy(saveTo = it.saveTo.copy(idQuestionKey = questionKey))
  }

  override fun setIdMatchProperty(property: String) = updateForm {
    it.copy(saveTo = it.saveTo.copy(idMatchProperty = property))
  }

  override fun setMapping(questionKey: String, property: String?) = updateForm {
    it.copy(saveTo = it.saveTo.withMapping(questionKey, property))
  }

  /** Follows a rename of dataset [oldId] so this Form keeps updating it. */
  fun renameTargetDataset(oldId: String, newId: String) = updateForm {
    it.withRenamedTargetDataset(oldId, newId)
  }

  /**
   * Follows a rename of property [oldName] of dataset [datasetId] in lookup, mappings, and status
   * rules.
   */
  fun renameTargetProperty(datasetId: String, oldName: String, newName: String) {
    val target = saveTarget
    updateForm { it.withRenamedTargetProperty(datasetId, oldName, newName, target) }
  }

  override fun setStatusEnabled(enabled: Boolean) = updateForm { form ->
    val current = form.saveTo.status
    val rules =
      if (enabled && current.rules.isEmpty()) EditorStatusConfig.defaultRules() else current.rules
    form.copy(saveTo = form.saveTo.copy(status = current.copy(enabled = enabled, rules = rules)))
  }

  override fun addStatusRule() {
    val current = form.saveTo.status
    val savable = SaveToRules.savableQuestions(form)
    val defaultQuestion = savable.firstOrNull { it.type.hasChoices } ?: savable.firstOrNull()
    val newRule =
      if (defaultQuestion != null) {
        val op = RelevanceOperator.availableFor(defaultQuestion.type).first()
        val valDefault =
          if (op.needsValue) {
            defaultQuestion.resolvedChoices(datasets).firstOrNull()?.value
              ?: if (defaultQuestion.type.isNumeric) "1" else "yes"
          } else {
            ""
          }
        EditorStatusRule(
          subject = StatusConditionSubject.QUESTION,
          questionKey = defaultQuestion.key,
          operator = op,
          value = valDefault,
          badge = EditorStatusBadge.IN_PROGRESS,
        )
      } else {
        EditorStatusRule(
          subject = StatusConditionSubject.SUBMISSIONS,
          minSubmissions = 2,
          badge = EditorStatusBadge.SURVEYED,
        )
      }
    val list = current.rules.toMutableList()
    val lastIsCatchAll =
      list.lastOrNull()?.let {
        it.subject == StatusConditionSubject.SUBMISSIONS && it.minSubmissions <= 1
      } == true
    val insertIndex = if (lastIsCatchAll) list.lastIndex else list.size
    list.add(insertIndex, newRule)
    updateForm { it.copy(saveTo = it.saveTo.copy(status = current.copy(rules = list))) }
  }

  override fun updateStatusRule(index: Int, transform: (EditorStatusRule) -> EditorStatusRule) =
    updateForm { form ->
      val current = form.saveTo.status
      if (index !in current.rules.indices) return@updateForm form
      val updated = current.rules.mapIndexed { i, r -> if (i == index) transform(r) else r }
      form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = updated)))
    }

  override fun removeStatusRule(index: Int) = updateForm { form ->
    val current = form.saveTo.status
    if (index !in current.rules.indices) return@updateForm form
    val updated = current.rules.filterIndexed { i, _ -> i != index }
    form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = updated)))
  }

  override fun moveStatusRule(index: Int, delta: Int) = updateForm { form ->
    val current = form.saveTo.status
    val targetIndex = index + delta
    if (index !in current.rules.indices || targetIndex !in current.rules.indices) {
      return@updateForm form
    }
    val list = current.rules.toMutableList()
    val item = list.removeAt(index)
    list.add(targetIndex, item)
    form.copy(saveTo = form.saveTo.copy(status = current.copy(rules = list)))
  }

  override fun updateDefaultStatusBadge(transform: (EditorStatusBadge) -> EditorStatusBadge) =
    updateForm { form ->
      val current = form.saveTo.status
      form.copy(
        saveTo =
          form.saveTo.copy(status = current.copy(defaultBadge = transform(current.defaultBadge)))
      )
    }

  private fun withUpdateDefaults(saveTo: EditorSaveTo): EditorSaveTo {
    val targets = updateTargets
    if (targets.any { it.id == saveTo.targetDatasetId }) return saveTo
    val target = targets.firstOrNull() ?: return saveTo.copy(targetDatasetId = null)
    return defaultsFor(saveTo, target)
  }

  /** Map layers default to the feature selected on the map; Data tables to a question's answer. */
  private fun defaultsFor(saveTo: EditorSaveTo, target: EditorDataset): EditorSaveTo {
    val idQuestion = if (target.isMapLayer) null else SaveToRules.defaultIdQuestion(form, target)
    return saveTo.copy(
      targetDatasetId = target.id,
      idSource =
        if (target.isMapLayer) EntityIdSource.SELECTED_FEATURE else EntityIdSource.QUESTION,
      idQuestionKey = idQuestion?.key,
      idMatchProperty = target.keyProperty,
      mappings = SaveToRules.autoMap(form, target, skipKey = idQuestion?.key),
    )
  }

  // --- Preview ---

  override fun startPreview() {
    val controller =
      try {
        FormWizardController(formDef = uiState.value.previewForm.proto)
      } catch (e: Exception) {
        session.update {
          it.copy(
            previewSubmitted = false,
            previewController = null,
            previewError = e.message ?: e.toString(),
          )
        }
        return
      }
    session.update {
      it.copy(previewSubmitted = false, previewController = controller, previewError = null)
    }
  }

  override fun restartPreview() = startPreview()

  override fun markPreviewSubmitted() {
    session.update { it.copy(previewSubmitted = true) }
  }

  override fun closePreview() {
    session.update {
      it.copy(previewController = null, previewError = null, previewSubmitted = false)
    }
  }

  // --- Dictionary (concept links) ---

  private val library: FormLibraryContext
    get() = libraryFlow.value

  /** Top concepts for [query] for [question]: type-compatible ones first. */
  private fun suggestionsFor(question: EditorQuestion, query: String): List<ConceptSuggestion> {
    val context = library
    val results =
      searchConcepts(
        context.library.concepts,
        query,
        SearchConceptsUseCase.SearchOptions(
          limit = SUGGESTION_POOL,
          organizationId = context.library.organizationId,
          boostedConceptIds = context.purposeConceptIds,
        ),
      )
    return results
      .map { it.concept }
      .sortedBy { !it.dataType.isCompatibleWith(question.type) }
      .take(MAX_SUGGESTIONS)
      .map { concept ->
        ConceptSuggestion(
          concept = concept,
          sourceLabel = context.sourceLabel(concept),
          label = concept.label.get(context.language),
          description = concept.description.get(context.language),
          isTypeCompatible = concept.dataType.isCompatibleWith(question.type),
        )
      }
  }

  private fun addToDictionaryLabel(query: String): String? =
    query.trim().takeIf {
      it.isNotEmpty() && library.canAddToDictionary && library.library.organizationId != null
    }

  override fun requestConceptSuggestions(key: String, query: String) {
    val question = form.find(key) ?: return
    val current = session.value.conceptSuggestions
    if (current?.isExplicitSearch == true && current.questionKey == key) return
    val trimmed = query.trim()
    if (trimmed.length < MIN_SUGGESTION_QUERY || question.conceptLink != null) {
      if (current?.questionKey == key) session.update { it.copy(conceptSuggestions = null) }
      return
    }
    val suggestions = suggestionsFor(question, trimmed)
    session.update {
      it.copy(
        conceptSuggestions =
          if (suggestions.isEmpty() && addToDictionaryLabel(trimmed) == null) null
          else
            ConceptSuggestionsState(
              questionKey = key,
              query = trimmed,
              suggestions = suggestions,
              addToDictionaryLabel = addToDictionaryLabel(trimmed),
            )
      )
    }
  }

  override fun openConceptSearch(key: String) {
    val question = form.find(key) ?: return
    session.update {
      it.copy(
        conceptSuggestions =
          ConceptSuggestionsState(
            questionKey = key,
            query = question.label,
            suggestions = suggestionsFor(question, question.label),
            isExplicitSearch = true,
            addToDictionaryLabel = addToDictionaryLabel(question.label),
          )
      )
    }
  }

  override fun updateConceptSearchQuery(query: String) {
    val current = session.value.conceptSuggestions ?: return
    val question = form.find(current.questionKey) ?: return
    session.update {
      it.copy(
        conceptSuggestions =
          current.copy(
            query = query,
            suggestions = suggestionsFor(question, query),
            addToDictionaryLabel = addToDictionaryLabel(question.label),
          )
      )
    }
  }

  override fun dismissConceptSuggestions() {
    session.update { it.copy(conceptSuggestions = null) }
  }

  override fun linkConcept(key: String, conceptId: String) {
    val concept = library.concept(conceptId) ?: return
    val takenNames = form.questions.map { it.name }.toSet()
    updateQuestion(key) { q -> ConceptLinking.link(q, concept, takenNames, library.language) }
    session.update { it.copy(conceptSuggestions = null) }
  }

  override fun unlinkConcept(key: String) =
    updateQuestion(key) { q ->
      q.copy(conceptLink = null, choices = q.choices.map { it.copy(code = null) })
    }

  override fun useStandardLabel(key: String) {
    val question = form.find(key) ?: return
    val concept = library.concept(question.conceptLink?.conceptId) ?: return
    updateQuestion(key) { it.copy(label = concept.label.get(library.language)) }
  }

  override fun addLabelToDictionary(key: String): String? {
    val question = form.find(key) ?: return "Question not found."
    val organizationId = library.library.organizationId
    if (!library.canAddToDictionary || organizationId == null) {
      return "Only Managers of this survey's organization can add to its dictionary."
    }
    val label = question.label.trim()
    if (label.isEmpty()) return "Enter a label first."
    val dataType = ConceptLinking.conceptTypeFor(question.type) ?: return "Notes can't be linked."
    val name = LibraryIds.nameFrom(label)
    val codeList =
      if (dataType.hasCodeList && !question.usesDatasetChoices) {
        question.choices.map { choice ->
          CodeListItem(
            code =
              choice.code ?: LibraryIds.nameFrom(choice.value.ifBlank { choice.label }, "value"),
            label = LocalizedText.en(choice.label.ifBlank { choice.value }),
          )
        }
      } else {
        emptyList()
      }
    val concept =
      LibraryConcept(
        id = LibraryIds.organizationEntryId(organizationId, name),
        organizationId = organizationId,
        label = LocalizedText.en(label),
        dataType = dataType,
        description =
          LocalizedText.en(question.hint.trim()).takeUnless { question.hint.isBlank() }
            ?: LocalizedText(),
        codeList = codeList,
        aggregation =
          when {
            dataType.hasCodeList -> ConceptAggregation.COUNT_BY_CODE
            question.type.isNumeric -> ConceptAggregation.SUM
            else -> ConceptAggregation.NONE
          },
        status = LibraryStatus.DRAFT,
      )
    addOrganizationConcept(concept)?.let {
      return it
    }
    updateQuestion(key) { q ->
      q.copy(
        conceptLink = ConceptLink.to(concept),
        choices =
          if (codeList.isEmpty()) q.choices
          else q.choices.mapIndexed { i, c -> c.copy(code = codeList.getOrNull(i)?.code) },
      )
    }
    session.update { it.copy(conceptSuggestions = null) }
    return null
  }

  override fun setChoiceCode(key: String, index: Int, code: String?) =
    updateChoiceAt(key, index) { it.copy(code = code?.takeIf { c -> c.isNotBlank() }) }

  override fun suggestImportMatches() {
    val context = library
    val matches =
      form.questions
        .filter { it.conceptLink == null && it.type != EditorQuestionType.NOTE }
        .mapNotNull { question ->
          val best =
            searchConcepts(
                context.library.concepts,
                question.label,
                SearchConceptsUseCase.SearchOptions(
                  questionType = question.type,
                  limit = 1,
                  organizationId = context.library.organizationId,
                  boostedConceptIds = context.purposeConceptIds,
                ),
              )
              .firstOrNull()
              ?: searchConcepts(
                  context.library.concepts,
                  question.name.replace('_', ' '),
                  SearchConceptsUseCase.SearchOptions(questionType = question.type, limit = 1),
                )
                .firstOrNull()
              ?: return@mapNotNull null
          if (best.kind == ConceptMatchKind.FUZZY && best.score < MIN_IMPORT_FUZZY_SCORE) {
            return@mapNotNull null
          }
          ImportMatch(
            questionKey = question.key,
            questionLabel = question.label,
            concept = best.concept,
            sourceLabel = context.sourceLabel(best.concept),
            isStrong = best.kind == ConceptMatchKind.EXACT || best.kind == ConceptMatchKind.PREFIX,
          )
        }
        .distinctBy { it.concept.id }
    session.update { it.copy(importMatches = matches) }
  }

  override fun setImportMatchChecked(key: String, checked: Boolean) {
    session.update { s ->
      s.copy(
        importMatches =
          s.importMatches.map { if (it.questionKey == key) it.copy(isChecked = checked) else it }
      )
    }
  }

  override fun linkImportMatches() {
    session.value.importMatches
      .filter { it.isChecked }
      .forEach { linkConcept(it.questionKey, it.concept.id) }
    dismissImportMatches()
  }

  override fun dismissImportMatches() {
    session.update { it.copy(importMatches = emptyList()) }
  }

  // --- Helpers ---

  private fun newKey(): String {
    var candidate: String
    do {
      candidate = "q${nextKeyId++}"
    } while (form.indexOf(candidate) >= 0)
    return candidate
  }

  private fun uniqueName(base: String): String {
    val taken = form.questions.map { it.name }.toSet()
    val stem = base.replace(Regex("_\\d+$"), "")
    if (stem !in taken) return stem
    var n = 2
    while ("${stem}_$n" in taken) n++
    return "${stem}_$n"
  }

  private fun defaultChoices(): List<EditorChoice> =
    listOf(EditorChoice("option_1", "Option 1"), EditorChoice("option_2", "Option 2"))

  companion object {
    /** Characters typed into a label before standard fields are suggested. */
    const val MIN_SUGGESTION_QUERY = 3

    /** Suggestions shown under the Label field. */
    const val MAX_SUGGESTIONS = 5

    private const val SUGGESTION_POOL = 25

    /** Weakest fuzzy match offered after an import (a single typo in a label word). */
    private const val MIN_IMPORT_FUZZY_SCORE = 18.0

    /**
     * A Form editor over its own copy of [initialForm] with a fixed dataset catalog, for the
     * standalone Form editor and tests. Edits stay in the returned ViewModel.
     */
    fun standalone(
      initialForm: EditorForm =
        EditorFormTemplates.blank(formId = FormIds.newFormId(), title = "Untitled form"),
      datasets: List<EditorDataset> = emptyList(),
      scope: CoroutineScope,
    ): FormEditorViewModel {
      val form = MutableStateFlow(initialForm)
      return FormEditorViewModel(
        formFlow = form,
        datasetsFlow = MutableStateFlow(datasets),
        updateForm = { transform -> form.update(transform) },
        scope = scope,
      )
    }
  }
}
