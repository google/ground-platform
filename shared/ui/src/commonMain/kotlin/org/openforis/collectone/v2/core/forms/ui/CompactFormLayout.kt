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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.ValidationStatus

/**
 * A host's request that [CompactFormRunner] bring the question at [path] into view (see its
 * `focusRequest` parameter). [token] must change between requests, so asking for the same question
 * twice in a row works; hosts typically count up.
 */
data class CompactFocusRequest(val path: String, val token: Long) {
  /** True when this request is for the card at [cardPath]. */
  fun targets(cardPath: String): Boolean = path == cardPath
}

/**
 * Completion state of one question in the compact (web) form layout, shown as the icon at the left
 * of its collapsible card.
 */
enum class CompactQuestionStatus {
  /** The question has a value (or is calculated / read-only and so needs no input). */
  ANSWERED,

  /** The collector explicitly skipped this optional question without answering it. */
  SKIPPED,

  /** The question has no value yet. */
  PENDING,

  /** The question has a value that fails validation, or is required and was left empty. */
  INVALID,
}

/**
 * Layout state of a [CompactFormRunner]: which question cards are expanded and which optional
 * questions were explicitly skipped. Neither is part of the form data, so it lives beside the
 * [FormWizardController] rather than in it. Create one per form session with
 * [rememberCompactFormLayoutState].
 *
 * Every card starts expanded (see [expandAllByDefault]). With [autoCollapseOnAnswer], the runner
 * reports each card's [CompactQuestionStatus] through [onStatusChanged] and a card collapses by
 * itself the moment it goes from not answered to [CompactQuestionStatus.ANSWERED]; that happens
 * once per such transition, so a collector who re-opens the card afterwards keeps it open.
 */
class CompactFormLayoutState(
  initiallyExpandAll: Boolean = true,
  /** Whether a card collapses on its own once its question becomes answered. */
  val autoCollapseOnAnswer: Boolean = false,
) {
  private val expanded = mutableStateMapOf<String, Boolean>()
  private val skipped = mutableStateMapOf<String, Boolean>()

  /** Last status reported for each card via [onStatusChanged]; not observed by the UI. */
  private val lastStatus = mutableMapOf<String, CompactQuestionStatus>()

  /**
   * Whether cards that haven't been toggled yet (by the collector, a skip, or an auto-collapse) are
   * expanded. True for both the read-only designer preview and interactive sessions, which then
   * rely on [autoCollapseOnAnswer] to tidy answered cards away.
   */
  var expandAllByDefault: Boolean by mutableStateOf(initiallyExpandAll)

  fun isExpanded(path: String): Boolean = expanded[path] ?: expandAllByDefault

  fun setExpanded(path: String, value: Boolean) {
    expanded[path] = value
  }

  fun toggle(path: String) = setExpanded(path, !isExpanded(path))

  /**
   * Records the latest [status] of the card at [path]. Returns true when this call auto-collapsed
   * the card, i.e. [autoCollapseOnAnswer] is on and the status just changed from a previously seen
   * non-answered status to [CompactQuestionStatus.ANSWERED]. The first status seen for a path never
   * collapses it, so questions that are already answered when the form opens (defaults, calculated
   * values) stay expanded like the rest; nor does a status that stays answered, so manual expands
   * after the collapse are respected. Conversely, when an answered card's value is cleared or
   * invalidated (for example, when changing a cascading-select parent prunes a child's stale
   * selection), the card re-expands so the collector sees the updated options right away.
   */
  fun onStatusChanged(path: String, status: CompactQuestionStatus): Boolean {
    val previous = lastStatus.put(path, status)
    val shouldCollapse =
      autoCollapseOnAnswer &&
        previous != null &&
        previous != CompactQuestionStatus.ANSWERED &&
        status == CompactQuestionStatus.ANSWERED
    if (shouldCollapse) {
      expanded[path] = false
    } else if (
      autoCollapseOnAnswer &&
        previous == CompactQuestionStatus.ANSWERED &&
        (status == CompactQuestionStatus.PENDING || status == CompactQuestionStatus.INVALID)
    ) {
      expanded[path] = true
    }
    return shouldCollapse
  }

  fun isSkipped(path: String): Boolean = skipped[path] == true

  /** Marks [path] as skipped and collapses its card. */
  fun skip(path: String) {
    skipped[path] = true
    expanded[path] = false
  }

  /** Clears the skipped mark on [path], e.g. once it receives a value. */
  fun unskip(path: String) {
    skipped.remove(path)
  }

  fun expandAll(paths: List<String>) = paths.forEach { expanded[it] = true }

  fun collapseAll(paths: List<String>) = paths.forEach { expanded[it] = false }

  /**
   * Forgets the card state of every entry of the repeat at [repeatPath], e.g. after one of its
   * entries was deleted: later entries shift down into the deleted entry's paths, so state keyed by
   * those paths would otherwise carry over to a different entry.
   */
  fun forgetRepeatEntries(repeatPath: String) {
    val prefix = "$repeatPath["
    expanded.keys.filter { it.startsWith(prefix) }.forEach { expanded.remove(it) }
    skipped.keys.filter { it.startsWith(prefix) }.forEach { skipped.remove(it) }
    lastStatus.keys.removeAll { it.startsWith(prefix) }
  }
}

