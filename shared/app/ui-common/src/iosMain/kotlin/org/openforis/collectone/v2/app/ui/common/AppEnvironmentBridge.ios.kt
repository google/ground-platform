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
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.openforis.collectone.v2.app.ui.common

import androidx.compose.runtime.Composable
import kotlin.native.Platform
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

@Composable
actual fun platformAppEnvironment(): AppEnvironment {
  val bundleEnv = NSBundle.mainBundle.objectForInfoDictionaryKey("APP_ENV") as? String
  val processEnv = NSProcessInfo.processInfo.environment["APP_ENV"] as? String
  AppEnvironment.parse(bundleEnv ?: processEnv)?.let {
    return it
  }
  return if (Platform.isDebugBinary) AppEnvironment.DEV else AppEnvironment.PROD
}
