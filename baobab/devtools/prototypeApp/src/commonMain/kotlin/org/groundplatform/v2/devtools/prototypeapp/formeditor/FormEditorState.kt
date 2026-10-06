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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability

/**
 * Observable state holder for the Form editor page: the [EditorForm] under edit, the selected
 * screen, and the in-browser flow preview session.
 *
 * [datasetCatalog] lists the survey's Map layers and Data tables, which the Form's save-to logic
 * adds features to or updates.
 */
class FormEditorState(
  initialForm: EditorForm =
    EditorFormTemplates.blank(formId = FormIds.newFormId(), title = "Untitled form"),
  private val datasetCatalog: () -> List<EditorDataset> = { emptyList() },
) {

  var form: EditorForm by mutableStateOf(initialForm)
    private set

  /** Selected question, or `null` when the Form itself is selected (Form properties). */
  var selectedKey: String? by mutableStateOf(initialForm.questions.firstOrNull()?.key)
    private set

  /** Live preview runner, or `null` when the preview overlay is closed. */
  var previewController: FormWizardController? by mutableStateOf(null)
    private set

  /** Message shown when the generated XForms cannot be parsed for preview. */
  var previewError: String? by mutableStateOf(null)
    private set

  /** Whether the preview reached a validated submission. */
  var previewSubmitted: Boolean by mutableStateOf(false)
    private set

  var isXmlViewerOpen: Boolean by mutableStateOf(false)

  /**
   * Whether the question panel's "Advanced" section is open. Kept here, like the app's other panel
   * expansion flags, so it stays open or closed as the author moves between questions.
   */
  var isAdvancedExpanded: Boolean by mutableStateOf(false)

  /**
   * Which platform the editor's canvas and **Preview** reproduce: the mobile
   * one-question-per-screen flow, or the web dashboard's stacked read-only fields
   * ([FormPreviewTarget.WEB]).
   */
  var previewTarget: FormPreviewTarget by mutableStateOf(FormPreviewTarget.MOBILE)
    private set

  /** Switches the canvas and **Preview** between the mobile flow and the web layout. */
  fun selectPreviewTarget(target: FormPreviewTarget) {
    previewTarget = target
  }

  /**
   * Parses the current [previewXml] into a fresh [FormWizardController] for the web canvas and the
   * preview. Returns the parse error message instead when the generated XForms don't load. Callers
   * cache the result per XML (e.g. `remember(previewXml)`), so the canvas tracks every edit.
   */
  fun parsePreviewController(): Result<FormWizardController> = runCatching {
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(previewXml))
  }

  /**
   * Instance path (`/data/<name>`) of question [key] in the generated XForms, used to match the
   * compact web cards to editor questions.
   */
  fun pathOf(key: String): String? = form.find(key)?.let { "/data/${it.name}" }

  /** Editor question whose generated instance node is [path], if any. */
  fun keyForPath(path: String): String? =
    form.questions.firstOrNull { "/data/${it.name}" == path }?.key

  private var nextKeyId = initialForm.questions.size + 1

  val selectedQuestion: EditorQuestion?
    get() = form.find(selectedKey)

  val selectedIndex: Int
    get() = selectedKey?.let { form.indexOf(it) } ?: -1

  /** Whether the Form itself (rather than a question) is selected. */
  val isFormSelected: Boolean
    get() = selectedQuestion == null

  val flowEdges: List<FlowEdge>
    get() = FormFlowGraph.edges(form)

  val pathCount: Long
    get() = FormFlowGraph.countPaths(form)

  /** The survey's Map layers and Data tables. */
  val datasets: List<EditorDataset>
    get() = datasetCatalog()

  /** Dataset a submission adds a feature to or updates, if any. */
  val saveTarget: EditorDataset?
    get() = SaveToRules.saveTarget(form, datasets)

  /** Datasets this Form can update: all but the one it adds features to. */
  val updateTargets: List<EditorDataset>
    get() = datasets.filterNot { it.isLinkedToThisForm }

  val issues: List<EditorIssue>
    get() = FormEditorValidator.validate(form) + SaveToValidator.validate(form, datasets)

  /** Issues that aren't about a single question, shown in Form properties. */
  val formIssues: List<EditorIssue>
    get() = issues.filter { it.questionKey == null }

  /** Exported XForms; updates reference the target's features as a CSV attachment. */
  val xformsXml: String
    get() = EditorXFormsGenerator.toXml(form, saveTarget)

  /** XForms run by the preview, with the target's features embedded. */
  val previewXml: String
    get() = EditorXFormsGenerator.toXml(form, saveTarget, inlineRows = true)

  fun issuesFor(key: String): List<EditorIssue> = issues.filter { it.questionKey == key }

  fun select(key: String?) {
    selectedKey = key?.takeIf { form.indexOf(it) >= 0 }
  }

  /** Selects the Form itself, showing Form properties. */
  fun selectForm() {
    selectedKey = null
  }

  fun updateTitle(title: String) {
    form = form.copy(title = title)
  }

  /** Sets where collectors can open this Form (mobile, web, both, or neither). */
  fun updateAvailability(availability: FormAvailability) {
    form = form.copy(availability = availability)
  }

  /** Whether collectors can open this Form on the platform the canvas is previewing. */
  val isEnabledOnPreviewTarget: Boolean
    get() =
      when (previewTarget) {
        FormPreviewTarget.MOBILE -> form.availability.includesMobile
        FormPreviewTarget.WEB -> form.availability.includesWeb
      }

  /** Makes the Form available on the platform the canvas is previewing (the banner's action). */
  fun enableOnPreviewTarget() {
    updateAvailability(
      when (previewTarget) {
        FormPreviewTarget.MOBILE -> form.availability.withMobile(true)
        FormPreviewTarget.WEB -> form.availability.withWeb(true)
      }
    )
  }

  /**
   * Inserts a new [type] question at [atIndex] (or after the selected one, or at the end if none
   * selected) and selects it.
   */
  fun addQuestion(type: EditorQuestionType, atIndex: Int? = null) {
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
    form = form.copy(questions = form.questions.toMutableList().apply { add(insertAt, question) })
    selectedKey = key
  }

  /** Inserts a copy of [key] right after it and selects the copy. */
  fun duplicateQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val original = form.questions[index]
    val copy = original.copy(key = newKey(), name = uniqueName(original.name))
    form = form.copy(questions = form.questions.toMutableList().apply { add(index + 1, copy) })
    selectedKey = copy.key
  }

  /**
   * Removes [key]. Display logic on other questions that depended on it is cleared so the Form
   * stays valid, save-to references to it are dropped, and the selection moves to the nearest
   * remaining neighbor.
   */
  fun deleteQuestion(key: String) {
    val index = form.indexOf(key)
    if (index < 0) return
    val remaining =
      form.questions
        .filterNot { it.key == key }
        .map { if (it.relevance?.sourceQuestionKey == key) it.copy(relevance = null) else it }
    form = form.copy(questions = remaining, saveTo = form.saveTo.withoutQuestion(key))
    if (selectedKey == key) {
      selectedKey = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key
    }
  }

  /** Moves [key] by [delta] positions (negative = earlier). */
  fun moveQuestion(key: String, delta: Int) {
    val index = form.indexOf(key)
    val target = index + delta
    if (index < 0 || target !in form.questions.indices) return
    val list = form.questions.toMutableList()
    val item = list.removeAt(index)
    list.add(target, item)
    form = form.copy(questions = list)
  }

  /** Moves [key] so it lands at [targetIndex]. */
  fun moveQuestionTo(key: String, targetIndex: Int) {
    val index = form.indexOf(key)
    if (index < 0) return
    moveQuestion(key, targetIndex.coerceIn(0, form.questions.lastIndex) - index)
  }

  fun updateQuestion(key: String, transform: (EditorQuestion) -> EditorQuestion) {
    form = form.copy(questions = form.questions.map { if (it.key == key) transform(it) else it })
  }

  /**
   * Replaces choice [index] of [key]. If the choice value was derived from its label, it follows
   * label edits; dependents comparing against the old value are rewritten to the new one.
   */
  fun updateChoiceLabel(key: String, index: Int, label: String) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val newValue =
      if (old.value == slugify(old.label, old.value)) slugify(label, old.value) else old.value
    updateChoice(key, index, old.copy(value = newValue, label = label))
  }

  fun updateChoice(key: String, index: Int, choice: EditorChoice) {
    val question = form.find(key) ?: return
    val old = question.choices.getOrNull(index) ?: return
    val choices = question.choices.toMutableList().apply { set(index, choice) }
    form =
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
          }
      )
  }

  fun addChoice(key: String) {
    updateQuestion(key) { q ->
      val taken = q.choices.map { it.value }.toSet()
      var n = q.choices.size + 1
      while ("option_$n" in taken) n++
      q.copy(choices = q.choices + EditorChoice("option_$n", "Option $n"))
    }
  }

  fun removeChoice(key: String, index: Int) {
    updateQuestion(key) { q -> q.copy(choices = q.choices.filterIndexed { i, _ -> i != index }) }
  }

  /** Sets (or with `null`, clears) the display color of choice [index] of [key]. */
  fun setChoiceColor(key: String, index: Int, colorHex: String?) {
    val color = colorHex?.uppercase()?.takeIf(ChoiceColors::isValidHex)
    if (colorHex != null && color == null) return
    updateChoiceAt(key, index) { it.copy(colorHex = color) }
  }

  /**
   * Attaches [image] to choice [index] of [key], or removes it when `null`. Returns `false` (and
   * changes nothing) if the image exceeds [EditorChoiceImage.MAX_BYTES] or isn't an image.
   */
  fun setChoiceImage(key: String, index: Int, image: EditorChoiceImage?): Boolean {
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

  private fun updateChoiceAt(key: String, index: Int, transform: (EditorChoice) -> EditorChoice) {
    updateQuestion(key) { q ->
      q.copy(choices = q.choices.mapIndexed { i, c -> if (i == index) transform(c) else c })
    }
  }

  /** Shows the Form settings (title, ID) in the properties panel instead of a question. */
  fun selectFormSettings() {
    selectedKey = null
  }

  /** Whether the properties panel shows Form settings rather than a question. */
  val isFormSettingsSelected: Boolean
    get() = selectedQuestion == null

  /**
   * Changes the type of [key], seeding choices and dropping display logic and validation rules that
   * no longer fit.
   */
  fun changeType(key: String, type: EditorQuestionType) {
    updateQuestion(key) { q ->
      q.copy(
        type = type,
        choices = if (type.hasChoices && q.choices.isEmpty()) defaultChoices() else q.choices,
        required = q.required && !type.isReadOnly,
        validation = ValidationRules.adaptToType(q.type, type, q.validation),
      )
    }
    // Dependents whose operator is incompatible with the new type fall back to "is answered".
    val allowed = RelevanceOperator.availableFor(type)
    form =
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

  private fun saveToAfterTypeChange(
    saveTo: EditorSaveTo,
    key: String,
    type: EditorQuestionType,
  ): EditorSaveTo =
    when {
      type == EditorQuestionType.NOTE -> saveTo.withoutQuestion(key)
      type != EditorQuestionType.LOCATION &&
        saveTo.propertyFor(key) == SaveToRules.GEOMETRY_PROPERTY -> saveTo.withMapping(key, null)
      else -> saveTo
    }

  // Save-to logic ------------------------------------------------------------------------------

  /**
   * Switches between adding new features and updating existing ones. Switching to updates picks a
   * default target, feature lookup, and field mapping unless a valid target is already set.
   */
  fun setSaveToMode(mode: SaveToMode) {
    val saveTo = form.saveTo
    if (saveTo.mode == mode) return
    form =
      form.copy(
        saveTo =
          if (mode == SaveToMode.UPDATE) withUpdateDefaults(saveTo.copy(mode = mode))
          else saveTo.copy(mode = mode)
      )
  }

  /** Updates features of the dataset with ID [datasetId], resetting lookup and mappings. */
  fun setTargetDataset(datasetId: String) {
    val target = datasets.firstOrNull { it.id == datasetId } ?: return
    if (form.saveTo.targetDatasetId == datasetId) return
    form = form.copy(saveTo = defaultsFor(form.saveTo, target))
  }

  fun setIdSource(source: EntityIdSource) {
    val saveTo = form.saveTo
    if (saveTo.idSource == source) return
    val target = saveTarget
    form =
      form.copy(
        saveTo =
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
      )
  }

  fun setIdQuestion(questionKey: String) {
    form = form.copy(saveTo = form.saveTo.copy(idQuestionKey = questionKey))
  }

  fun setIdMatchProperty(property: String) {
    form = form.copy(saveTo = form.saveTo.copy(idMatchProperty = property))
  }

  /** Saves the answer to [questionKey] into [property], or stops saving it when `null`. */
  fun setMapping(questionKey: String, property: String?) {
    form = form.copy(saveTo = form.saveTo.withMapping(questionKey, property))
  }

  /** Follows a rename of dataset [oldId] so this Form keeps updating it. */
  fun renameTargetDataset(oldId: String, newId: String) {
    val saveTo = form.saveTo
    if (saveTo.targetDatasetId != oldId) return
    form = form.copy(saveTo = saveTo.copy(targetDatasetId = newId))
  }

  /** Follows a rename of property [oldName] of dataset [datasetId] in lookup and mappings. */
  fun renameTargetProperty(datasetId: String, oldName: String, newName: String) {
    val saveTo = form.saveTo
    if (saveTo.targetDatasetId != datasetId) return
    form =
      form.copy(
        saveTo =
          saveTo.copy(
            idMatchProperty =
              if (saveTo.idMatchProperty == oldName) newName else saveTo.idMatchProperty,
            mappings =
              saveTo.mappings.map {
                if (it.property == oldName) it.copy(property = newName) else it
              },
          )
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

  /**
   * Updates the validation rule of [key]. A rule with no settings is stored as `null` so the
   * question exports without a `constraint`.
   */
  fun updateValidation(key: String, transform: (EditorValidation) -> EditorValidation) {
    updateQuestion(key) { q ->
      val updated = transform(q.validation ?: EditorValidation())
      q.copy(validation = updated.takeIf { it != EditorValidation() })
    }
  }

  /** Turns display logic on for [key] using the nearest earlier question as a default source. */
  fun enableRelevance(key: String) {
    val source = form.eligibleRelevanceSources(key).lastOrNull() ?: return
    val operator = RelevanceOperator.availableFor(source.type).first()
    val value = if (operator.needsValue) source.choices.firstOrNull()?.value.orEmpty() else ""
    updateQuestion(key) { it.copy(relevance = EditorRelevance(source.key, operator, value)) }
  }

  fun disableRelevance(key: String) {
    updateQuestion(key) { it.copy(relevance = null) }
  }

  fun startPreview() {
    previewSubmitted = false
    previewController =
      try {
        previewError = null
        FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(previewXml))
      } catch (e: Exception) {
        previewError = e.message ?: e.toString()
        null
      }
  }

  fun restartPreview() = startPreview()

  fun markPreviewSubmitted() {
    previewSubmitted = true
  }

  fun closePreview() {
    previewController = null
    previewError = null
    previewSubmitted = false
  }

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
}
