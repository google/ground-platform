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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.web.domain.model.ColumnPrefixGroup
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_collapse_column_group
import org.openforis.collectone.v2.web.resources.web_expand_column_group

/** Width of an inline collapsed [ColumnPrefixGroup] placeholder column inside a table. */
val CollapsedColumnGroupWidth: Dp = 156.dp

/** Test tag prefix for clickable toolbar column-group toggle chips (`column_group_chip:<id>`). */
fun columnGroupChipTestTag(groupId: String): String = "column_group_chip:$groupId"

/** Test tag prefix for inline collapsed column-group headers (`column_group_header:<id>`). */
fun columnGroupHeaderTestTag(groupId: String): String = "column_group_header:$groupId"

/** Test tag prefix for inline collapse buttons at the start of an expanded group. */
fun columnGroupCollapseButtonTestTag(groupId: String): String = "column_group_collapse:$groupId"

/**
 * Compact clickable row of pills for toggling common-prefix column groups (e.g. `whisp_* (230)`).
 * Lets users expand or collapse a group regardless of horizontal scroll position.
 */
@Composable
fun <T> ColumnPrefixGroupChipsRow(
  groups: List<ColumnPrefixGroup<T>>,
  expandedGroupIds: Set<String>,
  onToggle: (ColumnPrefixGroup<T>) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (groups.isEmpty()) return
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    groups.forEach { group ->
      val expanded = group.id in expandedGroupIds
      ColumnPrefixGroupChip(
        label = group.wildcardLabel,
        count = group.size,
        expanded = expanded,
        groupId = group.id,
        onClick = { onToggle(group) },
      )
    }
  }
}

@Composable
private fun ColumnPrefixGroupChip(
  label: String,
  count: Int,
  expanded: Boolean,
  groupId: String,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val description =
    if (expanded) {
      stringResource(Res.string.web_collapse_column_group, count, label)
    } else {
      stringResource(Res.string.web_expand_column_group, count, label)
    }
  val containerColor = if (expanded) colors.secondaryContainer else colors.surfaceContainerHigh
  val contentColor = if (expanded) colors.onSecondaryContainer else colors.onSurfaceVariant
  val borderColor = if (expanded) colors.secondary.copy(alpha = 0.5f) else colors.outlineVariant
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    contentColor = contentColor,
    border = BorderStroke(1.dp, borderColor),
    modifier =
      Modifier.height(28.dp)
        .testTag(columnGroupChipTestTag(groupId))
        .pointerHoverIcon(PointerIcon.Hand)
        .semantics { contentDescription = description },
  ) {
    Row(
      modifier = Modifier.padding(start = 6.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector =
          if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      GroupCountBadge(count = count, expanded = expanded)
    }
  }
}

/**
 * Clickable table header cell representing a collapsed common-prefix column group (e.g. `▸ whisp_*
 * 230`). Clicking expands all grouped columns in place.
 */
@Composable
fun CollapsedColumnGroupHeaderCell(
  groupId: String,
  wildcardLabel: String,
  count: Int,
  width: Dp,
  height: Dp,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val description = stringResource(Res.string.web_expand_column_group, count, wildcardLabel)
  Row(
    modifier =
      modifier
        .width(width)
        .height(height)
        .testTag(columnGroupHeaderTestTag(groupId))
        .background(colors.secondaryContainer.copy(alpha = 0.45f))
        .border(0.5.dp, colors.outlineVariant)
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable(role = Role.Button, onClickLabel = description, onClick = onExpand)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.ChevronRight,
      contentDescription = description,
      tint = colors.primary,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = wildcardLabel,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = colors.onSecondaryContainer,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    GroupCountBadge(count = count, expanded = false)
  }
}

/**
 * Muted inline table body cell shown under a [CollapsedColumnGroupHeaderCell]. Clicking any row in
 * the collapsed band expands the group in place.
 */
@Composable
fun CollapsedColumnGroupBodyCell(
  width: Dp,
  height: Dp,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
  showBorder: Boolean = true,
  tint: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.14f),
) {
  val borderModifier =
    if (showBorder) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant) else Modifier
  Box(
    modifier =
      modifier
        .width(width)
        .height(height)
        .background(tint)
        .then(borderModifier)
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable(role = Role.Button, onClick = onExpand),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "⋯",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      maxLines = 1,
    )
  }
}

/**
 * Compact inline collapse button anchored inside the leading edge of the first column header of an
 * expanded group.
 */
@Composable
fun ExpandedGroupCollapseButton(
  groupId: String,
  wildcardLabel: String,
  count: Int,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val description = stringResource(Res.string.web_collapse_column_group, count, wildcardLabel)
  Surface(
    onClick = onCollapse,
    shape = RoundedCornerShape(4.dp),
    color = colors.secondaryContainer,
    contentColor = colors.onSecondaryContainer,
    modifier =
      modifier
        .size(20.dp)
        .testTag(columnGroupCollapseButtonTestTag(groupId))
        .pointerHoverIcon(PointerIcon.Hand)
        .semantics { contentDescription = description },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Outlined.ChevronLeft,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

/** Accent stripe drawn along the top edge of an expanded prefix-group header cell. */
@Composable
fun ExpandedGroupAccentStrip(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(2.dp)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
  )
}

@Composable
private fun GroupCountBadge(count: Int, expanded: Boolean) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(8.dp),
    color =
      if (expanded) colors.secondary.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.8f),
  ) {
    Text(
      text = count.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = if (expanded) colors.onSecondaryContainer else colors.onSurfaceVariant,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
    )
  }
}

/** Collapsible accordion header row used inside vertical property panels (e.g. Survey Editor). */
@Composable
fun CollapsiblePropertyGroupRow(
  groupId: String,
  wildcardLabel: String,
  count: Int,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val description =
    if (expanded) {
      stringResource(Res.string.web_collapse_column_group, count, wildcardLabel)
    } else {
      stringResource(Res.string.web_expand_column_group, count, wildcardLabel)
    }
  Surface(
    onClick = onToggle,
    color =
      if (expanded) colors.secondaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(0.5.dp, colors.outlineVariant),
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("property_group_row:$groupId")
        .pointerHoverIcon(PointerIcon.Hand)
        .semantics { contentDescription = description },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector =
          if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = colors.primary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = wildcardLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      GroupCountBadge(count = count, expanded = expanded)
    }
  }
}
