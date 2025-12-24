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

import { AuditInfo } from 'app/models/audit-info.model';
import { Job } from 'app/models/job.model';
import { Map } from 'immutable';

import { Result } from './result.model';

export type SubmissionData = Map<string, Result>;

export class Submission {
  constructor(
    readonly id: string,
    readonly loiId: string,
    readonly job: Job | null,
    readonly created: AuditInfo,
    readonly lastModified: AuditInfo,
    readonly data: SubmissionData
  ) {}

  // Returns copy of Submission with data and lastModified replaced.
  withDataAndLastModified(
    data: SubmissionData,
    lastModified: AuditInfo
  ): Submission {
    return new Submission(
      this.id,
      this.loiId,
      this.job,
      this.created,
      lastModified,
      data
    );
  }
}
