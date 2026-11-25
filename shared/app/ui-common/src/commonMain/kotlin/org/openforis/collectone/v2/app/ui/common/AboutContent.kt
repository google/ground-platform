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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_about_blurb
import org.openforis.collectone.v2.app.resources.app_about_product_name
import org.openforis.collectone.v2.app.resources.app_about_version
import org.openforis.collectone.v2.core.forms.ui.LocalAppBrandFontFamily

/** Test tag of [AboutContent]. */
const val ABOUT_CONTENT_TAG = "about_content"

/**
 * What the About screen of the mobile app and the About dialog of the web console show: the product
 * name with the app's [appVersion], what Collect One is for and its mission, and the attribution
 * line linking to the project's contributors ([ContributorsAttributionText]).
 *
 * A plain column of sections: add further sections as children here, and both apps show them.
 */
@Composable
fun AboutContent(modifier: Modifier = Modifier, appVersion: String = AppBuildConfig.APP_VERSION) {
  val version = appVersion.trim()
  Column(
    modifier = modifier.testTag(ABOUT_CONTENT_TAG),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CloudAcaciaLogo(modifier = Modifier.size(40.dp))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(Res.string.app_about_product_name),
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontFamily = LocalAppBrandFontFamily.current,
              fontWeight = FontWeight.Bold,
            ),
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (version.isNotEmpty()) {
          Text(
            text = stringResource(Res.string.app_about_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Text(
      text = stringResource(Res.string.app_about_blurb),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    ContributorsAttributionText()
  }
}
