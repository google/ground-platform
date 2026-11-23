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

import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.message

/**
 * Platform the Form editor's canvas and **Preview** reproduce. Mobile shows the one-question-per-
 * screen flow; web shows the dashboard's compact layout, where every question is a collapsible card
 * stacked in the right-hand panel.
 */
enum class FormPreviewTarget {
  MOBILE,
  WEB,
}

/**
 * Question types offered by the visual Form editor, each mapped to the XForms bind `type` and body
 * control element it generates (per the ODK XForms specification, "Bind attributes" and "Body
 * elements" sections).
 */
enum class EditorQuestionType(
  val defaultNamePrefix: String,
  val bindType: String,
  val bodyElement: String,
  val appearance: String = "",
  val mediaType: String = "",
  val isReadOnly: Boolean = false,
  val hasChoices: Boolean = false,
  val isNumeric: Boolean = false,
  /**
   * Whether the answer is a geometry (`geopoint`, `geotrace`, or `geoshape`). The first geometry
   * question becomes a new map feature's geometry; see [EditorQuestion.capture] for how collectors
   * record it.
   */
  val isGeometry: Boolean = false,
) {
  TEXT("text", bindType = "string", bodyElement = "input"),
  LONG_TEXT(
    "notes",
    bindType = "string",
    bodyElement = "input",
    appearance = "multiline",
  ),
  NUMBER("number", bindType = "int", bodyElement = "input", isNumeric = true),
  SELECT_ONE(
    "choice",
    bindType = "select1",
    bodyElement = "select1",
    hasChoices = true,
  ),
  SELECT_MULTIPLE(
    "choices",
    bindType = "select",
    bodyElement = "select",
    hasChoices = true,
  ),
  DATE("date", bindType = "date", bodyElement = "input"),
  LOCATION("location", bindType = "geopoint", bodyElement = "input", isGeometry = true),
  LINE("line", bindType = "geotrace", bodyElement = "input", isGeometry = true),
  POLYGON("polygon", bindType = "geoshape", bodyElement = "input", isGeometry = true),
  PHOTO("photo", bindType = "binary", bodyElement = "upload", mediaType = "image/*"),
  VIDEO("video", bindType = "binary", bodyElement = "upload", mediaType = "video/*"),
  AUDIO("audio", bindType = "binary", bodyElement = "upload", mediaType = "audio/*"),
  NOTE("note", bindType = "string", bodyElement = "input", isReadOnly = true);

  /**
   * Whether the answer is a media file (photo, video, or audio) collected through an XForms
   * `<upload mediatype="…">` control; see [EditorQuestion.mediaSource] for how collectors provide
   * it.
   */
  val isMedia: Boolean
    get() = bodyElement == "upload"

  /** Whether this question type is available when adding or choosing questions for web forms. */
  val isSupportedOnWeb: Boolean
    get() = true

  /** Whether this question type can be displayed and answered inside Google Earth map balloons. */
  val isSupportedInGoogleEarth: Boolean
    get() = !isGeometry && !isMedia
}

/**
 * How collectors provide a media question's answer. Mirrors the shared form runner's reading of the
 * ODK `new` appearance: with it, collectors must capture a fresh photo / video / recording; without
 * it they may also pick an existing file from their device.
 */
enum class MediaSource(val appearance: String) {
  CAPTURE_OR_UPLOAD(""),
  CAPTURE_ONLY("new"),
}

/**
 * How collectors record a geometry question's answer on mobile. Mirrors the shared form runner's
 * reading of the body `appearance`: without `placement-map` a `geopoint` is locked to the device's
 * live GPS fix and a `geotrace` / `geoshape` to a GPS walk. On the web dashboard, all geometry
 * questions are drawn directly on the map regardless of this setting.
 */
enum class GeometryCapture(val appearance: String) {
  GPS_ONLY(""),
  GPS_OR_MAP("placement-map"),
}

/**
 * A single `<item>` of a `select1` / `select` question.
 *
 * @property image optional picture shown next to the label; exported as an XForms itext `image`
 *   form (XLSForm `media::image` column).
 * @property colorHex optional `#RRGGBB` display color. Specific to Collect One, so it is never
 *   written to XForms.
 * @property code code-list value of the question's linked concept that this choice stands for (e.g.
 *   the choice "Café" → `coffee`), exported as the `collectone_code` extra choice column
 *   (`ChoiceItem.properties["collectone_code"]`), or `null` when unmapped.
 */
