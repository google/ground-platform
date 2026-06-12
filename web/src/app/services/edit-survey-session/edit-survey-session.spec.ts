/**
 * Copyright 2026 The Ground Authors.
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
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import {
  DataSharingType,
  Survey,
  SurveyState,
} from 'app/models/survey.model';

import { EditSurveySession } from './edit-survey-session';
import { DataStoreService } from '../data-store/data-store.service';

describe('EditSurveySession', () => {
  let session: EditSurveySession;
  let dataStoreSpy: jasmine.SpyObj<DataStoreService>;

  const survey = new Survey(
    's1',
    'title',
    'description',
    Map<string, Job>(),
    Map<string, Role>(),
    'owner',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(() => {
    dataStoreSpy = jasmine.createSpyObj<DataStoreService>('DataStoreService', [
      'loadSurvey$',
      'convertTasksListToMap',
      'updateSurvey',
    ]);
    dataStoreSpy.loadSurvey$.and.returnValue(of(survey));
    dataStoreSpy.convertTasksListToMap.and.returnValue(Map());
    dataStoreSpy.updateSurvey.and.returnValue(Promise.resolve());

    TestBed.configureTestingModule({
      providers: [
        EditSurveySession,
        { provide: DataStoreService, useValue: dataStoreSpy },
      ],
    });
    session = TestBed.inject(EditSurveySession);
  });

  it('should be created', () => {
    expect(session).toBeTruthy();
  });

  it('starts with no survey and a clean, valid state', () => {
    expect(session.survey()).toBeUndefined();
    expect(session.dirty()).toBeFalse();
    expect(session.valid()).toEqual(Map());
  });

  describe('init', () => {
    it('loads the survey and resets dirty/valid', async () => {
      session.dirty.set(true);
      session.valid.set(Map({ stale: false }));

      await session.init('s1');

      expect(dataStoreSpy.loadSurvey$).toHaveBeenCalledWith('s1');
      expect(session.survey()).toBe(survey);
      expect(session.dirty()).toBeFalse();
      expect(session.valid()).toEqual(Map());
    });
  });

  describe('mutations', () => {
    beforeEach(async () => {
      await session.init('s1');
    });

    it('addOrUpdateJob assigns an index, adds the job, marks dirty and invalid', () => {
      session.addOrUpdateJob(new Job('j1', -1));

      const job = session.survey()!.jobs.get('j1')!;
      expect(job.index).toBe(1);
      expect(session.dirty()).toBeTrue();
      expect(session.valid().get('j1')).toBeFalse();
    });

    it('addOrUpdateJob does not mark invalid when duplicating', () => {
      session.addOrUpdateJob(new Job('j1', -1), /* duplicate= */ true);

      expect(session.valid().has('j1')).toBeFalse();
    });

    it('deleteJob removes the job and drops its validity', () => {
      session.addOrUpdateJob(new Job('j1', -1));

      session.deleteJob(session.survey()!.jobs.get('j1')!);

      expect(session.survey()!.jobs.has('j1')).toBeFalse();
      expect(session.valid().has('j1')).toBeFalse();
      expect(session.dirty()).toBeTrue();
    });

    it('addOrUpdateTasks updates the job tasks and records validity', () => {
      session.addOrUpdateJob(new Job('j1', -1));

      session.addOrUpdateTasks('j1', List(), /* valid= */ true);

      expect(dataStoreSpy.convertTasksListToMap).toHaveBeenCalled();
      expect(session.valid().get('j1')).toBeTrue();
      expect(session.dirty()).toBeTrue();
    });

    it('updateTitleAndDescription updates the survey, marks dirty and records validity', () => {
      session.updateTitleAndDescription('new title', 'new desc', true);

      expect(session.survey()!.title).toBe('new title');
      expect(session.survey()!.description).toBe('new desc');
      expect(session.dirty()).toBeTrue();
      expect(session.valid().get('s1')).toBeTrue();
    });

    it('updateState updates state without marking dirty', () => {
      session.updateState(SurveyState.READY);

      expect(session.survey()!.state).toBe(SurveyState.READY);
      expect(session.dirty()).toBeFalse();
    });

    it('updateSurvey persists the survey and clears dirty', async () => {
      session.updateTitleAndDescription('new title', 'new desc', true);

      await session.updateSurvey();

      expect(dataStoreSpy.updateSurvey).toHaveBeenCalled();
      expect(session.dirty()).toBeFalse();
    });
  });
});
