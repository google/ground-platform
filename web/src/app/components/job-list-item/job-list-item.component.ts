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

import {FlatTreeControl} from '@angular/cdk/tree';
import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {List} from 'immutable';
import {Subscription} from 'rxjs';

import {ImportDialogComponent} from 'app/components/import-dialog/import-dialog.component';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {environment} from 'environments/environment';

import {DynamicDataSource, DynamicFlatNode} from './tree-data-source';

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
    private authService: AuthService,
    readonly surveyService: SurveyService
  ) {
    this.jobPinUrl = sanitizer.bypassSecurityTrustUrl(
      groundPinService.getPinImageSource()
    );
    this.treeControl = new FlatTreeControl<DynamicFlatNode>(
      this.getLevel,
      this.isExpandable
    );
    this.dataSource = new DynamicDataSource(this.treeControl, this.loiService);
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

    // Add initial node for current job
    this.dataSource.data = this.dataSource.data.concat([
      new DynamicFlatNode(
        /* name= */ this.job!.name!,
        /* level= */ 0,
        /* expandable= */ true,
        /* iconName= */ 'label',
        /* iconColo= */ this.job!.color!,
        /* jobId= */ this.job!.id
      ),
    ]);
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
        'Are you sure you want to delete this job? Data collection sites, ' +
          'task definitions, and any associated data will be lost. This ' +
          'cannot be undone.'
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
        `survey=${this.surveyId}&job=${this.job?.id}`,
      '_blank'
    );
  }

  onJobListSelect(): void | undefined {
    if (!this.job?.id) {
      return;
    }
    this.navigationService.showLocationOfInterestList(this.job.id);
  }

  isSelectedLoi(node: DynamicFlatNode): boolean {
    return node.loi?.id === this.loiId;
  }

  isLoiNode(node: DynamicFlatNode): boolean {
    return node.loi ? true : false;
  }

  selectLoi(node: DynamicFlatNode) {
    if (this.isLoiNode(node)) {
      this.navigationService.selectLocationOfInterest(node.loi!.id);
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