data class EditorChoice(
  val value: String,
  val label: String,
  val image: EditorChoiceImage? = null,
  val colorHex: String? = null,
  val code: String? = null,
  /** Translations of [label] keyed by language code (excluding the survey's default language). */
  val labelTranslations: Map<String, String> = emptyMap(),
  val parentValue: String = "",
) {
  /**
   * Returns the raw label authored for [language], or [label] when [language] is blank or matches
   * [defaultLanguage].
   */
  fun labelIn(language: String, defaultLanguage: String = "default"): String =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      label
    } else {
      lookupEditorTranslation(labelTranslations, language).orEmpty()
    }

  /**
   * Returns the label to display in [language], falling back to the default [label] when no
   * translation has been entered yet.
   */
  fun displayLabel(language: String, defaultLanguage: String = "default"): String =
    labelIn(language, defaultLanguage).ifBlank { label }

  /** Returns a copy with the label for [language] set to [value]. */
  fun withLabelIn(language: String, defaultLanguage: String, value: String): EditorChoice =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      copy(label = value)
    } else {
      copy(labelTranslations = updateEditorTranslationMap(labelTranslations, language, value))
    }
}

/**
 * An attribute of a question's XForms `<bind>` in a namespace the editor doesn't model (another
 * tool's extension, or a `collectone:concept` naming a concept outside the survey's library), kept
 * so it round-trips (`FieldBinding.foreign_attributes`).
 */
data class EditorForeignAttribute(
  val namespaceUri: String,
  /** Qualified name as written (e.g. `acme:hint-style`). */
  val qualifiedName: String,
  val value: String,
)

/** A small image attached to an [EditorChoice], stored inline as base64. */
data class EditorChoiceImage(val mimeType: String, val base64: String) {
  /** Approximate decoded size in bytes. */
  val sizeBytes: Int
    get() = base64.length / 4 * 3 - base64.takeLast(2).count { it == '=' }

  /** File extension used for the exported media file name. */
  val fileExtension: String
    get() =
      when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/jpeg",
        "image/jpg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        else -> "img"
      }

  companion object {
    /** Long-edge limit, in pixels, applied when picking a choice image. */
    const val MAX_PIXELS = 256

    /** Largest accepted choice image, in bytes, so Forms stay light for offline download. */
    const val MAX_BYTES = 200 * 1024
  }
}

/** Preset choice colors offered by the Form editor, as `name` to `#RRGGBB`. */
object ChoiceColors {
  val palette: List<Pair<String, String>> =
    listOf(
      "Orange" to "#F37C22",
      "Red" to "#D13135",
      "Purple" to "#7A279F",
      "Blue" to "#2278CF",
      "Green" to "#3C8D40",
      "Yellow" to "#F9BF40",
      "Teal" to "#129EAF",
      "Pink" to "#E52592",
      "Brown" to "#8D6E63",
      "Gray" to "#80868B",
    )

  private val HEX_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

  fun isValidHex(hex: String): Boolean = HEX_PATTERN.matches(hex)

  /** Parses `#RRGGBB` into an opaque ARGB value, or `null` if malformed. */
  fun argb(hex: String?): Long? =
    hex?.takeIf(::isValidHex)?.substring(1)?.toLongOrNull(16)?.let { 0xFF000000 or it }

  fun nameOf(hex: String?): String? = palette.firstOrNull { it.second.equals(hex, true) }?.first
}

/** Comparison used by a question's display (skip) logic. */
enum class RelevanceOperator(
  val symbol: String,
  val needsValue: Boolean = true,
) {
  EQUALS("="),
  NOT_EQUALS("≠"),
  GREATER_THAN(">"),
  LESS_THAN("<"),
  INCLUDES("includes"),
  IS_ANSWERED("answered", needsValue = false);

  companion object {
    /** Operators that make sense when comparing against an answer of [type]. */
    fun availableFor(type: EditorQuestionType): List<RelevanceOperator> =
      when {
        type.isGeometry -> listOf(IS_ANSWERED)
        type == EditorQuestionType.SELECT_MULTIPLE -> listOf(INCLUDES, IS_ANSWERED)
        type.isNumeric -> listOf(EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, IS_ANSWERED)
        type == EditorQuestionType.SELECT_ONE ||
          type == EditorQuestionType.TEXT ||
          type == EditorQuestionType.LONG_TEXT -> listOf(EQUALS, NOT_EQUALS, IS_ANSWERED)
        else -> listOf(IS_ANSWERED)
      }
  }
}

/**
 * Display logic ("show this question only if …") compiled to an XForms `relevant` bind expression.
 * The source is referenced by [EditorQuestion.key] so renaming a question keeps the logic intact.
 */
data class EditorRelevance(
  val sourceQuestionKey: String,
  val operator: RelevanceOperator = RelevanceOperator.EQUALS,
  val value: String = "",
)

