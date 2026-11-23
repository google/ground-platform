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
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

actual object LocalAppLocale {
  // `navigator.languages` is patched once per page: the patch is global to the page, so the flag
  // that guards it is too.
  private var navigatorPatched = false
  private val LocalLocaleTag = staticCompositionLocalOf { Locale.current.toLanguageTag() }

  actual val current: String
    @Composable get() = LocalLocaleTag.current

  @Composable
  actual infix fun provides(languageCode: String?): ProvidedValue<*> {
    if (!navigatorPatched) {
      patchNavigatorLanguages()
      navigatorPatched = true
    }
    setCustomLocale(languageCode?.replace('_', '-'))
    return LocalLocaleTag provides Locale.current.toLanguageTag()
  }
}

/**
 * Sets the language that the patched `navigator.languages` reports; `null` restores the browser's.
 * A change fires the browser's `languagechange` event, which Compose listens to so that
 * `Locale.current` re-reads `navigator.languages` instead of keeping the language it read first.
 */
private fun setCustomLocale(tag: String?): Unit =
  js(
    """{
    if ((window.__customLocale || null) === tag) return;
    window.__customLocale = tag;
    window.dispatchEvent(new Event('languagechange'));
  }"""
  )

/**
 * Compose resources read the locale from `navigator.languages`. Wraps its getter so it reports
 * `window.__customLocale` when set.
 */
private fun patchNavigatorLanguages(): Unit =
  js(
    """{
    const original = Object.getOwnPropertyDescriptor(Navigator.prototype, 'languages');
    Object.defineProperty(Navigator.prototype, 'languages', Object.assign({}, original, {
      get: function () {
        return window.__customLocale ? [window.__customLocale] : original.get.apply(this);
      },
    }));
  }"""
  )
