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

import { AuthService } from './../../services/auth/auth.service';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-import-dialog',
  templateUrl: './import-dialog.component.html',
  styleUrls: ['./import-dialog.component.css'],
})
export class ImportDialogComponent implements OnInit {
  constructor(private authService: AuthService) {}

  getConfig(): any {
    const token = this.authService.getIdToken();
    return {
      multiple: false,
      formatsAllowed: '.csv,.txt',
      maxSize: '1',
      uploadAPI: {
        url: 'http://localhost:5001/gnd-dev/us-central1/importCsv',
      },
      method: 'POST',
      headers: {
        'Content-Type': 'multipart/form-data;charset=UTF-8',
        Authorization: `Bearer ${token || ''}`,
      },
      params: {
        projectId: 'test',
        layerId: 'layer',
      },
      //    theme: 'dragNDrop',
      hideProgressBar: false,
      hideResetBtn: true,
      hideSelectBtn: false,
      fileNameIndex: true,
    };
  }

  ngOnInit(): void {}
}
