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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability

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
  state: FormEditorState,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
) {
  val form = state.form
  val formIssues = state.formIssues
  val target = state.saveTarget
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
        text = "${form.questions.size} questions • ${state.pathCount} potential paths",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    OutlinedTextField(
      value = form.title,
      onValueChange = state::updateTitle,
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
    AvailabilityToggles(state)

    SectionLabel("Submissions")
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

    AdvancedSection(
      summary = saveToSummary(form, target),
      autoExpand = form.saveTo.isCustomized || formIssues.isNotEmpty(),
    ) {
      SaveToEditor(state, onSaveToModeChange)
    }
  }
}

/**
 * Two switches, one per platform, for where collectors can open the Form. Each row's caption spells
 * out the current effect; a warning appears when both are off.
 */
@Composable
private fun AvailabilityToggles(state: FormEditorState) {
  val availability = state.form.availability
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.MOBILE),
      title = "Available on mobile",
      platform = FormPreviewTarget.MOBILE.sentenceName(),
      checked = availability.includesMobile,
      onCheckedChange = { state.updateAvailability(availability.withMobile(it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.WEB),
      title = "Available on web",
      platform = FormPreviewTarget.WEB.sentenceName(),
      checked = availability.includesWeb,
      onCheckedChange = { state.updateAvailability(availability.withWeb(it)) },
    )
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
private fun SaveToEditor(state: FormEditorState, onSaveToModeChange: (SaveToMode) -> Unit) {
  val form = state.form
  val saveTo = form.saveTo
  val targets = state.updateTargets
  val linked = state.datasets.firstOrNull { it.isLinkedToThisForm }
  val updateTarget = if (saveTo.mode == SaveToMode.UPDATE) state.saveTarget else null

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
  if (saveTo.mode == SaveToMode.UPDATE) UpdateSettings(state, targets)
  Text(
    text = "Submissions are always kept, so every change has a record of who made it and when.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
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
private fun UpdateSettings(state: FormEditorState, targets: List<EditorDataset>) {
  val form = state.form
  val saveTo = form.saveTo
  val target = state.saveTarget
  DropdownSelector(
    label = "Map layer or data table to update",
    selectedText = target?.displayName ?: "Choose",
    options = targets,
    optionText = { "${it.displayName} (${if (it.isMapLayer) "Map layer" else "Data table"})" },
    optionIcon = { datasetIcon(it) },
    onSelect = { state.setTargetDataset(it.id) },
  )
  if (target == null) return

  SectionLabel("Find the ${target.featureNoun} by")
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.SELECTED_FEATURE,
      onClick = { state.setIdSource(EntityIdSource.SELECTED_FEATURE) },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
      label = { Text(if (target.isMapLayer) "Selected on map" else "Picked from list") },
    )
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.QUESTION,
      onClick = { state.setIdSource(EntityIdSource.QUESTION) },
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
        onSelect = { state.setIdQuestion(it.key) },
      )
      DropdownSelector(
        label = "Matches property",
        selectedText = target.property(saveTo.idMatchProperty)?.let(::propertyText) ?: "Choose",
        options = target.matchableProperties,
        optionText = ::propertyText,
        onSelect = { state.setIdMatchProperty(it.name) },
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
  questions.forEach { question -> FieldMappingRow(state, question, target) }
}

/** One question and the property its answer updates. */
@Composable
private fun FieldMappingRow(
  state: FormEditorState,
  question: EditorQuestion,
  target: EditorDataset,
) {
  val saveTo = state.form.saveTo
  val property = saveTo.propertyFor(question.key)
  val options: List<String?> = listOf(null) + SaveToRules.propertyOptions(question, target)
  val isDuplicate =
    property != null &&
      saveTo.mappings.count { it.property == property && state.form.find(it.questionKey) != null } >
        1
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
        onSelect = { state.setMapping(question.key, it) },
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

private fun datasetIcon(dataset: EditorDataset) =
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

private fun saveToSummary(form: EditorForm, target: EditorDataset?): String =
  when (form.saveTo.mode) {
    SaveToMode.CREATE -> SaveToRules.createLabel(form.hasGeometry)
    SaveToMode.UPDATE ->
      SaveToRules.updateLabel(target) + (target?.let { " in ${it.displayName}" } ?: "")
  }
