/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode

class FormEngineCalculateTest {

  @Test
  fun calculate_declaredBeforeDependencies_evaluatesInDependencyOrder() {
    val session = FormSession(invoiceForm)

    assertEquals(100.0, session.state.findFieldState("subtotal")?.value?.scalar_value?.double_value)
    assertEquals(10.0, session.state.findFieldState("tax")?.value?.scalar_value?.double_value)
    assertEquals(110.0, session.state.findFieldState("total")?.value?.scalar_value?.double_value)
  }

  @Test
  fun calculate_inputChange_recomputesDependentFields() {
    val session = FormSession(invoiceForm)

    session.updateInt("qty", 10)

    assertEquals(250.0, session.state.findFieldState("subtotal")?.value?.scalar_value?.double_value)
    assertEquals(25.0, session.state.findFieldState("tax")?.value?.scalar_value?.double_value)
    assertEquals(275.0, session.state.findFieldState("total")?.value?.scalar_value?.double_value)
  }

  @Test
  fun once_initialLoad_evaluatesExpression() {
    val session = FormSession(invoiceForm)

    assertEquals(
      "CODE-4",
      session.state.findFieldState("initial_code")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun once_inputChange_keepsFirstValue() {
    val session = FormSession(invoiceForm)

    session.updateInt("qty", 10)

    assertEquals(
      "CODE-4",
      session.state.findFieldState("initial_code")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun once_userEdit_survivesRecalculation() {
    // `once()` returns the context node's current value when non-empty, so the user's input is
    // preserved across recalculations, whereas a plain `calculate` field ("calculate wins" in
    // ODK/XForms) always recomputes its formula.
    val session = FormSession(invoiceForm)

    session.updateString("initial_code", "USER-OVERRIDE")
    session.updateInt("qty", 6)

    assertEquals(
      "USER-OVERRIDE",
      session.state.findFieldState("initial_code")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun calculate_afterOnceFieldUserEdit_stillRecomputes() {
    val session = FormSession(invoiceForm)

    session.updateString("initial_code", "USER-OVERRIDE")
    session.updateInt("qty", 6)

    assertEquals(150.0, session.state.findFieldState("subtotal")?.value?.scalar_value?.double_value)
  }

  /** Declares `total` BEFORE `tax` and `subtotal` to verify topological DAG sorting. */
  private val invoiceForm: FormDef =
    FormDef(
      form_id = "invoice_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "total", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "tax", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "subtotal", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "qty", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "price", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "initial_code", type = DataType.TYPE_STRING),
                    ),
                ),
              default_values =
                buildRecordNode {
                  int32("qty", 4)
                  double("price", 25.0)
                },
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "total",
                type = DataType.TYPE_DOUBLE,
                calculate_expression = "/data/subtotal + /data/tax",
              ),
              FieldBinding(
                field_path = "tax",
                type = DataType.TYPE_DOUBLE,
                calculate_expression = "/data/subtotal * 0.10",
              ),
              FieldBinding(
                field_path = "subtotal",
                type = DataType.TYPE_DOUBLE,
                calculate_expression = "/data/qty * /data/price",
              ),
              FieldBinding(
                field_path = "initial_code",
                type = DataType.TYPE_STRING,
                calculate_expression = "once(concat('CODE-', /data/qty))",
              ),
            ),
        ),
    )

  @Test
  fun compile_throwsOnCircularCalculateDependency() {
    // `a` depends on `b` and `b` depends on `a`, so no valid evaluation order exists. The previous
    // implementation silently picked an arbitrary order, producing values that depended on which
    // binding happened to be declared first.
    val formDef = formDefWithCalculates("a" to "../b + 1", "b" to "../a + 1")

    val failure = assertFailsWith<CyclicDependencyException> { FormEngine.compile(formDef) }
    assertEquals(listOf("a", "b"), failure.paths)
    assertTrue(failure.message!!.contains("Circular dependency"), failure.message!!)
  }

  @Test
  fun compile_throwsOnLongerDependencyCycle() {
    val formDef = formDefWithCalculates("a" to "../c + 1", "b" to "../a + 1", "c" to "../b + 1")

    val failure = assertFailsWith<CyclicDependencyException> { FormEngine.compile(formDef) }
    assertEquals(listOf("a", "b", "c"), failure.paths)
  }

  @Test
  fun compile_acceptsAcyclicDependencyChain() {
    // Declared in reverse dependency order to confirm the topological sort still reorders them.
    val formDef = formDefWithCalculates("c" to "../b + 1", "b" to "../a + 1", "a" to null)

    val compiled = FormEngine.compile(formDef)
    assertEquals(
      listOf("a", "b", "c"),
      compiled.topologicalBindings.map { it.relativePath },
      "Bindings should be ordered so each field is evaluated after its dependencies",
    )
  }

  @Test
  fun evaluate_relevanceDependsOnCalculatedValue_calculatesTheDependency() {
    val session = FormSession(bonusThresholdForm)

    assertEquals(12, session.state.findFieldState("total")?.value?.scalar_value?.int32_value)
  }

  @Test
  fun evaluate_relevanceDependsOnCalculatedValue_becomesRelevant() {
    // Within a single pass the engine evaluates relevance (stage 2) *before* calculations
    // (stage 3), so on the first pass `total` is still 0 and `bonus` looks non-relevant. The
    // surrounding convergence loop must re-evaluate relevance after `total` is computed,
    // otherwise `bonus` would stay hidden and never receive its calculated value.
    val session = FormSession(bonusThresholdForm)

    assertTrue(
      session.state.findFieldState("bonus")?.isRelevant == true,
      "bonus should become relevant once total is calculated",
    )
  }

  @Test
  fun evaluate_relevanceDependsOnCalculatedValue_calculatesTheNewlyRelevantField() {
    val session = FormSession(bonusThresholdForm)

    assertEquals(112, session.state.findFieldState("bonus")?.value?.scalar_value?.int32_value)
  }

  @Test
  fun evaluate_calculatedValueDropsBelowThreshold_recomputesTheDependency() {
    val session = FormSession(bonusThresholdForm)

    session.updateInt("qty", 1)

    assertEquals(2, session.state.findFieldState("total")?.value?.scalar_value?.int32_value)
  }

  @Test
  fun evaluate_calculatedValueDropsBelowThreshold_convergesBackToNonRelevant() {
    val session = FormSession(bonusThresholdForm)

    session.updateInt("qty", 1)

    assertTrue(session.state.findFieldState("bonus")?.isRelevant != true)
  }

  /** `bonus` is relevant only when the calculated `total` (`qty * 2`, `qty` = 6) exceeds 10. */
  private val bonusThresholdForm: FormDef =
    FormDef(
      form_id = "convergence_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "qty", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "total", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "bonus", type = DataType.TYPE_INT32),
                    ),
                ),
              default_values = buildRecordNode { int32("qty", 6) },
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "qty", type = DataType.TYPE_INT32),
              FieldBinding(
                field_path = "total",
                type = DataType.TYPE_INT32,
                calculate_expression = "/data/qty * 2",
              ),
              FieldBinding(
                field_path = "bonus",
                type = DataType.TYPE_INT32,
                relevant_expression = "/data/total > 10",
                calculate_expression = "/data/total + 100",
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control = ControlDef(field_ref = "qty", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                control = ControlDef(field_ref = "total", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                control = ControlDef(field_ref = "bonus", type = ControlType.CONTROL_INPUT)
              ),
            )
        ),
    )

  @Test
  fun validate_reportsCircularDependencyAsDataInsteadOfThrowing() {
    // compile() fails fast on a cycle, which is correct for the runtime but leaves an authoring
    // tool unable to even open the form to fix it. validate() hands back the offending paths.
    val formDef = formDefWithCalculates("a" to "../b + 1", "b" to "../a + 1")

    val result = FormEngine.validate(formDef)

    assertFalse(result.isValid)
    val problem = assertIs<FormValidationProblem.CircularDependency>(result.problems.single())
    assertEquals(listOf("a", "b"), problem.fieldPaths)
    assertTrue(problem.message.contains("Circular dependency"), problem.message)
  }

  @Test
  fun validate_returnsValidForAcyclicForm() {
    val formDef = formDefWithCalculates("c" to "../b + 1", "b" to "../a + 1", "a" to null)

    val result = FormEngine.validate(formDef)

    assertTrue(result.isValid, "Expected no problems, got ${result.problems}")
  }

  @Test
  fun validate_reportsMalformedExpressionAsDataInsteadOfThrowing() {
    // Expressions are parsed eagerly while indexing bindings, so a syntax error would otherwise
    // escape validate() as an exception.
    val formDef = formDefWithCalculates("a" to "1 +")

    val result = FormEngine.validate(formDef)

    assertFalse(result.isValid)
    assertIs<FormValidationProblem.InvalidExpression>(result.problems.single())
  }

  @Test
  fun evaluate_chainDeeperThanPassLimit_flagsDidNotConverge() {
    // Each level of "relevance depends on a calculated value" costs one extra pass, because
    // relevance (stage 2) is evaluated before calculations (stage 3) within a pass. A chain
    // deeper than MAX_CONVERGENCE_PASSES therefore runs out of passes with work still pending.
    // Without a diagnostic this is indistinguishable from a form that simply settled early.
    val session = FormSession(chainedRelevanceFormDef(depth = 6))

    assertTrue(
      session.state.didNotConverge,
      "A dependency chain deeper than the pass limit should be reported as non-convergent",
    )
  }

  @Test
  fun evaluate_chainDeeperThanPassLimit_leavesChainTailUncalculated() {
    // The tail of the chain never gets its calculated value, which is exactly why callers need to
    // be told rather than silently shown a half-evaluated form.
    val session = FormSession(chainedRelevanceFormDef(depth = 6))

    assertNull(session.state.findFieldState("c6")?.value?.scalar_value?.int32_value)
  }

  @Test
  fun evaluate_shallowChain_doesNotFlagDidNotConverge() {
    // Control: the flag must not be permanently on. A chain the loop can settle is not flagged.
    val session = FormSession(chainedRelevanceFormDef(depth = 2))

    assertFalse(session.state.didNotConverge)
  }

  @Test
  fun evaluate_shallowChain_calculatesChainTail() {
    val session = FormSession(chainedRelevanceFormDef(depth = 2))

    assertEquals(3, session.state.findFieldState("c2")?.value?.scalar_value?.int32_value)
  }

  /**
   * Builds a form with a `seed` of 1 followed by [depth] fields `c1..cN`, where each `cN` both
   * calculates from and takes its relevance from its predecessor. Resolving each link requires a
   * separate convergence pass.
   */
  private fun chainedRelevanceFormDef(depth: Int): FormDef {
    val names = (1..depth).map { "c$it" }
    fun predecessorOf(index: Int) = if (index == 0) "seed" else names[index - 1]
    return FormDef(
      form_id = "convergence_chain_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    (listOf("seed") + names).map {
                      FieldDefinition(name = it, type = DataType.TYPE_INT32)
                    },
                ),
              default_values = buildRecordNode { int32("seed", 1) },
            ),
          bindings =
            listOf(FieldBinding(field_path = "seed", type = DataType.TYPE_INT32)) +
              names.mapIndexed { i, name ->
                FieldBinding(
                  field_path = name,
                  type = DataType.TYPE_INT32,
                  relevant_expression = "/data/${predecessorOf(i)} > 0",
                  calculate_expression = "/data/${predecessorOf(i)} + 1",
                )
              },
        ),
      view =
        ViewDef(
          components =
            (listOf("seed") + names).map {
              ViewComponent(control = ControlDef(field_ref = it, type = ControlType.CONTROL_INPUT))
            }
        ),
    )
  }

  /** Builds a minimal flat int32 form where each named field carries an optional `calculate`. */
  private fun formDefWithCalculates(vararg fields: Pair<String, String?>): FormDef =
    FormDef(
      form_id = "cycle_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    fields.map { (name, _) ->
                      FieldDefinition(name = name, type = DataType.TYPE_INT32)
                    },
                )
            ),
          bindings =
            fields.map { (name, calculate) ->
              FieldBinding(
                field_path = name,
                type = DataType.TYPE_INT32,
                calculate_expression = calculate ?: "",
              )
            },
        ),
    )
}
