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
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.messageKey

class FormEditorValidatorTest {
  private fun question(
    key: String,
    name: String = key,
    type: EditorQuestionType = EditorQuestionType.TEXT,
    label: String = "Label $key",
    choices: List<EditorChoice> = emptyList(),
    choiceDatasetId: String? = null,
    allowAddEntity: Boolean = false,
    relevance: EditorRelevance? = null,
    capture: GeometryCapture = GeometryCapture.GPS_ONLY,
  ) =
    EditorQuestion(
      key = key,
      name = name,
      type = type,
      label = label,
      choices = choices,
      choiceDatasetId = choiceDatasetId,
      allowAddEntity = allowAddEntity,
      relevance = relevance,
      capture = capture,
    )

  private fun form(
    vararg questions: EditorQuestion,
    formId: String = "form-1",
    availability: FormAvailability = FormAvailability.MOBILE,
    openInGoogleEarth: Boolean = false,
  ) =
    EditorForm(
      formId = formId,
      title = "Form",
      questions = questions.toList(),
      availability = availability,
      openInGoogleEarth = openInGoogleEarth,
    )

  private fun dataset(
    id: String,
    isMapLayer: Boolean = true,
    isGenerated: Boolean = false,
    key: String = "",
    linkedFormKey: String? = null,
  ) =
    EditorDataset(
      id = id,
      displayName = "Dataset $id",
      isMapLayer = isMapLayer,
      keyProperty = "name",
      labelProperty = "label",
      properties = emptyList(),
      key = key,
      linkedFormKey = linkedFormKey,
      isGenerated = isGenerated,
    )

  private fun messages(form: EditorForm, datasets: List<EditorDataset> = emptyList()) =
    FormEditorValidator.validate(form, datasets).map { it.questionKey to it.message.messageKey }

  @Test
  fun validForm_hasNoIssues() {
    assertEquals(emptyList(), messages(form(question("q1"), question("q2"))))
  }

  @Test
  fun isValidName_followsXmlNameRulesAndRejectsReservedNames() {
    assertTrue(FormEditorValidator.isValidName("tree_count"))
    assertTrue(FormEditorValidator.isValidName("_a.b-c"))
    assertFalse(FormEditorValidator.isValidName("1st"))
    assertFalse(FormEditorValidator.isValidName("has space"))
    assertFalse(FormEditorValidator.isValidName("meta"))
    assertFalse(FormEditorValidator.isValidName("data"))
  }

  @Test
  fun blankFormId_isReportedWithoutAQuestion() {
    assertEquals(
      listOf(null to EditorIssueMessage.FORM_MISSING_ID),
      messages(form(question("q1"), formId = " ")),
    )
  }

  @Test
  fun names_mustBePresentValidAndUnique() {
    val issues =
      messages(
        form(
          question("q1", name = ""),
          question("q2", name = "2bad"),
          question("q3", name = "dup"),
          question("q4", name = "dup"),
        )
      )

    assertEquals(
      listOf(
        "q1" to EditorIssueMessage.NAME_REQUIRED,
        "q2" to EditorIssueMessage.NAME_INVALID,
        "q3" to EditorIssueMessage.NAME_DUPLICATE,
        "q4" to EditorIssueMessage.NAME_DUPLICATE,
      ),
      issues,
    )
  }

  @Test
  fun blankLabel_isReported() {
    assertEquals(
      listOf("q1" to EditorIssueMessage.LABEL_EMPTY),
      messages(form(question("q1", label = " "))),
    )
  }

