/**
 * Copyright 2024 The Ground Authors.
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

import { Injectable, Injector, runInInjectionContext } from '@angular/core';
import { Functions, httpsCallable } from '@angular/fire/functions';
import { environment } from 'environments/environment';

import { HttpClientService } from '../http-client/http-client.service';

@Injectable({
  providedIn: 'root',
})
export class CloudFunctionsService {
  constructor(
    private functions: Functions,
    private httpClientService: HttpClientService,
    private injector: Injector
  ) {}

  async sessionLogin(): Promise<{ expiresAt: number }> {
    return this.httpClientService.postWithAuth<{ expiresAt: number }>(
      `${environment.cloudFunctionsUrl}/sessionLogin`,
      {}
    );
  }

  async profileRefresh(): Promise<void> {
    const refreshProfile = runInInjectionContext(this.injector, () =>
      httpsCallable(this.functions, 'profile-refresh')
    );
    const result = (await refreshProfile({})).data;
    if (result !== 'OK') {
      throw new Error('User profile could not be updated');
    }
  }
}
