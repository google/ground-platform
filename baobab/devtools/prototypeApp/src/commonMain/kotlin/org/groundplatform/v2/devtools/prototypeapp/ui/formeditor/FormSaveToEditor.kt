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
package org.groundplatform.v2.devtools.prototypeapp.ui.formeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceColors
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDatasetProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusRule
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityIdSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.StatusConditionSubject
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.StatusMarkerSymbol
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormEditorActions

/**
 * Whether "Advanced" sections are expanded, shared by every Advanced section in the editor.
 *
 * Sections start collapsed (unless their settings are customized or have issues). Expanding or
 * collapsing one does the same to all of them for the rest of the session.
 */
object AdvancedDisclosure {
  /** Explicit choice, or `null` if the user hasn't toggled any Advanced section yet. */
  var expanded: Boolean? by mutableStateOf(null)

  /** Whether a section is expanded; [autoExpand] applies until the user toggles one. */
  fun isExpanded(autoExpand: Boolean): Boolean = expanded ?: autoExpand

  fun toggle(autoExpand: Boolean) {
    expanded = !isExpanded(autoExpand)
  }
}

/** Form-level properties, shown when the Form itself (or no question) is selected. */
@Composable
internal fun FormProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
  onCreateDataset: (() -> Unit)? = null,
  onUnlinkDataset: ((String) -> Unit)? = null,
) {
  val form = uiState.form
  val formIssues = uiState.formIssues
  val target = uiState.saveTarget
  val linkedDataset = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column {
      Text(
        text = "Form properties",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "${form.questions.size} questions • ${uiState.pathCount} potential paths",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    OutlinedTextField(
      value = form.title,
      onValueChange = actions::updateTitle,
      label = { Text("Form title") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (form.questions.isEmpty()) {
      Text(
        text = "This form has no questions yet. Use \"+\" on the canvas to add one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (formIssues.isNotEmpty()) IssueList(formIssues)

    SectionLabel("Availability")
    AvailabilityToggles(uiState, actions)

    SectionLabel("Submissions")
    if (form.saveTo.mode == SaveToMode.CREATE && linkedDataset != null) {
      val kindLabel = if (linkedDataset.isMapLayer) "map layer" else "data table"
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Outlined.Link,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              "Linked to ${linkedDataset.displayName}",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            "Each submission adds a new ${linkedDataset.featureNoun} to this $kindLabel. Schema properties stay in sync with form questions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          if (
            onOpenDataset != null || (onUnlinkDataset != null && linkedDataset.key.isNotBlank())
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (onOpenDataset != null) {
                Button(onClick = { onOpenDataset(linkedDataset.id) }) {
                  Icon(
                    datasetIcon(linkedDataset),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text("Edit $kindLabel")
                }
              }
              if (onUnlinkDataset != null && linkedDataset.key.isNotBlank()) {
                OutlinedButton(
                  onClick = { onUnlinkDataset(linkedDataset.key) },
                  colors =
                    ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                  border =
                    BorderStroke(
                      1.dp,
                      MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    ),
                ) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text("Unlink $kindLabel")
                }
              }
            }
          }
        }
      }
    } else if (form.saveTo.mode == SaveToMode.CREATE && onCreateDataset != null) {
      val kindLabel = if (form.hasGeometry) "map layer" else "data table"
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            submissionDescription(form, target),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = onCreateDataset) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Create $kindLabel for form")
          }
        }
      }
    } else {
      Text(
        text = submissionDescription(form, target),
        style = MaterialTheme.typography.bodyMedium,
      )
      if (target != null) {
        AssistChip(
          onClick = { onOpenDataset?.invoke(target.id) },
          enabled = onOpenDataset != null,
          label = { Text(target.displayName) },
          leadingIcon = {
            Icon(datasetIcon(target), contentDescription = null, modifier = Modifier.size(16.dp))
          },
          trailingIcon =
            if (onOpenDataset != null) {
              {
                Icon(
                  Icons.AutoMirrored.Outlined.OpenInNew,
                  contentDescription = "Open ${target.displayName}",
                  modifier = Modifier.size(16.dp),
                )
              }
            } else {
              null
            },
        )
      }
    }

    AdvancedSection(
      summary = saveToSummary(form, target),
      autoExpand = form.saveTo.isCustomized || formIssues.isNotEmpty(),
    ) {
      SaveToEditor(uiState, actions, onSaveToModeChange)
    }
  }
}