/** Remembers a [CompactFormLayoutState] for the lifetime of [controller]. */
@Composable
fun rememberCompactFormLayoutState(
  controller: FormWizardController,
  initiallyExpandAll: Boolean = true,
  autoCollapseOnAnswer: Boolean = false,
): CompactFormLayoutState =
  remember(controller) {
    CompactFormLayoutState(
      initiallyExpandAll = initiallyExpandAll,
      autoCollapseOnAnswer = autoCollapseOnAnswer,
    )
  }

/**
 * Resolves the [CompactQuestionStatus] of [control]. A value always wins over a stale skip mark;
 * read-only and calculated questions count as answered since the collector can't act on them.
 */
fun compactQuestionStatus(
  control: ComponentState.ControlState,
  isSkipped: Boolean,
  variant: FormVariant = FormVariant.WEB,
): CompactQuestionStatus {
  if (incompatibleQuestionTypeForVariant(control, variant) != null) {
    return CompactQuestionStatus.SKIPPED
  }
  val fs = control.fieldState
  return when {
    fs.validationStatus is ValidationStatus.Invalid && !fs.isEmpty -> CompactQuestionStatus.INVALID
    !fs.isEmpty -> CompactQuestionStatus.ANSWERED
    fs.isReadOnly || fs.isCalculated -> CompactQuestionStatus.ANSWERED
    isSkipped && !fs.isRequired -> CompactQuestionStatus.SKIPPED
    else -> CompactQuestionStatus.PENDING
  }
}

/** One entry in the vertical stack rendered by [CompactFormRunner]. */
sealed interface CompactFormItem {
  /** A single question card. */
  data class Question(val step: FormWizardStep.QuestionStep) : CompactFormItem

  /** A section heading for a `field-list` group, followed by its questions. */
  data class GroupHeading(val step: FormWizardStep.FieldListGroupStep) : CompactFormItem

  /** A repeat group's add/remove controls. */
  data class RepeatHub(val step: FormWizardStep.RepeatHubStep) : CompactFormItem
}

/**
 * Flattens [steps] into the compact layout's vertical stack: every relevant question becomes a card
 * (questions of a `field-list` group are preceded by a heading) and repeat hubs stay as-is. The
 * wizard's final "Review & Submit" step ([FormWizardStep.SummaryStep]) is never an entry: the
 * compact runner's footer owns submission and validation feedback, so the step is left to the
 * controller only. Progress tallies ([compactFormProgress]) therefore exclude it too.
 */
fun buildCompactFormItems(steps: List<FormWizardStep>): List<CompactFormItem> =
  steps.flatMap { step ->
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(CompactFormItem.Question(step))
      is FormWizardStep.FieldListGroupStep ->
        listOf(CompactFormItem.GroupHeading(step)) +
          step.controls.map { control ->
            CompactFormItem.Question(
              FormWizardStep.QuestionStep(
                stepKey = control.canonicalPath,
                breadcrumbs = step.breadcrumbs + listOfNotNull(step.groupLabel),
                repeatContext = step.repeatContext,
                control = control,
              )
            )
          }
      is FormWizardStep.RepeatHubStep -> listOf(CompactFormItem.RepeatHub(step))
      is FormWizardStep.SummaryStep -> emptyList()
    }
  }

/** The repeat context of [this] item's step (the innermost repeat entry it belongs to), if any. */
private val CompactFormItem.repeatContext: RepeatStepContext?
  get() =
    when (this) {
      is CompactFormItem.Question -> step.repeatContext
      is CompactFormItem.GroupHeading -> step.repeatContext
      is CompactFormItem.RepeatHub -> step.repeatContext
    }

