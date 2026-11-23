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

import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.ForeignAttribute
import openforis.collectone.v1.library.ConceptRef
import openforis.collectone.v1.survey.FormConceptLinks
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.LibraryText
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.core.forms.serialization.COLLECT_ONE_XFORMS_NAMESPACE
import org.openforis.collectone.v2.core.forms.serialization.COLLECT_ONE_XFORMS_PREFIX
import org.openforis.collectone.v2.core.forms.serialization.LEGACY_GROUND_XFORMS_NAMESPACE

/**
 * Links between Form questions and dictionary concepts (see
 * `docs/technical/model/library/01-concepts.md`, "Linking Form Fields"). Two mechanisms carry them:
 * - **Survey-level links (authoritative)**: `SurveyDef.form_concept_links`, keyed by field path
 *   ([FormConceptLinkSync]).
 * - **`collectone:concept` bind attributes (portable)**: generic foreign-attribute preservation in
 *   `FieldBinding.foreign_attributes` ([ConceptLinkAttributes]).
 */
object ConceptLinkAttributes {
  /** Namespace of Collect One's XForms extensions. */
  const val COLLECT_ONE_NAMESPACE: String = COLLECT_ONE_XFORMS_NAMESPACE

  /** Qualified name Collect One writes for concept links. */
  const val CONCEPT_ATTRIBUTE: String = "$COLLECT_ONE_XFORMS_PREFIX:concept"

  /** Qualified name Collect One writes on `<bind>` for soft-deleted questions. */
  const val DELETED_ATTRIBUTE: String = "$COLLECT_ONE_XFORMS_PREFIX:deleted"

  /** Qualified name Collect One writes on `<bind>` to preserve a soft-deleted question's label. */
  const val LABEL_ATTRIBUTE: String = "$COLLECT_ONE_XFORMS_PREFIX:label"

  private const val CONCEPT_LOCAL_NAME = "concept"
  private const val DELETED_LOCAL_NAME = "deleted"
  private const val LABEL_LOCAL_NAME = "label"

  /**
   * Whether [namespaceUri] / [qualifiedName] name a `collectone:concept` attribute (any prefix), or
   * a legacy `ground:concept` one in [LEGACY_GROUND_XFORMS_NAMESPACE], which [fromBinding] reads as
   * a link that [bindAttributes] then writes in [COLLECT_ONE_NAMESPACE].
   */
  fun isConceptAttribute(namespaceUri: String, qualifiedName: String): Boolean =
    (namespaceUri == COLLECT_ONE_NAMESPACE || namespaceUri == LEGACY_GROUND_XFORMS_NAMESPACE) &&
      qualifiedName.substringAfter(':') == CONCEPT_LOCAL_NAME

  /** Whether [namespaceUri] / [qualifiedName] name a `collectone:deleted` attribute. */
  fun isDeletedAttribute(namespaceUri: String, qualifiedName: String): Boolean =
    (namespaceUri == COLLECT_ONE_NAMESPACE || namespaceUri == LEGACY_GROUND_XFORMS_NAMESPACE) &&
      qualifiedName.substringAfter(':') == DELETED_LOCAL_NAME

  /** Whether [namespaceUri] / [qualifiedName] name a `collectone:label` attribute. */
  fun isLabelAttribute(namespaceUri: String, qualifiedName: String): Boolean =
    (namespaceUri == COLLECT_ONE_NAMESPACE || namespaceUri == LEGACY_GROUND_XFORMS_NAMESPACE) &&
      qualifiedName.substringAfter(':') == LABEL_LOCAL_NAME

  /** Whether [binding] is flagged with `collectone:deleted="true"`. */
  fun isDeleted(binding: FieldBinding?): Boolean =
    binding?.foreign_attributes.orEmpty().any {
      isDeletedAttribute(it.namespace_uri, it.qualified_name) &&
        it.value_.equals("true", ignoreCase = true)
    }