/**
 * Two switches, one per platform, for where collectors can open the Form. Each row's caption spells
 * out the current effect; a warning appears when both are off, and an error with a **Fix all**
 * action when the Form is on for web but has GPS-only geometry questions web can't capture.
 */
@Composable
private fun AvailabilityToggles(uiState: FormEditorUiState, actions: FormEditorActions) {
  val availability = uiState.form.availability
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.MOBILE),
      title = "Available on mobile",
      platform = FormPreviewTarget.MOBILE.sentenceName(),
      checked = availability.includesMobile,
      onCheckedChange = { actions.updateAvailability(availability.withMobile(it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.WEB),
      title = "Available on web",
      platform = FormPreviewTarget.WEB.sentenceName(),
      checked = availability.includesWeb,
      onCheckedChange = { actions.updateAvailability(availability.withWeb(it)) },
    )
    val incompatible = uiState.webIncompatibleGeometryQuestions
    if (incompatible.isNotEmpty()) {
      WebIncompatibleGeometryRow(
        count = incompatible.size,
        onFixAll = actions::makeGeometryQuestionsWebCompatible,
        modifier = Modifier.padding(start = 14.dp, end = 6.dp, bottom = 6.dp),
      )
    }
  }
  if (availability == FormAvailability.NONE) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Outlined.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = "Collectors can't open this form anywhere until one of these is turned on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
  }
}

/**
 * Error line under **Available on web** when [count] geometry questions are GPS only, which the web
 * dashboard can't capture. **Fix all** switches them to [GeometryCapture.GPS_OR_MAP].
 */
@Composable
internal fun WebIncompatibleGeometryRow(
  count: Int,
  onFixAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.ErrorOutline,
      contentDescription = null,
      tint = colors.error,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = webIncompatibleSummary(count),
      style = MaterialTheme.typography.bodySmall,
      color = colors.error,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onFixAll) { Text("Fix all") }
  }
}

/** Plain-language summary of [count] GPS-only geometry questions in a web-enabled Form. */
internal fun webIncompatibleSummary(count: Int): String {
  val subject = if (count == 1) "1 geometry question is" else "$count geometry questions are"
  return "$subject GPS only and can't be answered on web. " +
    "Switch them to \"${GeometryCapture.GPS_OR_MAP.label}\"."
}

/** One platform's row in [AvailabilityToggles]: icon, title, effect caption, and switch. */
@Composable
private fun AvailabilityToggleRow(
  icon: ImageVector,
  title: String,
  platform: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (checked) colors.primary else colors.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text =
          if (checked) "Collectors can open this form in $platform."
          else "Hidden from collectors in $platform.",
        style = MaterialTheme.typography.bodySmall,
        color = if (checked) colors.onSurfaceVariant else colors.tertiary,
      )
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

/** Collapsible "Advanced" section whose expanded state is shared via [AdvancedDisclosure]. */
@Composable
internal fun AdvancedSection(
  summary: String,
  autoExpand: Boolean,
  content: @Composable () -> Unit,
) {
  val expanded = AdvancedDisclosure.isExpanded(autoExpand)
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(
            onClickLabel = if (expanded) "Collapse" else "Expand",
            role = Role.Button,
          ) {
            AdvancedDisclosure.toggle(autoExpand)
          }
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Outlined.Tune,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (!expanded) {
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
      )
    }
    if (expanded) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        content()
      }
    }
  }
}

