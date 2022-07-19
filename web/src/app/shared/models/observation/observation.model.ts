/**
 * Copyright 2020 Google LLC
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

import { AuditInfo } from '../audit-info.model';
import { Form } from '../form/form.model';
import { Response } from './response.model';
import { Map } from 'immutable';

export class Observation {
  constructor(
    readonly id: string,
    readonly featureId: string,
    readonly jobId: string,
    readonly form: Form | null,
    readonly created: AuditInfo,
    readonly lastModified: AuditInfo,
    readonly responses: Map<string, Response>
  ) {}

  // Returns copy of Observation with responses and lastModified replaced.
  withResponsesAndLastModified(
    responses: Map<string, Response>,
    lastModified: AuditInfo
  ): Observation {
    return new Observation(
      this.id,
      this.featureId,
      this.jobId,
      this.form,
      this.created,
      lastModified,
      responses
    );
  }
}
