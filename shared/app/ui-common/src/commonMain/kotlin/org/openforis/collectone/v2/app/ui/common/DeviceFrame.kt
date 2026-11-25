/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.ui.common

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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_device_airplane_mode
import org.openforis.collectone.v2.app.resources.app_device_disable_airplane_mode
import org.openforis.collectone.v2.app.resources.app_device_enable_airplane_mode
import org.openforis.collectone.v2.app.resources.app_device_form_factor_mobile
import org.openforis.collectone.v2.app.resources.app_device_form_factor_tablet
import org.openforis.collectone.v2.app.resources.app_device_frame_viewport
import org.openforis.collectone.v2.app.resources.app_device_orientation_landscape
import org.openforis.collectone.v2.app.resources.app_device_orientation_portrait
import org.openforis.collectone.v2.app.resources.app_device_rotate
import org.openforis.collectone.v2.app.resources.app_device_rotate_description
import org.openforis.collectone.v2.app.resources.app_device_switch_to_dark_mode
import org.openforis.collectone.v2.app.resources.app_device_switch_to_light_mode
import org.openforis.collectone.v2.app.resources.app_device_theme_dark
import org.openforis.collectone.v2.app.resources.app_device_theme_light
import org.openforis.collectone.v2.app.resources.app_device_walk_closer
import org.openforis.collectone.v2.app.resources.app_device_walk_closer_description
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface

/**
 * Top inset (status bar height) inside the mobile viewport when the status bar is overlaid
 * transparently on top of the screen content (such as on the main map screen). `0.dp` when the
 * status bar occupies its own opaque row above the screen content.
 */
val LocalMobileTopInset = compositionLocalOf { 0.dp }

/**
 * Mobile or tablet hardware device frame (bezel, status bar, and gesture bar) sized for
 * [formFactor] in [orientation], embedding [content] as the device screen under a [AppTheme] with
 * [isDarkTheme]. The screen background is transparent when [isScreenTransparent] (e.g. so a map
 * rendered behind the frame shows through). When [isTopInsetTransparent] is `true`, the top status
 * bar is overlaid with a semi-transparent scrim on top of [content] and its height is provided via
 * [LocalMobileTopInset].
 *
 * [statusBarColor] and [gestureBarColor] replace the frame's own bar colors, for a host whose
 * screens continue their containers behind the system bars; [Color.Transparent] lets [content] (or
 * what is behind the frame) show through.
 */
@Composable
fun DeviceFrame(
  isDarkTheme: Boolean,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  isScreenTransparent: Boolean = false,
  isTopInsetTransparent: Boolean = isScreenTransparent,
  statusBarColor: Color? = null,
  gestureBarColor: Color? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val frameWidthDp = formFactor.widthForOrientation(orientation)
  val frameHeightDp = formFactor.heightForOrientation(orientation)
  val density = LocalDensity.current
  var statusBarHeight by remember { mutableStateOf(28.dp) }

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
    AppTheme(darkTheme = isDarkTheme) {
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
        // Screen viewport + Overlaid or Stacked Device Status Bar
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          val topInsetForContent = if (isTopInsetTransparent) statusBarHeight else 0.dp
          val contentTopPadding = if (isTopInsetTransparent) 0.dp else statusBarHeight
          CompositionLocalProvider(LocalMobileTopInset provides topInsetForContent) {
            Box(modifier = Modifier.fillMaxSize().padding(top = contentTopPadding)) { content() }
          }

          DeviceStatusBar(
            formFactor = formFactor,
            orientation = orientation,
            color = statusBarColor,
            isOverContent = isTopInsetTransparent,
            modifier =
              Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { size ->
                val measuredHeight = with(density) { size.height.toDp() }
                if (measuredHeight > 0.dp) {
                  statusBarHeight = measuredHeight
                }
              },
          )
        }

        DeviceGestureBar(
          formFactor = formFactor,
          orientation = orientation,
          color = gestureBarColor,
        )
      }
    }
  }
}

/**
 * The simulated status bar: clock, camera, and signal. [color] replaces its own color, which is a
 * scrim when it is [isOverContent] (laid over the screen) and opaque otherwise.
 */
