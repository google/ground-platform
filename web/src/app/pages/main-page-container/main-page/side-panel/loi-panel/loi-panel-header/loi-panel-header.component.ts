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

import {
  Component,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  OnInit,
} from '@angular/core';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {Map} from 'immutable';
import {Subscription} from 'rxjs';

import {GeometryType} from 'app/models/geometry/geometry';
import {Job} from 'app/models/job.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-loi-panel-header',
  templateUrl: './loi-panel-header.component.html',
  styleUrls: ['./loi-panel-header.component.scss'],
})
export class LocationOfInterestPanelHeaderComponent
  implements OnInit, OnDestroy, OnChanges
{
  @Input() job?: Job;
  surveyId?: string | null;
  loiId?: string | null;
  pinUrl: SafeUrl;
  readonly geometryType = GeometryType;
  subscription: Subscription = new Subscription();
  loiDisplayName?: string;
  loiGeometryType?: GeometryType;

  constructor(
    private sanitizer: DomSanitizer,
    private dialogService: DialogService,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private zone: NgZone,
    readonly loiService: LocationOfInterestService
  ) {
    this.pinUrl = sanitizer.bypassSecurityTrustUrl(
      groundPinService.getPinImageSource()
    );
    this.subscription.add(
      loiService.getSelectedLocationOfInterest$().subscribe(loi => {
        this.loiDisplayName = LocationOfInterestService.getDisplayName(loi);
        this.loiGeometryType = loi.geometry?.geometryType;
      })
    );
  }

  ngOnInit() {
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
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
  }

  ngOnChanges() {
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.job?.color)
    );
  }

  onCloseClick() {
    this.navigationService.clearLocationOfInterestId();
  }

  onDeleteLocationOfInterestClick() {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this loi? ' +
          'Any associated data, including all submissions, will be lost. ' +
          'This cannot be undone.'
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          await this.deleteLocationOfInterest();
        }
      });
  }

  async deleteLocationOfInterest() {
    if (!this.surveyId || !this.loiId) {
      return;
    }
    await this.dataStoreService.deleteLocationOfInterest(
      this.surveyId,
      this.loiId
    );
    this.onClose();
  }

  onClose() {
    // ng zone is run to fix navigation triggered outside Angular zone warning.
    this.zone.run(() => {
      this.navigationService.selectSurvey(this.surveyId!);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