/** Chooses between adding new features and updating existing ones, and how updates work. */
@Composable
private fun SaveToEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
) {
  val form = uiState.form
  val saveTo = form.saveTo
  val targets = uiState.updateTargets
  val linked = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  val updateTarget = if (saveTo.mode == SaveToMode.UPDATE) uiState.saveTarget else null

  SectionLabel("When the form is submitted")
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SaveToOption(
      selected = saveTo.mode == SaveToMode.CREATE,
      title = SaveToRules.createLabel(form.hasGeometry),
      description =
        linked?.let { "Each submission adds to ${it.displayName}." }
          ?: "A ${if (form.hasGeometry) "map layer" else "data table"} is created for this form.",
      enabled = true,
      onClick = { onSaveToModeChange(SaveToMode.CREATE) },
    )
    SaveToOption(
      selected = saveTo.mode == SaveToMode.UPDATE,
      title = SaveToRules.updateLabel(updateTarget),
      description =
        if (targets.isEmpty() && saveTo.mode != SaveToMode.UPDATE) {
          "Add another map layer or data table to the survey to use this option."
        } else {
          "Each submission changes properties of a feature that already exists."
        },
      enabled = targets.isNotEmpty() || saveTo.mode == SaveToMode.UPDATE,
      onClick = { onSaveToModeChange(SaveToMode.UPDATE) },
    )
  }
  if (saveTo.mode == SaveToMode.UPDATE) UpdateSettings(uiState, actions, targets)
  Text(
    text = "Submissions are always kept, so every change has a record of who made it and when.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  StatusMarkerEditor(uiState, actions)
}

@Composable
private fun SaveToOption(
  selected: Boolean,
  title: String,
  description: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(
          selected = selected,
          enabled = enabled,
          role = Role.RadioButton,
          onClick = onClick,
        )
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = null, enabled = enabled)
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      val contentAlpha = if (enabled) 1f else 0.38f
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
      )
    }
  }
}

/** Target dataset, feature lookup, and field mapping for [SaveToMode.UPDATE]. */
@Composable
private fun UpdateSettings(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  targets: List<EditorDataset>,
) {
  val form = uiState.form
  val saveTo = form.saveTo
  val target = uiState.saveTarget
  DropdownSelector(
    label = "Map layer or data table to update",
    selectedText = target?.displayName ?: "Choose",
    options = targets,
    optionText = { "${it.displayName} (${if (it.isMapLayer) "Map layer" else "Data table"})" },
    optionIcon = { datasetIcon(it) },
    onSelect = { actions.setTargetDataset(it.id) },
  )
  if (target == null) return

  SectionLabel("Find the ${target.featureNoun} by")
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.SELECTED_FEATURE,
      onClick = { actions.setIdSource(EntityIdSource.SELECTED_FEATURE) },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
      label = { Text(if (target.isMapLayer) "Selected on map" else "Picked from list") },
    )
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.QUESTION,
      onClick = { actions.setIdSource(EntityIdSource.QUESTION) },
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
      label = { Text("Question answer") },
    )
  }
  when (saveTo.idSource) {
    EntityIdSource.SELECTED_FEATURE ->
      HelperText(
        if (target.isMapLayer) {
          "Collectors open the form from a feature on the map. The form starts with a " +
            "\"Map feature to update\" screen, filled in automatically, so a feature can also " +
            "be picked from a list."
        } else {
          "The form starts with a \"Row to update\" screen listing the rows of ${target.displayName}."
        }
      )
    EntityIdSource.QUESTION -> {
      val question = form.find(saveTo.idQuestionKey)
      DropdownSelector(
        label = "Question",
        selectedText = question?.let { "${it.name} — ${it.label}" } ?: "Choose",
        options = SaveToRules.savableQuestions(form),
        optionText = { "${it.name} — ${it.label}" },
        optionIcon = { questionTypeIcon(it.type) },
        onSelect = { actions.setIdQuestion(it.key) },
      )
      DropdownSelector(
        label = "Matches property",
        selectedText = target.property(saveTo.idMatchProperty)?.let(::propertyText) ?: "Choose",
        options = target.matchableProperties,
        optionText = ::propertyText,
        onSelect = { actions.setIdMatchProperty(it.name) },
      )
    }
  }
  SaveToRules.entityIdExpression(form, target)?.let { xpath ->
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "id=\"$xpath\"",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.padding(8.dp),
      )
    }
  }

  SectionLabel("Fields to update")
  val questions =
    SaveToRules.savableQuestions(form).filterNot {
      saveTo.idSource == EntityIdSource.QUESTION && it.key == saveTo.idQuestionKey
    }
  if (questions.isEmpty()) {
    HelperText("Add questions to choose which properties they update.")
  }
  questions.forEach { question -> FieldMappingRow(uiState, actions, question, target) }
}

