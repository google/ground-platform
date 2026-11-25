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

import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.COMPARISON
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.JR_ITEXT
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.OR_OPERATOR
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.REF
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.SELECTED_CALL
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.isTrue
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.stripOuterParens
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.unquote
import org.openforis.collectone.v2.core.forms.media.ChoiceImages
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

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
  /**
   * Imports [xml] as an ODK XForms definition or a Collect Earth / Open Foris IDM schema. Returns
   * `null` when the XML cannot be parsed into any questions.
   */
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
        null
      }
    if (
      formDef != null &&
        (!formDef.view?.components.isNullOrEmpty() ||
          !formDef.model?.bindings.isNullOrEmpty() ||
          formDef.title.isNotBlank())
    ) {
      val resolvedFormId = formId.ifBlank { formDef.form_id.takeIf { it != "data" }.orEmpty() }
      val imported = fromFormDef(formDef, resolvedFormId, fallbackTitle, availability)
      if (
        imported.form.questions.isNotEmpty() ||
          imported.form.deletedQuestions.isNotEmpty() ||
          formDef.title.isNotBlank()
      ) {
        return imported
      }
    }
    return CollectEarthIdmParser.fromXml(
      xml = xml,
      formId = formId,
      fallbackTitle = fallbackTitle,
      availability = availability,
    )
  }

  /** Imports [formDefinition] as a Form with ID [formId] (defaults to the form's ID). */
  fun fromFormDefinition(
    formDefinition: FormDefinition,
    formId: String = formDefinition.id,
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.MOBILE,
  ): ImportedForm? {
    val formDef = formDefinition.proto
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

  /**
   * One import of [formDef]. It accumulates questions, sections, and path ↔ key maps while walking
   * the body, and resolves display logic only after the walk, when every question it may refer to
   * is known; so an instance is used for a single [run].
   */
  private class Importer(private val formDef: FormDef) {
    private val model = formDef.model
    private val rootName: String =
      model?.primary_instance?.record_schema?.name?.ifBlank { "data" } ?: "data"
    private val bindings: Map<String, FieldBinding> =
      model?.bindings.orEmpty().associateBy { rootRelative(it.field_path) }
    private val instances = model?.secondary_instances.orEmpty().associateBy { it.id }
    private val orderedLanguages =
      model?.translations?.languages.orEmpty().let { languages ->
        languages.filter { it.is_default } + languages.filterNot { it.is_default }
      }
    private val nonDefaultLanguages = orderedLanguages.drop(1)
    private val strings: Map<String, String> = buildMap {
      // Later languages don't override the default one.
      orderedLanguages.forEach { lang ->
        lang.strings.forEach { (id, text) -> if (id !in this) put(id, text.value_) }
      }
    }
    private val mediaByTextId: Map<String, MediaRef> = buildMap {
      orderedLanguages.forEach { lang ->
        lang.strings.forEach { (id, text) ->
          val media = text.media
          if (id !in this && media != null && media.image_uri.isNotBlank()) {
            put(id, media)
          }
        }
      }
    }
    private val notes = mutableListOf<String>()
    private val questions = mutableListOf<EditorQuestion>()
    private val sections = mutableListOf<EditorSection>()
    private val keyByPath = mutableMapOf<String, String>()
    private val pathByKey = mutableMapOf<String, String>()
    private val usedNames = mutableSetOf<String>()
    private val relevanceByKey = mutableMapOf<String, String>()
    private val relevanceContextByKey = mutableMapOf<String, String>()
    private val bindingByKey = mutableMapOf<String, FieldBinding>()

    fun run(formId: String, fallbackTitle: String, availability: FormAvailability): ImportedForm {
      walk(formDef.view?.components.orEmpty(), enclosing = "", enclosingRelevance = null)
      val resolved = questions.map { q ->
        val parentKey =
          if (q.parentQuestionKey.isNotBlank()) {
            questions
              .firstOrNull { it.key == q.parentQuestionKey || it.name == q.parentQuestionKey }
              ?.key ?: q.parentQuestionKey
          } else {
            ""
          }
        val binding = bindingByKey[q.key]
        val path = pathByKey[q.key].orEmpty()
        q.copy(
          relevance = relevanceOf(q),
          requiredCondition = requiredConditionOf(q, binding),
          validation = validationOf(q, binding, path),
          parentQuestionKey = parentKey,
        )
      }
      val entity = model?.entities?.firstOrNull()
      val isUpdate = entity != null && entity.update_condition.isNotBlank()
      val status = importStatus()
      var idMatchColumn: String? = null
      var idSource = EntityIdSource.SELECTED_FEATURE
      var idQuestionKey: String? = null
      if (isUpdate) {
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
              keyByPath[rootRelative(binding.field_path)]?.let {
                EditorFieldMapping(it, binding.entity_saveto)
              }
            }
        } else {
          emptyList()
        }
      val effectiveFormId = formId.ifBlank { FormIds.newFormId() }
      val resolvedSections = sections.map { sec ->
        sec.copy(relevance = relevanceOfSection(sec))
      }
      val deletedQuestions = importDeletedQuestions()
      val form =
        EditorForm(
          formId = effectiveFormId,
          title = formDef.title.ifBlank { fallbackTitle }.ifBlank { effectiveFormId },
          questions = resolved,
          saveTo =
            EditorSaveTo(
              mode = if (isUpdate) SaveToMode.UPDATE else SaveToMode.CREATE,
              targetDatasetId = if (isUpdate) entity.dataset.ifBlank { null } else null,
              idSource = idSource,
              idQuestionKey = idQuestionKey,
              mappings = mappings,
              status = status,
            ),
          availability = availability,
          sections = resolvedSections,
          deletedQuestions = deletedQuestions,
        )
      return ImportedForm(
        form = form,
        createDatasetId = if (!isUpdate) entity?.dataset?.ifBlank { null } else null,
        idMatchColumn = idMatchColumn,
        notes = notes.toList(),
      )
    }

    /**
     * Status marker rules read back from the status `calculate` binds the editor writes, or the
     * default rules (with a note) when the binds are in another shape.
     */
    private fun importStatus(): EditorStatusConfig {
      fun calculate(field: String) =
        (bindings[field] ?: bindings["/$rootName/$field"])?.calculate_expression
      val label = calculate(SaveToRules.STATUS_FIELD) ?: return EditorStatusConfig()
      val imported =
        StatusRulesImport.parse(
          label = label,
          symbol = calculate(SaveToRules.MARKER_SYMBOL_FIELD).orEmpty(),
          color = calculate(SaveToRules.MARKER_COLOR_FIELD).orEmpty(),
          questionKey = { name -> questions.firstOrNull { it.name == name }?.key },
        )
      if (imported == null) notes += "Status marker rules were reset to the default rules."
      return imported ?: EditorStatusConfig(enabled = true)
    }

    private data class RelevanceContext(val expression: String, val contextPath: String)

    private fun walk(
      components: List<ViewComponent>,
      enclosing: String,
      enclosingRelevance: RelevanceContext?,
      currentSectionId: String = "",
    ) {
      components.forEach { component ->
        component.control?.let {
          importControl(it, enclosing, enclosingRelevance, currentSectionId)
        }
        component.group?.let { group ->
          val groupPath = join(enclosing, group.field_ref)
          val groupRel = bindings[groupPath]?.relevant_expression?.trim().orEmpty()
          val rawSectionId = group.field_ref.trim().removePrefix("/").substringAfterLast('/')
          val sectionId = uniqueSectionId(rawSectionId, fallbackPrefix = "section")
          val sectionTitle = text(group.label) ?: rawSectionId.ifEmpty { sectionId }
          val sec =
            EditorSection(
              id = sectionId,
              title = sectionTitle,
              repeatable = false,
              relevance = null,
            )
          sections += sec
          if (groupRel.isNotEmpty() && !isTrue(groupRel)) {
            relevanceByKey[sectionId] = groupRel
            relevanceContextByKey[sectionId] = groupPath
          }
          val nextRel =
            if (groupRel.isNotEmpty() && !isTrue(groupRel)) {
              RelevanceContext(groupRel, groupPath)
            } else {
              enclosingRelevance
            }
          walk(group.components, groupPath, nextRel, currentSectionId = sectionId)
        }
        component.repeat?.let { repeat ->
          val repeatPath = join(enclosing, repeat.field_ref)
          val repeatRel = bindings[repeatPath]?.relevant_expression?.trim().orEmpty()
          val rawSectionId = repeat.field_ref.trim().removePrefix("/").substringAfterLast('/')
          val sectionId = uniqueSectionId(rawSectionId, fallbackPrefix = "repeat")
          val sectionTitle = text(repeat.label) ?: rawSectionId.ifEmpty { sectionId }
          val sec =
            EditorSection(
              id = sectionId,
              title = sectionTitle,
              repeatable = true,
              repeatCountExpression = repeat.count_expression,
              relevance = null,
            )
          sections += sec
          if (repeatRel.isNotEmpty() && !isTrue(repeatRel)) {
            relevanceByKey[sectionId] = repeatRel
            relevanceContextByKey[sectionId] = repeatPath
          }
          val nextRel =
            if (repeatRel.isNotEmpty() && !isTrue(repeatRel)) {
              RelevanceContext(repeatRel, repeatPath)
            } else {
              enclosingRelevance
            }
          walk(repeat.components, repeatPath, nextRel, currentSectionId = sectionId)
        }
      }
    }

    private fun join(enclosing: String, ref: String): String {
      if (ref.trim().startsWith("/")) return rootRelative(ref)
      val clean = ref.trim()
      return when {
        clean.isEmpty() -> enclosing
        enclosing.isEmpty() || clean.startsWith("$enclosing/") -> clean
        else -> "$enclosing/$clean"
      }
    }

    /**
     * [path] relative to the primary instance root, so `/data/plot/tree` and `plot/tree` both
     * become `plot/tree`. Forms saved by earlier editor versions wrote the paths of questions in
     * sections absolutely, and XForms allows either form.
     */
    private fun rootRelative(path: String): String {
      val trimmed = path.trim()
      if (!trimmed.startsWith("/")) return trimmed
      val withoutSlash = trimmed.removePrefix("/")
      val root =
        listOf(rootName, EditorForm.INSTANCE_ROOT).firstOrNull {
          withoutSlash == it || withoutSlash.startsWith("$it/")
        }
      return if (root == null) withoutSlash else withoutSlash.removePrefix(root).removePrefix("/")
    }

    private fun importControl(
      control: ControlDef,
      enclosing: String,
      enclosingRelevance: RelevanceContext?,
      currentSectionId: String = "",
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
          val parentVal =
            choice.parent_value.ifEmpty {
              choice.properties["parent_value"] ?: choice.properties["parent"] ?: ""
            }
          EditorChoice(
            value = choice.value_,
            label = text(choice.label) ?: choice.value_,
            colorHex = choice.properties[EditorXFormsGenerator.CHOICE_COLOR_COLUMN],
            code =
              choice.properties[EditorXFormsGenerator.CHOICE_CODE_COLUMN]
                ?: choice.properties[EditorXFormsGenerator.LEGACY_CHOICE_CODE_COLUMN],
            labelTranslations = translations(choice.label),
            parentValue = parentVal,
          )
        }
      var choiceDatasetId: String? = null
      var inferredParentRef = ""
      if (choices.isEmpty()) {
        control.itemset?.let { itemset ->
          val filterInfo = parseItemsetParentFilter(itemset.nodeset_filter, path)
          inferredParentRef = filterInfo?.second.orEmpty()
          choices = choicesFromInstance(itemset, filterInfo?.first)
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
        enclosingRelevance != null && currentSectionId.isEmpty() -> {
          relevanceByKey[key] = enclosingRelevance.expression
          relevanceContextByKey[key] = enclosingRelevance.contextPath
        }
      }
      if (binding != null) {
        bindingByKey[key] = binding
      }
      val decimalPlaces =
        if (type == EditorQuestionType.NUMBER && binding?.type == DataType.TYPE_DOUBLE) {
          EditorQuestion.DEFAULT_DECIMAL_PLACES
        } else {
          0
        }
      // `collectone:concept` becomes the question's concept link (checked against the survey's
      // library
      // later); other foreign attributes are kept as they are.
      val (conceptLink, foreignAttributes) = ConceptLinkAttributes.fromBinding(binding)
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
          decimalPlaces = decimalPlaces,
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
          conceptLink = conceptLink,
          foreignAttributes = foreignAttributes,
          labelTranslations = translations(control.label),
          hintTranslations = translations(control.hint),
          parentQuestionKey = control.parent_question_key.ifBlank { inferredParentRef },
          sectionId = currentSectionId,
        )
      keyByPath[path] = key
      pathByKey[key] = path
    }

    private fun importDeletedQuestions(): List<EditorQuestion> {
      var nextIndex = questions.size + 1
      return model?.bindings.orEmpty().mapNotNull { binding ->
        val path = rootRelative(binding.field_path)
        if (path.isEmpty() || path in keyByPath || !ConceptLinkAttributes.isDeleted(binding)) {
          return@mapNotNull null
        }
        val rawName = path.substringAfterLast('/')
        val parentSectionId =
          path
            .substringBeforeLast('/', "")
            .substringAfterLast('/')
            .takeIf { secId -> sections.any { it.id == secId } }
            .orEmpty()
        val name =
          if (FormEditorValidator.isValidName(rawName) && rawName !in usedNames) {
            usedNames += rawName
            rawName
          } else {
            uniqueName(rawName, path)
          }
        val key = "q${nextIndex++}"
        val type = deletedTypeOf(binding)
        val decimalPlaces =
          if (type == EditorQuestionType.NUMBER && binding.type == DataType.TYPE_DOUBLE) {
            EditorQuestion.DEFAULT_DECIMAL_PLACES
          } else {
            0
          }
        val (conceptLink, foreignAttributes) = ConceptLinkAttributes.fromBinding(binding)
        val label = ConceptLinkAttributes.deletedLabel(binding) ?: name
        EditorQuestion(
          key = key,
          name = name,
          type = type,
          label = label,
          decimalPlaces = decimalPlaces,
          conceptLink = conceptLink,
          foreignAttributes = foreignAttributes,
          sectionId = parentSectionId,
          isDeleted = true,
        )
      }
    }

    private fun deletedTypeOf(binding: FieldBinding): EditorQuestionType =
      when (binding.type) {
        DataType.TYPE_INT32,
        DataType.TYPE_INT64,
        DataType.TYPE_DOUBLE -> EditorQuestionType.NUMBER
        DataType.TYPE_DATE,
        DataType.TYPE_TIME,
        DataType.TYPE_DATETIME -> EditorQuestionType.DATE
        DataType.TYPE_GEOPOINT -> EditorQuestionType.LOCATION
        DataType.TYPE_GEOTRACE -> EditorQuestionType.LINE
        DataType.TYPE_GEOSHAPE -> EditorQuestionType.POLYGON
        DataType.TYPE_BINARY -> EditorQuestionType.PHOTO
        DataType.TYPE_SELECT_ONE,
        DataType.TYPE_BOOLEAN -> EditorQuestionType.SELECT_ONE
        DataType.TYPE_SELECT_MULTIPLE -> EditorQuestionType.SELECT_MULTIPLE
        else -> if (binding.read_only) EditorQuestionType.NOTE else EditorQuestionType.TEXT
      }

    private fun uniqueSectionId(rawId: String, fallbackPrefix: String): String {
      val fallback = "${fallbackPrefix}_${sections.size + 1}"
      val base =
        if (rawId.isNotEmpty() && FormEditorValidator.isValidName(rawId)) rawId
        else if (rawId.isNotEmpty()) slugify(rawId, fallback) else fallback
      var id = base
      var n = 2
      while (id in usedNames) id = "${base}_${n++}"
      usedNames += id
      return id
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
          EditorQuestionType.NUMBER
        }
        ControlType.CONTROL_TRIGGER -> {
          notes += "\"$label\" is an acknowledgement; imported as Select one."
          EditorQuestionType.SELECT_ONE
        }
        else ->
          when (dataType) {
            DataType.TYPE_INT32,
            DataType.TYPE_INT64,
            DataType.TYPE_DOUBLE -> EditorQuestionType.NUMBER
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
     * Imports structured constraints (numeric range, text length/pattern, date, selection count,
     * cross-field comparison, distance, conditional wrapper) or custom XPath constraints;
     * unparseable constraints are reported in [notes].
     */
    private fun validationOf(
      question: EditorQuestion,
      binding: FieldBinding?,
      path: String,
    ): EditorValidation? {
      val constraint = stripOuterParens(binding?.constraint_expression.orEmpty())
      if (binding == null || constraint.isEmpty()) return null
      val maxIndex = questions.indexOfFirst { it.key == question.key }
      fun resolveAny(rawRef: String): EditorQuestion? {
        val resolvedPath = resolvePath(rawRef, path) ?: return null
        val targetKey = keyByPath[resolvedPath] ?: return null
        return questions.firstOrNull { it.key == targetKey && it.key != question.key }
      }
      fun resolveEarlier(rawRef: String): EditorQuestion? {
        val resolvedPath = resolvePath(rawRef, path) ?: return null
        val srcKey = keyByPath[resolvedPath] ?: return null
        val sourceIndex = questions.indexOfFirst { it.key == srcKey }
        return if (sourceIndex in 0 until maxIndex) questions[sourceIndex] else null
      }
      val validation =
        ConstraintImport.parse(
          type = question.type,
          constraint = constraint,
          message = constraintMessage(binding, path),
          resolveQuestion = ::resolveAny,
          resolveEarlierQuestion = ::resolveEarlier,
        )
      if (validation == null) {
        notes +=
          "Constraint \"$constraint\" on \"${question.label}\" isn't supported by the editor."
      }
      return validation
    }

    private fun requiredConditionOf(
      question: EditorQuestion,
      binding: FieldBinding?,
    ): EditorRelevance? {
      val raw = binding?.required_expression?.trim().orEmpty()
      if (raw.isEmpty() || isTrue(raw) || raw.lowercase() in setOf("false()", "false")) {
        return null
      }
      val contextPath = pathByKey[question.key].orEmpty()
      val maxIndex = questions.indexOfFirst { it.key == question.key }
      fun source(rawRef: String): EditorQuestion? {
        val resolvedPath = resolvePath(rawRef, contextPath) ?: return null
        val srcKey = keyByPath[resolvedPath] ?: return null
        val sourceIndex = questions.indexOfFirst { it.key == srcKey }
        return if (sourceIndex in 0 until maxIndex) questions[sourceIndex] else null
      }
      val cond = RelevanceImport.parse(stripOuterParens(raw), ::source)
      if (cond == null) {
        notes +=
          "Required expression \"$raw\" on \"${question.label}\" isn't supported by the editor."
      }
      return cond
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

    private fun parseRelevance(
      key: String,
      maxIndex: Int,
      labelForNote: String,
    ): EditorRelevance? {
      val rawExpression = relevanceByKey[key] ?: return null
      val contextPath = relevanceContextByKey[key] ?: pathByKey[key].orEmpty()
      // Display logic may only depend on questions asked before ([maxIndex]).
      fun source(rawRef: String): EditorQuestion? {
        val resolvedPath = resolvePath(rawRef, contextPath) ?: return null
        val srcKey = keyByPath[resolvedPath] ?: return null
        val sourceIndex = questions.indexOfFirst { it.key == srcKey }
        return if (sourceIndex in 0 until maxIndex) questions[sourceIndex] else null
      }
      val relevance = RelevanceImport.parse(stripOuterParens(rawExpression), ::source)
      if (relevance == null) {
        notes +=
          "Display logic \"$rawExpression\" on \"$labelForNote\" isn't supported by the editor."
      }
      return relevance
    }

    private fun relevanceOf(question: EditorQuestion): EditorRelevance? {
      val index = questions.indexOfFirst { it.key == question.key }
      return parseRelevance(question.key, index, question.label)
    }

    private fun relevanceOfSection(section: EditorSection): EditorRelevance? {
      val firstQuestionIndex = questions.indexOfFirst { it.sectionId == section.id }
      val maxIndex = if (firstQuestionIndex >= 0) firstQuestionIndex else questions.size
      return parseRelevance(section.id, maxIndex, section.title)
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

    /**
     * The `(choice column, parent question key)` of a cascading select's itemset predicate (XLSForm
     * `choice_filter`), or `null` when [filter] isn't one. Recognizes, in any of its `or` clauses,
     * `<column> = <parent ref>` (either way round) and `selected(<parent ref>, <column>)`
     * (select-multiple parents, and what the editor writes). Other clauses, such as the editor's
     * `string-length(parent_value) = 0` (formerly `parent_value = ''`) for choices shown under
     * every parent value, are skipped.
     */
    private fun parseItemsetParentFilter(
      filter: String,
      contextPath: String,
    ): Pair<String, String>? =
      stripOuterParens(filter)
        .takeIf { it.isNotEmpty() }
        ?.split(OR_OPERATOR)
        ?.firstNotNullOfOrNull { rawClause ->
          val clause = stripOuterParens(rawClause)
          val selected = SELECTED_CALL.matchEntire(clause)
          val comparison = COMPARISON.matchEntire(clause)?.takeIf { it.groupValues[2] == "=" }
          when {
            selected != null ->
              cascadeColumnAndParent(selected.groupValues[2], selected.groupValues[1], contextPath)
            comparison != null -> {
              val lhs = comparison.groupValues[1].trim()
              val rhs = comparison.groupValues[3].trim()
              cascadeColumnAndParent(lhs, rhs, contextPath)
                ?: cascadeColumnAndParent(rhs, lhs, contextPath)
            }
            else -> null
          }
        }

    /**
     * `(column, parent question key)` when [column] is a bare choice column name and [parentRef] a
     * field reference (not a literal); the key falls back to the reference's last step when it
     * doesn't resolve to an imported question.
     */
    private fun cascadeColumnAndParent(
      column: String,
      parentRef: String,
      contextPath: String,
    ): Pair<String, String>? {
      val ref = parentRef.trim()
      val isFieldRef =
        ref.isNotEmpty() &&
          ref.first() !in "'\"" &&
          ref.toDoubleOrNull() == null &&
          ref.none(Char::isWhitespace)
      if (!isFieldRef || !REF.matches(column) || column.contains('/')) return null
      val parentKey =
        resolvePath(ref, contextPath)?.let { keyByPath[it] }
          ?: ref.removePrefix("\${").removeSuffix("}").substringAfterLast('/')
      return parentKey.takeIf { it.isNotBlank() }?.let { column to it }
    }

    private fun choicesFromInstance(
      itemset: ItemsetDef,
      parentColumn: String? = null,
    ): List<EditorChoice> {
      val rows = instances[itemset.instance_id]?.rows.orEmpty()
      if (rows.isEmpty()) return emptyList()
      val valueTag = itemset.value_ref.trim().ifEmpty { "name" }
      val rawLabelRef = itemset.label_ref.trim().ifEmpty { "label" }
      val itextColumnMatch = JR_ITEXT.matchEntire(rawLabelRef)
      val itextColumn = itextColumnMatch?.let { unquote(it.groupValues[1]) }
      val labelTag = itextColumn ?: rawLabelRef
      return rows
        .mapNotNull { row ->
          val value = rowField(row, valueTag) ?: rowField(row, "name") ?: return@mapNotNull null
          val itextId =
            if (itextColumn != null) {
              rowField(row, itextColumn)
            } else {
              rowField(row, "itextId")
            }
          val label =
            if (itextColumn != null) {
              itextId?.let { strings[it] ?: it } ?: rowField(row, "label") ?: value
            } else {
              rowField(row, labelTag)
                ?: rowField(row, "label")
                ?: itextId?.let { strings[it] }
                ?: value
            }
          val imageUri = itextId?.let { mediaByTextId[it]?.image_uri }?.takeIf { it.isNotBlank() }
          val inlineImageUri = rowField(row, EditorXFormsGenerator.CHOICE_IMAGE_COLUMN)
          val choiceImage =
            (ChoiceImages.lookup(inlineImageUri) ?: ChoiceImages.lookup(imageUri))?.let { media ->
              EditorChoiceImage(mimeType = media.mimeType, base64 = media.base64)
            }
          val parentVal =
            (parentColumn?.let { rowField(row, it) })
              ?: rowField(row, "parent_value")
              ?: rowField(row, "parent")
              ?: ""
          EditorChoice(
            value = value,
            label = label,
            colorHex = rowField(row, EditorXFormsGenerator.CHOICE_COLOR_COLUMN),
            image = choiceImage,
            code =
              rowField(row, EditorXFormsGenerator.CHOICE_CODE_COLUMN)
                ?: rowField(row, EditorXFormsGenerator.LEGACY_CHOICE_CODE_COLUMN),
            labelTranslations = translationsForTextId(itextId),
            parentValue = parentVal,
          )
        }
        .toList()
    }

    private fun rowField(row: RecordNode, name: String): String? =
      row.fields[name]?.scalar_value?.string_value?.trim()?.takeIf { it.isNotEmpty() }

    private fun text(label: LabelDef?): String? {
      if (label == null) return null
      val direct = label.text.trim()
      if (direct.isNotEmpty()) return direct
      return label.text_id.takeIf { it.isNotEmpty() }?.let { strings[it] }?.trim()?.ifEmpty { null }
    }

    private fun translations(label: LabelDef?): Map<String, String> =
      translationsForTextId(label?.text_id)

    private fun translationsForTextId(textId: String?): Map<String, String> {
      val id = textId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
      if (nonDefaultLanguages.isEmpty()) return emptyMap()
      return buildMap {
        nonDefaultLanguages.forEach { lang ->
          val translated = lang.strings[id]?.value_?.trim()?.takeIf { it.isNotEmpty() }
          if (translated != null) {
            val tag = normalizeLanguageTag(lang.language)
            if (tag.isNotEmpty() && tag !in this) {
              put(tag, translated)
            }
          }
        }
      }
    }

    private fun normalizeLanguageTag(raw: String): String {
      val trimmed = raw.trim()
      val match = PARENTHESIZED_LANG_CODE.matchEntire(trimmed)
      return match?.groupValues?.get(1) ?: trimmed
    }

    private companion object {
      val PARENTHESIZED_LANG_CODE = Regex("""^.*\(([A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*)\)$""")
      val ID_BY_QUESTION =
        Regex(
          """^instance\('([^']+)'\)/root/item\[([A-Za-z_][A-Za-z0-9_.\-]*)\s*=\s*([^\]]+)\]/name$"""
        )
    }
  }
}
