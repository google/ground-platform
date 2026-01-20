/**
 * Copyright 2023 The Ground Authors.
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

import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Subject } from 'rxjs';

import { DataStoreService } from 'app/services/data-store/data-store.service';
import { TaskService } from './task.service';

describe('TaskService', () => {
  const dataStoreServiceStub: Partial<DataStoreService> = {
    tasks$: () => new Subject(),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceStub },
      ],
    });
  });

  it('should be created', () => {
    const service: TaskService = TestBed.inject(TaskService);
    expect(service).toBeTruthy();
  });
});