/** One question and the property its answer updates. */
@Composable
private fun FieldMappingRow(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
  target: EditorDataset,
) {
  val saveTo = uiState.form.saveTo
  val property = saveTo.propertyFor(question.key)
  val options: List<String?> = listOf(null) + SaveToRules.propertyOptions(question, target)
  val isDuplicate =
    property != null &&
      saveTo.mappings.count {
        it.property == property && uiState.form.find(it.questionKey) != null
      } > 1
  val warning =
    when {
      isDuplicate -> "Another question already updates \"$property\"."
      property != null -> SaveToRules.compatibilityWarning(question, property, target)
      else -> null
    }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        questionTypeIcon(question.type),
        contentDescription = question.type.label,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(0.45f)) {
        Text(
          text = question.label.ifBlank { question.name },
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = question.name,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      DropdownSelector(
        label = "Updates",
        selectedText = property?.let { mappingText(it, target) } ?: "Don't update",
        options = options,
        optionText = { option -> option?.let { mappingText(it, target) } ?: "Don't update" },
        onSelect = { actions.setMapping(question.key, it) },
        modifier = Modifier.weight(0.55f),
      )
    }
    if (warning != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = warning,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}

/**
 * Conditional status marker and label rules, configured in Form properties → Advanced.
 *
 * Off by default. When turned on, defaults to marking the feature as `✓ Surveyed` when this form is
 * submitted and `○ Pending` otherwise; organizers can add or reorder rules based on submission
 * count, question answers, or feature properties.
 */
@Composable
private fun StatusMarkerEditor(uiState: FormEditorUiState, actions: FormEditorActions) {
  val form = uiState.form
  val status = form.saveTo.status
  val target = uiState.saveTarget
  val noun = target?.featureNoun ?: if (form.hasGeometry) "map feature" else "table row"
  val colors = MaterialTheme.colorScheme

  SectionLabel("Status marker")
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(
            value = status.enabled,
            role = Role.Switch,
            onValueChange = actions::setStatusEnabled,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Flag,
        contentDescription = null,
        tint = if (status.enabled) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Set status marker",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text =
            if (status.enabled) {
              "Sets the $noun's status marker symbol, color, and label when submitted."
            } else {
              "Off by default. Turn on to mark ${noun}s (for example, ✓ Surveyed or ○ Pending)."
            },
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Switch(checked = status.enabled, onCheckedChange = null)
    }
  }

  if (!status.enabled) return

  HelperText(
    "Rules are checked in order. The first matching rule sets the status marker and label."
  )

  status.rules.forEachIndexed { index, rule ->
    StatusRuleCard(
      uiState = uiState,
      actions = actions,
      index = index,
      totalRules = status.rules.size,
      rule = rule,
      target = target,
    )
  }

  TextButton(onClick = actions::addStatusRule) {
    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text("Add status rule")
  }

  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Otherwise",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Default status when no rule above matches.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        }
        StatusBadgeChip(status.defaultBadge)
      }
      StatusBadgeEditor(
        badge = status.defaultBadge,
        colorDescription = "Default status color",
        onUpdate = actions::updateDefaultStatusBadge,
      )
    }
  }
}

