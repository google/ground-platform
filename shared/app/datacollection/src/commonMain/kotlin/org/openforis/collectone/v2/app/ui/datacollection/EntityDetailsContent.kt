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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CollapseContent
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.regularDisplayProperties
import org.openforis.collectone.v2.app.domain.model.whispAnalysis
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_back_to_all_map_features
import org.openforis.collectone.v2.app.resources.app_back_to_details
import org.openforis.collectone.v2.app.resources.app_back_to_submissions
import org.openforis.collectone.v2.app.resources.app_close_details
import org.openforis.collectone.v2.app.resources.app_collapse_details
import org.openforis.collectone.v2.app.resources.app_collect_data
import org.openforis.collectone.v2.app.resources.app_details_geo_id
import org.openforis.collectone.v2.app.resources.app_details_geometry
import org.openforis.collectone.v2.app.resources.app_details_heading
import org.openforis.collectone.v2.app.resources.app_details_layer
import org.openforis.collectone.v2.app.resources.app_details_properties
import org.openforis.collectone.v2.app.resources.app_download_pdf
import org.openforis.collectone.v2.app.resources.app_form_action_completed
import org.openforis.collectone.v2.app.resources.app_more_actions
import org.openforis.collectone.v2.app.resources.app_no_local_submissions_for_entity
import org.openforis.collectone.v2.app.resources.app_no_submissions_for_entity
import org.openforis.collectone.v2.app.resources.app_no_submissions_yet
import org.openforis.collectone.v2.app.resources.app_offline_showing_local_submissions
import org.openforis.collectone.v2.app.resources.app_pending_uploads
import org.openforis.collectone.v2.app.resources.app_qr_code
import org.openforis.collectone.v2.app.resources.app_share
import org.openforis.collectone.v2.app.resources.app_share_pdf
import org.openforis.collectone.v2.app.resources.app_show_in_table
import org.openforis.collectone.v2.app.resources.app_stop_navigation_short
import org.openforis.collectone.v2.app.resources.app_submissions_count
import org.openforis.collectone.v2.app.resources.app_submissions_stored_offline
import org.openforis.collectone.v2.app.resources.app_view_submission_history
import org.openforis.collectone.v2.app.ui.common.GeoIdText
import org.openforis.collectone.v2.app.ui.common.displayLabel
import org.openforis.collectone.v2.app.ui.common.horizontalScrollWithMouseDrag
import org.openforis.collectone.v2.app.ui.map.LayerIcons
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.app.ui.text.entityGeometrySummaryText
import org.openforis.collectone.v2.app.ui.text.typeText
import org.openforis.collectone.v2.core.forms.ui.ChoiceColorPillsRow
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.map.GeometryType

private val GeometryType?.icon: ImageVector
  get() =
    when (this) {
      GeometryType.POINT -> Icons.Outlined.Place
      GeometryType.LINE_STRING -> Icons.Outlined.Timeline
      GeometryType.POLYGON -> Icons.Outlined.Pentagon
      null -> Icons.Outlined.TableRows
    }

/** Icon indicating whether [entity] has a point, line, polygon, or no geometry. */
@Composable
fun EntityGeometryIcon(
  entity: GeospatialEntityItem,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val type = entity.geometryType
  Icon(
    imageVector = type.icon,
    contentDescription = type.displayLabel(),
    tint = tint,
    modifier = modifier.size(size),
  )
}

/** Readable content color (symbol and text) on top of a marker-color fill. */
private fun contentColorOnMarker(markerColor: Color): Color =
  if (markerColor.luminance() > 0.6f) Color(0xFF1F1F1F) else Color.White

/**
 * The entity's layer marker: a bare icon tinted with its map [layer]'s color — the layer's pin icon
 * for points, or the entity's geometry icon (polygon, line, or point) for lines, polygons, and
 * layers without an icon. Workflow status isn't shown here; see [EntityStatusBalloon].
 */
