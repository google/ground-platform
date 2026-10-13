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
package org.groundplatform.v2.web.ui.surveyeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToLong
import org.groundplatform.v2.app.domain.model.ImpactEvent
import org.groundplatform.v2.app.domain.model.InvitationStatus
import org.groundplatform.v2.app.domain.model.InviteLinks
import org.groundplatform.v2.app.domain.model.LibraryPrograms
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.SurveyLifecycleState
import org.groundplatform.v2.app.domain.model.SurveyPlaceItem
import org.groundplatform.v2.app.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.app.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.app.domain.model.editor.SharingPolicy
import org.groundplatform.v2.app.domain.model.editor.SharingSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.SurveyAreaGeometry
import org.groundplatform.v2.app.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.app.domain.model.editor.surveyUrl
import org.groundplatform.v2.app.ui.common.DragAxis
import org.groundplatform.v2.app.ui.common.ProgramChips
import org.groundplatform.v2.app.ui.common.PurposePackGrid
import org.groundplatform.v2.app.ui.common.SurveyOutcomeCard
import org.groundplatform.v2.app.ui.common.ValidationErrorBanner
import org.groundplatform.v2.app.ui.map.SurveyBasemaps
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.FeatureFilter
import org.groundplatform.v2.map.GeoJsonSource
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.GeometryType
import org.groundplatform.v2.map.GroundMap
import org.groundplatform.v2.map.LngLatBounds
import org.groundplatform.v2.map.MapCameraState
import org.groundplatform.v2.map.MapContent
import org.groundplatform.v2.map.MapFeature
import org.groundplatform.v2.map.MapInsets
import org.groundplatform.v2.map.MapLayer
import org.groundplatform.v2.map.StyleValue
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.common.WebAppHeader
import org.groundplatform.v2.web.ui.common.WebHeaderContext
import org.groundplatform.v2.web.ui.common.WebHeaderSupportingText
import org.groundplatform.v2.web.ui.common.WebMobilePrototypeButton
import org.groundplatform.v2.web.ui.dashboard.SidePanelSeparator
import org.groundplatform.v2.web.ui.dashboard.SidePanelSeparatorWidth
import org.groundplatform.v2.web.ui.formeditor.DragReorderState
import org.groundplatform.v2.web.ui.formeditor.DropdownSelector
import org.groundplatform.v2.web.ui.formeditor.FormEditorPage
import org.groundplatform.v2.web.ui.formeditor.SectionLabel
import org.groundplatform.v2.web.ui.formeditor.dragToReorder
import org.groundplatform.v2.web.ui.state.SurveyEditorSection
import org.groundplatform.v2.web.ui.state.SurveyEditorUiState
import org.groundplatform.v2.web.ui.viewmodel.SurveyEditorActions

/**
 * Survey editor page for the active survey, driven by [WebAppState.surveyEditor]: collects its
 * state and renders the stateless overload below with the shared [WebAppHeader].
 */
@Composable
fun SurveyEditorPage(
  state: WebAppState,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  val uiState by state.surveyEditor.uiState.collectAsState()
  SurveyEditorPage(
    uiState = uiState,
    actions = state.surveyEditor,
    isDarkTheme = isDarkTheme,
    modifier = modifier,
    header = { navigationIcon, context, headerActions ->
      WebAppHeader(
        state = state,
        onSignOut = null,
        navigationIcon = navigationIcon,
        context = context,
        actions = {
          WebMobilePrototypeButton(state)
          headerActions()
        },
      )
    },
  )
}

/**
 * Survey editor page: the app header with publishing controls, a resizable left-hand navigation
 * list (Survey details, Sharing, Forms, Map layers, Data tables; drag its right border to resize it
 * via [SidePanelSeparator]), and a content pane showing the editor for the selected item.
 *
 * Edits are kept as an unpublished draft in [uiState]. [SurveyEditorActions.publish] commits them;
 * [SurveyEditorActions.close] throws them away (after the user confirms, if there are any). Both
 * are expected to leave the editor. [header] renders the page header around the editor's navigation
 * icon, title context, and publishing actions.
 */
