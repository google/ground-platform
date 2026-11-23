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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_access_anyone
import org.openforis.collectone.v2.web.resources.surveyeditor_access_anyone_in_organization
import org.openforis.collectone.v2.web.resources.surveyeditor_access_collect_hint_anyone
import org.openforis.collectone.v2.web.resources.surveyeditor_access_collect_hint_invited
import org.openforis.collectone.v2.web.resources.surveyeditor_access_collect_hint_organization
import org.openforis.collectone.v2.web.resources.surveyeditor_access_collect_hint_organization_unset
import org.openforis.collectone.v2.web.resources.surveyeditor_access_contributors_and_organizers
import org.openforis.collectone.v2.web.resources.surveyeditor_access_invited_people
import org.openforis.collectone.v2.web.resources.surveyeditor_access_public_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_access_public_option
import org.openforis.collectone.v2.web.resources.surveyeditor_access_slider_stop_description
import org.openforis.collectone.v2.web.resources.surveyeditor_access_unlisted_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_access_unlisted_option
import org.openforis.collectone.v2.web.resources.surveyeditor_access_view_hint_anyone
import org.openforis.collectone.v2.web.resources.surveyeditor_access_view_hint_contributors
import org.openforis.collectone.v2.web.resources.surveyeditor_access_view_hint_invited
import org.openforis.collectone.v2.web.resources.surveyeditor_access_view_hint_organization
import org.openforis.collectone.v2.web.resources.surveyeditor_access_view_hint_organization_unset
import org.openforis.collectone.v2.web.resources.surveyeditor_access_who_can_collect
import org.openforis.collectone.v2.web.resources.surveyeditor_access_who_can_find
import org.openforis.collectone.v2.web.resources.surveyeditor_access_who_can_view
import org.openforis.collectone.v2.web.ui.common.DropdownSelector

private enum class AccessAudienceStop(val label: StringResource) {
  CONTRIBUTORS_AND_ORGANIZERS(Res.string.surveyeditor_access_contributors_and_organizers),
  INVITED_PEOPLE(Res.string.surveyeditor_access_invited_people),
  ANYONE_IN_ORGANIZATION(Res.string.surveyeditor_access_anyone_in_organization),
  ANYONE(Res.string.surveyeditor_access_anyone),
}

private val PermissionLabelWidth = 176.dp

/**
 * Google Groups-style stepped permission sliders for **Who can view data** and **Who can collect
 * data**, plus a **Who can find this survey** selector and share link / QR code card when the
 * survey is open to **Anyone**.
 */
