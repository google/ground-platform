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

actual fun platformUserDataServiceUrl(): String? =
  System.getProperty("groundplatform.userDataServiceUrl")?.takeIf { it.isNotBlank() }

actual fun userDataServiceSetupHint(): String =
  "Set USER_DATA_SERVICE_URL in local.properties, as a Gradle property, or in the environment " +
    "when building the app (for example http://10.0.2.2:8080 on the emulator)."
