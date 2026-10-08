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
  fun relevanceSummary(question: EditorQuestion): String? {
    val relevance = question.relevance ?: return null
    val source = find(relevance.sourceQuestionKey) ?: return "Refers to a deleted question."
    val title = "\"${source.label.ifBlank { source.name }}\""
    if (!relevance.operator.needsValue) return "Shown only if $title is answered."
    val value =
      source.choices.firstOrNull { it.value == relevance.value }?.label
        ?: relevance.value.ifBlank {
          return null
        }
    return "Shown only if $title ${relevance.operator.label} $value."
  }

  /** Short human description of [question]'s display logic, e.g. `if has_shade = yes`. */
  fun describeRelevance(question: EditorQuestion): String? {
    val relevance = question.relevance ?: return null
    val source = find(relevance.sourceQuestionKey) ?: return "if (missing question)"
    return if (relevance.operator.needsValue) {
      val shownValue =
        source.choices.firstOrNull { it.value == relevance.value }?.label ?: relevance.value
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

  fun validate(form: EditorForm): List<EditorIssue> {
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
   * Whether [question]'s choices are exported as an internal secondary instance plus `itemset`
   * rather than inline `<item>`s: needed as soon as any choice carries extra columns (color) or
   * media (image).
   */
  fun usesChoiceInstance(question: EditorQuestion): Boolean =
    question.type.hasChoices && question.choices.any { it.colorHex != null || it.image != null }

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
   * Generates the XForms for [form]. When [target] is given, submissions add a feature to it or
   * update one of its features, per [EditorForm.saveTo]. For updates, [inlineRows] embeds the
   * target's current features in its secondary instance (for previews, where the
   * `jr://file-csv/<dataset>.csv` attachment isn't available).
   */
  fun toXml(form: EditorForm, target: EditorDataset? = null, inlineRows: Boolean = false): String =
    buildString {
      val entity = target?.let { EntityPlan(form, it) }
      val choiceInstances = form.questions.filter(::usesChoiceInstance)
      val itextQuestions = choiceInstances.filter(::choicesRequireItext)
      appendLine("""<?xml version="1.0"?>""")
      appendLine("""<h:html xmlns="http://www.w3.org/2002/xforms"""")
      appendLine("""        xmlns:h="http://www.w3.org/1999/xhtml"""")
      appendLine("""        xmlns:jr="http://openrosa.org/javarosa"""")
      if (entity == null) {
        appendLine("""        xmlns:orx="http://openrosa.org/xforms">""")
      } else {
        appendLine("""        xmlns:orx="http://openrosa.org/xforms"""")
        appendLine("""        xmlns:entities="http://www.opendatakit.org/xforms/entities">""")
      }
      appendLine("  <h:head>")
      appendLine("    <h:title>${escape(form.title)}</h:title>")
      if (entity == null) {
        appendLine("""    <model orx:xforms-version="1.0.0">""")
      } else {
        appendLine(
          """    <model orx:xforms-version="1.0.0" entities:entities-version="2024.1.0">"""
        )
      }
      if (itextQuestions.isNotEmpty()) {
        // ODK XForms spec, "Languages" / "Media": choice labels reference itext entries whose
        // `image` form points at a form attachment (XLSForm `media::image`). Ids follow pyxform's
        // positional `<list_name>-<index>` scheme.
        appendLine("      <itext>")
        appendLine("""        <translation default="true()" lang="default">""")
        itextQuestions.forEach { question ->
          val listName = choiceListName(question)
          question.choices.forEachIndexed { index, choice ->
            appendLine("""          <text id="${escape(choiceTextId(listName, index))}">""")
            appendLine("            <value>${escape(choice.label)}</value>")
            choiceImageFileName(question, choice)?.let { fileName ->
              appendLine(
                """            <value form="image">jr://images/${escape(fileName)}</value>"""
              )
            }
            appendLine("          </text>")
          }
        }
        appendLine("        </translation>")
        appendLine("      </itext>")
      }
      appendLine("      <instance>")
      appendLine("""        <data id="${escape(form.formId)}" version="1">""")
      if (entity?.selectsTarget == true)
        appendLine("          <${SaveToRules.TARGET_ENTITY_FIELD}/>")
      form.questions.forEach { appendLine("          <${it.name}/>") }
      if (entity?.hasStatus == true) {
        appendLine("          <${SaveToRules.STATUS_FIELD}/>")
        appendLine("          <${SaveToRules.MARKER_SYMBOL_FIELD}/>")
        appendLine("          <${SaveToRules.MARKER_COLOR_FIELD}/>")
      }
      appendLine("          <orx:meta>")
      appendLine("            <orx:instanceID/>")
      if (entity != null) appendEntityDeclaration(entity)
      appendLine("          </orx:meta>")
      appendLine("        </data>")
      appendLine("      </instance>")
      if (entity?.isUpdate == true) appendDatasetInstance(entity.target, inlineRows)
      // Internal secondary instances, shaped like pyxform's output for an XLSForm choices sheet:
      // one <item> per row with <name>, <label> (or <itextId>), then any extra columns. Extra
      // columns such as `color` are ordinary instance data that other clients ignore.
      choiceInstances.forEach { question ->
        val requiresItext = choicesRequireItext(question)
        val listName = choiceListName(question)
        appendLine("""      <instance id="${escape(listName)}">""")
        appendLine("        <root>")
        question.choices.forEachIndexed { index, choice ->
          appendLine("          <item>")
          appendLine("            <name>${escape(choice.value)}</name>")
          if (requiresItext) {
            appendLine("            <itextId>${escape(choiceTextId(listName, index))}</itextId>")
          } else {
            appendLine("            <label>${escape(choice.label)}</label>")
          }
          choice.colorHex?.let { color ->
            appendLine("            <$CHOICE_COLOR_COLUMN>${escape(color)}</$CHOICE_COLOR_COLUMN>")
          }
          appendLine("          </item>")
        }
        appendLine("        </root>")
        appendLine("      </instance>")
      }
      if (entity?.selectsTarget == true) {
        appendLine(
          """      <bind nodeset="/data/${SaveToRules.TARGET_ENTITY_FIELD}" type="string" required="true()"/>"""
        )
      }
      form.questions.forEach { question ->
        val attrs = buildList {
          add("""nodeset="/data/${question.name}"""")
          add("""type="${question.type.bindType}"""")
          if (question.required && !question.type.isReadOnly) add("""required="true()"""")
          if (question.type.isReadOnly) add("""readonly="true()"""")
          relevantExpression(form, question)?.let { add("""relevant="${escape(it)}"""") }
          // ODK XForms spec, "Bindings": `constraint` and `jr:constraintMsg`.
          if (!question.type.isReadOnly) {
            ValidationRules.constraintExpression(question.type, question.validation)?.let {
              add("""constraint="${escape(it)}"""")
              ValidationRules.message(question.type, question.validation)?.let { msg ->
                add("""jr:constraintMsg="${escape(msg)}"""")
              }
            }
          }
          entity?.saveTo?.get(question.key)?.let { add("""entities:saveto="${escape(it)}"""") }
        }
        appendLine("      <bind ${attrs.joinToString(" ")}/>")
      }
      if (entity != null) appendEntityBinds(entity)
      appendLine("    </model>")
      appendLine("  </h:head>")
      appendLine("  <h:body>")
      if (entity?.selectsTarget == true) appendTargetPicker(entity.target)
      form.questions.forEach { question ->
        val type = question.type
        val attrs = buildList {
          add("""ref="/data/${question.name}"""")
          bodyAppearance(question)?.let { add("""appearance="${escape(it)}"""") }
          if (type.mediaType.isNotEmpty()) add("""mediatype="${type.mediaType}"""")
        }
        appendLine("    <${type.bodyElement} ${attrs.joinToString(" ")}>")
        appendLine("      <label>${escape(question.label)}</label>")
        if (question.hint.isNotBlank()) appendLine("      <hint>${escape(question.hint)}</hint>")
        if (usesChoiceInstance(question)) {
          val nodeset = "instance('${choiceListName(question)}')/root/item"
          val labelRef = if (choicesRequireItext(question)) "jr:itext(itextId)" else "label"
          appendLine("""      <itemset nodeset="${escape(nodeset)}">""")
          appendLine("""        <value ref="name"/>""")
          appendLine("""        <label ref="${escape(labelRef)}"/>""")
          appendLine("      </itemset>")
        } else if (type.hasChoices) {
          question.choices.forEach { choice ->
            appendLine("      <item>")
            appendLine("        <label>${escape(choice.label)}</label>")
            appendLine("        <value>${escape(choice.value)}</value>")
            appendLine("      </item>")
          }
        }
        appendLine("    </${type.bodyElement}>")
      }
      appendLine("  </h:body>")
      append("</h:html>")
    }

  private fun StringBuilder.appendEntityDeclaration(entity: EntityPlan) {
    val attrs = buildList {
      add("""dataset="${escape(entity.target.id)}"""")
      add("""id="${escape(entity.idExpression)}"""")
      if (entity.isUpdate) {
        add("""update="1"""")
        add("""baseVersion="${escape(entity.baseVersionExpression)}"""")
      } else {
        add("""create="1"""")
      }
    }
    val label = entity.labelExpression
    if (label == null) {
      appendLine("            <entities:entity ${attrs.joinToString(" ")}/>")
    } else {
      appendLine("            <entities:entity ${attrs.joinToString(" ")}>")
      appendLine("              <entities:label>${escape(label)}</entities:label>")
      appendLine("            </entities:entity>")
    }
  }

  /** Secondary instance listing the target's features, in ODK Entities list form. */
  private fun StringBuilder.appendDatasetInstance(target: EditorDataset, inlineRows: Boolean) {
    val id = escape(target.id)
    val src = "jr://file-csv/$id.csv"
    if (!inlineRows) {
      appendLine("""      <instance id="$id" src="$src"/>""")
      return
    }
    val columns = target.updatableProperties.map { it.name }.filter { XML_NAME.matches(it) }
    appendLine("""      <instance id="$id" src="$src">""")
    appendLine("        <root>")
    target.rows.forEach { row ->
      val version = row.values["__version"] ?: "1"
      appendLine("          <item>")
      appendLine("            <name>${escape(row.name)}</name>")
      appendLine("            <label>${escape(row.label)}</label>")
      appendLine("            <__version>${escape(version)}</__version>")
      columns.forEach { column ->
        appendLine("            <$column>${escape(row.values[column].orEmpty())}</$column>")
      }
      appendLine("          </item>")
    }
    appendLine("        </root>")
    appendLine("      </instance>")
  }

  private fun StringBuilder.appendEntityBinds(entity: EntityPlan) {
    if (entity.hasStatus) {
      val statusCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.label }
      val symbolCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.symbol.symbol }
      val colorCalc =
        SaveToRules.statusCalculateExpression(entity.form, entity.target) { it.colorHex }
      appendLine(
        """      <bind nodeset="/data/${SaveToRules.STATUS_FIELD}" type="string" readonly="true()" calculate="${escape(statusCalc)}" entities:saveto="${SaveToRules.STATUS_PROPERTY}"/>"""
      )
      appendLine(
        """      <bind nodeset="/data/${SaveToRules.MARKER_SYMBOL_FIELD}" type="string" readonly="true()" calculate="${escape(symbolCalc)}" entities:saveto="${SaveToRules.MARKER_SYMBOL_PROPERTY}"/>"""
      )
      appendLine(
        """      <bind nodeset="/data/${SaveToRules.MARKER_COLOR_FIELD}" type="string" readonly="true()" calculate="${escape(colorCalc)}" entities:saveto="${SaveToRules.MARKER_COLOR_PROPERTY}"/>"""
      )
    }
    val path = "/data/orx:meta/entities:entity"
    if (entity.isUpdate) {
      appendLine(
        """      <bind nodeset="$path/@id" type="string" readonly="true()" calculate="${escape(entity.idExpression)}"/>"""
      )
      appendLine(
        """      <bind nodeset="$path/@baseVersion" type="string" readonly="true()" calculate="${escape(entity.baseVersionExpression)}"/>"""
      )
    } else {
      // ODK XForms Entities spec: new entity IDs are set once, when the Form is first opened.
      appendLine("""      <bind nodeset="$path/@id" type="string" readonly="true()"/>""")
      appendLine(
        """      <setvalue event="odk-instance-first-load" ref="$path/@id" value="uuid()"/>"""
      )
      entity.labelExpression?.let {
        appendLine(
          """      <bind nodeset="$path/entities:label" type="string" readonly="true()" calculate="${escape(it)}"/>"""
        )
      }
    }
  }

  /** Picker for the feature to update; the app pre-fills it when opened from a map feature. */
  private fun StringBuilder.appendTargetPicker(target: EditorDataset) {
    val noun = if (target.isMapLayer) "Map feature" else "Row"
    appendLine("""    <select1 ref="/data/${SaveToRules.TARGET_ENTITY_FIELD}">""")
    appendLine("      <label>$noun to update</label>")
    if (target.isMapLayer) {
      appendLine(
        "      <hint>Filled in automatically when the form is opened from a map feature.</hint>"
      )
    }
    appendLine("""      <itemset nodeset="instance('${escape(target.id)}')/root/item">""")
    appendLine("""        <value ref="name"/>""")
    appendLine("""        <label ref="label"/>""")
    appendLine("      </itemset>")
    appendLine("    </select1>")
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
      )
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString(" ")
      .ifEmpty { null }

  /** XPath 1.0 section 3.7: literals may be delimited by either `'` or `"`. */
  private fun xpathStringLiteral(value: String): String =
    if (value.contains('\'')) "\"$value\"" else "'$value'"

  private fun escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
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
