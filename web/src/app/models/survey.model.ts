/**
 * Copyright 2019 The Ground Authors.
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

import {Map as ImmutableMap, List} from 'immutable';

import {Copiable} from './copiable';
import {Job} from './job.model';
import {Role} from './role.model';

/** Enum for type of data sharing terms. */
export enum DataSharingType {
  PRIVATE = 1,
  PUBLIC = 2,
  CUSTOM = 3,
}

export const DATA_SHARING_TYPE_DESCRIPTION = new Map<DataSharingType, string>([
  [
    DataSharingType.PRIVATE,
    'Survey organizers may <strong>not</strong> share and use collected data publicly or with third parties',
  ],
  [
    DataSharingType.PUBLIC,
    'Data collectors waive all rights to data collected as part of this survey under <a href="https://creativecommons.org/public-domain/cc0/" target="_blank">the CC0 license</a>.<br />Survey organizers may share data freely.',
  ],
  [
    DataSharingType.CUSTOM,
    'Data collectors must agree to the custom terms you provide here',
  ],
]);

/** Enum for survey's current state. */
export enum SurveyState {
  UNSAVED = 0,
  DRAFT = 1,
  READY = 2,
}

export class Survey extends Copiable {
  static readonly UNSAVED_NEW = new Survey(
    /* id= */
    '',
    /* title= */
    '',
    /* description= */
    '',
    /* jobs= */
    ImmutableMap<string, Job>(),
    /* acl= */
    ImmutableMap<string, Role>(),
    /* ownerId= */
    '',
    /* dataSharingTerms= */
    {type: DataSharingType.PRIVATE},
    SurveyState.UNSAVED
  );

  constructor(
    readonly id: string,
    readonly title: string,
    readonly description: string,
    readonly jobs: ImmutableMap<string, Job>,
    readonly acl: ImmutableMap<string, Role>,
    readonly ownerId: string,
    readonly dataSharingTerms: {type: DataSharingType; customText?: string},
    readonly state?: SurveyState
  ) {
    super();
  }

  getJob(jobId: string): Job | undefined {
    return this.jobs.get(jobId);
  }

  getJobsSorted(): List<Job> {
    return this.jobs.sortBy(job => job.index).toList();
  }

  getAclSorted(): ImmutableMap<string, Role> {
    return this.acl.sortBy(([key]) => key);
  }

  getPreviousJob(job: Job): Job | undefined {
    const jobs = this.getJobsSorted();

    const index = jobs.findIndex(j => j.id === job.id);

    return jobs.get(index - 1);
  }
}
