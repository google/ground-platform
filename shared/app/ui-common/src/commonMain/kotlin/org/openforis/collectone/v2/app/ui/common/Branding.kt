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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable

/** Label displayed on the demo ribbon across all Mobile and Web pages. */
const val DEMO_BADGE_TEXT = "DEMO"

/** Label displayed on UI components that are only visible to platform administrators. */
const val ADMIN_ONLY_BADGE_TEXT = "Admin only"

/** Copyright year displayed in the attribution line ([ContributorsAttributionText]). */
const val DEMO_ATTRIBUTION_COPYRIGHT_YEAR = 2026

/** Contributors label displayed and linked in the attribution line. */
const val DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL = "The Collect One Contributors"

/** Contributors graph URL linked from the attribution line. */
const val DEMO_ATTRIBUTION_CONTRIBUTORS_URL =
  "https://github.com/google/ground-platform/graphs/contributors?all=1"

/** Attribution line shown on the About screen of the mobile app and the web console. */
const val DEMO_ATTRIBUTION_FOOTER_TEXT =
  "© $DEMO_ATTRIBUTION_COPYRIGHT_YEAR $DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL"

/** Label of the debugging tools trigger in the non-production web footer. */
const val DEV_TOOLS_FOOTER_LABEL = "Debugging tools"

/** Test tag on the debugging tools trigger in [DevToolsFooterBar]. */
const val DEV_TOOLS_FOOTER_BUTTON_TAG = "dev_tools_footer_button"

/**
 * Formats an app [version] string for compact display as a badge (e.g., `"2.0.0-SNAPSHOT"` →
 * `"v2.0.0-SNAPSHOT"`), preserving an existing `v`/`V` prefix.
 */
fun formatAppVersionBadge(version: String): String {
  val trimmed = version.trim()
  if (trimmed.isEmpty()) return ""
  return if (trimmed.first().equals('v', ignoreCase = true)) trimmed else "v$trimmed"
}

/**
 * Diagonal corner ribbon displaying [DEMO_BADGE_TEXT] on the right side of the toolbar, indicating
 * that the app is running as a demo build.
 */
@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.size(56.dp).clipToBounds(),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.offset(x = 9.dp, y = (-9).dp)
          .requiredWidth(84.dp)
          .height(15.dp)
          .rotate(45f)
          .background(Color(0xFFD97706)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = DEMO_BADGE_TEXT,
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.0.sp,
          ),
        color = Color.White,
        maxLines = 1,
      )
    }
  }
}

/**
 * Compact admin shield icon flagging UI components that are only available to platform
 * administrators, making it clear not all users can see them.
 */
@Composable
fun AdminOnlyBadge(modifier: Modifier = Modifier) {
  Icon(
    imageVector = Icons.Outlined.AdminPanelSettings,
    contentDescription = ADMIN_ONLY_BADGE_TEXT,
    tint = MaterialTheme.colorScheme.tertiary,
    modifier = modifier.size(18.dp),
  )
}

/**
 * The attribution line [DEMO_ATTRIBUTION_FOOTER_TEXT], whose contributors label links to the
 * project's contributors ([DEMO_ATTRIBUTION_CONTRIBUTORS_URL]).
 */
@Composable
fun ContributorsAttributionText(
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.bodySmall,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val linkColor = MaterialTheme.colorScheme.primary
  Text(
    text =
      buildAnnotatedString {
        append("© $DEMO_ATTRIBUTION_COPYRIGHT_YEAR ")
        withLink(
          LinkAnnotation.Url(
            url = DEMO_ATTRIBUTION_CONTRIBUTORS_URL,
            styles =
              TextLinkStyles(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
              ),
          )
        ) {
          append(DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL)
        }
      },
    modifier = modifier,
    style = style,
    color = color,
  )
}

/**
 * Slim bottom footer bar of the web console with the [onOpenDevTools] trigger (a bug icon and
 * [DEV_TOOLS_FOOTER_LABEL]).
 *
 * Only rendered in non-production builds ([AppEnvironment.DEV] and [AppEnvironment.STAGING]);
 * hidden in [AppEnvironment.PROD]. The app's version and attribution are on the About screen
 * ([AboutContent]).
 */
@Composable
fun DevToolsFooterBar(
  onOpenDevTools: () -> Unit,
  modifier: Modifier = Modifier,
  environment: AppEnvironment = currentAppEnvironment(),
) {
  if (!environment.isNonProduction) return
  val tint = MaterialTheme.colorScheme.primary
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 18.dp).padding(horizontal = 12.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier =
          Modifier.testTag(DEV_TOOLS_FOOTER_BUTTON_TAG)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClick = onOpenDevTools),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.BugReport,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = DEV_TOOLS_FOOTER_LABEL,
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              lineHeight = 12.sp,
              fontWeight = FontWeight.Medium,
            ),
          color = tint,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Renders the Collect One World Lens Badge "Windswept Savanna & Tapered-Wingtip Base" vector logo
 * (`shared/assets/logo.svg` • Variant 1C-4 • `viewBox="0 0 512 512"`).
 */
