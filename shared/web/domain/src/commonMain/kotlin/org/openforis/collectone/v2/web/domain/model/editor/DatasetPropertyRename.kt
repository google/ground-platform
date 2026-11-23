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

import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.toEditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.withRenamedTargetProperty

/** Replacing a property of a Map layer or Data table, and following a rename everywhere. */
object DatasetPropertyRename {
  /**
   * [draft] with property [index] of dataset [datasetKey] replaced by [property]. When the name
   * changes, the dataset's key and label properties, every row's cell, and the lookup, mappings,
   * and status rules of Forms that update the dataset follow the new name.
   */
  fun apply(
    draft: SurveyEditorDraft,
    datasetKey: String,
    index: Int,
    property: EntityProperty,
  ): SurveyEditorDraft {
    val dataset = draft.datasets.firstOrNull { it.key == datasetKey } ?: return draft
    val old = dataset.properties.getOrNull(index) ?: return draft
    val renamed = old.name != property.name
    val updated =
      dataset.copy(
        properties = dataset.properties.toMutableList().apply { set(index, property) },
        keyProperty =
          if (renamed && dataset.keyProperty == old.name) property.name else dataset.keyProperty,
        labelProperty =
          if (renamed && dataset.labelProperty == old.name) property.name
          else dataset.labelProperty,
        rows =
          if (!renamed) dataset.rows
          else
            dataset.rows.map { row ->
              val value = row.values[old.name]
              if (value == null) row
              else row.copy(values = row.values - old.name + (property.name to value))
            },
      )
    val datasets = draft.datasets.map { if (it.key == datasetKey) updated else it }
    if (!renamed) return draft.copy(datasets = datasets)
    val forms =
      draft.forms.map { entry ->
        val catalog = datasets.map { it.toEditorDataset(entry.key, draft.forms) }
        val saveTarget = SaveToRules.saveTarget(entry.form, catalog)
        entry.copy(
          form =
            entry.form.withRenamedTargetProperty(dataset.id, old.name, property.name, saveTarget)
        )
      }
    return draft.copy(datasets = datasets, forms = forms)
  }
}
