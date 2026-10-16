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
import { List, Map, OrderedMap } from 'immutable';
import { of } from 'rxjs';

import { Job } from 'app/models/job.model';
import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';
import { Task, TaskType } from 'app/models/task/task.model';

import { DraftSurveyService } from './draft-survey.service';
import { DataStoreService } from '../data-store/data-store.service';

describe('DraftSurveyService', () => {
  let service: DraftSurveyService;
  let dataStore: jasmine.SpyObj<DataStoreService>;

  const savedTask = new Task('task1', TaskType.TEXT, 'Label', false, 0);
  const newTask = new Task('task2', TaskType.NUMBER, 'New', false, 1);

  function surveyWith(state: SurveyState) {
    return new Survey(
      'survey1',
      'Title',
      '',
      Map([
        [
          'job1',
          new Job(
            'job1',
            0,
            '#000',
            'Job',
            OrderedMap([[savedTask.id, savedTask]])
          ),
        ],
      ]),
      Map(),
      'owner1',
      { type: DataSharingType.PRIVATE },
      state
    );
  }

  async function initWith(state: SurveyState) {
    dataStore.loadSurvey$.and.returnValue(of(surveyWith(state)));
    await service.init('survey1');
  }

  beforeEach(() => {
    dataStore = jasmine.createSpyObj<DataStoreService>('DataStoreService', [
      'loadSurvey$',
      'updateSurvey',
      'convertTasksListToMap',
    ]);
    dataStore.updateSurvey.and.resolveTo();
    dataStore.convertTasksListToMap.and.callFake(tasks =>
      OrderedMap(tasks.map(t => [t.id, t]))
    );
    TestBed.configureTestingModule({
      providers: [{ provide: DataStoreService, useValue: dataStore }],
    });
    service = TestBed.inject(DraftSurveyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getTypeLockedTaskIds()', () => {
    it('locks the saved tasks of a published survey', async () => {
      await initWith(SurveyState.READY);

      expect(service.getTypeLockedTaskIds('job1')).toEqual(
        new Set([savedTask.id])
      );
    });

    it('locks nothing before the survey is published', async () => {
      await initWith(SurveyState.DRAFT);

      expect(service.getTypeLockedTaskIds('job1').size).toBe(0);
    });

    it('leaves tasks added since the last save unlocked', async () => {
      await initWith(SurveyState.READY);

      service.addOrUpdateTasks('job1', List([savedTask, newTask]), true);

      expect(service.getTypeLockedTaskIds('job1')).toEqual(
        new Set([savedTask.id])
      );
    });

    it('locks a task once it has been saved', async () => {
      await initWith(SurveyState.READY);
      service.addOrUpdateTasks('job1', List([savedTask, newTask]), true);

      await service.updateSurvey();

      expect(service.getTypeLockedTaskIds('job1')).toEqual(
        new Set([savedTask.id, newTask.id])
      );
    });

    it('locks nothing for an unknown job', async () => {
      await initWith(SurveyState.READY);

      expect(service.getTypeLockedTaskIds('missing').size).toBe(0);
    });
  });
});
