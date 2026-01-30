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
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Storage, getDownloadURL, ref } from '@angular/fire/storage';
import { List } from 'immutable';
import { combineLatest, of } from 'rxjs';
import { delay, switchMap } from 'rxjs/operators';

import { Point } from 'app/models/geometry/point';
import { LocationOfInterest } from 'app/models/loi.model';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import { Survey } from 'app/models/survey.model';
import { Option } from 'app/models/task/option.model';
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
  private storage = inject(Storage);

  activeSurvey = input<Survey>();
  lois = input<List<LocationOfInterest>>();
  submissionId = input<string>();
  loiId = input<string>();
  selectedTaskId: string | null = null;
  firebaseURLs = new Map<string, string>();

  readonly isLoading = computed(() => {
    return this.submission() === undefined;
  });

  submission = toSignal(
    combineLatest([
      toObservable(this.activeSurvey),
      toObservable(this.lois),
      toObservable(this.submissionId),
      toObservable(this.loiId),
    ]).pipe(
      switchMap(([survey, lois, submissionId, loiId]) => {
        const loi = lois?.find(l => l.id === loiId);
        if (survey && loi && submissionId) {
          return this.submissionService
            .getSubmission$(survey, loi, submissionId)
            .pipe(delay(100));
        }
        return of(null).pipe(delay(100));
      })
    ),
    { initialValue: undefined }
  );

  tasks = computed(() => {
    const submission = this.submission();
    if (!submission) return List<Task>();
    return submission.job?.getTasksSorted().filter(t => !t.addLoiTask);
  });

  public taskType = TaskType;

  constructor() {}

  readonly submittedTasks = computed(() => {
    const currentTasks = this.tasks();
    if (!currentTasks || currentTasks.size === 0) return [];
    return currentTasks
      .filter(task => this.getTaskSubmissionResult(task) !== undefined)
      .toArray();
  });

  getFirebaseImageURLs() {
    const tasks = this.tasks();
    if (!tasks) return;
    tasks.forEach(task => {
      if (task.type === this.taskType.PHOTO) {
        const submissionImage = this.getTaskSubmissionResult(task);
        const submissionImageValue = submissionImage?.value as string;
        if (
          submissionImageValue &&
          submissionImageValue.trim() !== '' &&
          submissionImageValue !== '/'
        ) {
          const imageRef = ref(this.storage, submissionImageValue);
          getDownloadURL(imageRef)
            .then((url: string) => {
              this.firebaseURLs.set(submissionImageValue, url);
            })
            .catch((error: Error) => {
              console.error(
                `Could not load image: ${submissionImageValue}`,
                error
              );
            });
        } else {
          console.warn(`Task ${task.id} has no valid image path.`);
        }
      }
    });
  }

  navigateToSubmissionList() {
    const loiId = this.loiId();
    if (!loiId) return;
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't navigate to submission list");
      return;
    }
    if (!this.submission) {
      console.error("No submission - can't navigate to submission list");
      return;
    }
    this.navigationService.selectLocationOfInterest(survey.id, loiId);
  }

  getTaskSubmissionResult({ id: taskId }: Task): Result | undefined {
    const submission = this.submission();
    if (!submission) return;
    return submission.data.get(taskId);
  }

  getMultipleChoiceOption(task: Task, optionId: string) {
    return task.multipleChoice?.options.find(
      ({ id }: Option) => id === optionId
    );
  }

  getTaskMultipleChoiceSelections(task: Task): MultipleSelection {
    return this.getTaskSubmissionResult(task)!.value as MultipleSelection;
  }

  getTaskMultipleChoiceOtherValue(task: Task): string | null {
    const multipleSelection = this.getTaskSubmissionResult(task)!
      .value as MultipleSelection;
    // Temporary workaround: Ensure at least one value is present: if no values are selected and 'otherText' is empty, add 'Other' as a fallback.
    // https://github.com/google/ground-android/issues/2846
    if (multipleSelection.values.size === 0 && !multipleSelection.otherValue)
      return 'Other';
    if (multipleSelection.otherValue)
      return multipleSelection.otherValue.trim() !== ''
        ? `Other: ${multipleSelection.otherValue}`
        : 'Other';
    return null;
  }

  getCaptureLocationCoord(task: Task): string {
    // x represents longitude, y represents latitude
    const { coord, accuracy, altitude } = this.getTaskSubmissionResult(task)!
      .value as Point;
    const { x, y } = coord;
    const lngSuffix =
      x >= 0
        ? $localize`:@@app.labels.lngEast:E`
        : $localize`:@@app.labels.lngWest:W`;
    const latSuffix =
      y >= 0
        ? $localize`:@@app.labels.latNorth:N`
        : $localize`:@@app.labels.latSouth:S`;
    const lng = `${Math.abs(x)}° ${lngSuffix}`;
    const lat = `${Math.abs(y)}° ${latSuffix}`;
    const result = [`${lat}, ${lng}`];
    if (altitude)
      result.push(
        $localize`:@@app.labels.altitude:Altitude: ${altitude}:altitude:m`
      );
    if (accuracy)
      result.push(
        $localize`:@@app.labels.accuracy:Accuracy: ${accuracy}:accuracy:m`
      );
    return result.join('\n');
  }

  getDate(task: Task): string {
    return (
      this.getTaskSubmissionResult(task)?.value as Date
    ).toLocaleDateString();
  }

  getTime(task: Task): string {
    return (
      this.getTaskSubmissionResult(task)?.value as Date
    ).toLocaleTimeString([], { hour: 'numeric', minute: 'numeric' });
  }

  selectGeometry(task: Task): void {
    const submission = this.submission();
    if (!submission) return;
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't select geometry");
      return;
    }
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
