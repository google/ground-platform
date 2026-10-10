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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryPrograms
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary

/** Icon of a Purpose Pack's Material icon name (`PurposePack.icon`), or a flag. */
fun purposePackIcon(name: String): ImageVector =
  when (name) {
    "verified" -> Icons.Outlined.Verified
    "forest" -> Icons.Outlined.Forest
    "person_add" -> Icons.Outlined.PersonAdd
    "fact_check" -> Icons.Outlined.FactCheck
    else -> Icons.Outlined.Flag
  }

/**
 * Multi-select card grid of [packs] for "What will this data be used for?": each card shows the
 * pack's icon, title, description, source (organization name or "Global"), and what it adds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurposePackGrid(
  packs: List<PurposePack>,
  library: ResolvedLibrary,
  organizationName: String?,
  language: String,
  selectedIds: List<String>,
  onToggle: (packId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    packs.forEach { pack ->
      PurposePackCard(
        pack = pack,
        library = library,
        sourceLabel =
          if (library.isOrganizationEntry(pack.id)) organizationName ?: "Organization"
          else "Global",
        language = language,
        selected = pack.id in selectedIds,
        onClick = { onToggle(pack.id) },
      )
    }
  }
}

@Composable
private fun PurposePackCard(
  pack: PurposePack,
  library: ResolvedLibrary,
  sourceLabel: String,
  language: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val templates = library.templatesForPurposes(listOf(pack.id))
  val linked = templates.sumOf { it.linkedQuestionCount }
  val summary =
    listOfNotNull(
        "${templates.size} ${if (templates.size == 1) "form" else "forms"}"
          .takeIf { templates.isNotEmpty() },
        "$linked standard ${if (linked == 1) "field" else "fields"}".takeIf { linked > 0 },
      )
      .joinToString(" · ")
  OutlinedCard(
    onClick = onClick,
    modifier =
      Modifier.width(232.dp).heightIn(min = 168.dp).semantics {
        role = Role.Checkbox
        this.selected = selected
      },
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        if (selected) 2.dp else 1.dp,
        if (selected) colors.secondary else colors.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          purposePackIcon(pack.icon),
          contentDescription = null,
          tint = if (selected) colors.onSecondaryContainer else colors.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
          Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Selected",
            tint = colors.secondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Text(
        pack.title.get(language),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        pack.description.get(language),
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        minLines = 3,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        GroundTonalBadge(
          text = sourceLabel,
          tone = if (sourceLabel == "Global") GroundBadgeTone.NEUTRAL else GroundBadgeTone.TERTIARY,
        )
        if (summary.isNotEmpty()) {
          Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Optional program chips ([LibraryPrograms]) offered by the selected Purpose Packs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramChips(
  programIds: List<String>,
  selectedIds: List<String>,
  onToggle: (programId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (programIds.isEmpty()) return
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    programIds.forEach { id ->
      val selected = id in selectedIds
      GroundFilterChip(
        selected = selected,
        onClick = { onToggle(id) },
        label = { Text(LibraryPrograms.label(id)) },
        leadingIcon =
          if (selected) {
            {
              Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
          } else {
            null
          },
      )
    }
  }
}
