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
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorDatasetProperty
import org.openforis.collectone.v2.app.domain.model.editor.EditorFieldMapping
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.map.GeometryType

class SaveToModeSwitchTest {
  private val species = EditorQuestion("q1", "species", EditorQuestionType.TEXT, "Species")
  private val location = EditorQuestion("q2", "location", EditorQuestionType.LOCATION, "Location")
  private val note = EditorQuestion("q3", "intro", EditorQuestionType.NOTE, "Intro")
  private val form =
    EditorForm(formId = "trees", title = "Trees", questions = listOf(species, location, note))

  private fun catalogEntry(id: String, isLinkedToThisForm: Boolean = false) =
    EditorDataset(
      id = id,
      displayName = id,
      isMapLayer = true,
      keyProperty = "id",
      labelProperty = "id",
      properties = listOf(EditorDatasetProperty("species", "Species", EditorPropertyKind.TEXT)),
      isLinkedToThisForm = isLinkedToThisForm,
    )

  private fun dataset(
    key: String,
    linkedFormKey: String?,
    rows: Int = 0,
    generated: Boolean = false,
  ) =
    EntityDataset(
      key = key,
      kind = DatasetKind.MAP_LAYER,
      id = "layer_$key",
      displayName = "Layer $key",
      geometryType = GeometryType.POINT,
      keyProperty = "id",
      labelProperty = "id",
      properties = emptyList(),
      linkedFormKey = linkedFormKey,
      rows = List(rows) { EntityRow("$key-$it", emptyMap(), emptyList()) },
      generator = if (generated) SampleDesignConfig() else null,
    )

  @Test
  fun switchingToUpdates_targetsTheFirstDatasetTheFormDoesNotAddTo() {
    val catalog = listOf(catalogEntry("own", isLinkedToThisForm = true), catalogEntry("trees"))

    val saveTo = SaveToModeSwitch.saveToFor(form, catalog, SaveToMode.UPDATE)

    assertEquals(SaveToMode.UPDATE, saveTo.mode)
    assertEquals("trees", saveTo.targetDatasetId)
    assertEquals(EntityIdSource.SELECTED_FEATURE, saveTo.idSource)
    assertEquals("id", saveTo.idMatchProperty)
  }

  @Test
  fun switchingToUpdates_keepsAValidTarget() {
    val withTarget = form.copy(saveTo = EditorSaveTo(targetDatasetId = "plots"))
    val catalog = listOf(catalogEntry("trees"), catalogEntry("plots"))

    val saveTo = SaveToModeSwitch.saveToFor(withTarget, catalog, SaveToMode.UPDATE)

    assertEquals(EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = "plots"), saveTo)
  }

  @Test
  fun switchingToUpdates_withoutOtherDatasets_clearsTheTarget() {
    val withTarget = form.copy(saveTo = EditorSaveTo(targetDatasetId = "gone"))

    val saveTo = SaveToModeSwitch.saveToFor(withTarget, emptyList(), SaveToMode.UPDATE)

    assertEquals(null, saveTo.targetDatasetId)
  }

  @Test
  fun switchingBackToAdding_onlyChangesTheMode() {
    val updating =
      form.copy(saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = "trees"))

    val saveTo = SaveToModeSwitch.saveToFor(updating, emptyList(), SaveToMode.CREATE)

    assertEquals(EditorSaveTo(mode = SaveToMode.CREATE, targetDatasetId = "trees"), saveTo)
  }

  @Test
  fun detachLinkedDatasets_dropsEmptyOnesAndUnlinksOnesWithFeatures() {
    val datasets = listOf(dataset("a", "f1"), dataset("b", "f1", rows = 2), dataset("c", "f2"))

    val detached = SaveToModeSwitch.detachLinkedDatasets(datasets, "f1")

    assertEquals(listOf(dataset("b", null, rows = 2), dataset("c", "f2")), detached)
  }

  @Test
  fun defaultUpdateMappings_savePrimaryGeometryToHandMadeMapLayers() {
    val mappings = SaveToModeSwitch.defaultUpdateMappings(form, null, dataset("a", null))

    assertEquals(
      listOf(
        EditorFieldMapping("q1", "species"),
        EditorFieldMapping("q2", SaveToRules.GEOMETRY_PROPERTY),
      ),
      mappings,
    )
  }

  @Test
  fun defaultUpdateMappings_skipTheIdQuestionAndLockedGeometry() {
    val mappings =
      SaveToModeSwitch.defaultUpdateMappings(form, "q1", dataset("a", null, generated = true))

    assertEquals(emptyList(), mappings)
  }
}
