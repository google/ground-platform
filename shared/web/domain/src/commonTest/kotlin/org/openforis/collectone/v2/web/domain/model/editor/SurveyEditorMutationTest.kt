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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm

class SurveyEditorMutationTest {
  private fun sampleDataset(key: String, kind: DatasetKind = DatasetKind.MAP_LAYER) =
    EntityDataset(
      key = key,
      kind = kind,
      id = "ds_$key",
      displayName = "Dataset $key",
      keyProperty = "id",
      labelProperty = "id",
      properties = listOf(EntityProperty("id", "ID")),
    )

  @Test
  fun between_returnsNullWhenNothingChanged_andRewindsAndReplaysAllMutationSteps() {
    val form1 = SurveyEditorForm("f1", EditorForm("id1", "Form 1", emptyList()))
    val form2 = SurveyEditorForm("f2", EditorForm("id2", "Form 2", emptyList()))
    val form3 = SurveyEditorForm("f3", EditorForm("id3", "Form 3", emptyList()))
    val ds1 = sampleDataset("d1", DatasetKind.MAP_LAYER)
    val ds2 = sampleDataset("d2", DatasetKind.DATA_TABLE)
    val ds3 = sampleDataset("d3", DatasetKind.DATA_TABLE)

    val blank = SurveyEditorDraft.blank(surveyId = "s1")
    val before =
      blank.copy(
        details = blank.details.copy(title = "Before"),
        sharing = blank.sharing.copy(policy = SharingPolicy.RESTRICTED),
        forms = listOf(form1, form2),
        datasets = listOf(ds1, ds2),
        nextKeyId = 10,
      )
    assertNull(SurveyEditorMutation.between(before, before))

    val updatedForm2 = form2.copy(form = form2.form.copy(title = "Form 2 edited"))
    val updatedDs2 = ds2.copy(displayName = "D2 edited")
    val after =
      before.copy(
        details = before.details.copy(title = "After"),
        sharing = before.sharing.copy(policy = SharingPolicy.ORGANIZATION),
        forms = listOf(updatedForm2, form3),
        datasets = listOf(updatedDs2, ds3),
        nextKeyId = 15,
      )

    val mutation =
      assertNotNull(
        SurveyEditorMutation.between(
          before = before,
          after = after,
          formKey = "f2",
          selectedQuestionKeyBefore = "q1",
          selectedQuestionKeyAfter = "q2",
        )
      )
    assertEquals("f2", mutation.formKey)
    assertEquals("q1", mutation.selectedQuestionKeyBefore)
    assertEquals("q2", mutation.selectedQuestionKeyAfter)

    val rewound = mutation.rewind(after)
    assertEquals(before, rewound)
    val replayed = mutation.replay(rewound)
    assertEquals(after, replayed)
  }

  @Test
  fun reorderFormsAndDatasets_andUndoRedoStack_rewindAndReplayInOrder() {
    val form1 = SurveyEditorForm("f1", EditorForm("id1", "Form 1", emptyList()))
    val form2 = SurveyEditorForm("f2", EditorForm("id2", "Form 2", emptyList()))
    val ds1 = sampleDataset("d1", DatasetKind.MAP_LAYER)
    val ds2 = sampleDataset("d2", DatasetKind.DATA_TABLE)

    val draft0 =
      SurveyEditorDraft.blank(surveyId = "s1")
        .copy(
          forms = listOf(form1, form2),
          datasets = listOf(ds1, ds2),
        )
    val draft1 =
      draft0.copy(
        forms = listOf(form2, form1),
        datasets = listOf(ds2, ds1),
      )
    val draft2 = draft1.copy(details = draft1.details.copy(title = "Renamed"))

    var stack = SurveyUndoRedoStack()
    assertFalse(stack.canUndo)
    assertFalse(stack.canRedo)
    assertNull(stack.undo(draft0))
    assertNull(stack.redo(draft0))

    val m1 = assertNotNull(SurveyEditorMutation.between(draft0, draft1))
    val m2 = assertNotNull(SurveyEditorMutation.between(draft1, draft2))

    stack = stack.record(m1).record(m2)
    assertTrue(stack.canUndo)
    assertFalse(stack.canRedo)

    val u2 = assertNotNull(stack.undo(draft2))
    assertEquals(draft1, u2.draft)
    assertTrue(u2.stack.canUndo)
    assertTrue(u2.stack.canRedo)

    val u1 = assertNotNull(u2.stack.undo(u2.draft))
    assertEquals(draft0, u1.draft)
    assertFalse(u1.stack.canUndo)
    assertTrue(u1.stack.canRedo)

    val r1 = assertNotNull(u1.stack.redo(u1.draft))
    assertEquals(draft1, r1.draft)

    val r2 = assertNotNull(r1.stack.redo(r1.draft))
    assertEquals(draft2, r2.draft)
    assertFalse(r2.stack.canRedo)

    val cleared = r2.stack.clear()
    assertFalse(cleared.canUndo)
    assertFalse(cleared.canRedo)
  }
}
