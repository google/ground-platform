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

import {TestBed} from '@angular/core/testing';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject, of} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {NavigationService} from 'app/services/navigation/navigation.service';

describe('LocationOfInterestService', () => {
  const activeSurvey$ = new Subject<Survey | null>();

  beforeEach(() => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
    };
    TestBed.configureTestingModule({
      providers: [
        {provide: DataStoreService, useValue: {}},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => activeSurvey$,
          },
        },
        {provide: NavigationService, useValue: navigationService},
      ],
    });
  });

  it('should be created', () => {
    const service: LocationOfInterestService = TestBed.inject(
      LocationOfInterestService
    );
    expect(service).toBeTruthy();
  });
});