@Composable
internal fun EntityLayerMarker(
  entity: GeospatialEntityItem,
  layer: MapLayerItem?,
  modifier: Modifier = Modifier,
  size: Dp = 24.dp,
) {
  // Pin icons are only for points; lines and polygons show their geometry icon.
  val icon =
    LayerIcons.forName(
      layer
        ?.takeIf { it.id == entity.layerId && entity.geometryType == GeometryType.POINT }
        ?.iconName
    )
  Icon(
    imageVector = icon?.vector ?: entity.geometryType.icon,
    contentDescription = icon?.label ?: entity.geometryType.displayLabel(),
    tint = Color(entity.mapColorHex(layer)),
    modifier = modifier.size(size),
  )
}

/**
 * Status chip for an entity's details: its `marker-symbol` and status text on a fill of its
 * `marker-color`. Draws nothing when the entity has no workflow status.
 * - [compact]: a smaller chip (tighter padding, `labelSmall` text) for dense web surfaces such as
 *   the details card body and the data table. Mobile callers keep the default size.
 */
@Composable
fun EntityStatusChip(
  entity: GeospatialEntityItem,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  if (!entity.hasWorkflowStatus) return
  val fill = Color(entity.markerColorHex)
  val content = contentColorOnMarker(fill)
  val textStyle =
    if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
  Surface(
    modifier = modifier,
    shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.small,
    color = fill,
    contentColor = content,
  ) {
    Row(
      modifier =
        if (compact) {
          Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        } else {
          Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
      if (entity.hasMarkerSymbol) {
        Text(text = entity.markerSymbol, style = textStyle.copy(fontWeight = FontWeight.ExtraBold))
      }
      if (entity.workflowStatus.isNotEmpty()) {
        Text(text = entity.workflowStatus, style = textStyle.copy(fontWeight = FontWeight.SemiBold))
      }
    }
  }
}

/**
 * Compact status balloon for list rows: a small circle filled with the entity's `marker-color`
 * holding its `marker-symbol` (an empty dot when it has a `status` but no symbol). Draws nothing
 * when the entity has no workflow status. The status text itself isn't shown; list rows expose it
 * through their tooltip and `stateDescription`.
 */
@Composable
internal fun EntityStatusBalloon(
  entity: GeospatialEntityItem,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
) {
  if (!entity.hasWorkflowStatus) return
  val fill = Color(entity.markerColorHex)
  Box(
    modifier = modifier.size(size).background(fill, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (entity.hasMarkerSymbol) {
      Text(
        text = entity.markerSymbol,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = contentColorOnMarker(fill),
        maxLines = 1,
      )
    }
  }
}

/**
 * One-line row for an entity record in the searchable list: layer marker (the entity's [layer]
 * color and icon, matching its map pin), label, and status balloon (`marker-symbol` on
 * `marker-color`). Hovering (web) or long-pressing (mobile) shows the status text in a tooltip.
 * Records without a workflow status show neither the balloon nor the tooltip. The selected row is
 * highlighted and scrolled into view, including when it was selected on the map.
 *
 * When [compact] is `true` (the web dashboard's left-hand panel), the row is a dense
 * [compactHeight]-tall row indented by [compactStartIndent] so it lines up under its dataset header
 * row and matches that row's height; otherwise it is a Material 3 one-line [ListItem] with a
 * touch-friendly height.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EntityListRow(
  entity: GeospatialEntityItem,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  compactHeight: Dp = 32.dp,
  compactStartIndent: Dp = 8.dp,
  layer: MapLayerItem? = null,
) {
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  LaunchedEffect(isSelected) { if (isSelected) bringIntoViewRequester.bringIntoView() }

  val statusText = entity.workflowStatus
  val statusSemantics =
    if (statusText.isNotEmpty()) Modifier.semantics { stateDescription = statusText } else Modifier
  val rowContent: @Composable () -> Unit = {
    if (compact) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(compactHeight)
            .clip(MaterialTheme.shapes.small)
            .background(
              if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .then(statusSemantics)
            .padding(start = compactStartIndent, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        EntityLayerMarker(entity = entity, layer = layer, size = 18.dp)
        Text(
          text = entity.displayTitle,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        EntityStatusBalloon(entity = entity, size = 18.dp)
      }
    } else {
      val mobileInteractionSource = remember { MutableInteractionSource() }
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
              if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable(
              interactionSource = mobileInteractionSource,
              indication = null,
              onClick = onClick,
            )
            .then(statusSemantics)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        EntityLayerMarker(entity = entity, layer = layer)
        Text(
          text = entity.displayTitle,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        EntityStatusBalloon(entity = entity)
      }
    }
  }

  val outerModifier = modifier.bringIntoViewRequester(bringIntoViewRequester)
  if (compact && statusText.isNotEmpty()) {
    TooltipBox(
      positionProvider =
        TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
      tooltip = { PlainTooltip { Text(statusText) } },
      state = rememberTooltipState(),
      modifier = outerModifier,
    ) {
      rowContent()
    }
  } else {
    Box(modifier = outerModifier) { rowContent() }
  }
}

/**
 * Header of an entity's details surface: optional back button, layer marker (the entity's [layer]
 * color and icon, or its geometry icon when [layer] is `null`), label, status chip with an optional
 * [statusAccessory] next to it, optional [trailingActions], an optional collapse button, and an
 * optional close button (rendered only when [onClose] is non-null).
 * - [showStatus]: whether to show the status row (status chip and [statusAccessory]) under the
 *   label. The web details card turns it off and shows a compact status chip in its `Data` tab
 *   body.
 */
@Composable
fun EntityDetailsHeader(
  entity: GeospatialEntityItem,
  onClose: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backContentDescription: String = stringResource(Res.string.app_back_to_all_map_features),
  onCollapse: (() -> Unit)? = null,
  showStatus: Boolean = true,
  statusAccessory: @Composable () -> Unit = {},
  trailingActions: @Composable () -> Unit = {},
  layer: MapLayerItem? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = backContentDescription,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    if (layer != null) {
      EntityLayerMarker(entity = entity, layer = layer, size = 24.dp)
    } else {
      EntityGeometryIcon(entity = entity, size = 22.dp)
    }
    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = entity.displayTitle,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (showStatus) {
          statusAccessory()
        }
      }
      if (showStatus && entity.hasWorkflowStatus) {
        Row(
          modifier = Modifier.padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EntityStatusChip(entity = entity)
        }
      }
    }
    trailingActions()
    if (onCollapse != null) {
      IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.CollapseContent,
          contentDescription = stringResource(Res.string.app_collapse_details),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    if (onClose != null) {
      IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.app_close_details),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/**
 * An item of a details surface's overflow menu ([DetailsOverflowMenu]), labeled [labelResource]
 * when set, else [label].
 */
data class DetailsMenuAction(
  val label: String = "",
  val icon: ImageVector,
  val labelResource: StringResource? = null,
  val onClick: () -> Unit,
)

/** "More" ( ⋮ ) button opening a menu of secondary actions, e.g. QR code and Share PDF. */
@Composable
fun DetailsOverflowMenu(
  actions: List<DetailsMenuAction>,
  modifier: Modifier = Modifier,
  contentDescription: String = stringResource(Res.string.app_more_actions),
) {
  var isExpanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    IconButton(onClick = { isExpanded = true }, modifier = Modifier.size(32.dp)) {
      Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
      actions.forEach { action ->
        DropdownMenuItem(
          text = { Text(action.labelResource?.let { stringResource(it) } ?: action.label) },
          leadingIcon = { Icon(imageVector = action.icon, contentDescription = null) },
          onClick = {
            isExpanded = false
            action.onClick()
          },
        )
      }
    }
  }
}

/** Section heading inside an entity's details surface. */
@Composable
private fun DetailsSectionHeading(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 4.dp),
  )
}