@Composable
fun SurveyEditorPage(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  header:
    @Composable
    (
      navigationIcon: @Composable () -> Unit,
      context: @Composable () -> Unit,
      headerActions: @Composable () -> Unit,
    ) -> Unit,
) {
  Column(modifier = modifier.fillMaxSize()) {
    SurveyEditorTopBar(uiState, actions, header)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    uiState.readOnlyMessage?.let { message -> ReadOnlyBanner(message, uiState, actions) }
    uiState.outcomeCard?.let { card ->
      SurveyOutcomeCard(
        card = card,
        actions = actions,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).widthIn(max = 760.dp),
      )
    }
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      SurveyNavigation(
        uiState = uiState,
        actions = actions,
        modifier = Modifier.width(uiState.sidePanelWidthDp.dp).fillMaxHeight(),
      )
      SidePanelSeparator(
        widthDp = uiState.sidePanelWidthDp,
        onWidthChange = actions::updateSidePanelWidth,
        modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
      )
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when (val section = uiState.section) {
          SurveyEditorSection.Details -> SurveyDetailsPane(uiState, actions)
          SurveyEditorSection.Sharing -> SharingPane(uiState, actions)
          is SurveyEditorSection.Form -> {
            val entry = uiState.selectedForm
            if (entry != null) {
              key(entry.key) {
                val formEditor = remember(entry.key) { actions.formEditor(entry.key) }
                val formState by formEditor.uiState.collectAsState()
                FormEditorPage(
                  uiState = formState,
                  actions = formEditor,
                  isDarkTheme = isDarkTheme,
                  onCreateDataset = { actions.createDatasetForForm(entry.key) },
                  onDelete = { actions.deleteForm(entry.key) },
                  onSaveToModeChange = { actions.setFormSaveToMode(entry.key, it) },
                  onOpenDataset = { id ->
                    uiState.datasets
                      .firstOrNull { it.id == id }
                      ?.let { actions.select(SurveyEditorSection.Dataset(it.key)) }
                  },
                  onUnlinkDataset = { datasetKey -> actions.unlinkDataset(datasetKey) },
                  onCreateFormForDataset = { datasetKey ->
                    actions.createFormForDataset(datasetKey, open = false)
                  },
                  onOpenForm = { formKey -> actions.select(SurveyEditorSection.Form(formKey)) },
                )
              }
            }
          }
          is SurveyEditorSection.Dataset -> {
            val dataset = uiState.selectedDataset
            if (dataset != null) {
              key(section.key) { EntityDatasetEditor(uiState, actions, dataset) }
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

/**
 * Close button, survey title, and clickable lifecycle status badge on the left; Discard and Save
 * draft / Publish changes on the right. Close and Discard both run [SurveyEditorActions.close],
 * asking for confirmation first if there are uncommitted changes.
 */
@Composable
private fun SurveyEditorTopBar(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  header:
    @Composable
    (
      navigationIcon: @Composable () -> Unit,
      context: @Composable () -> Unit,
      headerActions: @Composable () -> Unit,
    ) -> Unit,
) {
  val hasChanges = uiState.hasUnpublishedChanges
  val issueCount = uiState.issueCount
  var isConfirmingDiscard by remember { mutableStateOf(false) }

  header(
    {
      IconButton(onClick = { if (hasChanges) isConfirmingDiscard = true else actions.close() }) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Close survey editor",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    {
      WebHeaderContext(
        title = uiState.details.title.ifBlank { "Untitled survey" },
        titleTrailing = {
          SurveyStatusBadgeMenu(
            lifecycleState = uiState.lifecycleState,
            canManageSurvey = uiState.canManageSurvey,
            hasUnpublishedChanges = hasChanges,
            onSelectState = actions::setLifecycleState,
          )
        },
      ) {
        WebHeaderSupportingText("Survey editor", color = MaterialTheme.colorScheme.primary)
        WebHeaderSupportingText("·")
        uiState.organization(uiState.details.organizationId)?.let { organization ->
          WebHeaderSupportingText(organization.name)
          WebHeaderSupportingText("·")
        }
        when {
          uiState.isReadOnly -> WebHeaderSupportingText(uiState.lifecycleState.label)
          issueCount > 0 ->
            WebHeaderSupportingText(
              "Fix $issueCount ${if (issueCount == 1) "issue" else "issues"} to ${if (uiState.isDraft) "save" else "publish"}",
              color = MaterialTheme.colorScheme.error,
            )
          hasChanges ->
            WebHeaderSupportingText(
              if (uiState.isDraft) "Unsaved changes" else "Unpublished changes"
            )
          else ->
            WebHeaderSupportingText(if (uiState.isDraft) "Draft saved" else "All changes published")
        }
      }
    },
    {
      if (uiState.isReadOnly && uiState.canManageSurvey) {
        OutlinedButton(onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }) {
          Text("Reopen")
        }
      }
      TextButton(onClick = { isConfirmingDiscard = true }, enabled = hasChanges) { Text("Discard") }
      val canSaveOrPublish =
        if (uiState.isDraft) !uiState.isReadOnly && issueCount == 0 && !uiState.isSaving
        else uiState.canPublish
      Button(onClick = actions::publish, enabled = canSaveOrPublish) {
        Text(uiState.saveActionLabel)
      }
    },
  )

  if (isConfirmingDiscard) {
    AlertDialog(
      onDismissRequest = { isConfirmingDiscard = false },
      title = {
        Text(if (uiState.isDraft) "Discard unsaved changes?" else "Discard unpublished changes?")
      },
      text = {
        Text(
          if (uiState.isDraft) {
            "Changes you've made since this draft was last saved will be lost."
          } else {
            "Changes you've made since this survey was last published will be lost. Data " +
              "collectors will keep seeing the published version."
          }
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            isConfirmingDiscard = false
            actions.close()
          }
        ) {
          Text("Discard")
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirmingDiscard = false }) { Text("Keep editing") }
      },
    )
  }
}

/** Semantic badge tone for each [SurveyLifecycleState]. */
internal fun SurveyLifecycleState.badgeTone(): GroundBadgeTone =
  when (this) {
    SurveyLifecycleState.DRAFT -> GroundBadgeTone.WARNING
    SurveyLifecycleState.PUBLISHED -> GroundBadgeTone.PRIMARY
    SurveyLifecycleState.CLOSED -> GroundBadgeTone.SECONDARY
    SurveyLifecycleState.ARCHIVED -> GroundBadgeTone.NEUTRAL
  }

/** Plain-language summary of what a [SurveyLifecycleState] means for organizers and collectors. */
internal fun SurveyLifecycleState.statusDescription(): String =
  when (this) {
    SurveyLifecycleState.DRAFT ->
      "Editable by organizers. Hidden from mobile data collectors until published."
    SurveyLifecycleState.PUBLISHED -> "Live for field and web data collection."
    SurveyLifecycleState.CLOSED ->
      "Data collection has ended and the survey is read-only until reopened."
    SurveyLifecycleState.ARCHIVED -> "Retired and kept read-only for long-term reference."
  }

/** One lifecycle transition offered in [SurveyStatusBadgeMenu] and `Survey details`. */
internal data class SurveyLifecycleActionOption(
  val targetState: SurveyLifecycleState,
  val label: String,
  val description: String,
  val requiresConfirmation: Boolean = false,
)

/** Available lifecycle state transitions from [current]. */
internal fun lifecycleActionOptions(
  current: SurveyLifecycleState
): List<SurveyLifecycleActionOption> =
  when (current) {
    SurveyLifecycleState.DRAFT ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = "Publish survey",
          description = "Make live for data collection on mobile and web",
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = "Archive survey",
          description = "Retire and keep read-only for reference",
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.PUBLISHED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = "Switch to draft",
          description = "Pause field visibility while continuing to edit",
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.CLOSED,
          label = "Close survey",
          description = "End data collection and make read-only",
          requiresConfirmation = true,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = "Archive survey",
          description = "Retire and keep read-only for reference",
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.CLOSED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = "Reopen survey",
          description = "Resume data collection and editing",
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = "Switch to draft",
          description = "Unlock editing in draft mode before republishing",
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = "Archive survey",
          description = "Retire and keep read-only for reference",
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.ARCHIVED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = "Reopen survey",
          description = "Restore for active data collection and editing",
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = "Switch to draft",
          description = "Restore in draft mode for editing",
        ),
      )
  }

/**
 * Tonal status pill shown next to the survey title in the top banner (`Draft`, `Published`,
 * `Closed`, `Archived`). When [canManageSurvey] is `true`, clicking it opens a dropdown menu with
 * options to publish or change the survey's lifecycle state.
 */
