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

import { DataStoreService } from './../data-store/data-store.service';
import { switchMap, take } from 'rxjs/operators';
import { Observable, ReplaySubject } from 'rxjs';
import { Survey } from './../../shared/models/survey.model';
import { SurveyService } from './../survey/survey.service';
import { Injectable } from '@angular/core';
import { Feature, LocationFeature } from '../../shared/models/feature.model';
import { List } from 'immutable';
import firebase from 'firebase/app';

@Injectable({
  providedIn: 'root',
})
export class FeatureService {
  private features$: Observable<List<Feature>>;
  private selectedFeatureId$ = new ReplaySubject<string>(1);
  private selectedFeature$: Observable<Feature>;

  constructor(
    private dataStore: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.features$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          survey.isUnsavedNew() ? List() : dataStore.features$(survey)
        )
      );

    this.selectedFeature$ = this.selectedFeatureId$.pipe(
      switchMap(featureId =>
        surveyService
          .getActiveSurvey$()
          .pipe(
            switchMap(survey =>
              this.dataStore.loadFeature$(survey.id, featureId)
            )
          )
      )
    );
  }

  getFeatures$(): Observable<List<Feature>> {
    return this.features$;
  }

  selectFeature(featureId: string) {
    this.selectedFeatureId$.next(featureId);
  }

  getSelectedFeature$(): Observable<Feature> {
    return this.selectedFeature$;
  }

  async addPoint(
    lat: number,
    lng: number,
    jobId: string
  ): Promise<Feature | null> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return await this.addPointInternal(survey, lat, lng, jobId);
  }

  async updatePoint(feature: Feature): Promise<void> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return await this.updatePointInternal(survey, feature);
  }

  private async addPointInternal(
    survey: Survey,
    lat: number,
    lng: number,
    jobId: string
  ): Promise<Feature | null> {
    if (!(survey.jobs || new Map()).get(jobId)) {
      return null;
    }
    const newFeature = new LocationFeature(
      this.dataStore.generateId(),
      jobId,
      new firebase.firestore.GeoPoint(lat, lng)
    );
    await this.dataStore.updateFeature(survey.id, newFeature);
    return newFeature;
  }

  private async updatePointInternal(survey: Survey, feature: Feature) {
    if (survey.jobs.isEmpty()) {
      return;
    }
    await this.dataStore.updateFeature(survey.id, feature);
  }
}
