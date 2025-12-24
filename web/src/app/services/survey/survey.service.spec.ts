/**
 * Copyright 2019 The Ground Authors.
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
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { Subject } from 'rxjs';

describe('SurveyService', () => {
  let service: SurveyService;
  let dataStoreService: DataStoreService;

  const user$ = new Subject<User | null>();
  const newSurveyId = 'newSurveyId';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: DataStoreService,
          useValue: {
            copySurvey: jasmine
              .createSpy('copySurvey')
              .and.resolveTo(newSurveyId),
          },
        },
        {
          provide: AuthService,
          useValue: {
            user$,
            getUser$: () => user$,
          },
        },
      ],
    });

    service = TestBed.inject(SurveyService);

    dataStoreService = TestBed.inject(DataStoreService) as DataStoreService & {
      copySurvey: jasmine.Spy;
    };
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call copySurvey and return the new ID', async () => {
    const originalSurveyId = 'original123';

    const resultId = await service.copySurvey(originalSurveyId);

    expect(dataStoreService.copySurvey).toHaveBeenCalledOnceWith(
      originalSurveyId
    );

    expect(resultId).toBe(newSurveyId);
  });
});
