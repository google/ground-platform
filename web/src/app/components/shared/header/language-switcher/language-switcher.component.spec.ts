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

import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatMenuModule } from '@angular/material/menu';

import {
  LANGUAGE_STORAGE_KEY,
  LanguageSwitcherComponent,
  getLanguageUrl,
} from './language-switcher.component';

describe('LanguageSwitcherComponent', () => {
  function createComponent(locale: string): LanguageSwitcherComponent {
    TestBed.configureTestingModule({
      imports: [MatMenuModule],
      declarations: [LanguageSwitcherComponent],
      providers: [{ provide: LOCALE_ID, useValue: locale }],
    });
    return TestBed.createComponent(LanguageSwitcherComponent).componentInstance;
  }

  it('should derive current language from locale', () => {
    expect(createComponent('en-US').currentLanguageCode).toBe('en');
  });

  it('should keep locale as is when it has no region', () => {
    expect(createComponent('fr').currentLanguageCode).toBe('fr');
  });

  describe('onLanguageClick', () => {
    let assignSpy: jasmine.Spy;
    let component: LanguageSwitcherComponent;

    beforeEach(() => {
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
      assignSpy = jasmine.createSpy('assign');
      const fakeDocument = {
        location: {
          pathname: '/es/survey/123',
          search: '',
          hash: '',
          assign: assignSpy,
        },
      } as unknown as Document;
      component = new LanguageSwitcherComponent('es', fakeDocument);
    });

    afterEach(() => localStorage.removeItem(LANGUAGE_STORAGE_KEY));

    it('should remember choice and navigate to new language', () => {
      component.onLanguageClick('pt');

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('pt');
      expect(assignSpy).toHaveBeenCalledWith('/pt/survey/123');
    });

    it('should do nothing when selecting current language', () => {
      component.onLanguageClick('es');

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBeNull();
      expect(assignSpy).not.toHaveBeenCalled();
    });
  });
});

describe('getLanguageUrl', () => {
  it('should replace locale prefix and keep path, query and hash', () => {
    expect(
      getLanguageUrl(
        { pathname: '/es/survey/123', search: '?a=1', hash: '#x' },
        'fr'
      )
    ).toBe('/fr/survey/123?a=1#x');
  });

  it('should replace locale prefix of root path', () => {
    expect(
      getLanguageUrl({ pathname: '/es/', search: '', hash: '' }, 'vi')
    ).toBe('/vi/');
  });

  it('should prepend locale when path has no locale prefix', () => {
    expect(
      getLanguageUrl({ pathname: '/survey/123', search: '', hash: '' }, 'th')
    ).toBe('/th/survey/123');
  });
});