  @Test
  fun manualChoices_mustExistAndHaveUniqueValuesWithoutSpaces() {
    val select = EditorQuestionType.SELECT_ONE
    val issues =
      messages(
        form(
          question("empty", type = select),
          question(
            "spaces",
            type = select,
            choices = listOf(EditorChoice("a b", "A"), EditorChoice("", "Blank")),
          ),
          question(
            "dups",
            type = select,
            choices = listOf(EditorChoice("a", "A"), EditorChoice("a", "A2")),
          ),
        )
      )

    assertEquals(
      listOf(
        "empty" to EditorIssueMessage.ADD_CHOICE,
        "spaces" to EditorIssueMessage.CHOICE_VALUE_INVALID,
        "dups" to EditorIssueMessage.CHOICE_VALUE_DUPLICATE,
      ),
      issues,
    )
  }

  @Test
  fun datasetChoices_mustPointAtAnExistingDataset() {
    val q = question("q1", type = EditorQuestionType.SELECT_ONE, choiceDatasetId = "gone")

    assertEquals(
      listOf("q1" to EditorIssueMessage.CHOICE_DATASET_MISSING),
      messages(form(q), datasets = listOf(dataset("other"))),
    )
    // Without dataset information (e.g. while it loads) the reference isn't checked.
    assertEquals(emptyList(), messages(form(q)))
  }

  @Test
  fun datasetChoicesWithBlankId_askForADatasetOfTheChosenSource() {
    val mapLayer =
      EditorQuestion(
        key = "q1",
        name = "q1",
        type = EditorQuestionType.SELECT_ONE,
        label = "Plot",
        choiceDatasetId = " ",
      )

    // A blank ID doesn't count as dataset choices: the question needs manual choices instead.
    assertEquals(listOf("q1" to EditorIssueMessage.ADD_CHOICE), messages(form(mapLayer)))
  }

  @Test
  fun addingFeaturesFromAQuestion_needsADatasetThatCanTakeThem() {
    val q =
      question(
        "q1",
        type = EditorQuestionType.SELECT_ONE,
        choiceDatasetId = "plots",
        allowAddEntity = true,
      )

    assertEquals(
      listOf("q1" to EditorIssueMessage.CHOICE_DATASET_GENERATED),
      messages(form(q), listOf(dataset("plots", isGenerated = true))),
    )
    assertEquals(
      listOf("q1" to EditorIssueMessage.CHOICE_DATASET_NO_CREATION_FORM_MAP_LAYER),
      messages(form(q), listOf(dataset("plots", key = "k"))),
    )
    assertEquals(
      listOf("q1" to EditorIssueMessage.CHOICE_DATASET_NO_CREATION_FORM_DATA_TABLE),
      messages(form(q), listOf(dataset("plots", isMapLayer = false, key = "k"))),
    )
    assertEquals(
      emptyList(),
      messages(form(q), listOf(dataset("plots", key = "k", linkedFormKey = "creator"))),
    )
  }

  @Test
  fun displayLogic_sourceMustExistAndComeEarlier() {
    val issues =
      messages(
        form(
          question("q1", relevance = EditorRelevance("gone")),
          question("q2", relevance = EditorRelevance("q3")),
          question("q3"),
        )
      )

    assertEquals(
      listOf(
        "q1" to EditorIssueMessage.DISPLAY_LOGIC_SOURCE_DELETED,
        "q2" to EditorIssueMessage.DISPLAY_LOGIC_SOURCE_LATER,
      ),
      issues,
    )
  }

  @Test
  fun displayLogic_operatorAndValueMustSuitTheSource() {
    val number = question("n", type = EditorQuestionType.NUMBER)
    val photo = question("p", type = EditorQuestionType.PHOTO)
    val issues =
      messages(
        form(
          number,
          photo,
          question("a", relevance = EditorRelevance("p", RelevanceOperator.EQUALS, "x")),
          question("b", relevance = EditorRelevance("n", RelevanceOperator.GREATER_THAN, "")),
          question("c", relevance = EditorRelevance("n", RelevanceOperator.GREATER_THAN, "ten")),
          question("d", relevance = EditorRelevance("n", RelevanceOperator.IS_ANSWERED)),
        )
      )

    assertEquals(
      listOf(
        "a" to EditorIssueMessage.DISPLAY_LOGIC_OPERATOR_NOT_APPLICABLE,
        "b" to EditorIssueMessage.DISPLAY_LOGIC_VALUE_MISSING,
        "c" to EditorIssueMessage.DISPLAY_LOGIC_VALUE_NOT_NUMBER,
      ),
      issues,
    )
  }

