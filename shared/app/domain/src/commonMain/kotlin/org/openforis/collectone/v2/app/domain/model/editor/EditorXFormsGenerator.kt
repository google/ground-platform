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

import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.media.ChoiceImageMedia
import org.openforis.collectone.v2.core.forms.media.ChoiceImages
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

/** Generates ODK-compatible XForms XML from an [EditorForm]. */
object EditorXFormsGenerator {

  /**
   * Returns the XPath `relevant` expression for [relevance], or `null` if always shown.
   *
   * The source question must come before [targetQuestionIndex]. Display logic evaluated inside
   * section [contextSectionId] that refers to a question of the same repeated section refers to it
   * relatively (`../name`), so each entry tests the answer given in that entry: the bind's context
   * node is the node it binds (W3C XForms 1.0 section 7.2), and this is what pyxform writes for
   * `${name}` inside a repeat. Every other source is referred to by its absolute instance path.
   */
  fun relevantExpression(
    form: EditorForm,
    relevance: EditorRelevance?,
    targetQuestionIndex: Int = Int.MAX_VALUE,
    contextSectionId: String = "",
  ): String? {
    if (relevance == null) return null
    val sourceIndex = form.indexOf(relevance.sourceQuestionKey)
    if (sourceIndex < 0 || sourceIndex >= targetQuestionIndex) return null
    val source = form.questions[sourceIndex]
    val sourceSection = form.sectionOf(source)
    val ref =
      if (sourceSection?.repeatable == true && sourceSection.id == contextSectionId) {
        "../${source.name}"
      } else {
        form.instancePath(source)
      }
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
      RelevanceOperator.INCLUDES -> "selected($ref, $literal)"
      RelevanceOperator.IS_ANSWERED -> "string-length($ref) > 0"
    }
  }

  /** Returns the XPath `relevant` expression for [question], or `null` if always shown. */
  fun relevantExpression(form: EditorForm, question: EditorQuestion): String? =
    relevantExpression(form, question.relevance, form.indexOf(question.key), question.sectionId)

  /**
   * Returns the XPath `relevant` expression of [section]'s group node, which shows or hides all its
   * questions together, or `null` if always shown. Its source must come before the section's first
   * question and can't be inside the section.
   */
  fun relevantExpression(form: EditorForm, section: EditorSection): String? {
    val relevance = section.relevance ?: return null
    if (form.find(relevance.sourceQuestionKey)?.sectionId == section.id) return null
    val firstIndex = form.firstQuestionIndex(section.id)
    return relevantExpression(
      form,
      relevance,
      targetQuestionIndex = if (firstIndex >= 0) firstIndex else Int.MAX_VALUE,
    )
  }

  /** Media file name referenced by [choice]'s image, unique within the Form. */
  fun choiceImageFileName(question: EditorQuestion, choice: EditorChoice): String? =
    choice.image?.let { "${question.name}-${choice.value}.${it.fileExtension}" }

  private fun choiceImageMediaRef(question: EditorQuestion, choice: EditorChoice): MediaRef? {
    val fileName = choiceImageFileName(question, choice) ?: return null
    return MediaRef(image_uri = "jr://images/$fileName")
  }

  /** Element name of the extra choices column carrying [EditorChoice.colorHex]. */
  const val CHOICE_COLOR_COLUMN = "color"

  /**
   * Element name of the extra choices column carrying an inline [EditorChoice.image] data URI
   * (`data:<mimeType>;base64,<base64>`) when the image is not one of [ChoiceImages]'s built-in
   * icons, so exported XForms XML and [FormDef]s remain self-contained without a mutable
   * process-wide registry.
   */
  const val CHOICE_IMAGE_COLUMN = "collectone_image"

  /**
   * Element name of the extra choices column carrying [EditorChoice.code] (an underscore rather
   * than a colon, because extra choice columns become XML element names).
   */
  const val CHOICE_CODE_COLUMN = "collectone_code"

  /**
   * [CHOICE_CODE_COLUMN] as Ground, the platform Collect One replaces, named it. Imports still read
   * it when [CHOICE_CODE_COLUMN] is absent; exports never write it.
   */
  const val LEGACY_CHOICE_CODE_COLUMN = "ground_code"

  /**
   * Body `appearance` token indicating that data collectors can add a new map feature or table row
   * inline while selecting from a dataset-backed choice list.
   */
  const val ADD_ENTITY_APPEARANCE = "add-entity"

  /**
   * Whether [question]'s choices are exported as an internal secondary instance plus `itemset`
   * rather than inline `<item>`s: needed as soon as any choice carries extra columns (color, code)
   * or media (image) when the choices are defined manually on the question.
   */
  fun usesChoiceInstance(question: EditorQuestion): Boolean =
    question.type.hasChoices &&
      !question.usesDatasetChoices &&
      (question.parentQuestionKey.isNotBlank() ||
        question.choices.any {
          it.colorHex != null || it.image != null || it.code != null || it.parentValue.isNotBlank()
        })

  /** Secondary instance id (XLSForm `list_name`) for [question]'s choices. */
  fun choiceListName(question: EditorQuestion): String = question.name

  /**
   * Whether [question]'s choice labels go through itext (`itextId`) because of media or
   * multi-language translations.
   */
  private fun choicesRequireItext(
    question: EditorQuestion,
    hasMultiLanguageTranslations: Boolean = false,
  ): Boolean = hasMultiLanguageTranslations || question.choices.any { it.image != null }

  /** pyxform's positional itext id for choice [index] of [listName], e.g. `fruits-0`. */
  private fun choiceTextId(listName: String, index: Int): String = "$listName-$index"

  private fun questionLabelId(form: EditorForm, question: EditorQuestion): String =
    "${form.instancePath(question)}:label"

  private fun questionHintId(form: EditorForm, question: EditorQuestion): String =
    "${form.instancePath(question)}:hint"

  private fun choiceInlineTextId(
    form: EditorForm,
    question: EditorQuestion,
    choice: EditorChoice,
    index: Int,
  ): String {
    val baseVal = choice.value.ifBlank { index.toString() }
    val suffix =
      if (question.choices.count { it.value == choice.value } > 1) "${baseVal}_$index" else baseVal
    return "${form.instancePath(question)}/$suffix:label"
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
    defaultLanguage: String = "default",
  ): FormDefinition {
    val entity = target?.let { XFormsEntityPlan(form, it) }
    val choiceInstances = form.questions.filter(::usesChoiceInstance)
    val translatedLanguages =
      form.questions
        .flatMap { q ->
          q.labelTranslations.filterValues { it.isNotBlank() }.keys +
            q.hintTranslations.filterValues { it.isNotBlank() }.keys +
            q.choices.flatMap { c -> c.labelTranslations.filterValues { it.isNotBlank() }.keys }
        }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !isDefaultEditorLanguage(it, defaultLanguage) }
        .distinct()
    val hasMultiLanguageTranslations = translatedLanguages.isNotEmpty()
    val itextQuestions = choiceInstances.filter {
      choicesRequireItext(it, hasMultiLanguageTranslations)
    }

    val effectiveDefaultLang = defaultLanguage.ifBlank { "default" }
    val translations =
      when {
        hasMultiLanguageTranslations -> {
          val defaultStrings = buildMap {
            form.questions.forEach { question ->
              put(questionLabelId(form, question), LocalizedString(value_ = question.label))
              if (
                question.hint.isNotBlank() ||
                  question.hintTranslations.any { it.value.isNotBlank() }
              ) {
                put(questionHintId(form, question), LocalizedString(value_ = question.hint))
              }
              if (question.type.hasChoices && !question.usesDatasetChoices) {
                if (usesChoiceInstance(question)) {
                  val listName = choiceListName(question)
                  question.choices.forEachIndexed { index, choice ->
                    val media = choiceImageMediaRef(question, choice)
                    put(
                      choiceTextId(listName, index),
                      LocalizedString(value_ = choice.label, media = media),
                    )
                  }
                } else {
                  question.choices.forEachIndexed { index, choice ->
                    put(
                      choiceInlineTextId(form, question, choice, index),
                      LocalizedString(value_ = choice.label),
                    )
                  }
                }
              }
            }
          }
          val otherTranslations = translatedLanguages.map { lang ->
            val langStrings = buildMap {
              form.questions.forEach { question ->
                question
                  .labelIn(lang, defaultLanguage)
                  .takeIf { it.isNotBlank() }
                  ?.let { put(questionLabelId(form, question), LocalizedString(value_ = it)) }
                question
                  .hintIn(lang, defaultLanguage)
                  .takeIf { it.isNotBlank() }
                  ?.let { put(questionHintId(form, question), LocalizedString(value_ = it)) }
                if (question.type.hasChoices && !question.usesDatasetChoices) {
                  if (usesChoiceInstance(question)) {
                    val listName = choiceListName(question)
                    question.choices.forEachIndexed { index, choice ->
                      choice
                        .labelIn(lang, defaultLanguage)
                        .takeIf { it.isNotBlank() }
                        ?.let { translated ->
                          val media = choiceImageMediaRef(question, choice)
                          put(
                            choiceTextId(listName, index),
                            LocalizedString(value_ = translated, media = media),
                          )
                        }
                    }
                  } else {
                    question.choices.forEachIndexed { index, choice ->
                      choice
                        .labelIn(lang, defaultLanguage)
                        .takeIf { it.isNotBlank() }
                        ?.let { translated ->
                          put(
                            choiceInlineTextId(form, question, choice, index),
                            LocalizedString(value_ = translated),
                          )
                        }
                    }
                  }
                }
              }
            }
            LanguageTranslation(language = lang, is_default = false, strings = langStrings)
          }
          TranslationCatalog(
            languages =
              listOf(
                LanguageTranslation(
                  language = effectiveDefaultLang,
                  is_default = true,
                  strings = defaultStrings,
                )
              ) + otherTranslations
          )
        }
        itextQuestions.isNotEmpty() -> {
          val strings = buildMap {
            itextQuestions.forEach { question ->
              val listName = choiceListName(question)
              question.choices.forEachIndexed { index, choice ->
                val media = choiceImageMediaRef(question, choice)
                put(
                  choiceTextId(listName, index),
                  LocalizedString(value_ = choice.label, media = media),
                )
              }
            }
          }
          TranslationCatalog(
            languages =
              listOf(
                LanguageTranslation(language = "default", is_default = true, strings = strings)
              )
          )
        }
        else -> null
      }

    val schemaFields = buildList {
      if (entity?.selectsTarget == true) {
        add(
          FieldDefinition(name = SaveToRules.TARGET_ENTITY_FIELD, type = DataType.TYPE_SELECT_ONE)
        )
      }
      val processedSectionIds = mutableSetOf<String>()
      val emittedRootNames = mutableSetOf<String>()
      var i = 0
      while (i < form.questions.size) {
        val q = form.questions[i]
        val section = form.section(q.sectionId)
        if (section == null) {
          add(FieldDefinition(name = q.name, type = dataTypeOf(q)))
          emittedRootNames += q.name
          i++
        } else if (processedSectionIds.add(section.id)) {
          val sectionQuestions = form.questions.filter { it.sectionId == section.id }
          val activeSectionNames = sectionQuestions.map { it.name }.toSet()
          val deletedSectionQuestions =
            form.deletedQuestions
              .filter { it.sectionId == section.id && it.name !in activeSectionNames }
              .distinctBy { it.name }
          val childFields =
            (sectionQuestions + deletedSectionQuestions).map {
              FieldDefinition(name = it.name, type = dataTypeOf(it))
            }
          add(
            FieldDefinition(
              name = section.id,
              type = DataType.TYPE_MESSAGE,
              is_repeated = section.repeatable,
              fields = childFields,
            )
          )
          emittedRootNames += section.id
          i++
        } else {
          i++
        }
      }
      form.deletedQuestions
        .filter { form.section(it.sectionId) == null && it.name !in emittedRootNames }
        .distinctBy { it.name }
        .forEach { dq ->
          add(FieldDefinition(name = dq.name, type = dataTypeOf(dq)))
          emittedRootNames += dq.name
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
        add(XFormsDatasetInstances.forDataset(entity.target, inlineRows))
        emittedDatasetIds += entity.target.id
      }
      form.questions.forEach { question ->
        val datasetId =
          question.choiceDatasetId?.takeIf { question.type.hasChoices && it.isNotBlank() }
        if (datasetId != null && emittedDatasetIds.add(datasetId)) {
          val dataset =
            datasets.firstOrNull { it.id == datasetId } ?: target?.takeIf { it.id == datasetId }
          if (dataset != null) {
            add(XFormsDatasetInstances.forDataset(dataset, inlineRows))
          } else {
            add(XFormsDatasetInstances.forUnknownDataset(datasetId, question.choices, inlineRows))
          }
        }
      }
      choiceInstances.forEach { question ->
        val requiresItext = choicesRequireItext(question, hasMultiLanguageTranslations)
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
              choice.image?.let { img ->
                val imageUri = "jr://images/${choiceImageFileName(question, choice)}"
                if (ChoiceImages.lookup(imageUri) != ChoiceImageMedia(img.mimeType, img.base64)) {
                  put(CHOICE_IMAGE_COLUMN, ChoiceImages.encodeDataUri(img.mimeType, img.base64))
                }
              }
              choice.code?.let { code -> put(CHOICE_CODE_COLUMN, code) }
              if (choice.parentValue.isNotBlank()) {
                put("parent_value", choice.parentValue)
              }
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
      form.sections.forEach { section ->
        if (form.questionsIn(section.id).isNotEmpty()) {
          val relExpr = relevantExpression(form, section)
          if (relExpr != null) {
            add(
              FieldBinding(
                field_path = section.id,
                type = DataType.TYPE_MESSAGE,
                relevant_expression = relExpr,
              )
            )
          }
        }
      }
      form.questions.forEach { question ->
        val fieldPath = form.relativePath(question)
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
            field_path = fieldPath,
            type = dataTypeOf(question),
            required_expression =
              if (question.required && !question.type.isReadOnly) "true()" else "",
            read_only = question.type.isReadOnly,
            relevant_expression = relevantExpression(form, question).orEmpty(),
            constraint_expression = constraintExpr,
            constraint_message = constraintMsg,
            entity_saveto = entity?.saveTo?.get(question.key).orEmpty(),
            foreign_attributes = ConceptLinkAttributes.bindAttributes(question),
          )
        )
      }
      val activePaths = form.questions.map { form.relativePath(it) }.toSet()
      form.deletedQuestions
        .distinctBy { form.relativePath(it) }
        .forEach { question ->
          val fieldPath = form.relativePath(question)
          if (fieldPath !in activePaths) {
            add(
              FieldBinding(
                field_path = fieldPath,
                type = dataTypeOf(question),
                read_only = question.type.isReadOnly,
                foreign_attributes =
                  ConceptLinkAttributes.bindAttributes(question.copy(isDeleted = true)),
              )
            )
          }
        }
      if (entity != null) {
        addAll(entity.bindings())
      }
    }

    val actions = entity?.firstLoadActions().orEmpty()

    val entities = listOfNotNull(entity?.declaration(bindings))

    val viewComponents = buildList {
      if (entity?.selectsTarget == true) {
        add(ViewComponent(control = entity.targetPickerControl()))
      }
      // A section becomes one group at its first question, holding all its questions, like its
      // instance node in `schemaFields`, even if (e.g. after an import) its questions aren't
      // contiguous: a node can't be the `ref` of two groups.
      val emittedSectionIds = mutableSetOf<String>()
      form.questions.forEach { q ->
        val section = form.sectionOf(q)
        if (section == null) {
          add(
            ViewComponent(
              control =
                buildQuestionControl(
                  q,
                  hasMultiLanguageTranslations,
                  form = form,
                  section = null,
                  datasets = datasets,
                )
            )
          )
        } else if (emittedSectionIds.add(section.id)) {
          val childComponents =
            form.questionsIn(section.id).map { childQ ->
              ViewComponent(
                control =
                  buildQuestionControl(
                    childQ,
                    hasMultiLanguageTranslations,
                    form = form,
                    section = section,
                    datasets = datasets,
                  )
              )
            }
          if (section.repeatable) {
            add(
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = section.id,
                    label = LabelDef(text = section.title),
                    count_expression = section.repeatCountExpression,
                    no_add_remove = false,
                    components = childComponents,
                  )
              )
            )
          } else {
            add(
              ViewComponent(
                group =
                  GroupDef(
                    field_ref = section.id,
                    label = LabelDef(text = section.title),
                    appearance = "field-list",
                    components = childComponents,
                  )
              )
            )
          }
        }
      }
    }

    return FormDefinition(
      proto =
        FormDef(
          form_id = form.formId,
          title = form.title,
          version = "1",
          default_language = if (hasMultiLanguageTranslations) effectiveDefaultLang else "",
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
    defaultLanguage: String = "default",
  ): String =
    XFormsXmlSerializer.serialize(
      compile(
          form = form,
          target = target,
          inlineRows = inlineRows,
          datasets = datasets,
          defaultLanguage = defaultLanguage,
        )
        .proto,
      prettyPrint = true,
    )

  private fun dataTypeOf(question: EditorQuestion): DataType =
    when (question.type) {
      EditorQuestionType.TEXT,
      EditorQuestionType.LONG_TEXT,
      EditorQuestionType.NOTE -> DataType.TYPE_STRING
      EditorQuestionType.NUMBER ->
        if (question.decimalPlaces <= 0) DataType.TYPE_INT32 else DataType.TYPE_DOUBLE
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

  private fun buildQuestionControl(
    question: EditorQuestion,
    hasMultiLanguageTranslations: Boolean = false,
    form: EditorForm? = null,
    section: EditorSection? = null,
    datasets: List<EditorDataset> = emptyList(),
  ): ControlDef {
    val type = question.type
    val parentQuestion = form?.cascadeParentOf(question)
    val cascadingNodesetFilter =
      if (form != null) cascadingChoiceFilter(form, question, section, datasets) else ""
    val itemset =
      buildQuestionItemset(question, cascadingNodesetFilter, hasMultiLanguageTranslations)
    val choices =
      if (type.hasChoices && !question.usesDatasetChoices && itemset == null) {
        buildInlineChoices(form, question, hasMultiLanguageTranslations)
      } else {
        emptyList()
      }
    val hasHint =
      question.hint.isNotBlank() ||
        (hasMultiLanguageTranslations && question.hintTranslations.any { it.value.isNotBlank() })
    val fieldRef = if (section != null) "${section.id}/${question.name}" else question.name
    return ControlDef(
      field_ref = fieldRef,
      type = controlTypeOf(type),
      label =
        if (hasMultiLanguageTranslations && form != null) {
          LabelDef(text_id = questionLabelId(form, question))
        } else {
          LabelDef(text = question.label)
        },
      hint =
        if (!hasHint) {
          null
        } else if (hasMultiLanguageTranslations && form != null) {
          LabelDef(text_id = questionHintId(form, question))
        } else {
          LabelDef(text = question.hint)
        },
      appearance = bodyAppearance(question).orEmpty(),
      media_type = type.mediaType,
      choices = choices,
      itemset = itemset,
      parent_question_key = parentQuestion?.name.orEmpty(),
    )
  }

  private fun buildQuestionItemset(
    question: EditorQuestion,
    cascadingNodesetFilter: String,
    hasMultiLanguageTranslations: Boolean,
  ): ItemsetDef? {
    val choiceDatasetId =
      question.choiceDatasetId?.takeIf {
        question.type.hasChoices && question.usesDatasetChoices && it.isNotBlank()
      }
    return when {
      choiceDatasetId != null ->
        ItemsetDef(
          instance_id = choiceDatasetId,
          nodeset_path = "/root/item",
          nodeset_filter = cascadingNodesetFilter,
          value_ref = "name",
          label_ref = "label",
        )
      usesChoiceInstance(question) ->
        ItemsetDef(
          instance_id = choiceListName(question),
          nodeset_path = "/root/item",
          nodeset_filter = cascadingNodesetFilter,
          value_ref = "name",
          label_ref =
            if (choicesRequireItext(question, hasMultiLanguageTranslations)) "jr:itext(itextId)"
            else "label",
        )
      else -> null
    }
  }

  private fun buildInlineChoices(
    form: EditorForm?,
    question: EditorQuestion,
    hasMultiLanguageTranslations: Boolean,
  ): List<ChoiceItem> =
    question.choices.mapIndexed { index, choice ->
      ChoiceItem(
        value_ = choice.value,
        label =
          if (hasMultiLanguageTranslations && form != null) {
            LabelDef(text_id = choiceInlineTextId(form, question, choice, index))
          } else {
            LabelDef(text = choice.label)
          },
        parent_value = choice.parentValue,
        properties =
          if (choice.parentValue.isNotBlank()) mapOf("parent_value" to choice.parentValue)
          else emptyMap(),
      )
    }

  /**
   * The `itemset` predicate of a cascading select (XLSForm `choice_filter`): a row is listed when
   * it has no `parent_value` or its `parent_value` is among the parent question's selections. The
   * column is only written for choices tied to a parent value (and dataset rows may lack it), so
   * "no parent value" is `string-length(parent_value) = 0`, which also holds for a missing column
   * (`parent_value = ''` doesn't: a comparison with an empty node-set is false). `selected()`
   * covers select-one and select-multiple parents alike, where a plain `=` would compare a
   * select-multiple parent's whole space-separated answer. A parent in the child's own repeat is
   * addressed from the child (`current()/../<name>`) so each repeat instance filters on its own
   * answer. Empty when [question] has no parent, or its parent no longer resolves to a choice
   * question ([FormEditorValidator] reports that), so the choices stay unfiltered rather than
   * filtered against a missing field.
   */
  fun cascadingChoiceFilter(
    form: EditorForm,
    question: EditorQuestion,
    section: EditorSection? = form.section(question.sectionId),
    datasets: List<EditorDataset> = emptyList(),
  ): String {
    val parent = form.cascadeParentOf(question) ?: return ""
    val parentSection = form.section(parent.sectionId)
    val parentRef =
      when {
        parentSection == null -> "/data/${parent.name}"
        parentSection.repeatable && parentSection.id == section?.id -> "current()/../${parent.name}"
        else -> "/data/${parentSection.id}/${parent.name}"
      }
    val dataset =
      question.choiceDatasetId
        ?.takeIf { question.usesDatasetChoices && it.isNotBlank() }
        ?.let { id -> datasets.firstOrNull { it.id == id } }
    val column =
      when {
        dataset == null -> "parent_value"
        dataset.columnFor("parent_value") != null -> "parent_value"
        dataset.columnFor(parent.name) != null -> dataset.columnFor(parent.name)!!
        dataset.columnFor("parent") != null -> "parent"
        else -> "parent_value"
      }
    return "(string-length($column) = 0 or selected($parentRef, $column))"
  }

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
}