@Composable
internal fun SurveyStatusBadgeMenu(
  lifecycleState: SurveyLifecycleState,
  canManageSurvey: Boolean,
  hasUnpublishedChanges: Boolean = false,
  onSelectState: (SurveyLifecycleState) -> Unit,
  modifier: Modifier = Modifier,
) {
  val options = remember(lifecycleState) { lifecycleActionOptions(lifecycleState) }
  var isOpen by remember { mutableStateOf(false) }
  var confirmingState by remember { mutableStateOf<SurveyLifecycleState?>(null) }

  if (!canManageSurvey || options.isEmpty()) {
    GroundTonalBadge(
      text = lifecycleState.label,
      tone = lifecycleState.badgeTone(),
      modifier = modifier,
    )
    return
  }

  val colors = MaterialTheme.colorScheme
  val (containerColor, contentColor) =
    when (lifecycleState.badgeTone()) {
      GroundBadgeTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
      GroundBadgeTone.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
      GroundBadgeTone.TERTIARY,
      GroundBadgeTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
      GroundBadgeTone.ERROR -> colors.errorContainer to colors.onErrorContainer
      GroundBadgeTone.NEUTRAL -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }

  Box(modifier = modifier) {
    Surface(
      shape = CircleShape,
      color = containerColor,
      contentColor = contentColor,
      modifier =
        Modifier.clip(CircleShape)
          .clickable { isOpen = !isOpen }
          .semantics {
            contentDescription = "Survey status: ${lifecycleState.label}. Click to change status"
          },
    ) {
      Row(
        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = lifecycleState.label,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          softWrap = false,
        )
        Icon(
          imageVector = Icons.Outlined.ArrowDropDown,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    DropdownMenu(
      expanded = isOpen,
      onDismissRequest = { isOpen = false },
      modifier = Modifier.width(300.dp),
    ) {
      Text(
        text = "Survey status: ${lifecycleState.label}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          onClick = {
            isOpen = false
            if (option.requiresConfirmation) {
              confirmingState = option.targetState
            } else {
              onSelectState(option.targetState)
            }
          },
        )
      }
    }
  }

  confirmingState?.let { state ->
    ConfirmLifecycleDialog(
      state = state,
      hasUnpublishedChanges = hasUnpublishedChanges,
      onConfirm = {
        confirmingState = null
        onSelectState(state)
      },
      onDismiss = { confirmingState = null },
    )
  }
}

/** Asks before closing or archiving the survey. */
@Composable
private fun ConfirmLifecycleDialog(
  state: SurveyLifecycleState,
  hasUnpublishedChanges: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isArchive = state == SurveyLifecycleState.ARCHIVED
  val discardNote = if (hasUnpublishedChanges) " Unsaved changes will be discarded." else ""
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isArchive) "Archive this survey?" else "Close this survey?") },
    text = {
      Text(
        (if (isArchive) {
          "Archived surveys are kept for reference. No one can collect data or make changes."
        } else {
          "Data collectors can't add new data, and no one can make changes."
        }) + " You can reopen it at any time." + discardNote
      )
    },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(if (isArchive) "Archive" else "Close survey") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** Banner shown while the survey is closed or archived, with Reopen for people who manage it. */
@Composable
private fun ReadOnlyBanner(
  message: String,
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
      Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      if (uiState.canManageSurvey) {
        TextButton(onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }) {
          Text("Reopen")
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SurveyNavigation(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  modifier: Modifier = Modifier,
) {
  var showAddForm by remember { mutableStateOf(false) }
  if (showAddForm) {
    AddFormDialog(actions, onDismiss = { showAddForm = false }, library = uiState.library)
  }
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      NavItem(
        label = "Survey details",
        icon = Icons.Outlined.Info,
        selected = uiState.section == SurveyEditorSection.Details,
        onClick = { actions.select(SurveyEditorSection.Details) },
        hasIssues = uiState.detailsIssues.isNotEmpty(),
      )
      NavItem(
        label = "Sharing",
        icon = Icons.Outlined.Person,
        selected = uiState.section == SurveyEditorSection.Sharing,
        onClick = { actions.select(SurveyEditorSection.Sharing) },
        trailing = "${uiState.sharing.collaborators.size + 1}",
        hasIssues = uiState.sharingIssues.isNotEmpty(),
      )

      NavHeading("Forms", addDescription = "Add form", onAdd = { showAddForm = true })
      if (uiState.forms.isEmpty()) NavEmpty("No forms yet")
      ReorderableNavList(items = uiState.forms, keyOf = { it.key }, onMove = actions::moveForm) {
        entry,
        showHandle ->
        val form = entry.form
        NavItem(
          label = form.title.ifBlank { "Untitled form" },
          icon = if (showHandle) Icons.Outlined.DragIndicator else Icons.Outlined.Description,
          selected = uiState.section == SurveyEditorSection.Form(entry.key),
          onClick = { actions.select(SurveyEditorSection.Form(entry.key)) },
          trailing = "${form.questions.size}",
          hasIssues = uiState.formIssues(entry).isNotEmpty(),
          nested = true,
        )
      }

      DatasetNavGroup(
        uiState,
        actions,
        DatasetKind.MAP_LAYER,
        uiState.mapLayers,
        Icons.Outlined.Layers,
      )
      DatasetNavGroup(
        uiState,
        actions,
        DatasetKind.DATA_TABLE,
        uiState.dataTables,
        Icons.AutoMirrored.Outlined.List,
      )
    }
  }
}

