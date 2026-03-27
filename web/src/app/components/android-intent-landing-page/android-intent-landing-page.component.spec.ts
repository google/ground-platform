/**
 * Copyright 2025 The Ground Authors.
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

import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {of} from 'rxjs';

import {AppConfigService} from 'app/services/app-config/app-config.service';
import {NavigationService} from 'app/services/navigation/navigation.service';

import {AndroidIntentLandingPageComponent} from './android-intent-landing-page.component';

describe('AndroidIntentLandingPageComponent', () => {
  let component: AndroidIntentLandingPageComponent;
  let fixture: ComponentFixture<AndroidIntentLandingPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AndroidIntentLandingPageComponent],
      providers: [
        {
          provide: AppConfigService,
          useValue: {getGooglePlayId: () => of('com.google.android.ground')},
        },
        {
          provide: NavigationService,
          useValue: {getHost: () => 'example.com'},
        },
        {provide: Router, useValue: {url: '/survey/123'}},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AndroidIntentLandingPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should set isAndroid to true on Android user agent', () => {
    spyOnProperty(window.navigator, 'userAgent').and.returnValue(
      'Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36'
    );
    // isAndroid/isIos are set synchronously at the start of ngOnInit, before
    // the async await that triggers the intent redirect. Checking here avoids
    // the window.location.href assignment that would reload the test runner.
    fixture.detectChanges();
    expect(component.isAndroid).toBeTrue();
    expect(component.isIos).toBeFalse();
  });

  it('should set isIos to true on iOS user agent', async () => {
    spyOnProperty(window.navigator, 'userAgent').and.returnValue(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)'
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.isIos).toBeTrue();
    expect(component.isAndroid).toBeFalse();
  });
});
