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

import { Observation } from './../../shared/models/observation/observation.model';
import { ObservationService } from './../../services/observation/observation.service';
import { FeatureService } from './../../services/feature/feature.service';
import { map, switchMap } from 'rxjs/operators';
import { ProjectService } from './../../services/project/project.service';
import { List } from 'immutable';
import { Observable, Subscription } from 'rxjs';
import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { Layer } from '../../shared/models/layer.model';
import { Field, FieldType } from '../../shared/models/form/field.model';
import { NavigationService } from '../../services/navigation/navigation.service';
import { FeatureHeaderActionType } from '../feature-panel-header/feature-panel-header.component';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { MatDialog } from '@angular/material/dialog';
import 'firebase/storage';

// TODO: Rename "FeatureDetailsComponent".
@Component({
  selector: 'ground-feature-panel',
  templateUrl: './feature-panel.component.html',
  styleUrls: ['./feature-panel.component.css'],
})
export class FeaturePanelComponent implements OnInit, OnDestroy {
  projectId?: string;
  observationId?: string;
  readonly observations$: Observable<List<Observation>>;
  readonly layer$: Observable<Layer>;
  readonly lang: string;
  readonly fieldTypes = FieldType;
  readonly featureHeaderActionType = FeatureHeaderActionType;
  subscription: Subscription = new Subscription();
  photoUrls: Map<string, string>;

  constructor(
    private navigationService: NavigationService,
    projectService: ProjectService,
    featureService: FeatureService,
    observationService: ObservationService,
    private dataStoreService: DataStoreService,
    private confirmationDialog: MatDialog,
    private zone: NgZone
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.observations$ = projectService
      .getActiveProject$()
      .pipe(
        switchMap(project =>
          featureService
            .getSelectedFeature$()
            .pipe(
              switchMap(feature =>
                observationService.observations$(project, feature)
              )
            )
        )
      );
    this.layer$ = projectService
      .getActiveProject$()
      .pipe(
        switchMap(project =>
          featureService
            .getSelectedFeature$()
            .pipe(map(feature => project.layers.get(feature.layerId)!))
        )
      );
    this.photoUrls = new Map();
    this.observations$.forEach((observations) => {
      observations.forEach(observation => {
        this.getFields(observation).forEach(
          field => {
            if (field.type == FieldType.PHOTO) {
              if (observation.responses?.get(field.id)?.value as string) {
                this.fillPhotoURL(field.id, observation.responses?.get(field.id)?.value as string);
              }
            }
          }
        );
      });
    });
  }

  openUrlInNewTab(url: string) {
    window.open(url, '_blank');
  }

  fillPhotoURL(fieldId: string, storageFilePath: string) {
    this.dataStoreService.getImageDownloadURL(storageFilePath)
      .then((url) => {
        this.photoUrls.set(fieldId, url);
      })
      .catch((error) => {
        console.log(error);
      });
  }

  ngOnInit() {
    this.subscription.add(
      this.navigationService.getProjectId$().subscribe(id => {
        this.projectId = id || undefined;
      })
    );

    this.subscription.add(
      this.navigationService.getObservationId$().subscribe(id => {
        this.observationId = id || undefined;
      })
    );
  }

  getFields(observation: Observation): List<Field> {
    return List(observation.form?.fields?.valueSeq() || []);
  }

  onEditObservationClick(observation: Observation) {
    this.navigationService.editObservation(
      this.navigationService.getFeatureId()!,
      observation.id
    );
  }

  onAddObservationClick() {
    this.navigationService.editObservation(
      this.navigationService.getFeatureId()!,
      NavigationService.OBSERVATION_ID_NEW
    );
  }

  onDeleteObservationClick(id: string) {
    this.navigationService.editObservation(
      this.navigationService.getFeatureId()!,
      id
    );
    const dialogRef = this.confirmationDialog.open(
      ConfirmationDialogComponent,
      {
        maxWidth: '500px',
        data: {
          title: 'Warning',
          message:
            'Are you sure you wish to delete this observation? Any associated data will be lost. This cannot be undone.',
        },
      }
    );

    dialogRef.afterClosed().subscribe(async dialogResult => {
      if (dialogResult) {
        await this.deleteObservation();
      }
    });
  }

  async deleteObservation() {
    if (!this.projectId || !this.observationId) {
      return;
    }
    await this.dataStoreService.deleteObservation(
      this.projectId,
      this.observationId
    );
    this.onClose();
  }

  onClose() {
    this.zone.run(() => {
      this.navigationService.selectProject(this.projectId!);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