@Composable
private fun DatasetNavGroup(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  kind: DatasetKind,
  datasets: List<EntityDataset>,
  icon: ImageVector,
) {
  var showAddMapLayer by remember { mutableStateOf(false) }
  if (showAddMapLayer) AddMapLayerDialog(uiState, actions, onDismiss = { showAddMapLayer = false })
  NavHeading(
    kind.plural,
    addDescription = "Add ${kind.singular.lowercase()}",
    onAdd = {
      if (kind == DatasetKind.MAP_LAYER) showAddMapLayer = true else actions.addDataset(kind)
    },
  )
  if (datasets.isEmpty()) NavEmpty("No ${kind.plural.lowercase()} yet")
  ReorderableNavList(items = datasets, keyOf = { it.key }, onMove = actions::moveDataset) {
    dataset,
    showHandle ->
    NavItem(
      label = dataset.displayName.ifBlank { "Untitled" },
      icon = if (showHandle) Icons.Outlined.DragIndicator else icon,
      selected = uiState.section == SurveyEditorSection.Dataset(dataset.key),
      onClick = { actions.select(SurveyEditorSection.Dataset(dataset.key)) },
      trailing = "${dataset.rows.size}",
      hasIssues = uiState.datasetIssues(dataset).isNotEmpty(),
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
      IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = addDescription) }
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

/**
 * One row of a left-hand navigation panel: icon, label, optional trailing count, and an issue
 * marker. Shared by the survey editor and the organization page so both panels look the same.
 */
@Composable
internal fun NavItem(
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
            Icons.Outlined.Warning,
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
internal fun PaneScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
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
private fun SurveyDetailsPane(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  val details = uiState.details
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
        onValueChange = { v -> actions.updateDetails { it.copy(title = v) } },
        label = { Text("Survey title") },
        singleLine = true,
        isError = details.title.isBlank(),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.description,
        onValueChange = { v -> actions.updateDetails { it.copy(description = v) } },
        label = { Text("Description") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.surveyId,
        onValueChange = { v -> actions.updateDetails { it.copy(surveyId = v.trim()) } },
        label = { Text("Survey ID") },
        singleLine = true,
        isError = !FormEditorValidator.isValidName(details.surveyId),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      SurveyStatusSection(uiState = uiState, actions = actions)
      OrganizationSection(uiState = uiState, actions = actions)
      PurposesSection(uiState = uiState, actions = actions)
      LanguageSelectorSection(uiState = uiState, actions = actions)
      SurveyAreaSection(
        uiState = uiState,
        actions = actions,
        localPlaces = uiState.localPlaces,
        searchPlaces = actions::searchPlaces,
      )
    }

    SectionLabel("Contents")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      SummaryCard("Forms", uiState.forms.size) {
        uiState.forms.firstOrNull()?.let { actions.select(SurveyEditorSection.Form(it.key)) }
      }
      SummaryCard("Map layers", uiState.mapLayers.size) {
        uiState.mapLayers.firstOrNull()?.let { actions.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard("Data tables", uiState.dataTables.size) {
        uiState.dataTables.firstOrNull()?.let {
          actions.select(SurveyEditorSection.Dataset(it.key))
        }
      }
      SummaryCard("People", uiState.sharing.collaborators.size + 1) {
        actions.select(SurveyEditorSection.Sharing)
      }
    }

    if (uiState.activity.isNotEmpty()) ActivitySection(uiState.activity)
  }
}

/**
 * Read-only list of the survey's "data was used" events (exports, receipts, closing): what
 * happened, when, how many map features and how much area it covered, and the export profile.
 */
@Composable
private fun ActivitySection(events: List<ImpactEvent>) {
  SectionLabel("Activity")
  OutlinedCard(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
    Column {
      events.forEachIndexed { index, event ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(event.type.label, style = MaterialTheme.typography.bodyMedium)
            Text(
              activitySummary(event),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            event.occurredAt.take(10),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/** "12 map features · 3.4 ha · Profile: eudr", leaving out what the event didn't record. */
internal fun activitySummary(event: ImpactEvent): String = buildList {
  val n = event.featureCount
  add("$n ${if (n == 1) "map feature" else "map features"}")
  if (event.areaHa > 0.0) add("${(event.areaHa * 10).roundToLong() / 10.0} ha")
  event.exportProfileId?.let { add("Profile: $it") }
}
  .joinToString(" · ")

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

/**
 * Shows the survey's current lifecycle state under "Survey details" with an interactive status
 * badge menu and a direct action button to publish a draft or reopen a closed/archived survey.
 */
@Composable
private fun SurveyStatusSection(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      "Survey status",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SurveyStatusBadgeMenu(
              lifecycleState = uiState.lifecycleState,
              canManageSurvey = uiState.canManageSurvey,
              hasUnpublishedChanges = uiState.hasUnpublishedChanges,
              onSelectState = actions::setLifecycleState,
            )
          }
          Text(
            text = uiState.lifecycleState.statusDescription(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (uiState.canManageSurvey) {
          if (uiState.isDraft) {
            Button(onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }) {
              Text("Publish survey")
            }
          } else if (uiState.isReadOnly) {
            OutlinedButton(
              onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }
            ) {
              Text("Reopen survey")
            }
          }
        }
      }
    }
  }
}

/** Sentinel for the "None (personal survey)" entry of the organization dropdown. */
private val NoOrganization = Organization(id = "", name = "None (personal survey)")

/**
 * Lets the owner move the survey into one of the organizations they belong to, or keep it personal.
 * An organization the survey already belongs to stays selectable even if the signed-in user isn't a
 * member, so opening the editor never silently changes it.
 */
@Composable
private fun OrganizationSection(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  val selectedId = uiState.details.organizationId
  val current = uiState.organization(selectedId)
  val options =
    remember(uiState.signedInUserOrganizations, current) {
      buildList {
        add(NoOrganization)
        addAll(uiState.signedInUserOrganizations)
        if (current != null && none { it.id == current.id }) add(current)
      }
    }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      "Organization",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      "Optionally run this survey on behalf of an organization you belong to. You stay the " +
        "owner. Managers of the organization can also edit the survey, manage sharing, and export " +
        "data, and its members can find the survey in their list.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.widthIn(max = 380.dp)) {
      DropdownSelector(
        label = "Organization",
        selectedText =
          current?.name ?: selectedId?.let { "Unknown organization ($it)" } ?: NoOrganization.name,
        options = options,
        optionText = { it.name },
        onSelect = { actions.setOrganization(it.id.ifBlank { null }) },
      )
    }
    if (selectedId != null && current == null) {
      Text(
        "This organization no longer exists. Choose another one or make the survey personal.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    uiState.organizationNotice?.let { notice ->
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(
            notice,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
          )
          IconButton(onClick = actions::dismissOrganizationNotice) {
            Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
          }
        }
      }
    }
  }
}