/**
 * One block of the compact layout's vertical stack: a lone [CompactFormItem], or a repeat group
 * framing its entries. See [buildCompactFormBlocks].
 */
sealed interface CompactFormBlock {
  /** A question card or section heading outside any repeat entry. */
  data class Single(val item: CompactFormItem) : CompactFormBlock

  /**
   * A repeat group: its [entries] in order and, when collectors may add or delete entries (or there
   * are none yet), its [hub] with the group's add / remove rules and entry summaries.
   */
  data class RepeatSection(
    val repeatPath: String,
    val label: String,
    val entries: List<CompactRepeatEntry>,
    val hub: FormWizardStep.RepeatHubStep?,
    val targetCount: Int? = hub?.repeatGroup?.targetCount,
  ) : CompactFormBlock
}

/**
 * One entry of a [CompactFormBlock.RepeatSection]: its title (the entry's own label, else
 * "<section> #<n>"), its hub [summary] (answer count and previews) when the section has a hub, its
 * completion [status] (computed even for fixed-count repeats without a hub), and the [blocks] it
 * holds (its question cards, and the sections of repeats nested inside it).
 */
data class CompactRepeatEntry(
  val instancePath: String,
  val repeatIndex: Int,
  val title: String,
  val summary: RepeatInstanceSummary?,
  val blocks: List<CompactFormBlock>,
  val status: RepeatEntryStatus = summary?.status ?: RepeatEntryStatus.NOT_STARTED,
)

/**
 * Nests the flat [items] of [buildCompactFormItems] into repeat sections, so the web runner can
 * frame each repeat entry's questions under that entry's header instead of repeating "Entry #n of
 * m" on every card. Items outside repeats stay [CompactFormBlock.Single]s in their original order.
 *
 * Each entry gathers the items whose repeat context points at it; repeats nested in an entry become
 * sections inside that entry (instance paths are prefixes of their descendants' paths, e.g.
 * `/data/plot[1]` holds `/data/plot[1]/tree[2]`). A section with a hub lists exactly the hub's
 * relevant entries, so an entry whose questions are all hidden still shows (and can be deleted).
 */
fun buildCompactFormBlocks(items: List<CompactFormItem>): List<CompactFormBlock> {
  val root = BlockListBuilder()
  for (item in items) {
    if (item is CompactFormItem.RepeatHub) {
      val repeatPath = item.step.repeatGroup.canonicalPath
      root.containerFor(repeatInstancePrefixes(repeatPath)).section(repeatPath).hub = item.step
    } else {
      val context = item.repeatContext
      val container =
        if (context == null) root
        else root.containerFor(repeatInstancePrefixes(context.instancePath), context)
      container.blocks += Built.Item(item)
    }
  }
  return root.build()
}

/**
 * Assigns 1-based display numbers to each rendered question card in [blocks], matching the
 * top-to-bottom order shown by [CompactFormRunner] (including questions inside repeat entries).
 * Every visible question receives a stable index regardless of its [CompactQuestionStatus], so
 * answering an earlier question does not renumber later ones.
 */
fun compactQuestionNumbers(blocks: List<CompactFormBlock>): Map<String, Int> = buildMap {
  var next = 1
  fun visit(blockList: List<CompactFormBlock>) {
    for (block in blockList) {
      when (block) {
        is CompactFormBlock.Single -> {
          val item = block.item
          if (item is CompactFormItem.Question) {
            put(item.step.stepKey, next++)
          }
        }
        is CompactFormBlock.RepeatSection -> {
          for (entry in block.entries) {
            visit(entry.blocks)
          }
        }
      }
    }
  }
  visit(blocks)
}

/**
 * The repeat instance paths along [path], outermost first: every prefix that ends with an `[n]`
 * index, e.g. `/data/plot[1]/tree[2]/dbh` → `/data/plot[1]`, `/data/plot[1]/tree[2]`.
 */
internal fun repeatInstancePrefixes(path: String): List<String> =
  path.indices
    .filter { i -> path[i] == ']' && (i == path.lastIndex || path[i + 1] == '/') }
    .map { path.substring(0, it + 1) }

private sealed interface Built {
  class Item(val item: CompactFormItem) : Built

  class Section(val repeatPath: String) : Built {
    var hub: FormWizardStep.RepeatHubStep? = null
    var label: String? = null
    val entries = mutableListOf<EntryBuilder>()

