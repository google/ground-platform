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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.web.WebConsolePages
import org.groundplatform.v2.web.ui.WebPage
import org.groundplatform.v2.workbench.ui.MobileWorkbenchPage

/**
 * Root Compose Multiplatform Web application for `baobab/devtools/prototypeApp`.
 *
 * Renders an interactive UX design workbench that embeds a live Mobile or Tablet device preview of
 * the Ground 2.0 Compose Multiplatform UI (`Sign In` -> `Terms of Service` -> `Download survey` ->
 * `Main Survey UI`) and the web console ([WebConsolePages] from `shared/web`).
 *
 * [initialPage] is the web page to open, or `null` for the mobile device preview.
 */
@Composable
fun PrototypeApp(
  state: PrototypeAppState = remember { PrototypeAppState() },
  initialPage: WebPage? = null,
  initialOrganizationId: String? = null,
  onHashChanged: (String) -> Unit = {},
) {
  val web = state.web
  LaunchedEffect(initialPage, initialOrganizationId) {
    if (initialPage == WebPage.ORGANIZATION && initialOrganizationId != null) {
      web.openOrganization(initialOrganizationId)
    } else if (
      initialPage == WebPage.CREATE_SURVEY &&
        web.createSurveyWizard.uiState.value.workingSurveyId.isBlank()
    ) {
      web.openCreateSurveyWizard(initialOrganizationId)
    } else {
      state.showPage(initialPage)
    }
  }
  state.onPageChanged = { targetPage ->
    onHashChanged(PrototypeAppState.hashFor(targetPage, web.openOrganizationId))
  }
  val page = state.activeWebPage

  val isMobileMapShowing = page == null && state.mobile.isMapShowing
  // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay transparent
  // whenever a page shows it.
  val isMapShowing = isMobileMapShowing || (page == WebPage.WEB_DASHBOARD && web.isSignedIn)

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color =
        if (isMapShowing) {
          Color.Transparent
        } else {
          MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (page == null) {
          MobileWorkbenchPage(
            workbench = state.workbench,
            isMapShowing = isMobileMapShowing,
            onOpenWebApp = { state.showPage(WebPage.WEB_SURVEYS) },
          )
        } else {
          WebConsolePages(web, page)
        }
      }
    }
  }
}
