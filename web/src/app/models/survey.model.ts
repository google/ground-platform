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

import {Map} from 'immutable';

import {Copiable} from './copiable';
import {Job} from './job.model';
import {Role} from './role.model';

export class Survey extends Copiable {
  static readonly UNSAVED_NEW = new Survey(
    /* id= */
    '',
    /* title= */
    '',
    /* description= */
    '',
    /* jobs= */
    Map<string, Job>(),
    /* acl= */
    Map<string, Role>()
  );

  constructor(
    readonly id: string,
    readonly title: string,
    readonly description: string,
    readonly jobs: Map<string, Job>,
    readonly acl: Map<string, Role>
  ) {
    super();
  }

  getJob(jobId: string): Job | undefined {
    return this.jobs.get(jobId);
  }

  isUnsavedNew() {
    return (
      !this.id &&
      !this.title &&
      !this.description &&
      !this.jobs.size &&
      !this.acl.size
    );
  }

  getJobsSorted(): Job[] {
    return (
      this.jobs
        .valueSeq()
        .sort(({index: index1}, {index: index2}) => (index1 < index2 ? -1 : 1))
        .toArray() || []
    );
  }
}
