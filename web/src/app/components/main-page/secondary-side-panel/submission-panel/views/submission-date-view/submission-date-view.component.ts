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

import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'submission-date-view',
  templateUrl: './submission-date-view.component.html',
  standalone: false,
})
export class SubmissionDateViewComponent {
  date = input.required<Date>();

  /**
   * The result is a `Date` only if the task type was a date when the
   * submission was made. If the type was changed afterwards, it can be
   * anything, in which case it is shown as it is.
   */
  readonly formattedDate = computed(() => {
    const date = this.date();
    return date instanceof Date ? date.toLocaleDateString() : String(date);
  });
}