/** Label/value pair arranged vertically. */
@Composable
private fun DetailsFieldRow(label: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    content()
  }
}

@Composable
private fun DetailsFieldRow(label: String, value: String) {
  DetailsFieldRow(label) {
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Link to a related record, shown for property values that reference another record. */
@Composable
private fun RelatedRecordLink(related: GeospatialEntityItem, onClick: () -> Unit) {
  Row(
    modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Link,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp),
    )
    EntityGeometryIcon(entity = related, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
    Text(
      text = related.label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      textDecoration = TextDecoration.Underline,
    )
  }
}

/**
 * Entity properties pane: all data associated with the entity (its current state), arranged
 * vertically.
 * - [isWeb]: the body of the web card's `Data` tab. Starts with a compact status chip (the web
 *   header omits it), shows the "Show in table" button followed by any [trailingWebActions] at the
 *   bottom, and omits the actions row (QR code and Download PDF live in the web header's overflow
 *   menu), mobile-only field actions (`Navigate`, data collection launchers, and `Uploads` links),
 *   and the submissions click-through (submissions live in the web card's `History` tab).
 * - On mobile, status and sync status are chips in the header, and the pane ends with a
 *   click-through to the entity's submissions.
 */
@Composable
fun EntityPropertiesPane(
  uiState: EntityDetailsUiState,
  actions: EntityDetailsActions,
  isWeb: Boolean,
  trailingWebActions: @Composable () -> Unit = {},
) {
  val entity = uiState.entity
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (isWeb) {
      EntityStatusChip(entity = entity, compact = true)
    } else {
      EntityActionsRow(uiState = uiState, actions = actions)
    }
    EntityDataCollectionLaunchers(
      entityId = entity.id,
      launchers = uiState.formLaunchers,
      onLaunchForm = actions::launchFormForEntity,
      notice = uiState.notice,
    )

    DetailsSectionHeading(stringResource(Res.string.app_details_heading))
    DetailsFieldRow(
      label = stringResource(Res.string.app_details_layer),
      value = entity.datasetName,
    )
    DetailsFieldRow(
      label = stringResource(Res.string.app_details_geometry),
      value = entityGeometrySummaryText(entity, uiState.unitSystem).resolve(),
    )
    DetailsFieldRow(label = stringResource(Res.string.app_details_geo_id)) {
      GeoIdText(
        entity = entity,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    val properties = entity.regularDisplayProperties
    if (properties.isNotEmpty()) {
      DetailsSectionHeading(stringResource(Res.string.app_details_properties))
      properties.forEach { (key, value) ->
        val related = uiState.relatedRecords[value]
        if (related != null) {
          DetailsFieldRow(label = key) {
            RelatedRecordLink(related = related, onClick = { actions.selectEntity(related.id) })
          }
        } else {
          val coloredAnswers =
            remember(key, value, uiState.formDefs) {
              ChoiceImageRegistry.resolveChoiceAnswerColors(
                questionName = key,
                answerValue = value,
                formDefs = uiState.formDefs,
              )
            }
          if (coloredAnswers.isNotEmpty()) {
            DetailsFieldRow(label = key) { ChoiceColorPillsRow(items = coloredAnswers) }
          } else {
            DetailsFieldRow(label = key, value = value)
          }
        }
      }
    }

    if (!isWeb) {
      val whispAnalysis = remember(entity.properties) { entity.whispAnalysis }
      if (whispAnalysis != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        WhispRiskAnalysisPane(analysis = whispAnalysis)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      EntitySubmissionsLink(
        uiState = uiState,
        onClick = { actions.openUploadsForEntity(entity.id) },
      )
    }

    if (isWeb) {
      EntityWebFooterActions(
        onShowInTable = { actions.showDatasetInTable(entity.datasetId) },
        trailingWebActions = trailingWebActions,
      )
    }
  }
}

/** Bottom action row of the web entity details card (`Show in table` plus [trailingWebActions]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityWebFooterActions(
  onShowInTable: () -> Unit,
  trailingWebActions: @Composable () -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EntityDetailsActionButton(
      text = stringResource(Res.string.app_show_in_table),
      icon = Icons.Outlined.TableRows,
      onClick = onShowInTable,
    )
    trailingWebActions()
  }
}

/** Compact tonal action button for the web entity details footer row. */
@Composable
fun EntityDetailsActionButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilledTonalButton(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    modifier = modifier,
  ) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}

/**
 * Web's `Download PDF` overflow menu item: generates the record's PDF on the device and saves it
 * right away (no share sheet, unlike mobile's `Share PDF`).
 */
fun downloadPdfMenuAction(onClick: () -> Unit): DetailsMenuAction =
  DetailsMenuAction(
    icon = Icons.Outlined.Download,
    labelResource = Res.string.app_download_pdf,
    onClick = onClick,
  )

/**
 * Row of mobile entity actions: the `Navigate` button with the distance and heading to the entity
 * ([NavigateAction]) and pending `Uploads`.
 */
@Composable
private fun EntityActionsRow(uiState: EntityDetailsUiState, actions: EntityDetailsActions) {
  val entity = uiState.entity
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (entity.hasGeometry) {
      NavigateAction(
        isNavigating = uiState.isNavigating,
        wayfinding = uiState.wayfinding,
        stopLabel = stringResource(Res.string.app_stop_navigation_short),
        onToggle = { actions.toggleNavigationToEntity(entity.id) },
      )
    }
    val pendingCount = uiState.pendingUploadCount
    if (pendingCount > 0) {
      AssistChip(
        onClick = { actions.openUploadsForEntity(entity.id) },
        label = {
          Text(pluralStringResource(Res.plurals.app_pending_uploads, pendingCount, pendingCount))
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Outlined.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
      )
    }
  }
}

/**
 * Organizer-defined data collection buttons ([launchers]) for the map feature with [entityId],
 * limited to the forms the survey designer made available on the current platform. On mobile they
 * open the one-question-per-screen runner over the map; on web they open the same form in the
 * right-hand panel's compact layout (`WebDataCollectionCard`), both through [onLaunchForm]. The
 * optional [notice] (the active survey notice) is shown under the buttons.
 */
@Composable
private fun EntityDataCollectionLaunchers(
  entityId: String,
  launchers: List<FormLauncher>,
  onLaunchForm: (entityId: String, formId: String) -> Unit,
  notice: String? = null,
) {
  if (launchers.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    DetailsSectionHeading(stringResource(Res.string.app_collect_data))
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      launchers.forEach { (form, isEnabled) ->
        Button(
          onClick = { onLaunchForm(entityId, form.id) },
          enabled = isEnabled,
          shape = MaterialTheme.shapes.small,
        ) {
          Icon(
            // Filled check marks the completed state; outlined form icon marks actionable.
            imageVector = if (isEnabled) Icons.Outlined.Description else Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (isEnabled) form.ctaLabel
            else stringResource(Res.string.app_form_action_completed, form.ctaLabel)
          )
        }
      }
    }
    notice?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/**
 * Mobile click-through from an entity's properties to its submissions ("Submissions (n) ›"). On
 * web, submissions are in the details card's `History` tab instead.
 */
@Composable
private fun EntitySubmissionsLink(uiState: EntityDetailsUiState, onClick: () -> Unit) {
  val entity = uiState.entity
  val isOffline = !uiState.isOnline
  val availableCount =
    if (isOffline) {
      uiState.submissionGroups.sumOf { it.submissions.size }
    } else {
      entity.submissionCount
    }
  ListItem(
    headlineContent = {
      Text(
        text = stringResource(Res.string.app_submissions_count, availableCount),
        style = MaterialTheme.typography.titleSmall,
      )
    },
    supportingContent = {
      Text(
        text =
          when {
            isOffline -> stringResource(Res.string.app_submissions_stored_offline)
            availableCount == 0 -> stringResource(Res.string.app_no_submissions_yet)
            else ->
              stringResource(
                Res.string.app_view_submission_history,
                entity.typeText.resolve().lowercase(),
              )
          },
        style = MaterialTheme.typography.bodySmall,
      )
    },
    leadingContent = { Icon(imageVector = Icons.Outlined.Description, contentDescription = null) },
    trailingContent = {
      Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
  )
}

/**
 * Entity submissions pane: the entity's `1:N` submissions grouped by form. Submissions open in a
 * document-style view ([SubmissionFullDetailsCard]).
 * - On mobile it is one step away from the properties, with a back button and a count heading.
 * - On web ([isWeb]) it is the body of the details card's `History` tab, which already labels it,
 *   so the back button and heading are omitted.
 *
 * Seeing the full list requires a connection, so on mobile while offline only submissions stored on
 * the device are listed, with a notice.
 */
@Composable
fun EntitySubmissionsPane(
  uiState: EntityDetailsUiState,
  actions: EntityDetailsActions,
  isWeb: Boolean,
) {
  val entity = uiState.entity
  val isOffline = !isWeb && !uiState.isOnline
  val groups = uiState.submissionGroups
  val count = groups.sumOf { it.submissions.size }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (!isWeb) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(onClick = { actions.showEntityProperties() }, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(Res.string.app_back_to_details),
            modifier = Modifier.size(20.dp),
          )
        }
        Text(
          text = stringResource(Res.string.app_submissions_count, count),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    if (isOffline) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = stringResource(Res.string.app_offline_showing_local_submissions),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    if (groups.isEmpty()) {
      Text(
        text =
          if (isOffline) {
            stringResource(
              Res.string.app_no_local_submissions_for_entity,
              entity.typeText.resolve().lowercase(),
            )
          } else {
            stringResource(
              Res.string.app_no_submissions_for_entity,
              entity.typeText.resolve().lowercase(),
            )
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      FormGroupedSubmissionsSection(
        groups = groups,
        viewer = uiState.viewer,
        showTargetEntityLabel = false,
        showFormActionSubtitle = false,
        onSelectSubmission = { actions.selectSubmissionDetail(it.id) },
        showSyncStatus = !isWeb,
      )
    }
  }
}

/**
 * Mobile details surface for the selected entity, replacing the bottom sheet's list:
 * - Header with back (← to the list), status, and a tappable sync status chip.
 * - Properties pane by default, the submissions pane one click away, and a document-style
 *   submission view (← back to the submissions) when a submission is opened.
 */
@Composable
internal fun EntityBottomSheetCard(
  uiState: EntityDetailsUiState,
  actions: EntityDetailsActions,
  modifier: Modifier = Modifier,
) {
  val entity = uiState.entity
  val openSubmission = uiState.openSubmission
  var isShareMenuExpanded by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EntityDetailsHeader(
      entity = entity,
      onBack = null,
      onClose = null,
      layer = uiState.layer,
      statusAccessory = {
        SyncStatusIndicatorBadge(
          syncStatus = entity.syncStatus,
          onClick = { actions.cycleEntitySyncStatus(entity.id) },
        )
      },
      trailingActions = {
        Box {
          IconButton(
            onClick = { isShareMenuExpanded = true },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Share,
              contentDescription = stringResource(Res.string.app_share),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
          DropdownMenu(
            expanded = isShareMenuExpanded,
            onDismissRequest = { isShareMenuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(Res.string.app_qr_code)) },
              leadingIcon = {
                Icon(imageVector = Icons.Outlined.QrCode, contentDescription = null)
              },
              onClick = {
                isShareMenuExpanded = false
                actions.openEntityQrCode(entity.id)
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(Res.string.app_share_pdf)) },
              leadingIcon = { Icon(imageVector = Icons.Outlined.Share, contentDescription = null) },
              onClick = {
                isShareMenuExpanded = false
                actions.shareEntityPdf(entity.id)
              },
            )
          }
        }
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when {
        openSubmission != null ->
          SubmissionFullDetailsCard(
            uiState = openSubmission,
            backLabel = stringResource(Res.string.app_back_to_submissions),
            onBack = { actions.selectSubmissionDetail(null) },
            onSharePdf = { actions.shareSubmissionPdf(openSubmission.submission.id) },
            onCycleSyncStatus = {
              actions.cycleSubmissionSyncStatus(openSubmission.submission.id)
            },
          )
        uiState.pane == EntityDetailsPane.SUBMISSIONS ->
          EntitySubmissionsPane(uiState = uiState, actions = actions, isWeb = false)
        else -> EntityPropertiesPane(uiState = uiState, actions = actions, isWeb = false)
      }
      Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
  }
}
