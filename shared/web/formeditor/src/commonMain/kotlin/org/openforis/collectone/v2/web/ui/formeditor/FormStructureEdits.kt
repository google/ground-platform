/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.formeditor

import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.editor.ValidationRules

// The Form editor's structural edits (inserting, copying, moving, retyping, and removing questions
// and sections) as functions of the Form, so FormEditorViewModel only decides what to select and
// records the result for undo. They're pure apart from the key counter they're given.

private val NUMBERED_SUFFIX = Regex("_\\d+$")

/** [name] without one trailing `_<n>`, so copies of `plot_2` are named `plot_3`, not `plot_2_2`. */
internal fun nameStem(name: String): String = name.replace(NUMBERED_SUFFIX, "")

/**
 * [stem] itself if no name in [taken] uses it, else the first free `<stem>_<n>` from `n = 2`.
 * Question names become XForms node names, so they must be unique within the Form.
 */
internal fun uniqueName(stem: String, taken: Set<String>): String {
  if (stem !in taken) return stem
  var n = 2
  while ("${stem}_$n" in taken) n++
  return "${stem}_$n"
}

/** The first free `section_<n>` ID, counting from one past the number of [EditorForm.sections]. */
internal fun EditorForm.newSectionId(): String {
  val existingIds = sections.map { it.id }.toSet()
  var counter = sections.size + 1
  while ("section_$counter" in existingIds) counter++
  return "section_$counter"
}

/**
 * The section a question inserted at [atIndex] joins: that of the question it displaces, else that
 * of the question before it, else none (`""`). `null` [atIndex] (append) joins no section.
 */
internal fun EditorForm.sectionIdForInsertAt(atIndex: Int?): String =
  when {
    atIndex == null -> ""
    atIndex in questions.indices -> questions[atIndex].sectionId
    atIndex > 0 && atIndex - 1 in questions.indices -> questions[atIndex - 1].sectionId
    else -> ""
  }

/**
 * Keys of the branch rooted at [rootKey]: the root and every later question whose display logic or
 * cascading-choice parent is in the branch, transitively. Only later questions are considered
 * because display logic can only refer back (see [EditorForm.eligibleRelevanceSources]).
 */
internal fun EditorForm.branchKeys(rootKey: String): Set<String> {
  val rootIndex = indexOf(rootKey)
  if (rootIndex < 0) return emptySet()
  val keys = linkedSetOf(rootKey)
  for (i in (rootIndex + 1) until questions.size) {
    val q = questions[i]
    if (q.relevance?.sourceQuestionKey in keys || q.parentQuestionKey in keys) keys += q.key
  }
  return keys
}

/**
 * Copies of [originals] with fresh keys and names, with display logic and cascading-choice parents
 * that point inside the copied set rewired to the copies (references outside it are kept). All keys
 * are allocated before any name, and names are made unique against this Form and each other.
 * [sectionId], when given, moves every copy into that section.
 */
internal fun EditorForm.copiesOf(
  originals: List<EditorQuestion>,
  keys: QuestionKeyAllocator,
  sectionId: String? = null,
): List<EditorQuestion> {
  val takenKeys = allQuestions.map { it.key }.toMutableSet()
  val takenNames = allQuestions.map { it.name }.toMutableSet()
  val keyMap = mutableMapOf<String, String>()
  originals.forEach { q ->
    keyMap[q.key] = keys.allocate { it in takenKeys }.also { takenKeys += it }
  }
  return originals.map { q ->
    val name = uniqueName(nameStem(q.name), takenNames).also { takenNames += it }
    q.copy(
      key = keyMap.getValue(q.key),
      name = name,
      sectionId = sectionId ?: q.sectionId,
      relevance = q.relevance?.let { rewireSource(it, keyMap) },
      requiredCondition = q.requiredCondition?.let { rewireSource(it, keyMap) },
      validation = q.validation?.let { rewireValidation(it, keyMap) },
      parentQuestionKey = keyMap[q.parentQuestionKey] ?: q.parentQuestionKey,
    )
  }
}

private fun rewireSource(rel: EditorRelevance, keyMap: Map<String, String>): EditorRelevance =
  rel.copy(sourceQuestionKey = keyMap[rel.sourceQuestionKey] ?: rel.sourceQuestionKey)

