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
import { SubmissionService } from 'app/services/submission/submission.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { AuthService } from 'app/services/auth/auth.service';
import { Subject } from 'rxjs';
import { User } from 'app/shared/models/user.model';

describe('SubmissionService', () => {
  const user$ = new Subject<User | null>();
  let service: SubmissionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: {} },
        { provide: SurveyService, useValue: {} },
        { provide: LocationOfInterestService, useValue: {} },
        {
          provide: AuthService,
          useValue: {
            user$,
          },
        },
      ],
    });
    service = TestBed.inject(SubmissionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