/**
 * A section (group) of questions in the editor, either a singleton group or repeated group.
 *
 * A section is exported as an XForms `<group>` (or `<repeat>` when [repeatable]) whose instance
 * node is named [id]. Group-level [relevance] is exported as the group node's `relevant` bind, so
 * it applies to every question in the section at once: relevance is inherited by descendants (W3C
 * XForms 1.0 section 6.1.4), so the questions inside don't need display logic of their own.
 *
 * @property insertAtIndex question index where an empty section is anchored on the canvas before
 *   its first question is added, or `null` once the section contains questions (or when appended at
 *   the end). Editor-only metadata; never emitted to XForms.
 */
data class EditorSection(
  val id: String,
  val title: String,
  val repeatable: Boolean = false,
  val repeatCountExpression: String = "",
  val relevance: EditorRelevance? = null,
  val insertAtIndex: Int? = null,
) {
  /** Whether the whole section is shown only when its display logic is true. */
  val isConditional: Boolean
    get() = relevance != null
}

/**
 * Where a [EditorQuestionType.SELECT_ONE] or [EditorQuestionType.SELECT_MULTIPLE] question gets its
 * options: a manual list of [EditorChoice]s, a survey Map layer, or a survey Data table.
 */
enum class ChoiceSource {
  MANUAL,
  MAP_LAYER,
  DATA_TABLE;

  val isDataset: Boolean
    get() = this != MANUAL
}