private fun rewireValidation(v: EditorValidation, keyMap: Map<String, String>): EditorValidation =
  v.copy(
    comparison =
      v.comparison?.let { cmp ->
        cmp.copy(targetQuestionKey = keyMap[cmp.targetQuestionKey] ?: cmp.targetQuestionKey)
      },
    distanceReferenceQuestionKey =
      keyMap[v.distanceReferenceQuestionKey] ?: v.distanceReferenceQuestionKey,
    condition = v.condition?.let { rewireSource(it, keyMap) },
  )

/**
 * Question index where [section] begins in this Form's flow: the index of its first question when
 * populated, or its clamped [EditorSection.insertAtIndex] (defaulting to [EditorForm.questions]
 * size) while empty.
 */
internal fun EditorForm.sectionAnchorIndex(section: EditorSection): Int {
  val firstIdx = questions.indexOfFirst { it.sectionId == section.id }
  return if (firstIdx >= 0) firstIdx
  else (section.insertAtIndex ?: questions.size).coerceIn(0, questions.size)
}

/**
 * Inserts [question] at [insertAt], clearing [EditorSection.insertAtIndex] on the section it joins
 * and shifting the anchors of empty sections that sit after the insertion point. When
 * [afterSectionId] is given, empty sections up to and including [afterSectionId] at [insertAt] stay
 * before [question].
 */
internal fun EditorForm.insertingQuestion(
  question: EditorQuestion,
  insertAt: Int,
  afterSectionId: String? = null,
): EditorForm {
  val clampedAt = insertAt.coerceIn(0, questions.size)
  val pivotSectionPos =
    when {
      afterSectionId != null -> sections.indexOfFirst { it.id == afterSectionId }
      question.sectionId.isNotEmpty() && questions.none { it.sectionId == question.sectionId } ->
        sections.indexOfFirst { it.id == question.sectionId }
      else -> -1
    }
  val updatedSections = sections.mapIndexed { pos, sec ->
    val anchor = sec.insertAtIndex
    when {
      sec.id == question.sectionId && anchor != null -> sec.copy(insertAtIndex = null)
      anchor == null -> sec
      anchor > clampedAt -> sec.copy(insertAtIndex = anchor + 1)
      anchor == clampedAt && pos > pivotSectionPos -> sec.copy(insertAtIndex = anchor + 1)
      else -> sec
    }
  }
  val updatedQuestions = questions.toMutableList().apply { add(clampedAt, question) }
  return copy(questions = updatedQuestions, sections = updatedSections)
}

/**
 * Inserts an empty [newSection] at [atIndex] (snapped out of any existing contiguous section run),
 * or right after [selectedKey] (or its section) when [atIndex] is `null`, keeping [sections] in
 * flow order.
 */
internal fun EditorForm.insertingSection(
  newSection: EditorSection,
  atIndex: Int?,
  selectedKey: String? = null,
  afterSectionId: String? = null,
): EditorForm {
  val targetIndex =
    if (atIndex != null) {
      questions.snapOutOfSection(atIndex.coerceIn(0, questions.size))
    } else {
      val selectedIdx = selectedKey?.let(::indexOf) ?: -1
      if (selectedIdx >= 0) {
        val secId = questions[selectedIdx].sectionId
        if (secId.isNotEmpty()) questions.indexOfLast { it.sectionId == secId } + 1
        else selectedIdx + 1
      } else {
        questions.size
      }
    }
  val anchored = newSection.copy(insertAtIndex = targetIndex)
  val afterPos = afterSectionId?.let { id -> sections.indexOfFirst { it.id == id } } ?: -1
  val sectionPos =
    when {
      afterPos >= 0 -> afterPos + 1
      atIndex != null ->
        sections.indexOfFirst { sectionAnchorIndex(it) >= targetIndex }.takeIf { it >= 0 }
          ?: sections.size
      else ->
        sections.indexOfFirst { sectionAnchorIndex(it) > targetIndex }.takeIf { it >= 0 }
          ?: sections.size
    }
  return copy(sections = sections.toMutableList().apply { add(sectionPos, anchored) })
}

