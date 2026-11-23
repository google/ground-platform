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

import kotlin.math.roundToLong
import kotlin.random.Random
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ItemsetDef
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption
import org.openforis.collectone.v2.core.forms.model.ResolvedLabel
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/**
 * Lists the choices of a select control: its static `<item>`s, or the rows of an `itemset` over a
 * secondary instance, filtered by the nodeset predicate and optionally shuffled
 * (`randomize="true()"`).
 */
internal class ChoiceOptionsResolver(
  private val pass: EvaluationPass,
  private val labels: LabelResolver,
) {

  private val compiled = pass.compiled
  private val activeLanguage = pass.activeLanguage
  private val environment = pass.environment

  /** Resolves the options of [control], evaluating itemset expressions at [controlContext]. */
  fun resolve(control: ControlDef, controlContext: EvaluationContext): List<ResolvedChoiceOption> {
    val itemset = control.itemset
    if (itemset != null && itemset.instance_id.isNotEmpty()) {
      return evaluateItemsetOptions(
        itemset = itemset,
        controlContext = controlContext,
      )
    }

    // Static choices from ControlDef.choices
    return control.choices.map { item ->
      val label = labels.resolve(item.label, controlContext) ?: ResolvedLabel(text = item.value_)
      val parentVal =
        item.parent_value.ifEmpty {
          item.properties["parent_value"] ?: item.properties["parent"] ?: ""
        }
      ResolvedChoiceOption(
        value = item.value_,
        label = label,
        properties = item.properties,
        parentValue = parentVal,
      )
    }
  }

  private fun evaluateItemsetOptions(
    itemset: ItemsetDef,
    controlContext: EvaluationContext,
  ): List<ResolvedChoiceOption> {
    val instanceRoot =
      controlContext.secondaryInstanceProvider.resolveRoot(itemset.instance_id)
        ?: return emptyList()
    val allItems =
      if (itemset.nodeset_path.isNotEmpty()) {
        val segments = itemset.nodeset_path.split('/').filter { it.isNotEmpty() }
        var currentNodes = listOf(instanceRoot)
        for (seg in segments) {
          currentNodes = currentNodes.flatMap { it.children(seg) }
        }
        currentNodes.ifEmpty {
          instanceRoot
            .children("root")
            .flatMap { it.children("item") }
            .ifEmpty { instanceRoot.children("item") }
        }
      } else {
        instanceRoot
          .children("root")
          .flatMap { it.children("item") }
          .ifEmpty { instanceRoot.children("item") }
      }

    val filterExpr = compiled.compileExpression(itemset.nodeset_filter)
    val filteredItems =
      if (filterExpr == null) {
        allItems
      } else {
        val total = allItems.size
        allItems.filterIndexed { idx, candidateNode ->
          val itemEvalCtx =
            controlContext.copy(
              contextNode = candidateNode,
              currentQuestionNode = controlContext.contextNode,
              contextPosition = idx + 1,
              contextSize = total,
            )
          filterExpr.evaluateBoolean(itemEvalCtx)
        }
      }

    val options = filteredItems.map { itemNode ->
      val valStr =
        if (itemset.value_ref.isNotEmpty()) {
          itemNode.children(itemset.value_ref).firstOrNull()?.extractValue()?.toXPathString() ?: ""
        } else {
          itemNode.extractValue().toXPathString()
        }

      val rawLabelRef =
        if (itemset.label_ref.isNotEmpty()) {
          if (itemset.label_ref.startsWith("jr:itext(") && itemset.label_ref.endsWith(")")) {
            // ODK XForms spec, "Itemsets": in `<label ref="jr:itext(itextId)"/>` the argument is
            // evaluated relative to each item and yields an itext id (pyxform emits this for media
            // choices). Resolve only the argument here so the translation lookup below keeps the
            // entry's media (e.g. choice images); evaluating the whole call would return the
            // already-resolved text and drop them.
            val argExpr =
              compiled.compileExpression(
                itemset.label_ref.removePrefix("jr:itext(").removeSuffix(")").trim()
              )
            val itemCtx = controlContext.withContextNode(itemNode)
            argExpr?.evaluateString(itemCtx)?.takeIf { it.isNotEmpty() } ?: valStr
          } else {
            itemNode.children(itemset.label_ref).firstOrNull()?.extractValue()?.toXPathString()
              ?: valStr
          }
        } else {
          valStr
        }

      val localizedEntry =
        lookupLocalizedString(compiled.formDef.model?.translations, activeLanguage, rawLabelRef)
      val resolvedLabel =
        if (localizedEntry != null) {
          ResolvedLabel(
            text = localizedEntry.value_.ifEmpty { rawLabelRef },
            shortText = localizedEntry.short_value.takeIf { it.isNotEmpty() },
            guidanceText = localizedEntry.guidance_value.takeIf { it.isNotEmpty() },
            media = localizedEntry.media,
          )
        } else {
          ResolvedLabel(text = rawLabelRef)
        }

      val props =
        if (itemNode is XPathNode.SecondaryInstanceNode) {
          itemNode.rowAttributes.mapValues { (_, tv) ->
            XPathValue.fromTypedValue(tv).toXPathString()
          }
        } else {
          emptyMap()
        }

      ResolvedChoiceOption(value = valStr, label = resolvedLabel, properties = props)
    }

    if (!itemset.randomize || options.size <= 1) {
      return options
    }

    val seedExpr = compiled.compileExpression(itemset.random_seed_expression)
    val seed: Long? =
      if (seedExpr != null) {
        val num = seedExpr.evaluateNumber(controlContext)
        if (!num.isNaN()) num.roundToLong()
        else seedExpr.evaluateString(controlContext).hashCode().toLong()
      } else {
        environment.randomSeed
      }
    return shuffleOptionsDeterministically(options, seed)
  }

  private fun shuffleOptionsDeterministically(
    options: List<ResolvedChoiceOption>,
    seed: Long?,
  ): List<ResolvedChoiceOption> {
    val list = options.toMutableList()
    val rng = if (seed != null) Random(seed) else Random.Default
    for (i in list.lastIndex downTo 1) {
      val j = rng.nextInt(i + 1)
      val tmp = list[i]
      list[i] = list[j]
      list[j] = tmp
    }
    return list
  }
}
