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

import { Component, Input, OnDestroy, OnInit, input } from '@angular/core';
import { Storage, getDownloadURL, ref } from '@angular/fire/storage';
import { List } from 'immutable';
import { Subscription } from 'rxjs';

import { Point } from 'app/models/geometry/point';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import { Submission } from 'app/models/submission/submission.model';
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
export class SubmissionPanelComponent implements OnInit, OnDestroy {
  subscription: Subscription = new Subscription();

  @Input() submissionId!: string;
  activeSurvey = input<Survey>();
  submission: Submission | null = null;
  tasks?: List<Task>;
  selectedTaskId: string | null = null;
  firebaseURLs = new Map<string, string>();
  isLoading = true;

  public taskType = TaskType;

  constructor(
    private submissionService: SubmissionService,
    private navigationService: NavigationService,
    private storage: Storage
  ) {}

  ngOnInit() {
    this.subscription.add(
      this.submissionService.getSelectedSubmission$().subscribe(submission => {
        if (submission instanceof Submission) {
          this.submission = submission;
          this.tasks = submission.job
            ?.getTasksSorted()
            .filter(task => !task.addLoiTask);
          // Get image URL upon initialization to not send Firebase requests multiple times
          this.getFirebaseImageURLs();
          this.isLoading = false;
        }
      })
    );
    this.subscription.add(
      this.navigationService.getTaskId$().subscribe(taskId => {
        this.selectedTaskId = taskId;
      })
    );
  }

  get submittedTasks() {
    if (!this.tasks) {
      return [];
    }

    return this.tasks.filter(
      task => this.getTaskSubmissionResult(task) !== undefined
    );
  }

  getFirebaseImageURLs() {
    this.tasks?.forEach(task => {
      if (task.type === this.taskType.PHOTO) {
        const submissionImage = this.getTaskSubmissionResult(task);
        if (submissionImage) {
          const submissionImageValue = submissionImage.value as string;
          const imageRef = ref(this.storage, submissionImageValue);
          getDownloadURL(imageRef)
            .then((url: string) => {
              this.firebaseURLs.set(submissionImageValue, url);
            })
            .catch((error: Error) => {
              console.error(error);
            });
        }
      }
    });
  }

  navigateToSubmissionList() {
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't navigate to submission list");
      return;
    }
    if (!this.submission) {
      console.error("No submission - can't navigate to submission list");
      return;
    }
    this.navigationService.selectLocationOfInterest(
      survey.id,
      this.submission.loiId
    );
  }

  getTaskSubmissionResult({ id: taskId }: Task): Result | undefined {
    return this.submission?.data.get(taskId);
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
    const lng = Math.abs(x).toString() + (x > 0 ? '° E' : '° W');
    const lat = Math.abs(y).toString() + (y > 0 ? '° N' : '° S');
    const result = [`${lat}, ${lng}`];
    if (altitude) result.push(`Altitude: ${altitude}m`);
    if (accuracy) result.push(`Accuracy: ${accuracy}m`);
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
    const survey = this.activeSurvey();
    if (!survey) {
      console.error("No active survey - can't select geometry");
      return;
    }
    if (!this.submission) {
      console.error("No submission - can't select geometry");
      return;
    }

    this.navigationService.showSubmissionDetailWithHighlightedTask(
      survey.id,
      this.submission.loiId,
      this.submission.id,
      task.id
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