  /** Preserved label stored in `collectone:label` on a soft-deleted [binding], if any. */
  fun deletedLabel(binding: FieldBinding?): String? =
    binding
      ?.foreign_attributes
      .orEmpty()
      .firstOrNull { isLabelAttribute(it.namespace_uri, it.qualified_name) }
      ?.value_
      ?.takeIf { it.isNotBlank() }

  /**
   * The foreign attributes of [question]'s `<bind>`: its preserved ones, then `collectone:concept`
   * for its [EditorQuestion.conceptLink], and `collectone:deleted` / `collectone:label` when
   * [EditorQuestion.isDeleted] is true. A link replaces any preserved `collectone:concept`.
   */
  fun bindAttributes(question: EditorQuestion): List<ForeignAttribute> {
    val link = question.conceptLink
    val preserved =
      question.foreignAttributes
        .filterNot {
          (link != null && isConceptAttribute(it.namespaceUri, it.qualifiedName)) ||
            isDeletedAttribute(it.namespaceUri, it.qualifiedName) ||
            isLabelAttribute(it.namespaceUri, it.qualifiedName)
        }
        .map { ForeignAttribute(it.namespaceUri, it.qualifiedName, it.value) }
    return buildList {
      addAll(preserved)
      if (link != null) {
        add(ForeignAttribute(COLLECT_ONE_NAMESPACE, CONCEPT_ATTRIBUTE, link.encoded))
      }
      if (question.isDeleted) {
        add(ForeignAttribute(COLLECT_ONE_NAMESPACE, DELETED_ATTRIBUTE, "true"))
        if (question.label.isNotBlank()) {
          add(ForeignAttribute(COLLECT_ONE_NAMESPACE, LABEL_ATTRIBUTE, question.label))
        }
      }
    }
  }

  /**
   * The concept link (from the first well-formed `collectone:concept`) and the other foreign
   * attributes of [binding]. Malformed `collectone:concept` values are kept as foreign attributes.
   */
  fun fromBinding(binding: FieldBinding?): Pair<ConceptLink?, List<EditorForeignAttribute>> {
    var link: ConceptLink? = null
    val others = mutableListOf<EditorForeignAttribute>()
    binding?.foreign_attributes.orEmpty().forEach { attribute ->
      if (
        isDeletedAttribute(attribute.namespace_uri, attribute.qualified_name) ||
          isLabelAttribute(attribute.namespace_uri, attribute.qualified_name)
      ) {
        return@forEach
      }
      val parsed =
        if (link == null && isConceptAttribute(attribute.namespace_uri, attribute.qualified_name)) {
          ConceptLink.parse(attribute.value_)
        } else {
          null
        }
      if (parsed != null) {
        link = parsed
      } else {
        others +=
          EditorForeignAttribute(
            attribute.namespace_uri,
            attribute.qualified_name,
            attribute.value_,
          )
      }
    }
    return link to others
  }

  /** Concept IDs named by `collectone:concept` attributes kept on [question] (not linked). */
  fun unlinkedConceptIds(question: EditorQuestion): List<String> =
    question.foreignAttributes
      .filter { isConceptAttribute(it.namespaceUri, it.qualifiedName) }
      .map { ConceptLink.parse(it.value)?.conceptId ?: it.value }
}

/** Keeps survey-level links (`SurveyDef.form_concept_links`) and Form questions in sync. */
object FormConceptLinkSync {
  /** Field path of an unsectioned [question] in the survey-level links (`/data/<name>`). */
  fun fieldPath(question: EditorQuestion): String = "/data/${question.name}"

  /**
   * Field path of [question] in [form]'s survey-level links (`/data/<name>` or
   * `/data/<sec>/<name>`).
   */
  fun fieldPath(form: EditorForm, question: EditorQuestion): String = form.instancePath(question)

