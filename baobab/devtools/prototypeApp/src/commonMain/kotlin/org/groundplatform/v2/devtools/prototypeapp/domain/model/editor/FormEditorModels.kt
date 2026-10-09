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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.ChoiceItem
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.EntityPropertyMapping
import groundplatform.v2.forms.EntitySyncMetadata
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.ItemsetDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.LanguageTranslation
import groundplatform.v2.forms.LocalizedString
import groundplatform.v2.forms.MediaRef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
import org.groundplatform.v2.core.forms.model.FormDefinition
import org.groundplatform.v2.core.forms.model.secondaryInstanceRow
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability

/**
 * Platform the Form editor's canvas and **Preview** reproduce. Mobile shows the one-question-per-
 * screen flow; web shows the dashboard's compact layout, where every question is a collapsible card
 * stacked in the right-hand panel.
 */
enum class FormPreviewTarget(val label: String) {
  MOBILE("Mobile"),
  WEB("Web"),
}

/**
 * Question types offered by the visual Form editor, each mapped to the XForms bind `type` and body
 * control element it generates (per the ODK XForms specification, "Bind attributes" and "Body
 * elements" sections).
 */
enum class EditorQuestionType(
  val label: String,
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
  TEXT("Text", "text", bindType = "string", bodyElement = "input"),
  LONG_TEXT(
    "Long text",
    "notes",
    bindType = "string",
    bodyElement = "input",
    appearance = "multiline",
  ),
  INTEGER("Integer", "count", bindType = "int", bodyElement = "input", isNumeric = true),
  DECIMAL("Decimal", "amount", bindType = "decimal", bodyElement = "input", isNumeric = true),
  SELECT_ONE(
    "Select one",
    "choice",
    bindType = "select1",
    bodyElement = "select1",
    hasChoices = true,
  ),
  SELECT_MULTIPLE(
    "Select multiple",
    "choices",
    bindType = "select",
    bodyElement = "select",
    hasChoices = true,
  ),
  DATE("Date", "date", bindType = "date", bodyElement = "input"),
  LOCATION("Point", "location", bindType = "geopoint", bodyElement = "input", isGeometry = true),
  LINE("Line", "line", bindType = "geotrace", bodyElement = "input", isGeometry = true),
  POLYGON("Polygon", "polygon", bindType = "geoshape", bodyElement = "input", isGeometry = true),
  PHOTO("Photo", "photo", bindType = "binary", bodyElement = "upload", mediaType = "image/*"),
  VIDEO("Video", "video", bindType = "binary", bodyElement = "upload", mediaType = "video/*"),
  AUDIO("Audio", "audio", bindType = "binary", bodyElement = "upload", mediaType = "audio/*"),
  NOTE("Note", "note", bindType = "string", bodyElement = "input", isReadOnly = true);

  /**
   * Whether the answer is a media file (photo, video, or audio) collected through an XForms
   * `<upload mediatype="…">` control; see [EditorQuestion.mediaSource] for how collectors provide
   * it.
   */
  val isMedia: Boolean
    get() = bodyElement == "upload"
}

/**
 * How collectors provide a media question's answer. Mirrors the shared form runner's reading of the
 * ODK `new` appearance: with it, collectors must capture a fresh photo / video / recording; without
 * it they may also pick an existing file from their device.
 */
enum class MediaSource(val label: String, val description: String, val appearance: String) {
  CAPTURE_OR_UPLOAD(
    "Capture or upload",
    "Collectors can use the camera or microphone, or upload an existing file from their device.",
    "",
  ),
  CAPTURE_ONLY(
    "Capture only",
    "Collectors must take a new photo, video, or recording. Uploading existing files is not allowed.",
    "new",
  ),
}

/**
 * How collectors record a geometry question's answer. Mirrors the shared form runner's reading of
 * the body `appearance`: without `placement-map` a `geopoint` is locked to the device's live GPS
 * fix and a `geotrace` / `geoshape` to a GPS walk, neither of which the web dashboard can do.
 */
enum class GeometryCapture(val label: String, val description: String, val appearance: String) {
  GPS_ONLY("GPS only", "Collectors record their device's GPS position. Not available on web.", ""),
  GPS_OR_MAP(
    "GPS or draw on map",
    "Collectors can use their GPS position or place it on the map. Required for web.",
    "placement-map",
  ),
}

/**
 * A single `<item>` of a `select1` / `select` question.
 *
 * @property image optional picture shown next to the label; exported as an XForms itext `image`
 *   form (XLSForm `media::image` column).
 * @property colorHex optional `#RRGGBB` display color. Ground-specific, so it is never written to
 *   XForms.
 */
