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

import { Injectable } from '@angular/core';
import { List, Map } from 'immutable';
import { Observable, switchMap } from 'rxjs';

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { MultipleChoice } from 'app/models/task/multiple-choice.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';

import { SurveyService } from '../survey/survey.service';

export type TaskUpdate = {
  label: string;
  required: boolean;
  taskType: TaskType;
  multipleChoice: MultipleChoice;
  index: number;
};

@Injectable({
  providedIn: 'root',
})
export class TaskService {
  private tasks$: Observable<List<Task>>;

  constructor(
    private dataStoreService: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.tasks$ = this.surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          this.dataStoreService.tasks$(
            survey.id,
            survey.jobs.values().next().value.id
          )
        )
      );
  }

  /**
   * Creates and returns a new task with a generated unique identifier.
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
   * Returns a new task which is an exact copy of the provided task, but with new UUIDs.
   */
  duplicateTask(task: Task): Task {
    return {
      ...task,
      id: this.dataStoreService.generateId(),
      multipleChoice: task.multipleChoice
        ? this.duplicateMultipleChoice(task.multipleChoice)
        : undefined,
    } as Task;
  }

  /**
   * Returns a new multiple choice which is an exact copy of the provided one, but with new UUIDs for all options.
   */
  duplicateMultipleChoice(multipleChoice: MultipleChoice): MultipleChoice {
    return {
      ...multipleChoice,
      options: multipleChoice.options?.map(option => ({
        ...option,
        id: this.dataStoreService.generateId(),
      })),
    } as MultipleChoice;
  }

  getTasks$(): Observable<List<Task>> {
    return this.tasks$;
  }

  addOrUpdateTasks(
    surveyId: string,
    job: Job,
    tasks: List<Task>
  ): Promise<void> {
    const newJob = job.copyWith({
      tasks: this.dataStoreService.convertTasksListToMap(tasks),
    });

    return this.dataStoreService.addOrUpdateJob(surveyId, newJob);
  }

  /**
   * Add a loiTask as first element, reindex the others.
   */
  addLoiTask(
    tasks: Map<string, Task>,
    addLoiTaskId?: string
  ): Map<string, Task> {
    if (tasks.some(task => task.addLoiTask === true)) return tasks;

    const loiTask = new Task(
      addLoiTaskId || this.dataStoreService.generateId(),
      TaskType.DRAW_AREA,
      '',
      true,
      -1,
      undefined,
      undefined,
      true
    );

    const newTasks = tasks.map((task: Task) =>
      task.copyWith({ index: task.index + 1 })
    );

    return newTasks.set(loiTask.id, loiTask);
  }

  /**
   * Remove the first element of the list if is loiTask.
   */
  removeLoiTask(tasks: Map<string, Task>): Map<string, Task> {
    const loiTask = tasks.find(task => task.addLoiTask === true);

    return loiTask ? tasks.remove(loiTask.id) : tasks;
  }

  updateLoiTasks(
    tasks: Map<string, Task> | undefined,
    strategy: DataCollectionStrategy,
    addLoiTaskId?: string
  ): Map<string, Task> {
    return strategy === DataCollectionStrategy.MIXED
      ? this.addLoiTask(tasks || Map<string, Task>(), addLoiTaskId)
      : this.removeLoiTask(tasks || Map<string, Task>());
  }
}
