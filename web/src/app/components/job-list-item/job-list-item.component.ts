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

import {FlatTreeControl} from '@angular/cdk/tree';
import {Component, Input, OnInit, OnDestroy} from '@angular/core';
import {DialogService} from 'app/services/dialog/dialog.service';
import {ImportDialogComponent} from 'app/components/import-dialog/import-dialog.component';
import {Job} from 'app/models/job.model';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {environment} from 'environments/environment';
import {shareReplay, Subscription} from 'rxjs';
import {SurveyService} from 'app/services/survey/survey.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {DynamicDataSource, DynamicFlatNode} from './tree-data-source';
import {LocationOfInterest} from 'app/models/loi.model';
import {List} from 'immutable';
import { AuthService } from 'app/services/auth/auth.service';

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
  treeControl: FlatTreeControl<DynamicFlatNode>;
  dataSource: DynamicDataSource;
  lois: List<LocationOfInterest> = List();
  submissionSubscriptions = new Map<string, Subscription>();

  getLevel = (node: DynamicFlatNode) => node.level;
  isExpandable = (node: DynamicFlatNode) => node.expandable;
  hasChild = (_: number, _nodeData: DynamicFlatNode) => _nodeData.expandable;

  constructor(
    private sanitizer: DomSanitizer,
    private dialogService: DialogService,
    private importDialog: MatDialog,
    private dataStoreService: DataStoreService,
    private loiService: LocationOfInterestService,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private submissionService: SubmissionService,
    private authService: AuthService,``
    readonly surveyService: SurveyService
  ) {
    this.jobPinUrl = sanitizer.bypassSecurityTrustUrl(
      groundPinService.getPinImageSource()
    );
    this.treeControl = new FlatTreeControl<DynamicFlatNode>(
      this.getLevel,
      this.isExpandable
    );
    this.dataSource = new DynamicDataSource(
      this.treeControl,
      this.submissionSubscriptions,
      submissionService,
      surveyService
    );
  }

  ngOnInit() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.job?.color)
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

    this.subscription.add(
      this.loiService
        .getLocationsOfInterest$()
        .pipe(shareReplay())
        .subscribe(lois => {
          this.lois = lois.filter(loi => {
            return loi.jobId === this.job?.id;
          });
          // Reset nodes for new loi values since we don't know how many lois
          // were added or removed from the previous value
          this.resetLoiNodes();
          for (const loi of LocationOfInterestService.getLoisWithNames(
            this.lois
          )) {
            this.addLoiNode(loi, loi.name!);
          }
        })
    );
  }

  ngOnChanges() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.job?.color)
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
      data: {surveyId: this.surveyId, jobId: this.job?.id},
      width: '350px',
      maxHeight: '800px',
    });
  }

  async onDownloadCsvClick() {
    // TODO(#1160): This can be optimized to only create a cookie when missing or expired.
    await this.authService.createSessionCookie();
    window.open(
      `${environment.cloudFunctionsUrl}/exportCsv?` +
      `survey=${this.surveyId}&job=${this.job?.id}`, '_blank'
    );
  }

  onJobListSelect(): void | undefined {
    if (!this.job?.id) {
      return;
    }
    this.navigationService.showLocationOfInterestList(this.job.id);
  }

  addLoiNode(loi: LocationOfInterest, displayName: string) {
    // Pushing does not cause the tree to rerender, but reassignment does.
    this.dataSource.data = this.dataSource.data.concat([
      new DynamicFlatNode(displayName, 0, true, loi),
    ]);
  }

  resetLoiNodes() {
    this.dataSource.data = [];
    this.submissionSubscriptions.forEach(subscription => {
      subscription.unsubscribe();
    });
  }

  isSelectedLoi(node: DynamicFlatNode): boolean {
    return node.loi?.id === this.loiId;
  }

  isLoiNode(node: DynamicFlatNode): boolean {
    return node.loi ? true : false;
  }

  isSubmissionNode(node: DynamicFlatNode): boolean {
    return node.submission ? true : false;
  }

  selectLoi(node: DynamicFlatNode) {
    if (this.isLoiNode(node)) {
      this.navigationService.selectLocationOfInterest(node.loi!.id);
    } else if (this.isSubmissionNode(node)) {
      this.submissionService.selectSubmission(node.submission!.id);
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.submissionSubscriptions.forEach(subscription => {
      subscription.unsubscribe();
    });
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
