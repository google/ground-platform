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

@Injectable({
  providedIn: 'root',
})
export class DraftSurveyService {
  constructor(private dataStoreService: DataStoreService) {}

  addOrUpdateJob(survey: Survey, job: Job): Survey {
    if (job.index === -1) {
      const index =
        Math.max(...survey.jobs.valueSeq().map(j => j.index), 0) + 1;
      job = job.copyWith({ index });
    }
    return survey.copyWith({ jobs: survey.jobs.set(job.id, job) });
  }

  deleteJob(survey: Survey, jobId: string): Survey {
    return survey.copyWith({ jobs: survey.jobs.remove(jobId) });
  }

  addOrUpdateTasks(survey: Survey, jobId: string, tasks: List<Task>): Survey {
    const currentJob = survey.jobs.get(jobId);
    if (!currentJob) return survey;

    const job = currentJob.copyWith({
      tasks: this.dataStoreService.convertTasksListToMap(tasks),
    });

    return survey.copyWith({ jobs: survey.jobs.set(job.id, job) });
  }

  updateTitleAndDescription(
    survey: Survey,
    title: string,
    description: string
  ): Survey {
    return survey.copyWith({ title, description });
  }

  updateAcl(survey: Survey, acl: Map<string, Role>): Survey {
    return survey.copyWith({ acl });
  }

  updateGeneralAccess(
    survey: Survey,
    generalAccess: SurveyGeneralAccess
  ): Survey {
    return survey.copyWith({ generalAccess });
  }

  updateDataVisibility(
    survey: Survey,
    dataVisibility: SurveyDataVisibility
  ): Survey {
    return survey.copyWith({ dataVisibility });
  }

  updateDataSharingTerms(
    survey: Survey,
    type: DataSharingType,
    customText?: string
  ): Survey {
    return survey.copyWith({
      dataSharingTerms: { type, ...(customText && { customText }) },
    });
  }

  updateState(survey: Survey, state: SurveyState): Survey {
    return survey.copyWith({ state });
  }

  async updateSurvey(
    currentSurvey: Survey,
    originalSurvey: Survey
  ): Promise<void> {
    await this.dataStoreService.updateSurvey(
      currentSurvey,
      originalSurvey.jobs
        .toList()
        .filter(job => !currentSurvey.jobs.get(job.id))
        .map(job => job.id)
    );
  }
}
