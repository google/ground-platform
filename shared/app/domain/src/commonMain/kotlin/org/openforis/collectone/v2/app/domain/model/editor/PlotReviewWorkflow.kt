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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.math.ceil
import org.openforis.collectone.v2.app.domain.model.FormAvailability

/**
 * How the sample plots of a generated Map layer are reviewed: interpreters first check each plot on
 * imagery in the web dashboard and, optionally, field crews then verify the plots that need it in
 * the mobile app (see `docs/product/ceo-integration.md`, "Base Case User Journey").
 */
data class PlotReviewOptions(
  /** Whether plots that need a visit are verified in the field with a mobile-only Form. */
  val fieldValidation: Boolean = false,
  /**
   * Desk confidence (0–100) below which plots go to the field automatically, or `0` to only send
   * the plots interpreters flag. Only used with [fieldValidation].
   */
  val autoFlagBelowConfidence: Int = DEFAULT_CONFIDENCE_THRESHOLD,
  /**
   * How many randomly chosen plots also go to the field, so the accuracy of desk checks can be
   * measured. Picked by the plots' shuffled `sample_order`. Only used with [fieldValidation].
   */
  val spotCheckCount: Int = 0,
  /**
   * Whether field crews see the desk answers on each plot. Off keeps field checks blind: the desk
   * answers are only kept in the desk check's submissions. Only used with [fieldValidation];
   * desk-only reviews always save them to the plot.
   */
  val showDeskAnswersInField: Boolean = true,
) {
  companion object {
    const val DEFAULT_CONFIDENCE_THRESHOLD = 50
  }
}

/**
 * The review Forms of a generated Map layer in a survey draft, and the [options] they implement.
 */
data class PlotReviewSetup(
  /** Key of the web-only Form interpreters fill in for each plot, or `null` if there's none. */
  val deskFormKey: String? = null,
  /** Key of the mobile-only Form field crews fill in, or `null` if there's none. */
  val fieldFormKey: String? = null,
  val options: PlotReviewOptions = PlotReviewOptions(),
) {
  /** Whether plots are checked on imagery, i.e. the desk Form exists. */
  val isEnabled: Boolean
    get() = deskFormKey != null
}

/**
 * Sets up the Forms that review the sample plots of a generated Map layer. Pure.
 * - **Desk check** (web only) updates each plot with what interpreters see on imagery and their
 *   confidence, and marks it `✓ Desk verified`, or `! Needs field validation` when it should be
 *   verified in the field (flagged by the interpreter, below the confidence threshold, or picked as
 *   a random spot check). It's either a new **Desk visual check** Form or an existing Form that
 *   updates the plots (such as the create-survey wizard's interpretation Form), adopted as is.
 * - **Field verification** (mobile only, optional) updates plots with what crews find on the
 *   ground, and marks them `✓ Field verified` or `✕ Not reachable`.
 *
 * The Forms are the setup's only record: [setup] reads it back from the Forms that update the layer
 * and have the review questions (found by name), so organizers can keep editing the Forms. [apply]
 * adds or removes the review questions and rewrites the parts of the Forms the review relies on:
 * where they're available, what they save to the plots, and their status rules.
 */
object PlotReviewWorkflow {
  /** Desk question (and plot property): land cover seen on imagery. */
  const val DESK_LAND_COVER = "desk_land_cover"

  /** Desk question (and plot property): interpreter's confidence, 0–100. */
  const val DESK_CONFIDENCE = "desk_confidence"

  /** Desk question: whether the plot needs a field visit. */
  const val NEEDS_FIELD_CHECK = "needs_field_check"

  /** Desk question (and plot property): what the field crew should check. */
  const val FLAG_REASON = "flag_reason"

  /** Field question (and plot property): whether the crew could reach the plot. */
  const val PLOT_REACHED = "plot_reached"

  /** Field question: the crew's location at the plot center (keeps the plot's geometry). */
  const val FIELD_LOCATION = "field_location"

  /** Field question (and plot property): land cover found on the ground. */
  const val FIELD_LAND_COVER = "field_land_cover"

  /** Field question: photo of the plot. */
  const val FIELD_PHOTO = "field_photo"

  /** Field question (and plot property): the crew's notes. */
  const val FIELD_NOTES = "field_notes"

