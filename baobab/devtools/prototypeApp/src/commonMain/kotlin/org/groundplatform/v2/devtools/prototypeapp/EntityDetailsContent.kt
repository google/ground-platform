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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
      EntityGeometryKind.LINE,
      EntityGeometryKind.POLYGON -> 17f
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
      EntityGeometryKind.POINT -> Icons.Outlined.Place
      EntityGeometryKind.LINE -> Icons.Outlined.Timeline
      EntityGeometryKind.POLYGON -> Icons.Outlined.Pentagon
      EntityGeometryKind.NONE -> Icons.Outlined.TableRows
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
 * - [compact]: a smaller chip (tighter padding, `labelSmall` text) for dense web surfaces such as
 *   the details card body and the data table. Mobile callers keep the default size.
 */
@Composable
internal fun EntityStatusChip(
  entity: GeospatialEntityItem,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
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
      Text(text = entity.workflowStatus, style = textStyle.copy(fontWeight = FontWeight.SemiBold))
    }
  }
}

/**
 * One-line row for an entity record in the searchable list: geometry icon, label, and marker
 * circle. Hovering (web) or long-pressing (mobile) shows the status text in a tooltip. The selected
 * row is highlighted and scrolled into view, including when it was selected on the map.
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
) {
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  LaunchedEffect(isSelected) { if (isSelected) bringIntoViewRequester.bringIntoView() }

  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(entity.workflowStatus) } },
    state = rememberTooltipState(),
    modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
  ) {
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
            .semantics { stateDescription = entity.workflowStatus }
            .padding(start = compactStartIndent, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        EntityGeometryIcon(entity = entity, size = 18.dp)
        Text(
          text = entity.label,
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
        EntityMarkerCircle(entity = entity, modifier = Modifier.size(20.dp))
      }
    } else {
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
}

/**
 * Header of an entity's details surface: optional back button, geometry icon, label, status chip
 * with an optional [statusAccessory] next to it, optional [trailingActions], an optional collapse
 * button, and an optional close button (rendered only when [onClose] is non-null).
 * - [showStatus]: whether to show the status row (status chip and [statusAccessory]) under the
 *   label. The web details card turns it off and shows a compact status chip in its `Data` tab
 *   body.
 */
