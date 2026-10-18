/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.AirplanemodeInactive
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.core.forms.ui.GroundTheme

/**
 * Mobile or tablet hardware device frame (bezel, status bar, and gesture bar) sized for
 * [formFactor] in [orientation], embedding [content] as the device screen under a [GroundTheme]
 * with [isDarkTheme]. The screen background is transparent when [isScreenTransparent] (e.g. so a
 * map rendered behind the frame shows through).
 */
@Composable
fun DeviceFrame(
  isDarkTheme: Boolean,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  isScreenTransparent: Boolean = false,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val frameWidthDp = formFactor.widthForOrientation(orientation)
  val frameHeightDp = formFactor.heightForOrientation(orientation)

  // Outer Device Hardware Bezel (dynamically sized for Mobile vs Tablet and Portrait vs
  // Landscape)
  val outerShape = RoundedCornerShape(formFactor.outerCornerRadiusDp.dp)
  val innerShape = RoundedCornerShape(formFactor.innerCornerRadiusDp.dp)
  Box(
    modifier =
      modifier
        .width(frameWidthDp.dp)
        .height(frameHeightDp.dp)
        .clip(outerShape)
        .background(Color(0xFF111827))
        .border(2.dp, Color(0xFF374151), outerShape)
        .padding(if (formFactor == DeviceFormFactor.TABLET) 12.dp else 10.dp)
  ) {
    GroundTheme(darkTheme = isDarkTheme) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .clip(innerShape)
            .background(
              if (isScreenTransparent) {
                Color.Transparent
              } else {
                MaterialTheme.colorScheme.surface
              }
            )
      ) {
        // Device Status Bar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (
                  formFactor == DeviceFormFactor.TABLET ||
                    orientation == DeviceOrientation.LANDSCAPE
                ) {
                  "09:41 • Wed Sep 19"
                } else {
                  "09:41"
                },
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
            )
            // Camera punch-hole notch (Mobile) or slim landscape bezel sensor (Tablet)
            if (formFactor == DeviceFormFactor.MOBILE) {
              Box(
                modifier =
                  Modifier.width(68.dp)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF061B12))
              )
            } else {
              Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF061B12)))
            }
            Text(
              text = "5G • 100%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        // Embedded Compose Multiplatform Screen Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

        // Bottom Gesture Navigation Bar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier.width(
                    if (
                      formFactor == DeviceFormFactor.TABLET ||
                        orientation == DeviceOrientation.LANDSCAPE
                    ) {
                      160.dp
                    } else {
                      116.dp
                    }
                  )
                  .height(4.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.outline)
            )
          }
        }
      }
    }
  }
}

/**
 * [DeviceFrame] with a side card showing [deviceTitle] and the current viewport, plus preview
 * controls: a Mobile / Tablet switcher ([onSelectFormFactor]), rotate ([onRotateDevice]), a dark /
 * light theme toggle ([onToggleDarkTheme]), and an airplane mode toggle ([onToggleAirplaneMode]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicePreviewFrame(
  deviceTitle: String,
  isDarkTheme: Boolean,
  isScreenTransparent: Boolean = false,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  onSelectFormFactor: (DeviceFormFactor) -> Unit = {},
  onRotateDevice: () -> Unit = {},
  onToggleDarkTheme: () -> Unit = {},
  isAirplaneMode: Boolean = false,
  onToggleAirplaneMode: () -> Unit = {},
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val dimensionsLabel = formFactor.dimensionsLabelForOrientation(orientation)
  val isRotated = orientation != formFactor.defaultOrientation

  val titleBlock: @Composable (Modifier) -> Unit = { titleModifier ->
    Column(modifier = titleModifier) {
      if (deviceTitle.isNotBlank()) {
        Text(
          text = deviceTitle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Viewport: ${formFactor.label} • ${orientation.label} ($dimensionsLabel)",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          ),
      )
    }
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    DeviceFrame(
      isDarkTheme = isDarkTheme,
      formFactor = formFactor,
      orientation = orientation,
      isScreenTransparent = isScreenTransparent,
      content = content,
    )

    // Side Controls Card with Device Title + Mobile / Tablet Form Factor Toggle + Rotate +
    // Dark / Light Theme Toggle + Airplane Mode Toggle
    ElevatedCard(
      modifier = Modifier.width(210.dp),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        titleBlock(Modifier.fillMaxWidth())

        // Segmented Form Factor Switcher
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          DeviceFormFactor.entries.forEachIndexed { index, factor ->
            val isSelected = formFactor == factor
            val icon =
              if (factor == DeviceFormFactor.MOBILE) {
                Icons.Outlined.Smartphone
              } else {
                Icons.Outlined.Tablet
              }
            SegmentedButton(
              selected = isSelected,
              onClick = { onSelectFormFactor(factor) },
              shape =
                SegmentedButtonDefaults.itemShape(
                  index = index,
                  count = DeviceFormFactor.entries.size,
                ),
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = factor.label,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = factor.label,
                  maxLines = 1,
                  softWrap = false,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
            )
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          itemVerticalAlignment = Alignment.CenterVertically,
        ) {
          // Rotate Device Widget
          GroundFilterChip(
            selected = isRotated,
            onClick = { onRotateDevice() },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.ScreenRotation,
                contentDescription = "Rotate device",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = "Rotate",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )

          // Dark / Light mode toggle for the mobile app preview
          GroundFilterChip(
            selected = isDarkTheme,
            onClick = { onToggleDarkTheme() },
            leadingIcon = {
              Icon(
                imageVector =
                  if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription =
                  if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = if (isDarkTheme) "Dark" else "Light",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )

          // Airplane mode (offline simulation) toggle
          GroundFilterChip(
            selected = isAirplaneMode,
            onClick = { onToggleAirplaneMode() },
            leadingIcon = {
              Icon(
                imageVector =
                  if (isAirplaneMode) {
                    Icons.Outlined.AirplanemodeActive
                  } else {
                    Icons.Outlined.AirplanemodeInactive
                  },
                contentDescription =
                  if (isAirplaneMode) "Disable airplane mode" else "Enable airplane mode",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = "Airplane mode",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )
        }
      }
    }
  }
}
