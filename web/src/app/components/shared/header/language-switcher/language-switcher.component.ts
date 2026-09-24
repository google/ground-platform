/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { DOCUMENT } from '@angular/common';
import { Component, Inject, LOCALE_ID } from '@angular/core';

export interface Language {
  code: string;
  /** Name of the language written in the language itself. */
  nativeName: string;
}

/**
 * localStorage key of the language explicitly chosen by the user. Read by the
 * inline script in index.html, which must be kept in sync.
 */
export const LANGUAGE_STORAGE_KEY = 'ground.language';

/**
 * Languages the app is built for. Must match i18n locales in angular.json and
 * the list in index.html.
 */
export const LANGUAGES: Language[] = [
  { code: 'en', nativeName: 'English' },
  { code: 'es', nativeName: 'Español' },
  { code: 'fr', nativeName: 'Français' },
  { code: 'lo', nativeName: 'ລາວ' },
  { code: 'pt', nativeName: 'Português' },
  { code: 'th', nativeName: 'ไทย' },
  { code: 'vi', nativeName: 'Tiếng Việt' },
];

/**
 * Returns the given URL with its locale prefix replaced by (or, if absent,
 * prepended with) the given language code.
 */
export function getLanguageUrl(
  { pathname, search, hash }: Pick<Location, 'pathname' | 'search' | 'hash'>,
  code: string
): string {
  const segments = pathname.split('/');
  if (LANGUAGES.some(l => l.code === segments[1])) {
    segments[1] = code;
  } else {
    segments.splice(1, 0, code);
  }
  return segments.join('/') + search + hash;
}

/**
 * Lets the user switch the UI language.
 *
 * Translations are compiled into a separate bundle per locale, each served
 * under its own path prefix (e.g. /fr/surveys), so switching language requires
 * loading the page from the new prefix.
 */
@Component({
  selector: 'ground-language-switcher',
  templateUrl: './language-switcher.component.html',
  standalone: false,
})
export class LanguageSwitcherComponent {
  readonly languages = LANGUAGES;
  readonly currentLanguageCode: string;

  constructor(
    @Inject(LOCALE_ID) locale: string,
    @Inject(DOCUMENT) private document: Document
  ) {
    this.currentLanguageCode = locale.split('-')[0];
  }

  onLanguageClick(code: string): void {
    if (code === this.currentLanguageCode) return;
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, code);
    } catch {
      // Storage may be unavailable (e.g. blocked); the switch still works.
    }
    this.document.location.assign(getLanguageUrl(this.document.location, code));
  }
}