/** One screen (question) in the editor. */
data class EditorQuestion(
  /** Stable editor-only identity; never emitted to XForms. */
  val key: String,
  /** XForms instance node name (`/data/<name>`). */
  val name: String,
  val type: EditorQuestionType,
  val label: String,
  val hint: String = "",
  val required: Boolean = false,
  val choices: List<EditorChoice> = emptyList(),
  /**
   * Dataset ID (`<instance id="…" src="jr://file-csv/….csv">`) supplying dynamic options for a
   * [EditorQuestionType.SELECT_ONE] or [EditorQuestionType.SELECT_MULTIPLE] question, or `null`
   * when the question uses its manual [choices] list.
   */
  val choiceDatasetId: String? = null,
  /** Where this question's choices come from when [EditorQuestionType.hasChoices] is true. */
  val choiceSource: ChoiceSource =
    if (choiceDatasetId != null) ChoiceSource.MAP_LAYER else ChoiceSource.MANUAL,
  /**
   * Whether data collectors may add a new map feature or table row inline while answering this
   * question, using the dataset's creation Form.
   */
  val allowAddEntity: Boolean = false,
  val relevance: EditorRelevance? = null,
  /** Answer validation, exported as the bind `constraint` / `jr:constraintMsg`. */
  val validation: EditorValidation? = null,
  /**
   * Number of decimal places allowed for a [EditorQuestionType.NUMBER] question (`0` means whole
   * numbers / integer; `> 0` means decimal). Ignored for non-numeric question types.
   */
  val decimalPlaces: Int = 0,
  /**
   * How collectors record the answer of a geometry question ([EditorQuestionType.isGeometry]);
   * ignored for other types. Exported as the body `appearance`.
   */
  val capture: GeometryCapture = GeometryCapture.GPS_ONLY,
  /**
   * How collectors provide the answer of a media question ([EditorQuestionType.isMedia]); ignored
   * for other types. Exported as the body `appearance` (`new` for capture only).
   */
  val mediaSource: MediaSource = MediaSource.CAPTURE_OR_UPLOAD,
  /**
   * Dictionary concept this question is linked to, or `null`. Stored in the survey's
   * `form_concept_links` and exported as a `collectone:concept` bind attribute (see
   * `docs/technical/model/library/01-concepts.md`).
   */
  val conceptLink: ConceptLink? = null,
  /** Unmodeled `<bind>` attributes kept for round-trip fidelity. */
  val foreignAttributes: List<EditorForeignAttribute> = emptyList(),
  /** Translations of [label] keyed by language code (excluding the survey's default language). */
  val labelTranslations: Map<String, String> = emptyMap(),
  /** Translations of [hint] keyed by language code (excluding the survey's default language). */
  val hintTranslations: Map<String, String> = emptyMap(),
  /** Key of the parent choice question this question's choices depend on (cascading select). */
  val parentQuestionKey: String = "",
  /** ID of the [EditorSection] this question belongs to, or empty string if not in a section. */
  val sectionId: String = "",
  /**
   * Whether this question has been soft-deleted in the editor. Soft-deleted questions are hidden
   * from active form flows and devices once synced, while their schema bindings and already
   * collected data are preserved.
   */
  val isDeleted: Boolean = false,
) {
  /**
   * Returns the raw label authored for [language], or [label] when [language] is blank or matches
   * [defaultLanguage].
   */
  fun labelIn(language: String, defaultLanguage: String = "default"): String =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      label
    } else {
      lookupEditorTranslation(labelTranslations, language).orEmpty()
    }

  /**
   * Returns the label to display in [language], falling back to the default [label] when no
   * translation has been entered yet.
   */
  fun displayLabel(language: String, defaultLanguage: String = "default"): String =
    labelIn(language, defaultLanguage).ifBlank { label }

  /** Returns a copy with the label for [language] set to [value]. */
  fun withLabelIn(language: String, defaultLanguage: String, value: String): EditorQuestion =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      copy(label = value)
    } else {
      copy(labelTranslations = updateEditorTranslationMap(labelTranslations, language, value))
    }

  /**
   * Returns the raw hint authored for [language], or [hint] when [language] is blank or matches
   * [defaultLanguage].
   */
  fun hintIn(language: String, defaultLanguage: String = "default"): String =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      hint
    } else {
      lookupEditorTranslation(hintTranslations, language).orEmpty()
    }

  /**
   * Returns the hint to display in [language], falling back to the default [hint] when no
   * translation has been entered yet.
   */
  fun displayHint(language: String, defaultLanguage: String = "default"): String =
    hintIn(language, defaultLanguage).ifBlank { hint }

  /** Returns a copy with the hint for [language] set to [value]. */
  fun withHintIn(language: String, defaultLanguage: String, value: String): EditorQuestion =
    if (isDefaultEditorLanguage(language, defaultLanguage)) {
      copy(hint = value)
    } else {
      copy(hintTranslations = updateEditorTranslationMap(hintTranslations, language, value))
    }

  val isConditional: Boolean
    get() = relevance != null

  /**
   * Whether this is a [EditorQuestionType.NUMBER] question that only allows whole numbers (`0`
   * decimal places).
   */
  val isInteger: Boolean
    get() = type == EditorQuestionType.NUMBER && decimalPlaces <= 0

  /** Whether this is a [EditorQuestionType.NUMBER] question that allows decimal places (`> 0`). */
  val isDecimal: Boolean
    get() = type == EditorQuestionType.NUMBER && decimalPlaces > 0

  companion object {
    /** Maximum number of decimal places configurable in the editor. */
    const val MAX_DECIMAL_PLACES = 9

    /** Default number of decimal places for decimal number questions. */
    const val DEFAULT_DECIMAL_PLACES = MAX_DECIMAL_PLACES
  }

  /**
   * Whether this question pulls its choices from a Map layer or Data table rather than [choices].
   */
  val usesDatasetChoices: Boolean
    get() = !choiceDatasetId.isNullOrBlank()

  /**
   * Effective [ChoiceSource] for this question, inferring [ChoiceSource.MAP_LAYER] vs
   * [ChoiceSource.DATA_TABLE] from [datasets] when [choiceDatasetId] is set, and keeping a dataset
   * [choiceSource] that has no dataset chosen yet.
   */
  fun effectiveChoiceSource(datasets: List<EditorDataset> = emptyList()): ChoiceSource {
    val datasetId = choiceDatasetId
    if (!datasetId.isNullOrBlank()) {
      val dataset = datasets.firstOrNull { it.id == datasetId }
      if (dataset != null) {
        return if (dataset.isMapLayer) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
      }
      return if (choiceSource.isDataset) choiceSource else ChoiceSource.MAP_LAYER
    }
    // A Map layer or Data table source with no dataset chosen yet (e.g. none exist, or creating one
    // was cancelled) stays selected so the editor can offer to pick or create one.
    return if (choiceSource.isDataset) choiceSource else ChoiceSource.MANUAL
  }

  /**
   * Effective choices for this question: rows from the linked [EditorDataset] when
   * [choiceDatasetId] is set (falling back to [choices] when the dataset is not in [datasets]), or
   * [choices] when using a manual list.
   */
  fun resolvedChoices(datasets: List<EditorDataset> = emptyList()): List<EditorChoice> {
    val datasetId = choiceDatasetId ?: return choices
    if (datasetId.isBlank()) return emptyList()
    val dataset = datasets.firstOrNull { it.id == datasetId } ?: return choices
    return dataset.rows.map { EditorChoice(value = it.name, label = it.label) }
  }

  /** Whether this question cannot be displayed in Google Earth map balloons. */
  val isGoogleEarthIncompatible: Boolean
    get() = !type.isSupportedInGoogleEarth
}

