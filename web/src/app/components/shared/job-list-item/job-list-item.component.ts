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

import { FlatTreeControl } from '@angular/cdk/tree';
import { Component, Input, OnInit, SimpleChanges, effect } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { List } from 'immutable';

import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { AuthService } from 'app/services/auth/auth.service';
import { GroundPinService } from 'app/services/ground-pin/ground-pin.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { environment } from 'environments/environment';

import { DynamicDataSource, DynamicFlatNode } from './tree-data-source';

@Component({
    selector: 'ground-job-list-item',
    templateUrl: './job-list-item.component.html',
    styleUrls: ['./job-list-item.component.scss'],
    standalone: false
})
export class JobListItemComponent implements OnInit {
  @Input() job!: Job;
  @Input() lois: List<LocationOfInterest> = List();
  @Input() actionsType: JobListItemActionsType = JobListItemActionsType.MENU;

  private urlParamsSignal = this.navigationService.getUrlParams();

  surveyId?: string | null;
  loiId?: string | null;
  jobPinUrl: SafeUrl;
  readonly jobListItemActionsType = JobListItemActionsType;
  treeControl: FlatTreeControl<DynamicFlatNode>;
  dataSource: DynamicDataSource;

  getLevel = (node: DynamicFlatNode) => node.level;
  isExpandable = (node: DynamicFlatNode) => node.expandable;
  hasChild = (node: DynamicFlatNode) => node.childCount > 0;
  isJob = (_: number, node: DynamicFlatNode) => node.level === 0;

  constructor(
    private sanitizer: DomSanitizer,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private authService: AuthService
  ) {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource()
    );

    this.treeControl = new FlatTreeControl<DynamicFlatNode>(
      this.getLevel,
      this.isExpandable
    );

    this.dataSource = new DynamicDataSource(this.treeControl);

    effect(() => {
      const { surveyId, loiId } = this.urlParamsSignal();
      this.surveyId = surveyId;
      this.loiId = loiId;
    });
  }

  ngOnInit() {
    this.updatePinUrl();

    this.dataSource.setJobAndLois(this.job, this.lois);

    this.treeControl.expansionModel.changed.subscribe(change => {
      if (change.added)
        change.added.forEach(node => this.dataSource.expandJob(node));
      if (change.removed)
        change.removed.forEach(node => this.dataSource.collapseJob(node));
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['job']) {
      this.updatePinUrl();
    }

    if (changes['job'] || changes['lois']) {
      if (this.job) {
        this.dataSource.setJobAndLois(this.job, this.lois);
      }
    }
  }

  private updatePinUrl() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.job?.color)
    );
  }

  onGoBackClick() {
    this.navigationService.clearLocationOfInterestId();
  }

  onClose() {
    return this.navigationService.selectSurvey(this.surveyId!);
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

  async onDownloadGeoJsonClick() {
    // TODO(#1160): This can be optimized to only create a cookie when missing or expired.
    await this.authService.createSessionCookie();
    window.open(
      `${environment.cloudFunctionsUrl}/exportGeojson?` +
        `survey=${this.surveyId}&job=${this.job?.id}`,
      '_blank'
    );
  }

  isSelectedLoi(node: DynamicFlatNode): boolean {
    return node.loi?.id === this.loiId;
  }

  selectLoi(node: DynamicFlatNode) {
    if (this.surveyId && !node.isJob) {
      this.navigationService.selectLocationOfInterest(
        this.surveyId,
        node.loi!.id
      );
    }
  }

  isSidePanelExpanded() {
    return this.navigationService.getSidePanelExpanded();
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