/**
 * The survey's purposes and programs (`SurveyDef.purpose_ids` / `program_ids`), chosen at creation
 * and editable here with the same cards and chips. Changing them doesn't add or remove Forms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposesSection(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  val context = uiState.library
  val packs = context.library.pickablePurposePacks
  val details = uiState.details
  val unavailable =
    details.purposeIds.filter { id ->
      id != SurveyWizardRules.OTHER_PURPOSE_ID && packs.none { it.id == id }
    }
  val offeredPrograms =
    LibraryPrograms.offeredFor(packs.filter { it.id in details.purposeIds }, details.programIds)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Purposes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
      "What this survey's data will be used for. Standard fields of its purposes are suggested " +
        "first when you write questions. Changing purposes doesn't add or remove forms.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (packs.isEmpty()) {
      Text(
        "No purposes are available for this survey.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      PurposePackGrid(
        packs = packs,
        library = context.library,
        organizationName = context.organizationName,
        language = context.language,
        selectedIds = details.purposeIds,
        onToggle = actions::togglePurpose,
        otherPurposeText = details.otherPurpose,
        onOtherPurposeTextChange = actions::updateOtherPurpose,
      )
    }
    if (unavailable.isNotEmpty()) {
      Text(
        "No longer available in this survey's library:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        unavailable.forEach { id ->
          InputChip(
            selected = false,
            onClick = { actions.togglePurpose(id) },
            label = { Text(id) },
            trailingIcon = {
              Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove $id",
                modifier = Modifier.size(18.dp),
              )
            },
          )
        }
      }
    }
    if (offeredPrograms.isNotEmpty()) {
      Text("Programs", style = MaterialTheme.typography.labelLarge)
      ProgramChips(
        programIds = offeredPrograms,
        selectedIds = details.programIds,
        onToggle = actions::toggleProgram,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSelectorSection(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  val details = uiState.details
  val supported = details.supportedLanguages
  val defaultLang = details.defaultLanguage
  var showPicker by remember { mutableStateOf(false) }

  if (showPicker) {
    LanguagePickerDialog(
      selectedCodes = supported.toSet(),
      onAddLanguage = { lang -> actions.addSupportedLanguage(lang.code) },
      onDismiss = { showPicker = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Languages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
      "Choose the languages in which forms and survey questions can be authored and collected. The default language is shown when a specific translation is not available.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Supported languages chips and Add button
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        "Supported languages",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        supported.forEach { code ->
          val lang = IsoLanguages.findByCode(code)
          val displayName = lang?.name ?: code
          val isDefault = code.equals(defaultLang, ignoreCase = true)
          InputChip(
            selected = isDefault,
            onClick = { actions.setDefaultLanguage(code) },
            label = { Text(if (isDefault) "$displayName • Default" else displayName) },
            trailingIcon = {
              if (supported.size > 1) {
                IconButton(
                  onClick = { actions.removeSupportedLanguage(code) },
                  modifier = Modifier.size(18.dp),
                ) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove $displayName",
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            },
          )
        }
        OutlinedButton(onClick = { showPicker = true }, shape = RoundedCornerShape(8.dp)) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add language")
        }
      }
    }

    // Default language selector
    if (supported.isNotEmpty()) {
      val defaultLanguageObject = IsoLanguages.findByCode(defaultLang)
      val defaultLabel = defaultLanguageObject?.name ?: defaultLang.ifEmpty { "None selected" }
      Box(modifier = Modifier.widthIn(max = 380.dp)) {
        DropdownSelector(
          label = "Default language",
          selectedText = defaultLabel,
          options = supported,
          optionText = { code -> IsoLanguages.findByCode(code)?.name ?: code },
          onSelect = { pickedCode -> actions.setDefaultLanguage(pickedCode) },
        )
      }
    }
  }
}

@Composable
internal fun SurveyAreaSection(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  localPlaces: List<SurveyPlaceItem>,
  searchPlaces: PlaceSearch,
  isError: Boolean = uiState.detailsIssues.isNotEmpty(),
) {
  val area = uiState.details.surveyArea
  var showEditor by remember { mutableStateOf(false) }

  if (showEditor) {
    SurveyAreaEditorDialog(
      surveyId = uiState.details.surveyId,
      surveyLocationLabel = uiState.details.title.ifBlank { "Survey" },
      current = area,
      localPlaces = localPlaces,
      searchPlaces = searchPlaces,
      onSave = { newArea -> actions.setSurveyArea(newArea) },
      onDismiss = { showEditor = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          "Survey area *",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          "Geographic coverage and boundary limits for data collection in this survey.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (area != null) {
      OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Small static, non-pan/zoomable map thumbnail preview
          SurveyAreaThumbnail(
            area = area,
            modifier = Modifier.size(width = 160.dp, height = 120.dp),
          )

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = area.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            val summary = remember(area) { areaSummary(area) }
            Text(
              text = summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Source: ${area.sourceLabel}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              OutlinedButton(
                onClick = { showEditor = true },
                shape = RoundedCornerShape(8.dp),
              ) {
                Icon(
                  Icons.Outlined.Edit,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Edit survey area")
              }
            }
          }
        }
      }
    } else {
      if (isError) {
        ValidationErrorBanner(message = "Select or draw a survey area to continue.")
      }
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border =
          if (isError) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
          else CardDefaults.outlinedCardBorder(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(width = 160.dp, height = 100.dp)
                .background(
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Outlined.Map,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(36.dp),
            )
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "No survey area set",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                "Search for a place, draw a boundary on the map, or upload a GeoJSON or KML file.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { showEditor = true },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              Icon(
                Icons.Outlined.Map,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Set survey area")
            }
          }
        }
      }
    }
  }
}

/**
 * Small, static (not pan-able or zoom-able) map thumbnail showing a satellite basemap with the
 * survey area's parts and center point on top.
 */
@Composable
internal fun SurveyAreaThumbnail(area: SurveyArea, modifier: Modifier = Modifier) {
  val boundaryColor = MaterialTheme.colorScheme.primary
  val cameraState =
    remember(area) { MapCameraState(CameraPosition(area.center.toMapLatLng(), area.zoom)) }
  val viewport = cameraState.viewportSize
  val viewportReady = viewport.width > 4.dp && viewport.height > 4.dp
  LaunchedEffect(area, viewportReady) {
    if (!viewportReady) return@LaunchedEffect
    val pts = (area.allVertices.ifEmpty { listOf(area.center) }).map { it.toMapLatLng() }
    val lngSpan = pts.maxOf { it.longitude } - pts.minOf { it.longitude }
    // Bounds spanning > 180° longitude (e.g. across the antimeridian) would center on the wrong
    // side of the world, so anchor on the area's own center and zoom instead.
    val zoom =
      if (lngSpan >= 180.0) {
        area.zoom
      } else {
        val padding = MapInsets(16.dp, 16.dp, 16.dp, 16.dp)
        cameraState.fitBounds(LngLatBounds.of(pts), padding, durationMs = 0).zoom
      }
    cameraState.move(CameraPosition(area.center.toMapLatLng(), zoom))
  }
  val content = remember(area, boundaryColor) { surveyAreaThumbnailContent(area, boundaryColor) }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(8.dp)),
    shape = RoundedCornerShape(8.dp),
    color = Color.Transparent,
    shadowElevation = 1.dp,
  ) {
    GroundMap(
      content = content,
      cameraState = cameraState,
      onEvent = {},
      modifier = Modifier.fillMaxSize(),
      gesturesEnabled = false,
    )
  }
}

/** Vertex dots are only drawn when the simplified area has at most this many vertices. */
internal const val THUMBNAIL_MAX_VERTEX_DOTS = 200

/**
 * The survey area's parts (fill, outline and, for simple areas, vertices) and center over a
 * satellite basemap. Parts are simplified for display, so large uploaded boundaries stay cheap.
 */