internal fun isDefaultEditorLanguage(language: String, defaultLanguage: String): Boolean =
  language.isBlank() ||
    language.equals("default", ignoreCase = true) ||
    (defaultLanguage.isNotBlank() && language.equals(defaultLanguage, ignoreCase = true))

private fun lookupEditorTranslation(map: Map<String, String>, language: String): String? {
  if (map.isEmpty() || language.isBlank()) return null
  map[language]?.let {
    return it
  }
  map.entries
    .firstOrNull { it.key.equals(language, ignoreCase = true) }
    ?.value
    ?.let {
      return it
    }
  val base = language.substringBefore('-').substringBefore('_').lowercase()
  return map.entries
    .firstOrNull { it.key.substringBefore('-').substringBefore('_').lowercase() == base }
    ?.value
}

private fun updateEditorTranslationMap(
  map: Map<String, String>,
  language: String,
  value: String,
): Map<String, String> {
  val key = language.trim()
  if (key.isEmpty()) return map
  val filtered = map.filterKeys { !it.equals(key, ignoreCase = true) }
  return if (value.isEmpty()) filtered else filtered + (key to value)
}

/** The whole Form being edited. */
data class EditorForm(
  val formId: String,
  val title: String,
  val questions: List<EditorQuestion>,
  /** What a submission does to the survey's Map layers and Data tables. */
  val saveTo: EditorSaveTo = EditorSaveTo(),
  /**
   * Where collectors can open the Form. New Forms start mobile-only; designers turn on **Available
   * on web** in Form settings to offer the Form in the web dashboard too.
   */
  val availability: FormAvailability = FormAvailability.MOBILE,
  /**
   * Custom label shown on buttons that start data collection for this Form. When blank, buttons
   * fall back to [title] via [effectiveCtaLabel].
   */
  val ctaLabel: String = "",
  /** Sections (groups of questions) configured in this form. */
  val sections: List<EditorSection> = emptyList(),
  /**
   * Library template this Form was first populated from, if any
   * (`SurveyDef.form_template_origins`). Internal provenance, not shown to organizers.
   */
  val templateOrigin: TemplateOrigin? = null,
  /**
   * Whether "Open in Google Earth" is enabled for this Form when web data collection is turned on.
   */
  val openInGoogleEarth: Boolean = false,
  /**
   * Questions that have been flagged as deleted. They are omitted from the form's active view
   * controls so collectors no longer see them once synced, while their model schema/bindings and
   * collected dataset values remain intact.
   */
  val deletedQuestions: List<EditorQuestion> = emptyList(),
) {
  /** All questions known to this Form, both active and soft-deleted. */
  val allQuestions: List<EditorQuestion>
    get() = questions + deletedQuestions

  fun section(sectionId: String): EditorSection? = sections.firstOrNull { it.id == sectionId }

  /** The section [question] belongs to, or `null` when it isn't in one. */
  fun sectionOf(question: EditorQuestion): EditorSection? =
    question.sectionId.takeIf { it.isNotEmpty() }?.let(::section)

  /** Questions in section [sectionId], in Form order. */
  fun questionsIn(sectionId: String): List<EditorQuestion> = questions.filter {
    it.sectionId == sectionId
  }

  /** Index of the first question in section [sectionId], or `-1` when the section is empty. */
  fun firstQuestionIndex(sectionId: String): Int = questions.indexOfFirst {
    it.sectionId == sectionId
  }

  /**
   * Path of [question]'s instance node relative to the primary instance root, e.g. `trees/species`
   * for a question in section `trees` or `species` for one outside every section. Bindings and body
   * controls use this form; the XForms serializer adds the `/data/` root.
   */
  fun relativePath(question: EditorQuestion): String =
    sectionOf(question)?.let { "${it.id}/${question.name}" } ?: question.name

  /** Absolute XPath of [question]'s instance node, e.g. `/data/trees/species`. */
  fun instancePath(question: EditorQuestion): String = "/$INSTANCE_ROOT/${relativePath(question)}"

  /**
   * Whether [question] may be hidden: it has display logic of its own or belongs to a section that
   * has display logic.
   */
  fun isShownConditionally(question: EditorQuestion): Boolean =
    question.isConditional || sectionOf(question)?.isConditional == true

  /**
   * Whether display logic of something in section [contextSectionId] (`""` for outside every
   * section) can refer to [source]. A question inside a repeated section has one answer per entry,
   * so only questions in the same repeated section can refer to it (to the answer in their own
   * entry); anything else would compare against every entry at once.
   */
  fun canReferTo(source: EditorQuestion, contextSectionId: String): Boolean {
    if (source.type == EditorQuestionType.NOTE) return false
    val sourceSection = sectionOf(source) ?: return true
    return !sourceSection.repeatable || sourceSection.id == contextSectionId
  }

  /** Label shown on buttons that start data collection for this Form. */
  val effectiveCtaLabel: String
    get() = ctaLabel.trim().ifEmpty { title }

  /** Language codes present in any question's or choice's non-blank translations. */
  val translationLanguages: List<String>
    get() =
      questions
        .flatMap { q ->
          q.labelTranslations.filterValues { it.isNotBlank() }.keys +
            q.hintTranslations.filterValues { it.isNotBlank() }.keys +
            q.choices.flatMap { c -> c.labelTranslations.filterValues { it.isNotBlank() }.keys }
        }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

  fun indexOf(key: String): Int = questions.indexOfFirst { it.key == key }

  fun find(key: String?): EditorQuestion? = questions.firstOrNull { it.key == key }

  /**
   * First geometry (Point, Line, or Polygon) question; its answer becomes a new map feature's
   * geometry.
   */
  val primaryGeometryQuestion: EditorQuestion?
    get() = questions.firstOrNull { it.type.isGeometry }

  /** Whether submissions capture a geometry, i.e. add map features rather than table rows. */
  val hasGeometry: Boolean
    get() = primaryGeometryQuestion != null

  /**
   * Whether this Form updates existing map features selected on the map (or clicked in a Google
   * Earth KML balloon): [EditorSaveTo.mode] is [SaveToMode.UPDATE], [EditorSaveTo.idSource] is
   * [EntityIdSource.SELECTED_FEATURE], and the resolved [SaveToRules.saveTarget] in [datasets] is a
   * Map layer.
   */
  fun updatesSelectedMapFeature(datasets: List<EditorDataset>): Boolean =
    saveTo.mode == SaveToMode.UPDATE &&
      saveTo.idSource == EntityIdSource.SELECTED_FEATURE &&
      SaveToRules.saveTarget(this, datasets)?.isMapLayer == true

  /**
   * Whether **Open in Google Earth** can be enabled on this Form given the survey's [datasets]: web
   * data collection is enabled and submissions update an existing map feature selected on the map.
   */
  fun canOpenInGoogleEarth(datasets: List<EditorDataset>): Boolean =
    availability.includesWeb && updatesSelectedMapFeature(datasets)

  /**
   * Questions whose field type cannot be displayed in Google Earth map balloons. Only a problem
   * when the Form is available on web and [openInGoogleEarth] is enabled.
   */
  fun googleEarthIncompatibleQuestions(): List<EditorQuestion> =
    if (availability.includesWeb && openInGoogleEarth) {
      questions.filter { it.isGoogleEarthIncompatible }
    } else {
      emptyList()
    }

  /**
   * Whether [type] is compatible with all currently enabled form variants ([availability] and
   * [openInGoogleEarth]) and should be selectable in question-type menus.
   */
  fun isQuestionTypeAvailable(type: EditorQuestionType): Boolean =
    (!availability.includesWeb || type.isSupportedOnWeb) &&
      (!(availability.includesWeb && openInGoogleEarth) || type.isSupportedInGoogleEarth)

  /** Questions before [key] whose answers can drive its display logic. */
  fun eligibleRelevanceSources(key: String): List<EditorQuestion> {
    val index = indexOf(key)
    if (index <= 0) return emptyList()
    val sectionId = questions[index].sectionId
    return questions.subList(0, index).filter { canReferTo(it, sectionId) }
  }

  /**
   * Questions whose answers can drive section [sectionId]'s display logic: those asked before the
   * section's first question (every question outside it when the section is still empty), except
   * questions inside repeated sections (see [canReferTo]).
   */
  fun eligibleSectionRelevanceSources(sectionId: String): List<EditorQuestion> {
    val firstIndex = firstQuestionIndex(sectionId)
    val before = if (firstIndex >= 0) questions.subList(0, firstIndex) else questions
    return before.filter { it.sectionId != sectionId && canReferTo(it, contextSectionId = "") }
  }

  /**
   * Questions before [key] that can be its cascading-select parent: choice questions, since a
   * child's choices are filtered by the parent's selected choice values.
   */
  fun eligibleCascadeParents(key: String): List<EditorQuestion> {
    val index = indexOf(key)
    if (index <= 0) return emptyList()
    return questions.subList(0, index).filter { it.type.hasChoices }
  }

  /**
   * The question [question]'s [EditorQuestion.parentQuestionKey] names, by key or (as imported
   * Forms may still hold) data name, whatever its type; `null` when it names none.
   */
  fun cascadeParentRefOf(question: EditorQuestion): EditorQuestion? {
    val ref = question.parentQuestionKey.trim()
    if (ref.isEmpty()) return null
    return questions.firstOrNull { it.key == ref } ?: questions.firstOrNull { it.name == ref }
  }

  /**
   * [question]'s cascading-select parent: the choice question (other than [question]) whose answer
   * filters its choices, or `null` when it has none or the reference is broken (see
   * [FormEditorValidator]).
   */
  fun cascadeParentOf(question: EditorQuestion): EditorQuestion? =
    cascadeParentRefOf(question)?.takeIf { it.type.hasChoices && it.key != question.key }

  /** Questions whose choices are filtered by the answer to the question [key]. */
  fun cascadeChildrenOf(key: String): List<EditorQuestion> {
    val parent = find(key) ?: return emptyList()
    return questions.filter { it.key != key && cascadeParentRefOf(it)?.key == parent.key }
  }

  /**
   * Plain-language sentence for [question]'s display logic, using question labels rather than data
   * names, e.g. `Shown only if "Are shade trees present?" equals Yes.`
   */
  fun relevanceSummary(
    question: EditorQuestion,
    datasets: List<EditorDataset> = emptyList(),
  ): DomainMessage? = relevanceSummary(question.relevance, datasets)

  /**
   * Plain-language sentence for [section]'s display logic, which shows or hides all its questions
   * together, e.g. `Shown only if "Are shade trees present?" equals Yes.`
   */
  fun relevanceSummary(
    section: EditorSection,
    datasets: List<EditorDataset> = emptyList(),
  ): DomainMessage? = relevanceSummary(section.relevance, datasets)

  private fun relevanceSummary(
    relevance: EditorRelevance?,
    datasets: List<EditorDataset>,
  ): DomainMessage? {
    relevance ?: return null
    val source =
      find(relevance.sourceQuestionKey) ?: return DisplayLogicMessage.SOURCE_DELETED.message()
    val title = source.label.ifBlank { source.name }
    if (!relevance.operator.needsValue) {
      return DisplayLogicMessage.SHOWN_IF_ANSWERED.message(title)
    }
    val value =
      source.resolvedChoices(datasets).firstOrNull { it.value == relevance.value }?.label
        ?: relevance.value.ifBlank {
          return null
        }
    return DisplayLogicMessage.SHOWN_IF.message(
      title,
      DomainMessage.Label(relevance.operator),
      value,
    )
  }

  /**
   * Short human description of when [question] is shown, e.g. `if has_shade = yes`: its section's
   * display logic, its own display logic, or both when both apply.
   */
  fun describeRelevance(
    question: EditorQuestion,
    datasets: List<EditorDataset> = emptyList(),
  ): String? {
    val clauses =
      listOfNotNull(
        sectionOf(question)?.relevance?.let { describeRelevanceClause(it, datasets) },
        question.relevance?.let { describeRelevanceClause(it, datasets) },
      )
    return if (clauses.isEmpty()) null else "if ${clauses.joinToString(", ")}"
  }

  /** Short human description of [section]'s display logic, e.g. `if has_shade = yes`. */
  fun describeRelevance(
    section: EditorSection,
    datasets: List<EditorDataset> = emptyList(),
  ): String? = section.relevance?.let { "if ${describeRelevanceClause(it, datasets)}" }

  private fun describeRelevanceClause(
    relevance: EditorRelevance,
    datasets: List<EditorDataset>,
  ): String {
    val source = find(relevance.sourceQuestionKey) ?: return "(missing question)"
    return if (relevance.operator.needsValue) {
      val shownValue =
        source.resolvedChoices(datasets).firstOrNull { it.value == relevance.value }?.label
          ?: relevance.value
      "${source.name} ${relevance.operator.symbol} ${shownValue.ifBlank { "…" }}"
    } else {
      "${source.name} is answered"
    }
  }

  companion object {
    /** Name of the primary instance root element of every Form the editor generates. */
    const val INSTANCE_ROOT = "data"
  }
}

