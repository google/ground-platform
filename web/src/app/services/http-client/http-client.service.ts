/**
 * Copyright 2023 Google LLC
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

import {AngularFireAuth} from '@angular/fire/compat/auth';
import {firstValueFrom} from 'rxjs';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class HttpClientService {
  constructor(private httpClient: HttpClient, private  afAuth: AngularFireAuth) {}

  /**
   * Sends an HTTP post with the Authorization Bearer token of the current user
   * in the request header.
   */
  async postWithAuth<T>(url: string, body: any | null): Promise<T> {
    const token = await firstValueFrom(this.afAuth.idToken);
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    return firstValueFrom(
      this.httpClient.post<T>(url, body, {headers})
    );
  }
}
