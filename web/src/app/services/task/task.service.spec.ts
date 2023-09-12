/**
 * Copyright 2023 Google LLC
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
import {TaskService} from './task.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {RouterTestingModule} from '@angular/router/testing';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';

describe('TaskService', () => {
  const dataStoreServiceStub: Partial<DataStoreService> = {};
  const activeSurvey$ = new Subject<Survey | null>();

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        {provide: DataStoreService, useValue: dataStoreServiceStub},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => activeSurvey$,
          },
        },
      ],
    });
  });

  it('should be created', () => {
    const service: TaskService = TestBed.inject(TaskService);
    expect(service).toBeTruthy();
  });
});
