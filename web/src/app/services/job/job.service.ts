/**
 * Copyright 2020 Google LLC
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
import { DataStoreService } from '../data-store/data-store.service';
import { Job } from '../../shared/models/job.model';
import { Step, StepType } from '../../shared/models/task/step.model';
import { Option } from '../../shared/models/task/option.model';
import { MultipleChoice } from '../../shared/models/task/multiple-choice.model';
import { List, Map } from 'immutable';
import { SurveyService } from '../survey/survey.service';
import { take } from 'rxjs/operators';

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
   * Creates and returns a new step with a generated unique identifier and a single English label.
   */
  createStep(
    type: StepType,
    label: string,
    required: boolean,
    index: number,
    multipleChoice?: MultipleChoice
  ): Step {
    const stepId = this.dataStoreService.generateId();
    return new Step(stepId, type, label, required, index, multipleChoice);
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
      job = job.withIndex(index);
    }
    return this.dataStoreService.addOrUpdateJob(surveyId, job);
  }

  /**
   * Converts list of steps to map.
   */
  convertStepsListToMap(steps: List<Step>): Map<string, Step> {
    let stepsMap = Map<string, Step>();
    steps.forEach((step: Step, index: number) => {
      const jobFieldId = steps && steps.get(index)?.id;
      const stepId = jobFieldId
        ? jobFieldId
        : this.dataStoreService.generateId();
      stepsMap = stepsMap.set(stepId, step);
    });
    return stepsMap;
  }

  private async getJobCount(): Promise<number> {
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return survey.jobs?.size;
  }
}