/**
 * Duplicates [sectionId] and its questions right after [sectionId], returning the updated Form and
 * new section ID (or `null` when [sectionId] is unknown).
 */
internal fun EditorForm.duplicatingSection(
  sectionId: String,
  keys: QuestionKeyAllocator,
): Pair<EditorForm, String>? {
  val section = section(sectionId) ?: return null
  val newSecId = newSectionId()
  val copies = copiesOf(questions.filter { it.sectionId == sectionId }, keys, sectionId = newSecId)
  val lastIdx = questions.indexOfLast { it.sectionId == sectionId }
  val insertAt = if (lastIdx >= 0) lastIdx + 1 else sectionAnchorIndex(section)
  val newSection =
    section.copy(
      id = newSecId,
      title = if (section.title.isBlank()) "" else "${section.title} (copy)",
      insertAtIndex = if (copies.isEmpty()) insertAt else null,
    )
  val sourcePos = sections.indexOfFirst { it.id == sectionId }
  val insertPos = if (sourcePos >= 0) sourcePos + 1 else sections.size
  val updatedSections =
    sections
      .toMutableList()
      .apply { add(insertPos, newSection) }
      .mapIndexed { pos, sec ->
        val anchor = sec.insertAtIndex
        when {
          copies.isEmpty() || anchor == null -> sec
          anchor > insertAt -> sec.copy(insertAtIndex = anchor + copies.size)
          anchor == insertAt && pos > insertPos -> sec.copy(insertAtIndex = anchor + copies.size)
          else -> sec
        }
      }
  val updatedQuestions = questions.toMutableList().apply { addAll(insertAt, copies) }
  return copy(sections = updatedSections, questions = updatedQuestions) to newSecId
}

/**
 * Moves the question at [index] by [delta] positions; it joins the section of the neighbor it lands
 * next to (the one it passed). [index] + [delta] must be a valid index.
 */
internal fun EditorForm.movingQuestion(index: Int, delta: Int): EditorForm {
  val target = index + delta
  val list = questions.toMutableList()
  val item = list.removeAt(index)
  val targetNeighborSectionId =
    when {
      target > 0 && delta > 0 -> list[target - 1].sectionId
      target < list.size && delta < 0 -> list[target].sectionId
      target > 0 -> list[target - 1].sectionId
      target < list.size -> list[target].sectionId
      else -> ""
    }
  list.add(target, item.copy(sectionId = targetNeighborSectionId))
  val updatedSections = sections.map { sec ->
    if (sec.id == targetNeighborSectionId && sec.insertAtIndex != null) {
      sec.copy(insertAtIndex = null)
    } else {
      sec
    }
  }
  return copy(questions = list, sections = updatedSections)
}

/**
 * Drops the question at [fromIndex] at [targetIndex] (an index in the list without it), as the flow
 * canvas's drag and drop does.
 *
 * The question joins [targetSectionId], or when that's `null` the section of the question before
 * the drop point (else after it). Dropped into a section, it's clamped inside that section's run of
 * questions so sections stay contiguous; dropped outside every section (`""`), a drop inside a
 * section's run snaps to the run's nearer edge.
 */
internal fun EditorForm.movingQuestionTo(
  fromIndex: Int,
  targetIndex: Int,
  targetSectionId: String?,
): EditorForm {
  val list = questions.toMutableList()
  val item = list.removeAt(fromIndex)
  val newSectionId =
    targetSectionId
      ?: run {
        val target = targetIndex.coerceIn(0, list.size)
        when {
          target > 0 -> list[target - 1].sectionId
          target < list.size -> list[target].sectionId
          else -> item.sectionId
        }
      }
  val finalIndex =
    if (newSectionId.isNotEmpty()) {
      val sectionIndices = list.indices.filter { list[it].sectionId == newSectionId }
      if (sectionIndices.isNotEmpty()) {
        targetIndex.coerceIn(sectionIndices.first(), sectionIndices.last() + 1)
      } else {
        targetIndex.coerceIn(0, list.size)
      }
    } else {
      list.snapOutOfSection(targetIndex.coerceIn(0, list.size))
    }
  list.add(finalIndex.coerceIn(0, list.size), item.copy(sectionId = newSectionId))
  val updatedSections = sections.map { sec ->
    if (sec.id == newSectionId && sec.insertAtIndex != null) {
      sec.copy(insertAtIndex = null)
    } else {
      sec
    }
  }
  return copy(questions = list, sections = updatedSections)
}

