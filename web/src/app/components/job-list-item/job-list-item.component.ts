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

import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { DialogService } from 'app/services/dialog/dialog.service';
import { ImportDialogComponent } from 'app/components/import-dialog/import-dialog.component';
import { Job } from 'app/shared/models/job.model';
import { getPinImageSource } from 'app/components/map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { environment } from 'environments/environment';
import { Subscription } from 'rxjs';
import { SurveyService } from 'app/services/survey/survey.service';

@Component({
  selector: 'ground-job-list-item',
  templateUrl: './job-list-item.component.html',
  styleUrls: ['./job-list-item.component.scss'],
})
export class JobListItemComponent implements OnInit, OnDestroy {
  @Input() job?: Job;
  @Input() actionsType: JobListItemActionsType = JobListItemActionsType.MENU;
  surveyId?: string | null;
  loiId?: string | null;
  jobPinUrl: SafeUrl;
  readonly jobListItemActionsType = JobListItemActionsType;
  subscription: Subscription = new Subscription();

  constructor(
    private sanitizer: DomSanitizer,
    private dialogService: DialogService,
    private importDialog: MatDialog,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    readonly surveyService: SurveyService
  ) {
    this.jobPinUrl = sanitizer.bypassSecurityTrustUrl(getPinImageSource());
  }

  ngOnInit() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.job?.color)
    );
    this.subscription.add(
      this.navigationService.getLocationOfInterestId$().subscribe(id => {
        this.loiId = id;
      })
    );
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(id => {
        this.surveyId = id;
      })
    );
  }

  ngOnChanges() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.job?.color)
    );
  }

  onCustomizeJob() {
    if (this.job?.id) {
      this.navigationService.customizeJob(this.job?.id);
    }
  }

  onGoBackClick() {
    this.navigationService.clearLocationOfInterestId();
  }

  onDeleteJob() {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this job? Any associated data ' +
          'including all lois and submissions in this job will be ' +
          'lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          await this.deleteJob();
        }
      });
  }

  async deleteJob() {
    await this.dataStoreService.deleteJob(this.surveyId!, this.job!.id);
    this.onClose();
  }

  onClose() {
    return this.navigationService.selectSurvey(this.surveyId!);
  }

  onImportLocationsOfInterest() {
    if (!this.surveyId || !this.job?.id) {
      return;
    }
    this.importDialog.open(ImportDialogComponent, {
      data: { surveyId: this.surveyId, jobId: this.job?.id },
      width: '350px',
      maxHeight: '800px',
    });
  }

  getDownloadCsvUrl() {
    return (
      `${environment.cloudFunctionsUrl}/exportCsv?` +
      `survey=${this.surveyId}&job=${this.job?.id}`
    );
  }

  onJobListSelect(): void | undefined {
    if (!this.job?.id) {
      return;
    }
    this.navigationService.showLocationOfInterestList(this.job.id);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