@Composable
private fun DeviceStatusBar(
  formFactor: DeviceFormFactor,
  orientation: DeviceOrientation,
  color: Color?,
  isOverContent: Boolean,
  modifier: Modifier = Modifier,
) {
  val container =
    color
      ?: if (isOverContent) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
      }
  Surface(
    color = container,
    contentColor = contentColorFor(container).takeOrElse { MaterialTheme.colorScheme.onSurface },
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text =
          if (formFactor == DeviceFormFactor.TABLET || orientation == DeviceOrientation.LANDSCAPE) {
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
            Modifier.width(68.dp).height(11.dp).clip(CircleShape).background(Color(0xFF061B12))
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
}

/** The simulated gesture navigation bar. [color] replaces its own color. */
@Composable
private fun DeviceGestureBar(
  formFactor: DeviceFormFactor,
  orientation: DeviceOrientation,
  color: Color?,
) {
  Surface(
    color = color ?: MaterialTheme.colorScheme.surfaceContainer,
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
                formFactor == DeviceFormFactor.TABLET || orientation == DeviceOrientation.LANDSCAPE
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

private const val COPY_LINK_FEEDBACK_MS = 2000L

/**
 * [DeviceFrame] with a side card showing [deviceTitle] and the current viewport, plus preview
 * controls: a Mobile / Tablet switcher ([onSelectFormFactor]), rotate ([onRotateDevice]), a dark /
 * light theme toggle ([onToggleDarkTheme]), an airplane mode toggle ([onToggleAirplaneMode]), and
 * optional Android/iOS build link copy chips ([onCopyApkLink], [onCopyIosLink]).
 */
@Composable
fun DevicePreviewFrame(
  deviceTitle: String,
  isDarkTheme: Boolean,
  isScreenTransparent: Boolean = false,
  isTopInsetTransparent: Boolean = isScreenTransparent,
  statusBarColor: Color? = null,
  gestureBarColor: Color? = null,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  onSelectFormFactor: (DeviceFormFactor) -> Unit = {},
  onRotateDevice: () -> Unit = {},
  onToggleDarkTheme: () -> Unit = {},
  isAirplaneMode: Boolean = false,
  onToggleAirplaneMode: (() -> Unit)? = null,
  onWalkCloser: (() -> Unit)? = null,
  onCopyApkLink: (() -> Unit)? = null,
  onCopyIosLink: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
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
      isTopInsetTransparent = isTopInsetTransparent,
      statusBarColor = statusBarColor,
      gestureBarColor = gestureBarColor,
      content = content,
    )

    DevicePreviewControlsCard(
      deviceTitle = deviceTitle,
      isDarkTheme = isDarkTheme,
      formFactor = formFactor,
      orientation = orientation,
      onSelectFormFactor = onSelectFormFactor,
      onRotateDevice = onRotateDevice,
      onToggleDarkTheme = onToggleDarkTheme,
      isAirplaneMode = isAirplaneMode,
      onToggleAirplaneMode = onToggleAirplaneMode,
      onWalkCloser = onWalkCloser,
      onCopyApkLink = onCopyApkLink,
      onCopyIosLink = onCopyIosLink,
    )
  }
}

@Composable
private fun DevicePreviewControlsCard(
  deviceTitle: String,
  isDarkTheme: Boolean,
  formFactor: DeviceFormFactor,
  orientation: DeviceOrientation,
  onSelectFormFactor: (DeviceFormFactor) -> Unit,
  onRotateDevice: () -> Unit,
  onToggleDarkTheme: () -> Unit,
  isAirplaneMode: Boolean,
  onToggleAirplaneMode: (() -> Unit)?,
  onWalkCloser: (() -> Unit)?,
  onCopyApkLink: (() -> Unit)?,
  onCopyIosLink: (() -> Unit)?,
) {
  val dimensionsLabel = formFactor.dimensionsLabelForOrientation(orientation)
  val isRotated = orientation != formFactor.defaultOrientation

  ElevatedCard(
    modifier = Modifier.width(210.dp),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        DevicePreviewTitleBlock(
          deviceTitle = deviceTitle,
          formFactor = formFactor,
          orientation = orientation,
          dimensionsLabel = dimensionsLabel,
          modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
        )
        DeviceFormFactorSwitcher(
          formFactor = formFactor,
          onSelectFormFactor = onSelectFormFactor,
        )
        DevicePreviewControlChips(
          isRotated = isRotated,
          isDarkTheme = isDarkTheme,
          isAirplaneMode = isAirplaneMode,
          onRotateDevice = onRotateDevice,
          onToggleDarkTheme = onToggleDarkTheme,
          onToggleAirplaneMode = onToggleAirplaneMode,
          onCopyApkLink = onCopyApkLink,
          onCopyIosLink = onCopyIosLink,
          onWalkCloser = onWalkCloser,
        )
      }
      DemoBadge(modifier = Modifier.align(Alignment.TopEnd))
    }
  }
}

@Composable
private fun DevicePreviewTitleBlock(
  deviceTitle: String,
  formFactor: DeviceFormFactor,
  orientation: DeviceOrientation,
  dimensionsLabel: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
      text =
        stringResource(
          Res.string.app_device_frame_viewport,
          formFactor.displayLabel(),
          orientation.displayLabel(),
          dimensionsLabel,
        ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      style =
        MaterialTheme.typography.labelSmall.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
    )
  }
}