/**
 * [index], or the nearer edge of the section run it falls strictly inside (ties go to the start),
 * so an unsectioned question is never dropped between two questions of one section.
 */
private fun List<EditorQuestion>.snapOutOfSection(index: Int): Int {
  if (index !in indices) return index
  val neighborSectionId = this[index].sectionId
  if (neighborSectionId.isEmpty()) return index
  val sectionIndices = indices.filter { this[it].sectionId == neighborSectionId }
  val firstIdx = sectionIndices.first()
  val lastIdx = sectionIndices.last()
  if (index <= firstIdx || index > lastIdx) return index
  return if (index - firstIdx <= lastIdx + 1 - index) firstIdx else lastIdx + 1
}

/**
 * Moves question [key] into section [sectionId] (or out of every section when blank), keeping
 * sections contiguous: it lands at the nearer edge of the section's questions (staying where it is
 * when the section is still empty), or, leaving a section, just before or after it.
 */
internal fun EditorForm.assigningSection(key: String, sectionId: String): EditorForm {
  val index = indexOf(key)
  if (index < 0 || questions[index].sectionId == sectionId) return this
  if (sectionId.isNotEmpty() && section(sectionId) == null) return this
  return movingQuestionTo(index, index, sectionId)
}

/**
 * Moves all of section [sectionId]'s questions by [delta] blocks (negative = earlier), where a
 * block is a question outside every section or all the questions of another section, so sections
 * move past each other as a whole and stay contiguous. Unchanged when the section is empty or
 * already first / last.
 */
internal fun EditorForm.movingSection(sectionId: String, delta: Int): EditorForm {
  val blocks = mutableListOf<MutableList<EditorQuestion>>()
  questions.forEach { q ->
    val last = blocks.lastOrNull()
    if (q.sectionId.isNotEmpty() && last != null && last.first().sectionId == q.sectionId) {
      last += q
    } else {
      blocks += mutableListOf(q)
    }
  }
  val from = blocks.indexOfFirst { it.first().sectionId == sectionId }
  val to = from + delta
  if (from < 0 || delta == 0 || to !in blocks.indices) return this
  val block = blocks.removeAt(from)
  blocks.add(to, block)
  return copy(questions = blocks.flatten())
}

/**
 * Display logic [relevance] adapted to its source [key] being retyped to a type whose operators are
 * [allowed]: an operator the type doesn't offer falls back to its first one, with no value.
 */
private fun EditorRelevance?.afterSourceRetyped(
  key: String,
  allowed: List<RelevanceOperator>,
): EditorRelevance? =
  if (this != null && sourceQuestionKey == key && operator !in allowed) {
    copy(operator = allowed.first(), value = "")
  } else {
    this
  }

/**
 * Retypes question [key] to [type], dropping the settings the new type can't have (choices and
 * choice datasets, decimal places, geometry capture, media source, `required` on read-only types)
 * and adapting its validation. Questions whose display logic, and status rules whose condition,
 * test [key] with an operator the new type doesn't offer fall back to the type's first operator
 * with an empty value, as do sections whose display logic does; a Note leaves the save-to mapping,
 * and a non-geometry type stops feeding the feature geometry.
 */
internal fun EditorForm.retypingQuestion(
  key: String,
  type: EditorQuestionType,
  defaultChoices: () -> List<EditorChoice>,
): EditorForm {
  val allowed = RelevanceOperator.availableFor(type)
  return copy(
    questions =
      questions.map { q ->
        if (q.key == key) {
          q.retypedTo(type, defaultChoices)
        } else {
          q.copy(relevance = q.relevance.afterSourceRetyped(key, allowed))
        }
      },
    sections = sections.map { it.copy(relevance = it.relevance.afterSourceRetyped(key, allowed)) },
    saveTo = saveTo.afterTypeChange(key, type),
  )
}

