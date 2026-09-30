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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.roundToInt

/** Geometry kind of a map feature, shown as the leading icon of list rows and details headers. */
internal enum class EntityGeometryKind(val label: String) {
  POINT("Point"),
  LINE("Line"),
  POLYGON("Polygon"),

  /** A record without geometry, i.e. a row in a Data table. */
  NONE("No geometry"),
}

/** Geometry kind derived from [GeospatialEntityItem.geometryTypeLabel]. */
internal val GeospatialEntityItem.geometryKind: EntityGeometryKind
  get() =
    when {
      geometryTypeLabel.contains("polygon", ignoreCase = true) -> EntityGeometryKind.POLYGON
      geometryTypeLabel.contains("line", ignoreCase = true) -> EntityGeometryKind.LINE
      geometryTypeLabel.contains("point", ignoreCase = true) -> EntityGeometryKind.POINT
      else -> EntityGeometryKind.NONE
    }

/** True when the record has a geometry the map can pan and zoom to. */
internal val GeospatialEntityItem.hasGeometry: Boolean
  get() = geometryKind != EntityGeometryKind.NONE

/**
 * `simplestyle-spec` presentation keys and the workflow `status`. They drive map styling and the
 * status icon, so they are left out of property listings and the map layer table.
 */
internal val EntityPresentationPropertyKeys =
  setOf(
    "marker-size",
    "marker-symbol",
    "marker-color",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "fill",
    "fill-opacity",
    "title",
    "description",
    "status",
  )

/** Organizer-defined properties of the entity, excluding presentation keys. */
internal val GeospatialEntityItem.displayProperties: List<Pair<String, String>>
  get() = properties.filterKeys { it !in EntityPresentationPropertyKeys }.toList()

/**
 * Maximum map camera zoom used when fitting a selected entity of this geometry kind into view, so
 * small features (and points, which have no extent) aren't framed too close.
 */
internal val EntityGeometryKind.maxFramingZoom: Float
  get() =
    when (this) {
      EntityGeometryKind.POINT -> 16f
      EntityGeometryKind.LINE, EntityGeometryKind.POLYGON -> 17f
      EntityGeometryKind.NONE -> 0f
    }

/** Human-readable geometry summary (kind plus measurements in the user's unit system). */
internal fun entityGeometrySummary(
  entity: GeospatialEntityItem,
  unitSystem: MeasurementUnitSystem,
): String {
  val isMetric = unitSystem == MeasurementUnitSystem.METRIC
  fun length(meters: Int) = if (isMetric) "$meters m" else "${(meters * 3.28084).roundToInt()} ft"
  return when (entity.geometryKind) {
    EntityGeometryKind.POLYGON -> {
      val area =
        if (isMetric) {
          "${entity.areaHectares} ha"
        } else {
          "${((entity.areaHectares * 2.47105) * 100.0).roundToInt() / 100.0} acres"
        }
      "Polygon • $area, ${length(entity.perimeterMeters)} perimeter"
    }
    EntityGeometryKind.LINE -> "Line • ${length(entity.perimeterMeters)}"
    EntityGeometryKind.POINT -> "Point • ${entity.coordinatesLabel}"
    EntityGeometryKind.NONE -> EntityGeometryKind.NONE.label
  }
}

private val EntityGeometryKind.icon: ImageVector
  get() =
    when (this) {
      EntityGeometryKind.POINT -> Icons.Default.Place
      EntityGeometryKind.LINE -> Icons.Default.Timeline
      EntityGeometryKind.POLYGON -> Icons.Default.Pentagon
      EntityGeometryKind.NONE -> Icons.Default.TableRows
    }

/** Icon indicating whether [entity] has a point, line, polygon, or no geometry. */
@Composable
internal fun EntityGeometryIcon(
  entity: GeospatialEntityItem,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val kind = entity.geometryKind
  Icon(
    imageVector = kind.icon,
    contentDescription = kind.label,
    tint = tint,
    modifier = modifier.size(size),
  )
}

/** Readable content color (symbol and text) on top of a marker-color fill. */
private fun contentColorOnMarker(markerColor: Color): Color =
  if (markerColor.luminance() > 0.6f) Color(0xFF1F1F1F) else Color.White

/**
 * The entity's marker circle: a circle filled with its `simplestyle-spec` `marker-color`,
 * containing its `marker-symbol`. Renders nothing when the entity has no marker symbol.
 */
