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

import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode

/**
 * Evaluates `relevant` expressions top-down, so a node is relevant only when its parent is (XForms
 * 1.0 section 6.1.4: relevance is inherited).
 */
internal class RelevanceEvaluator(private val pass: EvaluationPass) {

  /**
   * Returns the relevance of every concrete node in [rawNode], keyed by canonical path, evaluating
   * expressions against [currentPrunedNode] (the record without values that were non-relevant on
   * the previous convergence pass). The first instance of a repeat is also keyed by its unindexed
   * path.
   */
  fun evaluate(
    rawNode: RecordNode,
    currentPrunedNode: RecordNode,
    metadata: RecordMetadata,
    baseRecord: RecordInstance,
  ): LinkedHashMap<String, Boolean> {
    val compiled = pass.compiled
    val relevancyMap = linkedMapOf<String, Boolean>()
    val rootCanonical = "/${compiled.rootName}"
    relevancyMap[rootCanonical] = true

    val tempRecord = baseRecord.copy(metadata = metadata, data_ = currentPrunedNode)
    val baseEvalCtx = pass.evaluationContext(tempRecord)

    // Collect all relative paths from schema and bindings, ordered top-down by segment depth
    val allRelativePaths =
      (compiled.schemaByRelativePath.keys + compiled.bindingsByRelativePath.keys)
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.count { ch -> ch == '/' } }

    for (relPath in allRelativePaths) {
      val concretePaths =
        expandConcretePathsForRelativePath(
          rootNode = rawNode,
          rootName = compiled.rootName,
          relativePath = relPath,
        )
      val compiledBinding = compiled.bindingsByRelativePath[relPath]
      for (canonicalPath in concretePaths) {
        val parentCanonical = canonicalPath.substringBeforeLast('/', rootCanonical)
        val parentRelevant = relevancyMap[parentCanonical] ?: true
        val isRel =
          if (!parentRelevant) {
            false
          } else {
            val relExpr = compiledBinding?.relevantExpr
            if (relExpr == null) {
              true
            } else {
              val nodeCtx = positionContextAtPath(baseEvalCtx, canonicalPath)
              relExpr.evaluateBoolean(nodeCtx)
            }
          }
        relevancyMap[canonicalPath] = isRel
        // If this is a repeat group with 1 instance, also record unindexed path alias
        if (canonicalPath.endsWith("[1]")) {
          val unindexed = canonicalPath.removeSuffix("[1]")
          if (!relevancyMap.containsKey(unindexed)) {
            relevancyMap[unindexed] = isRel
          }
        }
      }
    }

    return relevancyMap
  }
}
