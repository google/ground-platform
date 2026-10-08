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

import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.ViewComponent
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability

/**
 * A Form imported into the editor from its XForms definition.
 *
 * @property form the editable Form. Every control of the XForms becomes a question; controls the
 *   editor has no exact type for are mapped to the closest type and listed in [notes].
 * @property createDatasetId dataset new submissions are added to (`<entities:entity create="1">`),
 *   or `null`. The Survey editor links the matching Map layer or Data table to the Form.
 * @property idMatchColumn for Forms that update a feature identified by a question's answer, the
 *   column of the dataset list the answer is matched against (`name`, `label`, or a property); the
 *   Survey editor resolves it to a dataset property once the dataset is known.
 * @property notes what the import could not reproduce exactly, for logging or display.
 */
data class ImportedForm(
  val form: EditorForm,
  val createDatasetId: String? = null,
  val idMatchColumn: String? = null,
  val notes: List<String> = emptyList(),
)

/**
 * Imports XForms definitions (parsed by the shared form engine into a `FormDef`) into
 * [EditorForm]s, the inverse of [EditorXFormsGenerator]. Forms the generator wrote round-trip with
 * the same questions, in the same order, with the same names, labels, types, and choices.
 *
 * Groups and repeats are flattened into plain questions; range, rank, trigger, boolean, time, and
 * date-time controls map to the closest editor type. Nothing is dropped silently: every mismatch is
 * reported in [ImportedForm.notes].
 */
object FormImport {
  /** Imports [xml]. Returns `null` when the XML can't be parsed by the form engine. */
  fun fromXml(
    xml: String,
    formId: String,
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.MOBILE,
  ): ImportedForm? {
    val formDef =
      try {
        XFormsXmlSerializer.deserializeFormDef(xml)
      } catch (e: Exception) {
        return null
      }
    return fromFormDef(formDef, formId, fallbackTitle, availability)
  }

  /** Imports [formDef] as a Form with ID [formId] (defaults to the XForms form ID). */
  fun fromFormDef(
    formDef: FormDef,
    formId: String = formDef.form_id,
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.MOBILE,
  ): ImportedForm = Importer(formDef).run(formId, fallbackTitle, availability)

  private class Importer(private val formDef: FormDef) {
    private val model = formDef.model
    private val bindings: Map<String, FieldBinding> =
      model?.bindings.orEmpty().associateBy { it.field_path }
    private val instances = model?.secondary_instances.orEmpty().associateBy { it.id }
    private val strings: Map<String, String> = buildMap {
      val languages = model?.translations?.languages.orEmpty()
      // Later languages don't override the default one.
      (languages.filter { it.is_default } + languages.filterNot { it.is_default }).forEach { lang ->
        lang.strings.forEach { (id, text) -> if (id !in this) put(id, text.value_) }
      }
    }
    private val notes = mutableListOf<String>()
    private val questions = mutableListOf<EditorQuestion>()
    private val keyByPath = mutableMapOf<String, String>()
    private val usedNames = mutableSetOf<String>()
    private val relevanceByKey = mutableMapOf<String, String>()

    fun run(formId: String, fallbackTitle: String, availability: FormAvailability): ImportedForm {
      walk(formDef.view?.components.orEmpty(), enclosing = "")
      val resolved = questions.map { it.copy(relevance = relevanceOf(it)) }
      val entity = model?.entities?.firstOrNull()
      val isUpdate = entity != null && entity.update_condition.isNotBlank()
      val statusEnabled = bindings.containsKey(SaveToRules.STATUS_FIELD)
      if (statusEnabled) notes += "Status marker rules were reset to the default rules."
      var idMatchColumn: String? = null
      var idSource = EntityIdSource.SELECTED_FEATURE
      var idQuestionKey: String? = null
      if (isUpdate && entity != null) {
        val idExpr = entity.entity_id_expression.trim()
        val byQuestion = ID_BY_QUESTION.matchEntire(idExpr)
        val direct = REF.matchEntire(idExpr)
        when {
          idExpr.contains(SaveToRules.TARGET_ENTITY_FIELD) -> Unit
          byQuestion != null && keyByPath.containsKey(byQuestion.groupValues[3]) -> {
            idSource = EntityIdSource.QUESTION
            idQuestionKey = keyByPath[byQuestion.groupValues[3]]
            idMatchColumn = byQuestion.groupValues[2]
          }
          direct != null && keyByPath.containsKey(direct.groupValues[1]) -> {
            idSource = EntityIdSource.QUESTION
            idQuestionKey = keyByPath[direct.groupValues[1]]
            idMatchColumn = "name"
          }
          else ->
            notes +=
              "Feature ID expression \"$idExpr\" isn't supported; using the selected feature."
        }
      }
      val mappings =
        if (isUpdate) {
          bindings.values
            .filter {
              it.entity_saveto.isNotBlank() && it.entity_saveto !in SaveToRules.STATUS_PROPERTIES
            }
            .mapNotNull { binding ->
              keyByPath[binding.field_path]?.let { EditorFieldMapping(it, binding.entity_saveto) }
            }
        } else {
          emptyList()
        }
      // GPS-only geometry questions can't be answered on web, so such Forms are mobile only.
      val effectiveAvailability =
        if (availability.includesWeb && resolved.any { it.isWebIncompatible }) {
          notes += "Form has GPS-only questions; made available on mobile only."
          if (availability.includesMobile) FormAvailability.MOBILE else FormAvailability.NONE
        } else {
          availability
        }
      val form =
        EditorForm(
          formId = formId,
          title = formDef.title.ifBlank { fallbackTitle }.ifBlank { formId },
          questions = resolved,
          saveTo =
            EditorSaveTo(
              mode = if (isUpdate) SaveToMode.UPDATE else SaveToMode.CREATE,
              targetDatasetId = if (isUpdate) entity?.dataset?.ifBlank { null } else null,
              idSource = idSource,
              idQuestionKey = idQuestionKey,
              mappings = mappings,
              status = EditorStatusConfig(enabled = statusEnabled),
            ),
          availability = effectiveAvailability,
        )
      return ImportedForm(
        form = form,
        createDatasetId = if (!isUpdate) entity?.dataset?.ifBlank { null } else null,
        idMatchColumn = idMatchColumn,
        notes = notes.toList(),
      )
    }

