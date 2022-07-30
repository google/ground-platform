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

import { Submission } from './../../shared/models/submission/submission.model';
import { SubmissionService } from './../../services/submission/submission.service';
import { LocationOfInterestService } from './../../services/loi/loi.service';
import { switchMap } from 'rxjs/operators';
import { SurveyService } from './../../services/survey/survey.service';
import { List } from 'immutable';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { Job } from '../../shared/models/job.model';
import { Task, TaskType } from '../../shared/models/task/task.model';
import { NavigationService } from '../../services/navigation/navigation.service';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { DialogService } from '../../services/dialog/dialog.service';

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
            (submission.results?.get(task.id)?.value as string)
          ) {
            this.fillPhotoURL(
              task.id,
              submission.results?.get(task.id)?.value as string
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
