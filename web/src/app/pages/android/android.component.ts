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

import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';

import {AppConfigService} from 'app/services/app-config/app-config.service';

@Component({
  selector: 'ground-android-page',
  template: `
    <div *ngIf="playStoreId$ | async as playStoreId">
      <a
        [href]="'https://play.google.com/store/apps/details?id=' + playStoreId"
      >
        Go to Play Store
      </a>
    </div>
  `,
})
export class AndroidPageComponent implements OnInit {
  fullPath = '';
  playStoreId$ = this.appConfigService.getPlayStoreId();

  constructor(
    private appConfigService: AppConfigService,
    private router: Router
  ) {}

  async ngOnInit(): Promise<void> {
    this.fullPath = this.router.url;

    const playStoreId = await firstValueFrom(this.playStoreId$);

    if (!playStoreId) return;

    const timeout = 1500;

    // Fallback: redirect to Play Store if app doesn't open
    const fallback = setTimeout(() => {
      window.location.href = `https://play.google.com/store/apps/details?id=${playStoreId}`;
    }, timeout);

    // Try opening the app via intent URL
    window.location.href = `intent://${this.fullPath}#Intent;scheme=https;package=${playStoreId};end`;

    // Cancel fallback if app is opened (browser loses focus)
    const blurHandler = () => {
      clearTimeout(fallback);
      window.removeEventListener('blur', blurHandler);
    };
    window.addEventListener('blur', blurHandler);
  }
}
