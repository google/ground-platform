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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.editor.ConceptLinking
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.web.ui.formeditor.DropdownSelector

/**
 * Dialog for adding, reordering, removing, and editing questions (label, type, required flag,
 * linked dictionary concept, and select choices) within an organization [FormTemplate].
 */
@Composable
internal fun TemplateQuestionsDialog(
  template: FormTemplate,
  availableConcepts: List<LibraryConcept>,
  onSave: (List<EditorQuestion>) -> String?,
  onDismiss: () -> Unit,
) {
  val questions =
    remember(template.id) {
      mutableStateListOf<EditorQuestion>().apply { addAll(template.form.questions) }
    }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit questions — ${template.title.text}") },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (questions.isEmpty()) {
          Text(
            text = "No questions in this template yet. Click \"+ Add question\" below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        questions.forEachIndexed { index, question ->
          key(question.key) {
            TemplateQuestionCard(
              index = index,
              questionCount = questions.size,
              question = question,
              availableConcepts = availableConcepts,
              takenNames = questions.map { it.name }.toSet(),
              onMoveUp = {
                if (index > 0) {
                  val prev = questions[index - 1]
                  questions[index - 1] = question
                  questions[index] = prev
                }
              },
              onMoveDown = {
                if (index < questions.lastIndex) {
                  val next = questions[index + 1]
                  questions[index + 1] = question
                  questions[index] = next
                }
              },
              onDelete = { questions.removeAt(index) },
              onUpdate = { updated ->
                error = null
                questions[index] = updated
              },
            )
          }
        }

        OutlinedButton(
          onClick = {
            val nextNum = questions.size + 1
            var candidateId = "q_$nextNum"
            var suffix = nextNum
            while (questions.any { it.key == candidateId }) {
              suffix += 1
              candidateId = "q_$suffix"
            }
            questions.add(
              EditorQuestion(
                key = candidateId,
                name = candidateId,
                label = "Question $nextNum",
                type = EditorQuestionType.TEXT,
              )
            )
          }
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Add question")
        }
      }
    },
    confirmButton = {
      Button(onClick = { error = onSave(questions.toList()) }) {
        Text("Save questions")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun TemplateQuestionCard(
  index: Int,
  questionCount: Int,
  question: EditorQuestion,
  availableConcepts: List<LibraryConcept>,
  takenNames: Set<String>,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
  onUpdate: (EditorQuestion) -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Question ${index + 1}",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onMoveUp, enabled = index > 0) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move question up")
          }
          IconButton(onClick = onMoveDown, enabled = index < questionCount - 1) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move question down")
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete question")
          }
        }
      }

      OutlinedTextField(
        value = question.label,
        onValueChange = { newLabel -> onUpdate(question.copy(label = newLabel)) },
        label = { Text("Question label") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          DropdownSelector(
            label = "Question type",
            selectedText = question.type.label,
            options = EditorQuestionType.entries,
            optionText = { it.label },
            onSelect = { newType ->
              val defaultChoices =
                if (
                  (newType == EditorQuestionType.SELECT_ONE ||
                    newType == EditorQuestionType.SELECT_MULTIPLE) && question.choices.isEmpty()
                ) {
                  listOf(
                    EditorChoice(value = "option_1", label = "Option 1", code = "option_1"),
                    EditorChoice(value = "option_2", label = "Option 2", code = "option_2"),
                  )
                } else {
                  question.choices
                }
              onUpdate(question.copy(type = newType, choices = defaultChoices))
            },
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = question.required,
            onCheckedChange = { isRequired -> onUpdate(question.copy(required = isRequired)) },
          )
          Text("Required", style = MaterialTheme.typography.bodySmall)
        }
      }

      val linkedConcept = availableConcepts.firstOrNull {
        it.id == question.conceptLink?.conceptId
      }
      DropdownSelector(
        label = "Linked dictionary concept",
        selectedText =
          if (linkedConcept == null) "None (unlinked)"
          else "${linkedConcept.label.text} (${linkedConcept.id})",
        options = listOf<LibraryConcept?>(null) + availableConcepts,
        optionText = { c ->
          if (c == null) "None (unlinked)" else "${c.label.text} (${c.id})"
        },
        onSelect = { selectedConcept ->
          onUpdate(
            if (selectedConcept == null) {
              question.copy(
                conceptLink = null,
                choices = question.choices.map { it.copy(code = null) },
              )
            } else {
              ConceptLinking.link(
                question = question,
                concept = selectedConcept,
                takenNames = takenNames,
                language = LocalizedText.DEFAULT_LANGUAGE,
              )
            }
          )
        },
      )

      if (
        question.type == EditorQuestionType.SELECT_ONE ||
          question.type == EditorQuestionType.SELECT_MULTIPLE
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Choices",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
          )
          question.choices.forEachIndexed { choiceIdx, choice ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = choice.code ?: choice.value,
                onValueChange = { newCode ->
                  val updated = question.choices.toMutableList()
                  updated[choiceIdx] =
                    choice.copy(
                      value = newCode.ifBlank { choice.value },
                      code = newCode.ifBlank { null },
                    )
                  onUpdate(question.copy(choices = updated))
                },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.weight(0.4f),
              )
              OutlinedTextField(
                value = choice.label,
                onValueChange = { newLabel ->
                  val updated = question.choices.toMutableList()
                  updated[choiceIdx] = choice.copy(label = newLabel)
                  onUpdate(question.copy(choices = updated))
                },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.weight(0.6f),
              )
              IconButton(
                onClick = {
                  val updated = question.choices.toMutableList()
                  updated.removeAt(choiceIdx)
                  onUpdate(question.copy(choices = updated))
                }
              ) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove choice")
              }
            }
          }
          TextButton(
            onClick = {
              val nextNum = question.choices.size + 1
              val updated =
                question.choices +
                  EditorChoice(
                    value = "option_$nextNum",
                    label = "Option $nextNum",
                    code = "option_$nextNum",
                  )
              onUpdate(question.copy(choices = updated))
            }
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add choice")
          }
        }
      }
    }
  }
}