internal fun surveyAreaThumbnailContent(area: SurveyArea, boundaryColor: Color): MapContent {
  val src = "survey-area"
  val parts =
    SurveyAreaGeometry.displayParts(area.parts)
      .filter { it.size >= 3 }
      .map { part -> part.map { it.toMapLatLng() } }
  val showVertices = parts.sumOf { it.size } <= THUMBNAIL_MAX_VERTEX_DOTS
  val features = buildList {
    parts.forEachIndexed { i, part -> add(MapFeature("part-$i", Geometry.Polygon(listOf(part)))) }
    if (showVertices) {
      var v = 0
      parts.forEach { part ->
        part.forEach { p ->
          add(MapFeature("vertex-${v++}", Geometry.Point(p), mapOf("kind" to "vertex")))
        }
      }
    }
    add(MapFeature("center", Geometry.Point(area.center.toMapLatLng()), mapOf("kind" to "center")))
  }
  return MapContent(
    basemap = SurveyBasemaps.Satellite,
    sources = listOf(GeoJsonSource(src, features)),
    layers =
      listOf(
        MapLayer.Fill(
          id = "survey-area-fill",
          sourceId = src,
          color = StyleValue.Constant(boundaryColor),
          opacity = StyleValue.Constant(0.28f),
        ),
        MapLayer.Line(
          id = "survey-area-line",
          sourceId = src,
          filter = FeatureFilter.GeometryTypeIs(GeometryType.POLYGON),
          color = StyleValue.Constant(boundaryColor),
          width = StyleValue.Constant(2.5.dp),
        ),
        MapLayer.Circle(
          id = "survey-area-vertices",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "vertex"),
          color = StyleValue.Constant(Color.White),
          radius = StyleValue.Constant(3.5.dp),
          strokeColor = StyleValue.Constant(boundaryColor),
          strokeWidth = 1.5.dp,
        ),
        MapLayer.Circle(
          id = "survey-area-center",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "center"),
          color = StyleValue.Constant(boundaryColor),
          radius = StyleValue.Constant(4.dp),
          strokeColor = StyleValue.Constant(Color.White),
          strokeWidth = 1.dp,
        ),
      ),
  )
}

/**
 * Searchable dialog allowing the user to search the full catalog of ISO 639-3 languages by code or
 * name and add them to the survey.
 */
