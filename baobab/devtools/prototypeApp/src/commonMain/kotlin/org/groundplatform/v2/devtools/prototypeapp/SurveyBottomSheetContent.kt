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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily


/**
 * Unified Persistent Bottom Sheet content for [SurveyMapView] (Option A):
 * 1. When a [GeospatialEntityItem] is selected (via map tap or list selection), renders
 * ```
 *    [EntityBottomSheetCard] with a `"All map features"` back pill, `simplestyle-spec` marker, baseline
 *    attributes, form launchers, and `1:N` submissions.
 * ```
 * 2. When a standalone [SubmissionPreviewItem] is selected from the searchable list, renders
 * ```
 *    [SubmissionFullDetailsCard] with a back button returning to the searchable list.
 * ```
 * 3. When no specific item is selected, renders [SurveyListView] — peeking at the bottom of the map
 * ```
 *    with a Search bar and category filter chips (`All`, `Places`, `Map features`) and expanding
 *    into the full grouped list.
 * ```
 *
 * When [isSidePanel] is `true` (the web dashboard's left-hand panel), the sheet expand/collapse
 * toggles and mobile-only field actions (data collection launchers, `Navigate`) are hidden.
 */
@Composable
internal fun SurveyPersistentBottomSheetContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val isDark = state.isDarkTheme
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  when {
    selectedEntity != null -> {
      EntityBottomSheetCard(
        entity = selectedEntity,
        state = state,
        modifier = modifier,
        isSidePanel = isSidePanel,
      )
    }
    selectedSubmission != null -> {
      Column(
        modifier =
          modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to Searchable List",
          onBack = { state.returnToBottomSheetList() },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      }
    }
    else -> {
      BottomSheetSearchableListContent(state = state, modifier = modifier, isSidePanel = isSidePanel)
    }
  }
}

/**
 * Shared Entity Summary Header used in both [EntityBottomSheetCard] and compact list cards in
 * [BottomSheetSearchableListContent].
 */
@Composable
internal fun EntitySummaryHeader(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  compact: Boolean = false,
  trailingContent: @Composable RowScope.() -> Unit = {},
) {
  val areaFormatted =
    if (state.unitSystem == MeasurementUnitSystem.METRIC) {
      "${entity.areaHectares} ha"
    } else {
      val acres = ((entity.areaHectares * 2.47105) * 100.0).roundToInt() / 100.0
      "$acres acres"
    }
  val perimeterFormatted =
    if (state.unitSystem == MeasurementUnitSystem.METRIC) {
      "${entity.perimeterMeters} m"
    } else {
      val feet = (entity.perimeterMeters * 3.28084).roundToInt()
      "$feet ft"
    }
  val badgeSize = if (compact) 22.dp else 28.dp

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(badgeSize).clip(CircleShape).background(Color(entity.markerColorHex)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = entity.markerSymbol,
          style =
            if (compact) {
              MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
            } else {
              MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
            },
          color = Color.White,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entity.label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            "${entity.datasetName} • ${entity.geometryTypeLabel} ($areaFormatted, $perimeterFormatted)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      content = trailingContent,
    )
  }
}

/**
 * Shared row of `simplestyle-spec` workflow status, `1:N` submission count, `GeoID`, sync status,
 * live GNSS wayfinding badge, and quick action chips (`Navigate`, `QR Code`, `Share PDF`).
 */
