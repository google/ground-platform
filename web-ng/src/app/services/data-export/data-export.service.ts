import { tap } from 'rxjs/operators';
/**
 * Copyright 2020 Google LLC
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

import { AuthService } from './../auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

const EXPORT_CSV_URL = `${environment.cloudFunctionsUrl}/exportCsv`;

@Injectable({
  providedIn: 'root',
})
export class DataExportService {
  constructor(
    private authService: AuthService,
    private httpClient: HttpClient
  ) {}

  async downloadCsv(projectId: string, layerId: string): Promise<any> {
    const headers = await this.authService.getAuthHeaders();
    // TODO(#586): Stream results instead of using blob to support large files.
    const data = await this.httpClient
      .get(EXPORT_CSV_URL, {
        headers,
        params: {
          project: projectId,
          layer: layerId,
        },
        responseType: 'blob',
      })
      .toPromise();
    const blob = new Blob([data], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const pwa = window.open(url);
    if (!pwa || pwa.closed || typeof pwa.closed === 'undefined') {
      alert('Please disable your Pop-up blocker and try again.');
    }
  }
}
