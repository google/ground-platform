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

import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.ForeignAttribute
import groundplatform.v2.library.ConceptRef
import groundplatform.v2.survey.FormConceptLinks
import org.groundplatform.v2.core.forms.serialization.GROUND_XFORMS_NAMESPACE
import org.groundplatform.v2.core.forms.serialization.GROUND_XFORMS_PREFIX
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryText

/**
 * Links between Form questions and dictionary concepts (see
 * `docs/technical/model/library/01-concepts.md`, "Linking Form Fields"). Two mechanisms carry them:
 * - **Survey-level links (authoritative)**: `SurveyDef.form_concept_links`, keyed by field path
 *   ([FormConceptLinkSync]).
 * - **`ground:concept` bind attributes (portable)**: generic foreign-attribute preservation in
 *   `FieldBinding.foreign_attributes` ([ConceptLinkAttributes]).
 */
object ConceptLinkAttributes {
  /** Namespace of Ground's XForms extensions. */
  const val GROUND_NAMESPACE: String = GROUND_XFORMS_NAMESPACE

  /** Qualified name Ground writes for concept links. */
  const val CONCEPT_ATTRIBUTE: String = "$GROUND_XFORMS_PREFIX:concept"

  private const val CONCEPT_LOCAL_NAME = "concept"

  /** Whether [namespaceUri] / [qualifiedName] name a `ground:concept` attribute (any prefix). */
  fun isConceptAttribute(namespaceUri: String, qualifiedName: String): Boolean =
    namespaceUri == GROUND_NAMESPACE && qualifiedName.substringAfter(':') == CONCEPT_LOCAL_NAME

  /**
   * The foreign attributes of [question]'s `<bind>`: its preserved ones, then `ground:concept` for
   * its [EditorQuestion.conceptLink]. A link replaces any preserved `ground:concept`.
   */
  fun bindAttributes(question: EditorQuestion): List<ForeignAttribute> {
    val link = question.conceptLink
    val preserved =
      question.foreignAttributes
        .filterNot { link != null && isConceptAttribute(it.namespaceUri, it.qualifiedName) }
        .map { ForeignAttribute(it.namespaceUri, it.qualifiedName, it.value) }
    return if (link == null) preserved
    else preserved + ForeignAttribute(GROUND_NAMESPACE, CONCEPT_ATTRIBUTE, link.encoded)
  }

