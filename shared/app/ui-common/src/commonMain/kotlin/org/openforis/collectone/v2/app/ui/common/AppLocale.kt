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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Overrides the locale that Compose resources (`stringResource`, `pluralStringResource`) resolve
 * against, so the in-app language picker takes effect without changing the device or browser
 * language.
 */
expect object LocalAppLocale {
  /** The language tag currently in effect, e.g. `"fr"`. */
  val current: String
    @Composable get

  /** Provides [languageCode] as the app locale; `null` restores the platform default. */
  @Composable infix fun provides(languageCode: String?): ProvidedValue<*>
}

/**
 * Renders [content] with Compose resources resolved in [languageCode] (a BCP-47 language tag such
 * as `"fr"`); `null` or blank follows the platform locale. Changing the language recreates
 * [content] so every string is reloaded.
 */
@Composable
fun ProvideAppLocale(languageCode: String?, content: @Composable () -> Unit) {
  val code = languageCode?.takeIf { it.isNotBlank() }
  CompositionLocalProvider(LocalAppLocale provides code) { key(code) { content() } }
}
