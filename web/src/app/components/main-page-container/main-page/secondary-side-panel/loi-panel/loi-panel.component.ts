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

import {Component, OnDestroy, OnInit, input} from '@angular/core';
import {toObservable} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {List} from 'immutable';
import {Subscription, combineLatest, switchMap} from 'rxjs';

import {LoiPropertiesDialogComponent} from 'app/components/shared/loi-properties-dialog/loi-properties-dialog.component';
import {LocationOfInterest} from 'app/models/loi.model';
import {Submission} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {getLoiIcon} from 'app/utils/utils';

@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
})
export class LocationOfInterestPanelComponent implements OnInit, OnDestroy {
  subscription: Subscription = new Subscription();
  activeSurvey = input<Survey>();

  activeSurvey$ = toObservable(this.activeSurvey);

  loi!: LocationOfInterest;
  name!: string | null;
  icon!: string;
  iconColor!: string;
  submissions!: List<Submission>;
  isLoading = true;

  constructor(
    private dialog: MatDialog,
    private loiService: LocationOfInterestService,
    private submissionService: SubmissionService,
    private navigationService: NavigationService
  ) {}

  ngOnInit() {
    this.subscription.add(
      combineLatest([
        this.activeSurvey$,
        this.loiService.getSelectedLocationOfInterest$(),
      ])
        .pipe(
          switchMap(([survey, loi]) => {
            if (survey) {
              this.iconColor = survey.getJob(loi.jobId)!.color!;
            }
            this.loi = loi;
            this.name = LocationOfInterestService.getDisplayName(loi);
            this.icon = getLoiIcon(loi);

            return this.submissionService.getSubmissions$();
          })
        )
        .subscribe(submissions => {
          this.submissions = submissions;
          this.isLoading = false;
        })
    );
  }

  onSelectSubmission(submissionId: string) {
    if (!this.activeSurvey()) {
      console.error('No active survey');
      return;
    }
    this.navigationService.showSubmissionDetail(
      this.activeSurvey()!.id,
      this.loi.id,
      submissionId
    );
  }

  onClosePanel() {
    this.navigationService.clearLocationOfInterestId();
  }

  hasProperties() {
    return this.loi.properties?.size;
  }

  openPropertiesDialog(event: Event): void {
    event.stopPropagation();
    this.dialog.open(LoiPropertiesDialogComponent, {
      width: '580px',
      height: '70%',
      autoFocus: false,
      data: {
        iconColor: this.iconColor,
        iconName: this.icon,
        loiDisplayName: this.name,
        properties: this.loi.properties?.toObject(),
      },
      panelClass: 'loi-properties-dialog-container',
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
