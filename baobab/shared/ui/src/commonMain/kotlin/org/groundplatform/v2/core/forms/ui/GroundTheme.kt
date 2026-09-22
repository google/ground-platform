/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Complete Material 3 Light ColorScheme using the exact palette from the Ground 2.0 prototype:
 * - Forest greens: `#1E6F50` (primary), `#144532` (secondary / deep headers), `#133A29`
 *   (inverseSurface / workbench & map HUDs), `#1B5E20` (onPrimaryContainer)
 * - Mint & sage containers: `#E8F5E9` (primaryContainer), `#EFF6F2` (secondaryContainer), `#8BD6B1`
 *   (inversePrimary accent), `#F3F6F4` (background), `#F3F8F5` (surfaceContainerLow), `#EBF3EE`
 *   (surfaceContainer), `#F0F4F1` (surfaceContainerLowest), `#C8E0D4` (outlineVariant)
 * - Cyan / geospatial accents: `#00838F` (tertiary), `#E0F7FA` (tertiaryContainer), `#006064`
 *   (onTertiaryContainer)
 */
val GroundLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF1E6F50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF1B5E20),
    inversePrimary = Color(0xFF8BD6B1),
    secondary = Color(0xFF144532),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFF6F2),
    onSecondaryContainer = Color(0xFF1E6F50),
    tertiary = Color(0xFF00838F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F7FA),
    onTertiaryContainer = Color(0xFF006064),
    background = Color(0xFFF3F6F4),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEFF6F2),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceTint = Color(0xFF1E6F50),
    inverseSurface = Color(0xFF133A29),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFFB3261E),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFFC8E0D4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5ECE8),
    surfaceContainerLowest = Color(0xFFF0F4F1),
    surfaceContainerLow = Color(0xFFF3F8F5),
    surfaceContainer = Color(0xFFEBF3EE),
    surfaceContainerHigh = Color(0xFFE8F5E9),
    surfaceContainerHighest = Color(0xFFDDE5E0),
  )

/**
 * Complete Material 3 Dark ColorScheme using the exact dark-mode palette from the Ground 2.0
 * prototype:
 * - Mint & forest greens: `#8BD6B1` (primary), `#003825` (onPrimary), `#1E6F50` (primaryContainer),
 *   `#C8E6C9` (onPrimaryContainer), `#81C784` (secondary), `#26332D` (secondaryContainer)
 * - Dark surfaces: `#191C1A` (background), `#1E2522` (surface), `#1E2622` (surfaceContainerLow),
 *   `#222A26` (surfaceContainer), `#252E2A` (surfaceContainerHigh), `#2E3833`
 *   (surfaceContainerHighest), `#133A29` (inverseSurface)
 * - Borders & outlines: `#386B52` (outline), `#2D5944` (outlineVariant)
 */
val GroundDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8BD6B1),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF1E6F50),
    onPrimaryContainer = Color(0xFFC8E6C9),
    inversePrimary = Color(0xFF1E6F50),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF003825),
    secondaryContainer = Color(0xFF26332D),
    onSecondaryContainer = Color(0xFF8BD6B1),
    tertiary = Color(0xFF80DEEA),
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF112A32),
    onTertiaryContainer = Color(0xFF80DEEA),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1E2522),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF26332D),
    onSurfaceVariant = Color(0xFFA7C4B5),
    surfaceTint = Color(0xFF8BD6B1),
    inverseSurface = Color(0xFF133A29),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFEF5350),
    onError = Color(0xFF3E1F1F),
    errorContainer = Color(0xFF3E1F1F),
    onErrorContainer = Color(0xFFFFCDD2),
    outline = Color(0xFF386B52),
    outlineVariant = Color(0xFF2D5944),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E3833),
    surfaceDim = Color(0xFF121815),
    surfaceContainerLowest = Color(0xFF0E1512),
    surfaceContainerLow = Color(0xFF1E2622),
    surfaceContainer = Color(0xFF222A26),
    surfaceContainerHigh = Color(0xFF252E2A),
    surfaceContainerHighest = Color(0xFF2E3833),
  )

/** Standard Material Design 3 5-tier shape scale (`extraSmall` 4dp through `extraLarge` 28dp). */
val GroundShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/** Standard Material Design 3 15-role typography scale. */
val GroundTypography = Typography()

/** Semantic tonal color roles for M3 pill badges and status indicators. */
enum class GroundBadgeTone {
  PRIMARY,
  SECONDARY,
  TERTIARY,
  WARNING,
  ERROR,
  NEUTRAL,
}

/**
 * Reusable Material 3 tonal pill badge backed by `MaterialTheme.colorScheme` container roles.
 */
@Composable
fun GroundTonalBadge(
  text: String,
  tone: GroundBadgeTone = GroundBadgeTone.PRIMARY,
  outlined: Boolean = false,
  monospace: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val (containerColor, contentColor, borderColor) =
    when (tone) {
      GroundBadgeTone.PRIMARY ->
        Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
      GroundBadgeTone.SECONDARY ->
        Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
      GroundBadgeTone.TERTIARY ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      GroundBadgeTone.WARNING ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      GroundBadgeTone.ERROR ->
        Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
      GroundBadgeTone.NEUTRAL ->
        Triple(colors.surfaceContainerHigh, colors.onSurfaceVariant, colors.outlineVariant)
    }
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    border = if (outlined) BorderStroke(1.dp, borderColor) else null,
  ) {
    Text(
      text = text,
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
          fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        ),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

/**
 * Material 3 Modal Bottom Sheet rendered strictly within its parent container bounds (rather than a
 * root Window Popup), ensuring sheets stay inside `MobileDevicePreviewFrame` when running in the
 * Web UX Workbench.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundModalBottomSheetOverlay(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    // M3 Modal Scrim confined to the mobile preview frame
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
          .clickable(onClick = onDismissRequest)
    )
    // M3 Bottom Sheet Container
    Surface(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .clickable(enabled = false) {},
      shape = BottomSheetDefaults.ExpandedShape,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = BottomSheetDefaults.Elevation,
      shadowElevation = 12.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        BottomSheetDefaults.DragHandle()
        content()
      }
    }
  }
}

/**
 * Material 3 AlertDialog rendered strictly within its parent container bounds (rather than a root
 * Window Dialog), ensuring dialogs stay centered inside `MobileDevicePreviewFrame`.
 */
@Composable
fun GroundAlertDialogOverlay(
  onDismissRequest: () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  icon: (@Composable () -> Unit)? = null,
  dismissButton: (@Composable () -> Unit)? = null,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    // M3 Modal Scrim confined to the mobile preview frame
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
          .clickable(onClick = onDismissRequest)
    )
    // M3 Dialog Container
    Surface(
      modifier =
        Modifier.padding(horizontal = 24.dp)
          .widthIn(min = 280.dp, max = 340.dp)
          .clickable(enabled = false) {},
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (icon != null) {
          Box(modifier = Modifier.padding(bottom = 16.dp)) {
            icon()
          }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp)) {
          title()
        }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
          text()
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (dismissButton != null) {
            dismissButton()
            Spacer(modifier = Modifier.width(8.dp))
          }
          confirmButton()
        }
      }
    }
  }
}

/** Shared Ground 2.0 Material 3 theme for Mobile and Web surfaces. */
@Composable
fun GroundTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) GroundDarkColorScheme else GroundLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    shapes = GroundShapes,
    typography = GroundTypography,
    content = content,
  )
}

