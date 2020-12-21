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

import { Component, Input, OnInit, OnDestroy, NgZone } from '@angular/core';
import { Layer } from '../../shared/models/layer.model';
import { getPinImageSource } from '../map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { NavigationService } from '../../services/router/router.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'ground-feature-panel-header',
  templateUrl: './feature-panel-header.component.html',
  styleUrls: ['./feature-panel-header.component.scss'],
})
export class FeaturePanelHeaderComponent implements OnInit, OnDestroy {
  @Input() layer?: Layer;
  @Input() actionsType: FeatureHeaderActionType = FeatureHeaderActionType.MENU;
  projectId?: string | null;
  featureId?: string | null;
  pinUrl: SafeUrl;
  readonly lang: string;
  readonly featureHeaderActionType = FeatureHeaderActionType;
  subscription: Subscription = new Subscription();

  constructor(
    private sanitizer: DomSanitizer,
    private confirmationDialog: MatDialog,
    private router: Router,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    private zone: NgZone
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.pinUrl = sanitizer.bypassSecurityTrustUrl(getPinImageSource());
  }

  ngOnInit() {
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.layer?.color)
    );
    this.subscription.add(
      this.navigationService.getFeatureId$().subscribe(id => {
        this.featureId = id;
      })
    );
    this.subscription.add(
      this.navigationService.getProjectId$().subscribe(id => {
        this.projectId = id;
      })
    );
  }

  ngOnChanges() {
    this.pinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.layer?.color)
    );
  }

  onCloseClick() {
    this.navigationService.setFeatureId(null);
  }

  onDeleteFeatureClick() {
    const dialogRef = this.confirmationDialog.open(
      ConfirmationDialogComponent,
      {
        maxWidth: '500px',
        data: {
          title: 'Warning',
          message:
            'Are you sure you wish to delete this feature? Any associated data including all observations in this feature will be lost. This cannot be undone.',
        },
      }
    );

    dialogRef.afterClosed().subscribe(async dialogResult => {
      if (dialogResult) {
        await this.deleteFeature();
      }
    });
  }

  async deleteFeature() {
    if (!this.projectId || !this.featureId) {
      return;
    }
    await this.dataStoreService.deleteFeature(this.projectId, this.featureId);
    this.onClose();
  }

  onClose() {
    // ng zone is run to fix navigation triggered outside Angular zone warning.
    this.zone.run(() => {
      this.router.navigate([`p/${this.projectId}`]);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

export enum FeatureHeaderActionType {
  MENU = 1,
  BACK = 2,
}
