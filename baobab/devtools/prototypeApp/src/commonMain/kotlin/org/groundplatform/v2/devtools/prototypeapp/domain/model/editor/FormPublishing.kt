/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

/**
 * Projections from the Survey editor's entity datasets to the Form editor's catalog and the
 * published XForms of a Form.
 */

/** The Form editor's view of this dataset, from the point of view of Form [formKey]. */
internal fun EntityDataset.toEditorDataset(
  formKey: String,
  forms: List<SurveyEditorForm> = emptyList(),
): EditorDataset =
  EditorDataset(
    id = id,
    displayName = displayName,
    isMapLayer = kind == DatasetKind.MAP_LAYER,
    keyProperty = keyProperty,
    labelProperty = labelProperty,
    properties =
      properties.map { p ->
        EditorDatasetProperty(
          name = p.name,
          label = p.label,
          kind =
            when (p.type) {
              PropertyType.TEXT -> EditorPropertyKind.TEXT
              PropertyType.INTEGER -> EditorPropertyKind.INTEGER
              PropertyType.DECIMAL -> EditorPropertyKind.DECIMAL
              PropertyType.BOOLEAN -> EditorPropertyKind.BOOLEAN
              PropertyType.DATE -> EditorPropertyKind.DATE
            },
        )
      },
    rows =
      rows.map { row ->
        EditorDatasetRow(
          name = row.values[keyProperty]?.takeIf { it.isNotBlank() } ?: row.key,
          label = labelOf(row),
          values = row.values,
        )
      },
    isLinkedToThisForm = linkedFormKey == formKey,
    key = key,
    linkedFormKey = linkedFormKey,
    linkedFormTitle = linkedFormKey?.let { fk -> forms.firstOrNull { it.key == fk }?.form?.title },
    isGenerated = isGenerated,
  )

/**
 * XForms published for [entry], including its save-to logic. Features of an updated dataset are
 * embedded, since published Forms have no CSV attachments in this prototype.
 */
fun SurveyEditorDraft.publishedFormXml(entry: SurveyEditorForm): String {
  val catalog = datasets.map { it.toEditorDataset(entry.key, forms) }
  return EditorXFormsGenerator.toXml(
    entry.form,
    SaveToRules.saveTarget(entry.form, catalog),
    inlineRows = true,
    datasets = catalog,
  )
}