  const val YES = "yes"
  const val NO = "no"

  const val DESK_FORM_TITLE = "Desk visual check"
  const val FIELD_FORM_TITLE = "Field verification"

  /** Spot checks [suggestedSpotCheckCount] suggests before plots are generated. */
  private const val DEFAULT_SPOT_CHECKS = 10

  /** Most spot checks [suggestedSpotCheckCount] suggests. */
  private const val MAX_SUGGESTED_SPOT_CHECKS = 100

  val NEEDS_FIELD_VALIDATION =
    EditorStatusBadge(StatusMarkerSymbol.ALERT, "#D13135", "Needs field validation")
  val DESK_VERIFIED = EditorStatusBadge(StatusMarkerSymbol.CHECK, "#2278CF", "Desk verified")
  val FIELD_VERIFIED = EditorStatusBadge(StatusMarkerSymbol.CHECK, "#3C8D40", "Field verified")
  val NOT_REACHABLE = EditorStatusBadge(StatusMarkerSymbol.CROSS, "#80868B", "Not reachable")

  /** IPCC land-use categories: the land cover classes both review Forms start with. */
  val LAND_COVER_CHOICES: List<EditorChoice> =
    listOf(
      EditorChoice("forest_land", "Forest land", colorHex = "#3C8D40"),
      EditorChoice("cropland", "Cropland", colorHex = "#F9BF40"),
      EditorChoice("grassland", "Grassland", colorHex = "#F37C22"),
      EditorChoice("wetlands", "Wetlands", colorHex = "#2278CF"),
      EditorChoice("settlements", "Settlements", colorHex = "#D13135"),
      EditorChoice("other_land", "Other land", colorHex = "#8D6E63"),
    )

  private val YES_NO = listOf(EditorChoice(YES, "Yes"), EditorChoice(NO, "No"))

  /**
   * Questions only a desk check has; a Form updating the layer with one of them is its desk check.
   */
  private val DESK_QUESTIONS = setOf(DESK_LAND_COVER, DESK_CONFIDENCE, NEEDS_FIELD_CHECK)

  /** Questions the review adds to a desk check, removed again when the review is turned off. */
  private val ADDED_DESK_QUESTIONS = setOf(DESK_CONFIDENCE, NEEDS_FIELD_CHECK, FLAG_REASON)

  /** Plot properties the review Forms may write; dropped from the layer once nothing uses them. */
  private val REVIEW_PROPERTIES =
    setOf(
      DESK_LAND_COVER,
      DESK_CONFIDENCE,
      FLAG_REASON,
      PLOT_REACHED,
      FIELD_LAND_COVER,
      FIELD_NOTES,
    )

  /**
   * Random spot checks suggested for a layer of [plotCount] plots: 10% of them (1 to 100), or 10
   * before plots are generated.
   */
  fun suggestedSpotCheckCount(plotCount: Int): Int =
    if (plotCount <= 0) {
      DEFAULT_SPOT_CHECKS
    } else {
      ceil(plotCount / 10.0).toInt().coerceIn(1, MAX_SUGGESTED_SPOT_CHECKS)
    }

  /**
   * The review setup of layer [datasetKey] in [draft]. Without field validation, the options hold
   * the defaults field validation would start with.
   */
  fun setup(draft: SurveyEditorDraft, datasetKey: String): PlotReviewSetup {
    val layer = draft.datasets.firstOrNull { it.key == datasetKey } ?: return PlotReviewSetup()
    val desk = deskForm(draft.forms, layer)
    val field = fieldForm(draft.forms, layer, excludeKey = desk?.key)
    val defaults =
      PlotReviewOptions(
        fieldValidation = field != null,
        spotCheckCount = suggestedSpotCheckCount(layer.rows.size),
      )
    if (desk == null || field == null) return PlotReviewSetup(desk?.key, field?.key, defaults)
    val form = desk.form
    val rules = form.saveTo.status.rules
    val thresholdRule = rules.firstOrNull {
      it.subject == StatusConditionSubject.QUESTION &&
        it.operator == RelevanceOperator.LESS_THAN &&
        form.find(it.questionKey)?.name == DESK_CONFIDENCE
    }
    val spotCheckRule = rules.firstOrNull {
      it.subject == StatusConditionSubject.ENTITY_PROPERTY &&
        it.operator == RelevanceOperator.LESS_THAN &&
        it.property == SamplePlotProperties.SAMPLE_ORDER
    }
    return PlotReviewSetup(
      deskFormKey = desk.key,
      fieldFormKey = field.key,
      options =
        PlotReviewOptions(
          fieldValidation = true,
          autoFlagBelowConfidence = thresholdRule?.value?.toDoubleOrNull()?.toInt() ?: 0,
          // `sample_order` counts from 1, so "less than N + 1" picks N plots.
          spotCheckCount =
            spotCheckRule?.value?.toDoubleOrNull()?.toInt()?.minus(1)?.coerceAtLeast(0) ?: 0,
          showDeskAnswersInField =
            deskAnswers(form).any { form.saveTo.propertyFor(it.key) != null },
        ),
    )
  }

