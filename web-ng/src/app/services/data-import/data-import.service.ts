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
import { environment } from '../../../environments/environment';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

const IMPORT_CSV_URL = `${environment.cloudFunctionsUrl}/importCsv`;

export interface ImportCsvResponse {
  message?: string;
}

@Injectable({
  providedIn: 'root',
})
export class DataImportService {
  constructor(
    private authService: AuthService,
    private httpClient: HttpClient
  ) {}

  async importCsv(
    projectId: string,
    layerId: string,
    file: File
  ): Promise<ImportCsvResponse> {
    const formData = new FormData();
    formData.set('project', projectId);
    formData.set('layer', layerId);
    formData.append('file', file);
    const headers = await this.authService.getAuthHeaders();
    // TODO: When run on protected importCsv returns 401 with response header:
    // www-authenticate: Bearer error="invalid_token" error_description="The access token could not be verified"
    return this.httpClient
      .post<ImportCsvResponse>(IMPORT_CSV_URL, formData, { headers })
      .toPromise();
  }
}
