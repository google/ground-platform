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
import { LocationOfInterestService } from './../../services/loi/loi.service';
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

// TODO: Rename "LocationOfInterestDetailsComponent".
@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
})
export class LocationOfInterestPanelComponent implements OnInit, OnDestroy {
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
    loiService: LocationOfInterestService,
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
          loiService
            .getSelectedLocationOfInterest$()
            .pipe(
              switchMap(loi => observationService.observations$(survey, loi))
            )
        )
      );
    combineLatest([
      surveyService.getActiveSurvey$(),
      loiService.getSelectedLocationOfInterest$(),
    ]).subscribe(([survey, loi]) => (this.job = survey.jobs.get(loi.jobId)));
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
      this.navigationService.getLocationOfInterestId()!,
      observation.id
    );
  }

  onAddObservationClick() {
    this.navigationService.editObservation(
      this.navigationService.getLocationOfInterestId()!,
      NavigationService.OBSERVATION_ID_NEW
    );
  }

  onDeleteObservationClick(id: string) {
    this.navigationService.editObservation(
      this.navigationService.getLocationOfInterestId()!,
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
