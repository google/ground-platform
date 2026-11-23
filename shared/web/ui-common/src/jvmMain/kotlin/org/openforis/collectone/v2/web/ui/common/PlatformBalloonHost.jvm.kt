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
package org.openforis.collectone.v2.web.ui.common

import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.domain.model.resolveBackendDebugBaseUrl
import org.openforis.collectone.v2.app.domain.model.resolveMobileWebUrl

actual fun platformBalloonAppUrl(): String = ""

actual fun platformServiceUrl(): String =
  System.getProperty("collectone.userDataServiceUrl")?.trim().orEmpty()

actual fun platformBackendDebugUrl(configuredServiceUrl: String?): String =
  resolveBackendDebugBaseUrl(
    configuredDebugUrl = null,
    configuredServiceUrl = configuredServiceUrl?.takeIf { it.isNotBlank() } ?: platformServiceUrl(),
  )

actual fun platformMobileWebUrl(environment: AppEnvironment): String? {
  val configured =
    System.getProperty("collectone.mobileWebUrl")?.trim()?.takeIf { it.isNotEmpty() }
      ?: System.getenv("MOBILE_WEB_URL")?.trim()?.takeIf { it.isNotEmpty() }
      ?: return null
  return resolveMobileWebUrl(environment = environment, configuredMobileWebUrl = configured)
}

/** Last external URL requested via [openExternalUrl] in JVM tests. */
var lastOpenedExternalUrlForTest: String? = null

actual fun openExternalUrl(url: String) {
  lastOpenedExternalUrlForTest = url
}

actual fun openGoogleEarthDeepLink(earthUrl: String) {
  openExternalUrl(earthUrl)
}