  /**
   * The concept link (from the first well-formed `ground:concept`) and the other foreign attributes
   * of [binding]. Malformed `ground:concept` values are kept as foreign attributes.
   */
  fun fromBinding(binding: FieldBinding?): Pair<ConceptLink?, List<EditorForeignAttribute>> {
    var link: ConceptLink? = null
    val others = mutableListOf<EditorForeignAttribute>()
    binding?.foreign_attributes.orEmpty().forEach { attribute ->
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

  /** Concept IDs named by `ground:concept` attributes kept on [question] (not linked). */
  fun unlinkedConceptIds(question: EditorQuestion): List<String> =
    question.foreignAttributes
      .filter { isConceptAttribute(it.namespaceUri, it.qualifiedName) }
      .map { ConceptLink.parse(it.value)?.conceptId ?: it.value }
}

/** Keeps survey-level links (`SurveyDef.form_concept_links`) and Form questions in sync. */
object FormConceptLinkSync {
  /** Field path of [question] in the survey-level links (`/data/<name>`). */
  fun fieldPath(question: EditorQuestion): String = "/data/${question.name}"

  /** [form]'s links as `FormConceptLinks`, or `null` when no question is linked. */
  fun toFormConceptLinks(form: EditorForm): FormConceptLinks? {
    val links =
      form.questions
        .mapNotNull { q ->
          q.conceptLink?.let { fieldPath(q) to ConceptRef(it.conceptId, it.version) }
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
   * field path). Without [links] the form is returned unchanged.
   */
  fun applyLinks(form: EditorForm, links: FormConceptLinks?): EditorForm {
    if (links == null) return form
    return form.copy(
      questions =
        form.questions.map { q ->
          val ref = links.field_concepts[fieldPath(q)]
          q.copy(conceptLink = ref?.let { ConceptLink(it.concept_id, it.version.coerceAtLeast(1)) })
        }
    )
  }

  /**
   * [form] after an import: links whose concept [isKnown] (in the survey's resolved library) are
   * kept; the others go back to `ground:concept` foreign attributes, which the validator reports.
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
                    ConceptLinkAttributes.GROUND_NAMESPACE,
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
      ConceptDataType.INTEGER -> EditorQuestionType.INTEGER
      ConceptDataType.DECIMAL -> EditorQuestionType.DECIMAL
      ConceptDataType.DATE -> EditorQuestionType.DATE
      ConceptDataType.SELECT_ONE -> EditorQuestionType.SELECT_ONE
      ConceptDataType.SELECT_MULTIPLE -> EditorQuestionType.SELECT_MULTIPLE
      ConceptDataType.POINT -> EditorQuestionType.LOCATION
      ConceptDataType.LINE -> EditorQuestionType.LINE
      ConceptDataType.POLYGON -> EditorQuestionType.POLYGON
      ConceptDataType.MEDIA -> EditorQuestionType.PHOTO
    }

  /** Concept type matching a question of [type], or `null` for notes. */
  fun conceptTypeFor(type: EditorQuestionType): ConceptDataType? =
    ConceptDataType.entries.firstOrNull { questionTypeFor(it) == type }
      ?: ConceptDataType.entries.firstOrNull { it.isCompatibleWith(type) }

  private val DEFAULT_CHOICE_VALUES = listOf("option_1", "option_2")

  /**
   * Whether [question] is still as the editor created it (apart from its label, which is being
   * typed): default name, no hint, not required, default or no choices, no validation, display
   * logic, or link. Linking a concept to a question in this state also sets its settings.
   */
  fun isDefaultState(question: EditorQuestion): Boolean {
    val defaultName = Regex("^${Regex.escape(question.type.defaultNamePrefix)}(_\\d+)?$")
    val defaultChoices =
      question.choices.isEmpty() ||
        (question.choices.map { it.value } == DEFAULT_CHOICE_VALUES &&
          question.choices.all { it.colorHex == null && it.image == null && it.code == null })
    return defaultName.matches(question.name) &&
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
  ): EditorQuestion {
    val linked = question.copy(conceptLink = ConceptLink.to(concept))
    if (!isDefaultState(question)) {
      return linked.copy(choices = withCodes(linked.choices, concept))
    }
    val type =
      if (concept.dataType.isCompatibleWith(question.type)) question.type
      else questionTypeFor(concept.dataType)
    val name = uniqueName(LibraryIds.nameOf(concept.id), takenNames - question.name)
    val choices =
      if (type.hasChoices) {
        concept.codeList.map {
          EditorChoice(value = it.code, label = it.label.get(language), code = it.code)
        }
      } else {
        emptyList()
      }
    val validation =
      if (type.isNumeric && concept.dataType != ConceptDataType.TEXT) EditorValidation(min = "0")
      else null
    return linked.copy(
      type = type,
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
        warnings += warning(question, "Linked to \"$id\", which isn't in this survey's dictionary.")
      }
      val link = question.conceptLink ?: continue
      val concept = lookup(link.conceptId)
      if (concept == null) {
        warnings +=
          warning(
            question,
            "Linked to \"${link.conceptId}\", which isn't in this survey's dictionary.",
          )
        continue
      }
      val label = concept.label.text
      if (!concept.dataType.isCompatibleWith(question.type)) {
        warnings +=
          warning(
            question,
            "\"$label\" expects ${concept.dataType.label.lowercase()} answers, but this is a " +
              "${question.type.label.lowercase()} question.",
          )
      }
      if (concept.isDeprecated) {
        warnings += warning(question, "\"$label\" is deprecated. Link a current standard field.")
      }
      if ((linkCounts[link.conceptId] ?: 0) > 1) {
        warnings +=
          warning(question, "\"$label\" is linked to more than one question in this form.")
      }
      if (
        question.type.hasChoices && !question.usesDatasetChoices && concept.codeList.isNotEmpty()
      ) {
        val codes = concept.codeList.map { it.code }.toSet()
        val unmapped = question.choices.filter { it.code == null || it.code !in codes }
        if (unmapped.isNotEmpty()) {
          warnings +=
            warning(
              question,
              "Match ${unmapped.size} ${if (unmapped.size == 1) "choice" else "choices"} to " +
                "\"$label\" values so answers can be added up: " +
                unmapped.joinToString { it.label.ifBlank { it.value } } +
                ".",
            )
        }
      }
    }
    return warnings
  }

  private fun warning(question: EditorQuestion, message: String) =
    EditorIssue(question.key, message, isWarning = true)
}
