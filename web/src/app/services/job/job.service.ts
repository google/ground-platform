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

import {Injectable} from '@angular/core';
import {List, Map} from 'immutable';
import {firstValueFrom} from 'rxjs';

import {Job} from 'app/models/job.model';
import {MultipleChoice} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {SurveyService} from 'app/services/survey/survey.service';

@Injectable({
  providedIn: 'root',
})
export class JobService {
  constructor(
    private dataStoreService: DataStoreService,
    private surveyService: SurveyService
  ) {}

  /**
   * Creates and returns a new job with a generated unique identifier.
   */
  createNewJob(): Job {
    const jobId = this.dataStoreService.generateId();
    return new Job(jobId, /* index */ -1);
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
  async addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    if (job.index === -1) {
      const index = await this.getJobCount();
      job = job.copyWith({index});
    }
    return this.dataStoreService.addOrUpdateJob(surveyId, job);
  }

  private async getJobCount(): Promise<number> {
    const survey = await firstValueFrom(this.surveyService.getActiveSurvey$());
    return survey.jobs?.size;
  }
}