@Composable
internal fun EntityMarkerCircle(entity: GeospatialEntityItem, modifier: Modifier = Modifier) {
  if (!entity.hasMarkerSymbol) return
  val fill = Color(entity.markerColorHex)
  Box(
    modifier = modifier.size(24.dp).background(fill, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = entity.markerSymbol,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = contentColorOnMarker(fill),
    )
  }
}

/**
 * Status chip for an entity's details: its `marker-symbol` and status text on a fill of its
 * `marker-color`.
 */
@Composable
internal fun EntityStatusChip(entity: GeospatialEntityItem, modifier: Modifier = Modifier) {
  val fill = Color(entity.markerColorHex)
  val content = contentColorOnMarker(fill)
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = fill,
    contentColor = content,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (entity.hasMarkerSymbol) {
        Text(
          text = entity.markerSymbol,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
        )
      }
      Text(
        text = entity.workflowStatus,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      )
    }
  }
}

/**
 * One-line row for an entity record in the searchable list: geometry icon, label, and marker
 * circle. Hovering (web) or long-pressing (mobile) shows the status text in a tooltip. The selected
 * row is highlighted and scrolled into view, including when it was selected on the map.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EntityListRow(
  entity: GeospatialEntityItem,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  LaunchedEffect(isSelected) { if (isSelected) bringIntoViewRequester.bringIntoView() }

  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(entity.workflowStatus) } },
    state = rememberTooltipState(),
    modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
  ) {
    ListItem(
      headlineContent = {
        Text(
          text = entity.label,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      leadingContent = { EntityGeometryIcon(entity = entity) },
      trailingContent = { EntityMarkerCircle(entity = entity) },
      colors =
        ListItemDefaults.colors(
          containerColor =
            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
          headlineColor =
            if (isSelected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onSurface
            },
        ),
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable(onClick = onClick)
          .semantics { stateDescription = entity.workflowStatus },
    )
  }
}

/**
 * Header of an entity's details surface: optional back button, geometry icon, label, status icon
 * and status text, optional [trailingActions], and a close button.
 */
@Composable
internal fun EntityDetailsHeader(
  entity: GeospatialEntityItem,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backContentDescription: String = "Back to all map features",
  trailingActions: @Composable () -> Unit = {},
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = backContentDescription,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    EntityGeometryIcon(entity = entity, size = 22.dp)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entity.label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      EntityStatusChip(entity = entity, modifier = Modifier.padding(top = 4.dp))
    }
    trailingActions()
    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close details",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

