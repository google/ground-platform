/**
 * Copyright 2019 Google LLC
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

import { Job } from './job.model';
import { StringMap } from './string-map.model';
import { Map } from 'immutable';
import { Role } from './role.model';

export class Survey {
  static readonly UNSAVED_NEW = new Survey(
    /* id= */
    '',
    /* title= */
    StringMap({}),
    /* description= */
    StringMap({}),
    /* jobs= */
    Map<string, Job>(),
    /* acl= */
    Map<string, Role>()
  );

  constructor(
    readonly id: string,
    readonly title: StringMap,
    readonly description: StringMap,
    readonly jobs: Map<string, Job>,
    readonly acl: Map<string, Role>
  ) {}

  getJob(jobId: string): Job | undefined {
    return this.jobs.get(jobId);
  }

  isUnsavedNew() {
    return (
      !this.id &&
      !this.title.size &&
      !this.description.size &&
      !this.jobs.size &&
      !this.acl.size
    );
  }
}