@Composable
private fun LanguagePickerDialog(
  selectedCodes: Set<String>,
  onAddLanguage: (IsoLanguage) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val searchResults = remember(searchQuery) { IsoLanguages.search(searchQuery, limit = 40) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Outlined.Language,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text("Select language")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text("Search by language name") },
          placeholder = { Text("e.g. Swahili, English, Spanish, Amharic...") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(
          text =
            if (searchQuery.isBlank()) "Popular languages & catalog preview:"
            else "${searchResults.size} matches found:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          if (searchResults.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                "No languages matching “$searchQuery”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(searchResults, key = { it.id }) { lang ->
                val isAlreadySelected = selectedCodes.any {
                  it.equals(lang.code, ignoreCase = true) || it.equals(lang.id, ignoreCase = true)
                }
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(enabled = !isAlreadySelected) {
                        onAddLanguage(lang)
                        onDismiss()
                      }
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = lang.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                  if (isAlreadySelected) {
                    GroundTonalBadge(text = "Added", tone = GroundBadgeTone.PRIMARY)
                  } else {
                    OutlinedButton(
                      onClick = {
                        onAddLanguage(lang)
                        onDismiss()
                      },
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text("Select")
                    }
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

// ---------------------------------------------------------------------------------------------
// Sharing
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SharingPane(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  PaneScaffold(
    title = "Sharing",
    subtitle = "Choose who can open this survey and what data collectors can see.",
  ) {
    SharingContent(
      uiState = uiState,
      actions = actions,
      modifier = Modifier.widthIn(max = 760.dp),
    )
  }
}

/**
 * Scroll-free body of the Sharing pane (people with access, organization managers, general access
 * policy, and peer data visibility), reused inside [SharingPane] and the Survey Creation Wizard.
 */
@Composable
internal fun SharingContent(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  modifier: Modifier = Modifier,
) {
  val sharing = uiState.sharing
  val organization = uiState.organization(uiState.details.organizationId)
  var acceptingEmail by remember { mutableStateOf<String?>(null) }
  sharing.collaborators
    .firstOrNull { it.email == acceptingEmail && it.status == InvitationStatus.PENDING }
    ?.let { invitee ->
      AcceptInviteDialog(
        surveyTitle = uiState.details.title,
        invitee = invitee,
        onAccept = { name, photo -> actions.acceptInvite(invitee.email, name, photo) },
        onDismiss = { acceptingEmail = null },
      )
    }
  Column(
    modifier = modifier,
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
        InviteRow(uiState, actions)
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
                        onResetLink = { actions.resetInviteLink(person.email) },
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
                  onSelect = { actions.setCollaboratorRole(person.email, it) },
                )
              }
              IconButton(onClick = { actions.removeCollaborator(person.email) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${person.displayName}")
              }
            }
          }
        }
        Text(
          "Names and photos come from each person's account. Collect One saves them when they " +
            "accept their invite link, and refreshes them when they sign in.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (organization != null) InheritedManagersCard(organization)

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionLabel("General access")
        uiState.sharingIssues.forEach { issue ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Outlined.Warning,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
            Text(
              issue,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        GeneralAccessPermissionSliders(
          sharing = sharing,
          organization = organization,
          onUpdateSharing = actions::updateSharing,
          surveyId = uiState.surveyId,
          surveyTitle = uiState.details.title,
        )
      }
    }
  }
}

private enum class AccessAudienceStop(val label: String) {
  CONTRIBUTORS_AND_ORGANIZERS("Contributors & organizers"),
  INVITED_PEOPLE("Invited people"),
  ANYONE_IN_ORGANIZATION("Anyone in your organization"),
  ANYONE("Anyone"),
}

private val PermissionLabelWidth = 176.dp

/**
 * Google Groups-style stepped permission sliders for **Who can view data** and **Who can collect
 * data**, plus a **Who can find this survey** selector and share link / QR code card when the
 * survey is open to **Anyone**.
 */
@Composable
private fun GeneralAccessPermissionSliders(
  sharing: SharingSettings,
  organization: Organization?,
  onUpdateSharing: ((SharingSettings) -> SharingSettings) -> Unit,
  surveyId: String = "",
  surveyTitle: String = "",
) {
  val visibleStops =
    remember(organization != null) {
      if (organization != null) {
        AccessAudienceStop.entries
      } else {
        AccessAudienceStop.entries.filterNot { it == AccessAudienceStop.ANYONE_IN_ORGANIZATION }
      }
    }
  val collectStop =
    when (sharing.policy) {
      SharingPolicy.RESTRICTED -> AccessAudienceStop.INVITED_PEOPLE
      SharingPolicy.ORGANIZATION ->
        if (organization != null) AccessAudienceStop.ANYONE_IN_ORGANIZATION
        else AccessAudienceStop.INVITED_PEOPLE
      SharingPolicy.ANYONE_WITH_LINK,
      SharingPolicy.PUBLIC -> AccessAudienceStop.ANYONE
    }
  val viewStop =
    if (sharing.peerDataVisibility == PeerDataVisibility.OWN_ONLY) {
      AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS
    } else {
      collectStop
    }
  val orgDisplayName = organization?.name ?: "your organization"

  val viewHint =
    when (viewStop) {
      AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS ->
        "Data collectors only see their own submissions. Survey organizers can view all " +
          "submissions and contributor identities."
      AccessAudienceStop.INVITED_PEOPLE ->
        "People added above can view all map features, answers, photos, and other uploaded " +
          "media. Only survey organizers can view contributor identities."
      AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
        if (organization != null) {
          "Anyone in ${organization.name} can view all map features, answers, photos, and other " +
            "uploaded media. Only survey organizers can view contributor identities."
        } else {
          "Anyone in your organization can view all map features, answers, photos, and other " +
            "uploaded media. Choose an organization in Survey details."
        }
      AccessAudienceStop.ANYONE ->
        "Anyone can view all map features, answers, photos, and other uploaded media. Only " +
          "survey organizers can view contributor identities. Participants must accept a consent " +
          "notice before collecting data."
    }

  val collectHint =
    when (collectStop) {
      AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS,
      AccessAudienceStop.INVITED_PEOPLE ->
        "Only people added above can open this survey and collect data."
      AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
        if (organization != null) {
          "Members of $orgDisplayName can open this survey and collect data. " +
            "Managers of ${organization.name} can also edit the survey."
        } else {
          "Members of your organization can open this survey and collect data. " +
            "Choose an organization in Survey details."
        }
      AccessAudienceStop.ANYONE -> "Anyone can open this survey and submit data."
    }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // Shared audience column headers
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = PermissionLabelWidth),
      verticalAlignment = Alignment.Bottom,
    ) {
      visibleStops.forEach { stop ->
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = stop.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(6.dp))
          Box(
            modifier =
              Modifier.width(1.dp).height(8.dp).background(MaterialTheme.colorScheme.outlineVariant)
          )
        }
      }
    }

    PermissionRangeSliderRow(
      title = "Who can view data",
      selectedStop = viewStop,
      stops = visibleStops,
      hint = viewHint,
      onSelectStop = { target ->
        onUpdateSharing { current ->
          val openPolicy =
            if (current.policy == SharingPolicy.ANYONE_WITH_LINK) {
              SharingPolicy.ANYONE_WITH_LINK
            } else {
              SharingPolicy.PUBLIC
            }
          when (target) {
            AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS ->
              current.copy(peerDataVisibility = PeerDataVisibility.OWN_ONLY)
            AccessAudienceStop.INVITED_PEOPLE ->
              current.copy(
                policy = SharingPolicy.RESTRICTED,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
            AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
              current.copy(
                policy = SharingPolicy.ORGANIZATION,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
            AccessAudienceStop.ANYONE ->
              current.copy(
                policy = openPolicy,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
          }
        }
      },
    )

    PermissionRangeSliderRow(
      title = "Who can collect data",
      selectedStop = collectStop,
      stops = visibleStops,
      hint = collectHint,
      onSelectStop = { rawTarget ->
        val target =
          if (rawTarget == AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS) {
            AccessAudienceStop.INVITED_PEOPLE
          } else {
            rawTarget
          }
        onUpdateSharing { current ->
          val openPolicy =
            if (current.policy == SharingPolicy.ANYONE_WITH_LINK) {
              SharingPolicy.ANYONE_WITH_LINK
            } else {
              SharingPolicy.PUBLIC
            }
          when (target) {
            AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS,
            AccessAudienceStop.INVITED_PEOPLE -> current.copy(policy = SharingPolicy.RESTRICTED)
            AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
              current.copy(policy = SharingPolicy.ORGANIZATION)
            AccessAudienceStop.ANYONE -> {
              val wasOpenToAnyone = current.policy.isOpenToAnyone
              current.copy(
                policy = openPolicy,
                peerDataVisibility =
                  if (wasOpenToAnyone) current.peerDataVisibility else PeerDataVisibility.OWN_ONLY,
              )
            }
          }
        }
      },
    )

    if (sharing.policy.isOpenToAnyone) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      val directoryOptions = listOf(SharingPolicy.PUBLIC, SharingPolicy.ANYONE_WITH_LINK)
      val policyOptionLabel: (SharingPolicy) -> String = { policy ->
        if (policy == SharingPolicy.ANYONE_WITH_LINK) {
          "Only people with a link or QR code (unlisted)"
        } else {
          "Anyone in the survey directory"
        }
      }
      DropdownSelector(
        label = "Who can find this survey",
        selectedText = policyOptionLabel(sharing.policy),
        options = directoryOptions,
        optionText = policyOptionLabel,
        onSelect = { selectedPolicy -> onUpdateSharing { it.copy(policy = selectedPolicy) } },
      )
      Text(
        text =
          if (sharing.policy == SharingPolicy.ANYONE_WITH_LINK) {
            "Unlisted: only people who have the direct link or scan the QR code below can find " +
              "and open this survey."
          } else {
            "Listed: anyone browsing the public survey directory can discover and open this " +
              "survey, or use the direct link and QR code below."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      SurveyShareLinkAndQrCard(
        surveyId = surveyId,
        surveyTitle = surveyTitle,
        isUnlisted = sharing.policy == SharingPolicy.ANYONE_WITH_LINK,
      )
    }
  }
}

@Composable
internal fun SurveyShareLinkAndQrCard(
  surveyId: String,
  surveyTitle: String,
  isUnlisted: Boolean,
  modifier: Modifier = Modifier,
) {
  val shareUrl = remember(surveyId) { InviteLinks.surveyUrl(surveyId) }
  val clipboard = LocalClipboardManager.current
  var copied by remember(shareUrl) { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }

  OutlinedCard(
    modifier = modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SurveyQrCodeCanvas(
        data = shareUrl,
        modifier =
          Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { showQrDialog = true },
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Share link & QR code",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          GroundTonalBadge(
            text = if (isUnlisted) "Unlisted" else "Public directory",
            tone = if (isUnlisted) GroundBadgeTone.NEUTRAL else GroundBadgeTone.PRIMARY,
          )
        }
        Text(
          text = shareUrl,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            clipboard.setText(AnnotatedString(shareUrl))
            copied = true
          }
        ) {
          Text(if (copied) "Copied" else "Copy link")
        }
        OutlinedButton(onClick = { showQrDialog = true }) { Text("Show QR code") }
      }
    }
  }

  if (showQrDialog) {
    AlertDialog(
      onDismissRequest = { showQrDialog = false },
      title = {
        Text(
          text =
            if (surveyTitle.isNotBlank()) "Share “$surveyTitle”" else "Survey share link & QR code"
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SurveyQrCodeCanvas(
            data = shareUrl,
            modifier =
              Modifier.size(216.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
          )
          Text(
            text =
              "Data collectors can scan this QR code with their phone camera or the Ground app " +
                "to open the survey directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = shareUrl,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(shareUrl))
            copied = true
          }
        ) {
          Text(if (copied) "Copied" else "Copy link")
        }
      },
      dismissButton = { TextButton(onClick = { showQrDialog = false }) { Text("Close") } },
    )
  }
}

