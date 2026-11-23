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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.web.ui.text.EditorDefaultNames

/** Properties and order of datasets, through the Survey editor's [DatasetActions]. */
class SurveyDatasetPropertiesTest {
  private val editor = surveyEditorViewModel()

  private fun dataset(key: String) = editor.ui.draft.datasets.first { it.key == key }

  @Test
  fun addProperty_addsAUniquelyNamedPropertyWithTheDefaultLabel() {
    val key = editor.ui.dataTables.first().key
    val before = dataset(key).properties.map { it.name }

    editor.addProperty(key)
    editor.addProperty(key)

    val added = dataset(key).properties.drop(before.size)
    assertEquals(2, added.size)
    assertEquals(added.size, (added.map { it.name } + before).distinct().size - before.size)
    assertTrue(added.all { it.label == EditorDefaultNames.ENGLISH.property })
  }

  @Test
  fun removeProperty_movesTheKeyAndLabelToTheFirstRemainingProperty() {
    val key = editor.ui.dataTables.first().key
    val first = dataset(key).properties.first().name
    editor.updateDataset(key) { it.copy(keyProperty = first, labelProperty = first) }

    editor.removeProperty(key, 0)

    val after = dataset(key)
    val next = after.properties.first().name
    assertTrue(after.properties.none { it.name == first })
    assertEquals(next, after.keyProperty)
    assertEquals(next, after.labelProperty)
    assertTrue(after.rows.none { first in it.values })
  }

  @Test
  fun removeProperty_outOfRange_changesNothing() {
    val key = editor.ui.dataTables.first().key
    val before = dataset(key)

    editor.removeProperty(key, before.properties.size)

    assertEquals(before, dataset(key))
  }

  @Test
  fun moveDataset_reordersOnlyItsOwnKind() {
    val tables = editor.ui.dataTables.map { it.key }
    val layers = editor.ui.mapLayers.map { it.key }

    editor.moveDataset(tables.last(), 0)

    assertEquals(listOf(tables.last()) + tables.dropLast(1), editor.ui.dataTables.map { it.key })
    assertEquals(layers, editor.ui.mapLayers.map { it.key })
    assertEquals(
      DatasetKind.DATA_TABLE,
      editor.ui.draft.datasets.first { it.key == tables.last() }.kind,
    )
  }
}
