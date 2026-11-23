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

import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordNode
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode

/** Repositions [baseContext] on the node at [canonicalPath], following repeat indexes. */
internal fun positionContextAtPath(
  baseContext: EvaluationContext,
  canonicalPath: String,
  explicitPosition: Int? = null,
  explicitSize: Int? = null,
): EvaluationContext {
  val segments = RecordMutator.parseSegments(canonicalPath)
  val steps =
    if (
      segments.isNotEmpty() &&
        segments.first().name in RecordMutator.resolveRootAliases(baseContext.formDef ?: FormDef())
    ) {
      segments.drop(1)
    } else {
      segments
    }

  var currentNode: XPathNode =
    if (baseContext.rootNode is XPathNode.DocumentRootNode) {
      baseContext.rootNode.instanceElementNode
    } else {
      baseContext.rootNode
    }

  for (seg in steps) {
    val candidates = currentNode.children(seg.name)
    if (candidates.isEmpty()) break
    val targetIdx = (seg.repeatIndex ?: 1) - 1
    currentNode = candidates.getOrElse(targetIdx) { candidates.first() }
  }

  return baseContext.copy(
    contextNode = currentNode,
    currentQuestionNode = currentNode,
    contextPosition = explicitPosition ?: currentNode.repeatIndex,
    contextSize = explicitSize ?: currentNode.siblingRepeatCount,
  )
}

/** Expands a schema [relativePath] into the canonical path of every matching record node. */
internal fun expandConcretePathsForRelativePath(
  rootNode: RecordNode,
  rootName: String,
  relativePath: String,
): List<String> {
  if (relativePath.isEmpty()) return listOf("/$rootName")
  val segments = relativePath.split('/').filter { it.isNotEmpty() }
  val results = mutableListOf<String>()

  fun expand(currentNode: RecordNode?, currentPrefix: String, segIndex: Int) {
    if (segIndex >= segments.size) {
      results.add(currentPrefix)
      return
    }
    val segName = segments[segIndex]
    val fv = currentNode?.fields?.get(segName)
    when {
      fv?.repeat_value != null -> {
        val nodes = fv.repeat_value.nodes
        for ((idx, itemNode) in nodes.withIndex()) {
          val idx1 = idx + 1
          expand(itemNode, "$currentPrefix/$segName[$idx1]", segIndex + 1)
        }
      }
      fv?.node_value != null -> {
        expand(fv.node_value, "$currentPrefix/$segName", segIndex + 1)
      }
      else -> {
        expand(null, "$currentPrefix/$segName", segIndex + 1)
      }
    }
  }

  expand(rootNode, "/$rootName", 0)
  return results
}
