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
import {Job} from 'app/models/job.model';
import {Survey} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';
import {List} from 'immutable';
import {BehaviorSubject, Observable, firstValueFrom} from 'rxjs';

import {DataStoreService} from '../data-store/data-store.service';

@Injectable({
  providedIn: 'root',
})
export class DraftSurveyService {
  private draftSurvey$!: BehaviorSubject<Survey>;

  private originalSurvey!: Survey;

  constructor(private dataStoreService: DataStoreService) {}

  async init(id: string) {
    this.originalSurvey = await firstValueFrom(
      this.dataStoreService.loadSurvey$(id)
    );

    this.draftSurvey$ = new BehaviorSubject<Survey>(this.originalSurvey);
  }

  getTempSurvey(): Survey {
    return this.draftSurvey$.getValue();
  }

  getTempSurvey$(): Observable<Survey> {
    return this.draftSurvey$.asObservable();
  }

  addOrUpdateJob(job: Job): void {
    const currentSurvey = this.draftSurvey$.getValue();

    if (job.index === -1) {
      const index = currentSurvey.jobs.size;
      job = job.copyWith({index});
    }

    this.draftSurvey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
    );
  }

  deleteJob(job: Job): void {
    const currentSurvey = this.draftSurvey$.getValue();

    this.draftSurvey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.remove(job.id)})
    );
  }

  addOrUpdateTasks(jobId: string, tasks: List<Task>): void {
    const currentSurvey = this.draftSurvey$.getValue();

    const currentJob = currentSurvey.jobs.get(jobId);

    if (currentJob) {
      const job = currentJob?.copyWith({
        tasks: this.dataStoreService.convertTasksListToMap(tasks),
      });

      this.draftSurvey$.next(
        currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
      );
    }
  }

  updateSurvey(): void {
    const currentSurvey = this.draftSurvey$.getValue();

    this.dataStoreService.updateSurveyTitleAndDescription(
      currentSurvey.id,
      currentSurvey.title,
      currentSurvey.description
    );

    this.originalSurvey.jobs.forEach(job => {
      if (!currentSurvey.jobs.get(job.id))
        this.dataStoreService.deleteJob(currentSurvey.id, job.id);
      else this.dataStoreService.addOrUpdateJob(currentSurvey.id, job);
    });
  }
}
