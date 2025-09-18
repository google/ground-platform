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

import {Injectable} from '@angular/core';
import {AngularFireRemoteConfig} from '@angular/fire/compat/remote-config';
import {Observable, from, switchMap} from 'rxjs';

/**
 * Service for fetching and managing app configuration from Firebase Remote Config.
 */
@Injectable({
  providedIn: 'root',
})
export class AppConfigService {
  constructor(private remoteConfig: AngularFireRemoteConfig) {}

  fetchConfig(): Promise<boolean> {
    return (this.remoteConfig as AngularFireRemoteConfig).fetchAndActivate();
  }

  getGooglePlayId(): Observable<string> {
    return from(this.fetchConfig()).pipe(
      switchMap(() => from(this.remoteConfig.getString('google_play_id')))
    );
  }
}
