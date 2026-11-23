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
package org.openforis.collectone.v2.core.forms.ui

import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption

/**
 * Keeps cascading selects consistent: a question whose choices are filtered by a parent question's
 * answer must not keep a choice the parent no longer allows. Two kinds of cascade are handled:
 * - Form editor cascades: the child names its parent (`ControlDef.parent_question_key`) and each
 *   choice its parent value (the `parent_value` column).
 * - XLSForm `choice_filter` cascades with no parent key: the form engine filters the child's
 *   `itemset` rows with a predicate that refers to the parent, so the child's resolved options are
 *   exactly the allowed ones.
 */
internal object CascadingSelects {
  /** The appearance that lets collectors add choices that are not among the resolved options. */
  private const val ADD_ENTITY_APPEARANCE = "add-entity"

  /**
   * The choice values selected in [field]: the items of a multi-select list, or the space-separated
   * tokens of a string answer (XForms `select` stores its choices as one space-separated string).
   * Empty when the field is unanswered.
   */
  fun selectedValues(field: FieldState?): List<String> {
    val stringVal = field?.value?.scalar_value?.string_value?.trim()
    val listVal = field?.value?.list_value?.values?.mapNotNull { it.string_value?.trim() }
    return when {
      !listVal.isNullOrEmpty() -> listVal.filter { it.isNotBlank() }
      !stringVal.isNullOrBlank() ->
        stringVal.split(' ').map { it.trim() }.filter { it.isNotBlank() }
      else -> emptyList()
    }
  }

  /**
   * The field of [child]'s cascading-select parent, or `null` when [child] has none or it can't be
   * found. The parent key is usually a bare data name, so a field next to [child] (in the same
   * group or repeat instance) wins: in a repeat, each instance must filter on its own parent answer
   * rather than on the first instance's.
   */
  fun parentFieldFor(state: FormState, child: ComponentState.ControlState): FieldState? {
    val parentRef = child.controlDef.parent_question_key.trim()
    if (parentRef.isEmpty()) return null
    val container = child.canonicalPath.substringBeforeLast('/', "")
    val sibling = state.fieldStates["$container/${parentRef.substringAfterLast('/')}"]
    return sibling ?: state.findFieldState(parentRef)
  }

  /**
   * The [options] a cascading select lists while its parent has [parentValues] selected: choices
   * with no parent value, or one among [parentValues]. All [options] when [parentValues] is `null`
   * (the question isn't a cascading select).
   */
  fun allowedOptions(
    options: List<ResolvedChoiceOption>,
    parentValues: Collection<String>?,
  ): List<ResolvedChoiceOption> {
    if (parentValues == null) return options
    val allowed = parentValues.toSet()
    return options.filter { it.parentValue.isBlank() || it.parentValue in allowed }
  }

  /**
   * Clears or trims the answers of every child of the question at [changedPath] that its new answer
   * no longer allows, and cascades to their own children.
   *
   * A form editor child (one with a parent key) whose parent field is [changedPath] keeps only
   * choices with no parent value or a parent value among the parent's selections; choices that
   * aren't in its option list are left alone, and every answer is cleared when the parent is
   * unanswered. An XLSForm child (an `itemset` whose predicate refers to [changedPath]'s data name)
   * keeps only values among its newly resolved options. Each edit goes through [session] (so
   * relevance, constraints, and itemsets re-evaluate) and is read back from its state before the
   * next child is checked; [visited] stops cycles between misconfigured parent keys.
   */
  fun pruneChildAnswers(
    session: FormSession,
    changedPath: String,
    visited: MutableSet<String> = mutableSetOf(),
  ) {
    if (changedPath in visited) return
    visited.add(changedPath)

    val state = session.state
    val changedField = state.findFieldState(changedPath)
    val canonicalChanged = changedField?.canonicalPath ?: changedPath
    val parentValues = selectedValues(changedField).toSet()

    for (child in allControls(state.rootComponents)) {
      if (child.canonicalPath == canonicalChanged) continue
      val kept =
        when {
          isEditorChild(state, child, changedPath, canonicalChanged) ->
            keptEditorChildValues(session.state, child, parentValues)
          isItemsetChild(child, canonicalChanged) -> keptItemsetChildValues(session.state, child)
          else -> null
        } ?: continue
      if (kept.isEmpty()) {
        session.clearField(child.canonicalPath)
      } else {
        session.updateMultiSelect(child.canonicalPath, kept)
      }
      pruneChildAnswers(session, child.canonicalPath, visited)
    }
  }

