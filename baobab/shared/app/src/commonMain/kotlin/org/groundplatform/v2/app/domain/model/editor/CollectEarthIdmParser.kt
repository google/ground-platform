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
package org.groundplatform.v2.app.domain.model.editor

import org.groundplatform.v2.app.domain.model.FormAvailability

/**
 * Parses Open Foris Collect / Collect Earth (`.cep` / `.idm.xml`) survey schema definitions
 * (`<survey>` or `<idm>` root elements with `<codeLists>` and `<schema>`) into an [ImportedForm].
 */
object CollectEarthIdmParser {
  private val projectRegex = Regex("""<project[^>]*>([^<]+)</project>""")
  private val listRegex = Regex("""<list\s+[^>]*name="([^"]+)"[^>]*>([\s\S]*?)</list>""")
  private val itemRegex = Regex("""<item[^>]*>([\s\S]*?)</item>""")
  private val codeRegex = Regex("""<code[^>]*>([^<]+)</code>""")
  private val labelRegex = Regex("""<label[^>]*>([^<]+)</label>""")
  private val schemaRegex = Regex("""<schema[^>]*>([\s\S]*?)</schema>""")
  private val attributeRegex =
    Regex("""<(text|number|code|boolean|date|coordinate|file)\s+([^>]*?)(?:/>|>([\s\S]*?)</\1>)""")
  private val nameAttrRegex = Regex("""\bname="([^"]+)"""")
  private val listAttrRegex = Regex("""\blist="([^"]+)"""")
  private val multipleAttrRegex = Regex("""\bmultiple="([^"]+)"""")
  private val requiredAttrRegex = Regex("""\brequired="([^"]+)"""")
  private val typeAttrRegex = Regex("""\btype="([^"]+)"""")
  private val descriptionRegex = Regex("""<description[^>]*>([^<]+)</description>""")

  /**
   * Parses [xml] as a Collect Earth / Open Foris IDM definition, or returns `null` when [xml] is
   * not an IDM schema or contains no recognizable attributes.
   */
  fun fromXml(
    xml: String,
    formId: String = "",
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.MOBILE,
  ): ImportedForm? {
    val trimmed = xml.trim()
    if (!trimmed.contains("<survey") && !trimmed.contains("<idm")) return null

    val projectTitle =
      projectRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { fallbackTitle }
    val codeLists = parseCodeLists(trimmed)
    val schemaBody = schemaRegex.find(trimmed)?.groupValues?.get(1) ?: trimmed
    val questions = parseQuestions(schemaBody, codeLists)
    if (questions.isEmpty()) return null

    val resolvedFormId = formId.ifBlank { slugify(projectTitle) }.ifBlank { FormIds.newFormId() }
    val resolvedTitle = projectTitle.ifBlank { resolvedFormId }
    return ImportedForm(
      form =
        EditorForm(
          formId = resolvedFormId,
          title = resolvedTitle,
          questions = questions,
          availability = availability,
        ),
      notes = listOf("Imported from Collect Earth (.cep / Open Foris IDM) schema."),
    )
  }

  private fun parseCodeLists(xml: String): Map<String, List<EditorChoice>> = buildMap {
    listRegex.findAll(xml).forEach { match ->
      val listName = match.groupValues[1].trim()
      val listBody = match.groupValues[2]
      val choices =
        itemRegex
          .findAll(listBody)
          .mapNotNull { itemMatch ->
            val itemBody = itemMatch.groupValues[1]
            val code = codeRegex.find(itemBody)?.groupValues?.get(1)?.trim().orEmpty()
            val label = labelRegex.find(itemBody)?.groupValues?.get(1)?.trim().orEmpty()
            if (code.isBlank() && label.isBlank()) {
              null
            } else {
              EditorChoice(
                value = code.ifBlank { label },
                label = label.ifBlank { code },
                code = code,
              )
            }
          }
          .toList()
      if (choices.isNotEmpty()) {
        put(listName, choices)
      }
    }
  }

  private fun parseQuestions(
    schemaBody: String,
    codeLists: Map<String, List<EditorChoice>>,
  ): List<EditorQuestion> {
    val questions = mutableListOf<EditorQuestion>()
    var keyIdx = 1
    attributeRegex.findAll(schemaBody).forEach { match ->
      val tag = match.groupValues[1]
      val attrs = match.groupValues[2]
      val body = match.groupValues[3]
      val name = nameAttrRegex.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
      if (name.isBlank()) return@forEach
      val label = labelRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { name }
      val hint = descriptionRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
      val isRequired = requiredAttrRegex.find(attrs)?.groupValues?.get(1) == "true"
      val key = "cep_${keyIdx++}_$name"
      val question =
        when (tag) {
          "text" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.TEXT,
              label = label,
              hint = hint,
              required = isRequired,
            )
          "number" -> {
            val numType = typeAttrRegex.find(attrs)?.groupValues?.get(1).orEmpty()
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.NUMBER,
              label = label,
              hint = hint,
              required = isRequired,
              decimalPlaces =
                if (numType.equals("real", ignoreCase = true)) {
                  EditorQuestion.DEFAULT_DECIMAL_PLACES
                } else {
                  0
                },
            )
          }
          "code" -> {
            val listName = listAttrRegex.find(attrs)?.groupValues?.get(1).orEmpty()
            val isMultiple = multipleAttrRegex.find(attrs)?.groupValues?.get(1) == "true"
            val choices =
              codeLists[listName]
                ?: listOf(
                  EditorChoice(value = "option_1", label = "Option 1", code = "1"),
                  EditorChoice(value = "option_2", label = "Option 2", code = "2"),
                )
            EditorQuestion(
              key = key,
              name = name,
              type =
                if (isMultiple) EditorQuestionType.SELECT_MULTIPLE
                else EditorQuestionType.SELECT_ONE,
              label = label,
              hint = hint,
              required = isRequired,
              choices = choices,
            )
          }
          "boolean" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.SELECT_ONE,
              label = label,
              hint = hint,
              required = isRequired,
              choices =
                listOf(
                  EditorChoice(value = "yes", label = "Yes", code = "yes"),
                  EditorChoice(value = "no", label = "No", code = "no"),
                ),
            )
          "date" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.DATE,
              label = label,
              hint = hint,
              required = isRequired,
            )
          "coordinate" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.LOCATION,
              label = label,
              hint = hint,
              required = isRequired,
            )
          "file" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.PHOTO,
              label = label,
              hint = hint,
              required = isRequired,
            )
          else -> null
        }
      if (question != null) {
        questions.add(question)
      }
    }
    return questions
  }
}
