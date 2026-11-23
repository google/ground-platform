/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.xpath.functions

import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** ODK functions that look values up in secondary instances and translations. */
internal object LookupFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("pulldata") {
      checkEagerArgCount(name, args, 4..4)
      val instanceId = args[0].toXPathString()
      val returnField = args[1].toXPathString()
      val keyField = args[2].toXPathString()
      val keyVal = args[3].toXPathString()
      evaluatePullData(instanceId, returnField, keyField, keyVal, context)
    }
    define("jr:itext") {
      checkEagerArgCount(name, args, 1..1)
      val textId = args[0].toXPathString()
      XPathValue.Str(resolveItext(textId, context))
    }
    define("jr:choice-name") {
      checkEagerArgCount(name, args, 2..2)
      evaluateChoiceName(args[0], args[1], context)
    }
  }

  private fun evaluatePullData(
    instanceId: String,
    returnField: String,
    keyField: String,
    keyVal: String,
    context: EvaluationContext,
  ): XPathValue {
    // 1. Try O(1) indexed equality pushdown on SecondaryInstanceProvider
    val pushdownRows =
      context.secondaryInstanceProvider.lookupByEquality(instanceId, keyField, keyVal)
    if (pushdownRows != null) {
      val firstRow = pushdownRows.firstOrNull() ?: return XPathValue.Str("")
      val cell = firstRow[returnField] ?: return XPathValue.Str("")
      return XPathValue.fromTypedValue(cell)
    }

    // 2. Fallback: traverse virtual XPathNode tree
    val root =
      context.secondaryInstanceProvider.resolveRoot(instanceId) ?: return XPathValue.Str("")
    val itemNodes =
      root.children("root").flatMap { it.children("item") }.ifEmpty { root.children("item") }
    for (item in itemNodes) {
      val keyChild = item.children(keyField).firstOrNull()
      if (keyChild != null && keyChild.extractValue().toXPathString() == keyVal) {
        val retChild = item.children(returnField).firstOrNull()
        return retChild?.extractValue() ?: XPathValue.Str("")
      }
    }
    return XPathValue.Str("")
  }

  private fun resolveItext(textId: String, context: EvaluationContext): String {
    val catalog = context.translations ?: return textId
    val languages = catalog.languages
    if (languages.isEmpty()) return textId

    val activeLang =
      languages.find { it.language.equals(context.activeLanguage, ignoreCase = true) }
        ?: languages.find { it.is_default }
        ?: languages.first()

    return activeLang.strings[textId]?.value_ ?: ""
  }

  private fun evaluateChoiceName(
    arg0: XPathValue,
    arg1: XPathValue,
    context: EvaluationContext,
  ): XPathValue {
    // Supports both JavaRosa/XForms signature: jr:choice-name(choice_val, 'field_path')
    // and ProtoForms doc signature: jr:choice-name(node_target, choice_val)
    val controls = mutableListOf<ControlDef>()
    fun collectControls(components: List<ViewComponent>) {
      for (comp in components) {
        comp.control?.let { controls.add(it) }
        comp.group?.let { collectControls(it.components) }
        comp.repeat?.let { collectControls(it.components) }
      }
    }
    context.formDef?.view?.components?.let { collectControls(it) }

    val choiceVal: String
    val fieldPath: String
    if (arg0 is XPathValue.NodeSet && arg0.nodes.isNotEmpty()) {
      val s1 = arg1.toXPathString().trim()
      if (s1.startsWith("/") || s1.startsWith(".") || s1.contains("/")) {
        choiceVal = arg0.toXPathString().trim()
        fieldPath = s1
      } else {
        fieldPath = arg0.nodes.first().name
        choiceVal = s1
      }
    } else {
      val s0 = arg0.toXPathString().trim()
      val s1 = arg1.toXPathString().trim()
      if (s1.startsWith("/") || s1.startsWith(".") || s1.contains("/")) {
        choiceVal = s0
        fieldPath = s1
      } else if (s0.startsWith("/") || s0.startsWith(".") || s0.contains("/")) {
        fieldPath = s0
        choiceVal = s1
      } else if (
        controls.any { it.field_ref == s1 || it.field_ref.substringAfterLast('/') == s1 } &&
          controls.none { it.field_ref == s0 || it.field_ref.substringAfterLast('/') == s0 }
      ) {
        choiceVal = s0
        fieldPath = s1
      } else {
        fieldPath = s0
        choiceVal = s1
      }
    }

    val targetFieldName = fieldPath.substringAfterLast('/')
    val matchingControl = controls.find {
      it.field_ref == fieldPath || it.field_ref.substringAfterLast('/') == targetFieldName
    }
    val choiceItem = matchingControl?.choices?.find { it.value_ == choiceVal }
    if (choiceItem != null) {
      val label = choiceItem.label
      if (label != null) {
        if (label.text_id.isNotEmpty()) {
          val itextVal = resolveItext(label.text_id, context)
          if (itextVal.isNotEmpty()) return XPathValue.Str(itextVal)
        }
        if (label.text.isNotEmpty()) return XPathValue.Str(label.text)
      }
    }

    // Fallback to dynamic <itemset> lookup on secondary instance
    val itemset = matchingControl?.itemset
    if (itemset != null && itemset.instance_id.isNotEmpty() && choiceVal.isNotEmpty()) {
      val valCol = itemset.value_ref.ifEmpty { "name" }
      val rawLabelRef = itemset.label_ref.ifEmpty { "label" }
      val isItextCall = rawLabelRef.startsWith("jr:itext(") && rawLabelRef.endsWith(")")
      val labelCol =
        if (isItextCall) {
          rawLabelRef.removePrefix("jr:itext(").removeSuffix(")").trim()
        } else {
          rawLabelRef
        }

      val pushdownRows =
        context.secondaryInstanceProvider.lookupByEquality(itemset.instance_id, valCol, choiceVal)
      val rawLabelVal: String? =
        if (pushdownRows != null) {
          pushdownRows.firstOrNull()?.get(labelCol)?.let {
            XPathValue.fromTypedValue(it).toXPathString()
          }
        } else {
          val root = context.secondaryInstanceProvider.resolveRoot(itemset.instance_id)
          val allItems =
            if (root == null) {
              emptyList()
            } else if (itemset.nodeset_path.isNotEmpty()) {
              var curr = listOf(root)
              for (seg in itemset.nodeset_path.split('/').filter { it.isNotEmpty() }) {
                curr = curr.flatMap { it.children(seg) }
              }
              curr
            } else {
              root
                .children("root")
                .flatMap { it.children("item") }
                .ifEmpty {
                  root.children("item")
                }
            }
          val matchNode = allItems.find { node ->
            node.children(valCol).firstOrNull()?.extractValue()?.toXPathString() == choiceVal
          }
          matchNode?.children(labelCol)?.firstOrNull()?.extractValue()?.toXPathString()
        }

      if (!rawLabelVal.isNullOrEmpty()) {
        val itextResolved = resolveItext(rawLabelVal, context)
        return XPathValue.Str(itextResolved.ifEmpty { rawLabelVal })
      }
    }

    return XPathValue.Str(choiceVal)
  }
}