  /** [form]'s links as `FormConceptLinks`, or `null` when no question is linked. */
  fun toFormConceptLinks(form: EditorForm): FormConceptLinks? {
    val links =
      form.questions
        .mapNotNull { q ->
          q.conceptLink?.let { fieldPath(form, q) to ConceptRef(it.conceptId, it.version) }
        }
        .toMap()
    return if (links.isEmpty()) null
    else FormConceptLinks(form_id = form.formId, field_concepts = links)
  }

  /** Survey-level links of every Form in [forms] that has any. */
  fun toFormConceptLinks(forms: List<EditorForm>): List<FormConceptLinks> =
    forms.mapNotNull(::toFormConceptLinks)

  /**
   * [form] with its question links set from the authoritative survey-level [links] (matched by
   * field path, falling back to `/data/<name>`). Without [links] the form is returned unchanged.
   */
  fun applyLinks(form: EditorForm, links: FormConceptLinks?): EditorForm {
    if (links == null) return form
    return form.copy(
      questions =
        form.questions.map { q ->
          val ref = links.field_concepts[fieldPath(form, q)] ?: links.field_concepts[fieldPath(q)]
          q.copy(conceptLink = ref?.let { ConceptLink(it.concept_id, it.version.coerceAtLeast(1)) })
        }
    )
  }

  /**
   * [form] after an import: links whose concept [isKnown] (in the survey's resolved library) are
   * kept; the others go back to `collectone:concept` foreign attributes, which the validator
   * reports.
   */
  fun reconcileImported(form: EditorForm, isKnown: (conceptId: String) -> Boolean): EditorForm =
    form.copy(
      questions =
        form.questions.map { q ->
          val link = q.conceptLink
          if (link == null || isKnown(link.conceptId)) {
            q
          } else {
            q.copy(
              conceptLink = null,
              foreignAttributes =
                q.foreignAttributes +
                  EditorForeignAttribute(
                    ConceptLinkAttributes.COLLECT_ONE_NAMESPACE,
                    ConceptLinkAttributes.CONCEPT_ATTRIBUTE,
                    link.encoded,
                  ),
            )
          }
        }
    )
}

/**
 * Linking a question to a concept: matching question types, the question's "default state" (which
 * decides whether linking also fills in its settings), and mapping choices to code-list values.
 */
object ConceptLinking {
  /** Question type created for a concept of [type]. */
  fun questionTypeFor(type: ConceptDataType): EditorQuestionType =
    when (type) {
      ConceptDataType.TEXT -> EditorQuestionType.TEXT
      ConceptDataType.INTEGER,
      ConceptDataType.DECIMAL -> EditorQuestionType.NUMBER
      ConceptDataType.DATE -> EditorQuestionType.DATE
      ConceptDataType.SELECT_ONE -> EditorQuestionType.SELECT_ONE
      ConceptDataType.SELECT_MULTIPLE -> EditorQuestionType.SELECT_MULTIPLE
      ConceptDataType.POINT -> EditorQuestionType.LOCATION
      ConceptDataType.LINE -> EditorQuestionType.LINE
      ConceptDataType.POLYGON -> EditorQuestionType.POLYGON
      ConceptDataType.MEDIA -> EditorQuestionType.PHOTO
    }

  /** Concept type matching a question of [type], or `null` for notes. */
  fun conceptTypeFor(type: EditorQuestionType, decimalPlaces: Int = 0): ConceptDataType? =
    when (type) {
      EditorQuestionType.NUMBER ->
        if (decimalPlaces > 0) ConceptDataType.DECIMAL else ConceptDataType.INTEGER
      else ->
        ConceptDataType.entries.firstOrNull { questionTypeFor(it) == type }
          ?: ConceptDataType.entries.firstOrNull { it.isCompatibleWith(type) }
    }

  private val DEFAULT_CHOICE_VALUES = listOf("option_1", "option_2")

