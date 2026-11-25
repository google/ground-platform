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
package org.openforis.collectone.v2.mobile.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.ui.common.CloudAcaciaLogo
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_CONTRIBUTORS_URL
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_COPYRIGHT_YEAR
import org.openforis.collectone.v2.app.ui.common.LocalMobileTopInset
import org.openforis.collectone.v2.app.ui.common.formatAppVersionBadge
import org.openforis.collectone.v2.app.ui.text.nameRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.LocalAppBrandFontFamily
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.NavigationDrawerItem
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_brand_organization
import org.openforis.collectone.v2.mobile.resources.mobile_brand_product
import org.openforis.collectone.v2.mobile.resources.mobile_close_drawer
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_active_survey
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_offline_maps
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_offline_maps_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_settings
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_settings_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_surveys
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_surveys_downloaded
import org.openforis.collectone.v2.mobile.resources.mobile_drawer_tos_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_terms_of_service_title
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_title
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * Hamburger Navigation Drawer overlay providing options to:
 * 1. Surveys (downloaded surveys screen with button to browse & download more surveys)
 * 2. Uploads
 * 3. Offline maps
 * 4. Change settings
 * 5. View Terms of Service
 *
 * User profile details and Sign out live in the top-right user avatar account menu. The footer
 * ([DrawerAboutFooter]) shows the app's version and links to its contributors.
 */
@Composable
internal fun MainSurveyNavigationDrawerOverlay(
  uiState: MobileDrawerUiState,
  actions: MobileDrawerActions,
) {
  val brandFont = LocalAppBrandFontFamily.current
  val organizationName = stringResource(Res.string.mobile_brand_organization)
  val productName = stringResource(Res.string.mobile_brand_product)
  Box(modifier = Modifier.fillMaxSize()) {
    // Scrim backdrop
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
          .backdropClickable { actions.updateDrawerOpen(false) }
    )

    // M3 ModalDrawerSheet Panel
    ModalDrawerSheet(
      modifier = Modifier.fillMaxHeight().width(308.dp),
      drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Brand Header with naturalistic Ground landscape background
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Box(modifier = Modifier.fillMaxWidth()) {
              DrawerHeaderBackground(
                isDarkTheme = uiState.isDarkTheme,
                scrimColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.matchParentSize(),
              )

              val topInset = LocalMobileTopInset.current
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(start = 18.dp, top = topInset + 20.dp, end = 18.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CloudAcaciaLogo(modifier = Modifier.size(30.dp))
                  Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                      text = organizationName.uppercase(),
                      style =
                        MaterialTheme.typography.labelSmall.copy(
                          fontSize = 9.sp,
                          lineHeight = 10.sp,
                          fontWeight = FontWeight.Bold,
                          letterSpacing = 1.1.sp,
                        ),
                      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                      text = productName,
                      style =
                        MaterialTheme.typography.titleLarge.copy(
                          fontFamily = brandFont,
                          fontWeight = FontWeight.ExtraBold,
                          letterSpacing = 0.5.sp,
                          lineHeight = 22.sp,
                        ),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
                IconButton(
                  onClick = { actions.updateDrawerOpen(false) },
                  modifier =
                    Modifier.size(32.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.mobile_close_drawer),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          // Active Survey Summary Banner
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = stringResource(Res.string.mobile_drawer_active_survey),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = uiState.activeSurveyTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Navigation Drawer Options using M3 NavigationDrawerItem
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            DrawerMenuItem(
              icon = Icons.Outlined.SwapHoriz,
              title = stringResource(Res.string.mobile_drawer_surveys),
              subtitle =
                pluralStringResource(
                  Res.plurals.mobile_drawer_surveys_downloaded,
                  uiState.downloadedSurveyCount,
                  uiState.downloadedSurveyCount,
                ),
              selected = uiState.activePage == MobileRoute.DownloadedSurveys,
              onClick = actions::drawerSwitchSurveys,
            )
            DrawerMenuItem(
              icon = Icons.Outlined.CloudUpload,
              title = stringResource(Res.string.mobile_uploads_title),
              subtitle = null,
              badgeText =
                if (uiState.pendingUploadCount > 0) "${uiState.pendingUploadCount}" else null,
              badgeTone = BadgeTone.WARNING,
              selected = uiState.activePage == MobileRoute.Uploads,
              onClick = actions::drawerOpenUploads,
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Map,
              title = stringResource(Res.string.mobile_drawer_offline_maps),
              subtitle = stringResource(Res.string.mobile_drawer_offline_maps_subtitle),
              selected = uiState.activePage == MobileRoute.OfflineMaps,
              onClick = actions::drawerManageOfflineMaps,
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Settings,
              title = stringResource(Res.string.mobile_drawer_settings),
              subtitle =
                stringResource(
                  Res.string.mobile_drawer_settings_subtitle,
                  stringResource(uiState.unitSystem.areaUnit.nameRes),
                ),
              selected = uiState.activePage == MobileRoute.Settings,
              onClick = actions::drawerOpenSettings,
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Description,
              title = stringResource(Res.string.mobile_terms_of_service_title),
              subtitle = stringResource(Res.string.mobile_drawer_tos_subtitle),
              selected = false,
              onClick = actions::drawerViewTermsOfService,
            )
          }

          DrawerAboutFooter(
            organizationName = organizationName,
            productName = productName,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    }
  }
}

/** Test tag of the navigation drawer's About footer ([DrawerAboutFooter]). */
internal const val DRAWER_ABOUT_FOOTER_TAG = "drawerAboutFooter"

/**
 * About information at the bottom of the navigation drawer: the product name with the app's
 * [appVersion], and the copyright line whose contributors link opens the project's source
 * repository.
 */
@Composable
internal fun DrawerAboutFooter(
  organizationName: String,
  productName: String,
  modifier: Modifier = Modifier,
  appVersion: String = AppBuildConfig.APP_VERSION,
) {
  val brandFont = LocalAppBrandFontFamily.current
  val versionLabel = formatAppVersionBadge(appVersion)
  val linkColor = MaterialTheme.colorScheme.primary
  Column(
    modifier = modifier.testTag(DRAWER_ABOUT_FOOTER_TAG),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text =
        buildAnnotatedString {
          append(organizationName)
          append(" ")
          withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.Bold)) {
            append(productName)
          }
          if (versionLabel.isNotEmpty()) {
            append(" ")
            append(versionLabel)
          }
        },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
internal fun DrawerMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  selected: Boolean,
  badgeText: String? = null,
  badgeTone: BadgeTone = BadgeTone.PRIMARY,
  isDestructive: Boolean = false,
  onClick: () -> Unit,
) {
  val itemColor =
    if (isDestructive) {
      MaterialTheme.colorScheme.error
    } else {
      MaterialTheme.colorScheme.onSurface
    }
  NavigationDrawerItem(
    label = {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = itemColor,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isDestructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    },
    badge = badgeText?.let { countText -> { TonalBadge(text = countText, tone = badgeTone) } },
    selected = selected,
    onClick = onClick,
    icon = {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = itemColor,
        modifier = Modifier.size(20.dp),
      )
    },
    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
  )
}