  /**
   * [draft] with the review Forms of layer [datasetKey] set up for [options]. The desk check is
   * Form [deskFormKey] when given (adopting it), else the layer's current desk check, else a new
   * **Desk visual check** Form; the field Form is added or removed per
   * [PlotReviewOptions.fieldValidation]. Properties the Forms write are added to the layer.
   */
  fun apply(
    draft: SurveyEditorDraft,
    datasetKey: String,
    options: PlotReviewOptions,
    deskFormKey: String? = null,
    newFormId: () -> String = FormIds::newFormId,
  ): SurveyEditorDraft {
    val layer =
      draft.datasets.firstOrNull { it.key == datasetKey && it.kind == DatasetKind.MAP_LAYER }
        ?: return draft
    var nextKeyId = draft.nextKeyId
    var forms = draft.forms
    val titles = forms.map { it.form.title }.toMutableList()
    fun uniqueTitle(base: String) = FormDatasetLinks.uniqueTitle(base, titles).also { titles += it }

    val existingDesk =
      forms.firstOrNull { it.key == deskFormKey && it.form.updates(layer) }
        ?: deskForm(forms, layer)
    val deskKey = existingDesk?.key ?: "f${nextKeyId++}"
    val desk =
      configureDesk(
        existingDesk?.form ?: newDeskForm(newFormId(), uniqueTitle(DESK_FORM_TITLE), layer),
        layer,
        options,
      )
    forms = forms.upsert(deskKey, desk)

    val existingField = fieldForm(forms, layer, excludeKey = deskKey)
    if (options.fieldValidation) {
      val fieldKey = existingField?.key ?: "f${nextKeyId++}"
      val field =
        configureField(
          existingField?.form ?: newFieldForm(newFormId(), uniqueTitle(FIELD_FORM_TITLE), layer)
        )
      forms = forms.upsert(fieldKey, field)
    } else if (existingField != null) {
      forms = forms.filterNot { it.key == existingField.key }
    }

    val updatedLayer =
      withoutUnusedReviewProperties(
        forms
          .filter { it.form.updates(layer) }
          .fold(layer) { d, entry -> SurveyEditorDerivation.withMappedProperties(d, entry.form) },
        forms,
      )
    return draft.copy(
      forms = forms,
      datasets = draft.datasets.map { if (it.key == layer.key) updatedLayer else it },
      nextKeyId = nextKeyId,
    )
  }

  /**
   * [draft] without the review of layer [datasetKey]: the field Form is removed, and so is the desk
   * check if it's a **Desk visual check** Form. An adopted desk check stays, without the questions
   * and status rules the review added.
   */
  fun remove(draft: SurveyEditorDraft, datasetKey: String): SurveyEditorDraft {
    val layer = draft.datasets.firstOrNull { it.key == datasetKey } ?: return draft
    val desk = deskForm(draft.forms, layer)
    val field = fieldForm(draft.forms, layer, excludeKey = desk?.key)
    if (desk == null && field == null) return draft
    val forms =
      draft.forms.mapNotNull { entry ->
        when {
          entry.key == field?.key -> null
          entry.key != desk?.key -> entry
          entry.form.has(DESK_LAND_COVER) -> null
          else -> entry.copy(form = withoutReview(entry.form))
        }
      }
    return draft.copy(
      forms = forms,
      datasets =
        draft.datasets.map {
          if (it.key == layer.key) withoutUnusedReviewProperties(it, forms) else it
        },
    )
  }

