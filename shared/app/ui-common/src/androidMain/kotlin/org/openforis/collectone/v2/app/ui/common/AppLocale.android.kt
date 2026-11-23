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

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
  // Captured before the first Locale.setDefault override so provides(null) can restore the device's
  // original locale rather than reading back the overridden default.
  private var platformDefault: Locale? = null

  actual val current: String
    @Composable get() = Locale.getDefault().toLanguageTag()

  @Composable
  @Suppress("DEPRECATION") // Resources.updateConfiguration: resolves resources app-wide in place.
  actual infix fun provides(languageCode: String?): ProvidedValue<*> {
    val default = platformDefault ?: Locale.getDefault().also { platformDefault = it }
    val locale = languageCode?.let(Locale::forLanguageTag) ?: default
    Locale.setDefault(locale)
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    val resources = LocalContext.current.resources
    resources.updateConfiguration(configuration, resources.displayMetrics)
    return LocalConfiguration provides configuration
  }
}