/** Uppercase section heading inside an entity's details surface. */
@Composable
private fun DetailsSectionHeading(text: String) {
  Text(
    text = text.uppercase(),
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
      imageVector = Icons.Default.Link,
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
 * vertically, with a click-through to its submissions.
 * - [isWeb]: shows the "Show in table" button and omits mobile-only field actions (`Navigate`, data
 * collection launchers, sync status, and `Uploads` links).
 */
@Composable
internal fun EntityPropertiesPane(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isWeb: Boolean,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    EntityActionsRow(entity = entity, state = state, isWeb = isWeb)

    if (!isWeb) {
      EntityDataCollectionLaunchers(entity = entity, state = state)
    }

    DetailsSectionHeading("Details")
    DetailsFieldRow(label = "Layer", value = entity.datasetName)
    DetailsFieldRow(label = "Geometry", value = entityGeometrySummary(entity, state.unitSystem))
    DetailsFieldRow(label = "GeoID", value = entity.geoId)
    if (!isWeb) {
      DetailsFieldRow(label = "Sync") {
        SyncStatusIndicatorBadge(
          syncStatus = entity.syncStatus,
          onClick = { state.cycleEntitySyncStatus(entity.id) },
        )
      }
    }

    val properties = entity.displayProperties
    if (properties.isNotEmpty()) {
      DetailsSectionHeading("Properties")
      properties.forEach { (key, value) ->
        val related = state.relatedEntityForPropertyValue(entity, value)
        if (related != null) {
          DetailsFieldRow(label = key) {
            RelatedRecordLink(related = related, onClick = { state.selectEntity(related.id) })
          }
        } else {
          DetailsFieldRow(label = key, value = value)
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    EntitySubmissionsLink(entity = entity, state = state, isWeb = isWeb)
  }
}

/** Row of entity actions: "Show in table" (web), `Navigate` (mobile), QR code, and Share PDF. */
@Composable
private fun EntityActionsRow(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isWeb: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isWeb) {
      FilledTonalButton(onClick = { state.showSelectedEntityInTable() }) {
        Icon(
          imageVector = Icons.Default.TableRows,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Show in table")
      }
    } else if (entity.hasGeometry) {
      val isNavigating = state.isNavigatingToEntity(entity.id)
      val wayfinding = state.formattedWayfindingBadgeForEntity(entity.id)
      FilterChip(
        selected = isNavigating,
        onClick = { state.toggleNavigationToEntity(entity.id) },
        label = {
          Text(
            text =
              when {
                isNavigating -> "Stop nav"
                wayfinding.isNotEmpty() -> "Navigate • $wayfinding"
                else -> "Navigate"
              },
            maxLines = 1,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
      )
    }
    AssistChip(
      onClick = { state.openEntityQrCode(entity.id) },
      label = { Text("QR code") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.QrCode,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      },
    )
    AssistChip(
      onClick = { state.shareEntityPdf(entity.id) },
      label = { Text("Share PDF") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      },
    )
    if (!isWeb) {
      val uploadCount = state.uploadCountForEntity(entity.id)
      if (uploadCount > 0) {
        val pendingCount = state.pendingUploadCountForEntity(entity.id)
        AssistChip(
          onClick = { state.openUploadsForEntity(entity.id) },
          label = {
            Text(
              if (pendingCount > 0) {
                "$pendingCount pending ${if (pendingCount == 1) "upload" else "uploads"}"
              } else {
                "Uploads ($uploadCount)"
              }
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
        )
      }
    }
  }
}

/** Organizer-defined data collection buttons for this entity's dataset (mobile only). */
@Composable
private fun EntityDataCollectionLaunchers(entity: GeospatialEntityItem, state: PrototypeAppState) {
  val entityForms = state.formsForEntity(entity)
  if (entityForms.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    DetailsSectionHeading("Collect data")
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
            imageVector = if (isEnabled) Icons.Default.Description else Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (isEnabled) form.ctaLabel else "${form.ctaLabel} (Completed)")
        }
      }
    }
    state.activeSurveyNotice?.let { notice ->
      Text(
        text = notice,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/** Click-through from an entity's properties to its submissions ("Submissions (n) ›"). */
@Composable
private fun EntitySubmissionsLink(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isWeb: Boolean,
) {
  val isOffline = !isWeb && state.isAirplaneMode
  val availableCount =
    if (isOffline) {
      state.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size }
    } else {
      entity.submissionCount
    }
  ListItem(
    headlineContent = {
      Text(
        text = "Submissions ($availableCount)",
        style = MaterialTheme.typography.titleSmall,
      )
    },
    supportingContent = {
      Text(
        text =
          when {
            isOffline -> "Stored on this device. Connect to the internet to see all submissions."
            availableCount == 0 -> "No submissions yet"
            else ->
              "View the history of data collected for this ${entity.singularTypeLabel.lowercase()}"
          },
        style = MaterialTheme.typography.bodySmall,
      )
    },
    leadingContent = { Icon(imageVector = Icons.Default.Description, contentDescription = null) },
    trailingContent = { Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
        state.showEntitySubmissions()
      },
  )
}

/**
 * Entity submissions pane: the entity's `1:N` submissions grouped by form, one step away from its
 * properties. Submissions open in a document-style view ([SubmissionFullDetailsCard]).
 *
 * Seeing the full list requires a connection, so on mobile while offline only submissions stored on
 * the device are listed, with a notice.
 */
@Composable
internal fun EntitySubmissionsPane(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isWeb: Boolean,
) {
  val isOffline = !isWeb && state.isAirplaneMode
  val groups =
    if (isWeb) {
      state.groupedSubmissionsForEntity(entity)
    } else {
      state.availableGroupedSubmissionsForEntity(entity)
    }
  val count = groups.sumOf { it.submissions.size }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      IconButton(onClick = { state.showEntityProperties() }, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back to details",
          modifier = Modifier.size(20.dp),
        )
      }
      Text(
        text = "Submissions ($count)",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
      )
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
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              "You're offline. Showing submissions stored on this device. Connect to the " +
                "internet to see all submissions.",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    if (groups.isEmpty()) {
      Text(
        text =
          if (isOffline) {
            "No submissions for this ${entity.singularTypeLabel.lowercase()} are stored on this device."
          } else {
            "No submissions yet for this ${entity.singularTypeLabel.lowercase()}."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      FormGroupedSubmissionsSection(
        groups = groups,
        state = state,
        showTargetEntityLabel = false,
        showFormActionSubtitle = false,
        onSelectSubmission = { state.selectSubmissionDetail(it.id) },
        showSyncStatus = !isWeb,
      )
    }
  }
}

/**
 * Mobile details surface for the selected entity, replacing the bottom sheet's list:
 * - Header with back (← to the list), status, expand/collapse, and close.
 * - Properties pane by default, the submissions pane one click away, and a document-style
 * submission view (← back to the submissions) when a submission is opened.
 */
@Composable
internal fun EntityBottomSheetCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val selectedSubmission = state.selectedSubmission?.takeIf { it.entityId == entity.id }
  val isDark = state.isDarkTheme

  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EntityDetailsHeader(
      entity = entity,
      onBack = { state.returnToBottomSheetList() },
      onClose = { state.selectEntity(null) },
      trailingActions = {
        IconButton(
          onClick = { state.toggleEntityBottomSheetExpanded() },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector =
              if (state.isEntityBottomSheetExpanded) {
                Icons.Default.KeyboardArrowDown
              } else {
                Icons.Default.KeyboardArrowUp
              },
            contentDescription =
              if (state.isEntityBottomSheetExpanded) "Collapse details" else "Expand details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when {
        selectedSubmission != null ->
          SubmissionFullDetailsCard(
            submission = selectedSubmission,
            state = state,
            isDark = isDark,
            textColor = MaterialTheme.colorScheme.onSurface,
            backLabel = "Back to submissions",
            onBack = { state.selectSubmissionDetail(null) },
            onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
          )
        state.entityDetailsPane == EntityDetailsPane.SUBMISSIONS ->
          EntitySubmissionsPane(entity = entity, state = state, isWeb = false)
        else -> EntityPropertiesPane(entity = entity, state = state, isWeb = false)
      }
      Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
  }
}

/**
 * Web dashboard's floating details card (upper-right corner of the map) for the selected entity.
 * - Opens on the entity's properties; its submissions are one click away.
 * - An opened submission appears in a second tab next to the entity, in a document-style view, so
 * the user can switch between the entity and the submission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebEntityDetailsCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val submission = state.selectedSubmission?.takeIf { it.entityId == entity.id }
  var selectedTab by
    remember(submission?.id) { mutableIntStateOf(if (submission != null) 1 else 0) }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 6.dp,
  ) {
    Column {
      EntityDetailsHeader(
        entity = entity,
        onClose = { state.selectEntity(null) },
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
      )

      if (submission != null) {
        PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = {
              Text(text = entity.singularTypeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = submission.formTitle,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                  modifier =
                    Modifier.padding(start = 4.dp)
                      .size(20.dp)
                      .clip(MaterialTheme.shapes.extraSmall)
                      .clickable { state.selectSubmissionDetail(null) },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close submission",
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            },
          )
        }
      } else {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }

      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
      ) {
        when {
          submission != null && selectedTab == 1 ->
            SubmissionFullDetailsCard(
              submission = submission,
              state = state,
              isDark = state.isDarkTheme,
              textColor = MaterialTheme.colorScheme.onSurface,
              backLabel = "Close submission",
              onBack = { state.selectSubmissionDetail(null) },
              onSharePdf = { state.shareSubmissionPdf(submission.id) },
              isSidePanel = true,
              showBackButton = false,
            )
          state.entityDetailsPane == EntityDetailsPane.SUBMISSIONS ->
            EntitySubmissionsPane(entity = entity, state = state, isWeb = true)
          else -> EntityPropertiesPane(entity = entity, state = state, isWeb = true)
        }
      }
    }
  }
}

/**
 * Web dashboard's floating card for a standalone submission (one without an attached map feature),
 * shown in the same document-style view.
 */
@Composable
internal fun WebSubmissionDetailsCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
      SubmissionFullDetailsCard(
        submission = submission,
        state = state,
        isDark = state.isDarkTheme,
        textColor = MaterialTheme.colorScheme.onSurface,
        backLabel = "Close submission",
        onBack = { state.selectSubmissionDetail(null) },
        onSharePdf = { state.shareSubmissionPdf(submission.id) },
        isSidePanel = true,
      )
    }
  }
}
