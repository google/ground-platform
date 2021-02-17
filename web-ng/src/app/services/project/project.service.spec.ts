/**
 * Copyright 2019 Google LLC
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

import { ProjectService } from './project.service';
import { DataStoreService } from '../data-store/data-store.service';
import { AuthService } from '../auth/auth.service';
import { Subject } from 'rxjs';
import { User } from '../../shared/models/user.model';

describe('ProjectService', () => {
  const dataStoreServiceStub: Partial<DataStoreService> = {};
  const user$ = new Subject<User | null>();
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceStub },
        {
          provide: AuthService,
          useValue: {
            user$,
            getUser$: () => user$,
          },
        },
      ],
    })
  );

  it('should be created', () => {
    // TODO(gino-m): Implement tests.
    const service: ProjectService = TestBed.inject(ProjectService);
    expect(service).toBeTruthy();
  });
});
