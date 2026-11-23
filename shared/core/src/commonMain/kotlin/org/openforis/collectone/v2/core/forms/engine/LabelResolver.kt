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
package org.openforis.collectone.v2.core.forms.engine

import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.TranslationCatalog
import org.openforis.collectone.v2.core.forms.model.ResolvedLabel
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext

/**
 * Resolves [LabelDef]s into display text in the active language: looks up `jr:itext` entries
 * (including their short, guidance and media forms) and interpolates `<output>` values.
 */
internal class LabelResolver(
  private val compiled: CompiledForm,
  private val activeLanguage: String,
) {

  /** Resolves [labelDef] at [evalContext], or returns null when it has nothing to show. */
  fun resolve(labelDef: LabelDef?, evalContext: EvaluationContext): ResolvedLabel? {
    if (labelDef == null) return null
    val localized =
      if (labelDef.text_id.isNotEmpty()) {
        lookupLocalizedString(
          catalog = compiled.formDef.model?.translations,
          activeLanguage = activeLanguage,
          textId = labelDef.text_id,
        )
      } else {
        null
      }

    val rawPrimary = localized?.value_?.takeIf { it.isNotEmpty() } ?: labelDef.text
    val rawShort = localized?.short_value?.takeIf { it.isNotEmpty() }
    val rawGuidance = localized?.guidance_value?.takeIf { it.isNotEmpty() }
    val media = localized?.media

    if (
      rawPrimary.isEmpty() &&
        rawShort == null &&
        rawGuidance == null &&
        media == null &&
        labelDef.outputs.isEmpty()
    ) {
      return null
    }

    val interpolatedPrimary = interpolateOutputs(rawPrimary, labelDef, evalContext)
    val interpolatedShort = rawShort?.let {
      interpolateOutputs(it, labelDef, evalContext)
    }
    val interpolatedGuidance = rawGuidance?.let {
      interpolateOutputs(it, labelDef, evalContext)
    }

    return ResolvedLabel(
      text = interpolatedPrimary,
      shortText = interpolatedShort,
      guidanceText = interpolatedGuidance,
      media = media,
    )
  }

  private fun interpolateOutputs(
    template: String,
    labelDef: LabelDef,
    evalContext: EvaluationContext,
  ): String {
    var result = template
    for ((idx, fragment) in labelDef.outputs.withIndex()) {
      val expr = compiled.compileExpression(fragment.value_expression) ?: continue
      val evaluatedStr = expr.evaluateString(evalContext)
      val pid = fragment.placeholder_id
      if (pid.isNotEmpty()) {
        result = result.replace("\${$pid}", evaluatedStr).replace("{$pid}", evaluatedStr)
        if (pid.startsWith("{") || pid.startsWith("$")) {
          result = result.replace(pid, evaluatedStr)
        }
      }
      result = result.replace("{$idx}", evaluatedStr)
    }
    return result
  }
}

/**
 * Looks up [textId] in [catalog], preferring [activeLanguage], then the default language, then the
 * first language, and finally any language that defines the id.
 */
internal fun lookupLocalizedString(
  catalog: TranslationCatalog?,
  activeLanguage: String,
  textId: String,
): LocalizedString? {
  if (catalog == null || catalog.languages.isEmpty() || textId.isEmpty()) return null
  val languages = catalog.languages
  val selectedLang =
    languages.find { it.language.equals(activeLanguage, ignoreCase = true) }
      ?: languages.find { it.is_default }
      ?: languages.first()
  return selectedLang.strings[textId] ?: languages.firstNotNullOfOrNull { it.strings[textId] }
}
