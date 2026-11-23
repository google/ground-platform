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

import org.openforis.collectone.v2.app.domain.model.EntityStatusFilterOption
import org.openforis.collectone.v2.core.forms.model.FormDefinition

/**
 * Reads status marker rules ([EditorStatusConfig]) back from the status label, marker symbol, and
 * marker color `calculate` expressions of a Form, so the rules survive publishing and syncing (the
 * editor draft is derived again from the published Forms). The inverse of
 * [SaveToRules.statusCalculateExpression]: nested `if(<condition>, '<value>', <else>)` chains whose
 * conditions are the ones [SaveToRules.statusConditionExpression] writes.
 */
internal object StatusRulesImport {
  private val VERSION_AT_LEAST =
    Regex("""number\(instance\('[^']*'\)/root/item\[name = .+]/__version\) >= (\d+)""")
  private val QUESTION_REF = Regex("""/data/([A-Za-z_][A-Za-z0-9_.-]*)""")
  private val PROPERTY_REF =
    Regex("""instance\('[^']*'\)/root/item\[name = .+]/([A-Za-z_][A-Za-z0-9_.-]*)""")

  /** Comparison operators, longest first so `!=` isn't read as `=`. */
  private val OPERATORS =
    listOf(
      " != " to RelevanceOperator.NOT_EQUALS,
      " = " to RelevanceOperator.EQUALS,
      " > " to RelevanceOperator.GREATER_THAN,
      " < " to RelevanceOperator.LESS_THAN,
    )

  /**
   * The status rules behind the [label], [symbol], and [color] expressions, or `null` when they
   * aren't in the shape the editor writes. [questionKey] finds the key of the question a
   * `/data/<name>` reference names.
   */
  fun parse(
    label: String,
    symbol: String,
    color: String,
    questionKey: (name: String) -> String?,
  ): EditorStatusConfig? {
    val labels = chain(label) ?: return null
    val symbols = chain(symbol) ?: return null
    val colors = chain(color) ?: return null
    if (symbols.conditions != labels.conditions || colors.conditions != labels.conditions) {
      return null
    }
    val rules =
      labels.conditions.mapIndexed { i, condition ->
        val rule = rule(condition, questionKey) ?: return null
        rule.copy(badge = badge(symbols.values[i], colors.values[i], labels.values[i]))
      }
    return EditorStatusConfig(
      enabled = true,
      rules = rules,
      defaultBadge = badge(symbols.fallback, colors.fallback, labels.fallback),
    )
  }

  /**
   * Distinct workflow status options configured in [formDefinition]'s `__status` and
   * `__marker_symbol` calculate bindings, or empty when [formDefinition] defines no status chain.
   */
  fun statusOptionsOf(formDefinition: FormDefinition): List<EntityStatusFilterOption> {
    val model = formDefinition.proto.model ?: return emptyList()
    val rootName = model.primary_instance?.record_schema?.name?.ifBlank { "data" } ?: "data"
    fun calc(field: String): String =
      model.bindings
        .firstOrNull {
          it.field_path == field ||
            it.field_path == "/$rootName/$field" ||
            it.field_path.endsWith("/$field")
        }
        ?.calculate_expression
        .orEmpty()
    val labelExpr = calc(SaveToRules.STATUS_FIELD)
    if (labelExpr.isBlank()) return emptyList()
    val labels = chain(labelExpr) ?: return emptyList()
    val symbols = chain(calc(SaveToRules.MARKER_SYMBOL_FIELD))
    val pairs = buildList {
      add(labels.fallback to symbols?.fallback.orEmpty())
      labels.values.forEachIndexed { index, statusLabel ->
        add(statusLabel to symbols?.values?.getOrNull(index).orEmpty())
      }
    }
    return pairs
      .mapNotNull { (rawLabel, rawSymbol) ->
        val status = rawLabel.trim().ifEmpty { rawSymbol.trim() }
        if (status.isEmpty()) {
          null
        } else {
          EntityStatusFilterOption(status = status, symbol = rawSymbol.trim())
        }
      }
      .distinctBy { it.status.lowercase() }
  }

  private fun badge(symbol: String, color: String, label: String) =
    EditorStatusBadge(StatusMarkerSymbol.fromSymbol(symbol), color, label)

  /** The conditions of an `if()` chain, the value each one selects, and the final fallback. */
  private class Chain(val conditions: List<String>, val values: List<String>, val fallback: String)

  private fun chain(expression: String): Chain? {
    val conditions = mutableListOf<String>()
    val values = mutableListOf<String>()
    var rest = expression.trim()
    while (rest.startsWith("if(") && rest.endsWith(")")) {
      val args = splitArguments(rest.substring(3, rest.length - 1)) ?: return null
      if (args.size != 3) return null
      conditions += args[0]
      values += unquote(args[1]) ?: return null
      rest = args[2]
    }
    return Chain(conditions, values, unquote(rest) ?: return null)
  }

