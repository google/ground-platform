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
import { RouterTestingModule } from '@angular/router/testing';
import { Map } from 'immutable';
import { Subject } from 'rxjs';

import { DataCollectionStrategy } from 'app/models/job.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { TaskService } from './task.service';

describe('TaskService', () => {
  const dataStoreServiceStub: Partial<DataStoreService> = {
    tasks$: () => new Subject(),
    generateId: () => 'generated-id',
  };

  const task = (id: string, index: number, addLoiTask = false) =>
    new Task(
      id,
      TaskType.TEXT,
      id,
      true,
      index,
      undefined,
      undefined,
      addLoiTask
    );

  const asMap = (...tasks: Task[]) =>
    Map(tasks.map(t => [t.id, t] as [string, Task]));

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceStub },
      ],
    });
  });

  it('should be created', () => {
    const service: TaskService = TestBed.inject(TaskService);
    expect(service).toBeTruthy();
  });

  describe('updateLoiTasks', () => {
    it('prepends an LOI task and shifts the others when switching to MIXED', () => {
      const service: TaskService = TestBed.inject(TaskService);

      const tasks = service.updateLoiTasks(
        asMap(task('a', 0), task('b', 1)),
        DataCollectionStrategy.MIXED
      );

      const loiTask = tasks.find(t => !!t.addLoiTask);
      expect(loiTask?.index).toBe(-1);
      expect(tasks.get('a')?.index).toBe(1);
      expect(tasks.get('b')?.index).toBe(2);
    });

    it('drops only the LOI task when switching to PREDEFINED', () => {
      const service: TaskService = TestBed.inject(TaskService);

      const tasks = service.updateLoiTasks(
        asMap(task('loi', -1, true), task('a', 0), task('b', 1)),
        DataCollectionStrategy.PREDEFINED
      );

      expect(tasks.keySeq().sort().toArray()).toEqual(['a', 'b']);
      expect(tasks.some(t => !!t.addLoiTask)).toBeFalse();
    });
  });
});
