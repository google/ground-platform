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
package org.groundplatform.v2.app.di

/**
 * `window.GROUND_USER_DATA_SERVICE_URL`, set by `user-data-service-config.js`: the page's own
 * origin when the dev server proxies the service, or the URL baked into a production bundle. The
 * service can't be chosen from the page URL, so a link can't point the app at another backend.
 */
@JsFun(
  """() => {
    if (typeof window === 'undefined') return null;
    const url = window.GROUND_USER_DATA_SERVICE_URL;
    return typeof url === 'string' ? url : null;
  }"""
)
private external fun jsConfiguredUrl(): String?

actual fun platformUserDataServiceUrl(): String? = jsConfiguredUrl()?.takeIf { it.isNotBlank() }

actual fun userDataServiceSetupHint(): String =
  "Set USER_DATA_SERVICE_URL in local.properties (or in the environment when starting the dev " +
    "server) to the service's address, for example http://localhost:8080, then reload the page."