/** How control can move from one screen to another. */
enum class FlowEdgeKind {
  /** Unconditional advance to the adjacent screen. */
  NEXT,
  /** Advance to a screen that is only shown when its display logic is true. */
  CONDITIONAL,
  /** Advance past one or more conditional screens whose logic evaluated false. */
  SKIP,
}

/**
 * A potential transition between flow slots. Slot `0` is the Form start, slots `1..n` are the
 * questions in order, and slot `n + 1` is the final Review & Submit screen.
 */
data class FlowEdge(val from: Int, val to: Int, val kind: FlowEdgeKind) {
  val span: Int
    get() = to - from
}

/** Derives the graph of potential screen-to-screen transitions from display logic. */
object FormFlowGraph {

  fun startSlot(): Int = 0

  fun endSlot(form: EditorForm): Int = form.questions.size + 1

  fun questionSlot(index: Int): Int = index + 1

  /**
   * For every slot, walks forward: each conditional question (one with display logic of its own or
   * in a section with display logic) may be shown (a [FlowEdgeKind.CONDITIONAL] edge) or skipped,
   * so the walk continues until the first unconditional question or the end.
   */
  fun edges(form: EditorForm): List<FlowEdge> {
    val questions = form.questions
    val end = endSlot(form)
    val result = mutableListOf<FlowEdge>()
    for (from in 0 until end) {
      var to = from + 1
      while (to <= end) {
        if (to == end || !form.isShownConditionally(questions[to - 1])) {
          result += FlowEdge(from, to, if (to == from + 1) FlowEdgeKind.NEXT else FlowEdgeKind.SKIP)
          break
        }
        result += FlowEdge(from, to, FlowEdgeKind.CONDITIONAL)
        to++
      }
    }
    return result
  }

