/**
 * Copyright 2020 The Ground Authors.
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

import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';

import {DataImportService} from 'app/services/data-import/data-import.service';

import {HttpClientService} from '../http-client/http-client.service';

describe('DataImportService', () => {
  let service: DataImportService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MatDialogModule],
      providers: [{provide: HttpClientService, useValue: {}}],
    });
    service = TestBed.inject(DataImportService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
