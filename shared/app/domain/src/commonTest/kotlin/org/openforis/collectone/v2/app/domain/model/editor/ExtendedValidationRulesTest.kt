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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.messageKey
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.ValidationErrorKind

class ExtendedValidationRulesTest {

  private fun q(
    key: String,
    name: String,
    type: EditorQuestionType,
    label: String = name,
    required: Boolean = false,
    requiredCondition: EditorRelevance? = null,
    validation: EditorValidation? = null,
    choices: List<EditorChoice> = emptyList(),
    sectionId: String = "",
  ) =
    EditorQuestion(
      key = key,
      name = name,
      type = type,
      label = label,
      required = required,
      requiredCondition = requiredCondition,
      validation = validation,
      choices = choices,
      sectionId = sectionId,
    )

  @Test
  fun strictNumberBounds_compileRoundTripAndValidate() {
    val form =
      EditorForm(
        formId = "f_strict",
        title = "Strict Bounds",
        questions =
          listOf(
            q(
              "q1",
              "slope",
              EditorQuestionType.NUMBER,
              validation =
                EditorValidation(
                  min = "0",
                  max = "90",
                  minExclusive = true,
                  maxExclusive = true,
                ),
            )
          ),
      )

    val expr =
      ValidationRules.constraintExpression(
        EditorQuestionType.NUMBER,
        form.questions[0].validation,
        form,
        "q1",
      )
    assertEquals(". > 0 and . < 90", expr)
    assertEquals(
      "Must be greater than 0 and be less than 90.",
      ValidationRules.message(EditorQuestionType.NUMBER, form.questions[0].validation, form, "q1"),
    )

    val xml = EditorXFormsGenerator.toXml(form)
    val reimported = FormImport.fromXml(xml)!!.form
    val reValidation = reimported.questions[0].validation
    assertNotNull(reValidation)
    assertEquals("0", reValidation.min)
    assertEquals("90", reValidation.max)
    assertTrue(reValidation.minExclusive)
    assertTrue(reValidation.maxExclusive)

    // Equal min/max with exclusive flag is flagged as invalid.
    val invalidQ =
      q(
        "q2",
        "bad",
        EditorQuestionType.NUMBER,
        validation = EditorValidation(min = "5", max = "5", minExclusive = true),
      )
    assertTrue(
      ValidationRules.issues(invalidQ).any { it.messageKey == EditorIssueMessage.MIN_MORE_THAN_MAX }
    )
  }

  @Test
  fun crossFieldComparison_compilesRoundTripsAndEnforcesInEngine() {
    val startDate = q("q1", "start_date", EditorQuestionType.DATE, label = "Start date")
    val endDate =
      q(
        "q2",
        "end_date",
        EditorQuestionType.DATE,
        label = "End date",
        validation =
          EditorValidation(
            comparison =
              EditorFieldComparison(
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                targetQuestionKey = "q1",
              )
          ),
      )
    val form = EditorForm(formId = "f_cmp", title = "Dates", questions = listOf(startDate, endDate))

    val expr =
      ValidationRules.constraintExpression(endDate.type, endDate.validation, form, endDate.key)
    assertEquals(". >= /data/start_date", expr)
    assertEquals(
      "Must be at least Start date.",
      ValidationRules.message(endDate.type, endDate.validation, form, endDate.key),
    )

    // Round-trip through XForms XML.
    val xml = EditorXFormsGenerator.toXml(form)
    val reimported = FormImport.fromXml(xml)!!.form
    val reEnd = reimported.questions[1]
    assertEquals(ComparisonOperator.GREATER_THAN_OR_EQUAL, reEnd.validation?.comparison?.operator)
    assertEquals(reimported.questions[0].key, reEnd.validation?.comparison?.targetQuestionKey)

    // Verify in FormEngine runtime.
    val session = FormSession(EditorXFormsGenerator.compile(form).proto)
    session.updateString("start_date", "2026-05-10")
    session.updateString("end_date", "2026-05-01")
    assertFalse(session.state.isValid)
    assertTrue(
      session.state.validationErrors.any { it.kind == ValidationErrorKind.CONSTRAINT_VIOLATED }
    )
    session.updateString("end_date", "2026-05-15")
    assertTrue(session.state.isValid)
  }

  @Test
  fun distanceCheck_compilesRoundTripsAndEnforcesInEngine() {
    val plotCenter = q("q1", "plot_center", EditorQuestionType.LOCATION, label = "Plot center")
    val treeLoc =
      q(
        "q2",
        "tree_loc",
        EditorQuestionType.LOCATION,
        label = "Tree location",
        validation =
          EditorValidation(
            min = "0",
            max = "50",
            distanceReferenceQuestionKey = "q1",
          ),
      )
    val form =
      EditorForm(formId = "f_dist", title = "Distance", questions = listOf(plotCenter, treeLoc))

    val expr =
      ValidationRules.constraintExpression(treeLoc.type, treeLoc.validation, form, treeLoc.key)
    assertEquals(
      "distance(., /data/plot_center) >= 0 and distance(., /data/plot_center) <= 50",
      expr,
    )
    assertEquals(
      "Must be between 0 and 50 meters from Plot center.",
      ValidationRules.message(treeLoc.type, treeLoc.validation, form, treeLoc.key),
    )

    // Round-trip through XForms XML.
    val xml = EditorXFormsGenerator.toXml(form)
    val reimported = FormImport.fromXml(xml)!!.form
    val reTree = reimported.questions[1]
    assertEquals(reimported.questions[0].key, reTree.validation?.distanceReferenceQuestionKey)
    assertEquals("0", reTree.validation?.min)
    assertEquals("50", reTree.validation?.max)

    // Verify in FormEngine runtime: ~111m away fails, ~11m away passes.
    val session = FormSession(EditorXFormsGenerator.compile(form).proto)
    session.updateGeoPoint("plot_center", 0.0, 0.0)
    session.updateGeoPoint("tree_loc", 0.001, 0.0)
    assertFalse(session.state.isValid)
    assertTrue(
      session.state.validationErrors.any { it.kind == ValidationErrorKind.CONSTRAINT_VIOLATED }
    )
    session.updateGeoPoint("tree_loc", 0.0001, 0.0)
    assertTrue(session.state.isValid)
  }