@Composable
private fun SurveyQrCodeCanvas(data: String, modifier: Modifier = Modifier) {
  val matrixSize = 21
  Canvas(modifier = modifier.background(Color.White)) {
    val quietZone = 2
    val totalModules = matrixSize + quietZone * 2
    val cellSize = size.minDimension / totalModules.toFloat()
    val seed = data.fold(5381) { acc, ch -> ((acc shl 5) + acc) xor ch.code }

    fun isFinderModule(r: Int, c: Int, topR: Int, topC: Int): Boolean? {
      val dr = r - topR
      val dc = c - topC
      if (dr !in -1..7 || dc !in -1..7) return null
      if (dr == -1 || dr == 7 || dc == -1 || dc == 7) return false
      if (dr == 0 || dr == 6 || dc == 0 || dc == 6) return true
      if (dr in 2..4 && dc in 2..4) return true
      return false
    }

    for (r in 0 until matrixSize) {
      for (c in 0 until matrixSize) {
        val finder =
          isFinderModule(r, c, 0, 0)
            ?: isFinderModule(r, c, 0, matrixSize - 7)
            ?: isFinderModule(r, c, matrixSize - 7, 0)
        val isDark =
          when {
            finder != null -> finder
            r == 6 -> c % 2 == 0
            c == 6 -> r % 2 == 0
            else -> {
              val charCode =
                if (data.isNotEmpty()) data[(r * matrixSize + c) % data.length].code else 0
              val mix = (seed xor (r * 131 + c * 313) xor (charCode * 17))
              (mix and 1) == 0
            }
          }
        if (isDark) {
          drawRect(
            color = Color(0xFF111827),
            topLeft = Offset((c + quietZone) * cellSize, (r + quietZone) * cellSize),
            size = Size(cellSize, cellSize),
          )
        }
      }
    }
  }
}

@Composable
private fun PermissionRangeSliderRow(
  title: String,
  selectedStop: AccessAudienceStop,
  stops: List<AccessAudienceStop> = AccessAudienceStop.entries,
  hint: String,
  onSelectStop: (AccessAudienceStop) -> Unit,
) {
  val selectedIndex = stops.indexOf(selectedStop).let { if (it >= 0) it else 0 }
  val activeColor = MaterialTheme.colorScheme.primary
  val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
  val outlineColor = MaterialTheme.colorScheme.outline
  val currentOnSelectStop by androidx.compose.runtime.rememberUpdatedState(onSelectStop)
  var trackWidthPx by remember { mutableStateOf(0) }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(PermissionLabelWidth).padding(end = 12.dp),
      )
      Row(
        modifier =
          Modifier.weight(1f)
            .height(36.dp)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(stops.size) {
              var dragX = 0f
              detectHorizontalDragGestures(
                onDragStart = { offset -> dragX = offset.x },
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  dragX += dragAmount
                  if (trackWidthPx > 0) {
                    val colWidth = trackWidthPx.toFloat() / stops.size
                    val index = ((dragX / colWidth).toInt()).coerceIn(0, stops.lastIndex)
                    currentOnSelectStop(stops[index])
                  }
                },
              )
            },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        stops.forEachIndexed { index, stop ->
          val isFilled = index <= selectedIndex
          val isSelected = index == selectedIndex
          val leftFilled = index > 0 && index <= selectedIndex
          val rightFilled = index < selectedIndex
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clickable(
                  role = Role.RadioButton,
                  onClick = { currentOnSelectStop(stop) },
                )
                .semantics(mergeDescendants = true) {
                  contentDescription = "$title: ${stop.label}"
                },
            contentAlignment = Alignment.Center,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(if (leftFilled) 8.dp else 4.dp)
                    .background(
                      color =
                        if (index == 0) Color.Transparent
                        else if (leftFilled) activeColor else inactiveColor,
                      shape =
                        if (index == 1 && leftFilled) {
                          RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        } else {
                          RoundedCornerShape(0.dp)
                        },
                    )
              )
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(if (rightFilled) 8.dp else 4.dp)
                    .background(
                      color =
                        if (index == stops.lastIndex) Color.Transparent
                        else if (rightFilled) activeColor else inactiveColor,
                      shape =
                        if (index == stops.lastIndex - 1 && !rightFilled) {
                          RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                        } else {
                          RoundedCornerShape(0.dp)
                        },
                    )
              )
            }
            Box(
              modifier =
                Modifier.size(if (isSelected) 20.dp else 14.dp)
                  .clip(CircleShape)
                  .background(if (isFilled) activeColor else MaterialTheme.colorScheme.surface)
                  .border(
                    width = 2.dp,
                    color = if (isFilled) activeColor else outlineColor,
                    shape = CircleShape,
                  )
            )
          }
        }
      }
    }
    Text(
      text = hint,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = PermissionLabelWidth),
    )
  }
}

@Composable
private fun InviteRow(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  var email by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(CollaboratorRole.DATA_COLLECTOR) }
  var error by remember { mutableStateOf<String?>(null) }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
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
        error = actions.inviteCollaborator(email, role)
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
private fun CheckboxOption(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Spacer(Modifier.width(12.dp))
    Column {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Read-only list of the people who can manage this survey because they manage [organization]. They
 * aren't on the survey's own access list, so they can't be removed here.
 */
@Composable
private fun InheritedManagersCard(organization: Organization) {
  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SectionLabel("Organization managers")
      Text(
        "Inherited from ${organization.name}. Managers can edit this survey, manage sharing, and " +
          "export data. Change who manages the organization from its Members page.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (organization.managers.isEmpty()) {
        Text(
          "No managers yet.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      organization.managers.forEach { manager ->
        key(manager.email) {
          PersonRow(
            displayName = manager.displayName,
            email = manager.email,
            photoUrl = manager.profile?.photoUrl,
            detail = "Manager of ${organization.name}",
          ) {
            Text(
              CollaboratorRole.SURVEY_ORGANIZER.label,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
          }
        }
      }
    }
  }
}
