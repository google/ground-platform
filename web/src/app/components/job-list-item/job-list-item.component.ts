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
import {Component, Input, OnDestroy, OnInit, effect} from '@angular/core';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {List} from 'immutable';
import {Subscription} from 'rxjs';

import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {AuthService} from 'app/services/auth/auth.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
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

  private urlParamsSignal = this.navigationService.getUrlParams();

  surveyId?: string | null;
  loiId?: string | null;
  jobPinUrl: SafeUrl;
  readonly jobListItemActionsType = JobListItemActionsType;
  subscription: Subscription = new Subscription();
  treeControl: FlatTreeControl<DynamicFlatNode>;
  dataSource: DynamicDataSource;

  getLevel = (node: DynamicFlatNode) => node.level;
  isExpandable = (node: DynamicFlatNode) => node.expandable;
  hasChild = (node: DynamicFlatNode) => node.childCount > 0;
  isJob = (_: number, node: DynamicFlatNode) => node.level === 0;

  constructor(
    private sanitizer: DomSanitizer,
    private loiService: LocationOfInterestService,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private authService: AuthService
  ) {
    console.log('JobListItemComponent urlParamsSignal:', this.urlParamsSignal);
    this.jobPinUrl = sanitizer.bypassSecurityTrustUrl(
      groundPinService.getPinImageSource()
    );
    this.treeControl = new FlatTreeControl<DynamicFlatNode>(
      this.getLevel,
      this.isExpandable
    );
    this.dataSource = new DynamicDataSource(this.treeControl, this.loiService);

    effect(() => {
      const {surveyId, loiId} = this.urlParamsSignal();
      this.surveyId = surveyId;
      this.loiId = loiId;
    });
  }

  ngOnInit() {
    this.jobPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.job?.color)
    );
    this.subscription.add(
      this.loiService.getLocationsOfInterest$().subscribe(lois => {
        this.dataSource.data = this.dataSource.data.concat([
          this.createJobNode(this.job!, lois),
        ]);
      })
    );
  }

  ngOnChanges() {
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

  createJobNode(job: Job, lois: List<LocationOfInterest>): DynamicFlatNode {
    return new DynamicFlatNode(
      /* name= */ job!.name!,
      /* level= */ 0,
      /* expandable= */ true,
      /* iconName= */ 'label',
      /* iconColo= */ job!.color!,
      /* jobId= */ job!.id,
      /* isJob= */ true,
      /* childCount= */ lois.filter(loi => loi.jobId === this.job?.id).size
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

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isSidePanelExpanded() {
    return this.navigationService.getSidePanelExpanded();
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
