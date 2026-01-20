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

import { Injectable } from '@angular/core';
import { List, Map } from 'immutable';
import { firstValueFrom } from 'rxjs';

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { MultipleChoice } from 'app/models/task/multiple-choice.model';
import { Option } from 'app/models/task/option.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';

import { TaskService } from '../task/task.service';

enum JobDefaultColors {
  ORANGE = '#F37C22',
  BLUE = '#2278CF',
  YELLOW = '#F9BF40',
  PURPLE = '#7A279F',
  RED = '#D13135',
  GREEN = '#3C8D40',
}

@Injectable({
  providedIn: 'root',
})
export class JobService {
  constructor(
    private dataStoreService: DataStoreService,
    private taskService: TaskService
  ) {}

  /**
   * Returns the first available color (based on lists difference) or undefined.
   */
  getNextColor(jobs?: Map<string, Job>): string | undefined {
    const alreadyUsedcolors =
      jobs?.toList().map((job: Job) => job.color || '') || List([]);

    return Object.values(JobDefaultColors).find(
      color => !alreadyUsedcolors.includes(color)
    );
  }

  /**
   * Creates and returns a new job with a generated unique identifier.
   */
  createNewJob(): Job {
    const jobId = this.dataStoreService.generateId();
    const tasks = this.taskService.addLoiTask(Map());
    return new Job(
      jobId,
      /* index */ -1,
      undefined,
      undefined,
      tasks,
      DataCollectionStrategy.MIXED
    );
  }

  /**
   * Returns a new job which is an exact copy of the specified job, but with new UUIDs for all items recursively.
   */
  duplicateJob(job: Job, color: string | undefined): Job {
    return job.copyWith({
      id: this.dataStoreService.generateId(),
      name: `Copy of ${job.name}`,
      color,
      index: -1,
      tasks: Map<string, Task>(
        job.tasks?.toArray().map(([_, task]) => {
          const duplicateTask = this.taskService.duplicateTask(task);
          return [duplicateTask.id, duplicateTask];
        })
      ),
    });
  }

  /**
   * Creates and returns a new task with a generated unique identifier and a single English label.
   */
  createTask(
    type: TaskType,
    label: string,
    required: boolean,
    index: number,
    multipleChoice?: MultipleChoice
  ): Task {
    const taskId = this.dataStoreService.generateId();
    return new Task(taskId, type, label, required, index, multipleChoice);
  }

  /**
   * Creates and returns a new option with a generated unique identifier, a single English label and code.
   */
  createOption(code: string, label: string, index: number): Option {
    const optionId = this.dataStoreService.generateId();
    const option = new Option(optionId || '', code, label, index);
    return option;
  }

  /**
   * Adds/Updates the job of a survey with a given job value.
   */
  async addOrUpdateJob(survey: Survey, job: Job): Promise<void> {
    if (job.index === -1) {
      const index = survey.jobs.size;
      job = job.copyWith({ index });
    }
    return this.dataStoreService.addOrUpdateJob(survey.id, job);
  }
}
