/**
 * Copyright 2025 The Ground Authors.
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

import { SideNavMode } from './navigation.constants';

export class UrlParams {
  public sideNavMode: SideNavMode | null;

  constructor(
    readonly surveyId: string | null,
    readonly loiId: string | null,
    readonly submissionId: string | null,
    readonly taskId: string | null
  ) {
    if (this.submissionId) {
      this.sideNavMode = SideNavMode.SUBMISSION;
    } else if (this.loiId) {
      this.sideNavMode = SideNavMode.JOB_LIST;
    } else {
      this.sideNavMode = null;
    }
  }
}
