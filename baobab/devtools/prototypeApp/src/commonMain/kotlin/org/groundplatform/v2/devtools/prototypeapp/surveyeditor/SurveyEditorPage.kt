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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DragAxis
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DragReorderState
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorPage
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.formeditor.SectionLabel
import org.groundplatform.v2.devtools.prototypeapp.formeditor.dragToReorder

/**
 * Survey editor page: a left-hand navigation list (Survey details, Sharing, Forms, Map layers, Data
 * tables) and a content pane showing the editor for the selected item.
 */
@Composable
fun SurveyEditorPage(
  state: SurveyEditorState,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  onBackToDashboard: (() -> Unit)? = null,
) {
  Row(modifier = modifier.fillMaxSize()) {
    SurveyNavigation(
      state = state,
      modifier = Modifier.width(280.dp).fillMaxHeight(),
      onBackToDashboard = onBackToDashboard,
    )
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
      when (val section = state.section) {
        SurveyEditorSection.Details -> SurveyDetailsPane(state)
        SurveyEditorSection.Sharing -> SharingPane(state)
        is SurveyEditorSection.Form -> {
          val entry = state.selectedForm
          if (entry != null) {
            key(entry.key) {
              FormEditorPage(
                state = entry.editor,
                isDarkTheme = isDarkTheme,
                onDelete = { state.deleteForm(entry.key) },
              )
            }
          }
        }
        is SurveyEditorSection.Dataset -> {
          val dataset = state.selectedDataset
          if (dataset != null) {
            key(section.key) { EntityDatasetEditor(state, dataset) }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------

@Composable
private fun SurveyNavigation(
  state: SurveyEditorState,
  modifier: Modifier = Modifier,
  onBackToDashboard: (() -> Unit)? = null,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              start = if (onBackToDashboard != null) 4.dp else 16.dp,
              end = 16.dp,
              top = 8.dp,
              bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (onBackToDashboard != null) {
          IconButton(onClick = onBackToDashboard, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to web dashboard",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Survey editor",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = state.details.title.ifBlank { "Untitled survey" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      NavItem(
        label = "Survey details",
        icon = Icons.Default.Info,
        selected = state.section == SurveyEditorSection.Details,
        onClick = { state.select(SurveyEditorSection.Details) },
      )
      NavItem(
        label = "Sharing",
        icon = Icons.Default.Person,
        selected = state.section == SurveyEditorSection.Sharing,
        onClick = { state.select(SurveyEditorSection.Sharing) },
        trailing = "${state.sharing.collaborators.size + 1}",
      )

      NavHeading("Forms", addDescription = "Add form", onAdd = state::addForm)
      if (state.forms.isEmpty()) NavEmpty("No forms yet")
      ReorderableNavList(
        items = state.forms,
        keyOf = { it.key },
        onMove = state::moveForm,
      ) { entry, showHandle ->
        val form = entry.editor.form
        NavItem(
          label = form.title.ifBlank { "Untitled form" },
          icon = if (showHandle) Icons.Default.DragIndicator else Icons.Default.Description,
          selected = state.section == SurveyEditorSection.Form(entry.key),
          onClick = { state.select(SurveyEditorSection.Form(entry.key)) },
          trailing = "${form.questions.size}",
          hasIssues = entry.editor.issues.isNotEmpty(),
          nested = true,
        )
      }

      DatasetNavGroup(state, DatasetKind.MAP_LAYER, state.mapLayers, Icons.Default.Layers)
      DatasetNavGroup(
        state,
        DatasetKind.DATA_TABLE,
        state.dataTables,
        Icons.AutoMirrored.Filled.List,
      )
    }
  }
}

@Composable
private fun DatasetNavGroup(
  state: SurveyEditorState,
  kind: DatasetKind,
  datasets: List<EntityDataset>,
  icon: ImageVector,
) {
  NavHeading(
    kind.plural,
    addDescription = "Add ${kind.singular.lowercase()}",
    onAdd = { state.addDataset(kind) },
  )
  if (datasets.isEmpty()) NavEmpty("No ${kind.plural.lowercase()} yet")
  ReorderableNavList(
    items = datasets,
    keyOf = { it.key },
    onMove = state::moveDataset,
  ) { dataset, showHandle ->
    NavItem(
      label = dataset.displayName.ifBlank { "Untitled" },
      icon = if (showHandle) Icons.Default.DragIndicator else icon,
      selected = state.section == SurveyEditorSection.Dataset(dataset.key),
      onClick = { state.select(SurveyEditorSection.Dataset(dataset.key)) },
      trailing = "${dataset.rows.size}",
      hasIssues = state.datasetIssues(dataset).isNotEmpty(),
      nested = true,
    )
  }
}

private val NavItemHeight = 44.dp
private val NavItemSpacing = 2.dp

/**
 * A vertical list of navigation items that can be reordered by dragging. [itemContent] receives
 * whether to show a drag handle (while hovered or dragged).
 */
@Composable
private fun <T> ReorderableNavList(
  items: List<T>,
  keyOf: (T) -> String,
  onMove: (key: String, toIndex: Int) -> Unit,
  itemContent: @Composable (item: T, showHandle: Boolean) -> Unit,
) {
  val drag = remember { DragReorderState() }
  val pitchPx = with(LocalDensity.current) { (NavItemHeight + NavItemSpacing).toPx() }
  Column(verticalArrangement = Arrangement.spacedBy(NavItemSpacing)) {
    items.forEachIndexed { index, item ->
      val itemKey = keyOf(item)
      key(itemKey) {
        val isDragged = drag.draggingKey == itemKey
        val shift by
          animateFloatAsState(
            targetValue = drag.shiftFor(index) * pitchPx,
            animationSpec = if (drag.isDragging) spring() else snap(),
          )
        val hover = remember { MutableInteractionSource() }
        val isHovered by hover.collectIsHoveredAsState()
        Surface(
          color =
            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.extraLarge,
          modifier =
            Modifier.zIndex(if (isDragged) 1f else 0f)
              .graphicsLayer {
                translationY = if (isDragged) drag.offset else shift
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                shape = RoundedCornerShape(28.dp)
                clip = isDragged
              }
              .hoverable(hover)
              .dragToReorder(
                state = drag,
                key = itemKey,
                index = index,
                count = items.size,
                pitchPx = pitchPx,
                axis = DragAxis.VERTICAL,
                onMove = onMove,
              )
              .semantics {
                customActions = buildList {
                  if (index > 0) {
                    add(
                      CustomAccessibilityAction("Move up") {
                        onMove(itemKey, index - 1)
                        true
                      }
                    )
                  }
                  if (index < items.lastIndex) {
                    add(
                      CustomAccessibilityAction("Move down") {
                        onMove(itemKey, index + 1)
                        true
                      }
                    )
                  }
                }
              },
        ) {
          itemContent(item, isHovered || isDragged)
        }
      }
    }
  }
}

@Composable
private fun NavHeading(text: String, addDescription: String, onAdd: () -> Unit) {
  Column {
    HorizontalDivider(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = addDescription) }
    }
  }
}

@Composable
private fun NavEmpty(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
  )
}

@Composable
private fun NavItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  trailing: String? = null,
  hasIssues: Boolean = false,
  nested: Boolean = false,
) {
  NavigationDrawerItem(
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
    badge = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasIssues) {
          Icon(
            Icons.Default.Warning,
            contentDescription = "Has issues",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium)
      }
    },
    colors =
      NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    modifier = Modifier.height(44.dp).padding(start = if (nested) 12.dp else 0.dp),
  )
}