@Composable
internal fun EntityMetadataAndActionsRow(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  showShareAndQrActions: Boolean,
  showNavigateAction: Boolean = true,
) {
  val entityWayfindingBadge = state.formattedWayfindingBadgeForEntity(entity.id)
  val isNavigatingEntity = state.isNavigatingToEntity(entity.id)

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Data-Driven Marker Symbol & Status Badge (○ Pending -> ◐ In progress -> ✓ Completed)
    GroundTonalBadge(
      text = entity.mapStatusSummaryBadge,
      tone =
        when (entity.markerSymbol) {
          "✓" -> GroundBadgeTone.PRIMARY
          "◐" -> GroundBadgeTone.TERTIARY
          else -> GroundBadgeTone.WARNING
        },
    )

    // 1:N Submission Count Badge
    GroundTonalBadge(
      text =
        "${entity.submissionCount} ${if (entity.submissionCount == 1) "submission" else "submissions"}",
      tone = GroundBadgeTone.SECONDARY,
    )

    // GeoID Badge
    GroundTonalBadge(
      text = "GeoID: ${entity.geoId}",
      tone = GroundBadgeTone.SECONDARY,
      monospace = true,
    )

    // Sync Status Indicator Badge
    SyncStatusIndicatorBadge(
      syncStatus = entity.syncStatus,
      onClick = { state.cycleEntitySyncStatus(entity.id) },
    )

    // Live Distance & Compass Bearing Badge from User GPS
    if (showNavigateAction && entityWayfindingBadge.isNotEmpty()) {
      GroundTonalBadge(
        text = "➤ $entityWayfindingBadge",
        tone = GroundBadgeTone.TERTIARY,
        monospace = true,
      )
    }

    // Straight-Line Navigation Toggle Button for Geospatial Entity
    if (showNavigateAction) {
      FilterChip(
        selected = isNavigatingEntity,
        onClick = { state.toggleNavigationToEntity(entity.id) },
        label = {
          Text(
            text = if (isNavigatingEntity) "Stop Nav" else "Navigate",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Straight-line navigate to entity",
            modifier = Modifier.size(13.dp),
          )
        },
      )
    }

    if (showShareAndQrActions) {
      // QR Code Link
      AssistChip(
        onClick = { state.openEntityQrCode(entity.id) },
        label = {
          Text(
            text = "QR Code",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "${entity.singularTypeLabel} QR Code",
            modifier = Modifier.size(13.dp),
          )
        },
      )

      // Share PDF Link
      AssistChip(
        onClick = { state.shareEntityPdf(entity.id) },
        label = {
          Text(
            text = "Share PDF",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share ${entity.singularTypeLabel} PDF",
            modifier = Modifier.size(13.dp),
          )
        },
      )
    }
  }
}

/**
 * Bottom Sheet detail card shown when a survey location is selected (from the map or the bottom
 * sheet list):
 * - Shows main location metadata via [EntitySummaryHeader] and [EntityMetadataAndActionsRow].
 * - Shows a `"All map features"` back pill to return directly to the searchable list in the bottom sheet.
 * - Shows available form collection buttons and the unified `1:N` list of submissions grouped by
 * form title via [FormGroupedSubmissionsSection].
 *
 * When [isSidePanel] is `true`, the sheet expansion toggle and mobile-only field actions (`Navigate`,
 * data collection launchers) are hidden.
 */
