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

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.openforis.collectone.v2.app.domain.model.AppEnvironment

@Composable
actual fun platformAppEnvironment(): AppEnvironment {
  val explicit = System.getProperty("collectone.environment") ?: System.getenv("APP_ENV")
  AppEnvironment.parse(explicit)?.let {
    return it
  }
  val context = LocalContext.current
  val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
  return if (isDebuggable) AppEnvironment.DEV else AppEnvironment.PROD
}