// ---------------------------------------------------------------------------------------------
// Survey details
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PaneScaffold(
  title: String,
  subtitle: String,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Column(modifier = Modifier.widthIn(max = 760.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    content()
  }
}

@Composable
private fun SurveyDetailsPane(state: SurveyEditorState) {
  val details = state.details
  PaneScaffold(
    title = "Survey details",
    subtitle = "Basic information shown to data collectors when they open the survey.",
  ) {
    Column(
      modifier = Modifier.widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      OutlinedTextField(
        value = details.title,
        onValueChange = { v -> state.updateDetails { it.copy(title = v) } },
        label = { Text("Survey title") },
        singleLine = true,
        isError = details.title.isBlank(),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.description,
        onValueChange = { v -> state.updateDetails { it.copy(description = v) } },
        label = { Text("Description") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = details.surveyId,
          onValueChange = { v -> state.updateDetails { it.copy(surveyId = v.trim()) } },
          label = { Text("Survey ID") },
          singleLine = true,
          isError = !FormEditorValidator.isValidName(details.surveyId),
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = details.defaultLanguage,
          onValueChange = { v -> state.updateDetails { it.copy(defaultLanguage = v.trim()) } },
          label = { Text("Default language") },
          singleLine = true,
          modifier = Modifier.width(160.dp),
        )
      }
      OutlinedTextField(
        value = details.supportedLanguages.joinToString(", "),
        onValueChange = { v ->
          state.updateDetails { d ->
            d.copy(supportedLanguages = v.split(',').map { it.trim() }.filter { it.isNotEmpty() })
          }
        },
        label = { Text("Supported languages") },
        supportingText = { Text("Comma-separated language codes, e.g. en, sw") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    SectionLabel("Contents")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      SummaryCard("Forms", state.forms.size) {
        state.forms.firstOrNull()?.let { state.select(SurveyEditorSection.Form(it.key)) }
      }
      SummaryCard("Map layers", state.mapLayers.size) {
        state.mapLayers.firstOrNull()?.let { state.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard("Data tables", state.dataTables.size) {
        state.dataTables.firstOrNull()?.let { state.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard("People", state.sharing.collaborators.size + 1) {
        state.select(SurveyEditorSection.Sharing)
      }
    }
  }
}

@Composable
private fun SummaryCard(label: String, count: Int, onClick: () -> Unit) {
  OutlinedCard(onClick = onClick, modifier = Modifier.width(160.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Sharing
// ---------------------------------------------------------------------------------------------

@Composable
private fun SharingPane(state: SurveyEditorState) {
  val sharing = state.sharing
  var acceptingEmail by remember { mutableStateOf<String?>(null) }
  sharing.collaborators
    .firstOrNull { it.email == acceptingEmail && it.status == InvitationStatus.PENDING }
    ?.let { invitee ->
      AcceptInviteDialog(
        surveyTitle = state.details.title,
        invitee = invitee,
        onAccept = { name, photo -> state.acceptInvite(invitee.email, name, photo) },
        onDismiss = { acceptingEmail = null },
      )
    }
  PaneScaffold(
    title = "Sharing",
    subtitle = "Choose who can open this survey and what data collectors can see.",
  ) {
    Column(
      modifier = Modifier.widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SectionLabel("People with access")
          InviteRow(state)
          PersonRow(
            displayName = sharing.ownerProfile?.displayName ?: sharing.ownerEmail,
            email = sharing.ownerEmail,
            photoUrl = sharing.ownerProfile?.photoUrl,
            detail = "Survey owner",
          ) {
            Text(
              "Owner",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
          }
          sharing.collaborators.forEach { person ->
            key(person.email) {
              val pending = person.status == InvitationStatus.PENDING
              PersonRow(
                displayName = person.displayName,
                email = person.email,
                photoUrl = person.profile?.photoUrl,
                pending = pending,
                detail =
                  when {
                    pending -> "Waiting for them to accept their invite link"
                    person.profile?.cachedOn.isNullOrBlank() -> "Joined just now"
                    else -> "Joined ${person.profile?.cachedOn}"
                  },
                footer =
                  person.inviteToken
                    ?.takeIf { pending }
                    ?.let { token ->
                      {
                        PendingInviteLinkRow(
                          token = token,
                          onResetLink = { state.resetInviteLink(person.email) },
                          onOpenAsInvitee = { acceptingEmail = person.email },
                        )
                      }
                    },
              ) {
                Box(modifier = Modifier.width(190.dp)) {
                  DropdownSelector(
                    label = "Role",
                    selectedText = person.role.label,
                    options = CollaboratorRole.entries,
                    optionText = { it.label },
                    onSelect = { state.setCollaboratorRole(person.email, it) },
                  )
                }
                IconButton(onClick = { state.removeCollaborator(person.email) }) {
                  Icon(Icons.Default.Close, contentDescription = "Remove ${person.displayName}")
                }
              }
            }
          }
          Text(
            "Names and photos come from each person's account. Ground saves them when they " +
              "accept their invite link, and refreshes them when they sign in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SectionLabel("General access")
          SharingPolicy.entries.forEach { policy ->
            RadioOption(
              title = policy.label,
              description = policy.description,
              selected = sharing.policy == policy,
              onSelect = { state.updateSharing { it.copy(policy = policy) } },
            )
          }
        }
      }

      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SectionLabel("What data collectors can see")
          PeerDataVisibility.entries.forEach { visibility ->
            RadioOption(
              title = visibility.label,
              description = visibility.description,
              selected = sharing.peerDataVisibility == visibility,
              onSelect = { state.updateSharing { it.copy(peerDataVisibility = visibility) } },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InviteRow(state: SurveyEditorState) {
  var email by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(CollaboratorRole.DATA_COLLECTOR) }
  var error by remember { mutableStateOf<String?>(null) }
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it
        error = null
      },
      label = { Text("Add people by email") },
      singleLine = true,
      isError = error != null,
      supportingText = error?.let { { Text(it) } },
      modifier = Modifier.weight(1f),
    )
    Box(modifier = Modifier.width(190.dp).padding(top = 6.dp)) {
      DropdownSelector(
        label = "Role",
        selectedText = role.label,
        options = CollaboratorRole.entries,
        optionText = { it.label },
        onSelect = { role = it },
      )
    }
    Button(
      onClick = {
        error = state.inviteCollaborator(email, role)
        if (error == null) email = ""
      },
      enabled = email.isNotBlank(),
      modifier = Modifier.padding(top = 10.dp),
    ) {
      Text("Invite")
    }
  }
}

@Composable
private fun RadioOption(
  title: String,
  description: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(Modifier.width(12.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
