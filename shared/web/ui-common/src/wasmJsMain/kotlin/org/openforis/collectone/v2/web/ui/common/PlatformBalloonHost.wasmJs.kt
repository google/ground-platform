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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.web.ui.common

import org.openforis.collectone.v2.app.domain.model.resolveBackendDebugBaseUrl

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.origin : '')")
private external fun jsLocationOrigin(): String

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.protocol : '')")
private external fun jsLocationProtocol(): String

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.hostname : '')")
private external fun jsLocationHostname(): String

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.port : '')")
private external fun jsLocationPort(): String

@JsFun(
  """() => {
    if (typeof window === 'undefined') return '';
    const url = window.COLLECT_ONE_USER_DATA_SERVICE_URL;
    return typeof url === 'string' ? url : '';
  }"""
)
private external fun jsUserDataServiceUrl(): String

@JsFun(
  """() => {
    if (typeof window === 'undefined') return '';
    const url = window.COLLECT_ONE_BACKEND_DEBUG_URL;
    return typeof url === 'string' ? url : '';
  }"""
)
private external fun jsBackendDebugUrl(): String

@JsFun(
  """(url) => {
    if (typeof window !== 'undefined' && typeof window.open === 'function') {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  }"""
)
private external fun jsOpenExternalUrl(url: String)

actual fun platformBalloonAppUrl(): String {
  val origin = jsLocationOrigin().trimEnd('/')
  return if (origin.isNotEmpty()) "$origin/balloon/balloon-form.html"
  else "/balloon/balloon-form.html"
}

actual fun platformServiceUrl(): String {
  val configured = jsUserDataServiceUrl().trim().trimEnd('/')
  return configured.ifEmpty { jsLocationOrigin().trimEnd('/') }
}

actual fun platformBackendDebugUrl(configuredServiceUrl: String?): String =
  resolveBackendDebugBaseUrl(
    configuredDebugUrl = jsBackendDebugUrl(),
    configuredServiceUrl = configuredServiceUrl?.takeIf { it.isNotBlank() } ?: platformServiceUrl(),
    browserProtocol = jsLocationProtocol(),
    browserHostname = jsLocationHostname(),
    browserPort = jsLocationPort(),
  )

actual fun openExternalUrl(url: String) {
  if (url.isNotBlank()) {
    jsOpenExternalUrl(url)
  }
}

actual fun openGoogleEarthDeepLink(earthUrl: String) {
  openExternalUrl(earthUrl)
}