  /** Number of distinct start → end screen sequences implied by [edges]. */
  fun countPaths(form: EditorForm, edges: List<FlowEdge> = edges(form)): Long {
    val end = endSlot(form)
    val paths = LongArray(end + 1)
    paths[end] = 1
    val outgoing = edges.groupBy { it.from }
    for (slot in end - 1 downTo 0) {
      paths[slot] = outgoing[slot].orEmpty().sumOf { paths[it.to] }
    }
    return paths[0]
  }
}

/** Converts a free-text label into a valid, readable XForms name / choice value. */
fun slugify(text: String, fallback: String = "item"): String {
  val slug =
    text
      .lowercase()
      .map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }
      .joinToString("")
      .replace(Regex("_+"), "_")
      .trim('_')
      .take(40)
  return when {
    slug.isEmpty() -> fallback
    slug.first().isDigit() -> "_$slug"
    else -> slug
  }
}

/**
 * The library template (and version) a Form was copied from, and whether the Form was edited since
 * (mirrors `openforis.collectone.v1.survey.FormTemplateOrigin`).
 */
/**
 * This Form, with its [EditorForm.templateOrigin] marked modified if its content differs from
 * [before] (the Form before a user edit). Changes to how the Form is tied to Map layers and Data
 * tables (where it saves, and choices read from a dataset) don't count. Forms without an origin are
 * returned as they are.
 */
