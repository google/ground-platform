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
package org.openforis.collectone.v2.app.ui.settings

import org.openforis.collectone.v2.app.domain.model.LANGUAGE_OPTIONS

/**
 * The language code to pass to `SettingsRepository` for what a language picker or test sent: a code
 * (`"fr"`), a native name (`"Français"`), or a formatted locale (`"fr (Français)"`), matched
 * case-insensitively against [LANGUAGE_OPTIONS]. Input that matches no option returns its first
 * word, lower-cased (which `SettingsRepository` ignores as unsupported), and blank input falls back
 * to English.
 */
internal fun languageCodeFrom(languageCodeOrLocale: String): String {
  val trimmed = languageCodeOrLocale.trim()
  val codeCandidate = trimmed.substringBefore(" ").lowercase()
  val matched = LANGUAGE_OPTIONS.firstOrNull {
    it.code.equals(trimmed, ignoreCase = true) ||
      it.code.equals(codeCandidate, ignoreCase = true) ||
      it.label.equals(trimmed, ignoreCase = true) ||
      "${it.code} (${it.label})".equals(trimmed, ignoreCase = true)
  }
  return matched?.code ?: codeCandidate.ifEmpty { "en" }
}
