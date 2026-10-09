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
import groundplatform.v2.forms.ItemsetDef
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
    formId: String = "",
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.MOBILE,
  ): ImportedForm? {
    val formDef =
      try {
        XFormsXmlSerializer.deserializeFormDef(xml)
      } catch (e: Exception) {
        return null
      }
    if (
      formDef.view?.components.isNullOrEmpty() &&
        formDef.model?.bindings.isNullOrEmpty() &&
        formDef.title.isBlank()
    ) {
      return null
    }
    val resolvedFormId = formId.ifBlank { formDef.form_id.takeIf { it != "data" }.orEmpty() }
    return fromFormDef(formDef, resolvedFormId, fallbackTitle, availability)
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
    private val rootName: String =
      model?.primary_instance?.record_schema?.name?.ifBlank { "data" } ?: "data"
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
    private val pathByKey = mutableMapOf<String, String>()
    private val usedNames = mutableSetOf<String>()
    private val relevanceByKey = mutableMapOf<String, String>()
    private val relevanceContextByKey = mutableMapOf<String, String>()

    fun run(formId: String, fallbackTitle: String, availability: FormAvailability): ImportedForm {
      walk(formDef.view?.components.orEmpty(), enclosing = "", enclosingRelevance = null)
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
        val byQuestionPath = byQuestion?.let { resolvePath(it.groupValues[3], "") }
        val directPath = direct?.let { resolvePath(it.groupValues[1], "") }
        when {
          idExpr.contains(SaveToRules.TARGET_ENTITY_FIELD) -> Unit
          byQuestion != null && byQuestionPath != null && keyByPath.containsKey(byQuestionPath) -> {
            idSource = EntityIdSource.QUESTION
            idQuestionKey = keyByPath[byQuestionPath]
            idMatchColumn = byQuestion.groupValues[2]
          }
          directPath != null && keyByPath.containsKey(directPath) -> {
            idSource = EntityIdSource.QUESTION
            idQuestionKey = keyByPath[directPath]
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
      val effectiveFormId = formId.ifBlank { FormIds.newFormId() }
      val form =
        EditorForm(
          formId = effectiveFormId,
          title = formDef.title.ifBlank { fallbackTitle }.ifBlank { effectiveFormId },
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

    private data class RelevanceContext(val expression: String, val contextPath: String)

    private fun walk(
      components: List<ViewComponent>,
      enclosing: String,
      enclosingRelevance: RelevanceContext?,
    ) {
      components.forEach { component ->
        component.control?.let { importControl(it, enclosing, enclosingRelevance) }
        component.group?.let { group ->
          val groupPath = join(enclosing, group.field_ref)
          val groupRel = bindings[groupPath]?.relevant_expression?.trim().orEmpty()
          val nextRel =
            if (groupRel.isNotEmpty() && !isTrue(groupRel)) {
              RelevanceContext(groupRel, groupPath)
            } else {
              enclosingRelevance
            }
          walk(group.components, groupPath, nextRel)
        }
        component.repeat?.let { repeat ->
          notes +=
            "Repeat \"${text(repeat.label) ?: repeat.field_ref}\" was imported as plain questions."
          val repeatPath = join(enclosing, repeat.field_ref)
          val repeatRel = bindings[repeatPath]?.relevant_expression?.trim().orEmpty()
          val nextRel =
            if (repeatRel.isNotEmpty() && !isTrue(repeatRel)) {
              RelevanceContext(repeatRel, repeatPath)
            } else {
              enclosingRelevance
            }
          walk(repeat.components, repeatPath, nextRel)
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

    private fun importControl(
      control: ControlDef,
      enclosing: String,
      enclosingRelevance: RelevanceContext?,
    ) {
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
          choices = choicesFromInstance(itemset)
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
      val ownRelevant = binding?.relevant_expression.orEmpty().trim()
      when {
        ownRelevant.isNotEmpty() && !isTrue(ownRelevant) -> {
          relevanceByKey[key] = ownRelevant
          relevanceContextByKey[key] = path
        }
        enclosingRelevance != null -> {
          relevanceByKey[key] = enclosingRelevance.expression
          relevanceContextByKey[key] = enclosingRelevance.contextPath
        }
      }
      val validation = validationOf(type, binding, path, label ?: name)
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
      pathByKey[key] = path
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

    /**
     * Imports numeric range, text length/pattern, date, or selection count constraints; unsupported
     * constraints are reported in [notes].
     */
    private fun validationOf(
      type: EditorQuestionType,
      binding: FieldBinding?,
      path: String,
      label: String,
    ): EditorValidation? {
      val constraint = stripOuterParens(binding?.constraint_expression.orEmpty())
      if (binding == null || constraint.isEmpty()) return null
      val message = constraintMessage(binding, path)
      val clauses =
        constraint.split(AND_SPLIT).map { stripOuterParens(it) }.filter { it.isNotEmpty() }
      when (ValidationKind.of(type)) {
        ValidationKind.NUMBER_RANGE -> {
          var min = ""
          var max = ""
          var recognized = true
          clauses.forEach { part ->
            val m = BOUND.matchEntire(part)
            val rev = if (m == null) REVERSED_BOUND.matchEntire(part) else null
            when {
              m != null ->
                when (m.groupValues[1]) {
                  ">=",
                  ">" -> min = m.groupValues[2]
                  "<=",
                  "<" -> max = m.groupValues[2]
                  "=" -> {
                    min = m.groupValues[2]
                    max = m.groupValues[2]
                  }
                  else -> recognized = false
                }
              rev != null ->
                when (rev.groupValues[2]) {
                  "<=",
                  "<" -> min = rev.groupValues[1]
                  ">=",
                  ">" -> max = rev.groupValues[1]
                  "=" -> {
                    min = rev.groupValues[1]
                    max = rev.groupValues[1]
                  }
                  else -> recognized = false
                }
              else -> recognized = false
            }
          }
          if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
            return EditorValidation(min = min, max = max, message = message)
          }
        }
        ValidationKind.TEXT -> {
          var min = ""
          var max = ""
          var pattern: TextPattern? = null
          var customPattern = ""
          var recognized = true
          clauses.forEach { part ->
            val lenMatch = STRING_LENGTH_BOUND.matchEntire(part)
            val regexMatch = if (lenMatch == null) REGEX_CALL.matchEntire(part) else null
            when {
              lenMatch != null -> {
                val op = lenMatch.groupValues[1]
                val n = lenMatch.groupValues[2].toIntOrNull()
                if (n == null) {
                  recognized = false
                } else {
                  when (op) {
                    ">=" -> min = n.toString()
                    ">" -> min = (n + 1).toString()
                    "<=" -> max = n.toString()
                    "<" -> if (n > 0) max = (n - 1).toString() else recognized = false
                    "=" -> {
                      min = n.toString()
                      max = n.toString()
                    }
                    else -> recognized = false
                  }
                }
              }
              regexMatch != null -> {
                val rawRegex = unquote(regexMatch.groupValues[1])
                val preset = TextPattern.entries.firstOrNull { it.regex == rawRegex }
                if (preset != null) {
                  pattern = preset
                  customPattern = ""
                } else {
                  pattern = TextPattern.CUSTOM
                  customPattern = rawRegex
                }
              }
              else -> recognized = false
            }
          }
          if (recognized && (min.isNotEmpty() || max.isNotEmpty() || pattern != null)) {
            return EditorValidation(
              min = min,
              max = max,
              pattern = pattern,
              customPattern = customPattern,
              message = message,
            )
          }
        }
        ValidationKind.DATE -> {
          if (clauses.size == 1 && DATE_TODAY.matches(clauses[0])) {
            val op = DATE_TODAY.matchEntire(clauses[0])!!.groupValues[1]
            val rule =
              when (op) {
                "<=",
                "<" -> DateRule.NOT_IN_FUTURE
                ">=",
                ">" -> DateRule.NOT_IN_PAST
                else -> null
              }
            if (rule != null) {
              return EditorValidation(dateRule = rule, message = message)
            }
          }
          var min = ""
          var max = ""
          var recognized = true
          clauses.forEach { part ->
            val m = DATE_BOUND.matchEntire(part)
            val iso = m?.let { unquote(it.groupValues[2]) }
            when {
              m == null || iso == null || !ValidationRules.isValidDate(iso) -> recognized = false
              m.groupValues[1] == ">=" || m.groupValues[1] == ">" -> min = iso
              m.groupValues[1] == "<=" || m.groupValues[1] == "<" -> max = iso
              m.groupValues[1] == "=" -> {
                min = iso
                max = iso
              }
              else -> recognized = false
            }
          }
          if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
            return EditorValidation(
              min = min,
              max = max,
              dateRule = DateRule.BETWEEN,
              message = message,
            )
          }
        }
        ValidationKind.SELECTION_COUNT -> {
          var min = ""
          var max = ""
          var recognized = true
          clauses.forEach { part ->
            val m = COUNT_SELECTED_BOUND.matchEntire(part)
            val n = m?.groupValues?.get(2)?.toIntOrNull()
            if (m == null || n == null) {
              recognized = false
            } else {
              when (m.groupValues[1]) {
                ">=" -> min = n.toString()
                ">" -> min = (n + 1).toString()
                "<=" -> max = n.toString()
                "<" -> if (n > 0) max = (n - 1).toString() else recognized = false
                "=" -> {
                  min = n.toString()
                  max = n.toString()
                }
                else -> recognized = false
              }
            }
          }
          if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
            return EditorValidation(min = min, max = max, message = message)
          }
        }
        null -> Unit
      }
      notes += "Constraint \"$constraint\" on \"$label\" isn't supported by the editor."
      return null
    }

    private fun constraintMessage(binding: FieldBinding, path: String): String {
      val raw = binding.constraint_message.trim()
      if (raw.isNotEmpty()) {
        val itextMatch = JR_ITEXT.matchEntire(raw)
        if (itextMatch != null) {
          val id = unquote(itextMatch.groupValues[1])
          return strings[id]?.trim() ?: raw
        }
        return raw
      }
      return strings["/$rootName/$path:jr:constraintMsg"]?.trim()
        ?: strings["/data/$path:jr:constraintMsg"]?.trim()
        ?: strings["$path:jr:constraintMsg"]?.trim()
        ?: ""
    }

    private fun relevanceOf(question: EditorQuestion): EditorRelevance? {
      val rawExpression = relevanceByKey[question.key] ?: return null
      val expression = stripOuterParens(rawExpression)
      val contextPath = relevanceContextByKey[question.key] ?: pathByKey[question.key].orEmpty()
      val index = questions.indexOfFirst { it.key == question.key }
      fun source(rawRef: String): EditorQuestion? {
        val resolvedPath = resolvePath(rawRef, contextPath) ?: return null
        val key = keyByPath[resolvedPath] ?: return null
        val sourceIndex = questions.indexOfFirst { it.key == key }
        return if (sourceIndex in 0 until index) questions[sourceIndex] else null
      }
      fun mapSelected(src: EditorQuestion, literal: String, negated: Boolean): EditorRelevance? {
        val value = unquote(literal)
        val available = RelevanceOperator.availableFor(src.type)
        if (value.isEmpty()) {
          val op = if (negated) RelevanceOperator.IS_ANSWERED else RelevanceOperator.EQUALS
          return if (op in available) EditorRelevance(src.key, op, "") else null
        }
        val op =
          if (negated) {
            if (RelevanceOperator.NOT_EQUALS in available) RelevanceOperator.NOT_EQUALS else null
          } else {
            when {
              src.type == EditorQuestionType.SELECT_MULTIPLE &&
                RelevanceOperator.INCLUDES in available -> RelevanceOperator.INCLUDES
              RelevanceOperator.EQUALS in available -> RelevanceOperator.EQUALS
              RelevanceOperator.INCLUDES in available -> RelevanceOperator.INCLUDES
              else -> null
            }
          }
        return op?.let { EditorRelevance(src.key, it, value) }
      }
      fun mapComparison(src: EditorQuestion, opToken: String, literal: String): EditorRelevance? {
        val value = unquote(literal)
        val available = RelevanceOperator.availableFor(src.type)
        if (value.isEmpty() && opToken == "!=") {
          return if (RelevanceOperator.IS_ANSWERED in available) {
            EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
          } else {
            null
          }
        }
        val operator =
          when (opToken) {
            "=" -> RelevanceOperator.EQUALS
            "!=" -> RelevanceOperator.NOT_EQUALS
            ">",
            ">=" -> RelevanceOperator.GREATER_THAN
            "<",
            "<=" -> RelevanceOperator.LESS_THAN
            else -> return null
          }
        return if (operator in available) EditorRelevance(src.key, operator, value) else null
      }
      val relevance =
        NOT_SELECTED.matchEntire(expression)?.let { m ->
          source(m.groupValues[1])?.let { mapSelected(it, m.groupValues[2], negated = true) }
        }
          ?: SELECTED.matchEntire(expression)?.let { m ->
            source(m.groupValues[1])?.let { mapSelected(it, m.groupValues[2], negated = false) }
          }
          ?: ANSWERED.matchEntire(expression)?.let { m ->
            source(m.groupValues[1])?.let { src ->
              if (RelevanceOperator.IS_ANSWERED in RelevanceOperator.availableFor(src.type)) {
                EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
              } else {
                null
              }
            }
          }
          ?: COUNT_SELECTED_ANSWERED.matchEntire(expression)?.let { m ->
            source(m.groupValues[1])?.let { src ->
              if (RelevanceOperator.IS_ANSWERED in RelevanceOperator.availableFor(src.type)) {
                EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
              } else {
                null
              }
            }
          }
          ?: COMPARISON.matchEntire(expression)?.let { m ->
            val lhs = m.groupValues[1].trim()
            val op = m.groupValues[2]
            val rhs = m.groupValues[3].trim()
            source(lhs)?.let { mapComparison(it, op, rhs) }
              ?: source(rhs)?.let { mapComparison(it, flipOperator(op), lhs) }
          }
          ?: source(expression)?.let { src ->
            if (RelevanceOperator.IS_ANSWERED in RelevanceOperator.availableFor(src.type)) {
              EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
            } else {
              null
            }
          }
      if (relevance == null) {
        notes +=
          "Display logic \"$rawExpression\" on \"${question.label}\" isn't supported by the editor."
      }
      return relevance
    }

    private fun resolvePath(rawRef: String, contextPath: String): String? {
      var ref = stripOuterParens(rawRef)
      if (ref.isEmpty()) return null
      if (ref.startsWith("'") || ref.startsWith("\"")) return null
      if (ref.startsWith("\${") && ref.endsWith("}")) {
        val varName = ref.substring(2, ref.length - 1).trim()
        return resolveByShortName(varName, contextPath)
      }
      if (ref.startsWith("current()/")) {
        ref = ref.removePrefix("current()/")
      } else if (ref == "current()") {
        ref = "."
      }
      val resolved =
        if (ref.startsWith("/")) {
          val trimmed = ref.removePrefix("/")
          when {
            trimmed.startsWith("$rootName/") -> trimmed.removePrefix("$rootName/")
            trimmed.startsWith("data/") -> trimmed.removePrefix("data/")
            else -> trimmed
          }
        } else if (ref.startsWith("$rootName/") && !keyByPath.containsKey(join(contextPath, ref))) {
          ref.removePrefix("$rootName/")
        } else if (ref.startsWith("data/") && !keyByPath.containsKey(join(contextPath, ref))) {
          ref.removePrefix("data/")
        } else {
          val baseSegments = contextPath.split('/').filter { it.isNotEmpty() }.toMutableList()
          val relSegments = ref.split('/').filter { it.isNotEmpty() }
          // In XForms `<bind nodeset="foo" relevant="../bar"/>`, `..` steps to the parent of `foo`.
          if (relSegments.firstOrNull() == ".." || relSegments.firstOrNull() == ".") {
            if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
          }
          for (seg in relSegments) {
            when (seg) {
              "." -> Unit
              ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
              else -> baseSegments.add(seg)
            }
          }
          baseSegments.joinToString("/")
        }
      if (keyByPath.containsKey(resolved)) return resolved
      return resolveByShortName(resolved, contextPath)
    }

    private fun resolveByShortName(name: String, contextPath: String): String? {
      if (keyByPath.containsKey(name)) return name
      val parent = contextPath.substringBeforeLast('/', "")
      if (parent.isNotEmpty()) {
        val sibling = "$parent/$name"
        if (keyByPath.containsKey(sibling)) return sibling
      }
      val suffixMatches = keyByPath.keys.filter { it.endsWith("/$name") }
      return suffixMatches.singleOrNull()
    }

    private fun stripOuterParens(expr: String): String {
      var s = expr.trim()
      while (s.length >= 2 && s.first() == '(' && s.last() == ')') {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var wrapsAll = true
        for (i in 0 until s.length - 1) {
          val c = s[i]
          when {
            c == '\'' && !inDouble -> inSingle = !inSingle
            c == '"' && !inSingle -> inDouble = !inDouble
            !inSingle && !inDouble -> {
              if (c == '(') depth++
              else if (c == ')') {
                depth--
                if (depth == 0) {
                  wrapsAll = false
                  break
                }
              }
            }
          }
        }
        if (wrapsAll && depth == 1) {
          s = s.substring(1, s.length - 1).trim()
        } else {
          break
        }
      }
      return s
    }

    private fun flipOperator(op: String): String =
      when (op) {
        ">" -> "<"
        ">=" -> "<="
        "<" -> ">"
        "<=" -> ">="
        else -> op
      }

    private fun choicesFromInstance(itemset: ItemsetDef): List<EditorChoice> {
      val data = instances[itemset.instance_id]?.inline_data.orEmpty()
      if (data.isBlank()) return emptyList()
      val valueTag = itemset.value_ref.trim().ifEmpty { "name" }
      val rawLabelRef = itemset.label_ref.trim().ifEmpty { "label" }
      val itextColumnMatch = JR_ITEXT.matchEntire(rawLabelRef)
      val itextColumn = itextColumnMatch?.let { unquote(it.groupValues[1]) }
      val labelTag = itextColumn ?: rawLabelRef
      val itemElements =
        ITEM.findAll(data)
          .map { it.groupValues[1] }
          .toList()
          .ifEmpty { customRecordsFromInstanceXml(data) }
      return itemElements
        .mapNotNull { body ->
          val value = element(body, valueTag) ?: element(body, "name") ?: return@mapNotNull null
          val label =
            if (itextColumn != null) {
              element(body, itextColumn)?.let { strings[it] ?: it }
                ?: element(body, "label")
                ?: value
            } else {
              element(body, labelTag)
                ?: element(body, "label")
                ?: element(body, "itextId")?.let { strings[it] }
                ?: value
            }
          EditorChoice(
            value = value,
            label = label,
            colorHex = element(body, EditorXFormsGenerator.CHOICE_COLOR_COLUMN),
          )
        }
        .toList()
    }

    private fun customRecordsFromInstanceXml(xml: String): List<String> {
      val rootMatch = OUTER_TAG.find(xml.trim()) ?: return emptyList()
      val inner = rootMatch.groupValues[2].trim()
      return CHILD_RECORD.findAll(inner).map { it.groupValues[2] }.toList()
    }

    private fun element(xml: String, name: String): String? =
      Regex("<${Regex.escape(name)}>(.*?)</${Regex.escape(name)}>", RegexOption.DOT_MATCHES_ALL)
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
      val REF = Regex("""^/?([A-Za-z_][A-Za-z0-9_.\-/]*)$""")
      val COMPARISON = Regex("""^(.+?)\s*(!=|>=|<=|=|>|<)\s*(.+)$""")
      val SELECTED = Regex("""^selected\(\s*([^,]+?)\s*,\s*(.+?)\s*\)$""")
      val NOT_SELECTED = Regex("""^not\(\s*selected\(\s*([^,]+?)\s*,\s*(.+?)\s*\)\s*\)$""")
      val ANSWERED = Regex("""^string-length\(\s*(.+?)\s*\)\s*(?:>|!=)\s*0$""")
      val COUNT_SELECTED_ANSWERED = Regex("""^count-selected\(\s*(.+?)\s*\)\s*(?:>|!=)\s*0$""")
      val AND_SPLIT = Regex("""\s+and\s+""")
      val BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*(-?(?:\d+(?:\.\d*)?|\.\d+))$""")
      val REVERSED_BOUND = Regex("""^(-?(?:\d+(?:\.\d*)?|\.\d+))\s*(>=|<=|>|<|=)\s*\.$""")
      val STRING_LENGTH_BOUND = Regex("""^string-length\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
      val REGEX_CALL = Regex("""^regex\(\s*\.\s*,\s*(.+?)\s*\)$""")
      val DATE_TODAY = Regex("""^\.\s*(<=|<|>=|>)\s*today\(\s*\)$""")
      val DATE_BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*date\(\s*(.+?)\s*\)$""")
      val COUNT_SELECTED_BOUND = Regex("""^count-selected\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
      val JR_ITEXT = Regex("""^jr:itext\(\s*(.+?)\s*\)$""")
      val ITEM = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
      val OUTER_TAG =
        Regex("""^<([A-Za-z_][A-Za-z0-9_.\-:]*)[^>]*>(.*)</\1>$""", RegexOption.DOT_MATCHES_ALL)
      val CHILD_RECORD =
        Regex("""<([A-Za-z_][A-Za-z0-9_.\-:]*)[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
      val ID_BY_QUESTION =
        Regex(
          """^instance\('([^']+)'\)/root/item\[([A-Za-z_][A-Za-z0-9_.\-]*)\s*=\s*([^\]]+)\]/name$"""
        )
    }
  }
}
