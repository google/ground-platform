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

import {
  Component,
  Input,
  OnInit,
  OnDestroy,
  NgZone,
  OnChanges,
} from '@angular/core';
import {Job} from 'app/models/job.model';
import {getPinImageSource} from 'app/ground-pin';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {DialogService} from 'app/services/dialog/dialog.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Subscription} from 'rxjs';
import {GeoJsonLocationOfInterest} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {Map} from 'immutable';
import {Point} from 'app/models/geometry/point';
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
  readonly loiType = LocationOfInterestType;
  subscription: Subscription = new Subscription();
  loiTypeValue?: LocationOfInterestType;
  private readonly CAPTION_PROPERTIES = ['caption', 'label', 'name'];
  private readonly ID_PROPERTIES = ['id', 'identifier', 'id_prod'];
  private loiProperties?: Map<string, string | number>;

  constructor(
    private sanitizer: DomSanitizer,
    private dialogService: DialogService,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    private zone: NgZone,
    readonly loiService: LocationOfInterestService
  ) {
    this.pinUrl = sanitizer.bypassSecurityTrustUrl(getPinImageSource());
    this.subscription.add(
      loiService.getSelectedLocationOfInterest$().subscribe(loi => {
        if (loi instanceof GeoJsonLocationOfInterest) {
          this.loiTypeValue = LocationOfInterestType.Polygon;
        } else if (loi.geometry instanceof Point) {
          this.loiTypeValue = LocationOfInterestType.Point;
        }
        this.loiProperties = loi.properties;
      })
    );
  }

  ngOnInit() {
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
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
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.job?.color)
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

  getLocationOfInterestName(): string | number {
    const caption = this.findProperty(this.CAPTION_PROPERTIES);
    if (caption) {
      return caption;
    }
    const loiType =
      this.loiTypeValue === LocationOfInterestType.Point ? 'Point' : 'Polygon';
    const id = this.findProperty(this.ID_PROPERTIES);
    if (id) {
      return loiType + ' ' + id;
    }
    return loiType;
  }

  private findProperty(matchKeys: string[]): string | number | undefined {
    if (!this.loiProperties) {
      return;
    }
    for (const matchKey of matchKeys) {
      for (const property of this.loiProperties.keys()) {
        if (property.toLowerCase() === matchKey) {
          return this.loiProperties.get(property);
        }
      }
    }
    return;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

enum LocationOfInterestType {
  Point = 'POINT',
  Polygon = 'POLYGON',
}
