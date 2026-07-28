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

import { CommonModule } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AppConfigService } from 'app/services/app-config/app-config.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

import { AndroidIntentLandingPageComponent } from './android-intent-landing-page.component';

describe('AndroidIntentLandingPageComponent', () => {
  let component: AndroidIntentLandingPageComponent;
  let fixture: ComponentFixture<AndroidIntentLandingPageComponent>;

  beforeEach(() => {
    const appConfigService = jasmine.createSpyObj('AppConfigService', [
      'getGooglePlayId',
    ]);
    appConfigService.getGooglePlayId.and.returnValue(of('org.ground.app'));

    const navigationService = jasmine.createSpyObj('NavigationService', [
      'getHost',
      'getPlayStoreUrl',
    ]);

    TestBed.configureTestingModule({
      declarations: [AndroidIntentLandingPageComponent],
      imports: [CommonModule],
      providers: [
        { provide: AppConfigService, useValue: appConfigService },
        { provide: NavigationService, useValue: navigationService },
        { provide: Router, useValue: { url: '' } },
        { provide: LOCALE_ID, useValue: 'en' },
      ],
    });

    // Note: `ngOnInit` navigates the window, so we deliberately create the
    // component without calling `detectChanges()` and test its helpers.
    fixture = TestBed.createComponent(AndroidIntentLandingPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('parseSurveyId', () => {
    const parseSurveyId = (path: string): string =>
      (component as any).parseSurveyId(path);

    it('extracts the survey id from an app link path', () => {
      expect(parseSurveyId('/android/survey/abc123')).toBe('abc123');
    });

    it('ignores query string and fragment', () => {
      expect(parseSurveyId('/android/survey/abc123?foo=bar#frag')).toBe(
        'abc123'
      );
    });

    it('returns empty string when there is no survey id', () => {
      expect(parseSurveyId('/android/survey')).toBe('');
      expect(parseSurveyId('/android')).toBe('');
    });
  });
});