  /**
   * Whether [child] names the question at [changedPath] as its parent: its parent field resolves to
   * [canonicalChanged], or (when it resolves to no field) its parent key matches [changedPath] by
   * full path or data name.
   */
  private fun isEditorChild(
    state: FormState,
    child: ComponentState.ControlState,
    changedPath: String,
    canonicalChanged: String,
  ): Boolean {
    val parentRef = child.controlDef.parent_question_key.trim()
    if (parentRef.isEmpty()) return false
    val parentField = parentFieldFor(state, child)
    if (parentField != null) return parentField.canonicalPath == canonicalChanged
    val leafName = changedPath.substringAfterLast('/')
    return parentRef == changedPath ||
      parentRef == leafName ||
      changedPath.endsWith("/$parentRef") ||
      parentRef.endsWith("/$leafName")
  }

  /**
   * Whether [child] is a relevant XLSForm cascading select with no parent key whose `itemset`
   * predicate refers to [canonicalChanged]'s data name. Children that let collectors add their own
   * choices are skipped, since those answers are legitimately missing from the resolved options.
   */
  private fun isItemsetChild(
    child: ComponentState.ControlState,
    canonicalChanged: String,
  ): Boolean {
    val control = child.controlDef
    val filter = control.itemset?.nodeset_filter.orEmpty()
    if (control.parent_question_key.isNotBlank() || filter.isBlank() || !child.isRelevant) {
      return false
    }
    if (ADD_ENTITY_APPEARANCE in control.appearance.split(' ')) return false
    val leafName = Regex.escape(canonicalChanged.substringAfterLast('/').substringBefore('['))
    return Regex("""(^|[^A-Za-z0-9_.\-])$leafName($|[^A-Za-z0-9_.\-])""").containsMatchIn(filter)
  }

  /**
   * The answer [child] keeps when its parent now has [parentValues] selected, or `null` when it
   * keeps its whole answer.
   *
   * When [child] uses a filtered `itemset` (as Form Designer cascading selects do), the engine has
   * already re-evaluated [ComponentState.ControlState.options] for the new parent answer, so any
   * value absent from the refreshed options was filtered out and is pruned (unless `add-entity`
   * allows custom collector-added options). For static inline `<item>` controls without an
   * `itemset`, values absent from the static choice list (e.g. legacy codes from an earlier form
   * version) are left alone.
   */
  private fun keptEditorChildValues(
    state: FormState,
    child: ComponentState.ControlState,
    parentValues: Set<String>,
  ): List<String>? {
    val fresh =
      allControls(state.rootComponents).firstOrNull { it.canonicalPath == child.canonicalPath }
        ?: child
    val current = selectedValues(state.fieldStates[fresh.canonicalPath] ?: return null)
    if (current.isEmpty()) return null
    if (parentValues.isEmpty()) return emptyList()
    val isFilteredItemset =
      fresh.controlDef.itemset?.nodeset_filter?.isNotBlank() == true &&
        ADD_ENTITY_APPEARANCE !in fresh.controlDef.appearance.split(' ')
    val kept = current.filter { value ->
      val option = fresh.options.firstOrNull { it.value == value }
      if (option == null) {
        !isFilteredItemset
      } else {
        option.parentValue.isBlank() || option.parentValue in parentValues
      }
    }
    return kept.takeIf { it.size != current.size }
  }

  /**
   * The answer an XLSForm cascading select keeps: the values among its resolved options, as read
   * from the latest [state] (the parent's edit re-filtered them), or `null` when it keeps all.
   */
  private fun keptItemsetChildValues(
    state: FormState,
    child: ComponentState.ControlState,
  ): List<String>? {
    val fresh =
      allControls(state.rootComponents).firstOrNull { it.canonicalPath == child.canonicalPath }
        ?: return null
    val current = selectedValues(fresh.fieldState)
    if (current.isEmpty()) return null
    val optionValues = fresh.options.map { it.value }.toSet()
    val kept = current.filter { it in optionValues }
    return kept.takeIf { it.size != current.size }
  }

  /** Every question in [components], including those in each repeat instance, in form order. */
  private fun allControls(components: List<ComponentState>): List<ComponentState.ControlState> {
    val result = mutableListOf<ComponentState.ControlState>()
    fun walk(list: List<ComponentState>) {
      for (item in list) {
        when (item) {
          is ComponentState.ControlState -> result.add(item)
          is ComponentState.GroupState -> walk(item.children)
          is ComponentState.RepeatGroupState -> item.instances.forEach { walk(it.children) }
        }
      }
    }
    walk(components)
    return result
  }
}