@Composable
internal fun GeneralAccessPermissionSliders(
  sharing: SharingSettings,
  organization: Organization?,
  onUpdateSharing: ((SharingSettings) -> SharingSettings) -> Unit,
  surveyId: String = "",
  surveyTitle: String = "",
  enabled: Boolean = true,
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
      when (sharing.effectiveViewPolicy) {
        SharingPolicy.RESTRICTED -> AccessAudienceStop.INVITED_PEOPLE
        SharingPolicy.ORGANIZATION ->
          if (organization != null) AccessAudienceStop.ANYONE_IN_ORGANIZATION
          else AccessAudienceStop.INVITED_PEOPLE
        SharingPolicy.ANYONE_WITH_LINK,
        SharingPolicy.PUBLIC -> AccessAudienceStop.ANYONE
      }
    }
  val viewHint =
    when (viewStop) {
      AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS ->
        stringResource(Res.string.surveyeditor_access_view_hint_contributors)
      AccessAudienceStop.INVITED_PEOPLE ->
        stringResource(Res.string.surveyeditor_access_view_hint_invited)
      AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
        if (organization != null) {
          stringResource(Res.string.surveyeditor_access_view_hint_organization, organization.name)
        } else {
          stringResource(Res.string.surveyeditor_access_view_hint_organization_unset)
        }
      AccessAudienceStop.ANYONE -> stringResource(Res.string.surveyeditor_access_view_hint_anyone)
    }

  val collectHint =
    when (collectStop) {
      AccessAudienceStop.CONTRIBUTORS_AND_ORGANIZERS,
      AccessAudienceStop.INVITED_PEOPLE ->
        stringResource(Res.string.surveyeditor_access_collect_hint_invited)
      AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
        if (organization != null) {
          stringResource(
            Res.string.surveyeditor_access_collect_hint_organization,
            organization.name,
          )
        } else {
          stringResource(Res.string.surveyeditor_access_collect_hint_organization_unset)
        }
      AccessAudienceStop.ANYONE ->
        stringResource(Res.string.surveyeditor_access_collect_hint_anyone)
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
            text = stringResource(stop.label),
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
      title = stringResource(Res.string.surveyeditor_access_who_can_view),
      selectedStop = viewStop,
      stops = visibleStops,
      hint = viewHint,
      enabled = enabled,
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
              current.copy(
                viewPolicy = null,
                peerDataVisibility = PeerDataVisibility.OWN_ONLY,
              )
            AccessAudienceStop.INVITED_PEOPLE ->
              current.copy(
                viewPolicy = SharingPolicy.RESTRICTED,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
            AccessAudienceStop.ANYONE_IN_ORGANIZATION ->
              current.copy(
                viewPolicy = SharingPolicy.ORGANIZATION,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
            AccessAudienceStop.ANYONE ->
              current.copy(
                viewPolicy = openPolicy,
                peerDataVisibility = PeerDataVisibility.ALL,
              )
          }
        }
      },
    )

    PermissionRangeSliderRow(
      title = stringResource(Res.string.surveyeditor_access_who_can_collect),
      selectedStop = collectStop,
      stops = visibleStops,
      hint = collectHint,
      enabled = enabled,
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
                  if (wasOpenToAnyone || current.viewPolicy != null) current.peerDataVisibility
                  else PeerDataVisibility.OWN_ONLY,
              )
            }
          }
        }
      },
    )

    if (sharing.policy.isOpenToAnyone) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      val directoryOptions = listOf(SharingPolicy.PUBLIC, SharingPolicy.ANYONE_WITH_LINK)
      val unlistedLabel = stringResource(Res.string.surveyeditor_access_unlisted_option)
      val publicLabel = stringResource(Res.string.surveyeditor_access_public_option)
      val policyOptionLabel: (SharingPolicy) -> String = { policy ->
        if (policy == SharingPolicy.ANYONE_WITH_LINK) unlistedLabel else publicLabel
      }
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_access_who_can_find),
        selectedText = policyOptionLabel(sharing.policy),
        options = directoryOptions,
        optionText = { policyOptionLabel(it) },
        enabled = enabled,
        onSelect = { selectedPolicy -> onUpdateSharing { it.copy(policy = selectedPolicy) } },
      )
      Text(
        text =
          if (sharing.policy == SharingPolicy.ANYONE_WITH_LINK) {
            stringResource(Res.string.surveyeditor_access_unlisted_hint)
          } else {
            stringResource(Res.string.surveyeditor_access_public_hint)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (enabled) {
        SurveyShareLinkAndQrCard(
          surveyId = surveyId,
          surveyTitle = surveyTitle,
          isUnlisted = sharing.policy == SharingPolicy.ANYONE_WITH_LINK,
        )
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
  enabled: Boolean = true,
  onSelectStop: (AccessAudienceStop) -> Unit,
) {
  val selectedIndex = stops.indexOf(selectedStop).let { if (it >= 0) it else 0 }
  val contentAlpha = if (enabled) 1f else 0.45f
  val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
  val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha)
  val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha)
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f),
        modifier = Modifier.width(PermissionLabelWidth).padding(end = 12.dp),
      )
      Row(
        modifier =
          Modifier.weight(1f)
            .height(36.dp)
            .onSizeChanged { trackWidthPx = it.width }
            .then(
              if (!enabled) {
                Modifier
              } else {
                Modifier.pointerInput(stops.size) {
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
                }
              }
            ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        stops.forEachIndexed { index, stop ->
          val stopDescription =
            stringResource(
              Res.string.surveyeditor_access_slider_stop_description,
              title,
              stringResource(stop.label),
            )
          val isFilled = index <= selectedIndex
          val isSelected = index == selectedIndex
          val leftFilled = index > 0 && index <= selectedIndex
          val rightFilled = index < selectedIndex
          val stopInteractionSource = remember { MutableInteractionSource() }
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clickable(
                  enabled = enabled,
                  interactionSource = stopInteractionSource,
                  indication = null,
                  role = Role.RadioButton,
                  onClick = { currentOnSelectStop(stop) },
                )
                .semantics(mergeDescendants = true) {
                  contentDescription = stopDescription
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
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .indication(stopInteractionSource, LocalIndication.current),
              contentAlignment = Alignment.Center,
            ) {
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
    }
    Text(
      text = hint,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = PermissionLabelWidth),
    )
  }
}