@Composable
fun CloudAcaciaLogo(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 512f
    fun sx(x: Float) = x * s
    fun sy(y: Float) = y * s

    val outerLensRing = Color(0xFF2C536E)
    val innerSageSky = Color(0xFFAEC66B)
    val savannaSunAmber = Color(0xFFFFE082)
    val darkSilhouette = Color(0xFF14261E)

    val aboveGroundClipPath =
      Path().apply {
        moveTo(sx(0f), sy(0f))
        lineTo(sx(512f), sy(0f))
        lineTo(sx(512f), sy(436f))
        cubicTo(sx(340f), sy(398f), sx(172f), sy(402f), sx(0f), sy(444f))
        close()
      }

    val lensClipPath =
      Path().apply {
        addOval(
          Rect(
            left = sx(40f),
            top = sy(40f),
            right = sx(472f),
            bottom = sy(472f),
          )
        )
      }

    clipPath(aboveGroundClipPath) {
      // 1. Outer World Lens Ring (#2C536E, cx=256, cy=256, r=236)
      drawCircle(
        color = outerLensRing,
        radius = sx(236f),
        center = Offset(sx(256f), sy(256f)),
      )

      clipPath(lensClipPath) {
        // 2. Inner Green Sky Lens (#AEC66B, cx=256, cy=256, r=216)
        drawCircle(
          color = innerSageSky,
          radius = sx(216f),
          center = Offset(sx(256f), sy(256f)),
        )

        // 3. Soft Savanna Sun-Amber Disc (#FFE082, cx=256, cy=288, r=142)
        drawCircle(
          color = savannaSunAmber,
          radius = sx(142f),
          center = Offset(sx(256f), sy(288f)),
        )

        // 4. Silhouette (#14261E): Windswept Umbrella Acacia Upper Crown
        val upperCrownPath =
          Path().apply {
            moveTo(sx(94f), sy(186f))
            cubicTo(sx(88f), sy(168f), sx(120f), sy(122f), sx(180f), sy(126f))
            cubicTo(sx(208f), sy(94f), sx(272f), sy(90f), sx(310f), sy(122f))
            cubicTo(sx(354f), sy(110f), sx(416f), sy(134f), sx(430f), sy(174f))
            cubicTo(sx(434f), sy(186f), sx(420f), sy(194f), sx(394f), sy(192f))
            cubicTo(sx(330f), sy(184f), sx(206f), sy(186f), sx(124f), sy(194f))
            cubicTo(sx(104f), sy(196f), sx(96f), sy(194f), sx(94f), sy(186f))
            close()
          }
        drawPath(path = upperCrownPath, color = darkSilhouette)

        // 5. Silhouette (#14261E): Windswept Lower-Left Cloud Bough
        val lowerLeftBoughPath =
          Path().apply {
            moveTo(sx(68f), sy(260f))
            cubicTo(sx(64f), sy(242f), sx(92f), sy(212f), sx(136f), sy(216f))
            cubicTo(sx(172f), sy(208f), sx(210f), sy(226f), sx(218f), sy(250f))
            cubicTo(sx(222f), sy(264f), sx(206f), sy(272f), sx(184f), sy(270f))
            cubicTo(sx(152f), sy(266f), sx(116f), sy(268f), sx(88f), sy(272f))
            cubicTo(sx(74f), sy(274f), sx(70f), sy(268f), sx(68f), sy(260f))
            close()
          }
        drawPath(path = lowerLeftBoughPath, color = darkSilhouette)

        // 6. Silhouette (#14261E): Windswept S-Curve Trunk & Crescent Left Branch
        val trunkPath =
          Path().apply {
            moveTo(sx(200f), sy(412f))
            cubicTo(sx(222f), sy(376f), sx(235f), sy(340f), sx(240f), sy(308f))
            cubicTo(sx(200f), sy(302f), sx(164f), sy(288f), sx(138f), sy(264f))
            lineTo(sx(154f), sy(264f))
            cubicTo(sx(178f), sy(280f), sx(212f), sy(286f), sx(246f), sy(286f))
            cubicTo(sx(254f), sy(250f), sx(266f), sy(214f), sx(278f), sy(182f))
            lineTo(sx(296f), sy(182f))
            cubicTo(sx(282f), sy(218f), sx(273f), sy(256f), sx(271f), sy(298f))
            cubicTo(sx(269f), sy(340f), sx(278f), sy(376f), sx(300f), sy(412f))
            close()
          }
        drawPath(path = trunkPath, color = darkSilhouette)
      }
    }

    // 7. Silhouette (#14261E): Tapered-Wingtip Wide Savanna Base Arch (x=16..496)
    val baseArchPath =
      Path().apply {
        moveTo(sx(16f), sy(458f))
        cubicTo(sx(16f), sy(442f), sx(156f), sy(384f), sx(256f), sy(382f))
        cubicTo(sx(356f), sy(380f), sx(496f), sy(434f), sx(496f), sy(450f))
        cubicTo(sx(496f), sy(464f), sx(472f), sy(468f), sx(436f), sy(462f))
        cubicTo(sx(340f), sy(444f), sx(172f), sy(448f), sx(76f), sy(468f))
        cubicTo(sx(40f), sy(474f), sx(16f), sy(470f), sx(16f), sy(458f))
        close()
      }
    drawPath(path = baseArchPath, color = darkSilhouette)

    // 8. Inner Green Tone (#AEC66B): Tapered Horizontal Accent Line
    val horizonAccentPath =
      Path().apply {
        moveTo(sx(96f), sy(446f))
        cubicTo(sx(186f), sy(408f), sx(322f), sy(404f), sx(412f), sy(438f))
        cubicTo(sx(322f), sy(420f), sx(188f), sy(424f), sx(96f), sy(446f))
        close()
      }
    drawPath(path = horizonAccentPath, color = innerSageSky)
  }
}