  /** The rule [condition] was written for, without its badge, or `null` if it isn't recognized. */
  private fun rule(condition: String, questionKey: (String) -> String?): EditorStatusRule? {
    when (condition) {
      "true()" -> return EditorStatusRule(StatusConditionSubject.SUBMISSIONS, minSubmissions = 1)
      // More than one submission can't match a Form that adds features.
      "false()" -> return EditorStatusRule(StatusConditionSubject.SUBMISSIONS, minSubmissions = 2)
    }
    VERSION_AT_LEAST.matchEntire(condition)?.let { match ->
      val count = match.groupValues[1].toIntOrNull() ?: return null
      return EditorStatusRule(StatusConditionSubject.SUBMISSIONS, minSubmissions = count)
    }
    if (condition.startsWith("selected(") && condition.endsWith(")")) {
      val args = splitArguments(condition.substring(9, condition.length - 1)) ?: return null
      if (args.size != 2) return null
      val value = unquote(args[1]) ?: return null
      return comparing(args[0], RelevanceOperator.INCLUDES, value, questionKey)
    }
    val (left, operator, right) = comparison(condition) ?: return null
    if (
      operator == RelevanceOperator.GREATER_THAN &&
        right == "0" &&
        left.startsWith("string-length(") &&
        left.endsWith(")")
    ) {
      val ref = left.substring(14, left.length - 1)
      return comparing(ref, RelevanceOperator.IS_ANSWERED, "", questionKey)
    }
    val value = unquote(right) ?: right.takeIf { it.toDoubleOrNull() != null } ?: return null
    return comparing(left, operator, value, questionKey)
  }

  /** A rule comparing the question or feature property [ref] reads with [value]. */
  private fun comparing(
    ref: String,
    operator: RelevanceOperator,
    value: String,
    questionKey: (String) -> String?,
  ): EditorStatusRule? {
    QUESTION_REF.matchEntire(ref)?.let { match ->
      val key = questionKey(match.groupValues[1]) ?: return null
      return EditorStatusRule(
        subject = StatusConditionSubject.QUESTION,
        questionKey = key,
        operator = operator,
        value = value,
      )
    }
    PROPERTY_REF.matchEntire(ref)?.let { match ->
      val column = match.groupValues[1]
      // The `name` and `label` columns stand for properties the Form doesn't name.
      if (column == "name" || column == "label") return null
      return EditorStatusRule(
        subject = StatusConditionSubject.ENTITY_PROPERTY,
        property = column,
        operator = operator,
        value = value,
      )
    }
    return null
  }

  /** [condition] split at its top-level comparison operator, or `null` if it has none. */
  private fun comparison(condition: String): Triple<String, RelevanceOperator, String>? {
    var depth = 0
    var quote: Char? = null
    condition.forEachIndexed { i, c ->
      when {
        quote != null -> if (c == quote) quote = null
        c == '\'' || c == '"' -> quote = c
        c == '(' || c == '[' -> depth++
        c == ')' || c == ']' -> depth--
        depth == 0 ->
          OPERATORS.firstOrNull { (token, _) -> condition.startsWith(token, i) }
            ?.let { (token, operator) ->
              return Triple(
                condition.substring(0, i),
                operator,
                condition.substring(i + token.length),
              )
            }
      }
    }
    return null
  }

  /**
   * Top-level comma-separated arguments of [text], trimmed, or `null` if its brackets or quotes
   * aren't balanced. XPath 1.0 section 3.7: literals can't contain their own quote character.
   */
  private fun splitArguments(text: String): List<String>? {
    val parts = mutableListOf<String>()
    var depth = 0
    var quote: Char? = null
    var start = 0
    text.forEachIndexed { i, c ->
      when {
        quote != null -> if (c == quote) quote = null
        c == '\'' || c == '"' -> quote = c
        c == '(' || c == '[' -> depth++
        c == ')' || c == ']' -> if (--depth < 0) return null
        c == ',' && depth == 0 -> {
          parts += text.substring(start, i).trim()
          start = i + 1
        }
      }
    }
    if (depth != 0 || quote != null) return null
    parts += text.substring(start).trim()
    return parts
  }

  /** The text of XPath string literal [text], or `null` if it isn't one. */
  private fun unquote(text: String): String? =
    if (text.length >= 2 && (text[0] == '\'' || text[0] == '"') && text.last() == text[0]) {
      text.substring(1, text.length - 1)
    } else {
      null
    }
}