fun EditorForm.withModificationFrom(before: EditorForm): EditorForm {
  val origin = templateOrigin ?: return this
  if (origin.modified || contentIgnoringEntities() == before.contentIgnoringEntities()) return this
  return copy(templateOrigin = origin.asModified())
}

/** This Form without its entity (Map layer and Data table) bindings or template origin. */
private fun EditorForm.contentIgnoringEntities(): EditorForm =
  copy(
    saveTo = EditorSaveTo(),
    templateOrigin = null,
    questions =
      questions.map {
        it.copy(choiceDatasetId = null, choiceSource = ChoiceSource.MANUAL, allowAddEntity = false)
      },
    deletedQuestions =
      deletedQuestions.map {
        it.copy(choiceDatasetId = null, choiceSource = ChoiceSource.MANUAL, allowAddEntity = false)
      },
  )

data class TemplateOrigin(
  val templateId: String,
  val templateVersion: Int,
  val modified: Boolean = false,
) {
  /** This origin marked as modified. */
  fun asModified(): TemplateOrigin = if (modified) this else copy(modified = true)

  companion object {
    /** Origin of a Form just copied from [template]. */
    fun of(template: org.openforis.collectone.v2.app.domain.model.FormTemplate): TemplateOrigin =
      TemplateOrigin(template.id, template.version)

    /** The `FormTemplateOrigin` records of [forms] that have an origin, keyed by form ID. */
    fun toProto(forms: List<EditorForm>): List<openforis.collectone.v1.survey.FormTemplateOrigin> =
      forms.mapNotNull { form ->
        form.templateOrigin?.let {
          openforis.collectone.v1.survey.FormTemplateOrigin(
            form_id = form.formId,
            template_id = it.templateId,
            template_version = it.templateVersion,
            modified = it.modified,
          )
        }
      }

    /** The origin recorded for [formId] in [origins], if any. */
    fun fromProto(
      origins: List<openforis.collectone.v1.survey.FormTemplateOrigin>,
      formId: String,
    ): TemplateOrigin? =
      origins
        .firstOrNull { it.form_id == formId }
        ?.let { TemplateOrigin(it.template_id, it.template_version, it.modified) }
  }
}
