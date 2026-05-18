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

import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { List } from 'immutable';
import { of } from 'rxjs';

import { Geometry } from 'app/models/geometry/geometry';
import { LocationOfInterest } from 'app/models/loi.model';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import { Survey } from 'app/models/survey.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';

@Component({
  selector: 'submission-panel',
  templateUrl: './submission-panel.component.html',
  styleUrls: ['./submission-panel.component.scss'],
  standalone: false,
})
export class SubmissionPanelComponent {
  private submissionService = inject(SubmissionService);
  private navigationService = inject(NavigationService);

  activeSurvey = input<Survey>();
  selectedLoi = input<LocationOfInterest>();
  submissionId = input<string>();

  selectedTaskId: string | null = null;

  public taskType = TaskType;

  readonly tasks = computed(() => {
    const submission = this.submission();
    if (!submission) return List<Task>();
    return submission.job?.getTasksSorted().filter(t => !t.addLoiTask);
  });

  readonly submittedTasks = computed(() => {
    const currentTasks = this.tasks();
    if (!currentTasks || currentTasks.size === 0) return [];
    return currentTasks
      .filter(task => this.getTaskSubmissionResult(task) !== undefined)
      .toArray();
  });

  private submissionResource = rxResource({
    params: () => ({
      survey: this.activeSurvey(),
      loi: this.selectedLoi(),
      submissionId: this.submissionId(),
    }),
    stream: ({params: {survey, loi, submissionId}}) =>
      survey && loi && submissionId
        ? this.submissionService.getSubmission$(survey, loi, submissionId)
        : of(null),
  });

  readonly submission = this.submissionResource.value;
  readonly isLoading = this.submissionResource.isLoading;

  navigateToSubmissionList() {
    const loi = this.selectedLoi();
    if (!loi) return;
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't navigate to submission list");
      return;
    }
    if (!this.submission()) {
      console.error("No submission - can't navigate to submission list");
      return;
    }
    this.navigationService.selectLocationOfInterest(survey.id, loi.id);
  }

  getTaskSubmissionResult({ id: taskId }: Task): Result | undefined {
    const submission = this.submission();
    if (!submission) return;
    return submission.data.get(taskId);
  }

  asMultipleSelection = (r: Result): MultipleSelection =>
    r.value as MultipleSelection;
  asGeometry = (r: Result): Geometry => r.value as Geometry;
  asDate = (r: Result): Date => r.value as Date;
  asString = (r: Result): string => r.value as string;
  asStringOrNumber = (r: Result): string | number =>
    r.value as string | number;

  selectGeometry(task: Task): void {
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't select geometry");
      return;
    }
    const submission = this.submission();
    if (!submission) {
      console.error("No submission - can't select geometry");
      return;
    }

    this.navigationService.showSubmissionDetailWithHighlightedTask(
      survey.id,
      submission.loiId,
      submission.id,
      task.id
    );
  }
}
