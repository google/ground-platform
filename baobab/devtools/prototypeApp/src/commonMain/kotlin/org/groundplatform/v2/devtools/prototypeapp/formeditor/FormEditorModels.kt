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
  LOCATION("Location", "location", bindType = "geopoint", bodyElement = "input"),
  PHOTO("Photo", "photo", bindType = "binary", bodyElement = "upload", mediaType = "image/*"),
  NOTE("Note", "note", bindType = "string", bodyElement = "input", isReadOnly = true),
}

/** A single `<item>` of a `select1` / `select` question. */
data class EditorChoice(val value: String, val label: String)

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
) {
  val isConditional: Boolean
    get() = relevance != null
}

/** The whole Form being edited. */
data class EditorForm(
  val formId: String,
  val title: String,
  val questions: List<EditorQuestion>,
  /** What a submission does to the survey's Map layers and Data tables. */
  val saveTo: EditorSaveTo = EditorSaveTo(),
) {
  fun indexOf(key: String): Int = questions.indexOfFirst { it.key == key }

  fun find(key: String?): EditorQuestion? = questions.firstOrNull { it.key == key }

  /** Location question whose answer becomes a new map feature's geometry. */
  val primaryGeometryQuestion: EditorQuestion?
    get() = questions.firstOrNull { it.type == EditorQuestionType.LOCATION }

  /** Whether submissions capture a geometry, i.e. add map features rather than table rows. */
  val hasGeometry: Boolean
    get() = primaryGeometryQuestion != null

  /** Questions before [key] whose answers can drive display logic. */
  fun eligibleRelevanceSources(key: String): List<EditorQuestion> {
    val index = indexOf(key)
    if (index <= 0) return emptyList()
    return questions.subList(0, index).filter { it.type != EditorQuestionType.NOTE }
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
    }
    return issues
  }
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

  /**
   * Entity declaration derived from a Form's save-to logic and its target dataset, following the
   * ODK XForms Entities specification (version 2024.1.0).
   *
   * The ODK spec leaves `id`, `baseVersion`, and the label empty in the primary instance and fills
   * them with `<bind calculate>` / `<setvalue>`. Ground's form engine evaluates those attributes
   * (and the label text) directly as XPath, so the generator writes the expression in both places:
   * ODK clients overwrite the instance default via the binds, and Ground evaluates it in place.
   */
  private class EntityPlan(form: EditorForm, val target: EditorDataset) {
    val isUpdate = form.saveTo.mode == SaveToMode.UPDATE
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
          .filter { (key, _) ->
            form.find(key)?.type?.let { it != EditorQuestionType.NOTE } == true
          }
          .toMap()
      } else {
        val geometryKey = form.primaryGeometryQuestion?.key
        SaveToRules.savableQuestions(form)
          .mapNotNull { q ->
            when {
              q.key == geometryKey && target.isMapLayer -> q.key to SaveToRules.GEOMETRY_PROPERTY
              SaveToRules.isReservedProperty(q.name) -> null
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
      appendLine("      <instance>")
      appendLine("""        <data id="${escape(form.formId)}" version="1">""")
      if (entity?.selectsTarget == true)
        appendLine("          <${SaveToRules.TARGET_ENTITY_FIELD}/>")
      form.questions.forEach { appendLine("          <${it.name}/>") }
      appendLine("          <orx:meta>")
      appendLine("            <orx:instanceID/>")
      if (entity != null) appendEntityDeclaration(entity)
      appendLine("          </orx:meta>")
      appendLine("        </data>")
      appendLine("      </instance>")
      if (entity?.isUpdate == true) appendDatasetInstance(entity.target, inlineRows)
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
          if (type.appearance.isNotEmpty()) add("""appearance="${type.appearance}"""")
          if (type.mediaType.isNotEmpty()) add("""mediatype="${type.mediaType}"""")
        }
        appendLine("    <${type.bodyElement} ${attrs.joinToString(" ")}>")
        appendLine("      <label>${escape(question.label)}</label>")
        if (question.hint.isNotBlank()) appendLine("      <hint>${escape(question.hint)}</hint>")
        if (type.hasChoices) {
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
      appendLine("          <item>")
      appendLine("            <name>${escape(row.name)}</name>")
      appendLine("            <label>${escape(row.label)}</label>")
      appendLine("            <__version>1</__version>")
      columns.forEach { column ->
        appendLine("            <$column>${escape(row.values[column].orEmpty())}</$column>")
      }
      appendLine("          </item>")
    }
    appendLine("        </root>")
    appendLine("      </instance>")
  }

  private fun StringBuilder.appendEntityBinds(entity: EntityPlan) {
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
