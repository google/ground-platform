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
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceColors
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
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState

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
  )

  private val session =
    MutableStateFlow(Session(selectedKey = formFlow.value.questions.firstOrNull()?.key))

  private var nextKeyId = formFlow.value.questions.size + 1

  val uiState: StateFlow<FormEditorUiState> =
    combine(formFlow, datasetsFlow, session) { form, datasets, session ->
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
        )
      }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        FormEditorUiState(
          form = formFlow.value,
          datasets = datasetsFlow.value,
          selectedKey = session.value.selectedKey,
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
    val value = if (operator.needsValue) source.choices.firstOrNull()?.value.orEmpty() else ""
    updateQuestion(key) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  override fun disableRelevance(key: String) = updateQuestion(key) { it.copy(relevance = null) }

  // --- Choices ---

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
            defaultQuestion.choices.firstOrNull()?.value
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
        FormWizardController(
          formDef = XFormsXmlSerializer.deserializeFormDef(uiState.value.previewXml)
        )
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