  @Test
  fun customExpressionAndConditionalValidation_compileRoundTripAndEnforceInEngine() {
    val hasCover =
      q(
        "q1",
        "has_cover",
        EditorQuestionType.SELECT_ONE,
        choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
      )
    val forestPct = q("q2", "forest_pct", EditorQuestionType.NUMBER)
    val cropPct =
      q(
        "q3",
        "crop_pct",
        EditorQuestionType.NUMBER,
        validation =
          EditorValidation(
            customExpression = ". + /data/forest_pct <= 100",
            condition = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
            message = "Percentages cannot exceed 100.",
          ),
      )
    val form =
      EditorForm(
        formId = "f_custom",
        title = "Custom & Conditional",
        questions = listOf(hasCover, forestPct, cropPct),
      )

    val expr =
      ValidationRules.constraintExpression(cropPct.type, cropPct.validation, form, cropPct.key)
    assertEquals("not(/data/has_cover = 'yes') or (. + /data/forest_pct <= 100)", expr)

    // Round-trip through XForms XML.
    val xml = EditorXFormsGenerator.toXml(form)
    val reimported = FormImport.fromXml(xml)!!.form
    val reCrop = reimported.questions[2]
    assertEquals(". + /data/forest_pct <= 100", reCrop.validation?.customExpression)
    assertEquals(reimported.questions[0].key, reCrop.validation?.condition?.sourceQuestionKey)
    assertEquals("yes", reCrop.validation?.condition?.value)
    assertEquals("Percentages cannot exceed 100.", reCrop.validation?.message)

    // Verify in FormEngine runtime: when has_cover = 'no', sum > 100 is allowed; when 'yes',
    // rejected.
    val session = FormSession(EditorXFormsGenerator.compile(form).proto)
    session.updateString("has_cover", "no")
    session.updateInt("forest_pct", 70)
    session.updateInt("crop_pct", 50)
    assertTrue(session.state.isValid)
    session.updateString("has_cover", "yes")
    assertFalse(session.state.isValid)
    assertTrue(
      session.state.validationErrors.any { it.kind == ValidationErrorKind.CONSTRAINT_VIOLATED }
    )

    // Invalid XPath syntax is caught by ValidationRules.issues.
    val badQ =
      q(
        "q_bad",
        "bad",
        EditorQuestionType.SELECT_ONE,
        validation = EditorValidation(customExpression = ". + ("),
      )
    assertTrue(
      ValidationRules.issues(badQ).any {
        it.messageKey == EditorIssueMessage.CUSTOM_EXPRESSION_INVALID
      }
    )
  }

  @Test
  fun conditionalRequiredness_compilesRoundTripsAndEnforcesInEngine() {
    val disturbed =
      q(
        "q1",
        "disturbed",
        EditorQuestionType.SELECT_ONE,
        choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
      )
    val cause =
      q(
        "q2",
        "cause",
        EditorQuestionType.TEXT,
        requiredCondition = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
      )
    val form =
      EditorForm(
        formId = "f_req",
        title = "Conditional Required",
        questions = listOf(disturbed, cause),
      )

    assertEquals("/data/disturbed = 'yes'", EditorXFormsGenerator.requiredExpression(form, cause))

    // Round-trip through XForms XML.
    val xml = EditorXFormsGenerator.toXml(form)
    val reimported = FormImport.fromXml(xml)!!.form
    val reCause = reimported.questions[1]
    assertFalse(reCause.required)
    assertEquals(reimported.questions[0].key, reCause.requiredCondition?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, reCause.requiredCondition?.operator)
    assertEquals("yes", reCause.requiredCondition?.value)

    // Verify in FormEngine runtime.
    val session = FormSession(EditorXFormsGenerator.compile(form).proto)
    session.updateString("disturbed", "no")
    assertTrue(session.state.isValid)
    session.updateString("disturbed", "yes")
    assertFalse(session.state.isValid)
    assertTrue(
      session.state.validationErrors.any { it.kind == ValidationErrorKind.REQUIRED_MISSING }
    )
  }

  @Test
  fun adaptToType_preservesCustomExpressionAndConditionAcrossEditableTypes() {
    val cond = EditorRelevance("q0", RelevanceOperator.IS_ANSWERED)
    val original =
      EditorValidation(
        min = "1",
        max = "10",
        customExpression = ". != 5",
        condition = cond,
        message = "No fives",
      )
    val adapted =
      ValidationRules.adaptToType(EditorQuestionType.NUMBER, EditorQuestionType.TEXT, original)
    assertNotNull(adapted)
    assertEquals("", adapted.min)
    assertEquals("", adapted.max)
    assertEquals(". != 5", adapted.customExpression)
    assertEquals(cond, adapted.condition)
    assertEquals("No fives", adapted.message)

    assertNull(
      ValidationRules.adaptToType(EditorQuestionType.NUMBER, EditorQuestionType.NOTE, original)
    )
  }
}
