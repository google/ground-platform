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
package org.groundplatform.v2.web.domain.model.editor

import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.SharingSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyDetails
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorForm

/**
 * An elementary reversible change to a [SurveyEditorDraft].
 *
 * Every edit in the Form editor (and Survey editor) is decomposed into one or more
 * [SurveyMutationStep]s inside a [SurveyEditorMutation] so edits that touch both a Form and its
 * linked [EntityDataset]s (or other Forms) can be rewound and replayed atomically.
 */
sealed interface SurveyMutationStep {
  /** Reverts this step on [draft]. */
  fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft

  /** Applies this step on [draft]. */
  fun replay(draft: SurveyEditorDraft): SurveyEditorDraft

  /** Replaces [SurveyEditorDraft.details]. */
  data class UpdateDetails(val before: SurveyDetails, val after: SurveyDetails) :
    SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft = draft.copy(details = before)

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft = draft.copy(details = after)
  }

  /** Replaces [SurveyEditorDraft.sharing]. */
  data class UpdateSharing(val before: SharingSettings, val after: SharingSettings) :
    SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft = draft.copy(sharing = before)

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft = draft.copy(sharing = after)
  }

  /** Replaces the [EditorForm] of Form [formKey]. */
  data class UpdateForm(val formKey: String, val before: EditorForm, val after: EditorForm) :
    SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = draft.forms.map { if (it.key == formKey) it.copy(form = before) else it })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = draft.forms.map { if (it.key == formKey) it.copy(form = after) else it })
  }

  /** Inserts [entry] at [index] in [SurveyEditorDraft.forms]. */
  data class AddForm(val index: Int, val entry: SurveyEditorForm) : SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = draft.forms.filterNot { it.key == entry.key })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(
        forms =
          draft.forms
            .filterNot { it.key == entry.key }
            .toMutableList()
            .apply { add(index.coerceIn(0, size), entry) }
      )
  }

  /** Removes [entry] from [index] in [SurveyEditorDraft.forms]. */
  data class RemoveForm(val index: Int, val entry: SurveyEditorForm) : SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(
        forms =
          draft.forms
            .filterNot { it.key == entry.key }
            .toMutableList()
            .apply { add(index.coerceIn(0, size), entry) }
      )

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = draft.forms.filterNot { it.key == entry.key })
  }

  /** Reorders [SurveyEditorDraft.forms] from [keysBefore] to [keysAfter]. */
  data class ReorderForms(val keysBefore: List<String>, val keysAfter: List<String>) :
    SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = reorderByKeys(draft.forms, keysBefore) { it.key })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(forms = reorderByKeys(draft.forms, keysAfter) { it.key })
  }

  /** Replaces dataset [datasetKey] in [SurveyEditorDraft.datasets]. */
  data class UpdateDataset(
    val datasetKey: String,
    val before: EntityDataset,
    val after: EntityDataset,
  ) : SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = draft.datasets.map { if (it.key == datasetKey) before else it })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = draft.datasets.map { if (it.key == datasetKey) after else it })
  }

  /** Inserts [dataset] at [index] in [SurveyEditorDraft.datasets]. */
  data class AddDataset(val index: Int, val dataset: EntityDataset) : SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = draft.datasets.filterNot { it.key == dataset.key })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(
        datasets =
          draft.datasets
            .filterNot { it.key == dataset.key }
            .toMutableList()
            .apply { add(index.coerceIn(0, size), dataset) }
      )
  }

  /** Removes [dataset] from [index] in [SurveyEditorDraft.datasets]. */
  data class RemoveDataset(val index: Int, val dataset: EntityDataset) : SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(
        datasets =
          draft.datasets
            .filterNot { it.key == dataset.key }
            .toMutableList()
            .apply { add(index.coerceIn(0, size), dataset) }
      )

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = draft.datasets.filterNot { it.key == dataset.key })
  }

  /** Reorders [SurveyEditorDraft.datasets] from [keysBefore] to [keysAfter]. */
  data class ReorderDatasets(val keysBefore: List<String>, val keysAfter: List<String>) :
    SurveyMutationStep {
    override fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = reorderByKeys(draft.datasets, keysBefore) { it.key })

    override fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
      draft.copy(datasets = reorderByKeys(draft.datasets, keysAfter) { it.key })
  }
}