@Composable
private fun DeviceFormFactorSwitcher(
  formFactor: DeviceFormFactor,
  onSelectFormFactor: (DeviceFormFactor) -> Unit,
) {
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
            contentDescription = factor.displayLabel(),
            modifier = Modifier.size(14.dp),
          )
        },
        label = {
          Text(
            text = factor.displayLabel(),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelSmall,
          )
        },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevicePreviewControlChips(
  isRotated: Boolean,
  isDarkTheme: Boolean,
  isAirplaneMode: Boolean,
  onRotateDevice: () -> Unit,
  onToggleDarkTheme: () -> Unit,
  onToggleAirplaneMode: (() -> Unit)?,
  onCopyApkLink: (() -> Unit)?,
  onCopyIosLink: (() -> Unit)?,
  onWalkCloser: (() -> Unit)?,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    PreviewActionChip(
      selected = isRotated,
      icon = Icons.Outlined.ScreenRotation,
      contentDescription = stringResource(Res.string.app_device_rotate_description),
      label = stringResource(Res.string.app_device_rotate),
      onClick = onRotateDevice,
    )
    PreviewActionChip(
      selected = isDarkTheme,
      icon = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
      contentDescription =
        stringResource(
          if (isDarkTheme) Res.string.app_device_switch_to_light_mode
          else Res.string.app_device_switch_to_dark_mode
        ),
      label =
        stringResource(
          if (isDarkTheme) Res.string.app_device_theme_dark else Res.string.app_device_theme_light
        ),
      onClick = onToggleDarkTheme,
    )
    if (onToggleAirplaneMode != null) {
      PreviewActionChip(
        selected = isAirplaneMode,
        icon =
          if (isAirplaneMode) Icons.Outlined.AirplanemodeActive
          else Icons.Outlined.AirplanemodeInactive,
        contentDescription =
          stringResource(
            if (isAirplaneMode) Res.string.app_device_disable_airplane_mode
            else Res.string.app_device_enable_airplane_mode
          ),
        label = stringResource(Res.string.app_device_airplane_mode),
        onClick = onToggleAirplaneMode,
      )
    }
    if (onCopyApkLink != null) {
      CopyDownloadLinkChip(
        label = "Copy APK Link",
        icon = Icons.Outlined.Android,
        onCopyLink = onCopyApkLink,
      )
    }
    if (onCopyIosLink != null) {
      CopyDownloadLinkChip(
        label = "Copy iOS Link",
        icon = Icons.Outlined.PhoneIphone,
        onCopyLink = onCopyIosLink,
      )
    }
    if (onWalkCloser != null) {
      PreviewActionChip(
        selected = false,
        icon = Icons.Outlined.Explore,
        contentDescription = stringResource(Res.string.app_device_walk_closer_description),
        label = stringResource(Res.string.app_device_walk_closer),
        onClick = onWalkCloser,
      )
    }
  }
}

@Composable
private fun PreviewActionChip(
  selected: Boolean,
  icon: ImageVector,
  contentDescription: String,
  label: String,
  onClick: () -> Unit,
) {
  FlatFilterChip(
    selected = selected,
    onClick = onClick,
    leadingIcon = {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(14.dp),
      )
    },
    label = {
      Text(
        text = label,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.labelSmall,
      )
    },
  )
}

@Composable
private fun CopyDownloadLinkChip(
  label: String,
  icon: ImageVector,
  onCopyLink: () -> Unit,
) {
  var showCopied by remember { mutableStateOf(false) }
  LaunchedEffect(showCopied) {
    if (showCopied) {
      delay(COPY_LINK_FEEDBACK_MS)
      showCopied = false
    }
  }
  PreviewActionChip(
    selected = false,
    icon = if (showCopied) Icons.Outlined.Check else icon,
    contentDescription = label,
    label = if (showCopied) "Copied Link" else label,
    onClick = {
      onCopyLink()
      showCopied = true
    },
  )
}

/** Localized display label of this form factor. */
@Composable
private fun DeviceFormFactor.displayLabel(): String =
  stringResource(
    when (this) {
      DeviceFormFactor.MOBILE -> Res.string.app_device_form_factor_mobile
      DeviceFormFactor.TABLET -> Res.string.app_device_form_factor_tablet
    }
  )

/** Localized display label of this orientation. */
@Composable
private fun DeviceOrientation.displayLabel(): String =
  stringResource(
    when (this) {
      DeviceOrientation.PORTRAIT -> Res.string.app_device_orientation_portrait
      DeviceOrientation.LANDSCAPE -> Res.string.app_device_orientation_landscape
    }
  )