@Composable
internal fun EntityBottomSheetCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val selectedSubmission = state.selectedSubmission
  val isDark = state.isDarkTheme
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Shared Header + Back to All Map Features pill + Expand/Collapse + Close
    EntitySummaryHeader(
      entity = entity,
      state = state,
      compact = false,
      trailingContent = {
        Surface(
          onClick = { state.returnToBottomSheetList() },
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to all map features list",
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "All map features",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        if (!isSidePanel) {
          IconButton(
            onClick = { state.toggleEntityBottomSheetExpanded() },
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              imageVector =
                if (state.isEntityBottomSheetExpanded) {
                  Icons.Default.KeyboardArrowDown
                } else {
                  Icons.Default.KeyboardArrowUp
                },
              contentDescription = "Toggle Sheet Expansion",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        IconButton(onClick = { state.selectEntity(null) }, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Location Sheet",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      },
    )

    // Shared Metadata Badges & Share/Navigate Actions Row
    EntityMetadataAndActionsRow(
      entity = entity,
      state = state,
      showShareAndQrActions = true,
      showNavigateAction = !isSidePanel,
    )

    // Organizer-defined Action Buttons for this dataset type (`form.targetDatasetId ==
    // entity.datasetId`). Data collection happens on mobile, so the side panel omits them.
    val entityForms = if (isSidePanel) emptyList() else state.formsForEntity(entity)
    if (entityForms.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "DATA COLLECTION FOR THIS ${entity.singularTypeLabel.uppercase()}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          entityForms.forEach { form ->
            val isEnabled = state.isFormButtonEnabled(entity, form)
            Button(
              onClick = { state.launchFormForEntity(entity.id, form.id) },
              enabled = isEnabled,
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(
                imageVector =
                  if (isEnabled) Icons.Default.Description else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text =
                  if (isEnabled) {
                    form.ctaLabel
                  } else {
                    "${form.ctaLabel} (Completed)"
                  },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
        val triggeredBanner = state.activeSurveyNotice
        if (triggeredBanner != null) {
          Text(
            text = triggeredBanner,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Body inside Bottom Sheet: Baseline Properties + Submission Data / History
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Baseline Attributes (`EntityRecord.properties`)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        entity.properties.forEach { (key, value) ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
              Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // --- UNIFIED 1:N SUBMISSION DISPLAY LOGIC ---
      if (selectedSubmission != null && selectedSubmission.entityId == entity.id) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to all ${entity.submissions.size} submissions",
          onBack = { state.selectSubmissionDetail(null) },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      } else if (entity.submissions.isNotEmpty()) {
        FormGroupedSubmissionsSection(
          groups = state.groupedSubmissionsForEntity(entity),
          state = state,
          showTargetEntityLabel = false,
          showFormActionSubtitle = false,
          onSelectSubmission = { state.selectSubmissionDetail(it.id) },
        )
      } else {
        Text(
          text =
            "No submissions recorded yet for this ${entity.singularTypeLabel.lowercase()} (Marker: ${entity.markerSymbol} ${entity.workflowStatus}). Launch a form above to advance its marker via save_to (○ → ◐ → ✓).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Shared component that renders chronological submissions grouped by form (`FormSubmissionGroup`),
 * used in both [EntityBottomSheetCard] (for a single entity's `1:N` submissions) and
 * [BottomSheetSearchableListContent] (for survey-wide submissions).
 */
@Composable
internal fun FormGroupedSubmissionsSection(
  groups: List<FormSubmissionsGroup>,
  state: PrototypeAppState,
  showTargetEntityLabel: Boolean,
  showFormActionSubtitle: Boolean,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { group ->
      val form = group.form
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Form Title Group Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = form.title,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              if (showFormActionSubtitle) {
                Text(
                  text = "Action: \"${form.ctaLabel}\" • ${form.version}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            GroundTonalBadge(
              text = "${group.submissions.size} submitted",
              tone = GroundBadgeTone.PRIMARY,
            )
          }

          group.submissions.forEachIndexed { index, sub ->
            OutlinedCard(
              onClick = { onSelectSubmission(sub) },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              colors =
                CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  if (showTargetEntityLabel) {
                    val targetDisplayLabel =
                      if (sub.hasAttachedEntity) {
                        "${sub.targetTypeLabel}: ${sub.entityLabel}"
                      } else if (sub.coordinatesLabel.isNotBlank()) {
                        "${sub.targetTypeLabel} • No attached map feature (${sub.coordinatesLabel.substringBefore(" (")})"
                      } else {
                        "${sub.targetTypeLabel} • No attached map feature"
                      }
                    Text(
                      text = targetDisplayLabel,
                      style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Person,
                      contentDescription = null,
                      tint =
                        if (showTargetEntityLabel) {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                      modifier = Modifier.size(13.dp),
                    )
                    Text(
                      text = sub.collectorName,
                      style =
                        if (showTargetEntityLabel) {
                          MaterialTheme.typography.labelSmall
                        } else {
                          MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        },
                      color =
                        if (showTargetEntityLabel) {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (index == 0 && !showTargetEntityLabel) {
                      GroundTonalBadge(text = "LATEST", tone = GroundBadgeTone.SECONDARY)
                    }
                    SyncStatusIndicatorBadge(
                      syncStatus = sub.syncStatus,
                      onClick = { state.cycleSubmissionSyncStatus(sub.id) },
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Schedule,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(11.dp),
                    )
                    Text(
                      text = "${sub.timestamp} • ${sub.formVersion}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                IconButton(onClick = { onSelectSubmission(sub) }, modifier = Modifier.size(32.dp)) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Submission details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Searchable List content embedded inside the unified persistent bottom sheet (
 * [SurveyPersistentBottomSheetContent]) when no specific entity or submission is selected:
 * - Peeks at the bottom of the map with the Search bar (`"Search..."`)
 *   and an Expand/Collapse sheet button.
 * - Expands to display **Map layers** (grouped by spatial layer with un-nested entity records,
 *   reusing [EntitySummaryHeader] and [EntityMetadataAndActionsRow]), **Data tables** (tabular datasets),
 *   and **Places** (geographic places, landmarks, and coordinates in the survey region).
 *
 * When [isSidePanel] is `true`, the Expand/Collapse sheet button is hidden.
 */
@Composable
internal fun BottomSheetSearchableListContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val allPlaces = state.places
  val apiPlaces = state.mapboxPlacesApiResults
  val isAirplaneMode = state.isAirplaneMode
  val allEntities = state.entities
  val listTab = state.listFilterTab
  val listQuery = state.listSearchQuery
  val mapLayers = state.mapLayers

  val matchedPlaces =
    remember(allPlaces, apiPlaces, listTab, listQuery, isAirplaneMode) { state.filteredListPlaces }
  val matchedEntities = remember(allEntities, listTab, listQuery) { state.filteredListEntities }
  val groupedEntities = remember(matchedEntities, mapLayers) { state.groupedFilteredListEntities }

  Column(modifier = modifier) {
    // Sticky Peek Header: Search Bar + Expand/Collapse Toggle
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = state.listSearchQuery,
          onValueChange = {
            state.updateListSearchQuery(it)
            if (it.isNotEmpty() && !state.isEntityBottomSheetExpanded) {
              state.updateEntityBottomSheetExpanded(true)
            }
          },
          modifier = Modifier.weight(1f),
          singleLine = true,
          placeholder = {
            Text(
              text = "Search...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (state.listSearchQuery.isNotEmpty()) {
              IconButton(
                onClick = { state.clearListSearchQuery() },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear Search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
          shape = MaterialTheme.shapes.extraLarge,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = Color.Transparent,
            ),
        )

        if (!isSidePanel) {
          IconButton(
            onClick = {
              if (state.isEntityBottomSheetExpanded) {
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
              } else {
                state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
              }
            },
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector =
                if (state.isEntityBottomSheetExpanded) {
                  Icons.Default.KeyboardArrowDown
                } else {
                  Icons.Default.KeyboardArrowUp
                },
              contentDescription =
                if (state.isEntityBottomSheetExpanded) {
                  "Collapse list sheet to map"
                } else {
                  "Expand searchable list sheet"
                },
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }



      // Offline / Airplane mode banner in the bottom sheet explaining that search is only in local
      // map features and that Places search is not available offline.
      if (isAirplaneMode) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Device offline",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Device offline • Searching local ${state.activeEntitiesCountNoun} only",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = "Search is only in local ${state.activeEntitiesCountNoun}. Places search is not available offline.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )
              }
            }

            Spacer(modifier = Modifier.width(6.dp))

            AssistChip(
              onClick = { state.updateAirplaneMode(false) },
              label = {
                Text(
                  text = "Turn Off",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Searchable List of Map features (grouped by dataset) and Places
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. MAP LAYERS SECTION
      if (groupedEntities.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "MAP LAYERS",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        groupedEntities.forEach { group ->
          val layer = group.layer
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Layer Group Header Banner
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (layer != null) {
                  Box(
                    modifier =
                      Modifier.size(14.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(layer.colorHex).copy(alpha = 0.25f))
                        .border(2.dp, Color(layer.colorHex), MaterialTheme.shapes.extraSmall)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                }
                Text(
                  text = layer?.label ?: group.datasetName,
                  style =
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }

              GroundTonalBadge(
                text = "${group.entities.size}",
                tone = GroundBadgeTone.PRIMARY,
              )
            }

            // Map features belonging to this dataset group elevated to direct list items (no outer card nesting)
            val maxRenderedFeatures = 40
            val displayedFeatures =
              if (group.entities.size > maxRenderedFeatures) {
                group.entities.take(maxRenderedFeatures)
              } else {
                group.entities
              }
            displayedFeatures.forEach { entity ->
              OutlinedCard(
                onClick = { state.selectEntityFromList(entity.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors =
                  CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  EntitySummaryHeader(
                    entity = entity,
                    state = state,
                    compact = true,
                    trailingContent = {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                      ) {
                        Text(
                          text = "Inspect",
                          style =
                            MaterialTheme.typography.labelSmall.copy(
                              fontWeight = FontWeight.Bold
                            ),
                          color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(12.dp),
                        )
                      }
                    },
                  )

                  EntityMetadataAndActionsRow(
                    entity = entity,
                    state = state,
                    showShareAndQrActions = false,
                  )
                }
              }
            }
            if (group.entities.size > maxRenderedFeatures) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text =
                    "Showing first $maxRenderedFeatures of ${group.entities.size} ${layer?.pluralNoun ?: "features"} in ${group.datasetName}. Use the search bar above to filter all ${group.entities.size} ${layer?.pluralNoun ?: "features"}.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
              }
            }
          }
        }
      }

      // 2. DATA TABLES SECTION — only displayed when tabular datasets exist and have matching entries
      // (Currently no non-spatial tabular datasets configured in the active survey)

      // 3. PLACES SECTION — only available when online (!isAirplaneMode) and entries match
      if (!isAirplaneMode && matchedPlaces.isNotEmpty()) {
        if (groupedEntities.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "PLACES",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          matchedPlaces.forEach { place ->
            val isSelectedPlace = state.selectedPlaceId == place.id
            val isNavigatingPlace = state.isNavigatingToPlace(place.id)
            val placeWayfindingBadge = state.formattedWayfindingBadgeForPlace(place.id)

            OutlinedCard(
              onClick = { state.selectPlace(place.id) },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              border =
                if (isSelectedPlace) {
                  BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                  CardDefaults.outlinedCardBorder()
                },
              colors =
                CardDefaults.outlinedCardColors(
                  containerColor =
                    if (isSelectedPlace) {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                      MaterialTheme.colorScheme.surface
                    }
                ),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      modifier = Modifier.size(24.dp),
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.Explore,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = place.name,
                        style =
                          MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${place.categoryLabel} • ${place.regionSubtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  FilledTonalIconButton(
                    onClick = { state.selectPlace(place.id) },
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Explore,
                      contentDescription = "Fly to place on map",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  GroundTonalBadge(text = place.categoryLabel, tone = GroundBadgeTone.SECONDARY)
                  GroundTonalBadge(
                    text = place.coordinatesLabel,
                    tone = GroundBadgeTone.SECONDARY,
                    monospace = true,
                  )
                  if (placeWayfindingBadge.isNotEmpty()) {
                    GroundTonalBadge(
                      text = "➤ $placeWayfindingBadge",
                      tone = GroundBadgeTone.TERTIARY,
                      monospace = true,
                    )
                  }
                  FilterChip(
                    selected = isNavigatingPlace,
                    onClick = { state.toggleNavigationToPlace(place.id) },
                    label = {
                      Text(
                        text = if (isNavigatingPlace) "Stop Nav" else "Navigate",
                        style =
                          MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Straight-line navigate to place",
                        modifier = Modifier.size(13.dp),
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }

      if (matchedPlaces.isEmpty() && matchedEntities.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text =
              when {
                isAirplaneMode ->
                  "No local map layers match \"${state.listSearchQuery}\". Places search is not available offline."
                else ->
                  "No map layers or places match \"${state.listSearchQuery}\"."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Bottom-centered Floating Action Button inside the expanded sheet as well
    if (state.isEntityBottomSheetExpanded) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) { DataCollectionFormsFab(state = state) }
    }
  }
}

/** Full Submission Details Card shown when a submission in a 1:N list or List view is clicked. */
@Composable
internal fun SubmissionFullDetailsCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  isDark: Boolean,
  textColor: Color,
  backLabel: String,
  onBack: () -> Unit,
  onSharePdf: () -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Header: Simple back arrow navigation + Form Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = { onBack() },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = backLabel,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = submission.formTitle,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
      }

      // Actions & Sync Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SyncStatusIndicatorBadge(
            syncStatus = submission.syncStatus,
            onClick = { state.cycleSubmissionSyncStatus(submission.id) },
          )

          AssistChip(
            onClick = { onSharePdf() },
            label = {
              Text(
                text = "Share PDF",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Submission PDF",
                modifier = Modifier.size(12.dp),
              )
            },
          )
        }

        Text(
          text = "Schema: ${submission.formVersion}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val detailTargetLabel =
          if (submission.hasAttachedEntity) {
            "${submission.targetTypeLabel}: ${submission.entityLabel}"
          } else if (submission.coordinatesLabel.isNotBlank()) {
            "${submission.targetTypeLabel} • No attached map feature (${submission.coordinatesLabel})"
          } else {
            "${submission.targetTypeLabel} • No attached map feature"
          }
        Text(
          text = detailTargetLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Data Collector: ${submission.collectorName} (${submission.collectorEmail})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Collected: ${submission.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      submission.fields.forEach { field ->
        val fieldWayfindingBadge =
          state.formattedWayfindingBadgeForSubmissionField(submission.id, field)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = field.questionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (fieldWayfindingBadge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                GroundTonalBadge(
                  text = "➤ $fieldWayfindingBadge",
                  tone = GroundBadgeTone.WARNING,
                  monospace = true,
                )
              }
            }
            Text(
              text = field.answerValue,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

/**
 * Material Design 3 status badge indicating whether a map feature (`GeospatialEntityItem`) or form
 * submission (`SubmissionPreviewItem`) is [SyncStatus.UPLOADING], [SyncStatus.SYNCED], or
 * [SyncStatus.FAILED].
 */
@Composable
internal fun SyncStatusIndicatorBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier,
  countSuffix: String? = null,
  isHighlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val (icon, containerColor, contentColor, borderColor) =
    when (syncStatus) {
      SyncStatus.UPLOADING ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudUpload,
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          borderColor =
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.SYNCED ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudDone,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor =
            MaterialTheme.colorScheme.primary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.FAILED ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudOff,
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          borderColor =
            MaterialTheme.colorScheme.error.copy(alpha = if (isHighlighted) 0.95f else 0.50f),
        )
    }

  val clickModifier =
    if (onClick != null) {
      Modifier.clickable(onClick = onClick)
    } else {
      Modifier
    }

  Surface(
    modifier = modifier.then(clickModifier),
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    contentColor = contentColor,
    border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = syncStatus.description,
        tint = contentColor,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text =
          if (countSuffix != null) {
            "${syncStatus.label} $countSuffix"
          } else {
            syncStatus.label
          },
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
      )
    }
  }
}

private data class SyncStatusVisualSpec(
  val icon: ImageVector,
  val containerColor: Color,
  val contentColor: Color,
  val borderColor: Color,
)
