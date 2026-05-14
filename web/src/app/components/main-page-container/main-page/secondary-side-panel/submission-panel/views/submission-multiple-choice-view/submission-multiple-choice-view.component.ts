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

import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import { Option } from 'app/models/task/option.model';
import { Task } from 'app/models/task/task.model';

@Component({
  selector: 'submission-multiple-choice-view',
  templateUrl: './submission-multiple-choice-view.component.html',
  styleUrls: ['./submission-multiple-choice-view.component.scss'],
  standalone: false,
})
export class SubmissionMultipleChoiceViewComponent {
  task = input.required<Task>();
  result = input.required<Result>();

  readonly selection = computed(
    () => this.result().value as MultipleSelection
  );

  readonly otherValue = computed<string | null>(() => {
    const sel = this.selection();
    // Temporary workaround: Ensure at least one value is present: if no
    // values are selected and 'otherText' is empty, add 'Other' as a
    // fallback. https://github.com/google/ground-android/issues/2846
    if (sel.values.size === 0 && !sel.otherValue) return 'Other';
    if (sel.otherValue)
      return sel.otherValue.trim() !== ''
        ? `Other: ${sel.otherValue}`
        : 'Other';
    return null;
  });

  getOptionLabel(optionId: string): string | undefined {
    return this.task().multipleChoice?.options.find(
      ({ id }: Option) => id === optionId
    )?.label;
  }
}
