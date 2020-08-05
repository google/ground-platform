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

import { TestBed } from '@angular/core/testing';
import { ObservationService } from './observation.service';
import { DataStoreService } from '../data-store/data-store.service';
import { ProjectService } from '../project/project.service';
import { FeatureService } from '../feature/feature.service';
import { AuthService } from './../../services/auth/auth.service';
import { Subject } from 'rxjs';
import { User } from '../../shared/models/user.model';

describe('ObservationService', () => {
  const user$ = new Subject<User | null>();

  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: {} },
        { provide: ProjectService, useValue: {} },
        { provide: FeatureService, useValue: {} },
        {
          provide: AuthService,
          useValue: {
            user$,
          },
        },
      ],
    })
  );

  it('should be created', () => {
    const service: ObservationService = TestBed.inject(ObservationService);
    expect(service).toBeTruthy();
  });
});