/**
 * A reversible mutation of a [SurveyEditorDraft] recorded by the Form editor's undo/redo stack.
 *
 * Applying [rewind] reverts [steps] in reverse order and restores [nextKeyIdBefore]; applying
 * [replay] replays [steps] in forward order and restores [nextKeyIdAfter].
 */
data class SurveyEditorMutation(
  val steps: List<SurveyMutationStep>,
  val nextKeyIdBefore: Int = 100,
  val nextKeyIdAfter: Int = nextKeyIdBefore,
  /** Key of the Form that was being edited when this mutation was recorded, if any. */
  val formKey: String? = null,
  /** Selected question key in the Form editor before this mutation, or `null` for Form settings. */
  val selectedQuestionKeyBefore: String? = null,
  /** Selected question key in the Form editor after this mutation, or `null` for Form settings. */
  val selectedQuestionKeyAfter: String? = null,
) {
  /** Rewinds this mutation on [draft], restoring the state before the mutation was applied. */
  fun rewind(draft: SurveyEditorDraft): SurveyEditorDraft =
    steps
      .asReversed()
      .fold(draft) { current, step -> step.rewind(current) }
      .copy(nextKeyId = nextKeyIdBefore)

  /** Replays this mutation on [draft], re-applying the state after the mutation. */
  fun replay(draft: SurveyEditorDraft): SurveyEditorDraft =
    steps.fold(draft) { current, step -> step.replay(current) }.copy(nextKeyId = nextKeyIdAfter)

  companion object {
    /**
     * Computes the [SurveyEditorMutation] that transforms [before] into [after], or returns `null`
     * when no survey content changed.
     */
    fun between(
      before: SurveyEditorDraft,
      after: SurveyEditorDraft,
      formKey: String? = null,
      selectedQuestionKeyBefore: String? = null,
      selectedQuestionKeyAfter: String? = null,
    ): SurveyEditorMutation? {
      val steps = buildList {
        if (before.details != after.details) {
          add(SurveyMutationStep.UpdateDetails(before.details, after.details))
        }
        if (before.sharing != after.sharing) {
          add(SurveyMutationStep.UpdateSharing(before.sharing, after.sharing))
        }
        addAll(diffForms(before.forms, after.forms))
        addAll(diffDatasets(before.datasets, after.datasets))
      }
      if (steps.isEmpty()) return null
      return SurveyEditorMutation(
        steps = steps,
        nextKeyIdBefore = before.nextKeyId,
        nextKeyIdAfter = after.nextKeyId,
        formKey = formKey,
        selectedQuestionKeyBefore = selectedQuestionKeyBefore,
        selectedQuestionKeyAfter = selectedQuestionKeyAfter,
      )
    }

    private fun diffForms(
      before: List<SurveyEditorForm>,
      after: List<SurveyEditorForm>,
    ): List<SurveyMutationStep> = buildList {
      val beforeByKey = before.associateBy { it.key }
      val afterByKey = after.associateBy { it.key }
      for (index in before.indices.reversed()) {
        val entry = before[index]
        if (entry.key !in afterByKey) {
          add(SurveyMutationStep.RemoveForm(index, entry))
        }
      }
      val survivingBeforeKeys = before.map { it.key }.filter { it in afterByKey }
      val survivingAfterKeys = after.map { it.key }.filter { it in beforeByKey }
      if (survivingBeforeKeys != survivingAfterKeys) {
        add(SurveyMutationStep.ReorderForms(survivingBeforeKeys, survivingAfterKeys))
      }
      for ((index, entry) in after.withIndex()) {
        if (entry.key !in beforeByKey) {
          add(SurveyMutationStep.AddForm(index, entry))
        }
      }
      for (entry in after) {
        val prev = beforeByKey[entry.key] ?: continue
        if (prev.form != entry.form) {
          add(SurveyMutationStep.UpdateForm(entry.key, prev.form, entry.form))
        }
      }
    }

    private fun diffDatasets(
      before: List<EntityDataset>,
      after: List<EntityDataset>,
    ): List<SurveyMutationStep> = buildList {
      val beforeByKey = before.associateBy { it.key }
      val afterByKey = after.associateBy { it.key }
      for (index in before.indices.reversed()) {
        val dataset = before[index]
        if (dataset.key !in afterByKey) {
          add(SurveyMutationStep.RemoveDataset(index, dataset))
        }
      }
      val survivingBeforeKeys = before.map { it.key }.filter { it in afterByKey }
      val survivingAfterKeys = after.map { it.key }.filter { it in beforeByKey }
      if (survivingBeforeKeys != survivingAfterKeys) {
        add(SurveyMutationStep.ReorderDatasets(survivingBeforeKeys, survivingAfterKeys))
      }
      for ((index, dataset) in after.withIndex()) {
        if (dataset.key !in beforeByKey) {
          add(SurveyMutationStep.AddDataset(index, dataset))
        }
      }
      for (dataset in after) {
        val prev = beforeByKey[dataset.key] ?: continue
        if (prev != dataset) {
          add(SurveyMutationStep.UpdateDataset(dataset.key, prev, dataset))
        }
      }
    }
  }
}