@Composable
internal fun EntityDetailsHeader(
  entity: GeospatialEntityItem,
  onClose: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backContentDescription: String = "Back to all map features",
  onCollapse: (() -> Unit)? = null,
  showStatus: Boolean = true,
  statusAccessory: @Composable () -> Unit = {},
  trailingActions: @Composable () -> Unit = {},
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
    EntityGeometryIcon(entity = entity, size = 22.dp)
    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
      Text(
        text = entity.label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (showStatus) {
        Row(
          modifier = Modifier.padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EntityStatusChip(entity = entity)
          statusAccessory()
        }
      }
    }
    trailingActions()
    if (onCollapse != null) {
      IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.CollapseContent,
          contentDescription = "Collapse details",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    if (onClose != null) {
      IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Close details",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/** An item of a details surface's overflow menu ([DetailsOverflowMenu]). */
internal data class DetailsMenuAction(
  val label: String,
  val icon: ImageVector,
  val onClick: () -> Unit,
)

/** "More" ( ⋮ ) button opening a menu of secondary actions, e.g. QR code and Share PDF. */
@Composable
internal fun DetailsOverflowMenu(
  actions: List<DetailsMenuAction>,
  modifier: Modifier = Modifier,
  contentDescription: String = "More actions",
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
          text = { Text(action.label) },
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
 *   header omits it), shows the "Show in table" button at the bottom, and omits the actions row (QR
 *   code and Download PDF live in the web header's overflow menu), mobile-only field actions
 *   (`Navigate`, data collection launchers, and `Uploads` links), and the submissions click-through
 *   (submissions live in the web card's `History` tab).
 * - On mobile, status and sync status are chips in the header, and the pane ends with a
 *   click-through to the entity's submissions.
 */
@Composable
internal fun EntityPropertiesPane(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isWeb: Boolean,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (isWeb) {
      EntityStatusChip(entity = entity, compact = true)
    } else {
      EntityActionsRow(entity = entity, state = state)
      EntityDataCollectionLaunchers(entity = entity, state = state)
    }

    DetailsSectionHeading("Details")
    DetailsFieldRow(label = "Layer", value = entity.datasetName)
    DetailsFieldRow(label = "Geometry", value = entityGeometrySummary(entity, state.unitSystem))
    DetailsFieldRow(label = "GeoID") {
      GeoIdText(
        entity = entity,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
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

    if (!isWeb) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      EntitySubmissionsLink(entity = entity, state = state)
    }

    if (isWeb) {
      FilledTonalButton(onClick = { state.showSelectedEntityInTable() }) {
        Icon(
          imageVector = Icons.Outlined.TableRows,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Show in table")
      }
    }
  }
}

/**
 * Web's `Download PDF` overflow menu item: generates the record's PDF on the device and saves it
 * right away (no share sheet, unlike mobile's `Share PDF`).
 */
internal fun downloadPdfMenuAction(onClick: () -> Unit): DetailsMenuAction =
  DetailsMenuAction("Download PDF", Icons.Outlined.Download, onClick = onClick)

/** Secondary entity actions shown in the web header's overflow menu. */
private fun entityOverflowActions(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
): List<DetailsMenuAction> =
  listOf(
    DetailsMenuAction("QR code", Icons.Outlined.QrCode) { state.openEntityQrCode(entity.id) },
    downloadPdfMenuAction { state.downloadEntityPdf(entity.id) },
  )

/** Row of mobile entity actions: `Navigate`, QR code, Share PDF, and `Uploads`. */
@Composable
private fun EntityActionsRow(entity: GeospatialEntityItem, state: PrototypeAppState) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (entity.hasGeometry) {
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
            imageVector = Icons.Outlined.Navigation,
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
          imageVector = Icons.Outlined.QrCode,
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
          imageVector = Icons.Outlined.Share,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      },
    )
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
            imageVector = Icons.Outlined.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
      )
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
      modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
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
            // Filled check marks the completed state; outlined form icon marks actionable.
            imageVector = if (isEnabled) Icons.Outlined.Description else Icons.Filled.CheckCircle,
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

/**
 * Mobile click-through from an entity's properties to its submissions ("Submissions (n) ›"). On
 * web, submissions are in the details card's `History` tab instead.
 */
@Composable
private fun EntitySubmissionsLink(entity: GeospatialEntityItem, state: PrototypeAppState) {
  val isOffline = state.isAirplaneMode
  val availableCount =
    if (isOffline) {
      state.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size }
    } else {
      entity.submissionCount
    }
  ListItem(
    headlineContent = {
      Text(text = "Submissions ($availableCount)", style = MaterialTheme.typography.titleSmall)
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
    leadingContent = { Icon(imageVector = Icons.Outlined.Description, contentDescription = null) },
    trailingContent = {
      Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
        state.showEntitySubmissions()
      },
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
    if (!isWeb) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(onClick = { state.showEntityProperties() }, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
 * - Header with back (← to the list), status, and a tappable sync status chip.
 * - Properties pane by default, the submissions pane one click away, and a document-style
 *   submission view (← back to the submissions) when a submission is opened.
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
      onClose = null,
      statusAccessory = {
        SyncStatusIndicatorBadge(
          syncStatus = entity.syncStatus,
          onClick = { state.cycleEntitySyncStatus(entity.id) },
        )
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
 * Web dashboard's floating details card (upper-right corner of the map) for the selected entity,
 * with two tabs backed by [PrototypeAppState.entityDetailsPane]:
 * - `Data` ([EntityDetailsPane.PROPERTIES], default): a compact status chip and the entity's
 *   properties (its current state).
 * - `History` ([EntityDetailsPane.SUBMISSIONS]): the entity's submissions. An opened submission
 *   replaces the list in a document-style view, with back to the list. Opening a submission from
 *   elsewhere (e.g. `Uploads`) selects this tab, and switching to `Data` and back keeps it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebEntityDetailsCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  val submission = state.selectedSubmission?.takeIf { it.entityId == entity.id }
  val pane = state.entityDetailsPane
  // Each tab (and an opened submission) starts scrolled to the top.
  val scrollState = remember(entity.id, pane, submission?.id) { ScrollState(initial = 0) }

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
        onCollapse = onCollapse,
        // The status chip is shown, compact, at the top of the `Data` tab body instead.
        showStatus = false,
        trailingActions = {
          DetailsOverflowMenu(
            actions = entityOverflowActions(entity, state),
            contentDescription = "More ${entity.singularTypeLabel.lowercase()} actions",
          )
        },
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      )

      PrimaryTabRow(selectedTabIndex = pane.ordinal, containerColor = Color.Transparent) {
        EntityDetailsPane.entries.forEach { tabPane ->
          Tab(
            selected = pane == tabPane,
            onClick = { state.selectEntityDetailsTab(tabPane) },
            text = {
              Text(
                text =
                  when (tabPane) {
                    EntityDetailsPane.PROPERTIES -> "Data"
                    EntityDetailsPane.SUBMISSIONS -> "History"
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      }

      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
      ) {
        when {
          pane == EntityDetailsPane.PROPERTIES ->
            EntityPropertiesPane(entity = entity, state = state, isWeb = true)
          submission != null ->
            SubmissionFullDetailsCard(
              submission = submission,
              state = state,
              isDark = state.isDarkTheme,
              textColor = MaterialTheme.colorScheme.onSurface,
              backLabel = "Back to submissions",
              onBack = { state.selectSubmissionDetail(null) },
              onSharePdf = { state.downloadSubmissionPdf(submission.id) },
              isSidePanel = true,
            )
          else -> EntitySubmissionsPane(entity = entity, state = state, isWeb = true)
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
  onCollapse: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 6.dp,
  ) {
    Column {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(
          onClick = { state.selectSubmissionDetail(null) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
          )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
          Text(
            text = submission.formTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = submission.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        DetailsOverflowMenu(
          actions = listOf(downloadPdfMenuAction { state.downloadSubmissionPdf(submission.id) }),
          contentDescription = "More submission actions",
        )
        if (onCollapse != null) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Outlined.CollapseContent,
              contentDescription = "Collapse details",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SubmissionFullDetailsCard(
          submission = submission,
          state = state,
          isDark = state.isDarkTheme,
          textColor = MaterialTheme.colorScheme.onSurface,
          backLabel = "Back",
          onBack = { state.selectSubmissionDetail(null) },
          onSharePdf = { state.downloadSubmissionPdf(submission.id) },
          isSidePanel = true,
          showHeader = false,
        )
      }
    }
  }
}