data class EditorChoice(
  val value: String,
  val label: String,
  val image: EditorChoiceImage? = null,
  val colorHex: String? = null,
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
  val label: String,
  val symbol: String,
  val needsValue: Boolean = true,
) {
  EQUALS("equals", "="),
  NOT_EQUALS("does not equal", "≠"),
  GREATER_THAN("is greater than", ">"),
  LESS_THAN("is less than", "<"),
  INCLUDES("includes", "includes"),
  IS_ANSWERED("is answered", "answered", needsValue = false);

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
 * Where a [EditorQuestionType.SELECT_ONE] or [EditorQuestionType.SELECT_MULTIPLE] question gets its
 * options: a manual list of [EditorChoice]s, a survey Map layer, or a survey Data table.
 */
enum class ChoiceSource(val label: String) {
  MANUAL("Manual list"),
  MAP_LAYER("Map layer"),
  DATA_TABLE("Data table");

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
   * How collectors record the answer of a geometry question ([EditorQuestionType.isGeometry]);
   * ignored for other types. Exported as the body `appearance`.
   */
  val capture: GeometryCapture = GeometryCapture.GPS_ONLY,
  /**
   * How collectors provide the answer of a media question ([EditorQuestionType.isMedia]); ignored
   * for other types. Exported as the body `appearance` (`new` for capture only).
   */
  val mediaSource: MediaSource = MediaSource.CAPTURE_OR_UPLOAD,
) {
  val isConditional: Boolean
    get() = relevance != null

  /**
   * Whether this question pulls its choices from a Map layer or Data table rather than [choices].
   */
  val usesDatasetChoices: Boolean
    get() = choiceSource.isDataset || choiceDatasetId != null

  /**
   * Effective [ChoiceSource] for this question, inferring [ChoiceSource.MAP_LAYER] vs
   * [ChoiceSource.DATA_TABLE] from [datasets] when [choiceDatasetId] is set.
   */
  fun effectiveChoiceSource(datasets: List<EditorDataset> = emptyList()): ChoiceSource {
    val datasetId = choiceDatasetId
    if (datasetId != null) {
      val dataset = datasets.firstOrNull { it.id == datasetId }
      if (dataset != null) {
        return if (dataset.isMapLayer) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
      }
      return if (choiceSource.isDataset) choiceSource else ChoiceSource.MAP_LAYER
    }
    return choiceSource
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

  /** Whether this is a geometry question that the web dashboard can't answer (GPS only). */
  val isWebIncompatible: Boolean
    get() = type.isGeometry && capture == GeometryCapture.GPS_ONLY
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
) {
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
   * Geometry questions collectors can't answer in the web dashboard because they are
   * [GeometryCapture.GPS_ONLY]. Only a problem when the Form is available on web.
   */
  fun webIncompatibleGeometryQuestions(): List<EditorQuestion> =
    if (availability.includesWeb) questions.filter { it.isWebIncompatible } else emptyList()

  /** Questions before [key] whose answers can drive display logic. */
  fun eligibleRelevanceSources(key: String): List<EditorQuestion> {
    val index = indexOf(key)
    if (index <= 0) return emptyList()
    return questions.subList(0, index).filter { it.type != EditorQuestionType.NOTE }
  }

  /**
   * Plain-language sentence for [question]'s display logic, using question labels rather than data
   * names, e.g. `Shown only if "Are shade trees present?" equals Yes.`
   */
  fun relevanceSummary(
    question: EditorQuestion,
    datasets: List<EditorDataset> = emptyList(),
  ): String? {
    val relevance = question.relevance ?: return null
    val source = find(relevance.sourceQuestionKey) ?: return "Refers to a deleted question."
    val title = "\"${source.label.ifBlank { source.name }}\""
    if (!relevance.operator.needsValue) return "Shown only if $title is answered."
    val value =
      source.resolvedChoices(datasets).firstOrNull { it.value == relevance.value }?.label
        ?: relevance.value.ifBlank {
          return null
        }
    return "Shown only if $title ${relevance.operator.label} $value."
  }

  /** Short human description of [question]'s display logic, e.g. `if has_shade = yes`. */
  fun describeRelevance(
    question: EditorQuestion,
    datasets: List<EditorDataset> = emptyList(),
  ): String? {
    val relevance = question.relevance ?: return null
    val source = find(relevance.sourceQuestionKey) ?: return "if (missing question)"
    return if (relevance.operator.needsValue) {
      val shownValue =
        source.resolvedChoices(datasets).firstOrNull { it.value == relevance.value }?.label
          ?: relevance.value
      "if ${source.name} ${relevance.operator.symbol} ${shownValue.ifBlank { "…" }}"
    } else {
      "if ${source.name} is answered"
    }
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
   * For every slot, walks forward: each conditional question may be shown (a
   * [FlowEdgeKind.CONDITIONAL] edge) or skipped, so the walk continues until the first
   * unconditional question or the end.
   */
  fun edges(form: EditorForm): List<FlowEdge> {
    val questions = form.questions
    val end = endSlot(form)
    val result = mutableListOf<FlowEdge>()
    for (from in 0 until end) {
      var to = from + 1
      while (to <= end) {
        if (to == end || !questions[to - 1].isConditional) {
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

/** A problem that prevents the Form from generating valid, unambiguous XForms. */
data class EditorIssue(val questionKey: String?, val message: String)

/** Structural validation of an [EditorForm]. */
object FormEditorValidator {
  private val NAME_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_.-]*$")
  private val RESERVED_NAMES = setOf("meta", "data")

  fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name) && name !in RESERVED_NAMES

  fun validate(form: EditorForm, datasets: List<EditorDataset> = emptyList()): List<EditorIssue> {
    val issues = mutableListOf<EditorIssue>()
    // Form IDs are opaque: generated by FormIds or preserved verbatim from imported XForms.
    if (form.formId.isBlank()) {
      issues += EditorIssue(null, "Form is missing its ID.")
    }
    val nameCounts = form.questions.groupingBy { it.name }.eachCount()
    form.questions.forEachIndexed { index, question ->
      val key = question.key
      when {
        question.name.isBlank() -> issues += EditorIssue(key, "Name is required.")
        !isValidName(question.name) ->
          issues +=
            EditorIssue(key, "Name must start with a letter and use only letters, digits, _ . -")
        (nameCounts[question.name] ?: 0) > 1 ->
          issues += EditorIssue(key, "Name \"${question.name}\" is used more than once.")
      }
      if (question.label.isBlank()) {
        issues += EditorIssue(key, "Label is empty.")
      }
      if (question.type.hasChoices) {
        if (question.usesDatasetChoices) {
          val kindLabel =
            if (question.effectiveChoiceSource(datasets) == ChoiceSource.DATA_TABLE) {
              "data table"
            } else {
              "map layer"
            }
          val choiceDatasetId = question.choiceDatasetId.orEmpty()
          val dataset = datasets.firstOrNull { it.id == choiceDatasetId }
          when {
            choiceDatasetId.isBlank() ->
              issues += EditorIssue(key, "Choose a $kindLabel for the options.")
            datasets.isNotEmpty() && dataset == null ->
              issues +=
                EditorIssue(
                  key,
                  "Map layer or data table \"$choiceDatasetId\" no longer exists.",
                )
            question.allowAddEntity && dataset != null && dataset.isGenerated ->
              issues +=
                EditorIssue(
                  key,
                  "Sample plots in \"${dataset.displayName}\" are generated and can't be added by collectors.",
                )
            question.allowAddEntity &&
              dataset != null &&
              (dataset.key.isNotEmpty() || dataset.linkedFormKey != null) &&
              !dataset.hasCreationForm ->
              issues +=
                EditorIssue(
                  key,
                  "\"${dataset.displayName}\" has no form to add new ${dataset.featureNounPlural}.",
                )
          }
        } else {
          if (question.choices.isEmpty()) {
            issues += EditorIssue(key, "Add at least one choice.")
          }
          val values = question.choices.map { it.value }
          if (values.any { it.isBlank() || it.any(Char::isWhitespace) }) {
            issues += EditorIssue(key, "Choice values must be non-empty and contain no spaces.")
          }
          if (values.toSet().size != values.size) {
            issues += EditorIssue(key, "Choice values must be unique.")
          }
        }
      }
      val relevance = question.relevance
      if (relevance != null) {
        val sourceIndex = form.indexOf(relevance.sourceQuestionKey)
        val source = form.questions.getOrNull(sourceIndex)
        when {
          source == null ->
            issues += EditorIssue(key, "Display logic refers to a deleted question.")
          sourceIndex >= index ->
            issues +=
              EditorIssue(key, "Display logic depends on \"${source.name}\", which comes later.")
          relevance.operator !in RelevanceOperator.availableFor(source.type) ->
            issues +=
              EditorIssue(
                key,
                "\"${relevance.operator.label}\" doesn't apply to ${source.type.label}.",
              )
          relevance.operator.needsValue && relevance.value.isBlank() ->
            issues += EditorIssue(key, "Display logic needs a value to compare against.")
          source.type.isNumeric &&
            relevance.operator.needsValue &&
            relevance.value.toDoubleOrNull() == null ->
            issues += EditorIssue(key, "Display logic value must be a number.")
        }
      }
      issues += ValidationRules.issues(question).map { EditorIssue(key, it) }
    }
    form.webIncompatibleGeometryQuestions().forEach { question ->
      issues += EditorIssue(question.key, webIncompatibleMessage(question))
    }
    return issues
  }

  /** Error shown for a GPS-only geometry question in a Form that is available on web. */
  fun webIncompatibleMessage(question: EditorQuestion): String =
    "\"${question.label.ifBlank { question.name }}\" is GPS only, which web can't capture. " +
      "Choose \"${GeometryCapture.GPS_OR_MAP.label}\" or turn off Available on web."
}

/** Generates ODK-compatible XForms XML from an [EditorForm]. */
object EditorXFormsGenerator {

  /** Returns the XPath `relevant` expression for [question], or `null` if always shown. */
  fun relevantExpression(form: EditorForm, question: EditorQuestion): String? {
    val relevance = question.relevance ?: return null
    val sourceIndex = form.indexOf(relevance.sourceQuestionKey)
    if (sourceIndex < 0 || sourceIndex >= form.indexOf(question.key)) return null
    val source = form.questions[sourceIndex]
    val ref = "/data/${source.name}"
    val literal =
      if (source.type.isNumeric && relevance.value.toDoubleOrNull() != null) {
        relevance.value.trim()
      } else {
        xpathStringLiteral(relevance.value)
      }
    return when (relevance.operator) {
      RelevanceOperator.EQUALS -> "$ref = $literal"
      RelevanceOperator.NOT_EQUALS -> "$ref != $literal"
      RelevanceOperator.GREATER_THAN -> "$ref > $literal"
      RelevanceOperator.LESS_THAN -> "$ref < $literal"
      // ODK XForms spec, "XPath functions": selected(space-delimited-list, value).
      RelevanceOperator.INCLUDES -> "selected($ref, $literal)"
      RelevanceOperator.IS_ANSWERED -> "string-length($ref) > 0"
    }
  }

  /** Media file name referenced by [choice]'s image, unique within the Form. */
  fun choiceImageFileName(question: EditorQuestion, choice: EditorChoice): String? =
    choice.image?.let { "${question.name}-${choice.value}.${it.fileExtension}" }

  /** Element name of the extra choices column carrying [EditorChoice.colorHex]. */
  const val CHOICE_COLOR_COLUMN = "color"

  /**
   * Body `appearance` token indicating that data collectors can add a new map feature or table row
   * inline while selecting from a dataset-backed choice list.
   */
  const val ADD_ENTITY_APPEARANCE = "add-entity"

  /**
   * Whether [question]'s choices are exported as an internal secondary instance plus `itemset`
   * rather than inline `<item>`s: needed as soon as any choice carries extra columns (color) or
   * media (image) when the choices are defined manually on the question.
   */
  fun usesChoiceInstance(question: EditorQuestion): Boolean =
    question.type.hasChoices &&
      !question.usesDatasetChoices &&
      question.choices.any { it.colorHex != null || it.image != null }

  /** Secondary instance id (XLSForm `list_name`) for [question]'s choices. */
  fun choiceListName(question: EditorQuestion): String = question.name

  /** Whether [question]'s choice labels go through itext (`itextId`) because of media. */
  private fun choicesRequireItext(question: EditorQuestion): Boolean =
    question.choices.any { it.image != null }

  /** pyxform's positional itext id for choice [index] of [listName], e.g. `fruits-0`. */
  private fun choiceTextId(listName: String, index: Int): String = "$listName-$index"

  /**
   * Entity declaration derived from a Form's save-to logic and its target dataset, following the
   * ODK XForms Entities specification (version 2024.1.0).
   *
   * The ODK spec leaves `id`, `baseVersion`, and the label empty in the primary instance and fills
   * them with `<bind calculate>` / `<setvalue>`. Ground's form engine evaluates those attributes
   * (and the label text) directly as XPath, so the generator writes the expression in both places:
   * ODK clients overwrite the instance default via the binds, and Ground evaluates it in place.
   */
  private class EntityPlan(val form: EditorForm, val target: EditorDataset) {
    val isUpdate = form.saveTo.mode == SaveToMode.UPDATE
    val hasStatus = form.saveTo.status.enabled
    val selectsTarget = isUpdate && form.saveTo.idSource == EntityIdSource.SELECTED_FEATURE
    val idExpression: String =
      if (isUpdate) SaveToRules.entityIdExpression(form, target).orEmpty() else "uuid()"
    val baseVersionExpression: String =
      if (isUpdate && idExpression.isNotEmpty()) {
        "instance('${target.id}')/root/item[name = $idExpression]/__version"
      } else {
        ""
      }
    val labelExpression: String? =
      if (isUpdate) {
        null
      } else {
        val labelQuestion =
          form.questions.firstOrNull { it.name == target.labelProperty }
            ?: form.questions.firstOrNull { it.type == EditorQuestionType.TEXT }
        labelQuestion?.let { "/data/${it.name}" } ?: xpathStringLiteral(form.title)
      }

    /** `entities:saveto` property per question key. */
    val saveTo: Map<String, String> =
      if (isUpdate) {
        form.saveTo.mappings
          .mapNotNull { m -> m.property?.let { m.questionKey to it } }
          .filter { (key, prop) ->
            form.find(key)?.type?.let { it != EditorQuestionType.NOTE } == true &&
              (!hasStatus || prop !in SaveToRules.STATUS_PROPERTIES)
          }
          .toMap()
      } else {
        val geometryKey = form.primaryGeometryQuestion?.key
        SaveToRules.savableQuestions(form)
          .mapNotNull { q ->
            when {
              q.key == geometryKey && target.isMapLayer -> q.key to SaveToRules.GEOMETRY_PROPERTY
              SaveToRules.isReservedProperty(q.name) -> null
              hasStatus && q.name in SaveToRules.STATUS_PROPERTIES -> null
              else -> q.key to q.name
            }
          }
          .toMap()
      }
  }

  /**
   * Compiles [form] into a [FormDefinition] protocol buffer model. When [target] is given,
   * submissions add a feature to it or update one of its features, per [EditorForm.saveTo]. For
   * updates or dataset-backed choice questions, [inlineRows] embeds the dataset's current features
   * in its secondary instance (for previews, where the `jr://file-csv/<dataset>.csv` attachment
   * isn't available).
   */
  fun compile(
    form: EditorForm,
    target: EditorDataset? = null,
    inlineRows: Boolean = false,
    datasets: List<EditorDataset> = emptyList(),
  ): FormDefinition {
    val entity = target?.let { EntityPlan(form, it) }
    val choiceInstances = form.questions.filter(::usesChoiceInstance)
    val itextQuestions = choiceInstances.filter(::choicesRequireItext)

    val translations =
      if (itextQuestions.isNotEmpty()) {
        val strings = buildMap {
          itextQuestions.forEach { question ->
            val listName = choiceListName(question)
            question.choices.forEachIndexed { index, choice ->
              val media =
                choiceImageFileName(question, choice)?.let { fileName ->
                  MediaRef(image_uri = "jr://images/$fileName")
                }
              put(
                choiceTextId(listName, index),
                LocalizedString(value_ = choice.label, media = media),
              )
            }
          }
        }
        TranslationCatalog(
          languages =
            listOf(LanguageTranslation(language = "default", is_default = true, strings = strings))
        )
      } else {
        null
      }

    val schemaFields = buildList {
      if (entity?.selectsTarget == true) {
        add(
          FieldDefinition(name = SaveToRules.TARGET_ENTITY_FIELD, type = DataType.TYPE_SELECT_ONE)
        )
      }
      form.questions.forEach { question ->
        add(FieldDefinition(name = question.name, type = dataTypeOf(question.type)))
      }
      if (entity?.hasStatus == true) {
        add(FieldDefinition(name = SaveToRules.STATUS_FIELD, type = DataType.TYPE_STRING))
        add(FieldDefinition(name = SaveToRules.MARKER_SYMBOL_FIELD, type = DataType.TYPE_STRING))
        add(FieldDefinition(name = SaveToRules.MARKER_COLOR_FIELD, type = DataType.TYPE_STRING))
      }
    }

    val primaryInstance =
      PrimaryInstance(
        record_schema = RecordSchema(name = "data", title = form.title, fields = schemaFields)
      )

    val secondaryInstances = buildList {
      val emittedDatasetIds = mutableSetOf<String>()
      if (entity?.isUpdate == true) {
        add(buildDatasetSecondaryInstance(entity.target, inlineRows))
        emittedDatasetIds += entity.target.id
      }
      form.questions.forEach { question ->
        val datasetId =
          question.choiceDatasetId?.takeIf { question.type.hasChoices && it.isNotBlank() }
        if (datasetId != null && emittedDatasetIds.add(datasetId)) {
          val dataset =
            datasets.firstOrNull { it.id == datasetId } ?: target?.takeIf { it.id == datasetId }
          if (dataset != null) {
            add(buildDatasetSecondaryInstance(dataset, inlineRows))
          } else {
            add(buildFallbackDatasetSecondaryInstance(datasetId, question.choices, inlineRows))
          }
        }
      }
      choiceInstances.forEach { question ->
        val requiresItext = choicesRequireItext(question)
        val listName = choiceListName(question)
        val rows =
          question.choices.mapIndexed { index, choice ->
            val cols = buildMap {
              put("name", choice.value)
              if (requiresItext) {
                put("itextId", choiceTextId(listName, index))
              } else {
                put("label", choice.label)
              }
              choice.colorHex?.let { color -> put(CHOICE_COLOR_COLUMN, color) }
            }
            secondaryInstanceRow(cols)
          }
        add(SecondaryInstance(id = listName, rows = rows))
      }
    }

    val bindings = buildList {
      if (entity?.selectsTarget == true) {
        add(
          FieldBinding(
            field_path = SaveToRules.TARGET_ENTITY_FIELD,
            type = DataType.TYPE_SELECT_ONE,
            required_expression = "true()",
          )
        )
      }
      form.questions.forEach { question ->
        val constraintExpr =
          if (!question.type.isReadOnly) {
            ValidationRules.constraintExpression(question.type, question.validation).orEmpty()
          } else {
            ""
          }
        val constraintMsg =
          if (constraintExpr.isNotEmpty()) {
            ValidationRules.message(question.type, question.validation).orEmpty()
          } else {
            ""
          }
        add(
          FieldBinding(
            field_path = question.name,
            type = dataTypeOf(question.type),
            required_expression =
              if (question.required && !question.type.isReadOnly) "true()" else "",
            read_only = question.type.isReadOnly,
            relevant_expression = relevantExpression(form, question).orEmpty(),
            constraint_expression = constraintExpr,
            constraint_message = constraintMsg,
            entity_saveto = entity?.saveTo?.get(question.key).orEmpty(),
          )
        )
      }
      if (entity != null) {
        addAll(buildEntityBindings(entity))
      }
    }

    val actions = buildList {
      if (entity != null && !entity.isUpdate) {
        add(
          ActionDef(
            events = listOf(EventType.EVENT_INSTANCE_FIRST_LOAD),
            type = ActionType.ACTION_SET_VALUE,
            target_field = "orx:meta/entities:entity/@id",
            value_expression = "uuid()",
          )
        )
      }
    }

    val entities =
      if (entity != null) {
        val mappings =
          bindings
            .filter { it.entity_saveto.isNotEmpty() }
            .map {
              EntityPropertyMapping(
                entity_property = it.entity_saveto,
                source_field_path = it.field_path,
              )
            }
        val syncMeta =
          if (entity.isUpdate && entity.baseVersionExpression.isNotEmpty()) {
            EntitySyncMetadata(base_version_expression = entity.baseVersionExpression)
          } else {
            null
          }
        listOf(
          EntityDeclaration(
            dataset = entity.target.id,
            entity_id_expression = entity.idExpression,
            label_expression = entity.labelExpression.orEmpty(),
            create_condition = if (entity.isUpdate) "" else "1",
            update_condition = if (entity.isUpdate) "1" else "",
            sync_metadata = syncMeta,
            property_mappings = mappings,
          )
        )
      } else {
        emptyList()
      }

    val viewComponents = buildList {
      if (entity?.selectsTarget == true) {
        add(ViewComponent(control = buildTargetPickerControl(entity.target)))
      }
      form.questions.forEach { question ->
        add(ViewComponent(control = buildQuestionControl(question)))
      }
    }

    return FormDefinition(
      proto =
        FormDef(
          form_id = form.formId,
          title = form.title,
          version = "1",
          model =
            ModelDef(
              primary_instance = primaryInstance,
              secondary_instances = secondaryInstances,
              bindings = bindings,
              translations = translations,
              actions = actions,
              entities = entities,
            ),
          view = ViewDef(components = viewComponents),
        )
    )
  }

  /**
   * Generates the XForms XML for [form] at the export boundary by serializing [compile]'s
   * [FormDefinition].
   */
  fun toXml(
    form: EditorForm,
    target: EditorDataset? = null,
    inlineRows: Boolean = false,
    datasets: List<EditorDataset> = emptyList(),
  ): String =
    XFormsXmlSerializer.serialize(
      compile(form = form, target = target, inlineRows = inlineRows, datasets = datasets).proto,
      prettyPrint = true,
    )

  private fun dataTypeOf(type: EditorQuestionType): DataType =
    when (type) {
      EditorQuestionType.TEXT,
      EditorQuestionType.LONG_TEXT,
      EditorQuestionType.NOTE -> DataType.TYPE_STRING
      EditorQuestionType.INTEGER -> DataType.TYPE_INT32
      EditorQuestionType.DECIMAL -> DataType.TYPE_DOUBLE
      EditorQuestionType.SELECT_ONE -> DataType.TYPE_SELECT_ONE
      EditorQuestionType.SELECT_MULTIPLE -> DataType.TYPE_SELECT_MULTIPLE
      EditorQuestionType.DATE -> DataType.TYPE_DATE
      EditorQuestionType.LOCATION -> DataType.TYPE_GEOPOINT
      EditorQuestionType.LINE -> DataType.TYPE_GEOTRACE
      EditorQuestionType.POLYGON -> DataType.TYPE_GEOSHAPE
      EditorQuestionType.PHOTO,
      EditorQuestionType.VIDEO,
      EditorQuestionType.AUDIO -> DataType.TYPE_BINARY
    }

  private fun controlTypeOf(type: EditorQuestionType): ControlType =
    when (type) {
      EditorQuestionType.SELECT_ONE -> ControlType.CONTROL_SELECT_ONE
      EditorQuestionType.SELECT_MULTIPLE -> ControlType.CONTROL_SELECT_MULTIPLE
      EditorQuestionType.PHOTO,
      EditorQuestionType.VIDEO,
      EditorQuestionType.AUDIO -> ControlType.CONTROL_UPLOAD
      else -> ControlType.CONTROL_INPUT
    }

  /** Secondary instance listing the target's features, in ODK Entities list form. */
  private fun buildDatasetSecondaryInstance(
    target: EditorDataset,
    inlineRows: Boolean,
  ): SecondaryInstance {
    val src = "jr://file-csv/${target.id}.csv"
    if (!inlineRows) {
      return SecondaryInstance(id = target.id, uri = src)
    }
    val columns = target.updatableProperties.map { it.name }.filter { XML_NAME.matches(it) }
    val rows =
      target.rows.map { row ->
        val cols = buildMap {
          put("name", row.name)
          put("label", row.label)
          put("__version", row.values["__version"] ?: "1")
          columns.forEach { column -> put(column, row.values[column].orEmpty()) }
        }
        secondaryInstanceRow(cols)
      }
    return SecondaryInstance(
      id = target.id,
      uri = src,
      root_name = if (rows.isEmpty()) "root" else "",
      rows = rows,
    )
  }

  /**
   * Secondary instance for a dataset-backed choice question when the dataset is not in the active
   * survey catalog (e.g., an imported standalone XForm).
   */
  private fun buildFallbackDatasetSecondaryInstance(
    datasetId: String,
    choices: List<EditorChoice>,
    inlineRows: Boolean,
  ): SecondaryInstance {
    val src = "jr://file-csv/$datasetId.csv"
    if (!inlineRows || choices.isEmpty()) {
      return SecondaryInstance(id = datasetId, uri = src)
    }
    val rows = choices.map { choice ->
      secondaryInstanceRow("name" to choice.value, "label" to choice.label)
    }
    return SecondaryInstance(id = datasetId, uri = src, rows = rows)
  }

  private fun buildEntityBindings(entity: EntityPlan): List<FieldBinding> = buildList {
    if (entity.hasStatus) {
      val statusCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.label }
      val symbolCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.symbol.symbol }
      val colorCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.colorHex }
      add(
        FieldBinding(
          field_path = SaveToRules.STATUS_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = statusCalc,
          entity_saveto = SaveToRules.STATUS_PROPERTY,
        )
      )
      add(
        FieldBinding(
          field_path = SaveToRules.MARKER_SYMBOL_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = symbolCalc,
          entity_saveto = SaveToRules.MARKER_SYMBOL_PROPERTY,
        )
      )
      add(
        FieldBinding(
          field_path = SaveToRules.MARKER_COLOR_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = colorCalc,
          entity_saveto = SaveToRules.MARKER_COLOR_PROPERTY,
        )
      )
    }
    val path = "orx:meta/entities:entity"
    if (entity.isUpdate) {
      add(
        FieldBinding(
          field_path = "$path/@id",
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = entity.idExpression,
        )
      )
      add(
        FieldBinding(
          field_path = "$path/@baseVersion",
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = entity.baseVersionExpression,
        )
      )
    } else {
      add(FieldBinding(field_path = "$path/@id", type = DataType.TYPE_STRING, read_only = true))
      entity.labelExpression?.let { labelExpr ->
        add(
          FieldBinding(
            field_path = "$path/entities:label",
            type = DataType.TYPE_STRING,
            read_only = true,
            calculate_expression = labelExpr,
          )
        )
      }
    }
  }

  /** Picker for the feature to update; the app pre-fills it when opened from a map feature. */
  private fun buildTargetPickerControl(target: EditorDataset): ControlDef {
    val noun = if (target.isMapLayer) "Map feature" else "Row"
    return ControlDef(
      field_ref = SaveToRules.TARGET_ENTITY_FIELD,
      type = ControlType.CONTROL_SELECT_ONE,
      label = LabelDef(text = "$noun to update"),
      hint =
        if (target.isMapLayer) {
          LabelDef(text = "Filled in automatically when the form is opened from a map feature.")
        } else {
          null
        },
      itemset =
        ItemsetDef(
          instance_id = target.id,
          nodeset_path = "/root/item",
          value_ref = "name",
          label_ref = "label",
        ),
    )
  }

  private fun buildQuestionControl(question: EditorQuestion): ControlDef {
    val type = question.type
    val choiceDatasetId =
      question.choiceDatasetId?.takeIf {
        type.hasChoices && question.usesDatasetChoices && it.isNotBlank()
      }
    val itemset =
      when {
        choiceDatasetId != null ->
          ItemsetDef(
            instance_id = choiceDatasetId,
            nodeset_path = "/root/item",
            value_ref = "name",
            label_ref = "label",
          )
        usesChoiceInstance(question) ->
          ItemsetDef(
            instance_id = choiceListName(question),
            nodeset_path = "/root/item",
            value_ref = "name",
            label_ref = if (choicesRequireItext(question)) "jr:itext(itextId)" else "label",
          )
        else -> null
      }
    val choices =
      if (type.hasChoices && !question.usesDatasetChoices && itemset == null) {
        question.choices.map { choice ->
          ChoiceItem(value_ = choice.value, label = LabelDef(text = choice.label))
        }
      } else {
        emptyList()
      }
    return ControlDef(
      field_ref = question.name,
      type = controlTypeOf(type),
      label = LabelDef(text = question.label),
      hint = question.hint.takeIf { it.isNotBlank() }?.let { LabelDef(text = it) },
      appearance = bodyAppearance(question).orEmpty(),
      media_type = type.mediaType,
      choices = choices,
      itemset = itemset,
    )
  }

  private val XML_NAME = Regex("^[A-Za-z_][A-Za-z0-9_.-]*$")

  /**
   * Body `appearance` of [question]: the type's own appearance (e.g. `multiline`) plus, for
   * geometry questions, the capture mode's (`placement-map`) and, for media questions, the media
   * source's (`new`), space-separated per the ODK XForms spec. `null` when none applies.
   */
  fun bodyAppearance(question: EditorQuestion): String? =
    listOf(
        question.type.appearance,
        if (question.type.isGeometry) question.capture.appearance else "",
        if (question.type.isMedia) question.mediaSource.appearance else "",
        if (question.type.hasChoices && question.usesDatasetChoices && question.allowAddEntity) {
          ADD_ENTITY_APPEARANCE
        } else {
          ""
        },
      )
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString(" ")
      .ifEmpty { null }

  /** XPath 1.0 section 3.7: literals may be delimited by either `'` or `"`. */
  private fun xpathStringLiteral(value: String): String =
    if (value.contains('\'')) "\"$value\"" else "'$value'"
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

/** Templates for new Forms created in the editor. */
object EditorFormTemplates {
  /** A new Form with a single text question, used by "+" in the Survey editor. */
  fun blank(formId: String, title: String): EditorForm =
    EditorForm(
      formId = formId,
      title = title,
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = "text",
            type = EditorQuestionType.TEXT,
            label = "New text question",
          )
        ),
    )
}