  @Test
  fun geometryQuestions_withoutGpsLockAreValidOnWeb() {
    val point = question("loc", type = EditorQuestionType.LOCATION)
    val line = question("line", type = EditorQuestionType.LINE)
    val polygon = question("poly", type = EditorQuestionType.POLYGON)
    val mapPlaced =
      question("loc2", type = EditorQuestionType.POLYGON, capture = GeometryCapture.GPS_OR_MAP)

    assertEquals(emptyList(), messages(form(point, line, polygon, mapPlaced)))
    assertEquals(
      emptyList(),
      messages(form(point, line, polygon, mapPlaced, availability = FormAvailability.BOTH)),
    )
    assertEquals(
      emptyList(),
      messages(form(point, line, polygon, mapPlaced, availability = FormAvailability.WEB)),
    )
  }

  @Test
  fun googleEarthIncompatibleQuestions_areAnErrorOnlyWhenOpenInGoogleEarthIsEnabled() {
    val text = question("t", type = EditorQuestionType.TEXT)
    val longText = question("lt", type = EditorQuestionType.LONG_TEXT)
    val number = question("n", type = EditorQuestionType.NUMBER)
    val selectOne =
      question(
        "so",
        type = EditorQuestionType.SELECT_ONE,
        choices = listOf(EditorChoice("one", "One")),
      )
    val selectMulti =
      question(
        "sm",
        type = EditorQuestionType.SELECT_MULTIPLE,
        choices = listOf(EditorChoice("one", "One")),
      )
    val date = question("d", type = EditorQuestionType.DATE)
    val note = question("note", type = EditorQuestionType.NOTE)
    val location =
      question("loc", type = EditorQuestionType.LOCATION, capture = GeometryCapture.GPS_OR_MAP)
    val line =
      question("line", type = EditorQuestionType.LINE, capture = GeometryCapture.GPS_OR_MAP)
    val polygon =
      question("poly", type = EditorQuestionType.POLYGON, capture = GeometryCapture.GPS_OR_MAP)
    val photo = question("photo", type = EditorQuestionType.PHOTO)
    val video = question("video", type = EditorQuestionType.VIDEO)
    val audio = question("audio", type = EditorQuestionType.AUDIO)

    val allQuestions =
      arrayOf(
        text,
        longText,
        number,
        selectOne,
        selectMulti,
        date,
        note,
        location,
        line,
        polygon,
        photo,
        video,
        audio,
      )

    assertEquals(
      emptyList(),
      messages(
        form(*allQuestions, availability = FormAvailability.BOTH, openInGoogleEarth = false)
      ),
    )
    assertEquals(
      emptyList(),
      messages(
        form(*allQuestions, availability = FormAvailability.MOBILE, openInGoogleEarth = true)
      ),
    )
    assertEquals(
      listOf(
        "loc" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
        "line" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
        "poly" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
        "photo" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
        "video" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
        "audio" to EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
      ),
      messages(form(*allQuestions, availability = FormAvailability.BOTH, openInGoogleEarth = true)),
    )
  }

  @Test
  fun googleEarthIncompatibleMessage_fallsBackToTheNameWhenTheLabelIsBlank() {
    val message =
      FormEditorValidator.googleEarthIncompatibleMessage(
        question("photo", name = "site_photo", type = EditorQuestionType.PHOTO, label = "")
      )

    assertEquals(
      listOf("site_photo", DomainMessage.Label(EditorQuestionType.PHOTO)),
      (message as DomainMessage.Text).args,
    )
  }
}
