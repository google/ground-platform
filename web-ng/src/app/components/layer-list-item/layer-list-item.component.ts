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

import { Component, Input, OnInit, Inject } from '@angular/core';
import { Layer } from '../../shared/models/layer.model';
import { getPinImageSource } from '../map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { RouterService } from './../../services/router/router.service';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import {
  MatDialog,
  MAT_DIALOG_DATA,
  MatDialogRef,
} from '@angular/material/dialog';
import { Feature } from '../../shared/models/feature.model';
import { Router } from '@angular/router';
import { DataStoreService } from '../../services/data-store/data-store.service';

@Component({
  selector: 'ground-layer-list-item',
  templateUrl: './layer-list-item.component.html',
  styleUrls: ['./layer-list-item.component.css'],
})
export class LayerListItemComponent implements OnInit {
  @Input() layer: Layer | undefined;
  @Input() actionsType: LayerListItemActionsType =
    LayerListItemActionsType.MENU;
  projectId?: string;
  featureId?: string;
  layerPinUrl: SafeUrl;
  readonly lang: string;
  readonly layerListItemActionsType = LayerListItemActionsType;

  constructor(
    private routerService: RouterService,
    private sanitizer: DomSanitizer,
    private confirmationDialog: MatDialog,
    private router: Router,
    private dataStoreService: DataStoreService
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.layerPinUrl = sanitizer.bypassSecurityTrustUrl(getPinImageSource());
  }

  ngOnInit() {
    this.layerPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.layer?.color)
    );
    this.routerService.getFeatureId$().subscribe(id => {
      this.featureId = id;
    });
    this.routerService.getProjectId$().subscribe(id => {
      this.projectId = id;
    });
  }

  ngOnChanges() {
    this.layerPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(this.layer?.color)
    );
  }

  onCustomizeLayer() {
    if (this.layer?.id) {
      this.routerService.setLayerId(this.layer?.id);
    }
  }

  onGoBackClick() {
    this.routerService.setFeatureId(null);
  }

  onDeleteFeature() {
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
    await this.dataStoreService.deleteFeature(
      this.projectId,
      this.featureId,
    );
    this.onClose();
  }

  onClose() {
    return this.router.navigate([`p/${this.projectId}`]);
  }
}

export enum LayerListItemActionsType {
  MENU = 1,
  BACK = 2,
}
