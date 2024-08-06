/**
 * Copyright 2020 The Ground Authors.
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

import {Component, NgZone, OnDestroy, OnInit} from '@angular/core';
import {List} from 'immutable';
import {Observable, Subscription, combineLatest} from 'rxjs';
import {switchMap} from 'rxjs/operators';

import {Job} from 'app/models/job.model';
import {MultipleSelection} from 'app/models/submission/multiple-selection';
import {Submission} from 'app/models/submission/submission.model';
import {Option} from 'app/models/task/option.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';

// TODO: Rename "LocationOfInterestDetailsComponent".
@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
})
export class LocationOfInterestPanelComponent implements OnInit, OnDestroy {
  surveyId?: string;
  submissionId?: string;
  readonly submissions$: Observable<List<Submission>>;
  readonly taskTypes = TaskType;
  subscription: Subscription = new Subscription();
  photoUrls: Map<string, string>;
  job?: Job;

  constructor(
    private navigationService: NavigationService,
    surveyService: SurveyService,
    loiService: LocationOfInterestService,
    submissionService: SubmissionService,
    private dataStoreService: DataStoreService,
    private dialogService: DialogService,
    private zone: NgZone
  ) {
    this.submissions$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          loiService
            .getSelectedLocationOfInterest$()
            .pipe(switchMap(loi => submissionService.submissions$(survey, loi)))
        )
      );
    combineLatest([
      surveyService.getActiveSurvey$(),
      loiService.getSelectedLocationOfInterest$(),
    ]).subscribe(([survey, loi]) => (this.job = survey.jobs.get(loi.jobId)));
    this.photoUrls = new Map();
    this.submissions$.forEach(submissions => {
      submissions.forEach(submission => {
        this.getTasks(submission).forEach(task => {
          if (
            task.type === TaskType.PHOTO &&
            (submission.data?.get(task.id)?.value as string)
          ) {
            this.fillPhotoURL(
              task.id,
              submission.data?.get(task.id)?.value as string
            );
          }
        });
      });
    });
  }

  openUrlInNewTab(url: string) {
    window.open(url, '_blank');
  }

  fillPhotoURL(taskId: string, storageFilePath: string) {
    this.dataStoreService
      .getImageDownloadURL(storageFilePath)
      .then(url => {
        this.photoUrls.set(taskId, url);
      })
      .catch(error => {
        console.log(error);
      });
  }

  ngOnInit() {
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(id => {
        this.surveyId = id || undefined;
      })
    );

    this.subscription.add(
      this.navigationService.getSubmissionId$().subscribe(id => {
        this.submissionId = id || undefined;
      })
    );
  }

  getTasks(submission: Submission): List<Task> {
    return List(submission.job?.tasks?.valueSeq() || []);
  }

  getOptions(task: Task, submission: Submission): List<Option> {
    const result = submission.data?.get(task.id);
    if (result && result instanceof List) {
      return (result.value as MultipleSelection).values;
    } else {
      return List.of();
    }
  }

  onEditSubmissionClick(submission: Submission) {
    this.navigationService.editSubmission(
      this.navigationService.getLocationOfInterestId()!,
      submission.id
    );
  }

  onAddSubmissionClick() {
    this.navigationService.editSubmission(
      this.navigationService.getLocationOfInterestId()!,
      NavigationService.SUBMISSION_ID_NEW
    );
  }

  onDeleteSubmissionClick(id: string) {
    this.navigationService.editSubmission(
      this.navigationService.getLocationOfInterestId()!,
      id
    );
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this submission? ' +
          'Any associated data will be lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          await this.deleteSubmission();
        }
      });
  }

  async deleteSubmission() {
    if (!this.surveyId || !this.submissionId) {
      return;
    }
    await this.dataStoreService.deleteSubmission(
      this.surveyId,
      this.submissionId
    );
    this.onClose();
  }

  onClose() {
    this.zone.run(() => {
      this.navigationService.selectSurvey(this.surveyId!);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
