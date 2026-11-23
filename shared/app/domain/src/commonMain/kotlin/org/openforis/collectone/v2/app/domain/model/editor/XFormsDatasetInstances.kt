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

import openforis.collectone.v1.forms.SecondaryInstance
import org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow

/**
 * Secondary instances (`<instance id="…" src="jr://file-csv/….csv">`) through which a generated
 * Form reads a Map layer or Data table: the features a Form updates, or the rows a dataset-backed
 * choice question lists. Following ODK Entities, each row has `name` (the feature ID), `label`, and
 * `__version`.
 */
internal object XFormsDatasetInstances {
  /** Secondary instance listing the target's features, in ODK Entities list form. */
  fun forDataset(
    target: EditorDataset,
    inlineRows: Boolean,
  ): SecondaryInstance {
    val src = "jr://file-csv/${target.id}.csv"
    if (!inlineRows) {
      return SecondaryInstance(id = target.id, uri = src)
    }
    val columns = target.updatableProperties.map { it.name }.filter { XML_NAME.matches(it) }
    val rows =
      target.rows.map { row ->
        val cols = buildMap {
          put("name", row.name)
          put("label", row.label)
          put("__version", row.values["__version"] ?: "1")
          columns.forEach { column -> put(column, row.values[column].orEmpty()) }
        }
        secondaryInstanceRow(cols)
      }
    return SecondaryInstance(
      id = target.id,
      uri = src,
      root_name = if (rows.isEmpty()) "root" else "",
      rows = rows,
    )
  }

  /**
   * Secondary instance for a dataset-backed choice question when the dataset is not in the active
   * survey catalog (e.g., an imported standalone XForm).
   */
  fun forUnknownDataset(
    datasetId: String,
    choices: List<EditorChoice>,
    inlineRows: Boolean,
  ): SecondaryInstance {
    val src = "jr://file-csv/$datasetId.csv"
    if (!inlineRows || choices.isEmpty()) {
      return SecondaryInstance(id = datasetId, uri = src)
    }
    val rows = choices.map { choice ->
      val cols = buildMap {
        put("name", choice.value)
        put("label", choice.label)
        if (choice.parentValue.isNotBlank()) put("parent_value", choice.parentValue)
      }
      secondaryInstanceRow(cols)
    }
    return SecondaryInstance(id = datasetId, uri = src, rows = rows)
  }

  private val XML_NAME = Regex("^[A-Za-z_][A-Za-z0-9_.-]*$")
}