@Composable
private fun StatusRuleCard(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  index: Int,
  totalRules: Int,
  rule: EditorStatusRule,
  target: EditorDataset?,
) {
  val form = uiState.form
  val savableQuestions = SaveToRules.savableQuestions(form)
  val propertyNoun = if (target?.isMapLayer == false) "Row property" else "Feature property"
  val subjectOptions = buildList {
    add(StatusConditionSubject.SUBMISSIONS)
    if (savableQuestions.isNotEmpty() || rule.subject == StatusConditionSubject.QUESTION) {
      add(StatusConditionSubject.QUESTION)
    }
    if (target != null || rule.subject == StatusConditionSubject.ENTITY_PROPERTY) {
      add(StatusConditionSubject.ENTITY_PROPERTY)
    }
  }
  fun subjectLabel(subject: StatusConditionSubject): String =
    when (subject) {
      StatusConditionSubject.SUBMISSIONS -> "Submission count"
      StatusConditionSubject.QUESTION -> "Question answer"
      StatusConditionSubject.ENTITY_PROPERTY -> propertyNoun
    }

  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Rule ${index + 1}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        StatusBadgeChip(rule.badge)
        if (totalRules > 1) {
          IconButton(
            onClick = { actions.moveStatusRule(index, -1) },
            enabled = index > 0,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowUp,
              contentDescription = "Move status rule ${index + 1} up",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = { actions.moveStatusRule(index, 1) },
            enabled = index < totalRules - 1,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Move status rule ${index + 1} down",
              modifier = Modifier.size(18.dp),
            )
          }
        }
        IconButton(
          onClick = { actions.removeStatusRule(index) },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove status rule ${index + 1}",
            modifier = Modifier.size(18.dp),
          )
        }
      }

      DropdownSelector(
        label = "When",
        selectedText = subjectLabel(rule.subject),
        options = subjectOptions,
        optionText = ::subjectLabel,
        onSelect = { picked ->
          actions.updateStatusRule(index) { current ->
            when (picked) {
              StatusConditionSubject.SUBMISSIONS ->
                current.copy(
                  subject = picked,
                  minSubmissions = current.minSubmissions.coerceAtLeast(1),
                )
              StatusConditionSubject.QUESTION -> {
                val q =
                  form.find(current.questionKey)
                    ?: savableQuestions.firstOrNull { it.type.hasChoices }
                    ?: savableQuestions.firstOrNull()
                val op =
                  q?.let { RelevanceOperator.availableFor(it.type).first() }
                    ?: RelevanceOperator.EQUALS
                val v = q?.resolvedChoices(uiState.datasets)?.firstOrNull()?.value.orEmpty()
                current.copy(
                  subject = picked,
                  questionKey = q?.key,
                  operator = op,
                  value = v,
                )
              }
              StatusConditionSubject.ENTITY_PROPERTY -> {
                val prop =
                  target?.property(current.property) ?: target?.matchableProperties?.firstOrNull()
                val op =
                  prop?.let { SaveToRules.operatorsForProperty(it.kind).first() }
                    ?: RelevanceOperator.EQUALS
                current.copy(
                  subject = picked,
                  property = prop?.name,
                  operator = op,
                )
              }
            }
          }
        },
      )

      when (rule.subject) {
        StatusConditionSubject.SUBMISSIONS -> {
          OutlinedTextField(
            value = rule.minSubmissions.toString(),
            onValueChange = { text ->
              val parsed = text.trim().toIntOrNull()
              if (parsed != null) {
                actions.updateStatusRule(index) { it.copy(minSubmissions = parsed) }
              } else if (text.isEmpty()) {
                actions.updateStatusRule(index) { it.copy(minSubmissions = 1) }
              }
            },
            label = { Text("At least (submissions)") },
            supportingText = {
              Text(
                if (rule.minSubmissions <= 1) {
                  "Matches when this form is submitted."
                } else {
                  "Matches when the ${target?.featureNoun ?: "feature"} has at least ${rule.minSubmissions} submissions."
                }
              )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        StatusConditionSubject.QUESTION -> {
          val question = form.find(rule.questionKey)
          DropdownSelector(
            label = "Question",
            selectedText = question?.let { "${it.name} — ${it.label}" } ?: "Choose",
            options = savableQuestions,
            optionText = { "${it.name} — ${it.label}" },
            optionIcon = { questionTypeIcon(it.type) },
            onSelect = { picked ->
              val op =
                rule.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
                  ?: RelevanceOperator.availableFor(picked.type).first()
              val v = picked.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
              actions.updateStatusRule(index) {
                it.copy(questionKey = picked.key, operator = op, value = v)
              }
            },
          )
          if (question != null) {
            DropdownSelector(
              label = "Condition",
              selectedText = rule.operator.label,
              options = RelevanceOperator.availableFor(question.type),
              optionText = { it.label },
              onSelect = { op -> actions.updateStatusRule(index) { it.copy(operator = op) } },
            )
            if (rule.operator.needsValue) {
              val questionChoices = question.resolvedChoices(uiState.datasets)
              if (question.type.hasChoices && questionChoices.isNotEmpty()) {
                DropdownSelector(
                  label = "Value",
                  selectedText =
                    questionChoices.firstOrNull { it.value == rule.value }?.label
                      ?: rule.value.ifBlank { "Pick a choice" },
                  options = questionChoices,
                  optionText = { "${it.label} (${it.value})" },
                  onSelect = { c -> actions.updateStatusRule(index) { it.copy(value = c.value) } },
                )
              } else {
                OutlinedTextField(
                  value = rule.value,
                  onValueChange = { v -> actions.updateStatusRule(index) { it.copy(value = v) } },
                  label = { Text("Value") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }
        StatusConditionSubject.ENTITY_PROPERTY -> {
          val prop = target?.property(rule.property)
          val properties = target?.matchableProperties.orEmpty()
          DropdownSelector(
            label = propertyNoun,
            selectedText = prop?.let(::propertyText) ?: "Choose",
            options = properties,
            optionText = ::propertyText,
            onSelect = { picked ->
              val op =
                rule.operator.takeIf { it in SaveToRules.operatorsForProperty(picked.kind) }
                  ?: SaveToRules.operatorsForProperty(picked.kind).first()
              actions.updateStatusRule(index) { it.copy(property = picked.name, operator = op) }
            },
          )
          if (prop != null) {
            DropdownSelector(
              label = "Condition",
              selectedText = rule.operator.label,
              options = SaveToRules.operatorsForProperty(prop.kind),
              optionText = { it.label },
              onSelect = { op -> actions.updateStatusRule(index) { it.copy(operator = op) } },
            )
            if (rule.operator.needsValue) {
              OutlinedTextField(
                value = rule.value,
                onValueChange = { v -> actions.updateStatusRule(index) { it.copy(value = v) } },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      StatusBadgeEditor(
        badge = rule.badge,
        colorDescription = "Status color for rule ${index + 1}",
        onUpdate = { transform ->
          actions.updateStatusRule(index) { it.copy(badge = transform(it.badge)) }
        },
      )
    }
  }
}

/** Edits a status outcome's color swatch, marker symbol, and status label. */
@Composable
private fun StatusBadgeEditor(
  badge: EditorStatusBadge,
  colorDescription: String,
  onUpdate: ((EditorStatusBadge) -> EditorStatusBadge) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatusColorButton(
      colorHex = badge.colorHex,
      contentDescription = colorDescription,
      onSelect = { hex -> onUpdate { it.copy(colorHex = hex) } },
    )
    DropdownSelector(
      label = "Symbol",
      selectedText = "${badge.symbol.symbol} ${badge.symbol.label}",
      options = StatusMarkerSymbol.entries,
      optionText = { "${it.symbol} ${it.label}" },
      onSelect = { sym -> onUpdate { it.copy(symbol = sym) } },
      modifier = Modifier.weight(0.45f),
    )
    OutlinedTextField(
      value = badge.label,
      onValueChange = { text -> onUpdate { it.copy(label = text) } },
      label = { Text("Status label") },
      singleLine = true,
      modifier = Modifier.weight(0.55f),
    )
  }
}

/** Color swatch button for a status badge; opens [ChoiceColors.palette]. */
@Composable
private fun StatusColorButton(
  colorHex: String,
  contentDescription: String,
  onSelect: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
      ColorDot(
        colorHex = colorHex,
        contentDescription = "$contentDescription: ${ChoiceColors.nameOf(colorHex) ?: colorHex}",
        size = 24.dp,
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      Text(
        text = "Marker color",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
      ChoiceColors.palette.chunked(5).forEach { row ->
        Row(
          modifier = Modifier.padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          row.forEach { (name, hex) ->
            IconButton(
              onClick = {
                onSelect(hex)
                expanded = false
              },
              modifier = Modifier.size(36.dp),
            ) {
              ColorDot(
                colorHex = hex,
                contentDescription = name,
                isSelected = hex.equals(colorHex, ignoreCase = true),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Live status pill preview matching `EntityStatusChip`: filled with [EditorStatusBadge.colorHex]
 * and showing `[symbol] [label]` in a contrasting foreground color.
 */
@Composable
internal fun StatusBadgeChip(badge: EditorStatusBadge, modifier: Modifier = Modifier) {
  val bg = ChoiceColors.argb(badge.colorHex)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
  val fg = if (bg.luminance() > 0.6f) Color(0xFF181D18) else Color.White
  val text = listOf(badge.symbol.symbol, badge.label.trim().ifEmpty { "Status" }).joinToString(" ")
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = bg,
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      fontWeight = FontWeight.SemiBold,
      color = fg,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun IssueList(issues: List<EditorIssue>) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      issues.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun HelperText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

internal fun datasetIcon(dataset: EditorDataset) =
  if (dataset.isMapLayer) Icons.Outlined.Layers else Icons.Outlined.TableChart

private fun propertyText(property: EditorDatasetProperty): String =
  if (property.label.isBlank() || property.label == property.name) property.name
  else "${property.label} (${property.name})"

private fun mappingText(property: String, target: EditorDataset): String =
  if (property == SaveToRules.GEOMETRY_PROPERTY) "Geometry (location)"
  else target.property(property)?.let(::propertyText) ?: property

private fun submissionDescription(form: EditorForm, target: EditorDataset?): String =
  when {
    form.saveTo.mode == SaveToMode.UPDATE ->
      if (target == null) "Each submission updates an existing feature. Choose which in Advanced."
      else "Each submission updates an existing ${target.featureNoun} in:"
    target == null -> "Submissions are saved without adding a map feature or table row."
    else -> "Each submission adds a new ${target.featureNoun} to:"
  }

private fun saveToSummary(form: EditorForm, target: EditorDataset?): String {
  val base =
    when (form.saveTo.mode) {
      SaveToMode.CREATE -> SaveToRules.createLabel(form.hasGeometry)
      SaveToMode.UPDATE ->
        SaveToRules.updateLabel(target) + (target?.let { " in ${it.displayName}" } ?: "")
    }
  val status = form.saveTo.status
  if (!status.enabled) return base
  val badges =
    (status.rules.map { "${it.badge.symbol.symbol} ${it.badge.label}" } +
        "${status.defaultBadge.symbol.symbol} ${status.defaultBadge.label}")
      .distinct()
      .joinToString(" / ")
  return "$base • Status: $badges"
}
