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

import kotlin.math.PI
import kotlin.math.cos
import org.openforis.collectone.v2.app.domain.model.FormAvailability

/**
 * A Collect Earth project imported from a `.cep` archive or `.idm.xml` schema, including its
 * [importedForm], survey metadata ([projectTitle], [description], [languages]), and optional
 * [plotDataset] and [surveyArea] extracted from the `.cep` archive's sample plot CSV grid.
 */
data class CollectEarthProjectImport(
  val fileName: String,
  val projectTitle: String,
  val description: String = "",
  val languages: List<String> = listOf("en"),
  val importedForm: ImportedForm,
  val plotDataset: EntityDataset? = null,
  val surveyArea: SurveyArea? = null,
)

/**
 * Parses Open Foris Collect / Collect Earth (`.cep` / `.idm.xml` / `idml.xml`) survey schema
 * definitions (`<survey>` or `<idm>` root elements with `<codeLists>` and `<schema>`) and bundled
 * `.cep` archives into [CollectEarthProjectImport] and [ImportedForm] models.
 */
object CollectEarthIdmParser {
  const val PROJECT_SEPARATOR = "<!--COLLECT_EARTH_PROJECT_SEPARATOR-->"
  private val fileNameCommentRegex = Regex("""<!--COLLECT_EARTH_FILE_NAME:(.*?)-->""")
  private val propertiesCommentRegex = Regex("""<!--COLLECT_EARTH_PROPERTIES\n([\s\S]*?)\n-->""")
  private val plotCsvCommentRegex = Regex("""<!--COLLECT_EARTH_PLOT_CSV\n([\s\S]*?)\n-->""")

