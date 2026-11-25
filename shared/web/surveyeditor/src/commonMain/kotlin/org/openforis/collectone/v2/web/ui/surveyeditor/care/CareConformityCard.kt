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
package org.openforis.collectone.v2.web.ui.surveyeditor.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.AppStaticColors
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_care_applied_body
import org.openforis.collectone.v2.web.resources.wizard_care_apply_defaults
import org.openforis.collectone.v2.web.resources.wizard_care_criterion_met
import org.openforis.collectone.v2.web.resources.wizard_care_criterion_not_met
import org.openforis.collectone.v2.web.resources.wizard_care_defaults_applied
import org.openforis.collectone.v2.web.resources.wizard_care_excluded_from_aggregates
import org.openforis.collectone.v2.web.resources.wizard_care_hide_details
import org.openforis.collectone.v2.web.resources.wizard_care_learn_more
import org.openforis.collectone.v2.web.resources.wizard_care_not_applied_body
import org.openforis.collectone.v2.web.resources.wizard_care_show_details
import org.openforis.collectone.v2.web.resources.wizard_care_state_collapsed
import org.openforis.collectone.v2.web.resources.wizard_care_state_expanded
import org.openforis.collectone.v2.web.resources.wizard_care_sticky_chip
import org.openforis.collectone.v2.web.resources.wizard_care_sticky_chip_a11y
import org.openforis.collectone.v2.web.resources.wizard_care_summary_count
import org.openforis.collectone.v2.web.resources.wizard_care_summary_title

/** Defaults for [CareConformityCard]. */
internal object CareConformityCardDefaults {
  /** How long to let the details expand before scrolling a criterion into view. */
  const val EXPAND_ANIMATION_MILLIS = 250L
}

/**
 * Where the CARE card sits on screen, so the Sharing step can show [CareStickyChip] while the
 * card's summary row is scrolled above the step's viewport, and scroll back to it.
 */
@Stable
class CareCardPosition {
  /** Top of the step's scrolling viewport, in root coordinates. */
  var viewportTop by mutableFloatStateOf(0f)
  /** Top of the CARE card, in root coordinates, or `NaN` before it's laid out. */
  var cardTop by mutableFloatStateOf(Float.NaN)
  /** Bottom of the CARE card's summary row, in root coordinates, or `NaN` before it's laid out. */
  var summaryBottom by mutableFloatStateOf(Float.NaN)

  /** Whether the summary row has scrolled out of view above the viewport. */
  val isSummaryScrolledAway: Boolean
    get() = !summaryBottom.isNaN() && summaryBottom < viewportTop
}

/**
 * CARE Principles & FAO FPIC card. Collapsed, it's a summary row: a status pip per criterion, how
 * many are met, the Apply CARE defaults action, and why CARE was applied automatically. Expanded,
 * it adds the full analysis: what the defaults do, the explainer link, and each criterion's effect.
 * Clicking a pip expands the card and scrolls to that criterion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CareConformityCard(
  conformity: CareConformity,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onApplyDefaults: (() -> Unit)?,
  modifier: Modifier = Modifier,
  position: CareCardPosition? = null,
  autoReason: String? = null,
) {
  val rowRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
  var pendingCriterion by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(pendingCriterion, expanded) {
    val code = pendingCriterion ?: return@LaunchedEffect
    if (!expanded) return@LaunchedEffect
    delay(CareConformityCardDefaults.EXPAND_ANIMATION_MILLIS)
    rowRequesters[code]?.bringIntoView()
    pendingCriterion = null
  }

  var isShowingCareExplainer by remember { mutableStateOf(false) }
  if (isShowingCareExplainer) {
    CareExplainerDialog(onDismiss = { isShowingCareExplainer = false })
  }

  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier =
      modifier.fillMaxWidth().onGloballyPositioned { position?.cardTop = it.positionInRoot().y },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      CareSummaryRow(
        conformity = conformity,
        expanded = expanded,
        position = position,
        onToggleExpanded = { onExpandedChange(!expanded) },
        onSelectCriterion = { code ->
          pendingCriterion = code
          onExpandedChange(true)
        },
        onApplyDefaults = onApplyDefaults,
      )

      // What CARE is and what its defaults do stays visible above the fold.
      Text(
        text =
          stringResource(
            if (conformity.isCareApplied) Res.string.wizard_care_applied_body
            else Res.string.wizard_care_not_applied_body
          ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 38.dp, end = 4.dp),
      )

      // Why CARE was applied for the organizer stays visible, never hidden behind "Show details".
      autoReason?.let { CareAutoReasonRow(it) }

      CareDetailsSection(
        conformity = conformity,
        expanded = expanded,
        rowRequesters = rowRequesters,
        onShowExplainer = { isShowingCareExplainer = true },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CareSummaryRow(
  conformity: CareConformity,
  expanded: Boolean,
  position: CareCardPosition?,
  onToggleExpanded: () -> Unit,
  onSelectCriterion: (String) -> Unit,
  onApplyDefaults: (() -> Unit)?,
) {
  val criteria = conformity.criteria
  val toggleLabel =
    stringResource(
      if (expanded) Res.string.wizard_care_hide_details else Res.string.wizard_care_show_details
    )
  val stateLabel =
    stringResource(
      if (expanded) Res.string.wizard_care_state_expanded
      else Res.string.wizard_care_state_collapsed
    )
  // Summary row: always visible. Clicking anywhere outside its own controls toggles details.
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded)
        .semantics { stateDescription = stateLabel }
        .onGloballyPositioned { position?.summaryBottom = it.positionInRoot().y + it.size.height }
        .padding(4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(22.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(Res.string.wizard_care_summary_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            stringResource(
              Res.string.wizard_care_summary_count,
              conformity.metCount,
              criteria.size,
            ),
          style = MaterialTheme.typography.labelMedium,
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        criteria.forEach { criterion ->
          CareStatusPip(criterion = criterion, onClick = { onSelectCriterion(criterion.code) })
        }
      }
    }
    if (conformity.isCareApplied) {
      CareDefaultsAppliedBadge()
    } else if (onApplyDefaults != null) {
      OutlinedButton(onClick = onApplyDefaults) {
        Text(stringResource(Res.string.wizard_care_apply_defaults))
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = toggleLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun CareAutoReasonRow(reason: String) {
  Row(
    modifier = Modifier.padding(start = 38.dp, end = 4.dp),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      Icons.Outlined.AutoAwesome,
      contentDescription = null,
      modifier = Modifier.padding(top = 1.dp).size(16.dp),
    )
    Text(
      text = reason,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CareDetailsSection(
  conformity: CareConformity,
  expanded: Boolean,
  rowRequesters: MutableMap<String, BringIntoViewRequester>,
  onShowExplainer: () -> Unit,
) {
  AnimatedVisibility(
    visible = expanded,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 4.dp).padding(top = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(
        modifier = Modifier.padding(start = 34.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        conformity.excludedOrganizationName?.let { organizationName ->
          Text(
            text =
              stringResource(
                Res.string.wizard_care_excluded_from_aggregates,
                organizationName,
              ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        TextButton(
          onClick = onShowExplainer,
          contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.wizard_care_learn_more))
        }
      }
      conformity.criteria.forEach { criterion ->
        val requester = rowRequesters.getOrPut(criterion.code) { BringIntoViewRequester() }
        CareCriterionRow(criterion, Modifier.bringIntoViewRequester(requester))
      }
    }
  }
}

/**
 * Compact, always-visible status of the CARE card while its summary row is scrolled away. Clicking
 * it expands the card and scrolls back to it.
 */
