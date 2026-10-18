package org.groundplatform.v2.app.domain.model.editor

/**
 * Duration & fatigue estimation helpers on [FormFlowGraph].
 *
 * Computes the longest path (maximum number of question screens visited along any path from
 * [FormFlowGraph.startSlot] to [FormFlowGraph.endSlot]) in the form flow DAG, and estimates
 * completion time at ~2 minutes per question along that longest path.
 */
fun FormFlowGraph.longestPathQuestionCount(
  form: EditorForm,
  edges: List<FlowEdge> = edges(form),
): Int {
  val n = form.questions.size
  if (n == 0) return 0
  val end = endSlot(form)
  val dp = IntArray(end + 1) { Int.MIN_VALUE }
  dp[startSlot()] = 0
  for (edge in edges) {
    val fromSlot = edge.from.coerceIn(0, end)
    val toSlot = edge.to.coerceIn(0, end)
    if (toSlot <= fromSlot) continue
    val fromBest = dp[fromSlot]
    if (fromBest == Int.MIN_VALUE) continue
    val stepCost = if (toSlot in 1..n) 1 else 0
    val candidate = fromBest + stepCost
    if (candidate > dp[toSlot]) {
      dp[toSlot] = candidate
    }
  }
  val bestToEnd = dp[end]
  return if (bestToEnd >= 0) bestToEnd else n
}

/**
 * Estimated completion duration in minutes along the longest path through [form], assuming ~2
 * minutes per question.
 */
fun FormFlowGraph.estimatedMinutes(
  form: EditorForm,
  edges: List<FlowEdge> = edges(form),
): Int = longestPathQuestionCount(form, edges) * 2
