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
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {Subscription} from 'rxjs';
import {Map} from 'immutable';
import {Submission} from 'app/models/submission/submission.model';
import {Task, TaskType} from 'app/models/task/task.model';

@Component({
  selector: 'submission-panel',
  templateUrl: './submission-panel.component.html',
  styleUrls: ['./submission-panel.component.scss'],
})
export class SubmissionPanelComponent implements OnInit, OnDestroy {
  subscription: Subscription = new Subscription();

  @Input() submissionId!: string;
  submission: Submission | null = null;
  tasks:  Map<string, Task> | undefined;

  public taskType = TaskType;

  constructor(
    private submissionService: SubmissionService,
    private navigationService: NavigationService
  ) {}

  ngOnInit() {
    this.submissionService.selectSubmission(this.submissionId);
    this.subscription.add(
      this.submissionService.getSelectedSubmission$()
      .subscribe(submission => {
        if (submission instanceof Submission) {
          this.submission = submission;
          this.tasks = submission.job?.tasks;
        }
      }));
  }

  navigateToSubmissionList() {
    this.navigationService.selectLocationOfInterest(this.submission!.loiId);
  }

  editSubmission() {
    // TODO(amysorto): implement this feature in a follow up PR
  }

  getTaskType(taskId: string): TaskType | undefined {
    return this.tasks?.get(taskId)?.type;
  }

  getTaskLabel(taskId: string) {
    return this.submission?.job?.tasks?.get(taskId)?.label;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
