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
package org.openforis.collectone.v2.web.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorFieldMapping
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.map.GeometryType

class DatasetPropertyRenameTest {
  private val trees =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.DATA_TABLE,
      id = "trees",
      displayName = "Trees",
      geometryType = GeometryType.POINT,
      keyProperty = "sp",
      labelProperty = "sp",
      properties = listOf(EntityProperty("sp", "Species"), EntityProperty("height", "Height")),
      rows = listOf(EntityRow("r1", mapOf("sp" to "oak", "height" to "12"), emptyList())),
    )

  private val updatingForm =
    EditorForm(
      formId = "remeasure",
      title = "Remeasure",
      questions = listOf(EditorQuestion("q1", "species", EditorQuestionType.TEXT, "Species")),
      saveTo =
        EditorSaveTo(
          mode = SaveToMode.UPDATE,
          targetDatasetId = "trees",
          mappings = listOf(EditorFieldMapping("q1", "sp")),
        ),
    )

  private val draft =
    SurveyEditorDraft.blank(surveyId = "s1")
      .copy(datasets = listOf(trees), forms = listOf(SurveyEditorForm("f1", updatingForm)))

  private fun renamed() =
    DatasetPropertyRename.apply(draft, "d1", 0, EntityProperty("species", "Species"))

  @Test
  fun rename_followsKeyAndLabelProperties() {
    val dataset = renamed().datasets.single()

    assertEquals("species", dataset.keyProperty)
    assertEquals("species", dataset.labelProperty)
  }

  @Test
  fun rename_movesEachRowsCell() {
    val row = renamed().datasets.single().rows.single()

    assertEquals(mapOf("height" to "12", "species" to "oak"), row.values)
  }

  @Test
  fun rename_followsMappingsOfFormsThatUpdateTheDataset() {
    val form = renamed().forms.single().form

    assertEquals(listOf(EditorFieldMapping("q1", "species")), form.saveTo.mappings)
  }

  @Test
  fun relabel_onlyReplacesTheProperty() {
    val relabeled =
      DatasetPropertyRename.apply(draft, "d1", 1, EntityProperty("height", "Tree height"))

    assertEquals(
      draft.copy(
        datasets =
          listOf(
            trees.copy(
              properties = listOf(trees.properties[0], EntityProperty("height", "Tree height"))
            )
          )
      ),
      relabeled,
    )
  }

  @Test
  fun unknownPropertyIndex_leavesTheDraftUnchanged() {
    assertEquals(draft, DatasetPropertyRename.apply(draft, "d1", 5, EntityProperty("x", "X")))
  }
}
