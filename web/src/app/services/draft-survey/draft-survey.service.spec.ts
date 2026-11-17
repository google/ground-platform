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
import { Map } from 'immutable';
import { of } from 'rxjs';

import { Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import { DataSharingType, Survey } from 'app/models/survey.model';

import { DraftSurveyService } from './draft-survey.service';
import { DataStoreService } from '../data-store/data-store.service';

describe('DraftSurveyService', () => {
  let service: DraftSurveyService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: DataStoreService, useValue: {} }],
    });
    service = TestBed.inject(DraftSurveyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('deletes a job added and removed after the survey was saved', async () => {
    const survey = new Survey(
      '123',
      'title',
      'description',
      Map<string, Job>(),
      Map<string, Role>(),
      '',
      { type: DataSharingType.PRIVATE }
    );
    const dataStore = jasmine.createSpyObj('DataStoreService', [
      'loadSurvey$',
      'updateSurvey',
    ]);
    dataStore.loadSurvey$.and.returnValue(of(survey));
    dataStore.updateSurvey.and.resolveTo();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: DataStoreService, useValue: dataStore }],
    });
    service = TestBed.inject(DraftSurveyService);
    await service.init('123');

    const job = new Job('job1', -1, undefined, 'Job');
    service.addOrUpdateJob(job);
    await service.updateSurvey();
    service.deleteJob(service.getSurvey().jobs.get('job1')!);
    await service.updateSurvey();

    const deletedJobIds = dataStore.updateSurvey.calls.mostRecent().args[1];
    expect(deletedJobIds.toArray()).toEqual(['job1']);
  });
});