  private fun deskForm(forms: List<SurveyEditorForm>, layer: EntityDataset): SurveyEditorForm? =
    forms.firstOrNull { entry ->
      entry.form.updates(layer) && entry.form.questions.any { it.name in DESK_QUESTIONS }
    }

  private fun fieldForm(
    forms: List<SurveyEditorForm>,
    layer: EntityDataset,
    excludeKey: String?,
  ): SurveyEditorForm? = forms.firstOrNull {
    it.key != excludeKey && it.form.updates(layer) && it.form.has(FIELD_LAND_COVER)
  }

  private fun EditorForm.updates(layer: EntityDataset): Boolean =
    saveTo.mode == SaveToMode.UPDATE && saveTo.targetDatasetId == layer.id

  private fun EditorForm.has(name: String): Boolean = questions.any { it.name == name }

  private fun EditorForm.keyOf(name: String): String? =
    questions.firstOrNull { it.name == name }?.key

  /** Desk questions whose answers describe the plot: all but the flagging questions. */
  private fun deskAnswers(form: EditorForm): List<EditorQuestion> =
    SaveToRules.savableQuestions(form).filter {
      !it.type.isGeometry && it.name != NEEDS_FIELD_CHECK && it.name != FLAG_REASON
    }

  private fun List<SurveyEditorForm>.upsert(key: String, form: EditorForm): List<SurveyEditorForm> =
    if (any { it.key == key }) map { if (it.key == key) it.copy(form = form) else it }
    else this + SurveyEditorForm(key, form)

  /** First unused `q<N>` question key of [questions]. */
  private fun nextQuestionKey(questions: List<EditorQuestion>): String {
    val used = questions.map { it.key }.toSet()
    var n = questions.size + 1
    while ("q$n" in used) n++
    return "q$n"
  }

  /** [questions] plus the question [create] builds (from a free key) if none is named [name]. */
  private fun ensure(
    questions: List<EditorQuestion>,
    name: String,
    create: (key: String) -> EditorQuestion,
  ): List<EditorQuestion> =
    if (questions.any { it.name == name }) questions
    else questions + create(nextQuestionKey(questions))

  /** [questions] without those named in [names], and without display logic that pointed at them. */
  private fun withoutQuestions(
    questions: List<EditorQuestion>,
    names: Set<String>,
  ): Pair<List<EditorQuestion>, Set<String>> {
    val removed = questions.filter { it.name in names }.map { it.key }.toSet()
    val kept =
      questions
        .filterNot { it.key in removed }
        .map { q -> if (q.relevance?.sourceQuestionKey in removed) q.copy(relevance = null) else q }
    return kept to removed
  }