    private fun walk(components: List<ViewComponent>, enclosing: String) {
      components.forEach { component ->
        component.control?.let { importControl(it, enclosing) }
        component.group?.let { group ->
          walk(group.components, join(enclosing, group.field_ref))
        }
        component.repeat?.let { repeat ->
          notes +=
            "Repeat \"${text(repeat.label) ?: repeat.field_ref}\" was imported as plain questions."
          walk(repeat.components, join(enclosing, repeat.field_ref))
        }
      }
    }

    private fun join(enclosing: String, ref: String): String {
      val clean = ref.trim().removePrefix("/")
      return when {
        clean.isEmpty() -> enclosing
        enclosing.isEmpty() || clean.startsWith("$enclosing/") -> clean
        else -> "$enclosing/$clean"
      }
    }

    private fun importControl(control: ControlDef, enclosing: String) {
      val path = join(enclosing, control.field_ref)
      if (path.isEmpty()) {
        notes += "A control without a field reference was skipped."
        return
      }
      val rawName = path.substringAfterLast('/')
      if (rawName == SaveToRules.TARGET_ENTITY_FIELD) {
        // The feature picker is regenerated from the Form's save-to logic.
        return
      }
      val binding = bindings[path]
      val label = text(control.label)
      val name = uniqueName(rawName, path)
      val key = "q${questions.size + 1}"
      val appearance = control.appearance.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
      var choices =
        control.choices.map { choice ->
          EditorChoice(
            value = choice.value_,
            label = text(choice.label) ?: choice.value_,
            colorHex = choice.properties[EditorXFormsGenerator.CHOICE_COLOR_COLUMN],
          )
        }
      var choiceDatasetId: String? = null
      if (choices.isEmpty()) {
        control.itemset?.let { itemset ->
          choices = choicesFromInstance(itemset.instance_id)
          val instance = instances[itemset.instance_id]
          if (itemset.instance_id.isNotBlank() && (instance == null || instance.uri.isNotBlank())) {
            choiceDatasetId = itemset.instance_id
          }
        }
      }
      var type = typeOf(control, binding, appearance, label ?: name)
      if (type.hasChoices && choices.isEmpty() && choiceDatasetId == null) {
        when {
          control.type == ControlType.CONTROL_TRIGGER -> choices = listOf(EditorChoice("OK", "OK"))
          binding?.type == DataType.TYPE_BOOLEAN ->
            choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))
          else -> {
            notes +=
              "\"${label ?: name}\" picks from a list the editor can't read; imported as Text."
            type = EditorQuestionType.TEXT
          }
        }
      }
      val relevant = binding?.relevant_expression.orEmpty().trim()
      if (relevant.isNotEmpty()) relevanceByKey[key] = relevant
      val validation = validationOf(type, binding, label ?: name)
      questions +=
        EditorQuestion(
          key = key,
          name = name,
          type = type,
          label = label ?: name,
          hint = text(control.hint).orEmpty(),
          required = isTrue(binding?.required_expression),
          choices = if (type.hasChoices) choices else emptyList(),
          choiceDatasetId = if (type.hasChoices) choiceDatasetId else null,
          allowAddEntity =
            type.hasChoices &&
              choiceDatasetId != null &&
              EditorXFormsGenerator.ADD_ENTITY_APPEARANCE in appearance,
          validation = validation,
          capture =
            if (type.isGeometry && GeometryCapture.GPS_OR_MAP.appearance in appearance) {
              GeometryCapture.GPS_OR_MAP
            } else {
              GeometryCapture.GPS_ONLY
            },
          mediaSource =
            if (type.isMedia && MediaSource.CAPTURE_ONLY.appearance in appearance) {
              MediaSource.CAPTURE_ONLY
            } else {
              MediaSource.CAPTURE_OR_UPLOAD
            },
        )
      keyByPath[path] = key
    }

    private fun uniqueName(rawName: String, path: String): String {
      var name =
        if (FormEditorValidator.isValidName(rawName)) rawName else slugify(rawName, "question")
      if (name in usedNames) {
        val parent = path.substringBeforeLast('/', "").substringAfterLast('/')
        val prefixed = if (parent.isNotEmpty()) slugify("${parent}_$name") else name
        name = prefixed
        var n = 2
        while (name in usedNames) name = "${prefixed}_${n++}"
        notes += "Question \"$rawName\" was renamed to \"$name\" to keep names unique."
      }
      usedNames += name
      return name
    }

    private fun typeOf(
      control: ControlDef,
      binding: FieldBinding?,
      appearance: List<String>,
      label: String,
    ): EditorQuestionType {
      val dataType = binding?.type ?: DataType.DATA_TYPE_UNSPECIFIED
      return when (control.type) {
        ControlType.CONTROL_SELECT_ONE -> EditorQuestionType.SELECT_ONE
        ControlType.CONTROL_SELECT_MULTIPLE -> EditorQuestionType.SELECT_MULTIPLE
        ControlType.CONTROL_RANK -> {
          notes += "\"$label\" ranks its choices; imported as Select multiple."
          EditorQuestionType.SELECT_MULTIPLE
        }
        ControlType.CONTROL_UPLOAD -> {
          val media = control.media_type.lowercase()
          when {
            media.startsWith("video") -> EditorQuestionType.VIDEO
            media.startsWith("audio") -> EditorQuestionType.AUDIO
            media.startsWith("image") -> EditorQuestionType.PHOTO
            else -> {
              notes += "\"$label\" uploads \"${control.media_type}\"; imported as Photo."
              EditorQuestionType.PHOTO
            }
          }
        }
        ControlType.CONTROL_RANGE -> {
          notes += "\"$label\" is a range slider; imported as a number."
          if (dataType == DataType.TYPE_DOUBLE) EditorQuestionType.DECIMAL
          else EditorQuestionType.INTEGER
        }
        ControlType.CONTROL_TRIGGER -> {
          notes += "\"$label\" is an acknowledgement; imported as Select one."
          EditorQuestionType.SELECT_ONE
        }
        else ->
          when (dataType) {
            DataType.TYPE_INT32,
            DataType.TYPE_INT64 -> EditorQuestionType.INTEGER
            DataType.TYPE_DOUBLE -> EditorQuestionType.DECIMAL
            DataType.TYPE_DATE -> EditorQuestionType.DATE
            DataType.TYPE_TIME,
            DataType.TYPE_DATETIME -> {
              notes += "\"$label\" asks for a time; imported as Date."
              EditorQuestionType.DATE
            }
            DataType.TYPE_GEOPOINT -> EditorQuestionType.LOCATION
            DataType.TYPE_GEOTRACE -> EditorQuestionType.LINE
            DataType.TYPE_GEOSHAPE -> EditorQuestionType.POLYGON
            DataType.TYPE_BINARY -> EditorQuestionType.PHOTO
            DataType.TYPE_BOOLEAN -> {
              notes += "\"$label\" is a yes / no value; imported as Select one."
              EditorQuestionType.SELECT_ONE
            }
            DataType.TYPE_SELECT_ONE -> EditorQuestionType.SELECT_ONE
            DataType.TYPE_SELECT_MULTIPLE -> EditorQuestionType.SELECT_MULTIPLE
            else ->
              when {
                binding?.read_only == true -> EditorQuestionType.NOTE
                EditorQuestionType.LONG_TEXT.appearance in appearance ->
                  EditorQuestionType.LONG_TEXT
                else -> EditorQuestionType.TEXT
              }
          }
      }
    }

    /** Imports a numeric range constraint; other constraints are reported in the notes. */
    private fun validationOf(
      type: EditorQuestionType,
      binding: FieldBinding?,
      label: String,
    ): EditorValidation? {
      val constraint = binding?.constraint_expression?.trim().orEmpty()
      if (binding == null || constraint.isEmpty()) return null
      if (type.isNumeric) {
        var min = ""
        var max = ""
        var recognized = true
        constraint.split(" and ").forEach { part ->
          val m = BOUND.matchEntire(part.trim())
          when {
            m == null -> recognized = false
            m.groupValues[1] == ">=" -> min = m.groupValues[2]
            else -> max = m.groupValues[2]
          }
        }
        if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
          return EditorValidation(min = min, max = max, message = binding.constraint_message)
        }
      }
      notes += "Constraint \"$constraint\" on \"$label\" isn't supported by the editor."
      return null
    }

    private fun relevanceOf(question: EditorQuestion): EditorRelevance? {
      val expression = relevanceByKey[question.key] ?: return null
      val index = questions.indexOfFirst { it.key == question.key }
      fun source(path: String): EditorQuestion? {
        val key = keyByPath[path] ?: return null
        val sourceIndex = questions.indexOfFirst { it.key == key }
        return if (sourceIndex in 0 until index) questions[sourceIndex] else null
      }
      val relevance =
        COMPARISON.matchEntire(expression)?.let { m ->
          val src = source(m.groupValues[1]) ?: return@let null
          val operator =
            when (m.groupValues[2]) {
              "=" -> RelevanceOperator.EQUALS
              "!=" -> RelevanceOperator.NOT_EQUALS
              ">" -> RelevanceOperator.GREATER_THAN
              else -> RelevanceOperator.LESS_THAN
            }
          EditorRelevance(src.key, operator, unquote(m.groupValues[3]))
        }
          ?: SELECTED.matchEntire(expression)?.let { m ->
            source(m.groupValues[1])?.let {
              EditorRelevance(it.key, RelevanceOperator.INCLUDES, unquote(m.groupValues[2]))
            }
          }
          ?: ANSWERED.matchEntire(expression)?.let { m ->
            source(m.groupValues[1])?.let { EditorRelevance(it.key, RelevanceOperator.IS_ANSWERED) }
          }
      if (relevance == null) {
        notes +=
          "Display logic \"$expression\" on \"${question.label}\" isn't supported by the editor."
      }
      return relevance
    }

    private fun choicesFromInstance(instanceId: String): List<EditorChoice> {
      val data = instances[instanceId]?.inline_data.orEmpty()
      if (data.isBlank()) return emptyList()
      return ITEM.findAll(data)
        .mapNotNull { item ->
          val body = item.groupValues[1]
          val value = element(body, "name") ?: return@mapNotNull null
          val label =
            element(body, "label") ?: element(body, "itextId")?.let { strings[it] } ?: value
          EditorChoice(
            value = value,
            label = label,
            colorHex = element(body, EditorXFormsGenerator.CHOICE_COLOR_COLUMN),
          )
        }
        .toList()
    }

    private fun element(xml: String, name: String): String? =
      Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
        .find(xml)
        ?.groupValues
        ?.get(1)
        ?.let(::unescape)
        ?.trim()

    private fun text(label: LabelDef?): String? {
      if (label == null) return null
      val direct = label.text.trim()
      if (direct.isNotEmpty()) return direct
      return label.text_id.takeIf { it.isNotEmpty() }?.let { strings[it] }?.trim()?.ifEmpty { null }
    }

    private fun isTrue(expression: String?): Boolean =
      when (expression?.trim()?.lowercase()) {
        "true()",
        "true",
        "1" -> true
        else -> false
      }

    private fun unquote(literal: String): String {
      val v = literal.trim()
      return if (
        v.length >= 2 && (v.first() == '\'' || v.first() == '"') && v.last() == v.first()
      ) {
        v.substring(1, v.length - 1)
      } else {
        v
      }
    }

    private fun unescape(text: String): String =
      text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private companion object {
      val REF = Regex("""^/data/([A-Za-z_][A-Za-z0-9_.\-/]*)$""")
      val COMPARISON = Regex("""^/data/([A-Za-z_][A-Za-z0-9_.\-/]*) (=|!=|>|<) (.+)$""")
      val SELECTED = Regex("""^selected\(/data/([A-Za-z_][A-Za-z0-9_.\-/]*), (.+)\)$""")
      val ANSWERED = Regex("""^string-length\(/data/([A-Za-z_][A-Za-z0-9_.\-/]*)\) > 0$""")
      val BOUND = Regex("""^\. (>=|<=) (-?\d+(?:\.\d+)?)$""")
      val ITEM = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
      val ID_BY_QUESTION =
        Regex(
          """^instance\('([^']+)'\)/root/item\[([A-Za-z_][A-Za-z0-9_.\-]*) = /data/([A-Za-z_][A-Za-z0-9_.\-/]*)\]/name$"""
        )
    }
  }
}
