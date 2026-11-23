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

import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorFieldMapping
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules

/**
 * Rules for switching a Form between adding new features ([SaveToMode.CREATE]) and updating
 * existing ones ([SaveToMode.UPDATE]).
 */
object SaveToModeSwitch {
  /**
   * [form]'s save-to settings for [mode]. Switching to updates keeps a valid target, otherwise
   * picks the first dataset of [catalog] the Form doesn't add to, with a default lookup and field
   * mapping. [catalog] must still mark the dataset linked to the Form, so it isn't picked.
   */
  fun saveToFor(form: EditorForm, catalog: List<EditorDataset>, mode: SaveToMode): EditorSaveTo {
    val saveTo = form.saveTo
    if (saveTo.mode == mode) return saveTo
    val switched = saveTo.copy(mode = mode)
    return if (mode == SaveToMode.UPDATE) withUpdateTarget(form, switched, catalog) else switched
  }

  /** [saveTo] keeping its target if [catalog] still offers it, otherwise the first one offered. */
  private fun withUpdateTarget(
    form: EditorForm,
    saveTo: EditorSaveTo,
    catalog: List<EditorDataset>,
  ): EditorSaveTo {
    val targets = catalog.filterNot { it.isLinkedToThisForm }
    return when {
      targets.any { it.id == saveTo.targetDatasetId } -> saveTo
      targets.isEmpty() -> saveTo.copy(targetDatasetId = null)
      else -> defaultsFor(form, saveTo, targets.first())
    }
  }

  /**
   * [saveTo] retargeted to update features of [target]. Map layers default to the feature selected
   * on the map; Data tables to a question's answer.
   */
  fun defaultsFor(form: EditorForm, saveTo: EditorSaveTo, target: EditorDataset): EditorSaveTo {
    val idQuestion = if (target.isMapLayer) null else SaveToRules.defaultIdQuestion(form, target)
    return saveTo.copy(
      targetDatasetId = target.id,
      idSource =
        if (target.isMapLayer) EntityIdSource.SELECTED_FEATURE else EntityIdSource.QUESTION,
      idQuestionKey = idQuestion?.key,
      idMatchProperty = target.keyProperty,
      mappings = SaveToRules.autoMap(form, target, skipKey = idQuestion?.key),
    )
  }

  /**
   * [datasets] with those linked to Form [formKey] detached: deleted while they have no features
   * yet, otherwise kept but unlinked.
   */
  fun detachLinkedDatasets(datasets: List<EntityDataset>, formKey: String): List<EntityDataset> {
    val emptyLinkedKeys =
      datasets.filter { it.linkedFormKey == formKey && it.rows.isEmpty() }.map { it.key }.toSet()
    return datasets
      .filterNot { it.key in emptyLinkedKeys }
      .map { if (it.linkedFormKey == formKey) it.copy(linkedFormKey = null) else it }
  }

  /**
   * Field mappings for [form] once it updates [target]: every savable question except
   * [idQuestionKey] saves to the property of its name, and the primary geometry question to the
   * feature geometry of a hand-made Map layer. Other geometry questions aren't saved.
   */
  fun defaultUpdateMappings(
    form: EditorForm,
    idQuestionKey: String?,
    target: EntityDataset,
  ): List<EditorFieldMapping> {
    val geometryKey = form.primaryGeometryQuestion?.key
    return SaveToRules.savableQuestions(form)
      .filter { it.key != idQuestionKey }
      .mapNotNull { question ->
        val property =
          when {
            target.kind == DatasetKind.MAP_LAYER && question.key == geometryKey ->
              if (target.isGenerated) null else SaveToRules.GEOMETRY_PROPERTY
            question.type.isGeometry -> null
            else -> question.name
          }
        property?.let { EditorFieldMapping(question.key, it) }
      }
  }
}
