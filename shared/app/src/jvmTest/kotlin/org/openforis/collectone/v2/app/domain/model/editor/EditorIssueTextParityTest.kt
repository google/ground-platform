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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.map.GeometryType

/**
 * Validation issues read in English exactly as they did when the domain built English text itself
 * (before issues carried only a [org.openforis.collectone.v2.app.domain.model.DomainMessage]).
 */
class EditorIssueTextParityTest {

  private fun q(
    key: String,
    name: String,
    type: EditorQuestionType = EditorQuestionType.TEXT,
    label: String = "Label",
    choices: List<EditorChoice> = emptyList(),
    validation: EditorValidation? = null,
    relevance: EditorRelevance? = null,
  ) =
    EditorQuestion(
      key = key,
      name = name,
      type = type,
      label = label,
      choices = choices,
      validation = validation,
      relevance = relevance,
    )

  @Test
  fun formIssues_matchEnglish() {
    val form =
      EditorForm(
        formId = "",
        title = "Form",
        questions =
          listOf(
            q("k1", "", label = ""),
            q("k2", "1bad"),
            q("k3", "dup"),
            q("k4", "dup"),
            q("k5", "choice", EditorQuestionType.SELECT_ONE),
            q(
              "k6",
              "choices",
              EditorQuestionType.SELECT_MULTIPLE,
              choices = listOf(EditorChoice("a b", "A"), EditorChoice("a b", "B")),
              validation = EditorValidation(min = "5", max = "1"),
            ),
            q("k7", "num", EditorQuestionType.NUMBER, validation = EditorValidation("x", "y")),
            q(
              "k8",
              "txt",
              validation = EditorValidation(min = "-1", max = "z", pattern = TextPattern.CUSTOM),
            ),
            q(
              "k9",
              "date",
              EditorQuestionType.DATE,
              validation = EditorValidation(dateRule = DateRule.BETWEEN),
            ),
            q("k10", "rel1", relevance = EditorRelevance("missing")),
            q("k11", "rel2", relevance = EditorRelevance("k12")),
            q("k12", "later", EditorQuestionType.NUMBER),
            q(
              "k13",
              "rel3",
              relevance = EditorRelevance("k12", RelevanceOperator.GREATER_THAN, "abc"),
            ),
            q("k14", "rel4", relevance = EditorRelevance("k12", RelevanceOperator.EQUALS, "")),
            q("k15", "rel5", relevance = EditorRelevance("k2", RelevanceOperator.INCLUDES, "a")),
          ),
      )
    val issues = FormEditorValidator.validate(form)
    assertEquals(
      listOf(
        null to "Form is missing its ID.",
        "k1" to "Name is required.",
        "k1" to "Label is empty.",
        "k2" to "Name must start with a letter and use only letters, digits, _ . -",
        "k3" to "Name \"dup\" is used more than once.",
        "k4" to "Name \"dup\" is used more than once.",
        "k5" to "Add at least one choice.",
        "k6" to "Choice values must be non-empty and contain no spaces.",
        "k6" to "Choice values must be unique.",
        "k6" to "Minimum number of selections can't be more than maximum.",
        "k6" to "Minimum number of selections is more than the number of choices.",
        "k7" to "Minimum must be a number.",
        "k7" to "Maximum must be a number.",
        "k8" to "Minimum length must be a whole number of 0 or more.",
        "k8" to "Maximum length must be a whole number of 0 or more.",
        "k8" to "Enter a custom pattern.",
        "k9" to "Pick an earliest or latest date.",
        "k10" to "Display logic refers to a deleted question.",
        "k11" to "Display logic depends on \"later\", which comes later.",
        "k13" to "Display logic value must be a number.",
        "k14" to "Display logic needs a value to compare against.",
        "k15" to "\"includes\" doesn't apply to Short text.",
      ),
      runBlocking { issues.map { it.questionKey to it.message.toUiText().resolveString() } },
    )
  }

  @Test
  fun datasetIssues_matchEnglish() {
    val dataset =
      EntityDataset(
        key = "d",
        kind = DatasetKind.MAP_LAYER,
        id = "1x",
        displayName = "",
        geometryType = GeometryType.POLYGON,
        keyProperty = "missing_key",
        labelProperty = "missing_label",
        properties =
          listOf(
            EntityProperty("geometry", "Geometry"),
            EntityProperty("count", "Count", PropertyType.INTEGER, required = true),
            EntityProperty("count", "Count again", PropertyType.DECIMAL),
          ),
        rows =
          listOf(
            EntityRow("r1", mapOf("count" to "abc")),
            EntityRow("r2", mapOf("count" to "")),
          ),
      )
    val issues = EntityDatasetValidator.validate(dataset)
    assertEquals(
      listOf(
        null to "ID must start with a letter and use only letters, digits, _ . -",
        null to "Name is required.",
        null to "Property \"geometry\" has an invalid name.",
        null to "Property names must be unique.",
        null to "Choose a key property.",
        null to "Choose a label property.",
        "r1" to "\"(unnamed)\" is missing a key.",
        "r1" to "\"(unnamed)\": Count must be integer.",
        "r1" to "\"(unnamed)\": Count again must be decimal.",
        "r1" to "\"(unnamed)\" needs at least 3 vertices.",
        "r2" to "\"(unnamed)\" is missing a key.",
        "r2" to "\"(unnamed)\" is missing Count.",
        "r2" to "\"(unnamed)\" needs at least 3 vertices.",
      ),
      runBlocking { issues.map { it.rowKey to it.message.toUiText().resolveString() } },
    )
  }

  @Test
  fun sharingIssue_matchesEnglish() {
    val sharing =
      SharingSettings(ownerEmail = "owner@example.com", policy = SharingPolicy.ORGANIZATION)
    assertEquals(
      listOf(
        "\"Anyone in the organization\" needs an organization. Choose one in Survey details or pick another " +
          "general access option."
      ),
      runBlocking {
        SurveyAccess.issues(sharing, organizationId = null).map { it.toUiText().resolveString() }
      },
    )
  }

  @Test
  fun googleEarthIncompatibleIssue_matchesEnglish() {
    val photoQuestion =
      q("k1", "canopy_photo", type = EditorQuestionType.PHOTO, label = "Canopy photo")
    assertEquals(
      "\"Canopy photo\" (Photo) isn't supported in Google Earth. Remove it, change its question type, " +
        "or turn off Enable Open in Google Earth.",
      runBlocking {
        FormEditorValidator.googleEarthIncompatibleMessage(photoQuestion).toUiText().resolveString()
      },
    )
  }
}