@Composable
fun CareStickyChip(
  conformity: CareConformity,
  position: CareCardPosition,
  scrollState: ScrollState,
  onShowCard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val criteria = conformity.criteria
  val metCount = conformity.metCount
  val allMet = metCount == criteria.size
  val scope = rememberCoroutineScope()
  val topMarginPx = with(LocalDensity.current) { STICKY_SCROLL_MARGIN.toPx() }
  val description = stringResource(Res.string.wizard_care_sticky_chip_a11y, metCount, criteria.size)

  AnimatedVisibility(
    visible = position.isSummaryScrolledAway,
    enter = fadeIn() + slideInVertically { -it },
    exit = fadeOut() + slideOutVertically { -it },
    modifier = modifier,
  ) {
    Surface(
      onClick = {
        onShowCard()
        val offset = position.cardTop - position.viewportTop - topMarginPx
        if (!offset.isNaN()) {
          scope.launch { scrollState.animateScrollTo(scrollState.value + offset.roundToInt()) }
        }
      },
      shape = CircleShape,
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      shadowElevation = 4.dp,
      modifier = Modifier.semantics { contentDescription = description },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
          text = stringResource(Res.string.wizard_care_sticky_chip, metCount, criteria.size),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Icon(
          if (allMet) Icons.Filled.CheckCircle else Icons.Outlined.Warning,
          contentDescription = null,
          tint = if (allMet) MaterialTheme.colorScheme.primary else AppStaticColors.Yellow,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

/** A criterion's code with a check (met) or warning (not met); its title shows on hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareStatusPip(criterion: CareGovernanceCriterion, onClick: () -> Unit) {
  val title = stringResource(criterion.title)
  val status =
    stringResource(
      if (criterion.isSatisfied) Res.string.wizard_care_criterion_met
      else Res.string.wizard_care_criterion_not_met
    )
  // Animated so a pip that changes state as the organizer edits Sharing settings draws the eye.
  val iconTint by
    animateColorAsState(
      if (criterion.isSatisfied) MaterialTheme.colorScheme.primary else AppStaticColors.Yellow
    )
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text("$title · $status") } },
    state = rememberTooltipState(),
  ) {
    Surface(
      onClick = onClick,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { contentDescription = "$title: $status" },
    ) {
      Row(
        modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          if (criterion.isSatisfied) Icons.Filled.CheckCircle else Icons.Outlined.Warning,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = criterion.code,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
private fun CareDefaultsAppliedBadge() {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
      Text(
        text = stringResource(Res.string.wizard_care_defaults_applied),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * One CARE / FPIC criterion: a check when the survey meets it or a warning when it doesn't, its
 * code, title, and source standard, and what it means for this survey in its current state.
 */
@Composable
private fun CareCriterionRow(criterion: CareGovernanceCriterion, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector =
          if (criterion.isSatisfied) Icons.Filled.CheckCircle else Icons.Outlined.Warning,
        contentDescription =
          stringResource(
            if (criterion.isSatisfied) Res.string.wizard_care_criterion_met
            else Res.string.wizard_care_criterion_not_met
          ),
        tint =
          if (criterion.isSatisfied) MaterialTheme.colorScheme.primary else AppStaticColors.Yellow,
        modifier = Modifier.padding(top = 1.dp).size(18.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${criterion.code} · ${stringResource(criterion.title)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(criterion.standard),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = stringResource(criterion.description),
          style = MaterialTheme.typography.bodySmall,
          color =
            if (criterion.isSatisfied) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/** Space left above the CARE card when the sticky chip scrolls back to it. */
private val STICKY_SCROLL_MARGIN = 16.dp
