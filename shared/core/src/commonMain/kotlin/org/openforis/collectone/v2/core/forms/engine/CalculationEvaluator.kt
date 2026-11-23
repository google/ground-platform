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
 * Evaluates `calculate` expressions in dependency order ([CompiledForm.topologicalBindings]) and
 * writes changed results back into the record. Non-relevant nodes are skipped.
 */
internal class CalculationEvaluator(private val pass: EvaluationPass) {

  /**
   * Recomputes every relevant calculated node in [rawNode], evaluating against [prunedNode] so
   * expressions never read non-relevant values, and returns the updated record node and metadata.
   */
  fun evaluate(
    rawNode: RecordNode,
    prunedNode: RecordNode,
    metadata: RecordMetadata,
    relevancyMap: Map<String, Boolean>,
    baseRecord: RecordInstance,
  ): Pair<RecordNode, RecordMetadata> {
    val compiled = pass.compiled
    var currentRaw = rawNode
    var currentPruned = prunedNode
    var currentMeta = metadata

    for (compiledBinding in compiled.topologicalBindings) {
      val calcExpr = compiledBinding.calculateExpr ?: continue
      val relPath = compiledBinding.relativePath
      val targetType = compiled.resolveDataType(relPath)

      val concretePaths =
        expandConcretePathsForRelativePath(
          rootNode = currentRaw,
          rootName = compiled.rootName,
          relativePath = relPath,
        )

      for (canonicalPath in concretePaths) {
        val isRelevant = relevancyMap[canonicalPath] ?: true
        if (!isRelevant) continue

        val tempRecord = baseRecord.copy(metadata = currentMeta, data_ = currentPruned)
        val baseEvalCtx = pass.evaluationContext(tempRecord)
        val nodeCtx = positionContextAtPath(baseEvalCtx, canonicalPath)
        val calculatedFv = calcExpr.evaluateFieldValue(nodeCtx, targetType)
        val existingFv =
          RecordMutator.getFieldValue(
            rootNode = currentRaw,
            metadata = currentMeta,
            rootAliases = compiled.rootAliases,
            canonicalPath = canonicalPath,
          )

        if (calculatedFv != existingFv) {
          val (nextRaw, nextMeta) =
            RecordMutator.setFieldValue(
              rootNode = currentRaw,
              metadata = currentMeta,
              rootAliases = compiled.rootAliases,
              canonicalPath = canonicalPath,
              newValue = calculatedFv,
            )
          currentRaw = nextRaw
          if (nextMeta != null) currentMeta = nextMeta
          currentPruned =
            RecordMutator.pruneNonRelevantNodes(
              node = currentRaw,
              currentPathPrefix = "/${compiled.rootName}",
              relevancyMap = relevancyMap,
            )
        }
      }
    }

    return currentRaw to currentMeta
  }
}
