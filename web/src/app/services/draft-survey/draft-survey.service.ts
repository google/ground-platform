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

import {Injectable} from '@angular/core';
import {List, Map} from 'immutable';
import {BehaviorSubject, Observable, firstValueFrom} from 'rxjs';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {DataSharingType, Survey, SurveyState} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';

import {DataStoreService} from '../data-store/data-store.service';

@Injectable({
  providedIn: 'root',
})
export class DraftSurveyService {
  private survey$!: BehaviorSubject<Survey>;

  private originalSurvey!: Survey;

  dirty = false;
  valid = Map<string, boolean>();

  constructor(private dataStoreService: DataStoreService) {}

  async init(id: string) {
    this.dirty = false;
    this.valid = Map<string, boolean>();

    this.originalSurvey = await firstValueFrom(
      this.dataStoreService.loadSurvey$(id)
    );

    this.survey$ = new BehaviorSubject<Survey>(this.originalSurvey);
  }

  getSurvey(): Survey {
    return this.survey$.getValue();
  }

  getSurvey$(): Observable<Survey> {
    return this.survey$.asObservable();
  }

  addOrUpdateJob(job: Job, duplicate?: boolean): void {
    const currentSurvey = this.survey$.getValue();

    if (job.index === -1) {
      const index =
        Math.max(...currentSurvey.jobs.valueSeq().map(j => j.index), 0) + 1;

      job = job.copyWith({index});

      if (!duplicate) this.valid = this.valid.set(job.id, false);
    }

    this.survey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
    );

    this.dirty = true;
  }

  deleteJob(job: Job): void {
    const currentSurvey = this.survey$.getValue();

    this.survey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.remove(job.id)})
    );

    this.dirty = true;
    this.valid = this.valid.remove(job.id);
  }

  addOrUpdateTasks(jobId: string, tasks: List<Task>, valid: boolean): void {
    const currentSurvey = this.survey$.getValue();

    const currentJob = currentSurvey.jobs.get(jobId)!;

    const job = currentJob?.copyWith({
      tasks: this.dataStoreService.convertTasksListToMap(tasks),
    });

    this.survey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
    );

    this.dirty = true;

    this.valid = this.valid.set(currentJob.id, valid);
  }

  updateTitleAndDescription(
    title: string,
    description: string,
    valid: boolean
  ): void {
    const currentSurvey = this.survey$.getValue();

    this.survey$.next(currentSurvey.copyWith({title, description}));

    this.dirty = true;

    this.valid = this.valid.set(currentSurvey.id, valid);
  }

  updateAcl(acl: Map<string, Role>): void {
    const currentSurvey = this.survey$.getValue();

    this.survey$.next(currentSurvey.copyWith({acl}));

    this.dirty = true;
  }

  updateDataSharingTerms(type: DataSharingType, customText?: string): void {
    const currentSurvey = this.survey$.getValue();

    this.survey$.next(
      currentSurvey.copyWith({
        dataSharingTerms: {type, ...(customText && {customText})},
      })
    );

    this.dirty = true;
  }

  updateState(state: SurveyState): void {
    const currentSurvey = this.survey$.getValue();

    this.survey$.next(currentSurvey.copyWith({state}));
  }

  async updateSurvey(): Promise<void> {
    const currentSurvey = this.survey$.getValue();

    await this.dataStoreService.updateSurvey(
      currentSurvey,
      this.originalSurvey.jobs
        .toList()
        .filter(job => !currentSurvey.jobs.get(job.id))
        .map(job => job.id)
    );

    this.dirty = false;
  }
}