private fun EditorQuestion.retypedTo(
  type: EditorQuestionType,
  defaultChoices: () -> List<EditorChoice>,
): EditorQuestion =
  copy(
    type = type,
    choices = if (type.hasChoices && choices.isEmpty()) defaultChoices() else choices,
    choiceDatasetId = if (type.hasChoices) choiceDatasetId else null,
    choiceSource = if (type.hasChoices) choiceSource else ChoiceSource.MANUAL,
    allowAddEntity = if (type.hasChoices) allowAddEntity else false,
    required = required && !type.isReadOnly,
    validation = ValidationRules.adaptToType(this.type, type, validation),
    decimalPlaces =
      when {
        !type.isNumeric -> 0
        this.type.isNumeric -> decimalPlaces
        else -> EditorQuestion.DEFAULT_DECIMAL_PLACES
      },
    capture = if (type.isGeometry) capture else GeometryCapture.GPS_ONLY,
    mediaSource = if (type.isMedia) mediaSource else MediaSource.CAPTURE_OR_UPLOAD,
  )

private fun EditorSaveTo.afterTypeChange(key: String, type: EditorQuestionType): EditorSaveTo {
  if (type == EditorQuestionType.NOTE) return withoutQuestion(key)
  val allowed = RelevanceOperator.availableFor(type)
  val updatedMapping =
    if (!type.isGeometry && propertyFor(key) == SaveToRules.GEOMETRY_PROPERTY) {
      withMapping(key, null)
    } else {
      this
    }
  return updatedMapping.copy(
    status =
      updatedMapping.status.copy(
        rules =
          updatedMapping.status.rules.map { rule ->
            if (
              rule.subject == StatusConditionSubject.QUESTION &&
                rule.questionKey == key &&
                rule.operator !in allowed
            ) {
              rule.copy(operator = allowed.first(), value = "")
            } else {
              rule
            }
          }
      )
  )
}

/**
 * [EditorForm.questions] without [keys], with display logic and validation refs that tested them
 * removed.
 */
internal fun EditorForm.questionsWithout(keys: Set<String>): List<EditorQuestion> =
  questions
    .filterNot { it.key in keys }
    .map { q ->
      val updatedValidation =
        q.validation?.let { v ->
          val cleaned =
            v.copy(
              comparison = v.comparison?.takeIf { it.targetQuestionKey !in keys },
              distanceReferenceQuestionKey =
                if (v.distanceReferenceQuestionKey in keys) "" else v.distanceReferenceQuestionKey,
              condition = v.condition?.takeIf { it.sourceQuestionKey !in keys },
            )
          cleaned.takeIf {
            it != org.openforis.collectone.v2.app.domain.model.editor.EditorValidation()
          }
        }
      q.copy(
        relevance = q.relevance?.takeIf { it.sourceQuestionKey !in keys },
        requiredCondition = q.requiredCondition?.takeIf { it.sourceQuestionKey !in keys },
        validation = updatedValidation,
      )
    }

private fun EditorForm.deletedQuestionsWith(removed: List<EditorQuestion>): List<EditorQuestion> {
  if (removed.isEmpty()) return deletedQuestions
  val existingKeys = deletedQuestions.mapTo(mutableSetOf()) { it.key }
  val existingNames = deletedQuestions.mapTo(mutableSetOf()) { it.name }
  val newlyDeleted =
    removed
      .filter { it.key !in existingKeys && it.name !in existingNames }
      .map { it.copy(isDeleted = true, relevance = null, parentQuestionKey = "") }
  return deletedQuestions + newlyDeleted
}

/**
 * This Form without questions [keys]: removed questions are retained in [deletedQuestions] with
 * [EditorQuestion.isDeleted] set so existing data and schema fields are preserved, while display
 * logic of questions and sections that tested them is removed (so a section that depended on one is
 * always shown again), as are save-to references.
 */
internal fun EditorForm.withoutQuestions(keys: Set<String>): EditorForm =
  copy(
    questions = questionsWithout(keys),
    deletedQuestions = deletedQuestionsWith(questions.filter { it.key in keys }),
    sections =
      sections.map {
        if (it.relevance?.sourceQuestionKey in keys) it.copy(relevance = null) else it
      },
    saveTo = keys.fold(saveTo) { saveTo, key -> saveTo.withoutQuestion(key) },
  )

