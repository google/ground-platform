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
import { switchMap } from 'rxjs/operators';
import { SurveyService } from './../../services/survey/survey.service';
import { List } from 'immutable';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { Job } from '../../shared/models/job.model';
import { Step, StepType } from '../../shared/models/task/step.model';
import { NavigationService } from '../../services/navigation/navigation.service';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { DialogService } from '../../services/dialog/dialog.service';

// TODO: Rename "FeatureDetailsComponent".
@Component({
  selector: 'ground-feature-panel',
  templateUrl: './feature-panel.component.html',
  styleUrls: ['./feature-panel.component.scss'],
})
export class FeaturePanelComponent implements OnInit, OnDestroy {
  surveyId?: string;
  observationId?: string;
  readonly observations$: Observable<List<Observation>>;
  readonly lang: string;
  readonly stepTypes = StepType;
  subscription: Subscription = new Subscription();
  photoUrls: Map<string, string>;
  job?: Job;

  constructor(
    private navigationService: NavigationService,
    surveyService: SurveyService,
    featureService: FeatureService,
    observationService: ObservationService,
    private dataStoreService: DataStoreService,
    private dialogService: DialogService,
    private zone: NgZone
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.observations$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          featureService
            .getSelectedFeature$()
            .pipe(
              switchMap(feature =>
                observationService.observations$(survey, feature)
              )
            )
        )
      );
    combineLatest([
      surveyService.getActiveSurvey$(),
      featureService.getSelectedFeature$(),
    ]).subscribe(
      ([survey, feature]) => (this.job = survey.jobs.get(feature.jobId))
    );
    this.photoUrls = new Map();
    this.observations$.forEach(observations => {
      observations.forEach(observation => {
        this.getSteps(observation).forEach(step => {
          if (
            step.type === StepType.PHOTO &&
            (observation.responses?.get(step.id)?.value as string)
          ) {
            this.fillPhotoURL(
              step.id,
              observation.responses?.get(step.id)?.value as string
            );
          }
        });
      });
    });
  }

  openUrlInNewTab(url: string) {
    window.open(url, '_blank');
  }

  fillPhotoURL(stepId: string, storageFilePath: string) {
    this.dataStoreService
      .getImageDownloadURL(storageFilePath)
      .then(url => {
        this.photoUrls.set(stepId, url);
      })
      .catch(error => {
        console.log(error);
      });
  }

  ngOnInit() {
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(id => {
        this.surveyId = id || undefined;
      })
    );

    this.subscription.add(
      this.navigationService.getObservationId$().subscribe(id => {
        this.observationId = id || undefined;
      })
    );
  }

  getSteps(observation: Observation): List<Step> {
    return List(observation.task?.steps?.valueSeq() || []);
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
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this observation? ' +
          'Any associated data will be lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          await this.deleteObservation();
        }
      });
  }

  async deleteObservation() {
    if (!this.surveyId || !this.observationId) {
      return;
    }
    await this.dataStoreService.deleteObservation(
      this.surveyId,
      this.observationId
    );
    this.onClose();
  }

  onClose() {
    this.zone.run(() => {
      this.navigationService.selectSurvey(this.surveyId!);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