  private val projectRegex = Regex("""<project[^>]*>([^<]+)</project>""")
  private val topDescriptionRegex = Regex("""<description[^>]*>([\s\S]*?)</description>""")
  private val languageRegex = Regex("""<language[^>]*>([a-zA-Z]{2,3})</language>""")
  private val listRegex = Regex("""<list\s+[^>]*name="([^"]+)"[^>]*>([\s\S]*?)</list>""")
  private val itemRegex = Regex("""<item(?:\s[^>]*)?>([\s\S]*?)</item>""")
  private val codeRegex = Regex("""<code(?:\s[^>]*)?>([^<]+)</code>""")
  private val labelTagRegex = Regex("""<label(\s[^>]*)?>([^<]*)</label>""")
  private val xmlLangAttrRegex = Regex("""\bxml:lang="([^"]+)"""")
  private val labelTypeAttrRegex = Regex("""\btype="([^"]+)"""")
  private val schemaRegex = Regex("""<schema[^>]*>([\s\S]*?)</schema>""")
  private val attributeRegex =
    Regex(
      """<(text|number|code|boolean|date|time|coordinate|range|taxon|file)\s+([^>]*?)(?:/>|>([\s\S]*?)</\1>)"""
    )
  private val nameAttrRegex = Regex("""\bname="([^"]+)"""")
  private val listAttrRegex = Regex("""\blist="([^"]+)"""")
  private val multipleAttrRegex = Regex("""\bmultiple="([^"]+)"""")
  private val requiredAttrRegex = Regex("""\brequired="([^"]+)"""")
  private val typeAttrRegex = Regex("""\btype="([^"]+)"""")
  private val relevantAttrRegex = Regex("""\brelevant="([^"]+)"""")
  private val uiHideAttrRegex = Regex("""\bui:hide="([^"]+)"""")
  private val calculatedAttrRegex = Regex("""\bcalculated="([^"]+)"""")
  private val fromCsvAttrRegex = Regex("""\bcollectearth:fromcsv="([^"]+)"""")
  private val keyAttrRegex = Regex("""\bkey="([^"]+)"""")
  private val descriptionRegex = Regex("""<description[^>]*>([^<]+)</description>""")

  private val SYSTEM_PLOT_ATTRIBUTES =
    setOf("actively_saved", "actively_saved_on", "plot_file", "operator")

  /**
   * Parses [xml] as a Collect Earth / Open Foris IDM definition (or the first project in a `.cep` /
   * `.zip` bundle), returning `null` when [xml] is not an IDM schema or contains no recognizable
   * questions.
   */
  fun fromXml(
    xml: String,
    formId: String = "",
    fallbackTitle: String = "",
    availability: FormAvailability = FormAvailability.BOTH,
  ): ImportedForm? =
    parseProjects(
        xml = xml,
        fallbackFileName = fallbackTitle,
        formId = formId,
        availability = availability,
      )
      .firstOrNull()
      ?.importedForm

  /**
   * Parses all Collect Earth projects present in [xml] (either a single `.idm.xml` / `.cep` project
   * or a multi-`.cep` archive separated by [PROJECT_SEPARATOR]).
   */
  fun parseProjects(
    xml: String,
    fallbackFileName: String = "",
    formId: String = "",
    availability: FormAvailability = FormAvailability.BOTH,
  ): List<CollectEarthProjectImport> {
    val segments =
      if (xml.contains(PROJECT_SEPARATOR)) {
        xml.split(PROJECT_SEPARATOR)
      } else {
        listOf(xml)
      }
    return segments.mapNotNull { segment ->
      parseSingleProject(
        segment = segment,
        fallbackFileName = fallbackFileName,
        formId = formId,
        availability = availability,
      )
    }
  }

  private fun parseSingleProject(
    segment: String,
    fallbackFileName: String,
    formId: String,
    availability: FormAvailability,
  ): CollectEarthProjectImport? {
    val trimmed = segment.trim()
    if (!trimmed.contains("<survey") && !trimmed.contains("<idm")) return null

    val embeddedFileName = fileNameCommentRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
    val effectiveFileName = embeddedFileName.ifBlank { fallbackFileName }
    val fallbackTitle =
      effectiveFileName
        .removeSuffix(".zip")
        .removeSuffix(".cep")
        .removeSuffix(".idm.xml")
        .removeSuffix(".xml")
        .ifBlank { "Imported form" }

    val propertiesText = propertiesCommentRegex.find(trimmed)?.groupValues?.get(1).orEmpty()
    val plotCsvText = plotCsvCommentRegex.find(trimmed)?.groupValues?.get(1).orEmpty()

    val projectTitle =
      projectRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { fallbackTitle }

    val headerBoundary =
      listOf("<codeLists", "<spatialReferenceSystems", "<referenceDataSchema", "<schema")
        .mapNotNull { marker -> trimmed.indexOf(marker).takeIf { it >= 0 } }
        .minOrNull() ?: trimmed.length
    val surveyHeader = trimmed.substring(0, headerBoundary)
    val surveyDescription =
      topDescriptionRegex.find(surveyHeader)?.groupValues?.get(1)?.trim().orEmpty()
    val languages =
      languageRegex
        .findAll(surveyHeader)
        .map { it.groupValues[1].trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
        .ifEmpty { listOf("en") }

    val codeLists = parseCodeLists(trimmed)
    val schemaBody = schemaRegex.find(trimmed)?.groupValues?.get(1) ?: trimmed
    val questions = parseQuestions(schemaBody, codeLists)
    if (questions.isEmpty()) return null

    val resolvedFormId = formId.ifBlank { slugify(projectTitle) }.ifBlank { FormIds.newFormId() }
    val resolvedTitle = projectTitle.ifBlank { resolvedFormId }

    val (plotDataset, surveyArea) =
      parsePlotGridCsv(
        csvText = plotCsvText,
        propertiesText = propertiesText,
        projectTitle = resolvedTitle,
      )

    val notes = buildList {
      add("Imported from Collect Earth (.cep / Open Foris IDM) schema.")
      if (plotDataset != null) {
        add("Loaded ${plotDataset.rows.size} predefined sample plots from Collect Earth grid.")
      }
    }

    val importedForm =
      ImportedForm(
        form =
          EditorForm(
            formId = resolvedFormId,
            title = resolvedTitle,
            questions = questions,
            availability = availability,
          ),
        notes = notes,
      )

    return CollectEarthProjectImport(
      fileName = effectiveFileName.ifBlank { "$resolvedFormId.cep" },
      projectTitle = resolvedTitle,
      description = surveyDescription,
      languages = languages,
      importedForm = importedForm,
      plotDataset = plotDataset,
      surveyArea = surveyArea,
    )
  }

  private fun parseLabels(xmlFragment: String): Pair<String, Map<String, String>> {
    var defaultLabel = ""
    var fallbackLabel = ""
    val translations = mutableMapOf<String, String>()
    labelTagRegex.findAll(xmlFragment).forEach { match ->
      val attrs = match.groupValues[1]
      val text = match.groupValues[2].trim()
      if (text.isEmpty()) return@forEach
      val lang = xmlLangAttrRegex.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()
      val labelType = labelTypeAttrRegex.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()
      if (lang == null || lang == "en") {
        if (labelType != "number" && defaultLabel.isEmpty()) {
          defaultLabel = text
        } else if (fallbackLabel.isEmpty()) {
          fallbackLabel = text
        }
      } else if (labelType != "number" && lang !in translations) {
        translations[lang] = text
      }
    }
    return (defaultLabel.ifEmpty { fallbackLabel }) to translations
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
            val (label, translations) = parseLabels(itemBody)
            if (code.isBlank() && label.isBlank()) {
              null
            } else {
              EditorChoice(
                value = code.ifBlank { label },
                label = label.ifBlank { code },
                code = code,
                labelTranslations = translations,
              )
            }
          }
          .toList()
      if (choices.isNotEmpty()) {
        put(listName, choices)
      }
    }
  }

  private data class ParsedAttributeCandidate(
    val question: EditorQuestion,
    val rawRelevant: String,
    val isBoolean: Boolean,
    val isSystemOrCsvField: Boolean,
  )

  private fun parseQuestions(
    schemaBody: String,
    codeLists: Map<String, List<EditorChoice>>,
  ): List<EditorQuestion> {
    val candidates = mutableListOf<ParsedAttributeCandidate>()
    var keyIdx = 1
    attributeRegex.findAll(schemaBody).forEach { match ->
      val tag = match.groupValues[1]
      val attrs = match.groupValues[2]
      val body = match.groupValues[3]
      val name = nameAttrRegex.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
      if (name.isBlank()) return@forEach

      val relevantExpr = relevantAttrRegex.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
      val isHidden =
        relevantExpr.equals("false()", ignoreCase = true) ||
          relevantExpr.equals("false", ignoreCase = true) ||
          uiHideAttrRegex.find(attrs)?.groupValues?.get(1)?.trim() == "true" ||
          calculatedAttrRegex.find(attrs)?.groupValues?.get(1)?.trim() == "true"
      if (isHidden) return@forEach

      val isFromCsv = fromCsvAttrRegex.find(attrs)?.groupValues?.get(1)?.trim() == "true"
      val isKey = keyAttrRegex.find(attrs)?.groupValues?.get(1)?.trim() == "true"
      val isSystemOrCsv =
        isFromCsv ||
          name in SYSTEM_PLOT_ATTRIBUTES ||
          (name == "id" && isKey) ||
          (name == "location" && tag == "coordinate")

      val (parsedLabel, labelTranslations) = parseLabels(body)
      val label = parsedLabel.ifBlank { name }
      val hint = descriptionRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
      val isRequired = requiredAttrRegex.find(attrs)?.groupValues?.get(1) == "true"
      val key = "cep_${keyIdx++}_$name"

      val question =
        when (tag) {
          "text",
          "time",
          "taxon" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.TEXT,
              label = label,
              hint = hint,
              required = isRequired,
              labelTranslations = labelTranslations,
            )
          "number",
          "range" -> {
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
              labelTranslations = labelTranslations,
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
              labelTranslations = labelTranslations,
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
              labelTranslations = labelTranslations,
            )
          "date" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.DATE,
              label = label,
              hint = hint,
              required = isRequired,
              labelTranslations = labelTranslations,
            )
          "coordinate" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.LOCATION,
              label = label,
              hint = hint,
              required = isRequired,
              labelTranslations = labelTranslations,
            )
          "file" ->
            EditorQuestion(
              key = key,
              name = name,
              type = EditorQuestionType.PHOTO,
              label = label,
              hint = hint,
              required = isRequired,
              labelTranslations = labelTranslations,
            )
          else -> null
        }

      if (question != null) {
        candidates.add(
          ParsedAttributeCandidate(
            question = question,
            rawRelevant = relevantExpr,
            isBoolean = tag == "boolean",
            isSystemOrCsvField = isSystemOrCsv,
          )
        )
      }
    }

    val activeCandidates = candidates.filterNot { it.isSystemOrCsvField }.ifEmpty { candidates }
    val byName = mutableMapOf<String, ParsedAttributeCandidate>()
    return activeCandidates.map { candidate ->
      val resolvedRelevance = resolveIdmRelevance(candidate.rawRelevant, byName)
      val updated =
        if (resolvedRelevance != null) {
          candidate.question.copy(relevance = resolvedRelevance)
        } else {
          candidate.question
        }
      byName[updated.name] = candidate.copy(question = updated)
      updated
    }
  }

  private fun resolveIdmRelevance(
    rawExpr: String,
    earlierByName: Map<String, ParsedAttributeCandidate>,
  ): EditorRelevance? {
    val expr = rawExpr.trim()
    if (expr.isEmpty()) return null

    // Handle `not(field_name)`
    val notMatch = Regex("""^not\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)$""").matchEntire(expr)
    if (notMatch != null) {
      val src = earlierByName[notMatch.groupValues[1]] ?: return null
      return if (src.isBoolean) {
        EditorRelevance(
          sourceQuestionKey = src.question.key,
          operator = RelevanceOperator.EQUALS,
          value = "no",
        )
      } else {
        null
      }
    }

    // Handle `field_name = 'val'` or `field_name != 'val'`
    val cmpMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(!=|=)\s*(.+)$""").matchEntire(expr)
    if (cmpMatch != null) {
      val src = earlierByName[cmpMatch.groupValues[1]] ?: return null
      val opToken = cmpMatch.groupValues[2]
      val rawLiteral = cmpMatch.groupValues[3].trim().removeSurrounding("'").removeSurrounding("\"")
      val literal =
        when (rawLiteral.lowercase()) {
          "true()",
          "true",
          "1" -> if (src.isBoolean) "yes" else rawLiteral
          "false()",
          "false",
          "0" -> if (src.isBoolean) "no" else rawLiteral
          else -> rawLiteral
        }
      val operator = if (opToken == "!=") RelevanceOperator.NOT_EQUALS else RelevanceOperator.EQUALS
      return EditorRelevance(
        sourceQuestionKey = src.question.key,
        operator = operator,
        value = literal,
      )
    }

    // Handle bare identifier `forest_loss`
    val identMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)$""").matchEntire(expr)
    if (identMatch != null) {
      val src = earlierByName[identMatch.groupValues[1]] ?: return null
      return if (src.isBoolean) {
        EditorRelevance(
          sourceQuestionKey = src.question.key,
          operator = RelevanceOperator.EQUALS,
          value = "yes",
        )
      } else {
        EditorRelevance(
          sourceQuestionKey = src.question.key,
          operator = RelevanceOperator.IS_ANSWERED,
        )
      }
    }
    return null
  }

  private fun parsePlotGridCsv(
    csvText: String,
    propertiesText: String,
    projectTitle: String,
  ): Pair<EntityDataset?, SurveyArea?> {
    val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return null to null

    val headers = parseCsvRow(lines.first())
    val idIdx = headers.indexOfFirst { it.equals("id", ignoreCase = true) }.takeIf { it >= 0 } ?: 0
    val latIdx =
      headers
        .indexOfFirst {
          it.equals("YCoordinate", ignoreCase = true) ||
            it.equals("latitude", ignoreCase = true) ||
            it.equals("lat", ignoreCase = true) ||
            it.equals("y", ignoreCase = true)
        }
        .takeIf { it >= 0 } ?: 1
    val lngIdx =
      headers
        .indexOfFirst {
          it.equals("XCoordinate", ignoreCase = true) ||
            it.equals("longitude", ignoreCase = true) ||
            it.equals("lon", ignoreCase = true) ||
            it.equals("lng", ignoreCase = true) ||
            it.equals("x", ignoreCase = true)
        }
        .takeIf { it >= 0 } ?: 2

    val extraAttrIndices =
      headers.indices.filter { idx -> idx != idIdx && idx != latIdx && idx != lngIdx }
    val extraProperties = extraAttrIndices.map { idx ->
      val rawName = headers[idx].trim().ifBlank { "attr_$idx" }
      val cleanName = slugify(rawName).ifBlank { "attr_$idx" }
      val displayLabel =
        rawName.replace('_', ' ').replaceFirstChar {
          if (it.isLowerCase()) it.titlecase() else it.toString()
        }
      idx to EntityProperty(name = cleanName, label = displayLabel, type = PropertyType.TEXT)
    }

    val halfSideMeters =
      Regex("""^distance_to_plot_boundaries\s*=\s*(\d+(?:\.\d+)?)""", RegexOption.MULTILINE)
        .find(propertiesText)
        ?.groupValues
        ?.get(1)
        ?.toDoubleOrNull()
        ?.coerceIn(10.0, 500.0) ?: 35.0

    val rows = mutableListOf<EntityRow>()
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY

    lines.drop(1).take(150).forEachIndexed { idx, line ->
      val cols = parseCsvRow(line)
      if (cols.size <= maxOf(latIdx, lngIdx)) return@forEachIndexed
      val lat = cols[latIdx].toDoubleOrNull() ?: return@forEachIndexed
      val lng = cols[lngIdx].toDoubleOrNull() ?: return@forEachIndexed
      if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@forEachIndexed

      val rawId = cols.getOrNull(idIdx)?.trim().orEmpty().ifBlank { "${idx + 1}" }
      minLat = minOf(minLat, lat)
      maxLat = maxOf(maxLat, lat)
      minLng = minOf(minLng, lng)
      maxLng = maxOf(maxLng, lng)

      val dLat = halfSideMeters / 111_320.0
      val cosLat = cos(lat * PI / 180.0).coerceAtLeast(0.01)
      val dLng = halfSideMeters / (111_320.0 * cosLat)
      val squareRing =
        listOf(
          LatLng(lat - dLat, lng - dLng),
          LatLng(lat - dLat, lng + dLng),
          LatLng(lat + dLat, lng + dLng),
          LatLng(lat + dLat, lng - dLng),
        )

      val values = buildMap {
        put("id", rawId)
        extraProperties.forEach { (colIdx, prop) ->
          val cell = cols.getOrNull(colIdx)?.trim().orEmpty()
          if (cell.isNotEmpty()) put(prop.name, cell)
        }
      }
      rows.add(
        EntityRow(
          key = "cep_plot_${idx + 1}",
          values = values,
          geometry = squareRing,
        )
      )
    }

    if (rows.isEmpty()) return null to null

    val latPad = ((maxLat - minLat) * 0.04).coerceAtLeast(0.02)
    val lngPad = ((maxLng - minLng) * 0.04).coerceAtLeast(0.02)
    val bMinLat = (minLat - latPad).coerceIn(-85.0, 85.0)
    val bMaxLat = (maxLat + latPad).coerceIn(-85.0, 85.0)
    val bMinLng = (minLng - lngPad).coerceIn(-179.9, 179.9)
    val bMaxLng = (maxLng + lngPad).coerceIn(-179.9, 179.9)
    val areaRing =
      listOf(
        LatLng(bMinLat, bMinLng),
        LatLng(bMinLat, bMaxLng),
        LatLng(bMaxLat, bMaxLng),
        LatLng(bMaxLat, bMinLng),
      )
    val surveyArea =
      SurveyArea(
        name = "$projectTitle study area",
        parts = listOf(areaRing),
        sourceLabel = "Collect Earth plot grid (${rows.size} plots)",
      )

    val dataset =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        description = "Predefined sample plots imported from Collect Earth (.cep) grid.",
        geometryKind = GeometryKind.POLYGON,
        keyProperty = "id",
        labelProperty = "id",
        fieldCreationEnabled = false,
        properties =
          listOf(EntityProperty("id", "Plot ID", PropertyType.TEXT, required = true)) +
            extraProperties.map { it.second },
        rows = rows,
        style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.18),
      )

    return dataset to surveyArea
  }

  private fun parseCsvRow(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val c = line[i]
      when {
        c == '"' -> {
          if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
            sb.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        c == ',' && !inQuotes -> {
          result.add(sb.toString().trim())
          sb.clear()
        }
        else -> sb.append(c)
      }
      i++
    }
    result.add(sb.toString().trim())
    return result
  }
}
