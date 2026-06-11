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

import { Injectable, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { List, Map } from 'immutable';
import { Observable, filter, firstValueFrom } from 'rxjs';

import { Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import {
  DataSharingType,
  Survey,
  SurveyDataVisibility,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';

import { DataStoreService } from '../data-store/data-store.service';

/**
 * Holds the in-progress (draft) state of the survey currently being edited or
 * created.
 *
 * Unlike a `providedIn: 'root'` service, this is provided at the component
 * level (on `EditSurveyComponent` and `CreateSurveyComponent`), so each editing
 * flow owns its own instance instead of sharing a single global async state.
 */
@Injectable()
export class EditSurveySession {
  /** The draft survey. `undefined` until {@link init} resolves. */
  readonly survey = signal<Survey | undefined>(undefined);

  /** Whether the draft has unsaved changes. */
  readonly dirty = signal(false);

  /** Per-entity validity of the draft, keyed by survey/job id. */
  readonly valid = signal(Map<string, boolean>());

  private originalSurvey!: Survey;

  /**
   * Observable view of {@link survey}, emitting only once a survey has been
   * loaded. Provided as a bridge for consumers not yet migrated to the signal;
   * remove once all consumers read {@link survey} directly.
   */
  private readonly survey$ = toObservable(this.survey).pipe(
    filter((survey): survey is Survey => survey !== undefined)
  );

  constructor(private dataStoreService: DataStoreService) {}

  async init(id: string) {
    this.dirty.set(false);
    this.valid.set(Map<string, boolean>());

    this.originalSurvey = await firstValueFrom(
      this.dataStoreService.loadSurvey$(id)
    );

    this.survey.set(this.originalSurvey);
  }

  getSurvey(): Survey {
    return this.survey()!;
  }

  getSurvey$(): Observable<Survey> {
    return this.survey$;
  }

  addOrUpdateJob(job: Job, duplicate?: boolean): void {
    const currentSurvey = this.survey()!;

    if (job.index === -1) {
      const index =
        Math.max(...currentSurvey.jobs.valueSeq().map(j => j.index), 0) + 1;

      job = job.copyWith({ index });

      if (!duplicate) this.valid.update(valid => valid.set(job.id, false));
    }

    this.survey.set(
      currentSurvey.copyWith({ jobs: currentSurvey.jobs.set(job.id, job) })
    );

    this.dirty.set(true);
  }

  deleteJob(job: Job): void {
    const currentSurvey = this.survey()!;

    this.survey.set(
      currentSurvey.copyWith({ jobs: currentSurvey.jobs.remove(job.id) })
    );

    this.dirty.set(true);
    this.valid.update(valid => valid.remove(job.id));
  }

  addOrUpdateTasks(jobId: string, tasks: List<Task>, valid: boolean): void {
    const currentSurvey = this.survey()!;

    const currentJob = currentSurvey.jobs.get(jobId)!;

    const job = currentJob?.copyWith({
      tasks: this.dataStoreService.convertTasksListToMap(tasks),
    });

    this.survey.set(
      currentSurvey.copyWith({ jobs: currentSurvey.jobs.set(job.id, job) })
    );

    this.dirty.set(true);

    this.valid.update(v => v.set(currentJob.id, valid));
  }

  updateTitleAndDescription(
    title: string,
    description: string,
    valid: boolean
  ): void {
    const currentSurvey = this.survey()!;

    this.survey.set(currentSurvey.copyWith({ title, description }));

    this.dirty.set(true);

    this.valid.update(v => v.set(currentSurvey.id, valid));
  }

  updateAcl(acl: Map<string, Role>): void {
    const currentSurvey = this.survey()!;

    this.survey.set(currentSurvey.copyWith({ acl }));

    this.dirty.set(true);
  }

  updateGeneralAccess(generalAccess: SurveyGeneralAccess): void {
    const currentSurvey = this.survey()!;

    this.survey.set(currentSurvey.copyWith({ generalAccess }));

    this.dirty.set(true);
  }

  updateDataVisibility(dataVisibility: SurveyDataVisibility): void {
    const currentSurvey = this.survey()!;

    this.survey.set(currentSurvey.copyWith({ dataVisibility }));

    this.dirty.set(true);
  }

  updateDataSharingTerms(type: DataSharingType, customText?: string): void {
    const currentSurvey = this.survey()!;

    this.survey.set(
      currentSurvey.copyWith({
        dataSharingTerms: { type, ...(customText && { customText }) },
      })
    );

    this.dirty.set(true);
  }

  updateState(state: SurveyState): void {
    const currentSurvey = this.survey()!;

    this.survey.set(currentSurvey.copyWith({ state }));
  }

  async updateSurvey(): Promise<void> {
    const currentSurvey = this.survey()!;

    await this.dataStoreService.updateSurvey(
      currentSurvey,
      this.originalSurvey.jobs
        .toList()
        .filter(job => !currentSurvey.jobs.get(job.id))
        .map(job => job.id)
    );

    this.dirty.set(false);
  }
}
