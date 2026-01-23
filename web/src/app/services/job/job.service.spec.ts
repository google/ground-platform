/**
 * Copyright 2020 The Ground Authors.
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
import { Map } from 'immutable';

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { JobService } from 'app/services/job/job.service';
import { TaskService } from 'app/services/task/task.service';

describe('JobService', () => {
  let service: JobService;
  let dataStoreServiceSpy: jasmine.SpyObj<DataStoreService>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;

  beforeEach(() => {
    dataStoreServiceSpy = jasmine.createSpyObj('DataStoreService', [
      'generateId',
      'addOrUpdateJob',
    ]);
    taskServiceSpy = jasmine.createSpyObj('TaskService', [
      'addLoiTask',
      'duplicateTask',
    ]);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: TaskService, useValue: taskServiceSpy },
      ],
    });
    service = TestBed.inject(JobService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getNextColor', () => {
    it('should return first available color', () => {
      const color = service.getNextColor(Map());
      expect(color).toBeDefined();
    });

    it('should return undefined if all colors used', () => {
      // Create jobs with all default colors
      const jobs = Map(
        ['#F37C22', '#2278CF', '#F9BF40', '#7A279F', '#D13135', '#3C8D40'].map(
          (color, index) => [
            `job${index}`,
            new Job(`job${index}`, index, color),
          ]
        )
      );
      expect(service.getNextColor(jobs)).toBeUndefined();
    });
  });

  describe('createNewJob', () => {
    it('should create new job with mixed strategy', () => {
      dataStoreServiceSpy.generateId.and.returnValue('job1');
      taskServiceSpy.addLoiTask.and.returnValue(Map());

      const job = service.createNewJob();

      expect(job.id).toBe('job1');
      expect(job.strategy).toBe(DataCollectionStrategy.MIXED);
      expect(taskServiceSpy.addLoiTask).toHaveBeenCalled();
    });
  });

  describe('duplicateJob', () => {
    it('should duplicate job with new IDs', () => {
      const oldTask = new Task('task1', TaskType.TEXT, 'Label', false, 0);
      const newTask = new Task('task1', TaskType.TEXT, 'Label', false, 0);
      const job = new Job(
        'job1',
        0,
        '#000',
        'Job 1',
        Map({ task1: oldTask }),
        DataCollectionStrategy.MIXED
      );

      dataStoreServiceSpy.generateId.and.returnValue('job2');
      taskServiceSpy.duplicateTask.and.returnValue(newTask);

      const newJob = service.duplicateJob(job, '#FFF');

      expect(newJob.id).toBe('job2');
      expect(newJob.name).toBe('Copy of Job 1');
      expect(newJob.color).toBe('#FFF');
      expect(newJob.tasks?.get('task1')).toEqual(newTask);
      expect(taskServiceSpy.duplicateTask).toHaveBeenCalledWith(oldTask, true);
    });
  });

  describe('createTask', () => {
    it('should create task', () => {
      dataStoreServiceSpy.generateId.and.returnValue('task1');
      const task = service.createTask(
        TaskType.TEXT,
        'Label',
        true,
        0,
        undefined
      );

      expect(task.id).toBe('task1');
      expect(task.type).toBe(TaskType.TEXT);
      expect(task.label).toBe('Label');
      expect(task.required).toBe(true);
      expect(task.index).toBe(0);
    });
  });

  describe('createOption', () => {
    it('should create option', () => {
      dataStoreServiceSpy.generateId.and.returnValue('opt1');
      const option = service.createOption('code', 'Label', 0);

      expect(option.id).toBe('opt1');
      expect(option.code).toBe('code');
      expect(option.label).toBe('Label');
    });
  });

  describe('addOrUpdateJob', () => {
    it('should add new job with index', async () => {
      const survey = new Survey('s1', 't', 'd', Map(), Map(), '', {
        type: DataSharingType.PRIVATE,
      });
      const job = new Job('j1', -1);

      await service.addOrUpdateJob(survey, job);

      expect(dataStoreServiceSpy.addOrUpdateJob).toHaveBeenCalledWith(
        's1',
        jasmine.objectContaining({ index: 0 })
      );
    });

    it('should update existing job', async () => {
      const survey = new Survey('s1', 't', 'd', Map(), Map(), '', {
        type: DataSharingType.PRIVATE,
      });
      const job = new Job('j1', 1);

      await service.addOrUpdateJob(survey, job);

      expect(dataStoreServiceSpy.addOrUpdateJob).toHaveBeenCalledWith(
        's1',
        job
      );
    });
  });
});
