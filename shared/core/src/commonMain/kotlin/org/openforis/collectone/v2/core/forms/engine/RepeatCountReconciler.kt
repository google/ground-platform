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

import kotlin.math.max
import kotlin.math.roundToInt
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode

/** The record after [RepeatCountReconciler] ran, plus each repeat's evaluated `jr:count`. */
internal data class RepeatReconciliationResult(
  val updatedRawNode: RecordNode,
  val updatedMetadata: RecordMetadata,
  /** Target instance count keyed by the repeat's canonical path. */
  val evaluatedCounts: Map<String, Int>,
)

/**
 * Keeps count-controlled repeats (`jr:count`, [openforis.collectone.v1.forms.RepeatDef]
 * `count_expression`) at their evaluated size: appends default instances, firing their
 * `odk-new-repeat` actions, or truncates surplus ones.
 */
internal class RepeatCountReconciler(private val pass: EvaluationPass) {

  private val actionExecutor = ActionExecutor(pass)

  /**
   * Evaluates every repeat's count against [prunedNode] (the record without non-relevant values)
   * and resizes the matching repeats in [rawNode].
   */
  fun reconcile(
    rawNode: RecordNode,
    prunedNode: RecordNode,
    metadata: RecordMetadata,
    baseRecord: RecordInstance,
  ): RepeatReconciliationResult {
    val compiled = pass.compiled
    var currentRaw = rawNode
    var currentMeta = metadata
    val evaluatedCounts = mutableMapOf<String, Int>()

    for ((relRepeatPath, repeatDef) in compiled.repeatDefsByRelativePath) {
      val countExpr = compiled.compileExpression(repeatDef.count_expression) ?: continue
      val repeatSchema = compiled.schemaByRelativePath[relRepeatPath]

      // Expand parent paths if this repeat is nested inside another repeat
      val parentRelPath = relRepeatPath.substringBeforeLast('/', "")
      val repeatName = relRepeatPath.substringAfterLast('/')
      val parentCanonicalPaths =
        expandConcretePathsForRelativePath(
          rootNode = currentRaw,
          rootName = compiled.rootName,
          relativePath = parentRelPath,
        )

      for (parentCanonical in parentCanonicalPaths) {
        val repeatCanonicalPath = "$parentCanonical/$repeatName"
        val tempRecord = baseRecord.copy(metadata = currentMeta, data_ = prunedNode)
        val parentCtx = pass.evaluationContext(tempRecord, parentCanonical)
        val parentNode = parentCtx.contextNode
        val repeatContextNode =
          XPathNode.SecondaryInstanceNode(
            name = repeatName,
            parent = parentNode,
            childrenProvider = { _, nameFilter ->
              if (nameFilter == null) parentNode.children() else parentNode.children(nameFilter)
            },
          )
        val evalCtx =
          parentCtx.copy(
            contextNode = repeatContextNode,
            currentQuestionNode = repeatContextNode,
          )
        val countDouble = countExpr.evaluateNumber(evalCtx)
        val targetCount = if (countDouble.isNaN()) 0 else max(0, countDouble.roundToInt())
        evaluatedCounts[repeatCanonicalPath] = targetCount

        val existing =
          RecordMutator.getRepeatInstances(
            rootNode = currentRaw,
            rootAliases = compiled.rootAliases,
            repeatCanonicalPath = repeatCanonicalPath,
          )

        if (existing.size < targetCount) {
          val mutableList = existing.toMutableList()
          val startIndex = existing.size + 1
          for (newIdx in startIndex..targetCount) {
            mutableList.add(RecordMutator.buildDefaultRepeatItemNode(repeatSchema))
          }
          currentRaw =
            RecordMutator.setRepeatInstances(
              rootNode = currentRaw,
              rootAliases = compiled.rootAliases,
              repeatCanonicalPath = repeatCanonicalPath,
              instances = mutableList,
            )

          // Fire EVENT_REPEAT_INSERT actions for each newly auto-created repeat instance
          val repeatActions = compiled.repeatInsertActionsByRepeatPath[relRepeatPath] ?: emptyList()
          if (repeatActions.isNotEmpty()) {
            for (newIdx in startIndex..targetCount) {
              val instanceCanonical = "$repeatCanonicalPath[$newIdx]"
              val afterActions =
                actionExecutor.execute(
                  actions = repeatActions,
                  rawNode = currentRaw,
                  metadata = currentMeta,
                  contextCanonicalPath = instanceCanonical,
                )
              currentRaw = afterActions.node
              currentMeta = afterActions.metadata ?: currentMeta
            }
          }
        } else if (existing.size > targetCount) {
          currentRaw =
            RecordMutator.setRepeatInstances(
              rootNode = currentRaw,
              rootAliases = compiled.rootAliases,
              repeatCanonicalPath = repeatCanonicalPath,
              instances = existing.take(targetCount),
            )
        }
      }
    }

    return RepeatReconciliationResult(currentRaw, currentMeta, evaluatedCounts)
  }
}
