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

import { Component, Inject, LOCALE_ID, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AppConfigService } from 'app/services/app-config/app-config.service';
import { SURVEY_SEGMENT } from 'app/services/navigation/navigation.constants';
import { NavigationService } from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-android-landing-page',
  templateUrl: './android-intent-landing-page.component.html',
  styleUrls: ['./android-intent-landing-page.component.scss'],
  standalone: false,
})
export class AndroidIntentLandingPageComponent implements OnInit {
  private appConfigService = inject(AppConfigService);
  private navigationService = inject(NavigationService);
  private router = inject(Router);

  googlePlayId$ = this.appConfigService.getGooglePlayId();
  getItOnGooglePlayImageSrc: string;
  isAndroid = false;
  isIos = false;
  playStoreUrl = '';

  constructor(@Inject(LOCALE_ID) public locale: string) {
    const languageId = locale.split('-')[0];
    switch (languageId) {
      case 'es':
        this.getItOnGooglePlayImageSrc =
          'GetItOnGooglePlay_Badge_Web_color_Spanish.png';
        break;
      case 'fr':
        this.getItOnGooglePlayImageSrc =
          'GetItOnGooglePlay_Badge_Web_color_French.png';
        break;
      case 'pt':
        this.getItOnGooglePlayImageSrc =
          'GetItOnGooglePlay_Badge_Web_color_Portuguese-Portugal.png';
        break;
      case 'vi':
        this.getItOnGooglePlayImageSrc =
          'GetItOnGooglePlay_Badge_Web_color_Vietnamese.png';
        break;
      default:
        this.getItOnGooglePlayImageSrc =
          'assets/img/GetItOnGooglePlay_Badge_Web_color_English.png';
    }
  }

  /**
   * Extracts the survey id from an app link path of the form
   * `/android/survey/{surveyId}`, ignoring any query string or fragment.
   * Returns an empty string when no survey id is present.
   */
  private parseSurveyId(path: string): string {
    const segments = path.split(/[?#]/)[0].split('/');
    const surveyIndex = segments.indexOf(SURVEY_SEGMENT);

    return surveyIndex >= 0 ? (segments[surveyIndex + 1] ?? '') : '';
  }

  private isAndroidDevice(): boolean {
    const userAgent =
      window.navigator.userAgent || window.navigator.vendor || 'unknown';

    return /Android/i.test(userAgent);
  }

  private isIosDevice(): boolean {
    const userAgent =
      window.navigator.userAgent || window.navigator.vendor || 'unknown';

    return /iPad|iPhone|iPod/.test(userAgent) && !('MSStream' in window);
  }

  async ngOnInit(): Promise<void> {
    this.isAndroid = this.isAndroidDevice();

    this.isIos = this.isIosDevice();

    if (this.isIos) return;

    const googlePlayId = await firstValueFrom(this.googlePlayId$);

    if (!googlePlayId) return;

    const host = this.navigationService.getHost();

    const path = this.router.url;

    const surveyId = this.parseSurveyId(path);

    this.playStoreUrl = surveyId
      ? this.navigationService.getPlayStoreUrl(googlePlayId, surveyId)
      : `https://play.google.com/store/apps/details?id=${googlePlayId}`;

    const timeout = 5000;

    // Fallback: if the app didn't open, redirect to Google Play.
    const redirectTimeoutId = setTimeout(() => {
      window.location.href = this.playStoreUrl;
    }, timeout);

    // Try opening the app via intent URL
    window.location.href = `intent://${host}${path}#Intent;scheme=https;package=${googlePlayId};end`;

    // Cancel fallback if app is opened (browser loses focus)
    const blurHandler = () => {
      clearTimeout(redirectTimeoutId);
      window.removeEventListener('blur', blurHandler);
    };
    window.addEventListener('blur', blurHandler);
  }
}