  private fun newDeskForm(formId: String, title: String, layer: EntityDataset): EditorForm =
    EditorForm(
      formId = formId,
      title = title,
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = DESK_LAND_COVER,
            type = EditorQuestionType.SELECT_ONE,
            label = "Land cover on imagery",
            required = true,
            choices = LAND_COVER_CHOICES,
          )
        ),
      saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = layer.id),
      availability = FormAvailability.WEB,
      ctaLabel = "Check plot on imagery",
    )

  private fun newFieldForm(formId: String, title: String, layer: EntityDataset): EditorForm {
    val reachedKey = "q1"
    val ifReached = EditorRelevance(reachedKey, RelevanceOperator.EQUALS, YES)
    return EditorForm(
      formId = formId,
      title = title,
      questions =
        listOf(
          reachedQuestion(reachedKey),
          EditorQuestion(
            key = "q2",
            name = FIELD_LOCATION,
            type = EditorQuestionType.LOCATION,
            label = "Your location at the plot center",
            hint = "Stand at the center of the plot before saving your location.",
            relevance = ifReached,
            capture = GeometryCapture.GPS_ONLY,
          ),
          EditorQuestion(
            key = "q3",
            name = FIELD_LAND_COVER,
            type = EditorQuestionType.SELECT_ONE,
            label = "Land cover on the ground",
            required = true,
            choices = LAND_COVER_CHOICES,
            relevance = ifReached,
          ),
          EditorQuestion(
            key = "q4",
            name = FIELD_PHOTO,
            type = EditorQuestionType.PHOTO,
            label = "Photo of the plot",
            relevance = ifReached,
            mediaSource = MediaSource.CAPTURE_ONLY,
          ),
          EditorQuestion(
            key = "q5",
            name = FIELD_NOTES,
            type = EditorQuestionType.LONG_TEXT,
            label = "Field notes",
          ),
        ),
      saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = layer.id),
      availability = FormAvailability.MOBILE,
      ctaLabel = "Verify plot in field",
    )
  }

  private fun reachedQuestion(key: String) =
    EditorQuestion(
      key = key,
      name = PLOT_REACHED,
      type = EditorQuestionType.SELECT_ONE,
      label = "Could you reach the plot?",
      required = true,
      choices = YES_NO,
    )

  /**
   * [form] as the web-only desk check for [options]: with the confidence question (and, with field
   * validation, the flagging questions), its answers saved to plot properties of the same names
   * (only the flag reason for blind field checks), and its status rules.
   */
  private fun configureDesk(
    form: EditorForm,
    layer: EntityDataset,
    options: PlotReviewOptions,
  ): EditorForm {
    val field = options.fieldValidation
    var questions =
      ensure(form.questions, DESK_CONFIDENCE) { key ->
        EditorQuestion(
          key = key,
          name = DESK_CONFIDENCE,
          type = EditorQuestionType.NUMBER,
          label = "Confidence (0–100)",
          hint = "0 is a guess, 100 is certain.",
          required = true,
          validation = EditorValidation(min = "0", max = "100"),
        )
      }
    if (field) {
      questions =
        ensure(questions, NEEDS_FIELD_CHECK) { key ->
          EditorQuestion(
            key = key,
            name = NEEDS_FIELD_CHECK,
            type = EditorQuestionType.SELECT_ONE,
            label = "Does this plot need a field visit?",
            required = true,
            choices = YES_NO,
          )
        }
      val needsVisitKey = questions.first { it.name == NEEDS_FIELD_CHECK }.key
      questions =
        ensure(questions, FLAG_REASON) { key ->
          EditorQuestion(
            key = key,
            name = FLAG_REASON,
            type = EditorQuestionType.LONG_TEXT,
            label = "What should the field crew check?",
            required = true,
            relevance = EditorRelevance(needsVisitKey, RelevanceOperator.EQUALS, YES),
          )
        }
    }
    val (kept, removedKeys) =
      if (field) questions to emptySet()
      else withoutQuestions(questions, setOf(NEEDS_FIELD_CHECK, FLAG_REASON))
    val updated =
      form.copy(
        questions = kept,
        availability = FormAvailability.WEB,
      )

    val showDeskAnswers = !field || options.showDeskAnswersInField
    val answers = deskAnswers(updated).map { it.key }.toSet()
    val mappings =
      SaveToRules.savableQuestions(updated).fold(
        removedKeys.fold(updated.saveTo, EditorSaveTo::withoutQuestion)
      ) { saveTo, q ->
        val property =
          when {
            q.name == FLAG_REASON -> FLAG_REASON
            q.key in answers && showDeskAnswers -> q.name.takeIf { isPlotProperty(it, layer) }
            // The flag question, geometry (plots keep their own), and hidden desk answers.
            else -> null
          }
        saveTo.withMapping(q.key, property)
      }
    return updated.copy(
      saveTo =
        mappings.copy(
          status = EditorStatusConfig(enabled = true, rules = deskRules(updated, layer, options))
        )
    )
  }

  /** Whether [name] can be a plot property that a review Form writes. */
  private fun isPlotProperty(name: String, layer: EntityDataset): Boolean =
    !SaveToRules.isReservedProperty(name) &&
      name != layer.keyProperty &&
      name != layer.labelProperty &&
      name !in SamplePlotProperties.all

  /** [form] without the questions and status rules a review added to it. */
  private fun withoutReview(form: EditorForm): EditorForm {
    val (questions, removedKeys) = withoutQuestions(form.questions, ADDED_DESK_QUESTIONS)
    val saveTo = removedKeys.fold(form.saveTo, EditorSaveTo::withoutQuestion)
    return form.copy(questions = questions, saveTo = saveTo.copy(status = EditorStatusConfig()))
  }

  private fun deskRules(
    form: EditorForm,
    layer: EntityDataset,
    options: PlotReviewOptions,
  ): List<EditorStatusRule> = buildList {
    if (options.fieldValidation) {
      form.keyOf(NEEDS_FIELD_CHECK)?.let { key ->
        add(
          EditorStatusRule(
            subject = StatusConditionSubject.QUESTION,
            questionKey = key,
            operator = RelevanceOperator.EQUALS,
            value = YES,
            badge = NEEDS_FIELD_VALIDATION,
          )
        )
      }
      val threshold = options.autoFlagBelowConfidence.coerceIn(0, 100)
      val confidenceKey = form.keyOf(DESK_CONFIDENCE)
      if (threshold > 0 && confidenceKey != null) {
        add(
          EditorStatusRule(
            subject = StatusConditionSubject.QUESTION,
            questionKey = confidenceKey,
            operator = RelevanceOperator.LESS_THAN,
            value = threshold.toString(),
            badge = NEEDS_FIELD_VALIDATION,
          )
        )
      }
      // Published Forms embed the layer's plots, so `sample_order` (fixed when plots are
      // generated) can be read from the plot being checked.
      if (options.spotCheckCount > 0 && layer.property(SamplePlotProperties.SAMPLE_ORDER) != null) {
        add(
          EditorStatusRule(
            subject = StatusConditionSubject.ENTITY_PROPERTY,
            property = SamplePlotProperties.SAMPLE_ORDER,
            operator = RelevanceOperator.LESS_THAN,
            value = (options.spotCheckCount + 1).toString(),
            badge = NEEDS_FIELD_VALIDATION,
          )
        )
      }
    }
    add(EditorStatusRule(subject = StatusConditionSubject.SUBMISSIONS, badge = DESK_VERIFIED))
  }

  /** [form] as the mobile-only field check: its questions, their mappings, and status rules. */
  private fun configureField(form: EditorForm): EditorForm {
    val updated =
      form.copy(
        questions = ensure(form.questions, PLOT_REACHED, ::reachedQuestion),
        availability = FormAvailability.MOBILE,
      )
    val mapped =
      mapOf(
          PLOT_REACHED to PLOT_REACHED,
          FIELD_LOCATION to null,
          FIELD_LAND_COVER to FIELD_LAND_COVER,
          FIELD_PHOTO to null,
          FIELD_NOTES to FIELD_NOTES,
        )
        .entries
        .fold(updated.saveTo) { s, (name, prop) ->
          updated.keyOf(name)?.let { s.withMapping(it, prop) } ?: s
        }
    val notReached =
      updated.keyOf(PLOT_REACHED)?.let { key ->
        EditorStatusRule(
          subject = StatusConditionSubject.QUESTION,
          questionKey = key,
          operator = RelevanceOperator.EQUALS,
          value = NO,
          badge = NOT_REACHABLE,
        )
      }
    val submitted =
      EditorStatusRule(subject = StatusConditionSubject.SUBMISSIONS, badge = FIELD_VERIFIED)
    return updated.copy(
      saveTo =
        mapped.copy(
          status = EditorStatusConfig(enabled = true, rules = listOfNotNull(notReached, submitted))
        )
    )
  }

  /**
   * [layer] without the review properties that no Form updating it writes and no plot has a value
   * for, e.g. the field properties after field validation is turned off.
   */
  private fun withoutUnusedReviewProperties(
    layer: EntityDataset,
    forms: List<SurveyEditorForm>,
  ): EntityDataset {
    val written =
      forms
        .filter { it.form.updates(layer) }
        .flatMap { it.form.saveTo.mappings.mapNotNull { m -> m.property } }
        .toSet()
    val unused =
      layer.properties
        .map { it.name }
        .filter { name ->
          name in REVIEW_PROPERTIES &&
            name !in written &&
            layer.rows.none { !it.values[name].isNullOrBlank() }
        }
        .toSet()
    return if (unused.isEmpty()) layer
    else layer.copy(properties = layer.properties.filterNot { it.name in unused })
  }
}
