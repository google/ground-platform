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

/** The record once repeat counts, relevance and calculations stopped changing. */
internal data class ConvergedRecord(
  /** Every value, including the ones in non-relevant nodes. */
  val rawNode: RecordNode,
  /** [rawNode] without non-relevant nodes: what expressions and submissions see. */
  val prunedNode: RecordNode,
  val metadata: RecordMetadata,
  /** Relevance keyed by canonical path. */
  val relevancyMap: Map<String, Boolean>,
  /** Evaluated `jr:count` keyed by repeat path. */
  val repeatCounts: Map<String, Int>,
  /** False when [MAX_CONVERGENCE_PASSES] ran out before a fixed point was reached. */
  val converged: Boolean,
)

/**
 * Repeat counts, relevance and calculations feed each other (a calculation can change a count or a
 * `relevant` expression, which changes what the next calculation reads), so they are re-evaluated
 * together until none of them changes, up to [MAX_CONVERGENCE_PASSES] times.
 */
internal class ConvergenceLoop(private val pass: EvaluationPass) {

  private val repeatCounts = RepeatCountReconciler(pass)
  private val relevance = RelevanceEvaluator(pass)
  private val calculations = CalculationEvaluator(pass)

  /** Iterates from [rawRecordNode] (the data of [baseRecord] after any edits) to a fixed point. */
  fun converge(baseRecord: RecordInstance, rawRecordNode: RecordNode): ConvergedRecord {
    var workingRawNode = rawRecordNode
    var workingMetadata = baseRecord.metadata ?: RecordMetadata()
    var relevancyMap: Map<String, Boolean> = linkedMapOf()
    var prunedNode = workingRawNode
    var dynamicRepeatCounts: Map<String, Int> = emptyMap()
    var converged = false
    var passesRun = 0
    while (!converged && passesRun < MAX_CONVERGENCE_PASSES) {
      passesRun++
      val prevPruned = prunedNode
      val prevRelevancy = relevancyMap
      val prevCounts = dynamicRepeatCounts

      // 1. Reconcile dynamic repeat counts (RepeatDef.count_expression)
      val repeatResult =
        repeatCounts.reconcile(
          rawNode = workingRawNode,
          prunedNode = prunedNode,
          metadata = workingMetadata,
          baseRecord = baseRecord,
        )
      workingRawNode = repeatResult.updatedRawNode
      workingMetadata = repeatResult.updatedMetadata
      dynamicRepeatCounts = repeatResult.evaluatedCounts

      // 2. Top-down relevancy pass
      relevancyMap =
        relevance.evaluate(
          rawNode = workingRawNode,
          currentPrunedNode = prunedNode,
          metadata = workingMetadata,
          baseRecord = baseRecord,
        )
      prunedNode = prune(workingRawNode, relevancyMap)

      // 3. Topological calculate_expression pass
      val calcResult =
        calculations.evaluate(
          rawNode = workingRawNode,
          prunedNode = prunedNode,
          metadata = workingMetadata,
          relevancyMap = relevancyMap,
          baseRecord = baseRecord,
        )
      workingRawNode = calcResult.first
      workingMetadata = calcResult.second
      prunedNode = prune(workingRawNode, relevancyMap)

      converged =
        prunedNode == prevPruned &&
          relevancyMap == prevRelevancy &&
          dynamicRepeatCounts == prevCounts
    }
    return ConvergedRecord(
      rawNode = workingRawNode,
      prunedNode = prunedNode,
      metadata = workingMetadata,
      relevancyMap = relevancyMap,
      repeatCounts = dynamicRepeatCounts,
      converged = converged,
    )
  }

  private fun prune(rawNode: RecordNode, relevancyMap: Map<String, Boolean>): RecordNode =
    RecordMutator.pruneNonRelevantNodes(
      node = rawNode,
      currentPathPrefix = pass.rootCanonicalPath,
      relevancyMap = relevancyMap,
    )

  internal companion object {
    const val MAX_CONVERGENCE_PASSES = 5
  }
}