  /**
   * Whether [question] is still as the editor created it (apart from its label, which is being
   * typed): default name, no hint, not required, default or no choices, no validation, display
   * logic, or link. Linking a concept to a question in this state also sets its settings.
   */
  fun isDefaultState(question: EditorQuestion): Boolean {
    val allowedPrefixes =
      if (question.type == EditorQuestionType.NUMBER) {
        setOf(question.type.defaultNamePrefix, "count", "amount")
      } else {
        setOf(question.type.defaultNamePrefix)
      }
    val defaultName = allowedPrefixes.any { prefix ->
      Regex("^${Regex.escape(prefix)}(_\\d+)?$").matches(question.name)
    }
    val defaultChoices =
      question.choices.isEmpty() ||
        (question.choices.map { it.value } == DEFAULT_CHOICE_VALUES &&
          question.choices.all { it.colorHex == null && it.image == null && it.code == null })
    return defaultName &&
      question.hint.isBlank() &&
      !question.required &&
      defaultChoices &&
      !question.usesDatasetChoices &&
      question.validation == null &&
      question.relevance == null &&
      question.conceptLink == null
  }

  /**
   * [question] linked to [concept]. When it's in its [isDefaultState], linking also sets its type
   * (unless already compatible), name (from the concept ID, unique among [takenNames]), choices
   * (the code list, labeled in [language]), unit hint, and a `≥ 0` minimum for numbers. The label
   * is kept either way; manual choices of a customized select question get codes where they match.
   */
  fun link(
    question: EditorQuestion,
    concept: LibraryConcept,
    takenNames: Set<String>,
    language: String,
    supportedLanguages: List<String> = emptyList(),
  ): EditorQuestion {
    val linked = question.copy(conceptLink = ConceptLink.to(concept))
    if (!isDefaultState(question)) {
      return linked.copy(choices = withCodes(linked.choices, concept))
    }
    val type =
      if (concept.dataType.isCompatibleWith(question.type)) question.type
      else questionTypeFor(concept.dataType)
    val decimalPlaces =
      when (concept.dataType) {
        ConceptDataType.DECIMAL ->
          if (question.decimalPlaces > 0) question.decimalPlaces
          else EditorQuestion.DEFAULT_DECIMAL_PLACES
        ConceptDataType.INTEGER -> 0
        else -> question.decimalPlaces
      }
    val name = uniqueName(LibraryIds.nameOf(concept.id), takenNames - question.name)
    val otherLanguages =
      supportedLanguages
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(language, ignoreCase = true) }
        .distinct()
    val choices =
      if (type.hasChoices) {
        concept.codeList.map { item ->
          val translations =
            otherLanguages
              .mapNotNull { lang ->
                val raw =
                  item.label.values[lang]
                    ?: item.label.values[lang.substringBefore('-').substringBefore('_').lowercase()]
                raw?.takeIf { it.isNotBlank() }?.let { lang to it }
              }
              .toMap()
          EditorChoice(
            value = item.code,
            label = item.label.get(language),
            code = item.code,
            labelTranslations = translations,
          )
        }
      } else {
        emptyList()
      }
    val validation =
      if (type.isNumeric && concept.dataType != ConceptDataType.TEXT) EditorValidation(min = "0")
      else null
    return linked.copy(
      type = type,
      decimalPlaces = decimalPlaces,
      name = if (FormEditorValidator.isValidName(name)) name else question.name,
      choices = choices.ifEmpty { if (type.hasChoices) question.choices else emptyList() },
      hint = unitHint(concept.unit),
      validation = validation,
      capture = if (type.isGeometry) GeometryCapture.GPS_OR_MAP else question.capture,
    )
  }

  /** Hint naming the concept's unit (`"Unit: ha"`), or blank. */
  fun unitHint(unit: String): String =
    unitLabel(unit).takeIf { it.isNotEmpty() }?.let { "Unit: $it" }.orEmpty()

  /** Display form of a UCUM unit code (`har` → `ha`, `m3` → `m³`). */
  fun unitLabel(unit: String): String =
    when (unit) {
      "har" -> "ha"
      "m3" -> "m³"
      "m2" -> "m²"
      else -> unit
    }

  /**
   * [choices] with a code added to each unmapped choice whose value, code, or label (in any
   * language) matches a code-list value of [concept]. Existing codes are kept.
   */
  fun withCodes(choices: List<EditorChoice>, concept: LibraryConcept): List<EditorChoice> {
    if (concept.codeList.isEmpty()) return choices
    return choices.map { choice ->
      if (choice.code != null) choice else choice.copy(code = matchingCode(choice, concept))
    }
  }

  /** Code-list value of [concept] that [choice] matches by value or label, or `null`. */
  fun matchingCode(choice: EditorChoice, concept: LibraryConcept): String? {
    val value = LibraryText.fold(choice.value)
    val label = LibraryText.fold(choice.label)
    return concept.codeList
      .firstOrNull { item ->
        LibraryText.fold(item.code) == value ||
          LibraryText.fold(item.code) == label ||
          item.label.values.values.any { LibraryText.fold(it) == label }
      }
      ?.code
  }

  private fun uniqueName(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
  }
}

