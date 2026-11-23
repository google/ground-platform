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
package org.openforis.collectone.v2.web.ui.formeditor

import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.withRenamedTargetDataset
import org.openforis.collectone.v2.app.domain.model.editor.withRenamedTargetProperty
import org.openforis.collectone.v2.web.domain.model.editor.SaveToModeSwitch

/**
 * [SaveToActions] of the Form editor: whether the Form adds features or updates existing ones, and
 * how it finds and fills the feature to update. Also follows renames of the targeted dataset.
 */
internal class FormSaveToHandler(private val session: FormDraftSession) : SaveToActions {
  private val saveTarget: EditorDataset?
    get() = SaveToRules.saveTarget(session.form, session.datasets)

  override fun setSaveToMode(mode: SaveToMode) {
    val saveTo = session.form.saveTo
    if (saveTo.mode == mode) return
    val datasets = session.datasets
    val updated = SaveToModeSwitch.saveToFor(session.form, datasets, mode)
    session.mutateForm {
      val next = it.copy(saveTo = updated)
      next.copy(openInGoogleEarth = next.openInGoogleEarth && next.canOpenInGoogleEarth(datasets))
    }
  }

  override fun setTargetDataset(datasetId: String) {
    val datasets = session.datasets
    val target = datasets.firstOrNull { it.id == datasetId } ?: return
    if (session.form.saveTo.targetDatasetId == datasetId) return
    val updated = SaveToModeSwitch.defaultsFor(session.form, session.form.saveTo, target)
    session.mutateForm {
      val next = it.copy(saveTo = updated)
      next.copy(openInGoogleEarth = next.openInGoogleEarth && next.canOpenInGoogleEarth(datasets))
    }
  }

  override fun setIdSource(source: EntityIdSource) {
    val saveTo = session.form.saveTo
    if (saveTo.idSource == source) return
    val datasets = session.datasets
    val target = saveTarget
    val updated =
      if (source == EntityIdSource.QUESTION && target != null) {
        val question =
          session.form.find(saveTo.idQuestionKey)
            ?: SaveToRules.defaultIdQuestion(session.form, target)
        saveTo.copy(
          idSource = source,
          idQuestionKey = question?.key,
          idMatchProperty = saveTo.idMatchProperty ?: target.keyProperty,
        )
      } else {
        saveTo.copy(idSource = source)
      }
    session.mutateForm {
      val next = it.copy(saveTo = updated)
      next.copy(openInGoogleEarth = next.openInGoogleEarth && next.canOpenInGoogleEarth(datasets))
    }
  }

  override fun setIdQuestion(questionKey: String) = session.mutateForm {
    it.copy(saveTo = it.saveTo.copy(idQuestionKey = questionKey))
  }

  override fun setIdMatchProperty(property: String) = session.mutateForm {
    it.copy(saveTo = it.saveTo.copy(idMatchProperty = property))
  }

  override fun setMapping(questionKey: String, property: String?) = session.mutateForm {
    it.copy(saveTo = it.saveTo.withMapping(questionKey, property))
  }

  /** Follows a rename of dataset [oldId] so this Form keeps updating it. */
  fun renameTargetDataset(oldId: String, newId: String) = session.mutateForm {
    it.withRenamedTargetDataset(oldId, newId)
  }

  /**
   * Follows a rename of property [oldName] of dataset [datasetId] in lookup, mappings, and status
   * rules.
   */
  fun renameTargetProperty(datasetId: String, oldName: String, newName: String) {
    val target = saveTarget
    session.mutateForm { it.withRenamedTargetProperty(datasetId, oldName, newName, target) }
  }
}
