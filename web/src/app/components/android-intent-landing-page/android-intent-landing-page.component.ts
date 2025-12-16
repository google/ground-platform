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

import { Component, Inject, LOCALE_ID, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AppConfigService } from 'app/services/app-config/app-config.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-android-landing-page',
  templateUrl: './android-intent-landing-page.component.html',
  styleUrls: ['./android-intent-landing-page.component.scss'],
})
export class AndroidIntentLandingPageComponent implements OnInit {
  googlePlayId$ = this.appConfigService.getGooglePlayId();
  getItOnGooglePlayImageSrc: string;
  isAndroid = false;
  isIos = false;

  constructor(
    @Inject(LOCALE_ID) public locale: string,
    private appConfigService: AppConfigService,
    private navigationService: NavigationService,
    private router: Router
  ) {
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

    this.isIos = true;

    if (this.isIos) return;

    const googlePlayId = await firstValueFrom(this.googlePlayId$);

    if (!googlePlayId) return;

    const host = this.navigationService.getHost();

    const path = this.router.url;

    const timeout = 5000;

    // Fallback: redirect to Google Play if app doesn't open
    const redirectTimeoutId = setTimeout(() => {
      // window.location.href = `https://play.google.com/store/apps/details?id=${googlePlayId}`;
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
