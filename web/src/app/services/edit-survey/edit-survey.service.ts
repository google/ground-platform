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
export class EditSurveyService {
  private editSurvey$!: BehaviorSubject<Survey>;

  constructor(private dataStoreService: DataStoreService) {}

  async init(id: string) {
    this.editSurvey$ = new BehaviorSubject<Survey>(
      await firstValueFrom(this.dataStoreService.loadSurvey$(id))
    );
  }

  getTempSurvey(): Survey {
    return this.editSurvey$.getValue();
  }

  getTempSurvey$(): Observable<Survey> {
    return this.editSurvey$.asObservable();
  }

  addOrUpdateJob(job: Job): void {
    const currentSurvey = this.editSurvey$.getValue();

    if (job.index === -1) {
      const index = currentSurvey.jobs.size;
      job = job.copyWith({index});
    }

    this.editSurvey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
    );
  }

  deleteJob(job: Job): void {
    const currentSurvey = this.editSurvey$.getValue();

    this.editSurvey$.next(
      currentSurvey.copyWith({jobs: currentSurvey.jobs.remove(job.id)})
    );
  }

  addOrUpdateTasks(jobId: string, tasks: List<Task>): void {
    const currentSurvey = this.editSurvey$.getValue();

    const currentJob = currentSurvey.jobs.get(jobId);

    if (currentJob) {
      const job = currentJob?.copyWith({
        tasks: this.dataStoreService.convertTasksListToMap(tasks),
      });

      this.editSurvey$.next(
        currentSurvey.copyWith({jobs: currentSurvey.jobs.set(job.id, job)})
      );
    }
  }

  updateSurvey(): void {
    this.dataStoreService.addOrUpdateJob;
    console.log(this.getTempSurvey());
  }
}