    fun entry(instancePath: String): EntryBuilder =
      entries.lastOrNull()?.takeIf { it.instancePath == instancePath }
        ?: entries.firstOrNull { it.instancePath == instancePath }
        ?: EntryBuilder(instancePath).also { entries += it }
  }
}

private open class BlockListBuilder {
  val blocks = mutableListOf<Built>()

  /** The section for [repeatPath] that the last block opened, or a new one appended after it. */
  fun section(repeatPath: String): Built.Section =
    (blocks.lastOrNull() as? Built.Section)?.takeIf { it.repeatPath == repeatPath }
      ?: Built.Section(repeatPath).also { blocks += it }

  /**
   * The entry block list at the end of [instancePaths], creating sections and entries as needed.
   */
  fun containerFor(
    instancePaths: List<String>,
    context: RepeatStepContext? = null,
  ): BlockListBuilder {
    var container = this
    for (instancePath in instancePaths) {
      val section = container.section(instancePath.substringBeforeLast('['))
      val entry = section.entry(instancePath)
      if (context != null && context.instancePath == instancePath) {
        entry.context = context
        section.label = section.label ?: context.repeatGroupLabel
      }
      container = entry
    }
    return container
  }

  fun build(): List<CompactFormBlock> = blocks.map { block ->
    when (block) {
      is Built.Item -> CompactFormBlock.Single(block.item)
      is Built.Section -> block.build()
    }
  }

  private fun Built.Section.build(): CompactFormBlock.RepeatSection {
    val hub = hub
    val label =
      hub?.repeatGroup?.label?.text?.takeIf { it.isNotBlank() }
        ?: this.label
        ?: repeatSectionLabel(null, repeatPath)
    val summaries = hub?.summaries
    val ordered =
      if (summaries == null) {
        entries.map { it to null }
      } else {
        summaries.map { summary -> entry(summary.instancePath) to summary }
      }
    val targetCount =
      hub?.repeatGroup?.targetCount ?: entries.firstNotNullOfOrNull { it.context?.targetCount }
    return CompactFormBlock.RepeatSection(
      repeatPath = repeatPath,
      label = label,
      entries =
        ordered.map { (entry, summary) ->
          val index = entry.instancePath.substringAfterLast('[').substringBefore(']').toIntOrNull()
          val builtBlocks = entry.build()
          CompactRepeatEntry(
            instancePath = entry.instancePath,
            repeatIndex = summary?.repeatIndex ?: entry.context?.repeatIndex ?: index ?: 0,
            title = summary?.title ?: entry.context?.instanceLabel ?: "$label #${index ?: ""}",
            summary = summary,
            blocks = builtBlocks,
            status = summary?.status ?: RepeatEntryStatus.of(entry.collectControls()),
          )
        },
      hub = hub,
      targetCount = targetCount,
    )
  }

  private fun BlockListBuilder.collectControls(): List<ComponentState.ControlState> =
    blocks.flatMap { block ->
      when (block) {
        is Built.Item ->
          when (val item = block.item) {
            is CompactFormItem.Question -> listOf(item.step.control)
            else -> emptyList()
          }
        is Built.Section -> block.entries.flatMap { it.collectControls() }
      }
    }
}

private class EntryBuilder(val instancePath: String) : BlockListBuilder() {
  var context: RepeatStepContext? = null
}

/** Progress of a compact form: how many question cards are settled (answered or skipped). */
data class CompactFormProgress(
  val answered: Int,
  val skipped: Int,
  val pending: Int,
  val invalid: Int,
) {
  val total: Int
    get() = answered + skipped + pending + invalid

  val settled: Int
    get() = answered + skipped

  val fraction: Float
    get() = if (total == 0) 0f else settled.toFloat() / total.toFloat()
}

/** Tallies the statuses of every question card in [items]. */
fun compactFormProgress(
  items: List<CompactFormItem>,
  isSkipped: (String) -> Boolean,
  variant: FormVariant = FormVariant.WEB,
): CompactFormProgress {
  var answered = 0
  var skipped = 0
  var pending = 0
  var invalid = 0
  items.filterIsInstance<CompactFormItem.Question>().forEach { item ->
    val control = item.step.control
    when (compactQuestionStatus(control, isSkipped(control.canonicalPath), variant)) {
      CompactQuestionStatus.ANSWERED -> answered++
      CompactQuestionStatus.SKIPPED -> skipped++
      CompactQuestionStatus.PENDING -> pending++
      CompactQuestionStatus.INVALID -> invalid++
    }
  }
  return CompactFormProgress(answered, skipped, pending, invalid)
}