/**
 * This Form with choice value [oldValue] of question [key] renamed to [newValue] in the display
 * logic of questions and sections that compare against it.
 */
internal fun EditorForm.withChoiceValueRenamed(
  key: String,
  oldValue: String,
  newValue: String,
): EditorForm {
  fun EditorRelevance?.renamed(): EditorRelevance? =
    if (this != null && sourceQuestionKey == key && value == oldValue) copy(value = newValue)
    else this
  return copy(
    questions =
      questions.map { q ->
        q.copy(
          relevance = q.relevance.renamed(),
          requiredCondition = q.requiredCondition.renamed(),
          validation = q.validation?.let { v -> v.copy(condition = v.condition.renamed()) },
        )
      },
    sections = sections.map { it.copy(relevance = it.relevance.renamed()) },
  )
}

/**
 * Soft-deletes question [key], recording it in [deletedQuestions] with [EditorQuestion.isDeleted]
 * set, clearing dependent question and section display logic and save-to bindings, and keeping
 * empty section anchors aligned with the remaining questions.
 */
internal fun EditorForm.removingQuestion(key: String): EditorForm {
  val removedIndex = indexOf(key)
  if (removedIndex < 0) return this
  val removedQuestion = questions[removedIndex]
  val oldSectionId = removedQuestion.sectionId
  val remaining = questionsWithout(setOf(key))
  val updatedSections = sections.map { rawSec ->
    val sec =
      if (rawSec.relevance?.sourceQuestionKey == key) rawSec.copy(relevance = null) else rawSec
    val anchor = sec.insertAtIndex
    when {
      sec.id == oldSectionId && remaining.none { it.sectionId == oldSectionId } ->
        sec.copy(insertAtIndex = removedIndex)
      anchor != null && anchor > removedIndex -> sec.copy(insertAtIndex = anchor - 1)
      else -> sec
    }
  }
  return copy(
    questions = remaining,
    deletedQuestions = deletedQuestionsWith(listOf(removedQuestion)),
    sections = updatedSections,
    saveTo = saveTo.withoutQuestion(key),
  )
}

/**
 * Applies an update of the template this Form was created from: removes [removedQuestionKeys] (with
 * the display logic and save-to settings that used them) and appends [addedQuestions] unsectioned,
 * renaming their keys and names where they clash with the questions kept. A blank name becomes
 * `field`.
 */
internal fun EditorForm.withTemplateUpdate(
  addedQuestions: List<EditorQuestion>,
  removedQuestionKeys: Set<String>,
  keys: QuestionKeyAllocator,
  templateId: String? = null,
  templateVersion: Int? = null,
): EditorForm {
  val withoutRemoved = withoutQuestions(removedQuestionKeys)
  val remaining = withoutRemoved.questions
  val takenKeys = withoutRemoved.allQuestions.map { it.key }.toMutableSet()
  val takenNames = withoutRemoved.allQuestions.map { it.name }.toMutableSet()
  val preparedAdditions = addedQuestions.map { q ->
    val newKey =
      if (q.key.isBlank() || q.key in takenKeys) keys.allocate { it.isBlank() || it in takenKeys }
      else q.key
    takenKeys += newKey
    val newName = uniqueName(nameStem(q.name).ifBlank { "field" }, takenNames)
    takenNames += newName
    q.copy(key = newKey, name = newName, sectionId = "")
  }
  val currentOrigin = withoutRemoved.templateOrigin
  val updatedOrigin =
    when {
      currentOrigin != null && templateVersion != null ->
        currentOrigin.copy(templateVersion = maxOf(currentOrigin.templateVersion, templateVersion))
      currentOrigin == null && templateId != null && templateVersion != null ->
        org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin(
          templateId = templateId,
          templateVersion = templateVersion,
          modified = true,
        )
      else -> currentOrigin
    }
  return withoutRemoved.copy(
    questions = remaining + preparedAdditions,
    templateOrigin = updatedOrigin,
  )
}