/** Result of rewinding or replaying a [SurveyEditorMutation] on a [SurveyUndoRedoStack]. */
data class SurveyUndoRedoTransition(
  val stack: SurveyUndoRedoStack,
  val draft: SurveyEditorDraft,
  val mutation: SurveyEditorMutation,
)

/**
 * Immutable undo/redo history of [SurveyEditorMutation]s.
 *
 * Recording a new mutation pushes it onto [undoStack] and clears [redoStack]. Calling [undo]
 * rewinds the top mutation of [undoStack] and moves it onto [redoStack]; calling [redo] replays the
 * top mutation of [redoStack] and moves it back onto [undoStack].
 */
data class SurveyUndoRedoStack(
  val undoStack: List<SurveyEditorMutation> = emptyList(),
  val redoStack: List<SurveyEditorMutation> = emptyList(),
) {
  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  /** Pushes [mutation] onto [undoStack] and clears [redoStack]. */
  fun record(mutation: SurveyEditorMutation): SurveyUndoRedoStack =
    copy(undoStack = undoStack + mutation, redoStack = emptyList())

  /**
   * Rewinds the most recent [SurveyEditorMutation] on [draft], moving it from [undoStack] to
   * [redoStack], or returns `null` when [canUndo] is `false`.
   */
  fun undo(draft: SurveyEditorDraft): SurveyUndoRedoTransition? {
    val mutation = undoStack.lastOrNull() ?: return null
    return SurveyUndoRedoTransition(
      stack = copy(undoStack = undoStack.dropLast(1), redoStack = redoStack + mutation),
      draft = mutation.rewind(draft),
      mutation = mutation,
    )
  }

  /**
   * Replays the most recently rewound [SurveyEditorMutation] on [draft], moving it from [redoStack]
   * back to [undoStack], or returns `null` when [canRedo] is `false`.
   */
  fun redo(draft: SurveyEditorDraft): SurveyUndoRedoTransition? {
    val mutation = redoStack.lastOrNull() ?: return null
    return SurveyUndoRedoTransition(
      stack = copy(undoStack = undoStack + mutation, redoStack = redoStack.dropLast(1)),
      draft = mutation.replay(draft),
      mutation = mutation,
    )
  }

  /** Returns an empty [SurveyUndoRedoStack]. */
  fun clear(): SurveyUndoRedoStack = SurveyUndoRedoStack()
}

private fun <T> reorderByKeys(
  items: List<T>,
  orderedKeys: List<String>,
  keyOf: (T) -> String,
): List<T> {
  val byKey = items.associateBy(keyOf)
  val ordered = orderedKeys.mapNotNull { byKey[it] }
  val orderedSet = orderedKeys.toSet()
  val remaining = items.filterNot { keyOf(it) in orderedSet }
  return ordered + remaining
}
