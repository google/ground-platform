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

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { MultipleChoice } from 'app/models/task/multiple-choice.model';
import { Option } from 'app/models/task/option.model';
import {
  TaskCondition,
  TaskConditionExpression,
} from 'app/models/task/task-condition.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { Survey } from 'app/models/survey.model';

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
   * Returns a new job which is an exact copy of the specified job, but with
   * new UUIDs for the job, every task, and every multiple-choice option.
   * Conditional task references (TaskConditionExpression) are remapped to
   * the new task/option ids so dependencies survive the duplication.
   */
  duplicateJob(job: Job, color: string | undefined): Job {
    // Pass 1: duplicate each task (with new ids) and record old → new id
    // mappings for tasks and options.
    const taskIdMap = new globalThis.Map<string, string>();
    const optionIdMap = new globalThis.Map<string, string>();
    const duplicates = (job.tasks?.toArray() ?? []).map(([, oldTask]) => {
      const newTask = this.taskService.duplicateTask(oldTask);
      taskIdMap.set(oldTask.id, newTask.id);
      oldTask.multipleChoice?.options.forEach((oldOption, i) => {
        const newOption = newTask.multipleChoice?.options.get(i);
        if (newOption) optionIdMap.set(oldOption.id, newOption.id);
      });
      return newTask;
    });

    // Pass 2: remap any condition references using the id maps.
    const tasks = Map<string, Task>(
      duplicates.map(newTask => {
        const remapped = newTask.condition
          ? newTask.copyWith({
              condition: this.remapCondition(
                newTask.condition,
                taskIdMap,
                optionIdMap
              ),
            })
          : newTask;
        return [remapped.id, remapped];
      })
    );

    return job.copyWith({
      id: this.dataStoreService.generateId(),
      name: `Copy of ${job.name}`,
      color,
      index: -1,
      tasks,
    });
  }

  private remapCondition(
    condition: TaskCondition,
    taskIdMap: globalThis.Map<string, string>,
    optionIdMap: globalThis.Map<string, string>
  ): TaskCondition {
    const expressions = condition.expressions
      .map(expr => {
        const newTaskId = taskIdMap.get(expr.taskId);
        if (!newTaskId) return null;
        const newOptionIds = expr.optionIds
          .map(id => optionIdMap.get(id))
          .filter((id): id is string => !!id);
        return new TaskConditionExpression(
          expr.expressionType,
          newTaskId,
          newOptionIds
        );
      })
      .filter((expr): expr is TaskConditionExpression => !!expr);
    return new TaskCondition(condition.matchType, expressions);
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
