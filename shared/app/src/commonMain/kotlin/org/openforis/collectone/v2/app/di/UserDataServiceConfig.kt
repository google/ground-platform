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
package org.openforis.collectone.v2.app.di

/**
 * Base URL of the user data service (`server/userDataService`) configured for this platform, or
 * null when none is configured. There's no implicit default: the app entry points refuse to start
 * without one (see [requireUserDataServiceUrl]).
 *
 * - Web: `window.GROUND_USER_DATA_SERVICE_URL`. In development it's the page's own origin, because
 *   the dev server proxies the service configured by `USER_DATA_SERVICE_URL` (environment or
 *   `local.properties`); production bundles bake in an absolute URL. It can't be set from the page
 *   URL.
 * - JVM: the `groundplatform.userDataServiceUrl` system property or the `USER_DATA_SERVICE_URL`
 *   environment variable.
 * - Android: the `groundplatform.userDataServiceUrl` system property, which `androidApp` sets from
 *   its `USER_DATA_SERVICE_URL` build setting.
 */
expect fun platformUserDataServiceUrl(): String?

/** How to configure the user data service URL on this platform, for error messages. */
expect fun userDataServiceSetupHint(): String

/** Explains that no user data service is configured and how to configure one. */
fun missingUserDataServiceMessage(): String =
  "No user data service is configured, so the app can't load any data. " +
    userDataServiceSetupHint() +
    " See server/userDataService/README.md to run the service."

/** [platformUserDataServiceUrl], or throws with setup instructions when none is configured. */
fun requireUserDataServiceUrl(): String =
  platformUserDataServiceUrl() ?: throw IllegalStateException(missingUserDataServiceMessage())
