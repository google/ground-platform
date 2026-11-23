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

/**
 * Absolute URL of the standalone KML balloon form runner (`/balloon/balloon-form.html`) served
 * alongside this Web Console build, or `""` outside a browser host.
 */
expect fun platformBalloonAppUrl(): String

/**
 * Base URL of the Collect One backend (`window.COLLECT_ONE_USER_DATA_SERVICE_URL` in browser
 * hosts), or `""` when unconfigured.
 */
expect fun platformServiceUrl(): String

/**
 * Base URL of the Collect One backend server for opening `/debug*` consoles, honoring the upstream
 * `window.COLLECT_ONE_BACKEND_DEBUG_URL` when a webpack dev server proxies gRPC-Web over the page's
 * own origin, or [configuredServiceUrl] / [platformServiceUrl] otherwise.
 */
expect fun platformBackendDebugUrl(configuredServiceUrl: String? = null): String

/** Opens [url] in a new browser tab (`_blank` with `noopener,noreferrer`). */
expect fun openExternalUrl(url: String)

/** Opens [earthUrl] in Google Earth Web in a new browser tab. */
expect fun openGoogleEarthDeepLink(earthUrl: String)
