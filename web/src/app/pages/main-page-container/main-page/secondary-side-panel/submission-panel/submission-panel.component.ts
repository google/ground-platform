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

import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {AngularFireStorage} from '@angular/fire/compat/storage';
import {List} from 'immutable';
import {Subscription, firstValueFrom} from 'rxjs';

import {Point} from 'app/models/geometry/point';
import {MultipleSelection} from 'app/models/submission/multiple-selection';
import {Result} from 'app/models/submission/result.model';
import {Submission} from 'app/models/submission/submission.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';

@Component({
  selector: 'submission-panel',
  templateUrl: './submission-panel.component.html',
  styleUrls: ['./submission-panel.component.scss'],
})
export class SubmissionPanelComponent implements OnInit, OnDestroy {
  subscription: Subscription = new Subscription();

  @Input() submissionId!: string;
  submission: Submission | null = null;
  tasks?: List<Task>;
  selectedTaskId: string | null = null;
  firebaseURLs = new Map<string, string>();
  isLoading = true;

  public taskType = TaskType;

  constructor(
    private submissionService: SubmissionService,
    private navigationService: NavigationService,
    private storage: AngularFireStorage
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

  getFirebaseImageURLs() {
    this.tasks?.forEach(task => {
      if (task.type === this.taskType.PHOTO) {
        const submissionImage = this.getTaskSubmissionResult(task);
        if (submissionImage) {
          const submissionImageValue = submissionImage.value as string;
          const imageRef = this.storage.ref(submissionImageValue);
          firstValueFrom(imageRef.getDownloadURL())
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
    this.navigationService.selectLocationOfInterest(this.submission!.loiId);
  }

  getTaskSubmissionResult({id: taskId}: Task): Result | undefined {
    return this.submission?.data.get(taskId);
  }

  getTaskMultipleChoiceSelections(task: Task): MultipleSelection {
    return this.getTaskSubmissionResult(task)!.value as MultipleSelection;
  }

  getCaptureLocationCoord(task: Task): string {
    // x represents longitude, y represents latitude
    const {coord, accuracy, altitude} = (this.getTaskSubmissionResult(task)!.value as Point);
    const {x, y} = coord;
    const long = Math.abs(x).toString() + (x > 0 ? '° E' : '° W');
    const lat = Math.abs(y).toString() + (y > 0 ? '° N' : '° S');

    let result =  lat + ', ' + long;
    if (altitude) {
      result += '\nAltitude: ' + altitude + 'm';
    }
    if (accuracy) {
      result += '\nAccuracy: ' + accuracy + 'm';
    }
    return result;
  }

  getDate(task: Task): string {
    return (
      this.getTaskSubmissionResult(task)?.value as Date
    ).toLocaleDateString();
  }

  getTime(task: Task): string {
    return (
      this.getTaskSubmissionResult(task)?.value as Date
    ).toLocaleTimeString([], {hour: 'numeric', minute: 'numeric'});
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