/**
 * Warnings (never blocking) about a Form's concept links, against the survey's resolved library
 * ([lookup] returns the concept for an ID, or `null` when it isn't in the library).
 */
object ConceptLinkValidator {
  fun validate(
    form: EditorForm,
    lookup: (conceptId: String) -> LibraryConcept?,
  ): List<EditorIssue> {
    val warnings = mutableListOf<EditorIssue>()
    val linkCounts =
      form.questions.mapNotNull { it.conceptLink?.conceptId }.groupingBy { it }.eachCount()
    for (question in form.questions) {
      ConceptLinkAttributes.unlinkedConceptIds(question).forEach { id ->
        warnings +=
          warning(
            question,
            EditorIssueMessage.CONCEPT_NOT_IN_DICTIONARY.message(id),
          )
      }
      val link = question.conceptLink ?: continue
      val concept = lookup(link.conceptId)
      if (concept == null) {
        warnings +=
          warning(
            question,
            EditorIssueMessage.CONCEPT_NOT_IN_DICTIONARY.message(link.conceptId),
          )
        continue
      }
      val label = concept.label.text
      if (!concept.dataType.isCompatibleWith(question)) {
        val isDecimal = question.type == EditorQuestionType.NUMBER && question.decimalPlaces > 0
        val actualType: Enum<*> = if (isDecimal) ConceptDataType.DECIMAL else question.type
        warnings +=
          warning(
            question,
            EditorIssueMessage.CONCEPT_TYPE_MISMATCH.message(
              label,
              DomainMessage.Lowercase(DomainMessage.Label(concept.dataType)),
              DomainMessage.Lowercase(DomainMessage.Label(actualType)),
            ),
          )
      }
      if (concept.isDeprecated) {
        warnings +=
          warning(
            question,
            EditorIssueMessage.CONCEPT_DEPRECATED.message(label),
          )
      }
      if ((linkCounts[link.conceptId] ?: 0) > 1) {
        warnings +=
          warning(
            question,
            EditorIssueMessage.CONCEPT_LINKED_TWICE.message(label),
          )
      }
      if (
        question.type.hasChoices && !question.usesDatasetChoices && concept.codeList.isNotEmpty()
      ) {
        val codes = concept.codeList.map { it.code }.toSet()
        val unmapped = question.choices.filter { it.code == null || it.code !in codes }
        if (unmapped.isNotEmpty()) {
          val unmappedLabels = unmapped.joinToString { it.label.ifBlank { it.value } }
          warnings +=
            warning(
              question,
              EditorIssueCountedMessage.CONCEPT_UNMAPPED_CHOICES.message(
                unmapped.size,
                unmapped.size,
                label,
                unmappedLabels,
              ),
            )
        }
      }
    }
    return warnings
  }

  private fun warning(question: EditorQuestion, message: DomainMessage) =
    EditorIssue(question.key, message, isWarning = true)
}
